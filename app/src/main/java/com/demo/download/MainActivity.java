package com.demo.download;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;


public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private String url = "apk下载地址";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
    }

    private void initView() {
        Button start = (Button) findViewById(R.id.start);
        Button stop = (Button) findViewById(R.id.stop);
        start.setOnClickListener(this);
        stop.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.start:
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    //申请WRITE_EXTERNAL_STORAGE权限
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            2);
                } else {
                    AppDownloadUtils.getInstance(this).setDownUrl(url).start();
                }
                break;
            case R.id.stop:
                AppDownloadUtils.getInstance(this).stop();
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // 8.0以上版本安装apk 获取未知来源为true才会继续下载安装
        if (requestCode == AppDownloadUtils.REQUEST_CODE_APP_INSTALL) {
//            if (StringUtils.isEmpty(url)) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (AppDownloadUtils.isHasInstallPermissionWithO(this)) {
                    if (AppDownloadUtils.getInstance(this).getmFile() != null) {
                        AppDownloadUtils.getInstance(this).installApk(this,
                                AppDownloadUtils.getInstance(this).getmFile(),
                                AppDownloadUtils.getInstance(this).getFilePath());
                    } else {
                        AppDownloadUtils.getInstance(this).setDownUrl(url).start();
                    }
                }
            }
        }
    }
}