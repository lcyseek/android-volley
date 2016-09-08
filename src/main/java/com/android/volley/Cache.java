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

import java.util.Collections;
import java.util.Map;

/**
 * An interface for a cache keyed by a String with a byte array as data.
 */

/** 缓存的抽象接口 */
public interface Cache {
    /**
     * Retrieves an entry from the cache.
     * @param key Cache key
     * @return An {@link Entry} or null in the event of a cache miss
     */
    /** 通过key获取请求的缓存实体. */
    public Entry get(String key);

    /**
     * Adds or replaces an entry to the cache.
     * @param key Cache key
     * @param entry Data to store and metadata for cache coherency, TTL, etc.
     */
    /** 存入一个请求的缓存实体. */
    public void put(String key, Entry entry);

    /**
     * Performs any potentially long-running actions needed to initialize the cache;
     * will be called from a worker thread.
     */
    //初始化，扫描缓存目录得到所有缓存数据摘要信息放入内存。
    public void initialize();

    /**
     * Invalidates an entry in the cache.
     * @param key Cache key
     * @param fullExpire True to fully expire the entry, false to soft expire
     */
    public void invalidate(String key, boolean fullExpire);

    /**
     * Removes an entry from the cache.
     * @param key Cache key
     */
    /** 移除指定的缓存实体. */
    public void remove(String key);

    /**
     * Empties the cache.
     */
    /** 清空缓存. */
    public void clear();

    /**
     * Data and metadata for an entry returned by the cache.
     */
    /** 真正HTTP请求缓存实体类. */
    public static class Entry {
        /** The data returned from cache. */
        /** HTTP响应体. */
        public byte[] data;

        /** ETag for cache coherency. */
        /** HTTP响应首部中用于缓存新鲜度验证的ETag. */
        public String etag;

        /** Date of this response as reported by the server. */
        /** HTTP响应时间. */
        public long serverDate;

        /** The last modified date for the requested object. */
        /** 缓存内容最后一次修改的时间. */
        public long lastModified;

        /** TTL for this record. */
        /** Request的缓存过期时间. */
        public long ttl;

        /** Soft TTL for this record. */
        /** Request的缓存新鲜时间. */
        public long softTtl;

        /** Immutable response headers as received from server; must be non-null. */
        /** HTTP响应Headers. */
        public Map<String, String> responseHeaders = Collections.emptyMap();

        /** True if the entry is expired. */
        /** 判断缓存内容是否过期. */
        public boolean isExpired() {
            return this.ttl < System.currentTimeMillis();
        }

        /** True if a refresh is needed from the original data source. */
        /** 判断缓存是否新鲜，不新鲜的缓存需要发到服务端做新鲜度的检测. */
        public boolean refreshNeeded() {
            return this.softTtl < System.currentTimeMillis();
        }
    }

}
