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

package com.google.mlkit.vision.demo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;

import com.google.android.gms.common.images.Size;
import com.google.mlkit.vision.demo.preference.PreferenceUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * カメラを管理し、UIの更新（追加のグラフィックスのオーバーレイや追加情報の表示など）を可能にします。
 * このクラスはカメラからプレビューフレームを指定された速度で受け取り、それらのフレームを子クラスのディテクター
 * /クラシファイアにできるだけ高速に処理します。
 */
public class CameraSource {
    @SuppressLint("InlinedApi")
    public static final int CAMERA_FACING_BACK = CameraInfo.CAMERA_FACING_BACK;

    @SuppressLint("InlinedApi")
    public static final int CAMERA_FACING_FRONT = CameraInfo.CAMERA_FACING_FRONT;

    public static final int IMAGE_FORMAT = ImageFormat.NV21;
    public static final int DEFAULT_REQUESTED_CAMERA_PREVIEW_WIDTH = 480;
    public static final int DEFAULT_REQUESTED_CAMERA_PREVIEW_HEIGHT = 360;

    private static final String TAG = "MIDemoApp:CameraSource";

    /**
     * ダミーのサーフェステクスチャには選択した名前を割り当てる必要があります。
     * OpenGLコンテキストを使用しないため、ここで任意のIDを選択できます。
     * ダミーのサーフェステクスチャはクレイジーなハックではなく、実際にはカメラチームがプレビューなしでカメラを使用する方法です。
     */
    private static final int DUMMY_TEXTURE_NAME = 100;

    /**
     * プレビューサイズのアスペクト比と画像サイズのアスペクト比の絶対差がこの許容範囲未満の場合、
     * それらは同じアスペクト比と見なされます。
     */
    private static final float ASPECT_RATIO_TOLERANCE = 0.01f;

    protected Activity activity;

    private Camera camera;

    //内カメラ、外カメラの初期設定
    private int facing = CAMERA_FACING_FRONT;

    /**
     * デバイスの回転、およびデバイスからキャプチャされたプレビューイメージの回転角度
     */
    private int rotationDegrees;

    private Size previewSize;

    private static final float REQUESTED_FPS = 30.0f;
    private static final boolean REQUESTED_AUTO_FOCUS = true;

    // このインスタンスは、その下にあるリソースがGCされないように保持する必要があります。
    // 使用されていない場合でも、作成するメソッドの外部で使用されない限り、ハードな参照を保持する必要があります。
    private SurfaceTexture dummySurfaceTexture;

    private final GraphicOverlay graphicOverlay;

    /**
     * フレームが利用可能になると、ディテクターにフレームを渡すための専用のスレッドと関連する実行可能オブジェクト。
     */
    private Thread processingThread;

    private final FrameProcessingRunnable processingRunnable;
    private final Object processorLock = new Object();

    private VisionImageProcessor frameProcessor;

    /**
     * カメラから受け取ったバイト配列とそれに関連するバイトバッファの変換に使用されるマップ。
     * バイトバッファを内部で使用するのは、後でネイティブコードに効率的に呼び出すためのより効率的な方法です（潜在的なコピーを回避します）。
     *
     * <p><b>注意:</b> 配列のequals、hashCode、toStringメソッドの動作は、役に立たず予想外です。
     * IdentityHashMapはキーに対して等号('==')チェックを強制します。
     */
    private final IdentityHashMap<byte[], ByteBuffer> bytesToByteBuffer = new IdentityHashMap<>();

    public CameraSource(Activity activity, GraphicOverlay overlay) {
        this.activity = activity;
        graphicOverlay = overlay;
        graphicOverlay.clear();
        processingRunnable = new FrameProcessingRunnable();
    }

    // ==============================================================================================
    // Public
    // ==============================================================================================

    /**
     * カメラを停止し、カメラと基礎となるディテクターのリソースを解放します。
     */
    public void release() {
        synchronized (processorLock) {
            stop();
            cleanScreen();

            if (frameProcessor != null) {
                frameProcessor.stop();
            }
        }
    }

    /**
     * カメラを開き、プレビューフレームを基礎となるディテクターに送信し始めます。
     * プレビューフレームは表示されません。
     *
     * @throws IOException カメラのプレビューテクスチャや表示の初期化に失敗した場合
     */
    @RequiresPermission(Manifest.permission.CAMERA)
    public synchronized CameraSource start() throws IOException {
        if (camera != null) {
            return this;
        }

        camera = createCamera();
        dummySurfaceTexture = new SurfaceTexture(DUMMY_TEXTURE_NAME);
        camera.setPreviewTexture(dummySurfaceTexture);
        camera.startPreview();

        processingThread = new Thread(processingRunnable);
        processingRunnable.setActive(true);
        processingThread.start();
        return this;
    }

    /**
     * カメラを開き、プレビューフレームを基礎となるディテクターに送信し始めます。指定された
     * サーフェスホルダーはプレビューに使用され、フレームがユーザーに表示されます。
     *
     * @param surfaceHolder プレビューフレームに使用するサーフェスホルダー
     * @throws IOException サプライされたサーフェスホルダーをプレビュー表示に使用できなかった場合
     */
    @RequiresPermission(Manifest.permission.CAMERA)
    public synchronized CameraSource start(SurfaceHolder surfaceHolder) throws IOException {
        if (camera != null) {
            return this;
        }

        camera = createCamera();
        camera.setPreviewDisplay(surfaceHolder);
        camera.startPreview();

        processingThread = new Thread(processingRunnable);
        processingRunnable.setActive(true);
        processingThread.start();
        return this;
    }

    /**
     * カメラを閉じ、フレームを基礎となるフレームディテクターに送信するのを停止します。
     *
     * <p>このカメラソースは、{@link #start()}や{@link #start(SurfaceHolder)}を呼び出すことで再起動することができます。
     *
     * <p>完全にシャットダウンし、基礎となるディテクターのリソースを解放するには、{@link #release()}を呼び出してください。
     */
    public synchronized void stop() {
        processingRunnable.setActive(false);
        if (processingThread != null) {
            try {
                // スレッドが完了するまで待機して、同時に複数のスレッドが実行されないようにします。
                // （つまり、stopの直後にすぐにstartが呼ばれる場合に起こる可能性があります）。
                processingThread.join();
            } catch (InterruptedException e) {
                Log.d(TAG, "Frame processing thread interrupted on release.");
            }
            processingThread = null;
        }

        if (camera != null) {
            camera.stopPreview();
            camera.setPreviewCallbackWithBuffer(null);
            try {
                camera.setPreviewTexture(null);
                dummySurfaceTexture = null;
                camera.setPreviewDisplay(null);
            } catch (Exception e) {
                Log.e(TAG, "Failed to clear camera preview: " + e);
            }
            camera.release();
            camera = null;
        }

        // 使用されなくなったイメージバッファへの参照を解放します。
        bytesToByteBuffer.clear();
    }

    /**
     * 現在カメラで使用されているプレビューサイズを返します。
     */
    public Size getPreviewSize() {
        return previewSize;
    }

    /**
     * 選択されたカメラを返します。{@link #CAMERA_FACING_BACK}または{@link #CAMERA_FACING_FRONT}のいずれかです。
     */
    public int getCameraFacing() {
        return facing;
    }

    /**
     * カメラをオープンし、ユーザーの設定を適用します。
     *
     * @throws IOException カメラが見つからないか、プレビューを処理できない場合に発生します
     */
    @SuppressLint("InlinedApi")
    private Camera createCamera() throws IOException {
        int requestedCameraId = getIdForRequestedCamera(facing);
        if (requestedCameraId == -1) {
            throw new IOException("Could not find requested camera.");
        }
        Camera camera = Camera.open(requestedCameraId);

        SizePair sizePair = PreferenceUtils.getCameraPreviewSizePair(activity, requestedCameraId);
        if (sizePair == null) {
            sizePair =
                    selectSizePair(
                            camera,
                            DEFAULT_REQUESTED_CAMERA_PREVIEW_WIDTH,
                            DEFAULT_REQUESTED_CAMERA_PREVIEW_HEIGHT);
        }

        if (sizePair == null) {
            throw new IOException("Could not find suitable preview size.");
        }

        previewSize = sizePair.preview;
        Log.v(TAG, "Camera preview size: " + previewSize);

        int[] previewFpsRange = selectPreviewFpsRange(camera, REQUESTED_FPS);
        if (previewFpsRange == null) {
            throw new IOException("Could not find suitable preview frames per second range.");
        }

        Camera.Parameters parameters = camera.getParameters();

        Size pictureSize = sizePair.picture;
        if (pictureSize != null) {
            Log.v(TAG, "Camera picture size: " + pictureSize);
            parameters.setPictureSize(pictureSize.getWidth(), pictureSize.getHeight());
        }
        parameters.setPreviewSize(previewSize.getWidth(), previewSize.getHeight());
        parameters.setPreviewFpsRange(
                previewFpsRange[Camera.Parameters.PREVIEW_FPS_MIN_INDEX],
                previewFpsRange[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]);
        // OCR検出のためにYV12を使用してNV21への自動変換ロジックをテストします
        parameters.setPreviewFormat(IMAGE_FORMAT);

        setRotation(camera, parameters, requestedCameraId);

        if (REQUESTED_AUTO_FOCUS) {
            if (parameters
                    .getSupportedFocusModes()
                    .contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            } else {
                Log.i(TAG, "Camera auto focus is not supported on this device.");
            }
        }

        camera.setParameters(parameters);

        // カメラで使用するために、4つのフレームバッファが必要です：
//
//   現在検出処理中のフレームに対するバッファ
//   次の処理待ちのフレームに対するバッファ（検出完了後すぐに処理するため）
//   カメラが将来のプレビュー画像を作成するために使用する2つのバッファ
//
// 試行錯誤の結果、このコードで使用する2つのバッファに加えて、カメラが正常に動作するためにはさらに2つの空きバッファが必要です。
// おそらく、カメラは画像の取得に1つのスレッドを使用し、ユーザーコードに呼び出しを行うために別のスレッドを使用しているのかもしれません。
// 3つのバッファだけを使用すると、検出に時間がかかるとカメラが何千もの警告メッセージを表示します。
        camera.setPreviewCallbackWithBuffer(new CameraPreviewCallback());
        camera.addCallbackBuffer(createPreviewBuffer(previewSize));
        camera.addCallbackBuffer(createPreviewBuffer(previewSize));
        camera.addCallbackBuffer(createPreviewBuffer(previewSize));
        camera.addCallbackBuffer(createPreviewBuffer(previewSize));

        return camera;
    }

    /**
     * 指定された方向を向いているカメラのIDを取得します。該当するカメラが見つからない場合は-1を返します。
     *
     * @param facing カメラの方向（フロントカメラまたはリアカメラ）
     */
    private static int getIdForRequestedCamera(int facing) {
        CameraInfo cameraInfo = new CameraInfo();
        for (int i = 0; i < Camera.getNumberOfCameras(); ++i) {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == facing) {
                return i;// 該当するカメラが見つかった場合はそのIDを返す
            }
        }
        return -1;// 該当するカメラが見つからない場合は-1を返す
    }

    /**
     * 指定された幅と高さに基づいて、最適なプレビューサイズと写真サイズを選択します。
     *
     * <p>プレビューサイズのみを見つければ良いように思えますが、プレビューサイズと写真サイズは同じアスペクト比を持つ必要があるため、両方を一緒に見つける必要があります。一部のハードウェアでは、プレビューサイズのみを設定すると画像が歪んで表示される場合があります。
     *
     * @param camera        カメラからプレビューサイズを選択するためのカメラインスタンス
     * @param desiredWidth  カメラのプレビューフレームの希望する幅
     * @param desiredHeight カメラのプレビューフレームの希望する高さ
     * @return 選択されたプレビューサイズと写真サイズのペア
     */
    public static SizePair selectSizePair(Camera camera, int desiredWidth, int desiredHeight) {
        // カメラがサポートする有効なプレビューサイズのリストを生成します
        List<SizePair> validPreviewSizes = generateValidPreviewSizeList(camera);

        // 最適なサイズを選択する方法は、幅と高さの希望値と実際の値の差の合計を最小化することです。
        // これは最適なサイズを選択する唯一の方法ではありませんが、最も近いアスペクト比と最も近いピクセル面積のトレードオフを提供します。
        SizePair selectedPair = null;
        int minDiff = Integer.MAX_VALUE;
        for (SizePair sizePair : validPreviewSizes) {
            Size size = sizePair.preview;
            // 幅と高さの差の絶対値を計算し、合計して差の合計を求めます
            int diff =
                    Math.abs(size.getWidth() - desiredWidth) + Math.abs(size.getHeight() - desiredHeight);
            // 差の合計が最小となるサイズペアを選択します
            if (diff < minDiff) {
                selectedPair = sizePair;
                minDiff = diff;
            }
        }

        return selectedPair;
    }

    /**
     * プレビューサイズとそれに対応する同じアスペクト比の写真サイズを保持します。
     * 一部のデバイスで歪んだプレビュー画像を避けるためには、写真サイズをプレビューサイズと同じアスペクト比に設定する必要があります。
     * もし写真サイズがnullの場合、プレビューサイズと同じアスペクト比の写真サイズは存在しないことを意味します。
     */
    public static class SizePair {
        public final Size preview; // プレビューサイズ
        @Nullable
        public final Size picture;  // 写真サイズ（nullの場合もあります）

        SizePair(Camera.Size previewSize, @Nullable Camera.Size pictureSize) {
            preview = new Size(previewSize.width, previewSize.height);
            picture = pictureSize != null ? new Size(pictureSize.width, pictureSize.height) : null;
        }

        public SizePair(Size previewSize, @Nullable Size pictureSize) {
            preview = previewSize;
            picture = pictureSize;
        }
    }

    /**
     * 有効なプレビューサイズのリストを生成します。
     * プレビューサイズに対応する同じアスペクト比の写真サイズがない場合、プレビューサイズは使用できません。
     * 同じアスペクト比の写真サイズがある場合、プレビューサイズと写真サイズをペアにします。
     *
     * <p>この処理は、静止画を使用しない場合でも、選択したプレビューサイズと同じアスペクト比の静止画サイズを設定する必要があるため、必要です。
     * そうしないと、一部のデバイスでプレビュー画像が歪んでしまいます。
     */
    public static List<SizePair> generateValidPreviewSizeList(Camera camera) {
        Camera.Parameters parameters = camera.getParameters();
        List<Camera.Size> supportedPreviewSizes = parameters.getSupportedPreviewSizes();// サポートされているプレビューサイズのリスト
        List<Camera.Size> supportedPictureSizes = parameters.getSupportedPictureSizes();  // サポートされている写真サイズのリスト
        List<SizePair> validPreviewSizes = new ArrayList<>();  // 有効なプレビューサイズのリスト
        for (Camera.Size previewSize : supportedPreviewSizes) {
            float previewAspectRatio = (float) previewSize.width / (float) previewSize.height; // プレビューサイズのアスペクト比

            // 写真サイズを順にループして、より高い解像度を優先します。
            // これにより、後でフル解像度の写真を撮影できるように最高解像度を選択します。
            for (Camera.Size pictureSize : supportedPictureSizes) {
                float pictureAspectRatio = (float) pictureSize.width / (float) pictureSize.height; // 写真サイズのアスペクト比
                if (Math.abs(previewAspectRatio - pictureAspectRatio) < ASPECT_RATIO_TOLERANCE) {
                    validPreviewSizes.add(new SizePair(previewSize, pictureSize));
                    break;
                }
            }
        }

        // もし、どのプレビューサイズにも同じアスペクト比の写真サイズが存在しない場合、
// 全てのプレビューサイズを許可し、カメラが処理できることを期待します。
// おそらくそれは起こりにくいですが、それにも対応します。
        if (validPreviewSizes.size() == 0) {
            Log.w(TAG, "No preview sizes have a corresponding same-aspect-ratio picture size");
            for (Camera.Size previewSize : supportedPreviewSizes) {
                // nullの写真サイズは、写真サイズを設定しないことを示します。
                validPreviewSizes.add(new SizePair(previewSize, null));
            }
        }

        return validPreviewSizes;
    }

    /**
     * ディレードしたフレームレートに基づいて、最適なプレビューフレームレート範囲を選択します。
     *
     * @param camera            カメラ
     * @param desiredPreviewFps カメラプレビューフレームの希望フレームレート
     * @return 選択されたプレビューフレームレート範囲
     */
    @SuppressLint("InlinedApi")
    private static int[] selectPreviewFpsRange(Camera camera, float desiredPreviewFps) {
        // カメラAPIは、浮動小数点のフレームレートではなく、1000倍された整数を使用します。
        int desiredPreviewFpsScaled = (int) (desiredPreviewFps * 1000.0f);

        // 上限が希望するfpsにできるだけ近く、下限が低光条件下でフレームを適切に露出するためにできるだけ小さい範囲を選択します。
// ただし、これにより、希望する値が範囲外にある可能性があります。
// たとえば、希望するフレームレートが30.5の場合、(30, 30)の範囲はおそらく(30, 40)よりも望ましいです。
        int[] selectedFpsRange = null;
        int minUpperBoundDiff = Integer.MAX_VALUE;
        int minLowerBound = Integer.MAX_VALUE;
        List<int[]> previewFpsRangeList = camera.getParameters().getSupportedPreviewFpsRange();
        for (int[] range : previewFpsRangeList) {
            int upperBoundDiff =
                    Math.abs(desiredPreviewFpsScaled - range[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]);
            int lowerBound = range[Camera.Parameters.PREVIEW_FPS_MIN_INDEX];
            if (upperBoundDiff <= minUpperBoundDiff && lowerBound <= minLowerBound) {
                selectedFpsRange = range;
                minUpperBoundDiff = upperBoundDiff;
                minLowerBound = lowerBound;
            }
        }
        return selectedFpsRange;
    }

    /**
     * カメラのIDに基づいて正しい回転を計算し、パラメータに回転を設定します。
     * また、カメラのディスプレイの向きと回転も設定します。
     *
     * @param parameters 回転を設定するカメラのパラメータ
     * @param cameraId   回転の基準となるカメラのID
     */
    private void setRotation(Camera camera, Camera.Parameters parameters, int cameraId) {
        WindowManager windowManager = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
        int degrees = 0;
        int rotation = windowManager.getDefaultDisplay().getRotation();
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
            default:
                Log.e(TAG, "Bad rotation value: " + rotation);
        }

        CameraInfo cameraInfo = new CameraInfo();
        Camera.getCameraInfo(cameraId, cameraInfo);

        int displayAngle;
        if (cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT) {
            this.rotationDegrees = (cameraInfo.orientation + degrees) % 360;
            displayAngle = (360 - this.rotationDegrees) % 360; // ミラーリングされているため補正する
        } else { // 背面カメラ
            this.rotationDegrees = (cameraInfo.orientation - degrees + 360) % 360;
            displayAngle = this.rotationDegrees;
        }
        Log.d(TAG, "Display rotation is: " + rotation);
        Log.d(TAG, "Camera face is: " + cameraInfo.facing);
        Log.d(TAG, "Camera rotation is: " + cameraInfo.orientation);
        // ImageMetadataが受け入れる度数のいずれかであるべき値です：0、90、180、または270。
        Log.d(TAG, "RotationDegrees is: " + this.rotationDegrees);

        camera.setDisplayOrientation(displayAngle);
        parameters.setRotation(this.rotationDegrees);
    }

    /**
     * カメラのプレビューコールバック用に、1つのバッファを作成します。
     * バッファのサイズは、カメラのプレビューサイズとカメラ画像のフォーマットに基づいています。
     *
     * @return 現在のカメラ設定に適したサイズの新しいプレビューバッファ
     */
    @SuppressLint("InlinedApi")
    private byte[] createPreviewBuffer(Size previewSize) {
        int bitsPerPixel = ImageFormat.getBitsPerPixel(IMAGE_FORMAT);
        long sizeInBits = (long) previewSize.getHeight() * previewSize.getWidth() * bitsPerPixel;
        int bufferSize = (int) Math.ceil(sizeInBits / 8.0d) + 1;

        // .allocate()を使用する代わりに、この方法でバイト配列を作成してラップすることで、
        // 使用できる配列があることが保証されるはずです。
        byte[] byteArray = new byte[bufferSize];
        ByteBuffer buffer = ByteBuffer.wrap(byteArray);
        if (!buffer.hasArray() || (buffer.array() != byteArray)) {
            // これは起こる可能性はほとんどないと思いますが、もし起こった場合は、後続の検出器にプレビューコンテンツを渡すことはできません。
            throw new IllegalStateException("Failed to create valid buffer for camera source.");
        }

        bytesToByteBuffer.put(byteArray, buffer);
        return byteArray;
    }

    // ==============================================================================================
// フレーム処理
// ==============================================================================================

    /**
     * カメラが新しいプレビューフレームを持つと呼ばれます。
     */
    private class CameraPreviewCallback implements Camera.PreviewCallback {
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            processingRunnable.setNextFrame(data, camera);
        }
    }

    public void setMachineLearningFrameProcessor(VisionImageProcessor processor) {
        synchronized (processorLock) {
            cleanScreen();
            if (frameProcessor != null) {
                frameProcessor.stop();
            }
            frameProcessor = processor;
        }
    }

    /**
     * このRunnableは、下位の受信機へのアクセスを制御し、利用可能なフレームがカメラから受信された場合に処理を呼び出します。
     * これは、不要なコンテキストの切り替えや次のフレームの待機なしに、フレームでの検出をできるだけ高速に実行するために設計されています。
     *
     * <p>検出がフレーム上で実行されている間、カメラから新しいフレームが受信される可能性があります。
     * これらのフレームが到着すると、直前に受信したフレームは保留中として保持されます。
     * 直前のフレームの検出とそれに関連する処理が前のフレームに対して完了すると、
     * 最も最近に受信したフレームでの検出がすぐに同じスレッドで開始されます。
     */
    private class FrameProcessingRunnable implements Runnable {

        // 以下のメンバ変数のアクセスを保護するためにこのロックを使用します。
        private final Object lock = new Object();
        private boolean active = true;

        // これらの保留中の変数には、処理待ちの新しいフレームに関連する状態が格納されます。
        private ByteBuffer pendingFrameData;

        FrameProcessingRunnable() {
        }

        /**
         * Runnableをアクティブ/非アクティブにします。ブロックされているスレッドに続行するようにシグナルを送信します。
         */
        void setActive(boolean active) {
            synchronized (lock) {
                this.active = active;
                lock.notifyAll();
            }
        }

        /**
         * カメラから受け取ったフレームデータを設定します。
         * これにより、以前に使用されていないフレームバッファ（存在する場合）がカメラに戻され、
         * 将来の使用のためにフレームデータへの保留参照が保持されます。
         */
        @SuppressWarnings("ByteBufferBackingArray")
        void setNextFrame(byte[] data, Camera camera) {
            synchronized (lock) {
                if (pendingFrameData != null) {
                    camera.addCallbackBuffer(pendingFrameData.array());
                    pendingFrameData = null;
                }

                if (!bytesToByteBuffer.containsKey(data)) {
                    Log.d(
                            TAG,
                            "Skipping frame. Could not find ByteBuffer associated with the image "
                                    + "data from the camera.");
                    return;
                }

                pendingFrameData = bytesToByteBuffer.get(data);

                // プロセッサスレッドが次のフレームを待機している場合は通知します（後述）。
                lock.notifyAll();
            }
        }

        /**
         * 処理スレッドがアクティブである限り、連続してフレーム上で検出を実行します。
         * 次の保留中のフレームはすぐに利用可能であるか、まだ受信されていません。
         * 利用可能になると、フレーム情報をローカル変数に転送し、そのフレーム上で検出を実行します。
         * 一時停止せずにすぐに次のフレームに戻ります。
         *
         * <p>検出がカメラからの新しいフレームの間隔よりも長い時間かかる場合、
         * このループはフレームの待機なしに実行されるため、コンテキストの切り替えやフレーム取得時間の待機を回避します。
         *
         * <p>もしCPU使用率が望ましくないほど高い場合は、上記のFPS設定を減らしてフレーム間にアイドル時間を設ける必要があります。
         */
        @SuppressLint("InlinedApi")
        @SuppressWarnings({"GuardedBy", "ByteBufferBackingArray"})
        @Override
        public void run() {
            ByteBuffer data;

            while (true) {
                synchronized (lock) {
                    while (active && (pendingFrameData == null)) {
                        try {
                            // まだフレームがないため、カメラからの次のフレームを待機します。
                            lock.wait();
                        } catch (InterruptedException e) {
                            Log.d(TAG, "Frame processing loop terminated.", e);
                            return;
                        }
                    }

                    if (!active) {
                        // このカメラソースが停止または解放されたら、ループを終了します。
                        // この場所でチェックすることで、上記のwait()の直後にsetActive(false)が呼び出されて
                        // ループの終了がトリガーされる場合に対応します。
                        return;
                    }

                    // pendingFrameDataをローカルに保持し、このデータを検出に使用した後、
                    // pendingFrameDataをクリアしてこのバッファが使用されないようにします。
                    data = pendingFrameData;
                    pendingFrameData = null;
                }

                // 以下のコードは同期の外で実行する必要があります。
                // これにより、カメラが現在のフレームで検出中に保留中のフレームを追加できるようになります。

                try {
                    synchronized (processorLock) {
                        frameProcessor.processByteBuffer(
                                data,
                                new FrameMetadata.Builder()
                                        .setWidth(previewSize.getWidth())
                                        .setHeight(previewSize.getHeight())
                                        .setRotation(rotationDegrees)
                                        .build(),
                                graphicOverlay);
                    }
                } catch (Exception t) {
                    Log.e(TAG, "Exception thrown from receiver.", t);
                } finally {
                    camera.addCallbackBuffer(data.array());
                }
            }
        }
    }

    /**
     * graphicOverlayをクリーンアップし、子クラスも必要なクリーンアップを行います。
     */
    private void cleanScreen() {
        graphicOverlay.clear();
    }
}
