package com.yincheng.artresimageloader.adapter;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;

import com.yincheng.artresimageloader.R;
import com.yincheng.artresimageloader.loader.ImageLoader;

import java.util.List;

/**
 * GradViewAdapter
 * Created by yinCheng on 2015/12/18 014.
 */
public class GradViewAdapter extends BaseAdapter {

    private List<String> picUrlList;
    private LayoutInflater mInflater;
    private Drawable mDefaultBitmapDrawable;
    private ImageLoader mImageLoader;

    private boolean mIsGridViewIdle;
    private boolean mCanGetBitmapFromNetWork;
    private int mImageWidth;
    private int mImageHeight;


    @SuppressWarnings("deprecation")
    public GradViewAdapter(Context mContext, List<String> picUrlList) {
        this.picUrlList = picUrlList;
        this.mInflater = LayoutInflater.from(mContext);
        this.mDefaultBitmapDrawable = mContext.getResources().getDrawable(R.drawable.image_default);
        this.mImageLoader = ImageLoader.build(mContext);

    }

    public void setmIsGridViewIdle(boolean mIsGridViewIdle) {
        this.mIsGridViewIdle = mIsGridViewIdle;
    }

    public void setmCanGetBitmapFromNetWork(boolean mCanGetBitmapFromNetWork) {
        this.mCanGetBitmapFromNetWork = mCanGetBitmapFromNetWork;
    }

    public void setmImageWidth(int mImageWidth) {
        this.mImageWidth = mImageWidth;
    }

    public void setmImageHeight(int mImageHeight) {
        this.mImageHeight = mImageHeight;
    }

    @Override
    public int getCount() {
        return picUrlList != null ? picUrlList.size() : 0;
    }

    @Override
    public String getItem(int position) {
        return picUrlList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder viewHolder = null;
        if (convertView == null) {
            viewHolder = new ViewHolder();
            convertView = mInflater.inflate(R.layout.image_list_item, null);

            viewHolder.imageView = (ImageView) convertView.findViewById(R.id.m_ImageView);

            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }

        ImageView imageView = viewHolder.imageView;
        final String tag = (String) imageView.getTag();
        final String uri = getItem(position);
        if (!uri.equals(tag)) {
            imageView.setImageDrawable(mDefaultBitmapDrawable);
        }
        //GradView处于停顿状态且网络下载允许，就加载图片
        if (mIsGridViewIdle && mCanGetBitmapFromNetWork) {
            imageView.setTag(uri);
            mImageLoader.bindBitmap(uri, imageView, mImageWidth, mImageHeight);
        }
        return convertView;
    }

    private static class ViewHolder {
        public ImageView imageView;
    }
}
