package com.demo.download;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.support.annotation.RequiresApi;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.widget.Toast;

import com.blankj.utilcode.util.LogUtils;
import com.blankj.utilcode.util.SPUtils;
import com.blankj.utilcode.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


/**
 * app内更新版本工具类
 */
public class AppDownloadUtils {

    private static final int DOWNLOAD_SUCCESS = 1;
    private static final int DOWNLOAD_FAIL = 2;
    private static final int DOWNLOAD_PROGRESS = 3;
    private static final int DOWNLOAD_CONNECT = 4;
    private static final String TOTALSIZE = "totalSize";
    private static final String DOWN_SIZE = "downSize";
    private static final String AUTHRITIES = "com.demo.download.fileprovider";

    private static final String SD_FOLDER = Environment.getExternalStorageDirectory() + "/version/";
    public static final int REQUEST_CODE_APP_INSTALL = 1010;

    private DownloadDialog mDownloadDialog;
    private final ThreadPoolExecutor mThreadPool = new ThreadPoolExecutor(5, 5, 50, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(2000));
    private boolean isDownLoder;
    private RandomAccessFile mAccessFile = null;
    private long totalSize;
    private long downSize;
    private SPUtils mSPUtils;
    private DownLoadThread mDownLoadThread;
    private File mFile;
    private Context mContext;
    private String downURL;
    private boolean isConnect = true; // 自动二次连接
    private String filePath;
    private static AppDownloadUtils sInstance;

    public static AppDownloadUtils getInstance(Context context) {
        if (sInstance == null) {
            synchronized (AppDownloadUtils.class) {
                if (sInstance == null) {
                    sInstance = new AppDownloadUtils(context);
                }
            }
        }
        return sInstance;
    }

    private AppDownloadUtils(Context context) {
        this.mContext = context;
        initialization();
    }

    public AppDownloadUtils setDownUrl(String url) {
        this.downURL = url;
        return this;
    }

    private void initialization() {
        mDownloadDialog = new DownloadDialog(mContext);
        mSPUtils = SPUtils.getInstance("AppDownloadUtils");
        mSPUtils.clear();
    }


    private int getContentLength() {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(downURL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            LogUtils.e("---getContentLength = " + connection.getResponseCode());
            if (connection.getResponseCode() == 200) {
                return connection.getContentLength();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try { //释放资源
                if (connection != null) {
                    connection.disconnect();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return 0;
    }

    private Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case DOWNLOAD_SUCCESS:
                    mFile = (File) msg.obj;
                    if (!StringUtils.isEmpty(filePath) && mFile != null && downSize == totalSize) {
                        AppDownloadUtils.this.stop();
                        installApk(mContext, mFile, filePath);
                    }
                    break;
                case DOWNLOAD_CONNECT:
                    isConnect = false;
                    Toast.makeText(mContext, "正在连接...", Toast.LENGTH_SHORT).show();
                    start();
                    break;
                case DOWNLOAD_FAIL:
                    Toast.makeText(mContext,"网络连接失败", Toast.LENGTH_SHORT).show();
                    stop();
                    break;
                case DOWNLOAD_PROGRESS:
                    mSPUtils.put(DOWN_SIZE, downSize);
                    mSPUtils.put(TOTALSIZE, totalSize);
                    mDownloadDialog.getmProgressBar().setProgress((int) downSize);
                    mDownloadDialog.getmTvPercentage().setText(downSize * 100 / totalSize + "%");
                    mDownloadDialog.getmTvSize().setText(bytes2kb(downSize) + "/" + bytes2kb(totalSize));
                    break;
            }
        }
    };

    /**
     * 从服务器下载最新更新文件
     *
     * @return
     * @throws Exception
     */
    private void downloadFile() {
        if (StringUtils.isEmpty(downURL)) {
            LogUtils.e("----url is null");
            return;
        }
        long contentLength = getContentLength();
        InputStream inputStream = null;
        HttpURLConnection connection;
        try {
            URL url = new URL(downURL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            totalSize = mSPUtils.getLong(TOTALSIZE);
            downSize = mSPUtils.getLong(DOWN_SIZE);
            /**
             * 下载进度与总大小相同或小于1 说明下载已完成或者第一次下载
             */
            if (downSize == totalSize || downSize < 1) {
                downSize = 0;
            }
            /**
             * 判断下载的文件大小跟上次下载的文件大小 是否 相同
             * 如果相同：则下载的是一个文件，可以直接从上次下载的地方继续下载 (断点续传)
             * 不相同： 则下载的不是一个文件，需要从新开始下载
             */
            if (totalSize != contentLength || totalSize < 1) {
                downSize = 0;
                totalSize = contentLength;
            }
            mSPUtils.put(TOTALSIZE, contentLength);
            //设置文件请求的位置 请求头格式 键值对 key : value range: bytes = 开始的位置 - 结束的位置
            connection.setRequestProperty("Range", "bytes=" + downSize + "-" + totalSize);
            mDownloadDialog.getmProgressBar().setMax((int) totalSize);
            mDownloadDialog.getmProgressBar().setProgress((int) downSize);
            // 如果以地址最后的文件命名，那么删除的时候就取这个名字删除
            String appName = downURL.substring(downURL.lastIndexOf("/"));
            filePath = SD_FOLDER + appName;
            File file = new File(filePath);
            if (!file.getParentFile().exists()) {//判断文件目录是否存在  
                file.getParentFile().mkdirs();
            }
            mAccessFile = new RandomAccessFile(file, "rw");
            mAccessFile.seek(downSize);
            inputStream = connection.getInputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = inputStream.read(buffer)) != -1 && isDownLoder) {
                mAccessFile.write(buffer, 0, len);
                downSize += len;
                // 获取当前下载量
                mHandler.sendEmptyMessage(DOWNLOAD_PROGRESS);
            }
            mHandler.sendMessage(mHandler.obtainMessage(DOWNLOAD_SUCCESS, file));
        } catch (IOException e) {
            e.printStackTrace();
            if (isNetworkConnected(mContext)) {
                if (isConnect) {
                    mHandler.sendEmptyMessage(DOWNLOAD_CONNECT);
                } else {
                    mHandler.sendEmptyMessage(DOWNLOAD_FAIL);
                }
            } else {
                mHandler.sendEmptyMessage(DOWNLOAD_FAIL);
            }
        } finally {
            try {
                if (mAccessFile != null) {
                    mAccessFile.close();
                }
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    //控制开始与下载
    public void start() {
        if (isNetworkConnected(mContext)) {
            downSize = mSPUtils.getLong(DOWN_SIZE);
            totalSize = mSPUtils.getLong(TOTALSIZE);
            // 如果下载完文件返回之后不用重新下载 直接安装(结束后台除外)
            if (!StringUtils.isEmpty(filePath) && mFile != null && downSize == totalSize) {
                installApk(mContext, mFile, filePath);
                return;
            }

            isDownLoder = true;
            mDownloadDialog.show();
            if (mDownLoadThread == null) {
                mDownLoadThread = new DownLoadThread();
            }
            mThreadPool.execute(mDownLoadThread);
        } else {
            stop();
            Toast.makeText(mContext, "请检查网络连接之后再试！", Toast.LENGTH_SHORT).show();
        }
    }

    //控制暂停下载
    public void stop() {
        isDownLoder = false;
        if (mDownloadDialog != null) {
            mDownloadDialog.dismiss();
        }
        if (mDownLoadThread != null) {
            mThreadPool.remove(mDownLoadThread);
            mDownLoadThread = null;
        }
        isConnect = true;
    }

    public File getmFile() {
        return mFile;
    }

    public String getFilePath() {
        return filePath;
    }

    public void deleteFile() {
        if (mFile != null) {
            mFile.delete();
        }
    }

    class DownLoadThread extends Thread {
        @Override
        public void run() {
            super.run();
            downloadFile();
        }
    }


    /**
     * 注意：自己配置fileprovider
     * 安装apk
     */
    public void installApk(Context context, File file, String filePath) {
        if (StringUtils.isEmpty(filePath)) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!isHasInstallPermissionWithO(context)) {
                startInstallPermissionSettingActivity(context);
                return;
            }
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri data;
        // 判断版本大于等于7.0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // "AUTHRITIES"即是在清单文件中配置的authorities
            data = FileProvider.getUriForFile(context, AUTHRITIES, file);
            // 给目标应用一个临时授权
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
//            data = Uri.fromFile(file);
            data = Uri.fromFile(new File(filePath));
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);// 防止打不开应用
        intent.setDataAndType(data, "application/vnd.android.package-archive");
        context.startActivity(intent);
    }


    @RequiresApi(api = Build.VERSION_CODES.O)
    public static boolean isHasInstallPermissionWithO(Context context) {
        if (context == null) return false;
        return context.getPackageManager().canRequestPackageInstalls();
    }

    /**
     * 开启设置安装未知来源应用权限界面
     *
     * @param context
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private static void startInstallPermissionSettingActivity(Context context) {
        if (context == null) return;
        Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
        ((Activity) context).startActivityForResult(intent, REQUEST_CODE_APP_INSTALL);
    }


    /**
     * byte(字节)根据长度转成kb(千字节)和mb(兆字节)
     *
     * @param bytes
     * @return
     */
    private static String bytes2kb(final long bytes) {
        BigDecimal filesize = new BigDecimal(bytes);
        BigDecimal megabyte = new BigDecimal(1024 * 1024);
        float returnValue = filesize.divide(megabyte, 2, BigDecimal.ROUND_UP).floatValue();
        if (returnValue > 1)
            return (returnValue + "MB");
        BigDecimal kilobyte = new BigDecimal(1024);
        returnValue = filesize.divide(kilobyte, 2, BigDecimal.ROUND_UP).floatValue();
        return (returnValue + "KB");
    }


    /**
     * 判断是否有网络连接
     *
     * @param context
     * @return
     */
    public static boolean isNetworkConnected(Context context) {
        if (context != null) {
            ConnectivityManager mConnectivityManager = (ConnectivityManager) context
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo mNetworkInfo = mConnectivityManager.getActiveNetworkInfo();
            if (mNetworkInfo != null) {
                return mNetworkInfo.isAvailable();
            }
        }
        return false;
    }


    // 判断是否为wifi网络
    public static boolean isWifi(Context mContext) {
        ConnectivityManager connectivityManager = (ConnectivityManager) mContext
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetInfo = connectivityManager.getActiveNetworkInfo();
        if (activeNetInfo != null
                && activeNetInfo.getType() == ConnectivityManager.TYPE_WIFI) {
            return true;
        }
        return false;
    }
}