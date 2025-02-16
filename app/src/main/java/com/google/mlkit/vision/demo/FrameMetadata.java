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


/**
 * フレームの情報を記述するクラスです。
 */
public class FrameMetadata {

    private final int width;// フレームの幅
    private final int height;// フレームの高さ
    private final int rotation;// フレームの回転角度

    /**
     * フレームの幅を取得します。
     */
    public int getWidth() {
        return width;
    }

    /**
     * フレームの高さを取得します。
     */
    public int getHeight() {
        return height;
    }

    /**
     * フレームの回転角度を取得します。
     */
    public int getRotation() {
        return rotation;
    }

    private FrameMetadata(int width, int height, int rotation) {
        this.width = width;
        this.height = height;
        this.rotation = rotation;
    }

    /**
     * {@link FrameMetadata} のビルダークラスです。
     */
    public static class Builder {

        private int width;
        private int height;
        private int rotation;

        /**
         * フレームの幅を設定します。
         */
        public Builder setWidth(int width) {
            this.width = width;
            return this;
        }

        /**
         * フレームの高さを設定します。
         */
        public Builder setHeight(int height) {
            this.height = height;
            return this;
        }

        /**
         * フレームの回転を設定します。
         */
        public Builder setRotation(int rotation) {
            this.rotation = rotation;
            return this;
        }

        /**
         * ビルダーを使用して {@link FrameMetadata} オブジェクトを構築します。
         *
         * @return 構築された {@link FrameMetadata} オブジェクト
         */
        public FrameMetadata build() {
            return new FrameMetadata(width, height, rotation);
        }
    }
}
