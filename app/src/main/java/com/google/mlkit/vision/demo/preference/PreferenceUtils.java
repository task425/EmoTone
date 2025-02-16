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

package com.google.mlkit.vision.demo.preference;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build.VERSION_CODES;
import android.preference.PreferenceManager;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;

import com.google.android.gms.common.images.Size;
import com.google.common.base.Preconditions;
import com.google.mlkit.vision.demo.CameraSource;
import com.google.mlkit.vision.demo.CameraSource.SizePair;
import com.google.mlkit.vision.demo.R;
import com.google.mlkit.vision.face.FaceDetectorOptions;

/**
 * 共有プリファレンスを取得するためのユーティリティクラス。
 */
public class PreferenceUtils {

    // カメラのプレビューサイズを取得する
    @Nullable
    public static SizePair getCameraPreviewSizePair(Context context, int cameraId) {
        Preconditions.checkArgument(
                cameraId == CameraSource.CAMERA_FACING_BACK
                        || cameraId == CameraSource.CAMERA_FACING_FRONT);
        String previewSizePrefKey;
        String pictureSizePrefKey;
        if (cameraId == CameraSource.CAMERA_FACING_BACK) {
            previewSizePrefKey = context.getString(R.string.pref_key_rear_camera_preview_size);
            pictureSizePrefKey = context.getString(R.string.pref_key_rear_camera_picture_size);
        } else {
            previewSizePrefKey = context.getString(R.string.pref_key_front_camera_preview_size);
            pictureSizePrefKey = context.getString(R.string.pref_key_front_camera_picture_size);
        }

        try {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            return new SizePair(
                    Size.parseSize(sharedPreferences.getString(previewSizePrefKey, null)),
                    Size.parseSize(sharedPreferences.getString(pictureSizePrefKey, null)));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Android 5.0 (Lollipop) 以上が必要な処理についての条件付き注釈です。
     */
    @RequiresApi(VERSION_CODES.LOLLIPOP)

    //検出情報を非表示にするかどうかを取得する。
    public static boolean shouldHideDetectionInfo(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String prefKey = context.getString(R.string.pref_key_info_hide);
        return sharedPreferences.getBoolean(prefKey, false);
    }

    // 顔検出オプションを取得する
    public static FaceDetectorOptions getFaceDetectorOptions(Context context) {
        ///変更しました NONE→ALL
        //顔の「ランドマーク」（目、耳、鼻、頬、口）を識別するかどうか。
        int landmarkMode =
                getModeTypePreferenceValue(
                        context,
                        R.string.pref_key_live_preview_face_detection_landmark_mode,
                        FaceDetectorOptions.LANDMARK_MODE_ALL);
        //顔の特徴の輪郭を検出するかどうか。輪郭は、画像内で最も目立つ顔についてのみ検出されます。
        int contourMode =
                getModeTypePreferenceValue(
                        context,
                        R.string.pref_key_live_preview_face_detection_contour_mode,
                        FaceDetectorOptions.CONTOUR_MODE_ALL);
        ///変更しました　NONE→ALL
        //顔を「ほほ笑んでいる」や「目を開けている」などのカテゴリに分類するかどうか。
        int classificationMode =
                getModeTypePreferenceValue(
                        context,
                        R.string.pref_key_live_preview_face_detection_classification_mode,
                        FaceDetectorOptions.CLASSIFICATION_MODE_ALL);
        ///変更しました FAST→ACCURATE
        //顔を検出する際に速度を優先するか精度を優先するか。
        int performanceMode =
                getModeTypePreferenceValue(
                        context,
                        R.string.pref_key_live_preview_face_detection_performance_mode,
                        FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean enableFaceTracking =
                sharedPreferences.getBoolean(
                        context.getString(R.string.pref_key_live_preview_face_detection_face_tracking), false);
        float minFaceSize =
                Float.parseFloat(
                        sharedPreferences.getString(
                                context.getString(R.string.pref_key_live_preview_face_detection_min_face_size),
                                "0.1"));

        FaceDetectorOptions.Builder optionsBuilder =
                new FaceDetectorOptions.Builder()
                        .setLandmarkMode(landmarkMode)
                        .setContourMode(contourMode)
                        .setClassificationMode(classificationMode)
                        .setPerformanceMode(performanceMode)
                        .setMinFaceSize(minFaceSize);
        if (enableFaceTracking) {
            optionsBuilder.enableTracking();
        }
        return optionsBuilder.build();
    }

    /**
     * モードタイプのプリファレンスは、{@link android.preference.ListPreference}によってバックアップされており、
     * エントリの値を文字列型で保存するだけなので、文字列から整数に変換する必要があります。
     */
    private static int getModeTypePreferenceValue(
            Context context, @StringRes int prefKeyResId, int defaultValue) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String prefKey = context.getString(prefKeyResId);
        return Integer.parseInt(sharedPreferences.getString(prefKey, String.valueOf(defaultValue)));
    }

    // カメラのライブビューポートが有効かどうかを取得する。
    //有効な場合はtrue、それ以外の場合はfalse
    public static boolean isCameraLiveViewportEnabled(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String prefKey = context.getString(R.string.pref_key_camera_live_viewport);
        return sharedPreferences.getBoolean(prefKey, false);
    }

    /**
     * プライベートなコンストラクタ。このクラスのインスタンス化を防止します。
     */
    private PreferenceUtils() {
    }
}
