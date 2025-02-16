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


import com.google.mlkit.common.MlKitException;

import java.nio.ByteBuffer;

/**
 * 異なるビジョンディテクターやカスタムイメージモデルで画像を処理するためのインターフェースです。
 */
public interface VisionImageProcessor {

    /**
     * ByteBuffer 形式の画像データを処理します。例：Camera1 のライブプレビューの場合に使用します。
     *
     * @param data           処理する ByteBuffer 画像データ
     * @param frameMetadata  フレームのメタデータ情報
     * @param graphicOverlay 処理結果を表示するための GraphicOverlay
     * @throws MlKitException 画像処理中にエラーが発生した場合にスローされます
     */
    void processByteBuffer(
            ByteBuffer data, FrameMetadata frameMetadata, GraphicOverlay graphicOverlay)
            throws MlKitException;

    /**
     * バックグラウンドで実行中の機械学習モデルを停止し、リソースを解放します。
     */
    void stop();
}
