package com.yincheng.artresimageloader.loader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.StatFs;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;

import com.yincheng.artresimageloader.util.MyUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ImageLoader
 * Created by yinCheng on 2016/2/14 014.
 */
public class ImageLoader {

    private static final String TAG = "ImageLoader";

    private Context mContext;
    private LruCache<String, Bitmap> mMemoryCache;
    private DiskLruCache mDiskLruCache;

    private static final long DISK_CACHE_SIZE = 1024 * 1024 * 50;
    private static final int IO_BUFFER_SIZE = 8 * 1024;
    private static final int DISK_CACHE_INDEX = 0;
    private boolean mIsmDiskLruCacheCreated = false;

    private static final int MESSAGE_POST_RESULT = 1;

    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE = CPU_COUNT + 1;
    private static final int MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1;
    private static final long KEEP_ALIVE = 10L;

    private WeakReference<ImageView> mImageViewReference;

    private static final ThreadFactory mThreadFactory = new ThreadFactory() {
        private final AtomicInteger mCount = new AtomicInteger(1);//自动增长int型变量

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "ImageLoader#" + mCount.getAndIncrement());
        }
    };

    public static final Executor THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(
            CORE_POOL_SIZE,
            MAXIMUM_POOL_SIZE,
            KEEP_ALIVE,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>(),
            mThreadFactory);

    private static Handler mMainHandler = new Handler(Looper.getMainLooper()) {

        @Override
        public void handleMessage(Message msg) {
            LoaderResult result = (LoaderResult) msg.obj;
            ImageView imageView = result.imageView;
            String uri = (String) imageView.getTag(imageView.getId());
            if (uri != null && uri.equals(result.uri)) {
                imageView.setImageBitmap(result.bitmap);
            } else {
                Log.w(TAG, "set image bitmap,but url has changed, ignored!");
            }
        }
    };


    private ImageLoader(Context context) {

        mContext = context.getApplicationContext();
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = maxMemory / 8;
        // Created memory cache by LruCache
        mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {

            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getRowBytes() * value.getHeight() / 1024;
            }
        };

        //Created Disk cache by DiskLruCache
        File diskCacheDir = getDiskCacheDir(mContext, "bitmap");
        if (!diskCacheDir.exists()) {
            diskCacheDir.mkdirs();
        }
        if (getUsableSpace(diskCacheDir) > DISK_CACHE_SIZE) {
            try {
                mDiskLruCache = DiskLruCache.open(diskCacheDir, 1, 1, DISK_CACHE_SIZE);
                mIsmDiskLruCacheCreated = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static ImageLoader build(Context context) {
        return new ImageLoader(context);
    }

    public void bindBitmap(final String uri, final ImageView imageView) {
        bindBitmap(uri, imageView, 0, 0);
    }

    public void bindBitmap(final String uri, final ImageView imageView,
                           final int reqWidth, final int reqHeight) {

        imageView.setTag(imageView.getId(), uri);
        final Bitmap bitmap = loadBitmapFromeMemoryCache(uri);
        if (null != bitmap) {
            imageView.setImageBitmap(bitmap);
            return;
        }

        //
        int pixelW = 0;
        int pixelH = 0;
        int imageViewW = imageView.getWidth() == 0 ? imageView.getHeight() : imageView.getWidth();
        int imageViewH = imageView.getHeight() == 0 ? imageView.getWidth() : imageView.getHeight();
        Log.d(TAG, "imageViewW:" + imageViewW + ", imageViewH:" + imageViewH);
        Log.d(TAG, "reqWidth:" + reqWidth + ", reqHeight:" + reqHeight);
        if (reqWidth > 0 && reqHeight > 0 && imageViewW > 0 && imageViewH > 0) { //如果指定压缩高宽大于0
            //如果ImageView的高宽有一个小于指定压缩高宽，就以ImageView的高宽为压缩基准
            if (imageViewW < reqWidth || imageViewH < reqHeight) {
                pixelW = imageViewW;
                pixelH = imageViewH;
                Log.i("TAG", "以ImageView高宽为压缩基准");
            } else { //否则就依然以指定高宽为压缩基准
                pixelW = reqWidth;
                pixelH = reqHeight;
                Log.i("TAG", "以指定高宽为压缩基准");
            }
        } else if (reqWidth <= 0 && reqWidth <= 0 && imageViewW > 0 && imageViewH > 0){ //如果指定压缩高宽小于0，就依然以ImageView的大小为压缩基准
            pixelW = imageViewW;
            pixelH = imageViewH;
            Log.i("TAG", "以ImageView高宽为压缩基准");
        } else if (reqWidth >0 && reqHeight >0 && imageViewW == 0 && imageViewH == 0) {
            pixelW = reqWidth;
            pixelH = reqHeight;
            Log.i("TAG", "以指定高宽为压缩基准");
        }
        Log.d(TAG, "pixelW:" + pixelW + ", pixelH:" + pixelH);
        final int finalPixelW = pixelW;
        final int finalPixelH = pixelH;

        Runnable loadBitmapTask = new Runnable() {
            @Override
            public void run() {
//                Bitmap bitmap = loadBitmap(uri, reqWidth, reqHeight);
                Bitmap bitmap = loadBitmap(uri, finalPixelW, finalPixelH);
                if (bitmap != null) {
                    LoaderResult result = new LoaderResult(imageView, uri, bitmap);
                    mMainHandler.obtainMessage(MESSAGE_POST_RESULT, result).sendToTarget();

                }
            }
        };
        THREAD_POOL_EXECUTOR.execute(loadBitmapTask);
    }


    public Bitmap loadBitmap(String url, int reqWidth, int reqHeight) {
        Bitmap bitmap;
        bitmap = loadBitmapFromeMemoryCache(url);
        if (null != bitmap) {
            Log.d(TAG, "loadBitmapFromMemCache,url:" + url);
            return bitmap;
        }
        bitmap = loadBitmapFromeDiskCache(url, reqWidth, reqHeight);
        if (null != bitmap) {
            Log.d(TAG, "loadBitmapFromDisk,url:" + url);
            return bitmap;
        }
        bitmap = loadBitmapFromHttp(url, reqWidth, reqHeight);
        if (null != bitmap) {
            Log.d(TAG, "loadBitmapFromHttp,url:" + url);
            return bitmap;
        }
        if (bitmap == null && !mIsmDiskLruCacheCreated) {
            Log.w(TAG, "encounter error, DiskLruCache is not created.");
            bitmap = downloadBitmapFromUrl(url);
        }
        return bitmap;
    }


    private Bitmap downloadBitmapFromUrl(String urlString) {
        Bitmap bitmap = null;
        HttpURLConnection urlConnection = null;
        BufferedInputStream in = null;
        try {
            final URL url = new URL(urlString);
            urlConnection = (HttpURLConnection) url.openConnection();
            in = new BufferedInputStream(urlConnection.getInputStream(), IO_BUFFER_SIZE);
            bitmap = BitmapFactory.decodeStream(in);
        } catch (IOException e) {
            Log.e(TAG, "Error in downloadBitmap: " + e);
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            MyUtils.close(in);
        }
        return bitmap;
    }

    /***********************
     * load Bitmap from cache
     **************************/
    private Bitmap loadBitmapFromeMemoryCache(String url) {
        final String key = hashKeyFromUrl(url);
        Bitmap bitmap = getBitmapFromMemeryCache(key);
        return bitmap;
    }

    private Bitmap loadBitmapFromeDiskCache(String url, int reqWidth, int reqHeight) {
        //判断当前线程中的Looper对象是否为主线程的Looper
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.w(TAG, "load bitmap from UI Thread, it's not recommended!");
        }
        if (mDiskLruCache == null) {
            return null;
        }
        Bitmap bitmap = null;
        String key = hashKeyFromUrl(url);
        FileInputStream in = null;
        try {
            DiskLruCache.Snapshot snapShot = mDiskLruCache.get(key);
            if (null != snapShot) {
                in = (FileInputStream) snapShot.getInputStream(DISK_CACHE_INDEX);
                FileDescriptor fd = in.getFD();
                bitmap = ImageResizer.decodeSampleBitmapFromFileDescriptor(fd, reqWidth, reqHeight);
                if (null != bitmap) {
                    addBitmapToMemoryCache(key, bitmap);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            MyUtils.close(in);
        }
        return bitmap;
    }

    private Bitmap loadBitmapFromHttp(String url, int reqWidth, int reqHeight) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new RuntimeException("can not visit network from UI Thread.");
        }
        if (mDiskLruCache == null) {
            return null;
        }
        String key = hashKeyFromUrl(url);
        try {
            DiskLruCache.Editor editor = mDiskLruCache.edit(key);
            if (null != editor) {
                OutputStream out = editor.newOutputStream(DISK_CACHE_INDEX);
                if (downloadUrlToStream(url, out)) {
                    editor.commit();
                } else {
                    editor.abort();
                }
                mDiskLruCache.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return loadBitmapFromeDiskCache(url, reqWidth, reqHeight);
    }

    /****************** Memory cache ***********************/
    /**
     * 添加图片至内存缓存中
     *
     * @param key    图片url对应的key
     * @param bitmap 添加的Bitmap对象
     */
    private void addBitmapToMemoryCache(String key, Bitmap bitmap) {
        //如果内存缓存中没有这个图片就添加至缓存
        if (getBitmapFromMemeryCache(key) == null) {
            mMemoryCache.put(key, bitmap);
        }
    }

    /**
     * 从内存缓存中获取图片
     *
     * @param key 图片url对应的key
     * @return
     */
    private Bitmap getBitmapFromMemeryCache(String key) {
        return mMemoryCache.get(key);
    }


    /****************** Disk memory cache *****************/

    /**
     * 1.创建磁盘缓存区域
     *
     * @param context
     * @param uniqueName
     * @return
     */
    public File getDiskCacheDir(Context context, String uniqueName) {
        boolean externalStorageAvailable = Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
        final String cachePath;
        if (externalStorageAvailable) {
            cachePath = context.getExternalCacheDir().getPath();
        } else {
            cachePath = context.getCacheDir().getPath();
        }
        return new File(cachePath + File.separator + uniqueName);
    }

    /**
     * 获取可用的磁盘剩余空间
     *
     * @param path
     * @return
     */
    @SuppressWarnings("deprecation")
    private long getUsableSpace(File path) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            return path.getUsableSpace();
        }
        final StatFs stats = new StatFs(path.getPath());
        return (long) stats.getBlockSize() * (long) stats.getAvailableBlocks();
    }

    /**
     * 根据图片url使用MD5加密生成key
     *
     * @param url
     * @return
     */
    private String hashKeyFromUrl(String url) {
        String cacheKey;
        try {
            final MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(url.getBytes());
            cacheKey = bytesToHexString(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            cacheKey = String.valueOf(url.hashCode());
        }
        return cacheKey;
    }

    /**
     * 字节数组转16进制字符串
     *
     * @param bytes
     * @return
     */
    private String bytesToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            String hex = Integer.toHexString(0xFF & bytes[i]);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    /**
     * 文件输出流写入文件系统
     *
     * @param url
     */
    private void editOutputStream(String url) {
        String key = hashKeyFromUrl(url);
        try {
            DiskLruCache.Editor editor = mDiskLruCache.edit(key);
            if (null != editor) {
                OutputStream out = editor.newOutputStream(DISK_CACHE_INDEX);
                if (downloadUrlToStream(url, out)) {
                    editor.commit();
                } else {
                    editor.abort();
                }
            }
            mDiskLruCache.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 由图片url下载图片写入OutputStream
     *
     * @param urlString
     * @param outputStream
     * @return
     */
    private boolean downloadUrlToStream(String urlString, OutputStream outputStream) {

        HttpURLConnection urlConnection = null;
        BufferedOutputStream out = null;
        BufferedInputStream in = null;
        try {
            final URL url = new URL(urlString);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setConnectTimeout(5000);
            urlConnection.setRequestMethod("GET");
            if (urlConnection.getResponseCode() == HttpURLConnection.HTTP_OK){
                in = new BufferedInputStream(urlConnection.getInputStream(), IO_BUFFER_SIZE);
                out = new BufferedOutputStream(outputStream, IO_BUFFER_SIZE);
                byte[] buffer = new byte[1024];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
                return true;
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
//        Log.e(TAG, "downloadBitmap failed." + e);
            e.printStackTrace();
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            MyUtils.close(out);
            MyUtils.close(in);
        }
        return false;
    }

    private static class LoaderResult {
        public ImageView imageView;
        public String uri;
        public Bitmap bitmap;

        public LoaderResult(ImageView imageView, String uri, Bitmap bitmap) {
            this.imageView = imageView;
            this.uri = uri;
            this.bitmap = bitmap;
        }
    }

}
