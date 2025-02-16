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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.media.Image.Plane;
import android.os.Build.VERSION_CODES;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageProxy;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/**
 * ビットマップ変換のためのユーティリティ関数です。
 */
public class BitmapUtils {
    private static final String TAG = "BitmapUtils";

    /**
     * NV21形式のバイトバッファをビットマップに変換します。
     *
     * @param data     NV21形式のバイトバッファ
     * @param metadata フレームのメタデータ
     * @return 変換されたビットマップ、変換に失敗した場合はnull
     */
    @Nullable
    public static Bitmap getBitmap(ByteBuffer data, FrameMetadata metadata) {
        // バッファの位置を先頭に戻します
        data.rewind();

        // バッファからバイト配列にデータをコピーします
        byte[] imageInBuffer = new byte[data.limit()];
        data.get(imageInBuffer, 0, imageInBuffer.length);
        try {
            // NV21形式のイメージを作成します
            YuvImage image =
                    new YuvImage(
                            imageInBuffer, ImageFormat.NV21, metadata.getWidth(), metadata.getHeight(), null);
            // JPEG形式で圧縮し、バイト配列として出力します
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            image.compressToJpeg(new Rect(0, 0, metadata.getWidth(), metadata.getHeight()), 80, stream);

            // バイト配列をビットマップにデコードします
            Bitmap bmp = BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size());

            // ストリームを閉じます
            stream.close();
            // ビットマップを回転させます
            return rotateBitmap(bmp, metadata.getRotation(), false, false);
        } catch (Exception e) {
            Log.e("VisionProcessorBase", "Error: " + e.getMessage());
        }
        return null;
    }

    /**
     * CameraX APIからYUV_420_888画像をビットマップに変換します。
     *
     * @param image イメージプロキシ
     * @return 変換されたビットマップ、変換に失敗した場合はnull
     */
    @RequiresApi(VERSION_CODES.LOLLIPOP)
    @Nullable
    @ExperimentalGetImage
    public static Bitmap getBitmap(ImageProxy image) {
        FrameMetadata frameMetadata =
                new FrameMetadata.Builder()
                        .setWidth(image.getWidth())
                        .setHeight(image.getHeight())
                        .setRotation(image.getImageInfo().getRotationDegrees())
                        .build();

        ByteBuffer nv21Buffer =
                yuv420ThreePlanesToNV21(image.getImage().getPlanes(), image.getWidth(), image.getHeight());
        return getBitmap(nv21Buffer, frameMetadata);
    }

    /**
     * バイトバッファからビットマップを回転します。
     *
     * @param bitmap          回転するビットマップ
     * @param rotationDegrees 回転する角度（度数法）
     * @param flipX           X軸に対して反転するかどうか
     * @param flipY           Y軸に対して反転するかどうか
     * @return 回転されたビットマップ
     */
    private static Bitmap rotateBitmap(
            Bitmap bitmap, int rotationDegrees, boolean flipX, boolean flipY) {
        Matrix matrix = new Matrix();

        // 画像を元に戻すために回転させます。
        matrix.postRotate(rotationDegrees);

        // 画像をX軸またはY軸を中心に反転させます。
        matrix.postScale(flipX ? -1.0f : 1.0f, flipY ? -1.0f : 1.0f);
        Bitmap rotatedBitmap =
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

        // 古いビットマップを破棄します。
        if (rotatedBitmap != bitmap) {
            bitmap.recycle();
        }
        return rotatedBitmap;
    }

    /**
     * YUV_420_888をNV21のByteBufferに変換します。
     *
     * <p>NV21フォーマットは、Y、U、Vの値を含む単一のバイト配列から構成されます。
     * サイズSの画像では、配列の最初のSの位置にすべてのYの値が含まれます。
     * 残りの位置には交互に配置されたVとUの値が含まれます。
     * UとVは、両方の次元で2倍のサンプリングが行われるため、
     * S/4個のUの値とS/4個のVの値があります。
     * 要するに、NV21配列はS個のYの値に続いてS/4個のVUの値が含まれます: YYYYYYYYYYYYYY(...)YVUVUVUVU(...)VU
     *
     * <p>YUV_420_888は、UとVが両方の次元で2倍のサンプリングされる任意のYUV画像を記述する汎用フォーマットです。
     * {@link Image#getPlanes}は、Y、U、Vのプレーンを含む配列を返します。
     * Yプレーンは非インターリーブされていることが保証されているため、その値をNV21配列の最初の部分にコピーするだけです。
     * UとVプレーンは、既にNV21形式の表現を持っている場合があります。
     * これは、プレーンが同じバッファを共有し、VバッファがUバッファの1つ前の位置であり、
     * プレーンのpixelStrideが2である場合に発生します。
     * この場合、それらを単純にNV21配列にコピーすることができます。
     */
    private static ByteBuffer yuv420ThreePlanesToNV21(
            Plane[] yuv420888planes, int width, int height) {
        int imageSize = width * height;
        byte[] out = new byte[imageSize + 2 * (imageSize / 4)];

        if (areUVPlanesNV21(yuv420888planes, width, height)) {
            //Yの値をコピーする。
            yuv420888planes[0].getBuffer().get(out, 0, imageSize);

            ByteBuffer uBuffer = yuv420888planes[1].getBuffer();
            ByteBuffer vBuffer = yuv420888planes[2].getBuffer();
            // Uバッファには最初のVの値は含まれていないため、Vバッファから最初のVの値を取得する
            vBuffer.get(out, imageSize, 1);
            // 最初のUの値と残りのVUの値をUバッファからコピーする。
            uBuffer.get(out, imageSize + 1, 2 * imageSize / 4 - 1);
        } else {
            // より遅いが動作する、UVの値を1つずつコピーするフォールバック方法。
            // Yを展開する。
            unpackPlane(yuv420888planes[0], width, height, out, 0, 1);
            // Uを展開する。
            unpackPlane(yuv420888planes[1], width, height, out, imageSize + 1, 2);
            // Vを展開する。
            unpackPlane(yuv420888planes[2], width, height, out, imageSize, 2);
        }

        return ByteBuffer.wrap(out);
    }

    /**
     * YUV_420_888画像のUVプレーンバッファがNV21形式かどうかをチェックします。
     */
    private static boolean areUVPlanesNV21(Plane[] planes, int width, int height) {
        int imageSize = width * height;

        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        // バッファのプロパティをバックアップする。
        int vBufferPosition = vBuffer.position();
        int uBufferLimit = uBuffer.limit();

        // Uバッファには最初のVの値は含まれていないため、Vバッファを1バイト進める。
        vBuffer.position(vBufferPosition + 1);
        // Uバッファの最後のバイトは含まれていないため、Uバッファの制限を調整する。
        uBuffer.limit(uBufferLimit - 1);

        // バッファが等しいかつ期待される要素の数を持っているかどうかをチェックする。
        boolean areNV21 =
                (vBuffer.remaining() == (2 * imageSize / 4 - 2)) && (vBuffer.compareTo(uBuffer) == 0);

        // バッファを初期状態に戻す。
        vBuffer.position(vBufferPosition);
        uBuffer.limit(uBufferLimit);

        return areNV21;
    }

    /**
     * 画像プレーンをバイト配列に展開します。
     *
     * <p>入力プレーンのデータは、'out'から'offset'で始まり、
     * 各ピクセルは'pixelStride'でスペースが開けられます。
     * 出力には行のパディングはありません。
     */
    private static void unpackPlane(
            Plane plane, int width, int height, byte[] out, int offset, int pixelStride) {
        ByteBuffer buffer = plane.getBuffer();
        buffer.rewind();

        // 現在のプレーンのサイズを計算します。
        // 元の画像と同じアスペクト比を持っていると仮定します。
        int numRow = (buffer.limit() + plane.getRowStride() - 1) / plane.getRowStride();
        if (numRow == 0) {
            return;
        }
        int scaleFactor = height / numRow;
        int numCol = width / scaleFactor;

        // 出力バッファ内のデータを抽出します。
        int outputPos = offset;
        int rowStart = 0;
        for (int row = 0; row < numRow; row++) {
            int inputPos = rowStart;
            for (int col = 0; col < numCol; col++) {
                out[outputPos] = buffer.get(inputPos);
                outputPos += pixelStride;
                inputPos += plane.getPixelStride();
            }
            rowStart += plane.getRowStride();
        }
    }
}
