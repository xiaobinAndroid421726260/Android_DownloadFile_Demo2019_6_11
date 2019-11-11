package com.demo.download;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.blankj.utilcode.util.LogUtils;

/**
 * description:
 *
 * @author Db_z
 * date 2019/6/14 13:22
 * @version V1.0
 */
public class MyInstalledReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        //接收安装广播
        if (intent.getAction().equals(Intent.ACTION_PACKAGE_ADDED)) {
            String packageName = intent.getDataString();
            LogUtils.e("安装了:" +packageName + "包名的程序");
            AppDownloadUtils.getInstance(context).deleteFile();
        }
    }
}
