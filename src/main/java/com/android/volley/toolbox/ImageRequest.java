/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.volley.toolbox;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.widget.ImageView.ScaleType;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyLog;

/**
 * A canned request for getting an image at a given URL and calling
 * back with a decoded Bitmap.
 */
public class ImageRequest extends Request<Bitmap> {
    /** Socket timeout in milliseconds for image requests */
    /** 默认图片获取的超时时间(单位:毫秒) */
    private static final int IMAGE_TIMEOUT_MS = 1000;

    /** Default number of retries for image requests */
    /** 默认图片获取的重试次数. */
    private static final int IMAGE_MAX_RETRIES = 2;

    /** Default backoff multiplier for image requests */
    private static final float IMAGE_BACKOFF_MULT = 2f;

    private final Response.Listener<Bitmap> mListener;
    private final Config mDecodeConfig;
    private final int mMaxWidth;
    private final int mMaxHeight;
    private ScaleType mScaleType;

    /** Decoding lock so that we don't decode more than one image at a time (to avoid OOM's) */
    /** Bitmap解析同步锁,保证同一时间只有一个Bitmap被load到内存进行解析,防止OOM. */
    private static final Object sDecodeLock = new Object();

    /**
     * Creates a new image request, decoding to a maximum specified width and
     * height. If both width and height are zero, the image will be decoded to
     * its natural size. If one of the two is nonzero, that dimension will be
     * clamped and the other one will be set to preserve the image's aspect
     * ratio. If both width and height are nonzero, the image will be decoded to
     * be fit in the rectangle of dimensions width x height while keeping its
     * aspect ratio.
     *
     * @param url URL of the image
     * @param listener Listener to receive the decoded bitmap
     * @param maxWidth Maximum width to decode this bitmap to, or zero for none
     * @param maxHeight Maximum height to decode this bitmap to, or zero for
     *            none
     * @param scaleType The ImageViews ScaleType used to calculate the needed image size.
     * @param decodeConfig Format to decode the bitmap to
     * @param errorListener Error listener, or null to ignore errors
     */

    /**
     * 构造一个网络图片请求.
     * @param url 图片的url地址.
     * @param listener 请求成功用户设置的回调接口.
     * @param maxWidth 图片的最大宽度.
     * @param maxHeight 图片的最大高度.
     * @param scaleType 图片缩放类型.
     * @param decodeConfig 解析bitmap的配置.
     * @param errorListener 请求失败用户设置的回调接口.
     */
    //如果设定的宽和高都是0，那么下载到的图片将会按照实际的大小进行解码，也就是不压缩。
    //如果宽和高中的一个或两个值不为0，那么图片的宽/高（取决于你设定了宽还是高）会压缩至设定好的值，而另一个宽/高将会按原始比例改变。
    //如果宽和高都不是0，那么得到的图片将会“按比例”解码到你设定的宽高，也就是说最终得到的图片大小不一定是你最初设定的大小。
    /**
     * 当maxWidth = 0，maxHeight = 0时，最终得到的bitmap的宽高是850x1200
     * 当maxWidth = 0，maxHeight = 600时，得到的bitmap是425x600.这就说明它会按照一个不为0的边的值，将图片进行等比缩放。
     * 当maxWidth = 100，maxHeight = 600时，我们得到的bitmap竟然是100x141，是按照100进行等比缩小后的图片，而不是100x600.
     *
     * */
    public ImageRequest(String url, Response.Listener<Bitmap> listener, int maxWidth, int maxHeight,
            ScaleType scaleType, Config decodeConfig, Response.ErrorListener errorListener) {
        super(Method.GET, url, errorListener); 
        setRetryPolicy(
                new DefaultRetryPolicy(IMAGE_TIMEOUT_MS, IMAGE_MAX_RETRIES, IMAGE_BACKOFF_MULT));
        mListener = listener;
        mDecodeConfig = decodeConfig;
        mMaxWidth = maxWidth;
        mMaxHeight = maxHeight;
        mScaleType = scaleType;
    }

    /**
     * For API compatibility with the pre-ScaleType variant of the constructor. Equivalent to
     * the normal constructor with {@code ScaleType.CENTER_INSIDE}.
     */
    @Deprecated
    public ImageRequest(String url, Response.Listener<Bitmap> listener, int maxWidth, int maxHeight,
            Config decodeConfig, Response.ErrorListener errorListener) {
        this(url, listener, maxWidth, maxHeight,
                ScaleType.CENTER_INSIDE, decodeConfig, errorListener);
    }
    @Override
    public Priority getPriority() {
        return Priority.LOW;
    }

    /**
     * Scales one side of a rectangle to fit aspect ratio.
     *
     * @param maxPrimary Maximum size of the primary dimension (i.e. width for
     *        max width), or zero to maintain aspect ratio with secondary
     *        dimension
     * @param maxSecondary Maximum size of the secondary dimension, or zero to
     *        maintain aspect ratio with primary dimension
     * @param actualPrimary Actual size of the primary dimension
     * @param actualSecondary Actual size of the secondary dimension
     * @param scaleType The ScaleType used to calculate the needed image size.
     */
    //它最终确定了图片的最终尺寸。
    private static int getResizedDimension(int maxPrimary, int maxSecondary, int actualPrimary,
            int actualSecondary, ScaleType scaleType) {

        // If no dominant value at all, just return the actual.
        if ((maxPrimary == 0) && (maxSecondary == 0)) {
            return actualPrimary;
        }

        // If ScaleType.FIT_XY fill the whole rectangle, ignore ratio.
        if (scaleType == ScaleType.FIT_XY) {
            if (maxPrimary == 0) {
                return actualPrimary;
            }
            return maxPrimary;
        }

        // If primary is unspecified, scale primary to match secondary's scaling ratio.
        if (maxPrimary == 0) {
            double ratio = (double) maxSecondary / (double) actualSecondary;
            return (int) (actualPrimary * ratio);
        }

        if (maxSecondary == 0) {
            return maxPrimary;
        }

        double ratio = (double) actualSecondary / (double) actualPrimary;
        int resized = maxPrimary;

        // If ScaleType.CENTER_CROP fill the whole rectangle, preserve aspect ratio.
        if (scaleType == ScaleType.CENTER_CROP) {
            if ((resized * ratio) < maxSecondary) {
                resized = (int) (maxSecondary / ratio);
            }
            return resized;
        }

        if ((resized * ratio) > maxSecondary) {
            resized = (int) (maxSecondary / ratio);
        }
        return resized;
    }

    @Override
    protected Response<Bitmap> parseNetworkResponse(NetworkResponse response) {
        // Serialize all decode on a global lock to reduce concurrent heap usage.
        synchronized (sDecodeLock) {
            try {
                return doParse(response);
            } catch (OutOfMemoryError e) {
                VolleyLog.e("Caught OOM for %d byte image, url=%s", response.data.length, getUrl());
                return Response.error(new ParseError(e));
            }
        }
    }

    /**
     * The real guts of parseNetworkResponse. Broken out for readability.
     */
    private Response<Bitmap> doParse(NetworkResponse response) {
        byte[] data = response.data;
        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        Bitmap bitmap = null;
        if (mMaxWidth == 0 && mMaxHeight == 0) {
            // 如果宽高都是0，那么就返回原始尺寸
            decodeOptions.inPreferredConfig = mDecodeConfig;
            bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, decodeOptions);
        } else {
            // If we have to resize this image, first get the natural bounds.
            // 如果我们已经重设了image的尺寸（宽高中有一个或两个不为0），那么先得到原始的大小
            decodeOptions.inJustDecodeBounds = true;

            // 第一次解码，主要获得的是bitmap的实际宽、高
            BitmapFactory.decodeByteArray(data, 0, data.length, decodeOptions);
            int actualWidth = decodeOptions.outWidth;
            int actualHeight = decodeOptions.outHeight;

            // Then compute the dimensions we would ideally like to decode to.
            // 然后计算我们想要得到的最终尺寸
            int desiredWidth = getResizedDimension(mMaxWidth, mMaxHeight,
                    actualWidth, actualHeight, mScaleType);
            int desiredHeight = getResizedDimension(mMaxHeight, mMaxWidth,
                    actualHeight, actualWidth, mScaleType);

            // Decode to the nearest power of two scaling factor.
            // 把图片解码到最接近2的幂次方的大小
            decodeOptions.inJustDecodeBounds = false;
            // TODO(ficus): Do we need this or is it okay since API 8 doesn't support it?
            // decodeOptions.inPreferQualityOverSpeed = PREFER_QUALITY_OVER_SPEED;
            decodeOptions.inSampleSize =
                findBestSampleSize(actualWidth, actualHeight, desiredWidth, desiredHeight);
            Bitmap tempBitmap =
                BitmapFactory.decodeByteArray(data, 0, data.length, decodeOptions);

            // If necessary, scale down to the maximal acceptable size.
            // 如果有必要的话，把得到的bitmap的最大边进行压缩来适应尺寸
            if (tempBitmap != null && (tempBitmap.getWidth() > desiredWidth ||
                    tempBitmap.getHeight() > desiredHeight)) {
                // 通过createScaledBitmap来压缩到目标尺寸
                bitmap = Bitmap.createScaledBitmap(tempBitmap,
                        desiredWidth, desiredHeight, true);
                tempBitmap.recycle();
            } else {
                bitmap = tempBitmap;
            }
        }

        if (bitmap == null) {
            return Response.error(new ParseError(response));
        } else {
            return Response.success(bitmap, HttpHeaderParser.parseCacheHeaders(response));
        }
    }

    @Override
    protected void deliverResponse(Bitmap response) {
        mListener.onResponse(response);
    }

    /**
     * Returns the largest power-of-two divisor for use in downscaling a bitmap
     * that will not result in the scaling past the desired dimensions.
     *
     * @param actualWidth Actual width of the bitmap
     * @param actualHeight Actual height of the bitmap
     * @param desiredWidth Desired width of the bitmap
     * @param desiredHeight Desired height of the bitmap
     */
    // Visible for testing.
    static int findBestSampleSize(
            int actualWidth, int actualHeight, int desiredWidth, int desiredHeight) {
        // 计算inSampleSize的方法，详细知识自行百度吧。最终原图会被压缩为inSampleSize分之一
        // inSampleSize的值计算出来都是2的幂次方
        double wr = (double) actualWidth / desiredWidth;
        double hr = (double) actualHeight / desiredHeight;
        double ratio = Math.min(wr, hr);
        float n = 1.0f;
        while ((n * 2) <= ratio) {
            n *= 2;
        }

        return (int) n;
    }
}
