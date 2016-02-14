package com.yincheng.artresimageloader.loader;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.FileDescriptor;

/**
 * ImageResizer 图片缩放
 * Created by yinCheng on 2016/2/14 014.
 */
public class ImageResizer {

    private static final String TAG = "ImageResizer";

    public static Bitmap decodeSampleBitmapFromResource(Resources res,
                    int resId, int resWidth, int reqHeight) {
        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(res, resId, options);
        options.inSampleSize = calculateInSampleSize(options, resWidth, reqHeight);
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeResource(res, resId, options);
    }

    public static Bitmap decodeSampleBitmapFromFileDescriptor(FileDescriptor fd,
                    int reqWidth, int reqHeight){
        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFileDescriptor(fd, null, options);
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFileDescriptor(fd, null, options);
    }

    public static int calculateInSampleSize(BitmapFactory.Options options,
                     int reqWidth, int reqHeight) {
        // if reqWidth == 0 or reqHeight == 0, inSampleSize =1
        Log.d(TAG, "request, w= " + reqWidth + " h=" + reqHeight);
        if (reqWidth == 0 || reqHeight == 0) {
            return 1;
        }
        final int orgHeight = options.outHeight;
        final int orgWidth = options.outWidth;
        Log.d(TAG, "origin, w= " + orgWidth + " h=" + orgHeight);
        int inSampleSize = 1;
        if (orgHeight > reqHeight || orgWidth > reqWidth) {
            final int halfHeight = orgHeight / 2;
            final int halfWidth = orgWidth / 2;
            while ((halfHeight / inSampleSize) >= reqHeight &&
                    (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        Log.d(TAG, "inSampleSize:" + inSampleSize);
        return inSampleSize;
    }
}
