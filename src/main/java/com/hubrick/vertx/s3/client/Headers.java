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
package com.hubrick.vertx.s3.client;

/**
 * @author Emir Dizdarevic
 * @since 3.0.0
 */
public class Headers {

    // Common request headers
    public static final String AUTHORIZATION = "Authorization";
    public static final String CONTENT_MD5 = "Content-MD5";
    // public static final String EXPECT = "Expect";
    public static final String HOST = "Host";
    public static final String X_AMZ_CONTENT_SHA256 = "x-amz-content-sha256";
    public static final String X_AMZ_DATE = "x-amz-date";
    public static final String X_AMZ_SECURITY_TOKEN = "x-amz-security-token";

    // Common response headers
    public static final String CONNECTION = "Connection";
    public static final String ETAG = "ETag";
    public static final String SERVER = "Server";
    public static final String X_AMZ_DELETE_MARKER = "x-amz-delete-marker";
    public static final String X_AMZ_ID_2 = "x-amz-id-2";
    public static final String X_AMZ_REQUEST_ID = "x-amz-request-id";
    public static final String X_AMZ_VERSION_ID = "x-amz-version-id";

    // Other headers
    public static final String CONTENT_LENGTH = "Content-Length";
    public static final String CONTENT_TYPE = "Content-Type";
    public static final String DATE = "Date";
    public static final String RANGE = "Range";
    public static final String IF_MODIFIED_SINCE = "If-Modified-Since";
    public static final String IF_UNMODIFIED_SINCE = "If-Unmodified-Since";
    public static final String IF_MATCH = "If-Match";
    public static final String IF_NONE_MATCH = "If-None-Match";
    public static final String CACHE_CONTROL = "Cache-Control";
    public static final String CONTENT_DISPOSITION = "Content-Disposition";
    public static final String CONTENT_ENCODING = "Content-Encoding";
    public static final String EXPIRES = "Expires";

    // Other amazon headers
    public static final String X_AMZ_META_PREFIX = "x-amz-meta-";
    public static final String X_AMZ_COPY_SOURCE = "x-amz-copy-source";
    public static final String X_AMZ_COPY_SOURCE_VERSION_ID = "x-amz-copy-source-version-id";
    public static final String X_AMZ_EXPIRATION = "x-amz-expiration";
    public static final String X_AMZ_MISSING_META = "x-amz-missing-meta";
    public static final String X_AMZ_REPLICATION_STATUS = "x-amz-replication-status";
    public static final String X_AMZ_RESTORE = "x-amz-restore";
    public static final String X_AMZ_SERVER_SIDE_ENCRYPTION = "x-amz-server-side-encryption";
    public static final String X_AMZ_SERVER_SIDE_ENCRYPTION_AWS_KMS_KEY_ID = "x-amz-server-side-encryption-aws-kms-key-id";
    public static final String X_AMZ_SERVER_SIDE_ENCRYPTION_CUSTOMER_ALGORITHM = "x-amz-server-side-encryption-customer-algorithm";
    public static final String X_AMZ_SERVER_SIDE_ENCRYPTION_CUSTOMER_KEY = "X-Amz-Server-Side-Encryption-Customer-Key";
    public static final String X_AMZ_SERVER_SIDE_ENCRYPTION_CUSTOMER_KEY_MD5 = "x-amz-server-side-encryption-customer-key-MD5";
    public static final String X_AMZ_STORAGE_CLASS = "x-amz-storage-class";
    public static final String X_AMZ_TAGGING_COUNT = "x-amz-tagging-count";
    public static final String X_AMZ_WEBSITE_REDIRECT_LOCATION = "x-amz-website-redirect-location";
    public static final String X_AMZ_TAGGING = "x-amz-tagging";
    public static final String X_AMZ_ACL = "x-amz-acl";
    public static final String X_AMZ_GRANT_READ = "x-amz-grant-read";
    public static final String X_AMZ_GRANT_WRITE = "x-amz-grant-write";
    public static final String X_AMZ_GRANT_READ_ACP = "x-amz-grant-read-acp";
    public static final String X_AMZ_GRANT_WRITE_ACP = "x-amz-grant-write-acp";
    public static final String X_AMZ_GRANT_FULL_CONTROL = "x-amz-grant-full-control";
    public static final String X_AMZ_METADATA_DIRECTIVE = "x-amz-metadata-directive";
    public static final String X_AMZ_COPY_SOURCE_IF_MATCH = "x-amz-copy-source-if-match";
    public static final String X_AMZ_COPY_SOURCE_IF_NONE_MATCH = "x-amz-copy-source-if-none-match";
    public static final String X_AMZ_COPY_SOURCE_IF_UNMODIFIED_SINCE = "x-amz-copy-source-if-unmodified-since";
    public static final String X_AMZ_COPY_SOURCE_IF_MODIFIED_SINCE = "x-amz-copy-source-if-modified-since";
    public static final String X_AMZ_TAGGING_DIRECTIVE = "x-amz-tagging-directive";
    public static final String X_AMZ_MFA = "x-amz-mfa";
    public static final String X_AMZ_ABORT_DATE = "x-amz-abort-date";
    public static final String X_AMZ_ABORT_RULE_ID = "x-amz-abort-rule-id";
}
