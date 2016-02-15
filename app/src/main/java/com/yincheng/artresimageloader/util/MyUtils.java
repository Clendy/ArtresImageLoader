package com.yincheng.artresimageloader.util;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.WindowManager;
import android.widget.ImageView;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

/**
 * MyUtils
 * Created by yinCheng on 2016/2/14 014.
 */
public class MyUtils {

    /**
     * 获取进场名称
     * @param context
     * @param pid
     * @return
     */
    public static String getProcessName(Context context, int pid) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<RunningAppProcessInfo> runningApps = am.getRunningAppProcesses();
        if (runningApps == null) {
            return null;
        }
        for (RunningAppProcessInfo procInfo : runningApps) {
            if (procInfo.pid == pid) {
                return procInfo.processName;
            }
        }
        return null;
    }

    /**
     * 关闭资源
     * @param closeable
     */
    public static void close(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取设备屏幕参数
     *
     * @param context
     * @return
     */
    public static DisplayMetrics getScreenMetrics(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(dm);
        return dm;
    }

    /**
     * dp转px
     *
     * @param context
     * @param dp
     * @return
     */
    public static float dp2px(Context context, float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                context.getResources().getDisplayMetrics());
    }

    /**
     * 判断网络类型是否为WiFi
     * @param context
     * @return
     */
    public static boolean isWifi(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetInfo = connectivityManager.getActiveNetworkInfo();
        if (activeNetInfo != null
                && activeNetInfo.getType() == ConnectivityManager.TYPE_WIFI) {
            return true;
        }
        return false;
    }

    /**
     * 开启子线程任务
     *
     * @param runnable
     */
    public static void executeInThread(Runnable runnable) {
        new Thread(runnable).start();
    }

    /**
     * 根据ImageView大小以及需要压缩的大小，计算出合适的压缩大小参数
     * @param imageView
     * @param reqWidth
     * @param reqHeight
     * @return
     */
    public static int[] calculateCompressParams(ImageView imageView, int reqWidth, int reqHeight){
        //设置压缩参数
        int[] compressParams = new int[2];
        int imageViewW = imageView.getWidth() == 0 ? imageView.getHeight() : imageView.getWidth();
        int imageViewH = imageView.getHeight() == 0 ? imageView.getWidth() : imageView.getHeight();
        if (reqWidth > 0 && reqHeight > 0 && imageViewW > 0 && imageViewH > 0) { //如果指定压缩高宽大于0
            //如果ImageView的高宽有一个小于指定压缩高宽，就以ImageView的高宽为压缩基准
            if (imageViewW < reqWidth || imageViewH < reqHeight) {
                compressParams[0] = imageViewW;
                compressParams[1] = imageViewH;
            } else { //否则就依然以指定高宽为压缩基准
                compressParams[0] = reqWidth;
                compressParams[1] = reqHeight;
            }
        } else if (reqWidth <= 0 && reqWidth <= 0 && imageViewW > 0 && imageViewH > 0) {
            //如果指定压缩高宽小于0，就依然以ImageView的大小为压缩基准
            compressParams[0] = imageViewW;
            compressParams[1] = imageViewH;
        } else if (reqWidth > 0 && reqHeight > 0 && imageViewW == 0 && imageViewH == 0) {
            compressParams[0] = reqWidth;
            compressParams[1] = reqHeight;
        }
        return compressParams;
    }

}
