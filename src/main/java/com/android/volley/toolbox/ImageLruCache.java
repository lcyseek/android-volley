package com.android.volley.toolbox;

import android.graphics.Bitmap;
import android.os.Build;
import android.util.LruCache;

/** Lru算法的L1缓存实现类.
 * 这个类不是Volley里面的!!!*/
@SuppressWarnings("unused")
public class ImageLruCache implements ImageLoader.ImageCache {
    private LruCache<String, Bitmap> mLruCache;

    public ImageLruCache() {
        this((int) Runtime.getRuntime().maxMemory() / 8);
    }

    public ImageLruCache(final int cacheSize) {
        createLruCache(cacheSize);
    }

    private void createLruCache(final int cacheSize) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) {
            mLruCache = new LruCache<String, Bitmap>(cacheSize) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getRowBytes() * value.getHeight();
                }
            };
        }
    }

    @Override
    public Bitmap getBitmap(String url) {
        return mLruCache.get(url);
    }

    @Override
    public void putBitmap(String url, Bitmap bitmap) {
        mLruCache.put(url, bitmap);
    }
}
