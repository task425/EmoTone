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


import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.util.AttributeSet;
import android.view.View;

import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.List;

/**
 * 関連するプレビュー（カメラプレビュー）の上にオーバーレイするためのカスタムグラフィックスのシリーズをレンダリングするビューです。
 * 作成者はグラフィックスオブジェクトを追加し、更新し、削除することができます。これにより、ビュー内の適切な描画と無効化がトリガーされます。
 * カメラのプレビュープロパティに対して、グラフィックスのスケーリングとミラーリングをサポートしています。
 * アイテムは画像サイズで表現されますが、フルビューサイズにスケーリングする必要があり、フロントカメラの場合にはミラーリングも必要です。
 * 関連する{@link Graphic}アイテムは、以下のメソッドを使用してビュー座標に変換する必要があります：
 * <ol>
 * <li>{@link Graphic#scale(float)}は、提供された値のサイズをイメージスケールからビュースケールに調整します。
 * </ol>
 */
public class GraphicOverlay extends View {
    private final Object lock = new Object();
    private final List<Graphic> graphics = new ArrayList<>();
    // 画像座標からオーバーレイビュー座標に変換するための行列
    private final Matrix transformationMatrix = new Matrix();

    private int imageWidth;
    private int imageHeight;
    // オーバーレイビューのサイズと画像サイズの比率。
    // 画像座標のすべての要素は、この量でスケーリングしてオーバーレイビューエリアに合わせる必要があります。
    private float scaleFactor = 1.0f;
    // スケーリング後にオーバーレイビューエリアに画像を合わせるために両側から切り取る必要のある水平ピクセル数
    private float postScaleWidthOffset;
    // スケーリング後にオーバーレイビューエリアに画像を合わせるために両側から切り取る必要のある垂直ピクセル数
    private float postScaleHeightOffset;
    private boolean isImageFlipped;
    private boolean needUpdateTransformation = true;

    /**
     * グラフィックオーバーレイ内にレンダリングされるカスタムグラフィックスオブジェクトの基本クラスです。
     * このクラスをサブクラス化し、{@link Graphic#draw(Canvas)}メソッドを実装してグラフィックス要素を定義します。
     * インスタンスは{@link GraphicOverlay#add(Graphic)}を使用してオーバーレイに追加します。
     */
    public abstract static class Graphic {
        private GraphicOverlay overlay;

        public Graphic(GraphicOverlay overlay) {
            this.overlay = overlay;
        }

        /**
         * グラフィックを指定されたキャンバスに描画します。描画には、以下のメソッドを使用してビュー座標に変換する必要があります：
         *
         * <ol>
         *   <li>{@link Graphic#scale(float)}は、提供された値のサイズをイメージスケールからビュースケールに調整します。
         * </ol>
         *
         * @param canvas 描画キャンバス
         */
        public abstract void draw(Canvas canvas);

        /**
         * イメージスケールからビュースケールへの変換で提供された値を調整します。
         */
        public float scale(float imagePixel) {
            return imagePixel * overlay.scaleFactor;
        }


        /**
         * イメージ座標からオーバーレイビュー座標への変換に使用する{@link Matrix}を取得します。
         */
        public Matrix getTransformationMatrix() {
            return overlay.transformationMatrix;
        }

        public void postInvalidate() {
            overlay.postInvalidate();
        }
    }

    // GraphicOverlayのコンストラクタです。addOnLayoutChangeListenerを使用して、ビューのレイアウト変更時に変換の更新が必要であることを設定します。
    public GraphicOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        addOnLayoutChangeListener(
                (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                        needUpdateTransformation = true);
    }

    /**
     * オーバーレイからすべてのグラフィックを削除します。
     */
    public void clear() {
        synchronized (lock) {
            graphics.clear();
        }
        postInvalidate();
    }

    // グラフィックオーバーレイに新しいグラフィックを追加します。
    public void add(Graphic graphic) {
        synchronized (lock) {
            graphics.add(graphic);
        }
    }

    // グラフィックオーバーレイからグラフィックを削除します。
    public void remove(Graphic graphic) {
        synchronized (lock) {
            graphics.remove(graphic);
        }
        postInvalidate();
    }

    // テキストスタイルを設定します。
    public void setImageSourceInfo(int imageWidth, int imageHeight, boolean isFlipped) {
        Preconditions.checkState(imageWidth > 0, "image width must be positive");
        Preconditions.checkState(imageHeight > 0, "image height must be positive");
        synchronized (lock) {
            this.imageWidth = imageWidth;
            this.imageHeight = imageHeight;
            this.isImageFlipped = isFlipped;
            needUpdateTransformation = true;
        }
        postInvalidate();
    }

    private void updateTransformationIfNeeded() {
        // 変換の更新が必要でない場合や画像のサイズが無効な場合は、処理を終了します。
        if (!needUpdateTransformation || imageWidth <= 0 || imageHeight <= 0) {
            return;
        }
        // ビューのアスペクト比と画像のアスペクト比を計算します。
        float viewAspectRatio = (float) getWidth() / getHeight();
        float imageAspectRatio = (float) imageWidth / imageHeight;
        // ポストスケールのオフセットを初期化します。
        postScaleWidthOffset = 0;
        postScaleHeightOffset = 0;
        if (viewAspectRatio > imageAspectRatio) {
            // このビューで表示するために、画像は垂直に切り取られる必要があります。
            scaleFactor = (float) getWidth() / imageWidth;
            postScaleHeightOffset = ((float) getWidth() / imageAspectRatio - getHeight()) / 2;
        } else {
            // このビューで表示するために、画像は水平に切り取られる必要があります。
            scaleFactor = (float) getHeight() / imageHeight;
            postScaleWidthOffset = ((float) getHeight() * imageAspectRatio - getWidth()) / 2;
        }

        // 変換行列をリセットして設定します。
        transformationMatrix.reset();
        transformationMatrix.setScale(scaleFactor, scaleFactor);
        transformationMatrix.postTranslate(-postScaleWidthOffset, -postScaleHeightOffset);

        // 画像がフリップされている場合は、変換行列にフリップのスケール変換を追加します。
        if (isImageFlipped) {
            transformationMatrix.postScale(-1f, 1f, getWidth() / 2f, getHeight() / 2f);
        }

        // 変換の更新が完了したので、フラグをリセットします。
        needUpdateTransformation = false;
    }

    /**
     * グラフィックオーバーレイ内のすべてのグラフィックを描画します。
     *
     * @param canvas 描画するためのキャンバス
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        synchronized (lock) {
            // 変換の更新を必要な場合に実行します。
            updateTransformationIfNeeded();

            // グラフィックオーバーレイ内のすべてのグラフィックを描画します。
            for (Graphic graphic : graphics) {
                graphic.draw(canvas);
            }
        }
    }
}
