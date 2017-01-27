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

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.hubrick.vertx.s3.S3Headers;
import com.hubrick.vertx.s3.exception.HttpErrorException;
import com.hubrick.vertx.s3.model.CommonPrefixes;
import com.hubrick.vertx.s3.model.Contents;
import com.hubrick.vertx.s3.model.CopyObjectRequest;
import com.hubrick.vertx.s3.model.DeleteObjectRequest;
import com.hubrick.vertx.s3.model.ErrorResponse;
import com.hubrick.vertx.s3.model.GetBucketRequest;
import com.hubrick.vertx.s3.model.GetBucketRespone;
import com.hubrick.vertx.s3.model.GetObjectRequest;
import com.hubrick.vertx.s3.model.Owner;
import com.hubrick.vertx.s3.model.PutObjectRequest;
import com.hubrick.vertx.s3.model.filter.NamespaceFilter;
import com.hubrick.vertx.s3.util.UrlEncodingUtils;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.sax.SAXSource;
import java.io.ByteArrayInputStream;
import java.text.MessageFormat;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class S3Client {

    private static final Logger log = LoggerFactory.getLogger(S3Client.class);

    public static final String DEFAULT_REGION = "us-east-1";
    public static final String DEFAULT_ENDPOINT = "s3.amazonaws.com";
    private static final String ENDPOINT_PATTERN = "s3-{0}.amazonaws.com";

    private final Marshaller jaxbMarshaller;
    private final Unmarshaller jaxbUnmarshaller;
    private final Long globalTimeout;
    private final String awsRegion;

    private final String hostname;

    private final Clock clock;
    private final HttpClient client;
    private final String awsAccessKey;
    private final String awsSecretKey;
    private final String awsServiceName;
    private final boolean signPayload;

    public S3Client(Vertx vertx, S3ClientOptions s3ClientOptions) {
        this(vertx, s3ClientOptions, Clock.systemDefaultZone());
    }

    public S3Client(Vertx vertx, S3ClientOptions s3ClientOptions, Clock clock) {
        checkNotNull(vertx, "vertx must not be null");
        checkNotNull(isNotBlank(s3ClientOptions.getAwsRegion()), "AWS region must be set");
        checkNotNull(isNotBlank(s3ClientOptions.getAwsServiceName()), "AWS service name must be set");
        checkNotNull(clock, "Clock must not be null");
        checkNotNull(s3ClientOptions.getGlobalTimeoutMs(), "global timeout must be null");
        checkArgument(s3ClientOptions.getGlobalTimeoutMs() > 0, "global timeout must be more than zero ms");

        this.jaxbMarshaller = createJaxbMarshaller();
        this.jaxbUnmarshaller = createJaxbUnmarshaller();

        this.clock = clock;
        this.awsServiceName = s3ClientOptions.getAwsServiceName();
        this.awsRegion = s3ClientOptions.getAwsRegion();
        this.awsAccessKey = s3ClientOptions.getAwsAccessKey();
        this.awsSecretKey = s3ClientOptions.getAwsSecretKey();
        this.globalTimeout = s3ClientOptions.getGlobalTimeoutMs();
        this.signPayload = s3ClientOptions.isSignPayload();

        final String hostnameOverride = s3ClientOptions.getHostnameOverride();
        if (!Strings.isNullOrEmpty(hostnameOverride)) {
            hostname = hostnameOverride;
        } else {
            if (DEFAULT_REGION.equals(s3ClientOptions.getAwsRegion())) {
                hostname = DEFAULT_ENDPOINT;
            } else {
                hostname = MessageFormat.format(ENDPOINT_PATTERN, awsRegion);
            }
        }

        final S3ClientOptions options = new S3ClientOptions(s3ClientOptions);
        options.setDefaultHost(hostname);

        this.client = vertx.createHttpClient(options);
    }

    public String getAwsRegion() {
        return awsRegion;
    }

    public String getAwsServiceName() {
        return awsServiceName;
    }

    public String getHostname() {
        return hostname;
    }

    public void close() {
        client.close();
    }

    public Long getGlobalTimeout() {
        return globalTimeout;
    }

    public void getObject(String bucket,
                          String key,
                          GetObjectRequest getObjectRequest,
                          Handler<HttpClientResponse> handler,
                          Handler<Throwable> exceptionHandler) {
        checkNotNull(StringUtils.trimToNull(bucket), "bucket must not be null");
        checkNotNull(StringUtils.trimToNull(key), "bucket must not be null");
        checkNotNull(getObjectRequest, "getObjectRequest must not be null");
        checkNotNull(handler, "handler must not be null");
        checkNotNull(exceptionHandler, "exceptionHandler must not be null");

        final S3ClientRequest request = createGetRequest(bucket, key, getObjectRequest, new StreamResponseHandler("getObject", jaxbUnmarshaller, handler, exceptionHandler));
        request.exceptionHandler(exceptionHandler);
        request.end();
    }

    public void putObject(String bucket,
                          String key,
                          PutObjectRequest putObjectRequest,
                          Buffer data,
                          Handler<HttpClientResponse> handler,
                          Handler<Throwable> exceptionHandler) {
        checkNotNull(StringUtils.trimToNull(bucket), "bucket must not be null");
        checkNotNull(StringUtils.trimToNull(key), "bucket must not be null");
        checkNotNull(putObjectRequest, "putObjectRequest must not be null");
        checkNotNull(data, "data must not be null");
        checkNotNull(handler, "handler must not be null");
        checkNotNull(exceptionHandler, "exceptionHandler must not be null");

        final S3ClientRequest request = createPutRequest(bucket, key, putObjectRequest, new StreamResponseHandler("putObject", jaxbUnmarshaller, handler, exceptionHandler));
        request.exceptionHandler(exceptionHandler);
        request.end(data);
    }

    public void copyObject(String sourceBucket,
                           String sourceKey,
                           String destinationBucket,
                           String destinationKey,
                           CopyObjectRequest copyObjectRequest,
                           Handler<HttpClientResponse> handler,
                           Handler<Throwable> exceptionHandler) {
        checkNotNull(StringUtils.trimToNull(sourceBucket), "sourceBucket must not be null");
        checkNotNull(StringUtils.trimToNull(sourceKey), "sourceKey must not be null");
        checkNotNull(StringUtils.trimToNull(destinationBucket), "destinationBucket must not be null");
        checkNotNull(StringUtils.trimToNull(destinationKey), "destinationKey must not be null");
        checkNotNull(copyObjectRequest, "copyObjectRequest must not be null");
        checkNotNull(handler, "handler must not be null");
        checkNotNull(exceptionHandler, "exceptionHandler must not be null");

        final S3ClientRequest request = createCopyRequest(sourceBucket, sourceKey, destinationBucket, destinationKey, copyObjectRequest, new StreamResponseHandler("copyObject", jaxbUnmarshaller, handler, exceptionHandler));
        request.exceptionHandler(exceptionHandler);
        request.end();
    }

    public void deleteObject(String bucket,
                             String key,
                             DeleteObjectRequest deleteObjectRequest,
                             Handler<HttpClientResponse> handler,
                             Handler<Throwable> exceptionHandler) {
        checkNotNull(StringUtils.trimToNull(bucket), "bucket must not be null");
        checkNotNull(StringUtils.trimToNull(key), "bucket must not be null");
        checkNotNull(deleteObjectRequest, "deleteObjectRequest must not be null");
        checkNotNull(handler, "handler must not be null");
        checkNotNull(exceptionHandler, "exceptionHandler must not be null");

        final S3ClientRequest request = createDeleteRequest(bucket, key, deleteObjectRequest, new StreamResponseHandler("deleteObject", jaxbUnmarshaller, handler, exceptionHandler));
        request.exceptionHandler(exceptionHandler);
        request.end();
    }

    public void getBucket(String bucket,
                          GetBucketRequest getBucketRequest,
                          Handler<GetBucketRespone> handler,
                          Handler<Throwable> exceptionHandler) {
        checkNotNull(StringUtils.trimToNull(bucket), "bucket must not be null");
        checkNotNull(getBucketRequest, "getBucketRequest must not be null");
        checkNotNull(handler, "handler must not be null");
        checkNotNull(exceptionHandler, "exceptionHandler must not be null");

        final S3ClientRequest request = createGetBucketRequest(bucket, getBucketRequest, new BodyResponseHandler<>("getBucket", jaxbUnmarshaller, handler, exceptionHandler));
        request.exceptionHandler(exceptionHandler);
        request.end();
    }

    private S3ClientRequest createPutRequest(String bucket,
                                             String key,
                                             PutObjectRequest putObjectRequest,
                                             Handler<HttpClientResponse> handler) {
        HttpClientRequest httpRequest = client.put("/" + bucket + "/" + key,
                handler);
        final S3ClientRequest s3ClientRequest = new S3ClientRequest(
                "PUT",
                awsRegion,
                awsServiceName,
                httpRequest,
                awsAccessKey,
                awsSecretKey,
                clock,
                signPayload
        )
                .setTimeout(globalTimeout)
                .putHeader("Host", hostname);

        s3ClientRequest.headers().addAll(populatePutObjectHeaders(putObjectRequest));
        return s3ClientRequest;
    }

    private MultiMap populatePutObjectHeaders(PutObjectRequest putObjectRequest) {
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap();

        if (StringUtils.trimToNull(putObjectRequest.getCacheControl()) != null) {
            headers.add("Cache-Control", StringUtils.trim(putObjectRequest.getCacheControl()));
        }
        if (StringUtils.trimToNull(putObjectRequest.getContentDisposition()) != null) {
            headers.add("Content-Disposition", StringUtils.trim(putObjectRequest.getContentDisposition()));
        }
        if (StringUtils.trimToNull(putObjectRequest.getContentEncoding()) != null) {
            headers.add("Content-Encoding", StringUtils.trim(putObjectRequest.getContentEncoding()));
        }
        if (StringUtils.trimToNull(putObjectRequest.getContentMD5()) != null) {
            headers.add("Content-MD5", StringUtils.trim(putObjectRequest.getContentMD5()));
        }
        if (StringUtils.trimToNull(putObjectRequest.getContentType()) != null) {
            headers.add("Content-Type", StringUtils.trim(putObjectRequest.getContentType()));
        }
        if (StringUtils.trimToNull(putObjectRequest.getExpect()) != null) {
            headers.add("Expect", StringUtils.trim(putObjectRequest.getExpect()));
        }
        if (StringUtils.trimToNull(putObjectRequest.getExpires()) != null) {
            headers.add("Expires", StringUtils.trim(putObjectRequest.getExpires()));
        }

        if (StringUtils.trimToNull(putObjectRequest.getAmzStorageClass()) != null) {
            headers.add("x-amz-storage-class", StringUtils.trim(putObjectRequest.getAmzStorageClass()));
        }
        if (StringUtils.trimToNull(putObjectRequest.getAmzTagging()) != null) {
            headers.add("x-amz-tagging", StringUtils.trim(putObjectRequest.getAmzTagging()));
        }
        if (StringUtils.trimToNull(putObjectRequest.getAmzWebsiteRedirectLocation()) != null) {
            headers.add("x-amz-website-redirect-location", StringUtils.trim(putObjectRequest.getAmzWebsiteRedirectLocation()));
        }
        if (StringUtils.trimToNull(putObjectRequest.getAmzAcl()) != null) {
            headers.add("x-amz-acl", StringUtils.trim(putObjectRequest.getAmzAcl()));
        }
        if (StringUtils.trimToNull(putObjectRequest.getAmzGrantRead()) != null) {
            headers.add("x-amz-grant-read", StringUtils.trim(putObjectRequest.getAmzGrantRead()));
        }
        if (StringUtils.trimToNull(putObjectRequest.getAmzGrantWrite()) != null) {
            headers.add("x-amz-grant-write", StringUtils.trim(putObjectRequest.getAmzGrantWrite()));
        }
        if (StringUtils.trimToNull(putObjectRequest.getAmzGrantWrite()) != null) {
            headers.add("x-amz-grant-write", StringUtils.trim(putObjectRequest.getAmzGrantWrite()));
        }
        if (StringUtils.trimToNull(putObjectRequest.getAmzGrantReadAcp()) != null) {
            headers.add("x-amz-grant-read-acp", StringUtils.trim(putObjectRequest.getAmzGrantReadAcp()));
        }
        if (StringUtils.trimToNull(putObjectRequest.getAmzGrantWriteAcp()) != null) {
            headers.add("x-amz-grant-write-acp", StringUtils.trim(putObjectRequest.getAmzGrantWriteAcp()));
        }
        if (StringUtils.trimToNull(putObjectRequest.getAmzGrantFullControl()) != null) {
            headers.add("x-amz-grant-full-control", StringUtils.trim(putObjectRequest.getAmzGrantFullControl()));
        }

        return headers;
    }

    private S3ClientRequest createCopyRequest(String sourceBucket,
                                              String sourceKey,
                                              String destinationBucket,
                                              String destinationKey,
                                              CopyObjectRequest copyObjectRequest,
                                              Handler<HttpClientResponse> handler) {
        final HttpClientRequest httpRequest = client.put("/" + destinationBucket + "/" + destinationKey, handler);
        final S3ClientRequest s3ClientRequest = new S3ClientRequest(
                "PUT",
                awsRegion,
                awsServiceName,
                httpRequest,
                awsAccessKey,
                awsSecretKey,
                clock,
                signPayload
        )
                .setTimeout(globalTimeout)
                .putHeader("Host", hostname);

        s3ClientRequest.putHeader(S3Headers.COPY_SOURCE_HEADER.getValue(), "/" + sourceBucket + "/" + sourceKey);
        s3ClientRequest.headers().addAll(populateCopyObjectHeaders(copyObjectRequest));
        return s3ClientRequest;
    }

    private MultiMap populateCopyObjectHeaders(CopyObjectRequest copyObjectRequest) {
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap();

        if (StringUtils.trimToNull(copyObjectRequest.getAmzMetadataDirective()) != null) {
            headers.add("x-amz-metadata-directive", StringUtils.trim(copyObjectRequest.getAmzMetadataDirective()));
        }
        if (StringUtils.trimToNull(copyObjectRequest.getAmzCopySourceIfMatch()) != null) {
            headers.add("x-amz-copy-source-if-match", StringUtils.trim(copyObjectRequest.getAmzCopySourceIfMatch()));
        }
        if (StringUtils.trimToNull(copyObjectRequest.getAmzCopySourceIfNoneMatch()) != null) {
            headers.add("x-amz-copy-source-if-none-match", StringUtils.trim(copyObjectRequest.getAmzCopySourceIfNoneMatch()));
        }
        if (StringUtils.trimToNull(copyObjectRequest.getAmzCopySourceIfUnmodifiedSince()) != null) {
            headers.add("x-amz-copy-source-if-unmodified-since", StringUtils.trim(copyObjectRequest.getAmzCopySourceIfUnmodifiedSince()));
        }
        if (StringUtils.trimToNull(copyObjectRequest.getAmzCopySourceIfModifiedSince()) != null) {
            headers.add("x-amz-copy-source-if-modified-since", StringUtils.trim(copyObjectRequest.getAmzCopySourceIfModifiedSince()));
        }
        if (StringUtils.trimToNull(copyObjectRequest.getAmzStorageClass()) != null) {
            headers.add("x-amz-storage-class", StringUtils.trim(copyObjectRequest.getAmzStorageClass()));
        }
        if (StringUtils.trimToNull(copyObjectRequest.getAmzTaggingDirective()) != null) {
            headers.add("x-amz-tagging-directive", StringUtils.trim(copyObjectRequest.getAmzTaggingDirective()));
        }
        if (StringUtils.trimToNull(copyObjectRequest.getAmzWebsiteRedirectLocation()) != null) {
            headers.add("x-amz-website-redirect-location", StringUtils.trim(copyObjectRequest.getAmzWebsiteRedirectLocation()));
        }

        return headers;
    }

    private S3ClientRequest createGetRequest(String bucket,
                                             String key,
                                             GetObjectRequest getObjectRequest,
                                             Handler<HttpClientResponse> handler) {
        final HttpClientRequest httpRequest = client.get(UrlEncodingUtils.addParamsSortedToUrl("/" + bucket + "/" + key, populateGetObjectQueryParams(getObjectRequest)), handler);
        final S3ClientRequest s3ClientRequest = new S3ClientRequest(
                "GET",
                awsRegion,
                awsServiceName,
                httpRequest,
                awsAccessKey,
                awsSecretKey,
                clock,
                signPayload
        )
                .setTimeout(globalTimeout)
                .putHeader("Host", hostname);

        s3ClientRequest.headers().addAll(populateGetObjectHeaders(getObjectRequest));
        return s3ClientRequest;
    }

    private Map<String, String> populateGetObjectQueryParams(GetObjectRequest getObjectRequest) {
        final Map<String, String> queryParams = new HashMap<>();

        if (StringUtils.trimToNull(getObjectRequest.getResponseCacheControl()) != null) {
            queryParams.put("response-cache-control", StringUtils.trim(getObjectRequest.getResponseCacheControl()));
        }
        if (StringUtils.trimToNull(getObjectRequest.getResponseContentDisposition()) != null) {
            queryParams.put("response-content-disposition", StringUtils.trim(getObjectRequest.getResponseContentDisposition()));
        }
        if (StringUtils.trimToNull(getObjectRequest.getResponseContentEncoding()) != null) {
            queryParams.put("response-content-encoding", StringUtils.trim(getObjectRequest.getResponseContentEncoding()));
        }
        if (StringUtils.trimToNull(getObjectRequest.getResponseContentLanguage()) != null) {
            queryParams.put("response-content-language", StringUtils.trim(getObjectRequest.getResponseContentLanguage()));
        }
        if (StringUtils.trimToNull(getObjectRequest.getResponseContentType()) != null) {
            queryParams.put("response-content-type", StringUtils.trim(getObjectRequest.getResponseContentType()));
        }
        if (StringUtils.trimToNull(getObjectRequest.getResponseExpires()) != null) {
            queryParams.put("response-expires", StringUtils.trim(getObjectRequest.getResponseExpires()));
        }

        return queryParams;
    }

    private MultiMap populateGetObjectHeaders(GetObjectRequest getObjectRequest) {
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap();

        if (StringUtils.trimToNull(getObjectRequest.getRange()) != null) {
            headers.add("Range", StringUtils.trim(getObjectRequest.getRange()));
        }
        if (StringUtils.trimToNull(getObjectRequest.getIfModifiedSince()) != null) {
            headers.add("If-Modified-Since", StringUtils.trim(getObjectRequest.getIfModifiedSince()));
        }
        if (StringUtils.trimToNull(getObjectRequest.getIfUnmodifiedSince()) != null) {
            headers.add("If-Unmodified-Since", StringUtils.trim(getObjectRequest.getIfUnmodifiedSince()));
        }
        if (StringUtils.trimToNull(getObjectRequest.getIfMatch()) != null) {
            headers.add("If-Match", StringUtils.trim(getObjectRequest.getIfMatch()));
        }
        if (StringUtils.trimToNull(getObjectRequest.getIfNoneMatch()) != null) {
            headers.add("If-None-Match", StringUtils.trim(getObjectRequest.getIfNoneMatch()));
        }

        return headers;
    }

    private S3ClientRequest createGetBucketRequest(String bucket,
                                                   GetBucketRequest getBucketRequest,
                                                   Handler<HttpClientResponse> handler) {
        final HttpClientRequest httpRequest = client.get(UrlEncodingUtils.addParamsSortedToUrl("/" + bucket, populateGetBucketQueryParams(getBucketRequest)), handler);
        final S3ClientRequest s3ClientRequest = new S3ClientRequest(
                "GET",
                awsRegion,
                awsServiceName,
                httpRequest,
                awsAccessKey,
                awsSecretKey,
                clock,
                signPayload
        )
                .setTimeout(globalTimeout)
                .putHeader("Host", hostname);

        return s3ClientRequest;
    }

    private Map<String, String> populateGetBucketQueryParams(GetBucketRequest listObjectsRequest) {
        final Map<String, String> queryParams = new HashMap<>();
        queryParams.put("list-type", "2");

        if (StringUtils.trimToNull(listObjectsRequest.getContinuationToken()) != null) {
            queryParams.put("continuation-token", StringUtils.trim(listObjectsRequest.getContinuationToken()));
        }
        if (StringUtils.trimToNull(listObjectsRequest.getDelimiter()) != null) {
            queryParams.put("delimiter", StringUtils.trim(listObjectsRequest.getDelimiter()));
        }
        if (StringUtils.trimToNull(listObjectsRequest.getEncodingType()) != null) {
            queryParams.put("encoding-type", StringUtils.trim(listObjectsRequest.getEncodingType()));
        }
        if (StringUtils.trimToNull(listObjectsRequest.getFetchOwner()) != null) {
            queryParams.put("fetch-owner", StringUtils.trim(listObjectsRequest.getFetchOwner()));
        }
        if (listObjectsRequest.getMaxKeys() != null) {
            queryParams.put("max-keys", StringUtils.trim(listObjectsRequest.getMaxKeys().toString()));
        }
        if (StringUtils.trimToNull(listObjectsRequest.getPrefix()) != null) {
            queryParams.put("prefix", StringUtils.trim(listObjectsRequest.getPrefix()));
        }
        if (StringUtils.trimToNull(listObjectsRequest.getStartAfter()) != null) {
            queryParams.put("start-after", StringUtils.trim(listObjectsRequest.getStartAfter()));
        }

        return queryParams;
    }

    private S3ClientRequest createDeleteRequest(String bucket,
                                                String key,
                                                DeleteObjectRequest deleteObjectRequest,
                                                Handler<HttpClientResponse> handler) {
        final HttpClientRequest httpRequest = client.delete("/" + bucket + "/" + key, handler);
        final S3ClientRequest s3ClientRequest = new S3ClientRequest(
                "DELETE",
                awsRegion,
                awsServiceName,
                httpRequest,
                awsAccessKey,
                awsSecretKey,
                clock,
                signPayload
        )
                .setTimeout(globalTimeout)
                .putHeader("Host", hostname);

        s3ClientRequest.headers().addAll(populateDeleteObjectHeaders(deleteObjectRequest));
        return s3ClientRequest;
    }

    private MultiMap populateDeleteObjectHeaders(DeleteObjectRequest deleteObjectRequest) {
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap();

        if (StringUtils.trimToNull(deleteObjectRequest.getAmzMfa()) != null) {
            headers.add("x-amz-mfa", StringUtils.trim(deleteObjectRequest.getAmzMfa()));
        }

        return headers;
    }

    private JAXBContext createJAXBContext() {
        try {
            return JAXBContext.newInstance(
                    Contents.class,
                    CommonPrefixes.class,
                    GetBucketRespone.class,
                    Owner.class,
                    ErrorResponse.class
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Marshaller createJaxbMarshaller() {
        try {
            final JAXBContext jaxbContext = createJAXBContext();
            final Marshaller jaxbMarshaller = jaxbContext.createMarshaller();

            // output pretty printed
            jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

            return jaxbMarshaller;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Unmarshaller createJaxbUnmarshaller() {
        try {
            final JAXBContext jaxbContext = createJAXBContext();
            return jaxbContext.createUnmarshaller();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private SAXSource convertToSaxSource(byte[] payload) throws SAXException {
        //Create an XMLReader to use with our filter
        final XMLReader reader = XMLReaderFactory.createXMLReader();

        //Create the filter to remove all namespaces and set the xmlReader as its parent.
        final NamespaceFilter inFilter = new NamespaceFilter(null, false);
        inFilter.setParent(reader);

        final InputSource inputSource = new InputSource(new ByteArrayInputStream(payload));

        //Create a SAXSource specifying the filter
        return new SAXSource(inFilter, inputSource);
    }

    private class StreamResponseHandler implements Handler<HttpClientResponse> {

        private final String action;
        private final Unmarshaller jaxbUnmarshaller;
        private final Handler<HttpClientResponse> successHandler;
        private final Handler<Throwable> exceptionHandler;

        private StreamResponseHandler(String action, Unmarshaller jaxbUnmarshaller, Handler<HttpClientResponse> successHandler, Handler<Throwable> exceptionHandler) {
            this.action = action;
            this.jaxbUnmarshaller = jaxbUnmarshaller;
            this.successHandler = successHandler;
            this.exceptionHandler = exceptionHandler;
        }

        @Override
        public void handle(HttpClientResponse event) {
            if (event.statusCode() / 100 != 2) {
                event.bodyHandler(buffer -> {
                    try {
                        log.warn("Error occurred. Status: {}, Message: {}", event.statusCode(), event.statusMessage());
                        if (log.isDebugEnabled()) {
                            log.debug("Response: {}", new String(buffer.getBytes(), Charsets.UTF_8));
                        }

                        exceptionHandler.handle(
                                new HttpErrorException(
                                        event.statusCode(),
                                        event.statusMessage(),
                                        (ErrorResponse) jaxbUnmarshaller.unmarshal(convertToSaxSource(buffer.getBytes())),
                                        "Error occurred during on '" + action + "'"
                                )
                        );
                    } catch (Exception e) {
                        exceptionHandler.handle(e);
                    }
                });
            } else {
                successHandler.handle(event);
            }
        }
    }

    private class BodyResponseHandler<T> implements Handler<HttpClientResponse> {

        private final String action;
        private final Unmarshaller jaxbUnmarshaller;
        private final Handler<T> successHandler;
        private final Handler<Throwable> exceptionHandler;

        private BodyResponseHandler(String action, Unmarshaller jaxbUnmarshaller, Handler<T> successHandler, Handler<Throwable> exceptionHandler) {
            this.action = action;
            this.jaxbUnmarshaller = jaxbUnmarshaller;
            this.successHandler = successHandler;
            this.exceptionHandler = exceptionHandler;
        }

        @Override
        public void handle(HttpClientResponse event) {
            event.bodyHandler(buffer -> {
                try {
                    if (event.statusCode() / 100 != 2) {
                        log.warn("Error occurred. Status: {}, Message: {}", event.statusCode(), event.statusMessage());
                        if (log.isDebugEnabled()) {
                            log.debug("Response: {}", new String(buffer.getBytes(), Charsets.UTF_8));
                        }

                        exceptionHandler.handle(
                                new HttpErrorException(
                                        event.statusCode(),
                                        event.statusMessage(),
                                        (ErrorResponse) jaxbUnmarshaller.unmarshal(convertToSaxSource(buffer.getBytes())),
                                        "Error occurred on '" + action + "'"
                                )
                        );
                    } else {
                        if (log.isDebugEnabled()) {
                            log.debug("Request successful. Status: {}, Message: {}", event.statusCode(), event.statusMessage());
                            log.debug("Response: {}", new String(buffer.getBytes(), Charsets.UTF_8));
                        }
                        successHandler.handle((T) jaxbUnmarshaller.unmarshal(convertToSaxSource(buffer.getBytes())));
                    }
                } catch (Exception e) {
                    exceptionHandler.handle(e);
                }
            });
        }
    }
}
