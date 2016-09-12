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

package com.android.volley;

/**
 * Encapsulates a parsed response for delivery.
 *
 * @param <T> Parsed type of this response
 */
/** 网络请求结果的封装类.其中泛型T为网络解析结果. */
public class Response<T> {

    /** Callback interface for delivering parsed responses. */
    /** request请求成功回调接口, 用于用户自行处理网络请求返回的结果. */
    public interface Listener<T> {
        /** Called when a response is received. */
        public void onResponse(T response);
    }

    /** Callback interface for delivering error responses. */
    /** request请求失败回调接口，用于用户自行处理网络请求失败的情况. */
    public interface ErrorListener {
        /**
         * Callback method that an error has been occurred with the
         * provided error code and optional user-readable message.
         */
        public void onErrorResponse(VolleyError error);
    }

    /** Returns a successful response containing the parsed result. */
    /** 构造一个request请求成功的response对象. */
    public static <T> Response<T> success(T result, Cache.Entry cacheEntry) {
        return new Response<T>(result, cacheEntry);
    }

    /**
     * Returns a failed response containing the given error code and an optional
     * localized message displayed to the user.
     */
    /** 构造一个request请求失败的response对象. */
    public static <T> Response<T> error(VolleyError error) {
        return new Response<T>(error);
    }

    /** Parsed response, or null in the case of error. */
    /** request的网络请求解析结果. */
    public final T result;

    /** Cache metadata for this response, or null in the case of error. */
    /** response的缓存内容. */
    public final Cache.Entry cacheEntry;

    /** Detailed error information if <code>errorCode != OK</code>. */
    /** 请求错误内容. */
    public final VolleyError error;

    /** True if this response was a soft-expired one and a second one MAY be coming. */
    /** 当前结果是否为中间请求结果. */
    public boolean intermediate = false;

    /**
     * Returns whether this response is considered successful.
     */
    /** 返回当前request请求结果是否成功. */
    public boolean isSuccess() {
        return error == null;
    }


    private Response(T result, Cache.Entry cacheEntry) {
        this.result = result;
        this.cacheEntry = cacheEntry;
        this.error = null;
    }

    private Response(VolleyError error) {
        this.result = null;
        this.cacheEntry = null;
        this.error = error;
    }
}
