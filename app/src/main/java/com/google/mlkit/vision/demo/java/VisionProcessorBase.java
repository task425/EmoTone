/*
 * Copyright 2020 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.mlkit.vision.demo.java;

import static java.lang.Math.max;
import static java.lang.Math.min;

import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageProxy;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskExecutors;
import com.google.android.gms.tasks.Tasks;
import com.google.android.odml.image.BitmapMlImageBuilder;
import com.google.android.odml.image.ByteBufferMlImageBuilder;
import com.google.android.odml.image.MediaMlImageBuilder;
import com.google.android.odml.image.MlImage;
import com.google.mlkit.common.MlKitException;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.demo.BitmapUtils;
import com.google.mlkit.vision.demo.CameraImageGraphic;
import com.google.mlkit.vision.demo.FrameMetadata;
import com.google.mlkit.vision.demo.GraphicOverlay;
import com.google.mlkit.vision.demo.ScopedExecutor;
import com.google.mlkit.vision.demo.VisionImageProcessor;
import com.google.mlkit.vision.demo.preference.PreferenceUtils;

import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;

/**
 * ビジョンフレームプロセッサの抽象基底クラスです。サブクラスは、検出結果に対して行いたい処理を定義するために{@link #onSuccess(Object, GraphicOverlay)}を実装し、
 * ディテクターオブジェクトを指定するために{@link #detectInImage(InputImage)}を実装する必要があります。
 *
 * @param <T> 検出された特徴の型
 */
public abstract class VisionProcessorBase<T> implements VisionImageProcessor {

    protected static final String MANUAL_TESTING_LOG = "LogTagForTest";
    private static final String TAG = "VisionProcessorBase";

    private final ActivityManager activityManager;
    private final Timer fpsTimer = new Timer();
    private final ScopedExecutor executor;

    // このプロセッサが既にシャットダウンされているかどうか
    private boolean isShutdown;

    // 同じスレッドで実行されるため、同期は不要
    private int numRuns = 0;
    private long totalFrameMs = 0;
    private long maxFrameMs = 0;
    private long minFrameMs = Long.MAX_VALUE;
    private long totalDetectorMs = 0;
    private long maxDetectorMs = 0;
    private long minDetectorMs = Long.MAX_VALUE;

    // 1秒間の間に処理されたフレーム数をカウントしてFPSを計算するためのフレームカウント
    private int frameProcessedInOneSecondInterval = 0;
    private int framesPerSecond = 0;

    // 最新の画像とそのメタデータを保持するための変数
    @GuardedBy("this")
    private ByteBuffer latestImage;

    @GuardedBy("this")
    private FrameMetadata latestImageMetaData;
    // 処理中の画像とメタデータを保持するための変数
    @GuardedBy("this")
    private ByteBuffer processingImage;

    @GuardedBy("this")
    private FrameMetadata processingMetaData;

    /**
     * VisionProcessorBaseのコンストラクタです。
     */
    protected VisionProcessorBase(Context context) {
        activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        executor = new ScopedExecutor(TaskExecutors.MAIN_THREAD);
        fpsTimer.scheduleAtFixedRate(
                new TimerTask() {
                    @Override
                    public void run() {
                        framesPerSecond = frameProcessedInOneSecondInterval;
                        frameProcessedInOneSecondInterval = 0;
                    }
                },
                /* delay= */ 0,
                /* period= */ 1000);
    }

    // -----------------単一の静止画像の処理のためのコード----------------------------------------
//    @Override
    public void processBitmap(Bitmap bitmap, final GraphicOverlay graphicOverlay) {
        long frameStartMs = SystemClock.elapsedRealtime();

        // ML KitのMlImageが有効な場合、BitmapをMlImageに変換して検出をリクエストする
        if (isMlImageEnabled(graphicOverlay.getContext())) {
            MlImage mlImage = new BitmapMlImageBuilder(bitmap).build();
            requestDetectInImage(
                    mlImage,
                    graphicOverlay,
                    /* originalCameraImage= */ null,
                    /* shouldShowFps= */ false,
                    frameStartMs);
            mlImage.close();

            return;
        }

        // MlImageが無効な場合、InputImageを作成して検出をリクエストする
        requestDetectInImage(
                InputImage.fromBitmap(bitmap, 0),
                graphicOverlay,
                /* originalCameraImage= */ null,
                /* shouldShowFps= */ false,
                frameStartMs);
    }

    // -----------------Camera1 APIからのライブプレビューフレームの処理のためのコード-----------------------
    @Override
    public synchronized void processByteBuffer(
            ByteBuffer data, final FrameMetadata frameMetadata, final GraphicOverlay graphicOverlay) {
        latestImage = data;
        latestImageMetaData = frameMetadata;
        if (processingImage == null && processingMetaData == null) {
            processLatestImage(graphicOverlay);
        }
    }

    // 最新のフレームを処理するためのメソッド
    private synchronized void processLatestImage(final GraphicOverlay graphicOverlay) {
        processingImage = latestImage;
        processingMetaData = latestImageMetaData;
        latestImage = null;
        latestImageMetaData = null;
        if (processingImage != null && processingMetaData != null && !isShutdown) {
            processImage(processingImage, processingMetaData, graphicOverlay);
        }
    }

    // フレームの処理を行うメソッド
    private void processImage(
            ByteBuffer data, final FrameMetadata frameMetadata, final GraphicOverlay graphicOverlay) {
        long frameStartMs = SystemClock.elapsedRealtime();

        // ライブビューポートがオンの場合、不要なビットマップ作成をスキップする
        Bitmap bitmap =
                PreferenceUtils.isCameraLiveViewportEnabled(graphicOverlay.getContext())
                        ? null
                        : BitmapUtils.getBitmap(data, frameMetadata);

        // ML KitのMlImageが有効な場合、ByteBufferをMlImageに変換して検出をリクエストする
        if (isMlImageEnabled(graphicOverlay.getContext())) {
            MlImage mlImage =
                    new ByteBufferMlImageBuilder(
                            data,
                            frameMetadata.getWidth(),
                            frameMetadata.getHeight(),
                            MlImage.IMAGE_FORMAT_NV21)
                            .setRotation(frameMetadata.getRotation())
                            .build();

            // MlImageを使用して検出をリクエストする
            requestDetectInImage(mlImage, graphicOverlay, bitmap, /* shouldShowFps= */ true, frameStartMs)
                    .addOnSuccessListener(executor, results -> processLatestImage(graphicOverlay));

            // MlImageをクローズする（オプション）
            mlImage.close();
            return;
        }

        // MlImageが無効な場合、ByteBufferからInputImageを作成して検出をリクエストする
        requestDetectInImage(
                InputImage.fromByteBuffer(
                        data,
                        frameMetadata.getWidth(),
                        frameMetadata.getHeight(),
                        frameMetadata.getRotation(),
                        InputImage.IMAGE_FORMAT_NV21),
                graphicOverlay,
                bitmap,
                /* shouldShowFps= */ true,
                frameStartMs)
                .addOnSuccessListener(executor, results -> processLatestImage(graphicOverlay));
    }

    // -----------------CameraX APIからのライブプレビューフレームの処理のためのコード-----------------------
//    @Override
    @RequiresApi(VERSION_CODES.LOLLIPOP)
    @ExperimentalGetImage
    public void processImageProxy(ImageProxy image, GraphicOverlay graphicOverlay) {
        long frameStartMs = SystemClock.elapsedRealtime();
        // シャットダウン中であれば、ImageProxyをクローズして終了する
        if (isShutdown) {
            image.close();
            return;
        }

        Bitmap bitmap = null;
        if (!PreferenceUtils.isCameraLiveViewportEnabled(graphicOverlay.getContext())) {
            // ライブビューポートがオフの場合、Bitmapを取得する
            bitmap = BitmapUtils.getBitmap(image);
        }

        // ML KitのMlImageが有効な場合、ImageProxyをMlImageに変換して検出をリクエストする
        if (isMlImageEnabled(graphicOverlay.getContext())) {
            MlImage mlImage =
                    new MediaMlImageBuilder(image.getImage())
                            .setRotation(image.getImageInfo().getRotationDegrees())
                            .build();

            // MlImageを使用して検出をリクエストする
            requestDetectInImage(
                    mlImage,
                    graphicOverlay,
                    /* originalCameraImage= */ bitmap,
                    /* shouldShowFps= */ true,
                    frameStartMs)
                    // カメラX解析ユースケースからのイメージの場合、使用が終わったらimage.close()を呼び出す必要があります。
                    // そうしないと、新しいイメージを受け取れないか、カメラが停止する可能性があります。
                    // 現在、MlImageは直接ImageProxyをサポートしていないため、ここでImageProxy.close()を呼び出す必要があります。
                    .addOnCompleteListener(results -> image.close());
            return;
        }

        // ML KitのMlImageが無効な場合、ImageProxyから直接InputImageを作成して検出をリクエストする
        requestDetectInImage(
                InputImage.fromMediaImage(image.getImage(), image.getImageInfo().getRotationDegrees()),
                graphicOverlay,
                /* originalCameraImage= */ bitmap,
                /* shouldShowFps= */ true,
                frameStartMs)
                // カメラX解析ユースケースからのイメージの場合、使用が終わったらimage.close()を呼び出す必要があります。
                // そうしないと、新しいイメージを受け取れないか、カメラが停止する可能性があります。
                .addOnCompleteListener(results -> image.close());
    }

    // -----------------共通の処理ロジック-------------------------------------------------------
    private Task<T> requestDetectInImage(
            final InputImage image,
            final GraphicOverlay graphicOverlay,
            @Nullable final Bitmap originalCameraImage,
            boolean shouldShowFps,
            long frameStartMs) {
        // 検出結果のリスナーを設定して検出を実行する
        return setUpListener(
                detectInImage(image), graphicOverlay, originalCameraImage, shouldShowFps, frameStartMs);
    }


    private Task<T> requestDetectInImage(
            final MlImage image,
            final GraphicOverlay graphicOverlay,
            @Nullable final Bitmap originalCameraImage,
            boolean shouldShowFps,
            long frameStartMs) {
        // 検出結果のリスナーを設定して検出を実行する
        return setUpListener(
                detectInImage(image), graphicOverlay, originalCameraImage, shouldShowFps, frameStartMs);
    }

    private Task<T> setUpListener(
            Task<T> task,
            final GraphicOverlay graphicOverlay,
            @Nullable final Bitmap originalCameraImage,
            boolean shouldShowFps,
            long frameStartMs) {
        final long detectorStartMs = SystemClock.elapsedRealtime();
        return task.addOnSuccessListener(
                        executor,
                        results -> {
                            long endMs = SystemClock.elapsedRealtime();
                            long currentFrameLatencyMs = endMs - frameStartMs;
                            long currentDetectorLatencyMs = endMs - detectorStartMs;
                            // フレーム処理の統計情報を更新する
                            if (numRuns >= 500) {
                                resetLatencyStats();
                            }
                            numRuns++;
                            frameProcessedInOneSecondInterval++;
                            totalFrameMs += currentFrameLatencyMs;
                            maxFrameMs = max(currentFrameLatencyMs, maxFrameMs);
                            minFrameMs = min(currentFrameLatencyMs, minFrameMs);
                            totalDetectorMs += currentDetectorLatencyMs;
                            maxDetectorMs = max(currentDetectorLatencyMs, maxDetectorMs);
                            minDetectorMs = min(currentDetectorLatencyMs, minDetectorMs);

                            // 1秒ごとに推論情報をログに記録する。frameProcessedInOneSecondIntervalが1と等しい場合、
                            // これは現在の秒の最初に処理されるフレームを意味します。
                            if (frameProcessedInOneSecondInterval == 1) {
                                Log.d(TAG, "Num of Runs: " + numRuns);
                                Log.d(
                                        TAG,
                                        "Frame latency: max="
                                                + maxFrameMs
                                                + ", min="
                                                + minFrameMs
                                                + ", avg="
                                                + totalFrameMs / numRuns);
                                Log.d(
                                        TAG,
                                        "Detector latency: max="
                                                + maxDetectorMs
                                                + ", min="
                                                + minDetectorMs
                                                + ", avg="
                                                + totalDetectorMs / numRuns);
                                // システムの利用可能なメモリ量をログに記録する
                                MemoryInfo mi = new MemoryInfo();
                                activityManager.getMemoryInfo(mi);
                                long availableMegs = mi.availMem / 0x100000L;
                                Log.d(TAG, "Memory available in system: " + availableMegs + " MB");
                            }

                            // オリジナルのカメラ画像をグラフィックオーバーレイに追加する
                            graphicOverlay.clear();
                            if (originalCameraImage != null) {
                                graphicOverlay.add(new CameraImageGraphic(graphicOverlay, originalCameraImage));
                            }
                            // 成功時の処理を呼び出す
                            VisionProcessorBase.this.onSuccess(results, graphicOverlay);
                            if (Build.VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
                                if (!PreferenceUtils.shouldHideDetectionInfo(graphicOverlay.getContext())) {
                                    // デバッグ情報の表示
                                }
                            }
                            // グラフィックオーバーレイを更新して再描画する
                            graphicOverlay.postInvalidate();
                        })
                .addOnFailureListener(
                        executor,
                        e -> {
                            graphicOverlay.clear();
                            graphicOverlay.postInvalidate();
                            // エラーメッセージを表示する
                            String error = "Failed to process. Error: " + e.getLocalizedMessage();
                            Toast.makeText(
                                            graphicOverlay.getContext(),
                                            error + "\nCause: " + e.getCause(),
                                            Toast.LENGTH_SHORT)
                                    .show();
                            Log.d(TAG, error);
                            e.printStackTrace();
                            // 失敗時の処理を呼び出す
                            VisionProcessorBase.this.onFailure(e);
                        });
    }

    @Override
    public void stop() {
        // Executorをシャットダウンし、処理を停止する
        executor.shutdown();

        // シャットダウンフラグを立てる
        isShutdown = true;

        // フレーム処理の統計情報をリセットする
        resetLatencyStats();

        // FPSタイマーをキャンセルする
        fpsTimer.cancel();
    }

    // フレーム処理の統計情報をリセットする
    private void resetLatencyStats() {
        numRuns = 0;
        totalFrameMs = 0;
        maxFrameMs = 0;
        minFrameMs = Long.MAX_VALUE;
        totalDetectorMs = 0;
        maxDetectorMs = 0;
        minDetectorMs = Long.MAX_VALUE;
    }

    // InputImageを使用して検出を実行する抽象メソッド
    protected abstract Task<T> detectInImage(InputImage image);

    // MlImageを使用して検出を実行するメソッド（デフォルトではエラーを返す）
    protected Task<T> detectInImage(MlImage image) {
        return Tasks.forException(
                new MlKitException(
                        "MlImage is currently not demonstrated for this feature",
                        MlKitException.INVALID_ARGUMENT));
    }

    // 検出成功時の処理を定義する抽象メソッド
    protected abstract void onSuccess(@NonNull T results, @NonNull GraphicOverlay graphicOverlay);

    // 検出失敗時の処理を定義する抽象メソッド
    protected abstract void onFailure(@NonNull Exception e);

    // MlImageが有効化されているかどうかを返すメソッド（デフォルトでは無効）
    protected boolean isMlImageEnabled(Context context) {
        return false;
    }
}
