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

import android.graphics.Canvas;
import android.util.Log;

import com.google.mlkit.vision.demo.GraphicOverlay;
import com.google.mlkit.vision.demo.GraphicOverlay.Graphic;
import com.google.mlkit.vision.face.Face;

/**
 * グラフィックオーバーレイビュー内で顔の位置、輪郭、ランドマークを描画するためのグラフィックインスタンスです。
 */
public class FaceGraphic extends Graphic {

    private volatile Face face;


    /**
     * FaceGraphic オブジェクトを生成します。
     *
     * @param overlay グラフィックオーバーレイビュー
     * @param face    描画する顔の情報を保持する Face オブジェクト
     */
    FaceGraphic(GraphicOverlay overlay, Face face) {
        super(overlay);
        this.face = face;
    }

    /**
     * 指定されたキャンバス上に顔の注釈を描画します。
     */
    @Override
    public void draw(Canvas canvas) {
        Face face = this.face;
        if (face == null) {
            return;
        }
        Log.d("smile", String.valueOf(face.getSmilingProbability()));
    }

}
