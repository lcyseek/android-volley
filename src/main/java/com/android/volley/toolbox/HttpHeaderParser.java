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

import com.android.volley.Cache;
import com.android.volley.NetworkResponse;

import org.apache.http.impl.cookie.DateParseException;
import org.apache.http.impl.cookie.DateUtils;
import org.apache.http.protocol.HTTP;

import java.util.Map;

/**
 * Utility methods for parsing HTTP headers.
 */
//Http header 的解析工具类，在 Volley 中主要作用是用于解析 Header 从而判断返回结果是否需要缓存，如果需要返回 Header 中相关信息。
public class HttpHeaderParser {

    /**
     * Extracts a {@link Cache.Entry} from a {@link NetworkResponse}.
     *
     * @param response The network response to parse headers from
     * @return a cache entry for the given response, or null if the response is not cacheable.
     */
    //通过网络响应中的缓存控制 Header 和 Body 内容，构建缓存实体。如果 Header 的 Cache-Control 字段含有no-cache或no-store表示不缓存，返回 null。
    //(1). 根据 Date 首部，获取响应生成时间
    //(2). 根据 ETag 首部，获取响应实体标签
    //(3). 根据 Cache－Control(单位秒) 和 Expires 首部，计算出缓存的过期时间，和缓存的新鲜度时间,Cache-Control使用的是相对时间，而expires使用的是绝对时间
    public static Cache.Entry parseCacheHeaders(NetworkResponse response) {
        long now = System.currentTimeMillis();

        Map<String, String> headers = response.headers;

        long serverDate = 0;
        long lastModified = 0;
        long serverExpires = 0;
        long softExpire = 0;
        long finalExpire = 0;
        long maxAge = 0;
        long staleWhileRevalidate = 0;
        boolean hasCacheControl = false;
        boolean mustRevalidate = false;

        String serverEtag = null;
        String headerValue;

        //获取"Date”字段，即服务器时间
        headerValue = headers.get("Date");
        if (headerValue != null) {
            serverDate = parseDateAsEpoch(headerValue);
        }

        //获取"Cache-Control”信息，即缓存过期时间
        headerValue = headers.get("Cache-Control");
        if (headerValue != null) {
            hasCacheControl = true;
            String[] tokens = headerValue.split(",");
            for (int i = 0; i < tokens.length; i++) {
                String token = tokens[i].trim();

                //如果不允许缓存，直接返回null
                if (token.equals("no-cache") || token.equals("no-store")) {
                    return null;
                } else if (token.startsWith("max-age=")) {
                    //“max-age=”为缓存过期时间
                    try {
                        maxAge = Long.parseLong(token.substring(8));
                    } catch (Exception e) {
                    }
                } else if (token.startsWith("stale-while-revalidate=")) {
                    //“stale-while-revalidate=”为过了缓存时间后还可以继续使用缓存的时间，即真正的缓存时间是“max-age=” + “stale-while-revalidate=”的总时间，
                    // 但如果有“must-revalidate”或者“proxy-revalidate”字段则过了缓存时间缓存就立即请求服务器
                    try {
                        staleWhileRevalidate = Long.parseLong(token.substring(23));
                    } catch (Exception e) {
                    }
                } else if (token.equals("must-revalidate") || token.equals("proxy-revalidate")) {
                    mustRevalidate = true;
                }
            }
        }

        //“Expires”字段是一个时间点，其实和Cache-Control的意思是一致的，控制缓存的过期时间的
        //如果有Cache-Control的话优先使用Cache-Control
        headerValue = headers.get("Expires");
        if (headerValue != null) {
            serverExpires = parseDateAsEpoch(headerValue);
        }

        //“Last-Modified”字段是服务器最后修改时间，如果缓存过期了，带上“Last-Modified”这个字段的时间与服务器的最后修改时间比较，
        // 如果服务器最后修改时间比“Last-Modified”字段的时间新，则重新获取，如果交旧，则返回304，继续用之前的缓存
        headerValue = headers.get("Last-Modified");
        if (headerValue != null) {
            lastModified = parseDateAsEpoch(headerValue);
        }

        //“ETag”字段是服务器资源的唯一标识符，与“Last-Modified”配合使用，因为“Last-Modified”只能精确到秒。
        // 如果“ETag”与服务器一致，再判断“Last-Modified”，防止一秒内服务器多次修改而导致数据不准确的情况
        serverEtag = headers.get("ETag");

        // Cache-Control takes precedence over an Expires header, even if both exist and Expires
        // is more restrictive.
        //如果支持Cache-Control则使用Cache-Control中的缓存验证信息，否则使用“Expires”中的验证信息
        if (hasCacheControl) {
            softExpire = now + maxAge * 1000;
            finalExpire = mustRevalidate
                    ? softExpire
                    : softExpire + staleWhileRevalidate * 1000;
        } else if (serverDate > 0 && serverExpires >= serverDate) {
            // Default semantic for Expire header in HTTP specification is softExpire.
            softExpire = now + (serverExpires - serverDate);
            finalExpire = softExpire;
        }

        //将服务器请求内容和验证信息都赋值给entry对象
        Cache.Entry entry = new Cache.Entry();
        entry.data = response.data;
        entry.etag = serverEtag;
        entry.softTtl = softExpire;
        entry.ttl = finalExpire;
        entry.serverDate = serverDate;
        entry.lastModified = lastModified;
        entry.responseHeaders = headers;

        return entry;
    }

    /**
     * Parse date in RFC1123 format, and return its value as epoch
     */
    //解析时间，将 RFC1123 的时间格式，解析成 epoch 时间
    public static long parseDateAsEpoch(String dateStr) {
        try {
            // Parse date in RFC1123 format if this header contains one
            return DateUtils.parseDate(dateStr).getTime();
        } catch (DateParseException e) {
            // Date in invalid format, fallback to 0
            return 0;
        }
    }

    /**
     * Retrieve a charset from headers
     *
     * @param headers An {@link java.util.Map} of headers
     * @param defaultCharset Charset to return if none can be found
     * @return Returns the charset specified in the Content-Type of this header,
     * or the defaultCharset if none can be found.
     */
    public static String parseCharset(Map<String, String> headers, String defaultCharset) {
        String contentType = headers.get(HTTP.CONTENT_TYPE);
        if (contentType != null) {
            String[] params = contentType.split(";");
            for (int i = 1; i < params.length; i++) {
                String[] pair = params[i].trim().split("=");
                if (pair.length == 2) {
                    if (pair[0].equals("charset")) {
                        return pair[1];
                    }
                }
            }
        }

        return defaultCharset;
    }

    /**
     * Returns the charset specified in the Content-Type of this header,
     * or the HTTP default (ISO-8859-1) if none can be found.
     */
    //解析编码集，在 Content-Type 首部中获取编码集，如果没有找到，默认返回 ISO-8859-1
    public static String parseCharset(Map<String, String> headers) {
        return parseCharset(headers, HTTP.DEFAULT_CONTENT_CHARSET);
    }
}
