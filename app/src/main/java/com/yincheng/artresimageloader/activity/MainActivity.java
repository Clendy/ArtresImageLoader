package com.yincheng.artresimageloader.activity;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.AbsListView;
import android.widget.GridView;

import com.yincheng.artresimageloader.R;
import com.yincheng.artresimageloader.adapter.GradViewAdapter;
import com.yincheng.artresimageloader.constant.PicUrlConstants;
import com.yincheng.artresimageloader.util.MyUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity implements AbsListView.OnScrollListener {

    private static final String TAG = "MainActivity";

    @Bind(R.id.m_GridView)
    GridView mGridView;

    private int mImageWidth = 0;
    private boolean mIsWifi = false;

    private List<String> mPicUrlList = new ArrayList<>();

    private GradViewAdapter mGradViewAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        initData();
        initView();
    }

    private void initData(){
        Collections.addAll(mPicUrlList, PicUrlConstants.imageUrls);
        int screenWidth = MyUtils.getScreenMetrics(this).widthPixels;
        int space = (int)MyUtils.dp2px(this, 20f);
        mImageWidth = (screenWidth - space) / 3;
        Log.d(TAG, "screenWidth:"+screenWidth+", space:"+space+", mImageWidth:"+mImageWidth);
        mIsWifi = MyUtils.isWifi(this);
    }

    private void initView(){
        mGradViewAdapter = new GradViewAdapter(this, mPicUrlList);
        mGradViewAdapter.setmIsGridViewIdle(true);
        if (mIsWifi){
            mGradViewAdapter.setmCanGetBitmapFromNetWork(true);
        }
        mGradViewAdapter.setmImageWidth(mImageWidth);
        mGradViewAdapter.setmImageHeight(mImageWidth);
        mGridView.setAdapter(mGradViewAdapter);
        mGridView.setOnScrollListener(this);
        if (!mIsWifi){
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("注意");
            builder.setMessage("初次使用会从网络下载大概5MB的图片，确认要下载吗？");
            builder.setPositiveButton("是", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    mGradViewAdapter.setmCanGetBitmapFromNetWork(true);
                    mGradViewAdapter.notifyDataSetChanged();
                }
            });
            builder.setNegativeButton("否", null);
            builder.show();
        }
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
            mGradViewAdapter.setmIsGridViewIdle(true);
            mGradViewAdapter.notifyDataSetChanged();
        } else {
            mGradViewAdapter.setmIsGridViewIdle(false);
        }
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        // ignored
    }
}
