/**
 * Copyright (C) 2016 Etaia AS (oss@hubrick.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hubrick.vertx.s3.util;

import com.google.common.base.Charsets;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author Emir Dizdarevic
 * @since 2.0.0
 */
public class UrlEncodingUtils {

    private static final Logger log = LoggerFactory.getLogger(UrlEncodingUtils.class);

    private static final String QUERY_CHAR = "?"; //$NON-NLS-1$
    private static final String ANCHOR_CHAR = "#"; //$NON-NLS-1$

    public static String addParamsSortedToUrl(String url, Map<String, String> params) {
        checkNotNull(url, "url must not be null");
        checkNotNull(!url.isEmpty(), "url must not be empty");
        checkNotNull(params, "params must not be null");
        checkNotNull(!params.isEmpty(), "params must not be empty");

        final String baseUrl = extractBaseUrl(url);
        final String urlParams = baseUrl.equals(url) ? "" : url.replace(baseUrl + "?", "");
        final List<NameValuePair> nameValuePairs = URLEncodedUtils.parse(urlParams, Charsets.UTF_8);

        for (Map.Entry<String, String> paramToUrlEncode : params.entrySet().stream().sorted(Comparator.comparing(Map.Entry::getKey)).collect(Collectors.toList())) {
            nameValuePairs.add(new BasicNameValuePair(paramToUrlEncode.getKey(), paramToUrlEncode.getValue()));
        }

        return baseUrl + "?" + URLEncodedUtils.format(nameValuePairs, Charsets.UTF_8);
    }

    public static String extractBaseUrl(String url) {
        if (url != null) {
            int queryPosition = url.indexOf(QUERY_CHAR);
            if (queryPosition <= 0) {
                queryPosition = url.indexOf(ANCHOR_CHAR);
            }

            if (queryPosition >= 0) {
                url = url.substring(0, queryPosition);
            }
        }
        return url;
    }
}
