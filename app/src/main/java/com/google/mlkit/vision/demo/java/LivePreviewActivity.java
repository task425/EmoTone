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

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.common.annotation.KeepName;
import com.google.mlkit.vision.demo.CameraSource;
import com.google.mlkit.vision.demo.CameraSourcePreview;
import com.google.mlkit.vision.demo.GraphicOverlay;
import com.google.mlkit.vision.demo.R;
import com.google.mlkit.vision.demo.SetumeiActivity;
import com.google.mlkit.vision.demo.java.facedetector.FaceDetectorProcessor;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Live preview demo for ML Kit APIs.
 */
@KeepName
public final class LivePreviewActivity extends AppCompatActivity {


    private static final String FACE_DETECTION = "Face Detection";

    private static final String TAG = "LivePreviewActivity";
    public SeekBar seekBarSmile;

    private CameraSource cameraSource = null;
    private CameraSourcePreview preview;
    private GraphicOverlay graphicOverlay;
    private String selectedModel = FACE_DETECTION;


    //EmpathのAPIに必要なもの
    private static final String API_ENDPOINT = "https://api.webempath.net/v2/analyzeWav";
    private static final String API_KEY = "i4wh-96Yt7IBJhr_aipmy55h9_55On-DpQChbP59r6E";


    //録音するwavファイルの形式
    private static final int SAMPLE_RATE = 11025;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private boolean isRecording = false;
    private AudioRecord audioRecord;
    private int bufferSize;

    //ファイル名
    private static String OUTPUT_FILE_PATH = null;
    private static String OUTPUT_FILE_PATH2 = null;
    private static String SEND_FILE_PATH = null;
    Context context;

    //一定間隔の繰り返し処理
    private Timer timer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long delay, period;
    int count;
    boolean aBoolean = true;

    //録音のデータを書き込みに使用
    DataOutputStream dataOutputStream;

    //ダイアログ
    public CustomDialog dialog = new CustomDialog();
    public String emoto;

    public ImageView imgsmile1;
    public ImageView imgsmile2;
    public ImageView imgsmile3;
    public ImageView imgsmile4;
    public ImageView imgsmile5;
    public ImageView imgsmile6;
    public ImageView imgsmile7;
    public ImageView imgsmile8;
    public ImageView imgsmile9;
    public ImageView imgsmile10;

    public ImageView imgJoy1;
    public ImageView imgJoy2;
    public ImageView imgJoy3;
    public ImageView imgJoy4;
    public ImageView imgJoy5;
    public ImageView imgJoy6;
    public ImageView imgJoy7;
    public ImageView imgJoy8;
    public ImageView imgJoy9;
    public ImageView imgJoy10;

    public ImageView imgEnergy1;
    public ImageView imgEnergy2;
    public ImageView imgEnergy3;
    public ImageView imgEnergy4;
    public ImageView imgEnergy5;
    public ImageView imgEnergy6;
    public ImageView imgEnergy7;
    public ImageView imgEnergy8;
    public ImageView imgEnergy9;
    public ImageView imgEnergy10;

    public ImageView imgAnger1;
    public ImageView imgAnger2;
    public ImageView imgAnger3;
    public ImageView imgAnger4;
    public ImageView imgAnger5;
    public ImageView imgAnger6;
    public ImageView imgAnger7;
    public ImageView imgAnger8;
    public ImageView imgAnger9;
    public ImageView imgAnger10;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.activity_vision_live_preview);

//        imgsmile1 = findViewById(R.id.imageViewSmile1);

        //アクションバーの非表示
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }

        dialog.lpa = this;
        CustomDialog.showCustomDialogRadioButton(LivePreviewActivity.this);

        findViewById(R.id.check).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        CustomDialog.showCustomDialogRadioButton(LivePreviewActivity.this);
                    }
                }
        );

        findViewById(R.id.smile).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        //「Smile」というTagを付けてDialogを開く。
                        emoto = "smile";
                        CustomDialog.showCustomDialog(LivePreviewActivity.this);
                    }
                }
        );

        findViewById(R.id.joy).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        //「joy」というTagを付けてDialogを開く。
                        emoto = "joy";
                        CustomDialog.showCustomDialog(LivePreviewActivity.this);
                    }
                }
        );

        findViewById(R.id.energy).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        //「energy」というTagを付けてDialogを開く。
                        emoto = "energy";
                        CustomDialog.showCustomDialog(LivePreviewActivity.this);
                    }
                }
        );

        findViewById(R.id.anger).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        //「anger」というTagを付けてDialogを開く。
                        emoto = "anger";
                        CustomDialog.showCustomDialog(LivePreviewActivity.this);
                    }
                }
        );

        //タイマー
        delay = 5000;
        period = 5000;


        preview = findViewById(R.id.preview_view);
        if (preview == null) {
            Log.d(TAG, "Preview is null");
        }
        graphicOverlay = findViewById(R.id.graphic_overlay);
        if (graphicOverlay == null) {
            Log.d(TAG, "graphicOverlay is null");
        }

        //一番最初の機能のやつ
        createCameraSource(selectedModel);

        //MLkit確認用
        seekBarSmile = findViewById(R.id.seekbarSmile);

        ///Empath確認用
        SeekBar seekBarEnergy = findViewById(R.id.seekbarEnergy);
        SeekBar seekBarJoy = findViewById(R.id.seekbarJoy);
        SeekBar seekBarAnger = findViewById(R.id.seekbarAnger);

        ///seekbarの無効化
        seekBarSmile.setEnabled(false);
        seekBarEnergy.setEnabled(false);
        seekBarJoy.setEnabled(false);
        seekBarAnger.setEnabled(false);

        //保存するファイル
        context = getApplicationContext();
        OUTPUT_FILE_PATH = context.getFilesDir() + "/recording.wav";
        OUTPUT_FILE_PATH2 = context.getFilesDir() + "/recording2.wav";
        SEND_FILE_PATH = context.getFilesDir() + "/sendWav.wav";

        //録音のデータ書き込みのサイズ
        bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);

        if (isRecording) {
            Log.d("MONE", "isRecording::::" + isRecording);
            if (null != timer) {
                // Cancel
                timer.cancel();
                timer = null;
            }
            try {
                stopRecording();
            } catch (IOException e) {
                e.printStackTrace();
            }

        } else {

            //audioRecordの初期化
            if (ActivityCompat.checkSelfPermission(LivePreviewActivity.this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize);


            //録音開始
            startRecording();

            //タイマー
            // Timer インスタンスを生成
            timer = new Timer();
            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    // handlerdを使って処理をキューイングする
                    handler.post(() -> {
                        Log.w("MONE", "handlerStart" + aBoolean);
                        //ここに処理を書く
                        ///Empath
                        try {
                            stopRecording();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        File wavFile = new File(SEND_FILE_PATH);
                        if (wavFile.exists()) {
                            wavFile.delete();
                            Log.d("MONE", "sendfile削除");
                        }


                        if (aBoolean == true) {
                            //ヘッダ情報追加
                            SEND_FILE_PATH = addWavHeader(OUTPUT_FILE_PATH);
                        } else {
                            //ヘッダ情報追加
                            SEND_FILE_PATH = addWavHeader(OUTPUT_FILE_PATH2);
                            Log.d("MONE", "OUT_FILE_PATH__2を参照");
                        }

                        //empathに送るファイル
                        wavFile = new File(SEND_FILE_PATH);

                        if (wavFile.exists()) {
                            // ファイルが存在する場合の処理
                            if (aBoolean == true) {
                                Log.d("MONE", "fileTrue");
                            } else {
                                Log.d("MONE", "fileFalse");
                            }
//
                        } else {
                            // ファイルが存在しない場合の処理
                            Log.d("MONE", "nothing");
                        }

                        wavFile.setExecutable(true, false);
                        wavFile.setWritable(true, false);
                        wavFile.setReadable(true, false);


                        OkHttpClient client = new OkHttpClient();

                        RequestBody requestBody = new MultipartBody.Builder().setType(MultipartBody.FORM)
                                .addFormDataPart("apikey", API_KEY)
                                .addFormDataPart("wav", wavFile.getName(),
                                        RequestBody.create(MediaType.parse("audio/wav"), wavFile))
                                .build();


                        Request request = new Request.Builder().url(API_ENDPOINT).post(requestBody).build();

                        client.newCall(request).enqueue(new Callback() {
                            @Override
                            public void onFailure(Call call, IOException e) {
                                // リクエストの送信に失敗した場合の処理
                                e.printStackTrace();
                                Log.d("MONE", "失敗" + aBoolean);
                            }

                            @Override
                            public void onResponse(Call call, Response response) throws IOException {


                                Log.e("MONE", "aaa");
                                // レスポンスの受信後の処理
                                if (response.isSuccessful()) {
                                    // 成功した場合の処理
                                    String responseBody = (String) response.body().string();
                                    System.out.println(responseBody);
                                    Log.d("MONE", "requestok" + aBoolean);

                                    String str = responseBody;

                                    JSONObject jsonObject = null;
                                    try {
                                        jsonObject = new JSONObject(responseBody);
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }

                                    /// 表示文字の作成
                                    String s = count + "■JSON全体\n" + jsonObject.toString() + "\n";
                                    Log.d("MONE", s);


                                    try {
                                        s = s + "calmは" + jsonObject.getString("calm") + "\n";
//                                        textViewCalm.setText(jsonObject.getString("calm"));
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }
                                    try {
                                        s = s + "angerは" + jsonObject.getString("anger") + "\n";
//                                        textViewAnger.setText(jsonObject.getString("anger"));
                                        seekBarAnger.setProgress(Integer.parseInt(jsonObject.getString("anger")));

                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }
                                    try {
                                        s = s + "joyは" + jsonObject.getString("joy") + "\n";
                                        seekBarJoy.setProgress(Integer.parseInt(jsonObject.getString("joy")));
//                                        textViewJoy.setText(jsonObject.getString("joy"));
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }
                                    try {
                                        s = s + "sorrowは" + jsonObject.getString("sorrow") + "\n";
//                                        textViewSorrow.setText(jsonObject.getString("sorrow"));
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }
                                    try {
                                        s = s + "energyは" + jsonObject.getString("energy") + "\n";
                                        seekBarEnergy.setProgress(Integer.parseInt(jsonObject.getString("energy")));

//                                        textViewEnergy.setText(jsonObject.getString("energy"));
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }

                                } else {
                                    // 失敗した場合の処理
                                    Log.d("MONE", "requestng");

                                    System.out.println("Request failed with code: " + response.code());
                                }
                                response.close();
                            }
                        });

                        //
                        aBoolean = !aBoolean;

                        //audioRecord.release()はここじゃなくてもいいかも
                        audioRecord.release();

                        startRecording();


                    });


                }
            }, delay, period);

        }
    }


    ///mlkit
    //どの機能使ってるかの確認
    private void createCameraSource(String model) {
        // カメラソースが存在しない場合は作成する
        if (cameraSource == null) {
            cameraSource = new CameraSource(this, graphicOverlay);
        }
        try {
            switch (model) {
                case FACE_DETECTION:
                    Log.i(TAG, "Using Face Detector Processor");
                    cameraSource.setMachineLearningFrameProcessor(new FaceDetectorProcessor(this));
                    break;
                // 他のモデルの場合はここに追加する
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "Can not create image processor: " + model, e);
            Toast.makeText(
                            getApplicationContext(),
                            "Can not create image processor: " + e.getMessage(),
                            Toast.LENGTH_LONG)
                    .show();
        }
    }

    /**
     * カメラソースが存在する場合にカメラソースを開始または再開します。
     * カメラソースがまだ存在しない場合（たとえば、カメラソースが作成される前にonResumeが呼び出された場合）は、
     * カメラソースが作成されたときに再び呼び出されます。
     */
    private void startCameraSource() {
        if (cameraSource != null) {
            try {
                if (preview == null) {
                    // プレビューが存在しない場合のログ
                    Log.d(TAG, "resume: Preview is null");
                }
                if (graphicOverlay == null) {
                    // グラフィックオーバーレイが存在しない場合のログ
                    Log.d(TAG, "resume: graphOverlay is null");
                }
                preview.start(cameraSource, graphicOverlay);
            } catch (IOException e) {
                // カメラソースの起動に失敗した場合のエラーログ
                Log.e(TAG, "Unable to start camera source.", e);
                cameraSource.release();
                cameraSource = null;
            }
        }
    }

    //起動時、再開時
    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        imgsmile1 = findViewById(R.id.imageViewSmile1);
        imgsmile2 = findViewById(R.id.imageViewSmile2);
        imgsmile3 = findViewById(R.id.imageViewSmile3);
        imgsmile4 = findViewById(R.id.imageViewSmile4);
        imgsmile5 = findViewById(R.id.imageViewSmile5);
        imgsmile6 = findViewById(R.id.imageViewSmile6);
        imgsmile7 = findViewById(R.id.imageViewSmile7);
        imgsmile8 = findViewById(R.id.imageViewSmile8);
        imgsmile9 = findViewById(R.id.imageViewSmile9);
        imgsmile10 = findViewById(R.id.imageViewSmile10);

        imgJoy1 = findViewById(R.id.imageViewJoy1);
        imgJoy2 = findViewById(R.id.imageViewJoy2);
        imgJoy3 = findViewById(R.id.imageViewJoy3);
        imgJoy4 = findViewById(R.id.imageViewJoy4);
        imgJoy5 = findViewById(R.id.imageViewJoy5);
        imgJoy6 = findViewById(R.id.imageViewJoy6);
        imgJoy7 = findViewById(R.id.imageViewJoy7);
        imgJoy8 = findViewById(R.id.imageViewJoy8);
        imgJoy9 = findViewById(R.id.imageViewJoy9);
        imgJoy10 = findViewById(R.id.imageViewJoy10);

        imgEnergy1 = findViewById(R.id.imageViewEnergy1);
        imgEnergy2 = findViewById(R.id.imageViewEnergy2);
        imgEnergy3 = findViewById(R.id.imageViewEnergy3);
        imgEnergy4 = findViewById(R.id.imageViewEnergy4);
        imgEnergy5 = findViewById(R.id.imageViewEnergy5);
        imgEnergy6 = findViewById(R.id.imageViewEnergy6);
        imgEnergy7 = findViewById(R.id.imageViewEnergy7);
        imgEnergy8 = findViewById(R.id.imageViewEnergy8);
        imgEnergy9 = findViewById(R.id.imageViewEnergy9);
        imgEnergy10 = findViewById(R.id.imageViewEnergy10);

        imgAnger1 = findViewById(R.id.imageViewAnger1);
        imgAnger2 = findViewById(R.id.imageViewAnger2);
        imgAnger3 = findViewById(R.id.imageViewAnger3);
        imgAnger4 = findViewById(R.id.imageViewAnger4);
        imgAnger5 = findViewById(R.id.imageViewAnger5);
        imgAnger6 = findViewById(R.id.imageViewAnger6);
        imgAnger7 = findViewById(R.id.imageViewAnger7);
        imgAnger8 = findViewById(R.id.imageViewAnger8);
        imgAnger9 = findViewById(R.id.imageViewAnger9);
        imgAnger10 = findViewById(R.id.imageViewAnger10);


        createCameraSource(selectedModel);
        startCameraSource();
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize);
        startRecording();
    }

    //Androidの戻るボタン押下時
    public void onBackPressed() {
        // ここに戻るボタンが押されたときの処理を記述します
        deleteEmpath();
        // デフォルトの戻るボタンの挙動を無効化する場合は以下のコメントアウトを解除します
        super.onBackPressed();
    }

    /**
     * Stops the camera.
     */
    //ホームボタン押したとき
    @Override
    protected void onPause() {
        super.onPause();
        preview.stop();
    }

    //タスク削除時
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (cameraSource != null) {
            cameraSource.release();
        }
    }

    ///Empath
    //
    //録音スタート
    //
    @SuppressLint("WrongConstant")
    private void startRecording() {

        Log.v("MONE", "start Recording");

        isRecording = true;


        /* ファイルが存在する場合は削除 */
        File wavFile;
        if (aBoolean == true) {
            wavFile = new File(OUTPUT_FILE_PATH);
        } else {
            wavFile = new File(OUTPUT_FILE_PATH2);
        }

        if (wavFile.exists()) {
            wavFile.delete();
        }
        wavFile = null;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    //dataOutputStreamを初期化
                    if (aBoolean == true) {
                        dataOutputStream = new DataOutputStream(new FileOutputStream(OUTPUT_FILE_PATH));
                    } else {
                        dataOutputStream = new DataOutputStream(new FileOutputStream(OUTPUT_FILE_PATH2));
                    }
                    byte[] buffer = new byte[bufferSize];

                    //audioRecordが初期化で来ていないときに初期化
                    if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                        audioRecord.release();
                        if (ActivityCompat.checkSelfPermission(LivePreviewActivity.this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            return;
                        }
                        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize);
                    }

                    if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                        audioRecord.startRecording();
                        // 録音中の処理を行う
                        while (isRecording) {
                            int bytesRead = audioRecord.read(buffer, 0, bufferSize);
                            dataOutputStream.write(buffer, 0, bytesRead);
                        }
                    } else {
                        // 初期化に失敗した場合の処理
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();

    }

    //
    //録音ストップ
    //
    private void stopRecording() throws IOException {
        Log.v("test", "stop Recording");

        isRecording = false;

        //audioRecord.stop()をここに書かないと落ちる
        audioRecord.stop();
        dataOutputStream.close();


    }

    //
    //wavヘッダー追加
    //
    private static String addWavHeader(String wavFile) {
        //ヘッダ情報追加
        try {
            // 元の録音ファイルを読み込む
            FileInputStream inputStream;

            inputStream = new FileInputStream(wavFile);

            byte[] audioData = new byte[inputStream.available()];
            inputStream.read(audioData);
            inputStream.close();

            // 録音データの長さを計算
            int audioDataSize = audioData.length;

            // ヘッダ情報を作成
            byte[] header = createWavHeader(audioDataSize, SAMPLE_RATE, 1);

            // ヘッダ情報と録音データを結合して新しいファイルに保存
            byte[] result = new byte[audioData.length + header.length];
            System.arraycopy(header, 0, result, 0, header.length);
            System.arraycopy(audioData, 0, result, header.length, audioData.length);
            FileOutputStream outputStream;
            //ヘッダ付きwavファイル保存
            outputStream = new FileOutputStream(SEND_FILE_PATH);

            outputStream.write(result);
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return SEND_FILE_PATH;
    }


    //
    //wavヘッダー作成
    //
    private static byte[] createWavHeader(int audioDataSize, int sampleRate, int numChannels) {
        int byteRate = sampleRate * numChannels * 2;
        int subChunk2Size = audioDataSize;
        int chunkSize = 36 + subChunk2Size;

        byte[] header = new byte[44];

        // ChunkID
        header[0] = 'R';
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';

        // ChunkSize
        header[4] = (byte) (chunkSize & 0xFF);
        header[5] = (byte) ((chunkSize >> 8) & 0xFF);
        header[6] = (byte) ((chunkSize >> 16) & 0xFF);
        header[7] = (byte) ((chunkSize >> 24) & 0xFF);

        // Format
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';

        // Subchunk1ID
        header[12] = 'f';
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';

        // Subchunk1Size
        header[16] = 16;
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;

        // AudioFormat
        header[20] = 1;
        header[21] = 0;

        // NumChannels
        header[22] = (byte) numChannels;
        header[23] = 0;

        // SampleRate
        header[24] = 17;
        header[25] = 43;
        header[26] = 0;
        header[27] = 0;

        // ByteRate
        header[28] = (byte) (byteRate & 0xFF);
        header[29] = (byte) ((byteRate >> 8) & 0xFF);
        header[30] = (byte) ((byteRate >> 16) & 0xFF);
        header[31] = (byte) ((byteRate >> 24) & 0xFF);

        // BlockAlign
        header[32] = (byte) (numChannels * 16 / 8);
        header[33] = 0;

        // BitsPerSample
        header[34] = 16;
        header[35] = 0;

        // Subchunk2ID
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';

        // Subchunk2Size
        header[40] = (byte) (subChunk2Size & 0xFF);
        header[41] = (byte) ((subChunk2Size >> 8) & 0xFF);
        header[42] = (byte) ((subChunk2Size >> 16) & 0xFF);
        header[43] = (byte) ((subChunk2Size >> 24) & 0xFF);

        return header;
    }

    public void deleteEmpath() {
        if (null != timer) {
            //cancel
            timer.cancel();
            timer = null;
        }
        try {
            stopRecording();
        } catch (IOException e) {
            e.printStackTrace();
        }
        audioRecord.release();
        audioRecord = null;
    }

    public void jumpSetumeiActivity() {
        deleteEmpath();
        Intent intent = new Intent(LivePreviewActivity.this, SetumeiActivity.class);
        startActivity(intent);
    }

    //ImageViewに基準をセットするやつ
    public void setImageView(int check) {
        Log.d("tasktask", String.valueOf(check));
        deleteImageView();
        if (check == 1) {
            Log.d("mone","check");
            imgsmile10.setImageResource(R.drawable.sankaku);
            imgJoy7.setImageResource(R.drawable.sankaku);
            imgEnergy3.setImageResource(R.drawable.sankaku);
            imgAnger1.setImageResource(R.drawable.sankaku);
        }else if(check==2){
            imgsmile7.setImageResource(R.drawable.sankaku);
            imgJoy6.setImageResource(R.drawable.sankaku);
            imgEnergy8.setImageResource(R.drawable.sankaku);
            imgAnger3.setImageResource(R.drawable.sankaku);
        }else if(check==3){
            imgsmile6.setImageResource(R.drawable.sankaku);
            imgJoy3.setImageResource(R.drawable.sankaku);
            imgEnergy6.setImageResource(R.drawable.sankaku);
            imgAnger2.setImageResource(R.drawable.sankaku);

        }else if(check==4){
            imgsmile9.setImageResource(R.drawable.sankaku);
            imgJoy2.setImageResource(R.drawable.sankaku);
            imgEnergy5.setImageResource(R.drawable.sankaku);
            imgAnger2.setImageResource(R.drawable.sankaku);

        }
    }

    //ImageViewのリセット
    public void deleteImageView() {

        imgsmile8.setImageDrawable(null);

        imgsmile2.setImageDrawable(null);
        imgsmile3.setImageDrawable(null);
        imgsmile4.setImageDrawable(null);
        imgsmile5.setImageDrawable(null);
        imgsmile6.setImageDrawable(null);
        imgsmile7.setImageDrawable(null);
        imgsmile8.setImageDrawable(null);
        imgsmile9.setImageDrawable(null);
        imgsmile10.setImageDrawable(null);

        imgJoy1.setImageDrawable(null);
        imgJoy2.setImageDrawable(null);
        imgJoy3.setImageDrawable(null);
        imgJoy4.setImageDrawable(null);
        imgJoy5.setImageDrawable(null);
        imgJoy6.setImageDrawable(null);
        imgJoy7.setImageDrawable(null);
        imgJoy8.setImageDrawable(null);
        imgJoy9.setImageDrawable(null);
        imgJoy10.setImageDrawable(null);

        imgEnergy1.setImageDrawable(null);
        imgEnergy2.setImageDrawable(null);
        imgEnergy3.setImageDrawable(null);
        imgEnergy4.setImageDrawable(null);
        imgEnergy5.setImageDrawable(null);
        imgEnergy6.setImageDrawable(null);
        imgEnergy7.setImageDrawable(null);
        imgEnergy8.setImageDrawable(null);
        imgEnergy9.setImageDrawable(null);
        imgEnergy10.setImageDrawable(null);

        imgAnger1.setImageDrawable(null);
        imgAnger2.setImageDrawable(null);
        imgAnger3.setImageDrawable(null);
        imgAnger4.setImageDrawable(null);
        imgAnger5.setImageDrawable(null);
        imgAnger6.setImageDrawable(null);
        imgAnger7.setImageDrawable(null);
        imgAnger8.setImageDrawable(null);
        imgAnger9.setImageDrawable(null);
        imgAnger10.setImageDrawable(null);
    }
}
