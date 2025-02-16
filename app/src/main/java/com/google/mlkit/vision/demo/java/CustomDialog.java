package com.google.mlkit.vision.demo.java;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.google.mlkit.vision.demo.MainActivity;
import com.google.mlkit.vision.demo.R;
import com.google.mlkit.vision.demo.SetumeiActivity;
import com.google.mlkit.vision.demo.java.LivePreviewActivity;

public class CustomDialog {

    public static LivePreviewActivity lpa;
    public static int check;

    public static void showCustomDialog(Context context){



        // カスタムレイアウトの用意
        LayoutInflater inflater = LayoutInflater.from(context);
        View dialogView = inflater.inflate(R.layout.custom_dialog,null);

        // ダイアログ内のTextViewの取得
        TextView titleTextView=dialogView.findViewById(R.id.dialogTitleTextView);
        TextView setumeiTextView=dialogView.findViewById(R.id.dialogSetumeiTextView);

        // AlertDialogを作成して、Custom Dialogのビューを設定
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setView(dialogView);

        // AlertDialogを表示
        AlertDialog dialog = builder.create();
        dialog.show();


        //押されたボタンの判別
        if (lpa.emoto.equals("smile")){
            titleTextView.setBackgroundResource(R.color.pink);
            titleTextView.setText("笑顔");
            setumeiTextView.setText("どれくらい笑顔なのか\nポイントが高いほど\nあなはたよい笑顔です");
        } else if (lpa.emoto.equals("joy")) {
            titleTextView.setBackgroundResource(R.color.yellowgreen);
            titleTextView.setText("喜び");
            setumeiTextView.setText("どれくらい喜んでいる声色なのか\nポイントが高いほど\n喜びに満ちた声色です");
        }else if (lpa.emoto.equals("energy")) {
            titleTextView.setBackgroundResource(R.color.green);
            titleTextView.setText("元気度");
            setumeiTextView.setText("どれくらい元気がある声色なのか\nポイントが高いほど\n元気いっぱいな声色です");
        }else if (lpa.emoto.equals("anger")) {
            titleTextView.setBackgroundResource(R.color.red);
            titleTextView.setText("怒り");
            setumeiTextView.setText("どれくらい怒っているような声色なのか\nポイントが高いほど\n怒っているような声色です");
        }

        //説明画面に飛ぶボタン
        try {
            dialogView.findViewById(R.id.dialogDismissButton).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    dialog.dismiss();
                    lpa.jumpSetumeiActivity();
                    Log.d("MONE", "click");
                }
            });

        } catch (Exception e) {
            Log.d("MONE", String.valueOf(e));
        }


    }

    public static void showCustomDialogRadioButton(Context context){
        // カスタムレイアウトの用意
        LayoutInflater inflater = LayoutInflater.from(context);
        View dialogView = inflater.inflate(R.layout.dialog_layout,null);


        // AlertDialogを作成して、Custom Dialogのビューを設定
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setView(dialogView);

        // AlertDialogを表示
        AlertDialog dialog = builder.create();
        dialog.show();

        //radiobuttonの何が押されたか確認
        ((RadioGroup) dialogView.findViewById(R.id.radioGroup)).setOnCheckedChangeListener(
                new RadioGroup.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(RadioGroup radioGroup, int i) {
                        if (i == R.id.radioButton1) {
                            check = 1;
                            Log.d("ttttttttt", "checkOK" + check);
                        } else if (i == R.id.radioButton2) {
                            check = 2;
                            Log.d("ttttttttt", "checkOK" + check);

                        }else if(i==R.id.radioButton3){
                            check=3;
                        }else if(i==R.id.radioButton4){
                            check=4;
                        }
                    }
                }
        );


        dialogView.findViewById(R.id.okButton).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Log.d("tttttttttt","buttonOK");
                        lpa.setImageView(check);
                        Log.d("mone","kakunin");
                        dialog.dismiss();
                    }
                }
        );


    }

}
