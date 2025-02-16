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

package com.google.mlkit.vision.demo.java.facedetector;

import android.content.Context;
import android.util.Log;
import android.widget.SeekBar;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.demo.GraphicOverlay;
import com.google.mlkit.vision.demo.R;
import com.google.mlkit.vision.demo.java.LivePreviewActivity;
import com.google.mlkit.vision.demo.java.VisionProcessorBase;
import com.google.mlkit.vision.demo.preference.PreferenceUtils;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.util.List;

/**
 * Face Detector Demo.
 */
public class FaceDetectorProcessor extends VisionProcessorBase<List<Face>> {
    private LivePreviewActivity lpa = null;/*-- LivePreviewActivityと繋げる --*/
    private static final String TAG = "FaceDetectorProcessor";

    private final FaceDetector detector;
    private Float ff;
    public SeekBar seekBarsmile;
    int count = 0;

    /**
     * FaceDetectorProcessorのコンストラクタです。
     *
     * @param context コンテキスト
     */
    public FaceDetectorProcessor(Context context) {
        super(context);
        lpa = (LivePreviewActivity) context;
        FaceDetectorOptions faceDetectorOptions = PreferenceUtils.getFaceDetectorOptions(context);
        Log.v(MANUAL_TESTING_LOG, "Face detector options: " + faceDetectorOptions);
        detector = FaceDetection.getClient(faceDetectorOptions);
    }

    @Override
    public void stop() {
        super.stop();
        detector.close();
    }

    @Override
    protected Task<List<Face>> detectInImage(InputImage image) {
        return detector.process(image);
    }

    /**
     * 顔の検出が成功した場合に呼び出されるメソッドです。
     *
     * @param faces           検出された顔のリスト
     * @param graphicOverlay  グラフィックオーバーレイ
     */
    @Override
    protected void onSuccess(@NonNull List<Face> faces, @NonNull GraphicOverlay graphicOverlay) {
        for (Face face : faces) {
            graphicOverlay.add(new FaceGraphic(graphicOverlay, face));

            // LivePreviewActivityに笑顔度の結果を送信します。
            ff = face.getSmilingProbability();
            seekBarsmile = this.lpa.seekBarSmile;
            seekBarsmile.findViewById(R.id.seekbarSmile);
            int vb;
            if(count > 10){
                if(ff != null) {

                    float ex = ff * 100;
                    Log.d("MONE", String.valueOf(ex));
                    vb=(int)ex;
                    Log.d("MONE",String.valueOf(vb));
                } else {
                    vb=0;
                }
                Log.w("nnnnn",String.valueOf(vb));
                seekBarsmile.setProgress(vb);
                count = 0;
            } else {
                count += 1;
            }

            Log.v(MANUAL_TESTING_LOG, "face smiling probability: " + String.valueOf(ff));
        }
    }

    /**
     * 顔の検出が失敗した場合に呼び出されるメソッドです。
     *
     * @param e エラー情報
     */
    @Override
    protected void onFailure(@NonNull Exception e) {
        Log.e(TAG, "Face detection failed " + e);
    }
}
