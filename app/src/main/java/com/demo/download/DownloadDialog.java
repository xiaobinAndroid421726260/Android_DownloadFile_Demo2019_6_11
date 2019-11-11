package com.demo.download;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * description:
 *
 * @author Db_z
 * date 2019/4/24 9:03
 * @version V1.0
 */
public class DownloadDialog extends AlertDialog {

    private ProgressBar mProgressBar;
    private TextView mTvPercentage;
    private TextView mTvSize;

    public DownloadDialog(Context context) {
        super(context, R.style.dialog);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_download);
        setCancelable(false);
        initView();
    }

    private void initView(){
        mProgressBar = findViewById(R.id.progressBar);
        mTvPercentage = findViewById(R.id.tv_percentage);
        mTvSize = findViewById(R.id.tv_size);
    }

    public ProgressBar getmProgressBar(){
        return mProgressBar;
    }

    public TextView getmTvPercentage(){
        return mTvPercentage;
    }

    public TextView getmTvSize(){
        return mTvSize;
    }
}