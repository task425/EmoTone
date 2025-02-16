package com.google.mlkit.vision.demo;

import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


public class EmpathActivity extends AppCompatActivity {

    //empathからのデータを格納
    //[0]:エラーコード [1]:平常 [2]:怒り [3]:喜び [4]:悲しみ [5]:元気度
    public static String[] empathData = new String[6];

    //Empathからデータを受け取る
    public static void getEmpathData(File wavFile, Boolean aBoolean) {
        //EmpathのAPIに必要なもの
        final String API_ENDPOINT = "https://api.webempath.net/v2/analyzeWav";
        final String API_KEY = "BZmqk354NCDqu3SZtv0S2_awcCmdwb0YfohQTC17ihw";

        empathData[0] = "-1";

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
                Log.d("empathtest", "失敗" + aBoolean);
                empathData[0] = "-2";
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {

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
                    String s = "■JSON全体\n" + jsonObject.toString() + "\n";
                    Log.d("empathtest", s);

                    //エラーコードを取得
                    try {
                        empathData[0] = jsonObject.getString("error");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    //「平常」の値を取得
                    try {
                        empathData[1] = jsonObject.getString("calm");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    //「怒り」の値を取得
                    try {
                        empathData[2] = jsonObject.getString("anger");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    //「喜び」の値を取得
                    try {
                        empathData[3] = jsonObject.getString("joy");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    //「悲しみ」の喜びを取得
                    try {
                        empathData[4] = jsonObject.getString("sorrow");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    //「元気度」の値を取得
                    try {
                        empathData[5] = jsonObject.getString("energy");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                } else {
                    // 失敗した場合の処理
                    Log.d("MONE", "requestng");
                    System.out.println("Request failed with code: " + response.code());
                    empathData[0] = "-3";
                }
                response.close();
            }
        });
    }
}

