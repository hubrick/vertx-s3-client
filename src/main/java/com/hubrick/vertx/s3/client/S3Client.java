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
import com.hubrick.vertx.s3.exception.HttpErrorException;
import com.hubrick.vertx.s3.model.AccessControlPolicy;
import com.hubrick.vertx.s3.model.CommonPrefixes;
import com.hubrick.vertx.s3.model.Connection;
import com.hubrick.vertx.s3.model.Contents;
import com.hubrick.vertx.s3.model.Grant;
import com.hubrick.vertx.s3.model.Grantee;
import com.hubrick.vertx.s3.model.HeaderOnlyResponse;
import com.hubrick.vertx.s3.model.Owner;
import com.hubrick.vertx.s3.model.Part;
import com.hubrick.vertx.s3.model.ReplicationStatus;
import com.hubrick.vertx.s3.model.Response;
import com.hubrick.vertx.s3.model.ResponseWithBody;
import com.hubrick.vertx.s3.model.StorageClass;
import com.hubrick.vertx.s3.model.filter.NamespaceFilter;
import com.hubrick.vertx.s3.model.header.CommonResponseHeaders;
import com.hubrick.vertx.s3.model.header.CompleteMultipartUploadResponseHeaders;
import com.hubrick.vertx.s3.model.header.ContinueMultipartUploadResponseHeaders;
import com.hubrick.vertx.s3.model.header.CopyObjectResponseHeaders;
import com.hubrick.vertx.s3.model.header.GetObjectResponseHeaders;
import com.hubrick.vertx.s3.model.header.HeadObjectResponseHeaders;
import com.hubrick.vertx.s3.model.header.InitMultipartUploadResponseHeaders;
import com.hubrick.vertx.s3.model.header.PutObjectResponseHeaders;
import com.hubrick.vertx.s3.model.header.ServerSideEncryptionResponseHeaders;
import com.hubrick.vertx.s3.model.request.*;
import com.hubrick.vertx.s3.model.response.CompleteMultipartUploadResponse;
import com.hubrick.vertx.s3.model.response.CopyObjectResponse;
import com.hubrick.vertx.s3.model.response.ErrorResponse;
import com.hubrick.vertx.s3.model.response.GetBucketRespone;
import com.hubrick.vertx.s3.model.response.InitMultipartUploadResponse;
import com.hubrick.vertx.s3.model.response.MultipartUploadWriteStream;
import com.hubrick.vertx.s3.util.ChunkedBufferReadStream;
import com.hubrick.vertx.s3.util.UrlEncodingUtils;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.streams.Pump;
import io.vertx.core.streams.ReadStream;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.UnmarshalException;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.sax.SAXSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.text.MessageFormat;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class S3Client {

    private static final Logger log = LoggerFactory.getLogger(S3Client.class);
    private static final Integer MAX_LOG_OUTPUT = 10000;
    private static final int FIVE_MB_IN_BYTES = 5242880;
    private static final String DEFAULT_REGION = "us-east-1";
    private static final String DEFAULT_ENDPOINT = "s3.amazonaws.com";
    private static final String ENDPOINT_PATTERN = "s3-{0}.amazonaws.com";

    private final Vertx vertx;
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
        this(vertx, s3ClientOptions, Clock.systemUTC());
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

        this.vertx = vertx;
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
                          Handler<Response<GetObjectResponseHeaders, ReadStream<Buffer>>> handler,
                          Handler<Throwable> exceptionHandler) {
        checkNotNull(StringUtils.trimToNull(bucket), "bucket must not be null");
        checkNotNull(StringUtils.trimToNull(key), "key must not be null");
        checkNotNull(getObjectRequest, "getObjectRequest must not be null");
        checkNotNull(handler, "handler must not be null");
        checkNotNull(exceptionHandler, "exceptionHandler must not be null");

        final S3ClientRequest request = createGetRequest(
                bucket,
                key,
                getObjectRequest,
                new StreamResponseHandler("getObject", jaxbUnmarshaller, new GetResponseHeadersMapper(), handler, exceptionHandler)
        );
        request.exceptionHandler(exceptionHandler);
        request.end();
    }

    public void getObjectAcl(String bucket,
                             String key,
                             Handler<Response<CommonResponseHeaders, AccessControlPolicy>> handler,
                             Handler<Throwable> exceptionHandler) {
        checkNotNull(StringUtils.trimToNull(bucket), "bucket must not be null");
        checkNotNull(StringUtils.trimToNull(key), "key must not be null");
        checkNotNull(handler, "handler must not be null");
        checkNotNull(exceptionHandler, "exceptionHandler must not be null");

        final S3ClientRequest request = createGetAclRequest(
                bucket,
                key,
                new XmlBodyResponseHandler<>("getObjectAcl", jaxbUnmarshaller, new CommonResponseHeadersMapper(), handler, exceptionHandler)
        );
        request.exceptionHandler(exceptionHandler);
        request.end();
    }

    public void getObjectSse(String bucket,
                          String key,
                          GetObjectRequest getObjectRequest,
                          SseHeadersRequest sseHeadersRequest,
                          Handler<Response<GetObjectResponseHeaders, ReadStream<Buffer>>> handler,
                          Handler<Throwable> exceptionHandler) {
        checkNotNull(StringUtils.trimToNull(bucket), "bucket must not be null");
        checkNotNull(StringUtils.trimToNull(key), "key must not be null");
        checkNotNull(getObjectRequest, "getObjectRequest must not be null");
        checkNotNull(sseHeadersRequest, "sseHeadersRequest must not be null");
        checkNotNull(handler, "handler must not be null");
        checkNotNull(exceptionHandler, "exceptionHandler must not be null");

        final S3ClientRequest request = createGetSseRequest(
                bucket,
                key,
                getObjectRequest,
                Optional.ofNullable(sseHeadersRequest),
                new StreamResponseHandler("getObjectSse", jaxbUnmarshaller, new GetResponseHeadersMapper(), handler, exceptionHandler)
        );
        request.exceptionHandler(exceptionHandler);
        request.end();
    }


    public void headObject(String bucket,
                           String key,
                           HeadObjectRequest headObjectRequest,
                           Handler<Response<HeadObjectResponseHeaders, Void>> handler,
                           Handler<Throwable> exceptionHandler) {
        checkNotNull(StringUtils.trimToNull(bucket), "bucket must not be null");
        checkNotNull(StringUtils.trimToNull(key), "key must not be null");
        checkNotNull(headObjectRequest, "headObjectRequest must not be null");
        checkNotNull(handler, "handler must not be null");
        checkNotNull(exceptionHandler, "exceptionHandler must not be null");

        final S3ClientRequest request = createHeadRequest(
                bucket,
                key,
                headObjectRequest,
                new HeadersResponseHandler("headObject", jaxbUnmarshaller, new HeadResponseHeadersMapper(), handler, exceptionHandler, true)
        );
        request.exceptionHandler(exceptionHandler);
        request.end();
    }

    public void putObject(String bucket,
                          String key,
                          PutObjectRequest putObjectRequest,
                          Handler<Response<PutObjectResponseHeaders, Void>> handler,
                          Handler<Throwable> exceptionHandler) {
        checkNotNull(StringUtils.trimToNull(bucket), "bucket must not be null");
        checkNotNull(StringUtils.trimToNull(key), "key must not be null");
        checkNotNull(putObjectRequest, "putObjectRequest must not be null");
        checkNotNull(handler, "handler must not be null");
        checkNotNull(exceptionHandler, "exceptionHandler must not be null");

        final S3ClientRequest request = createPutRequest(
                bucket,
                key,
                putObjectRequest,
                new HeadersResponseHandler("putObject", jaxbUnmarshaller, new PutResponseHeadersMapper(), handler, exceptionHandler, false)
        );
        request.exceptionHandler(exceptionHandler);
        request.end(putObjectRequest.getData());
    }

    public void putObjectAcl(String bucket,
                             String key,
                             PutObjectAclRequest putObjectAclRequest,
                             Handler<Response<CommonResponseHeaders, Void>> handler,
                             Handler<Throwable> exceptionHandler) {
        checkNotNull(StringUtils.trimToNull(bucket), "bucket must not be null");
        checkNotNull(StringUtils.trimToNull(key), "key must not be null");
        checkNotNull(putObjectAclRequest, "putObjectAclRequest must not be null");
        checkNotNull(handler, "handler must not be null");
        checkNotNull(exceptionHandler, "exceptionHandler must not be null");

        final S3ClientRequest request = createPutAclRequest(
                bucket,
                key,
                Optional.ofNullable(putObjectAclRequest.getAclHeadersRequest()),
                new HeadersResponseHandler("putObjectAcl", jaxbUnmarshaller, new PutResponseHeadersMapper(), handler, exceptionHandler, false)
        );
        request.exceptionHandler(exceptionHandler);

        if (putObjectAclRequest.getAccessControlPolicy() != null) {
            try {
                final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                jaxbMarshaller.marshal(putObjectAclRequest.getAccessControlPolicy(), outputStream);
                request.putHeader(Headers.CONTENT_TYPE, "application/xml");
                request.end(Buffer.buffer(outputStream.toByteArray()));
            } catch (JAXBException e) {
                exceptionHandler.handle(e);
            }
        } else {
            request.end();
        }
    }

    public void putObjectSse(String bucket,
                          String key,
                          PutObjectRequest putObjectRequest,
                          SseHeadersRequest sseHeadersRequest,
                          Handler<Response<PutObjectResponseHeaders, Void>> handler,
                          Handler<Throwable> exceptionHandler) {
        checkNotNull(StringUtils.trimToNull(bucket), "bucket must not be null");
        checkNotNull(StringUtils.trimToNull(key), "key must not be null");
        checkNotNull(putObjectRequest, "putObjectRequest must not be null");
        checkNotNull(sseHeadersRequest, "sseHeadersRequest must not be null");
        checkNotNull(handler, "handler must not be null");
        checkNotNull(exceptionHandler, "exceptionHandler must not be null");

        final S3ClientRequest request = createPutSseRequest(
                bucket,
                key,
                putObjectRequest,
                Optional.ofNullable(sseHeadersRequest),
                new HeadersResponseHandler("putObjectSse", jaxbUnmarshaller, new PutResponseHeadersMapper(), handler, exceptionHandler, false)
        );
        request.exceptionHandler(exceptionHandler);
        request.end(putObjectRequest.getData());
    }

    /**
     * Adaptively upload a file to S3 and take away the burden to choose between direct or multipart upload.
     * Since the minimum size of the multipart part has to be 5MB this method handles the upload automatically.
     * It either chooses between the direct upload if the stream contains less then 5MB or the multipart upload
     * if the stream is bigger then 5MB.
     *
     * @param bucket                The bucket
     * @param key                   The key of the final file
     * @param adaptiveUploadRequest The request
     * @param handler               Success handler
     * @param exceptionHandler      Exception handler
     */
    public void adaptiveUpload(String bucket,
                               String key,
                               AdaptiveUploadRequest adaptiveUploadRequest,
                               Handler<Response<CommonResponseHeaders, Void>> handler,
                               Handler<Throwable> exceptionHandler) {
        checkNotNull(StringUtils.trimToNull(bucket), "bucket must not be null");
        checkNotNull(StringUtils.trimToNull(key), "key must not be null");
        checkNotNull(adaptiveUploadRequest, "adaptiveUploadRequest must not be null");
        checkNotNull(handler, "handler must not be null");
        checkNotNull(exceptionHandler, "exceptionHandler must not be null");

        final ChunkedBufferReadStream chunkedBufferReadStream = new ChunkedBufferReadStream(vertx, adaptiveUploadRequest.getReadStream(), FIVE_MB_IN_BYTES);
        chunkedBufferReadStream.exceptionHandler(throwable -> exceptionHandler.handle(throwable));
        chunkedBufferReadStream.setChunkHandler(chunk -> {
            if (chunkedBufferReadStream.numberOfChunks() == 0) {
                if (chunk.length() < FIVE_MB_IN_BYTES) {
                    final Buffer buffer = Buffer.buffer();
                    chunkedBufferReadStream.handler(buffer::appendBuffer);
                    chunkedBufferReadStream.endHandler(aVoid -> {
                        putObject(bucket, key, mapAdaptiveUploadRequestToPutObjectRequest(buffer, adaptiveUploadRequest), event -> handler.handle(new HeaderOnlyResponse(event.getHeader())), exceptionHandler);
                    });
                    chunkedBufferReadStream.resume();
                } else {
                    chunkedBufferReadStream.pause();
                    initMultipartUpload(bucket, key, mapAdaptiveUploadRequestToInitMultipartUploadRequest(adaptiveUploadRequest),
                            event -> {
                                event.getData().exceptionHandler(throwable -> exceptionHandler.handle(throwable));
                                Pump.pump(chunkedBufferReadStream, event.getData()).start();
                                chunkedBufferReadStream.endHandler(aVoid -> event.getData().end(endResponse -> handler.handle(new HeaderOnlyResponse(event.getHeader()))));
                                chunkedBufferReadStream.resume();
                            },
                            exceptionHandler
                    );
                }
            }
        });
        chunkedBufferReadStream.resume();
    }

    private PutObjectRequest mapAdaptiveUploadRequestToPutObjectRequest(Buffer buffer, AdaptiveUploadRequest autoUploadRequest) {
        final PutObjectRequest putObjectRequest = new PutObjectRequest(buffer);

        putObjectRequest.withCacheControl(autoUploadRequest.getCacheControl());
        putObjectRequest.withContentDisposition(autoUploadRequest.getContentDisposition());
        putObjectRequest.withContentEncoding(autoUploadRequest.getContentEncoding());
        putObjectRequest.withContentMD5(autoUploadRequest.getContentMD5());
        putObjectRequest.withContentType(autoUploadRequest.getContentType());
        putObjectRequest.withExpires(autoUploadRequest.getExpires());

        for (Map.Entry<String, String> meta : autoUploadRequest.getAmzMeta()) {
            putObjectRequest.withAmzMeta(meta.getKey(), StringUtils.trim(meta.getValue()));
        }

        putObjectRequest.withAmzStorageClass(autoUploadRequest.getAmzStorageClass());
        putObjectRequest.withAmzTagging(autoUploadRequest.getAmzTagging());
        putObjectRequest.withAmzWebsiteRedirectLocation(autoUploadRequest.getAmzWebsiteRedirectLocation());
        putObjectRequest.withAmzAcl(autoUploadRequest.getAmzAcl());
        putObjectRequest.withAmzGrantRead(autoUploadRequest.getAmzGrantRead());
        putObjectRequest.withAmzGrantWrite(autoUploadRequest.getAmzGrantWrite());
        putObjectRequest.withAmzGrantWriteAcp(autoUploadRequest.getAmzGrantWriteAcp());
        putObjectRequest.withAmzGrantRead(autoUploadRequest.getAmzGrantRead());
        putObjectRequest.withAmzGrantReadAcp(autoUploadRequest.getAmzGrantReadAcp());
        putObjectRequest.withAmzGrantFullControl(autoUploadRequest.getAmzGrantFullControl());

        return putObjectRequest;
    }

    private InitMultipartUploadRequest mapAdaptiveUploadRequestToInitMultipartUploadRequest(AdaptiveUploadRequest autoUploadRequest) {
        final InitMultipartUploadRequest initMultipartUploadRequest = new InitMultipartUploadRequest();

        initMultipartUploadRequest.withCacheControl(autoUploadRequest.getCacheControl());
        initMultipartUploadRequest.withContentDisposition(autoUploadRequest.getContentDisposition());
        initMultipartUploadRequest.withContentEncoding(autoUploadRequest.getContentEncoding());
        initMultipartUploadRequest.withContentType(autoUploadRequest.getContentType());
        initMultipartUploadRequest.withExpires(autoUploadRequest.getExpires());

        for (Map.Entry<String, String> meta : autoUploadRequest.getAmzMeta()) {
            initMultipartUploadRequest.withAmzMeta(meta.getKey(), StringUtils.trim(meta.getValue()));
        }

        initMultipartUploadRequest.withAmzStorageClass(autoUploadRequest.getAmzStorageClass());
        initMultipartUploadRequest.withAmzWebsiteRedirectLocation(autoUploadRequest.getAmzWebsiteRedirectLocation());
        initMultipartUploadRequest.withAmzAcl(autoUploadRequest.getAmzAcl());
        initMultipartUploadRequest.withAmzGrantRead(autoUploadRequest.getAmzGrantRead());
        initMultipartUploadRequest.withAmzGrantWrite(autoUploadRequest.getAmzGrantWrite());
        initMultipartUploadRequest.withAmzGrantWriteAcp(autoUploadRequest.getAmzGrantWriteAcp());
        initMultipartUploadRequest.withAmzGrantRead(autoUploadRequest.getAmzGrantRead());
        initMultipartUploadRequest.withAmzGrantReadAcp(autoUploadRequest.getAmzGrantReadAcp());
        initMultipartUploadRequest.withAmzGrantFullControl(autoUploadRequest.getAmzGrantFullControl());

        return initMultipartUploadRequest;
    }

    /**
     * Initialize the multipart upload. After that continue either with the automated {@link MultipartUploadWriteStream}
     * if you don't need response headers for each part or manually handle the part uploads using the methods {@link #continueMultipartUpload}
     *
     * @param bucket                     The bucket
     * @param key                        The key of the final file
     * @param initMultipartUploadRequest The request
     * @param handler                    Success handler
     * @param exceptionHandler           Exception handler
     */
    public void initMultipartUpload(String bucket,
                                    String key,
                                    InitMultipartUploadRequest initMultipartUploadRequest,
                                    Handler<Response<InitMultipartUploadResponseHeaders, MultipartUploadWriteStream>> handler,
                                    Handler<Throwable> exceptionHandler) {
        checkNotNull(StringUtils.trimToNull(bucket), "bucket must not be null");
        checkNotNull(StringUtils.trimToNull(key), "key must not be null");
        checkNotNull(initMultipartUploadRequest, "initMultipartUploadRequest must not be null");
        checkNotNull(handler, "handler must not be null");
        checkNotNull(exceptionHandler, "exceptionHandler must not be null");

        final S3ClientRequest request = createInitMultipartUploadRequest(
                bucket,
                key,
                initMultipartUploadRequest,
                new XmlBodyResponseHandler<InitMultipartUploadResponseHeaders, InitMultipartUploadResponse>(
                        "initMultipartUpload",
                        jaxbUnmarshaller,
                        new InitMultipartUploadResponseHeadersMapper(),
                        response -> {
                            handler.handle(
                                    new ResponseWithBody(
                                            response.getHeader(),
                                            new MultipartUploadWriteStream(
                                                    this,
                                                    response.getData(),
                                                    exceptionHandler
                                            )
                                    )
                            );
                        },
                        exceptionHandler
                )
        );
        request.exceptionHandler(exceptionHandler);
        request.end();
    }

    /**
     * For manual handling of multipart uploads. Upload the next part.
     * Note you have manually to keep track of the sequential {@link ContinueMultipartUploadRequest#partNumber}
     *
     * @param bucket                         The bucket
     * @param key                            The key of the final file
     * @param continueMultipartUploadRequest The request
     * @param handler                        Success handler
     * @param exceptionHandler               Exception handler
     */
    public void continueMultipartUpload(String bucket,
                                        String key,
                                        ContinueMultipartUploadRequest continueMultipartUploadRequest,
                                        Handler<Response<ContinueMultipartUploadResponseHeaders, Void>> handler,
                                        Handler<Throwable> exceptionHandler) {
        checkNotNull(StringUtils.trimToNull(bucket), "bucket must not be null");
        checkNotNull(StringUtils.trimToNull(key), "key must not be null");
        checkNotNull(continueMultipartUploadRequest, "continueMultipartUploadRequest must not be null");
        checkNotNull(handler, "handler must not be null");
        checkNotNull(exceptionHandler, "exceptionHandler must not be null");

        final S3ClientRequest request = createContinueMultipartUploadRequest(
                bucket,
                key,
                continueMultipartUploadRequest,
                new HeadersResponseHandler("continueMultipartUpload", jaxbUnmarshaller, new ContinueMultipartUploadResponseHeadersMapper(), handler, exceptionHandler, false)
        );
        request.exceptionHandler(exceptionHandler);
        request.end(continueMultipartUploadRequest.getData());
    }

    /**
     * For manual handling of multipart uploads. Complete the multipart upload.
     *
     * @param bucket                         The bucket
     * @param key                            The key of the final file
     * @param completeMultipartUploadRequest The request
     * @param handler                        Success handler
     * @param exceptionHandler               Exception handler
     */
    public void completeMultipartUpload(String bucket,
                                        String key,
                                        CompleteMultipartUploadRequest completeMultipartUploadRequest,
                                        Handler<Response<CompleteMultipartUploadResponseHeaders, CompleteMultipartUploadResponse>> handler,
                                        Handler<Throwable> exceptionHandler) {
        checkNotNull(StringUtils.trimToNull(bucket), "bucket must not be null");
        checkNotNull(StringUtils.trimToNull(key), "key must not be null");
        checkNotNull(completeMultipartUploadRequest, "completeMultipartUploadRequest must not be null");
        checkNotNull(handler, "handler must not be null");
        checkNotNull(exceptionHandler, "exceptionHandler must not be null");

        final S3ClientRequest request = createCompleteMultipartUploadRequest(
                bucket,
                key,
                completeMultipartUploadRequest,
                new XmlBodyResponseHandler<>("completeMultipartUpload", jaxbUnmarshaller, new CompleteMultipartUploadResponseHeadersMapper(), handler, exceptionHandler)
        );
        request.exceptionHandler(exceptionHandler);

        try {
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            jaxbMarshaller.marshal(completeMultipartUploadRequest, outputStream);
            request.putHeader(Headers.CONTENT_TYPE, "application/xml");
            request.end(Buffer.buffer(outputStream.toByteArray()));
        } catch (JAXBException e) {
            exceptionHandler.handle(e);
        }
    }

    /**
     * For manual handling of multipart uploads. Abort the multipart upload.
     *
     * @param bucket                      The bucket
     * @param key                         The key of the final file
     * @param abortMultipartUploadRequest The request
     * @param handler                     Success handler
     * @param exceptionHandler            Exception handler
     */
    public void abortMultipartUpload(String bucket,
                                     String key,
                                     AbortMultipartUploadRequest abortMultipartUploadRequest,
                                     Handler<Response<CommonResponseHeaders, Void>> handler,
                                     Handler<Throwable> exceptionHandler) {
        checkNotNull(StringUtils.trimToNull(bucket), "bucket must not be null");
        checkNotNull(StringUtils.trimToNull(key), "key must not be null");
        checkNotNull(abortMultipartUploadRequest, "abortMultipartUploadRequest must not be null");
        checkNotNull(handler, "handler must not be null");
        checkNotNull(exceptionHandler, "exceptionHandler must not be null");

        final S3ClientRequest request = createAbortMultipartUploadRequest(
                bucket,
                key,
                abortMultipartUploadRequest,
                new HeadersResponseHandler<>("abortMultipartUpload", jaxbUnmarshaller, new CommonResponseHeadersMapper(), handler, exceptionHandler, false)
        );
        request.exceptionHandler(exceptionHandler);
        request.end();
    }

    public void copyObject(String sourceBucket,
                           String sourceKey,
                           String destinationBucket,
                           String destinationKey,
                           CopyObjectRequest copyObjectRequest,
                           Handler<Response<CopyObjectResponseHeaders, CopyObjectResponse>> handler,
                           Handler<Throwable> exceptionHandler) {
        checkNotNull(StringUtils.trimToNull(sourceBucket), "sourceBucket must not be null");
        checkNotNull(StringUtils.trimToNull(sourceKey), "sourceKey must not be null");
        checkNotNull(StringUtils.trimToNull(destinationBucket), "destinationBucket must not be null");
        checkNotNull(StringUtils.trimToNull(destinationKey), "destinationKey must not be null");
        checkNotNull(copyObjectRequest, "copyObjectRequest must not be null");
        checkNotNull(handler, "handler must not be null");
        checkNotNull(exceptionHandler, "exceptionHandler must not be null");

        final S3ClientRequest request = createCopyRequest(
                sourceBucket,
                sourceKey,
                destinationBucket,
                destinationKey,
                copyObjectRequest,
                new XmlBodyResponseHandler<>("copyObject", jaxbUnmarshaller, new CopyResponseHeadersMapper(), handler, exceptionHandler)
        );
        request.exceptionHandler(exceptionHandler);
        request.end();
    }

    public void deleteObject(String bucket,
                             String key,
                             DeleteObjectRequest deleteObjectRequest,
                             Handler<Response<CommonResponseHeaders, Void>> handler,
                             Handler<Throwable> exceptionHandler) {
        checkNotNull(StringUtils.trimToNull(bucket), "bucket must not be null");
        checkNotNull(StringUtils.trimToNull(key), "key must not be null");
        checkNotNull(deleteObjectRequest, "deleteObjectRequest must not be null");
        checkNotNull(handler, "handler must not be null");
        checkNotNull(exceptionHandler, "exceptionHandler must not be null");

        final S3ClientRequest request = createDeleteRequest(
                bucket,
                key,
                deleteObjectRequest,
                new HeadersResponseHandler("deleteObject", jaxbUnmarshaller, new CommonResponseHeadersMapper(), handler, exceptionHandler, false)
        );
        request.exceptionHandler(exceptionHandler);
        request.end();
    }

    public void getBucket(String bucket,
                          GetBucketRequest getBucketRequest,
                          Handler<Response<CommonResponseHeaders, GetBucketRespone>> handler,
                          Handler<Throwable> exceptionHandler) {
        checkNotNull(StringUtils.trimToNull(bucket), "bucket must not be null");
        checkNotNull(getBucketRequest, "getBucketRequest must not be null");
        checkNotNull(handler, "handler must not be null");
        checkNotNull(exceptionHandler, "exceptionHandler must not be null");

        final S3ClientRequest request = createGetBucketRequest(
                bucket,
                getBucketRequest,
                new XmlBodyResponseHandler<>("getBucket", jaxbUnmarshaller, new CommonResponseHeadersMapper(), handler, exceptionHandler)
        );
        request.exceptionHandler(exceptionHandler);
        request.end();
    }

    private S3ClientRequest createPutRequest(String bucket,
                                             String key,
                                             PutObjectRequest putObjectRequest,
                                             Handler<HttpClientResponse> handler) {
        HttpClientRequest httpRequest = client.put("/" + bucket + "/" + key, handler);
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
                .putHeader(Headers.HOST, hostname);

        s3ClientRequest.headers().addAll(populatePutObjectHeaders(putObjectRequest));
        s3ClientRequest.headers().addAll(populateAclHeadersRequest(putObjectRequest));
        return s3ClientRequest;
    }

    private S3ClientRequest createPutSseRequest(String bucket,
                                                String key,
                                                PutObjectRequest putObjectRequest,
                                                Optional<SseHeadersRequest> sseHeadersRequest,
                                                Handler<HttpClientResponse> handler) {
        final S3ClientRequest s3ClientRequest = createPutRequest(bucket, key, putObjectRequest, handler);
        sseHeadersRequest.ifPresent(e -> s3ClientRequest.headers().addAll(populateSseHeadersRequest(e)));
        return s3ClientRequest;
    }

    private S3ClientRequest createPutAclRequest(String bucket,
                                                String key,
                                                Optional<AclHeadersRequest> aclHeadersRequest,
                                                Handler<HttpClientResponse> handler) {
        HttpClientRequest httpRequest = client.put("/" + bucket + "/" + key + "?acl", handler);
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
                .putHeader(Headers.HOST, hostname);

        aclHeadersRequest.ifPresent(e -> s3ClientRequest.headers().addAll(populateAclHeadersRequest(e)));
        return s3ClientRequest;
    }

    private MultiMap populatePutObjectHeaders(PutObjectRequest putObjectRequest) {
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap();

        if (StringUtils.trimToNull(putObjectRequest.getCacheControl()) != null) {
            headers.add(Headers.CACHE_CONTROL, StringUtils.trim(putObjectRequest.getCacheControl()));
        }
        if (StringUtils.trimToNull(putObjectRequest.getContentDisposition()) != null) {
            headers.add(Headers.CONTENT_DISPOSITION, StringUtils.trim(putObjectRequest.getContentDisposition()));
        }
        if (StringUtils.trimToNull(putObjectRequest.getContentEncoding()) != null) {
            headers.add(Headers.CONTENT_ENCODING, StringUtils.trim(putObjectRequest.getContentEncoding()));
        }
        if (StringUtils.trimToNull(putObjectRequest.getContentMD5()) != null) {
            headers.add(Headers.CONTENT_MD5, StringUtils.trim(putObjectRequest.getContentMD5()));
        }
        if (StringUtils.trimToNull(putObjectRequest.getContentType()) != null) {
            headers.add(Headers.CONTENT_TYPE, StringUtils.trim(putObjectRequest.getContentType()));
        }
        if (StringUtils.trimToNull(putObjectRequest.getExpires()) != null) {
            headers.add(Headers.EXPIRES, StringUtils.trim(putObjectRequest.getExpires()));
        }


        for (Map.Entry<String, String> meta : putObjectRequest.getAmzMeta()) {
            headers.add(Headers.X_AMZ_META_PREFIX + meta.getKey(), StringUtils.trim(meta.getValue()));
        }
        if (putObjectRequest.getAmzStorageClass() != null) {
            headers.add(Headers.X_AMZ_STORAGE_CLASS, putObjectRequest.getAmzStorageClass().name());
        }
        if (StringUtils.trimToNull(putObjectRequest.getAmzTagging()) != null) {
            headers.add(Headers.X_AMZ_TAGGING, StringUtils.trim(putObjectRequest.getAmzTagging()));
        }
        if (StringUtils.trimToNull(putObjectRequest.getAmzWebsiteRedirectLocation()) != null) {
            headers.add(Headers.X_AMZ_WEBSITE_REDIRECT_LOCATION, StringUtils.trim(putObjectRequest.getAmzWebsiteRedirectLocation()));
        }

        return headers;
    }

    private MultiMap populateAclHeadersRequest(AclHeadersRequest aclHeadersRequest) {
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap();

        if (aclHeadersRequest.getAmzAcl() != null) {
            headers.add(Headers.X_AMZ_ACL, StringUtils.trim(aclHeadersRequest.getAmzAcl().getValue()));
        }
        if (StringUtils.trimToNull(aclHeadersRequest.getAmzGrantRead()) != null) {
            headers.add(Headers.X_AMZ_GRANT_READ, StringUtils.trim(aclHeadersRequest.getAmzGrantRead()));
        }
        if (StringUtils.trimToNull(aclHeadersRequest.getAmzGrantWrite()) != null) {
            headers.add(Headers.X_AMZ_GRANT_WRITE, StringUtils.trim(aclHeadersRequest.getAmzGrantWrite()));
        }
        if (StringUtils.trimToNull(aclHeadersRequest.getAmzGrantReadAcp()) != null) {
            headers.add(Headers.X_AMZ_GRANT_READ_ACP, StringUtils.trim(aclHeadersRequest.getAmzGrantReadAcp()));
        }
        if (StringUtils.trimToNull(aclHeadersRequest.getAmzGrantWriteAcp()) != null) {
            headers.add(Headers.X_AMZ_GRANT_WRITE_ACP, StringUtils.trim(aclHeadersRequest.getAmzGrantWriteAcp()));
        }
        if (StringUtils.trimToNull(aclHeadersRequest.getAmzGrantFullControl()) != null) {
            headers.add(Headers.X_AMZ_GRANT_FULL_CONTROL, StringUtils.trim(aclHeadersRequest.getAmzGrantFullControl()));
        }

        return headers;
    }

    private MultiMap populateSseHeadersRequest(SseHeadersRequest sseHeadersRequest) {
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap();

        if (StringUtils.trimToNull(sseHeadersRequest.getAmzServerSideEncryptionCustomerAlgorithm()) != null) {
            headers.add(Headers.X_AMZ_SERVER_SIDE_ENCRYPTION_CUSTOMER_ALGORITHM, StringUtils.trim(sseHeadersRequest.getAmzServerSideEncryptionCustomerAlgorithm()));
        }
        if (StringUtils.trimToNull(sseHeadersRequest.getAmzServerSideEncryptionCustomerKey()) != null) {
            headers.add(Headers.X_AMZ_SERVER_SIDE_ENCRYPTION_CUSTOMER_KEY, StringUtils.trim(sseHeadersRequest.getAmzServerSideEncryptionCustomerKey()));
        }
        if (StringUtils.trimToNull(sseHeadersRequest.getAmzServerSideEncryptionCustomerKeyMD5()) != null) {
            headers.add(Headers.X_AMZ_SERVER_SIDE_ENCRYPTION_CUSTOMER_KEY_MD5, StringUtils.trim(sseHeadersRequest.getAmzServerSideEncryptionCustomerKeyMD5()));
        }

        return headers;
    }

    private S3ClientRequest createInitMultipartUploadRequest(String bucket,
                                                             String key,
                                                             InitMultipartUploadRequest initMultipartUploadRequest,
                                                             Handler<HttpClientResponse> handler) {
        final HttpClientRequest httpRequest = client.post("/" + bucket + "/" + key + "?uploads", handler);
        final S3ClientRequest s3ClientRequest = new S3ClientRequest(
                "POST",
                awsRegion,
                awsServiceName,
                httpRequest,
                awsAccessKey,
                awsSecretKey,
                clock,
                signPayload
        )
                .setTimeout(globalTimeout)
                .putHeader(Headers.HOST, hostname);

        s3ClientRequest.headers().addAll(populateInitMultipartUploadHeaders(initMultipartUploadRequest));
        s3ClientRequest.headers().addAll(populateAclHeadersRequest(initMultipartUploadRequest));
        return s3ClientRequest;
    }


    private MultiMap populateInitMultipartUploadHeaders(InitMultipartUploadRequest multipartPutObjectRequest) {
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap();

        if (StringUtils.trimToNull(multipartPutObjectRequest.getCacheControl()) != null) {
            headers.add(Headers.CACHE_CONTROL, StringUtils.trim(multipartPutObjectRequest.getCacheControl()));
        }
        if (StringUtils.trimToNull(multipartPutObjectRequest.getContentDisposition()) != null) {
            headers.add(Headers.CONTENT_DISPOSITION, StringUtils.trim(multipartPutObjectRequest.getContentDisposition()));
        }
        if (StringUtils.trimToNull(multipartPutObjectRequest.getContentEncoding()) != null) {
            headers.add(Headers.CONTENT_ENCODING, StringUtils.trim(multipartPutObjectRequest.getContentEncoding()));
        }
        if (StringUtils.trimToNull(multipartPutObjectRequest.getContentType()) != null) {
            headers.add(Headers.CONTENT_TYPE, StringUtils.trim(multipartPutObjectRequest.getContentType()));
        }
        if (StringUtils.trimToNull(multipartPutObjectRequest.getExpires()) != null) {
            headers.add(Headers.EXPIRES, StringUtils.trim(multipartPutObjectRequest.getExpires()));
        }


        for (Map.Entry<String, String> meta : multipartPutObjectRequest.getAmzMeta()) {
            headers.add(Headers.X_AMZ_META_PREFIX + meta.getKey(), StringUtils.trim(meta.getValue()));
        }
        if (multipartPutObjectRequest.getAmzStorageClass() != null) {
            headers.add(Headers.X_AMZ_STORAGE_CLASS, multipartPutObjectRequest.getAmzStorageClass().name());
        }
        if (StringUtils.trimToNull(multipartPutObjectRequest.getAmzWebsiteRedirectLocation()) != null) {
            headers.add(Headers.X_AMZ_WEBSITE_REDIRECT_LOCATION, StringUtils.trim(multipartPutObjectRequest.getAmzWebsiteRedirectLocation()));
        }

        return headers;
    }

    private S3ClientRequest createContinueMultipartUploadRequest(String bucket,
                                                                 String key,
                                                                 ContinueMultipartUploadRequest continueMultipartUploadRequest,
                                                                 Handler<HttpClientResponse> handler) {
        final HttpClientRequest httpRequest = client.put(UrlEncodingUtils.addParamsSortedToUrl("/" + bucket + "/" + key, populateContinueMultipartUploadQueryParams(continueMultipartUploadRequest)), handler);
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
                .putHeader(Headers.HOST, hostname);

        s3ClientRequest.headers().addAll(populateContinueMultipartUploadHeaders(continueMultipartUploadRequest));
        return s3ClientRequest;
    }

    private Map<String, String> populateContinueMultipartUploadQueryParams(ContinueMultipartUploadRequest continueMultipartPutObjectRequest) {
        final Map<String, String> queryParams = new HashMap<>();

        queryParams.put("partNumber", continueMultipartPutObjectRequest.getPartNumber().toString());
        queryParams.put("uploadId", StringUtils.trim(continueMultipartPutObjectRequest.getUploadId()));

        return queryParams;
    }

    private MultiMap populateContinueMultipartUploadHeaders(ContinueMultipartUploadRequest continueMultipartPutObjectRequest) {
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap();

        if (StringUtils.trimToNull(continueMultipartPutObjectRequest.getContentMD5()) != null) {
            headers.add(Headers.CONTENT_MD5, StringUtils.trim(continueMultipartPutObjectRequest.getContentMD5()));
        }

        return headers;
    }

    private S3ClientRequest createCompleteMultipartUploadRequest(String bucket,
                                                                 String key,
                                                                 CompleteMultipartUploadRequest completeMultipartUploadRequest,
                                                                 Handler<HttpClientResponse> handler) {
        final HttpClientRequest httpRequest = client.post(UrlEncodingUtils.addParamsSortedToUrl("/" + bucket + "/" + key, populateCompleteMultipartUploadQueryParams(completeMultipartUploadRequest)), handler);
        final S3ClientRequest s3ClientRequest = new S3ClientRequest(
                "POST",
                awsRegion,
                awsServiceName,
                httpRequest,
                awsAccessKey,
                awsSecretKey,
                clock,
                signPayload
        )
                .setTimeout(globalTimeout)
                .putHeader(Headers.HOST, hostname);

        return s3ClientRequest;
    }

    private Map<String, String> populateCompleteMultipartUploadQueryParams(CompleteMultipartUploadRequest completeMultipartPutObjectRequest) {
        final Map<String, String> queryParams = new HashMap<>();

        queryParams.put("uploadId", StringUtils.trim(completeMultipartPutObjectRequest.getUploadId()));

        return queryParams;
    }

    private S3ClientRequest createAbortMultipartUploadRequest(String bucket,
                                                              String key,
                                                              AbortMultipartUploadRequest abortMultipartUploadRequest,
                                                              Handler<HttpClientResponse> handler) {
        final HttpClientRequest httpRequest = client.delete(UrlEncodingUtils.addParamsSortedToUrl("/" + bucket + "/" + key, populateAbortMultipartUploadQueryParams(abortMultipartUploadRequest)), handler);
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
                .putHeader(Headers.HOST, hostname);

        return s3ClientRequest;
    }

    private Map<String, String> populateAbortMultipartUploadQueryParams(AbortMultipartUploadRequest abortMultipartPutObjectRequest) {
        final Map<String, String> queryParams = new HashMap<>();

        queryParams.put("uploadId", StringUtils.trim(abortMultipartPutObjectRequest.getUploadId()));

        return queryParams;
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
                .putHeader(Headers.HOST, hostname);

        s3ClientRequest.putHeader(Headers.X_AMZ_COPY_SOURCE, "/" + sourceBucket + "/" + sourceKey);
        s3ClientRequest.headers().addAll(populateCopyObjectHeaders(copyObjectRequest));
        s3ClientRequest.headers().addAll(populateAclHeadersRequest(copyObjectRequest));
        return s3ClientRequest;
    }

    private MultiMap populateCopyObjectHeaders(CopyObjectRequest copyObjectRequest) {
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap();

        if (StringUtils.trimToNull(copyObjectRequest.getCacheControl()) != null) {
            headers.add(Headers.CACHE_CONTROL, StringUtils.trim(copyObjectRequest.getCacheControl()));
        }
        if (StringUtils.trimToNull(copyObjectRequest.getContentDisposition()) != null) {
            headers.add(Headers.CONTENT_DISPOSITION, StringUtils.trim(copyObjectRequest.getContentDisposition()));
        }
        if (StringUtils.trimToNull(copyObjectRequest.getContentEncoding()) != null) {
            headers.add(Headers.CONTENT_ENCODING, StringUtils.trim(copyObjectRequest.getContentEncoding()));
        }
        if (StringUtils.trimToNull(copyObjectRequest.getContentType()) != null) {
            headers.add(Headers.CONTENT_TYPE, StringUtils.trim(copyObjectRequest.getContentType()));
        }
        if (StringUtils.trimToNull(copyObjectRequest.getExpires()) != null) {
            headers.add(Headers.EXPIRES, StringUtils.trim(copyObjectRequest.getExpires()));
        }


        for (Map.Entry<String, String> meta : copyObjectRequest.getAmzMeta()) {
            headers.add(Headers.X_AMZ_META_PREFIX + meta.getKey(), StringUtils.trim(meta.getValue()));
        }

        if (copyObjectRequest.getAmzMetadataDirective() != null) {
            headers.add(Headers.X_AMZ_METADATA_DIRECTIVE, copyObjectRequest.getAmzMetadataDirective().name());
        }
        if (StringUtils.trimToNull(copyObjectRequest.getAmzCopySourceIfMatch()) != null) {
            headers.add(Headers.X_AMZ_COPY_SOURCE_IF_MATCH, StringUtils.trim(copyObjectRequest.getAmzCopySourceIfMatch()));
        }
        if (StringUtils.trimToNull(copyObjectRequest.getAmzCopySourceIfNoneMatch()) != null) {
            headers.add(Headers.X_AMZ_COPY_SOURCE_IF_NONE_MATCH, StringUtils.trim(copyObjectRequest.getAmzCopySourceIfNoneMatch()));
        }
        if (StringUtils.trimToNull(copyObjectRequest.getAmzCopySourceIfUnmodifiedSince()) != null) {
            headers.add(Headers.X_AMZ_COPY_SOURCE_IF_UNMODIFIED_SINCE, StringUtils.trim(copyObjectRequest.getAmzCopySourceIfUnmodifiedSince()));
        }
        if (StringUtils.trimToNull(copyObjectRequest.getAmzCopySourceIfModifiedSince()) != null) {
            headers.add(Headers.X_AMZ_COPY_SOURCE_IF_MODIFIED_SINCE, StringUtils.trim(copyObjectRequest.getAmzCopySourceIfModifiedSince()));
        }
        if (copyObjectRequest.getAmzStorageClass() != null) {
            headers.add(Headers.X_AMZ_STORAGE_CLASS, copyObjectRequest.getAmzStorageClass().name());
        }
        if (copyObjectRequest.getAmzTaggingDirective() != null) {
            headers.add(Headers.X_AMZ_TAGGING_DIRECTIVE, copyObjectRequest.getAmzTaggingDirective().name());
        }
        if (StringUtils.trimToNull(copyObjectRequest.getAmzWebsiteRedirectLocation()) != null) {
            headers.add(Headers.X_AMZ_WEBSITE_REDIRECT_LOCATION, StringUtils.trim(copyObjectRequest.getAmzWebsiteRedirectLocation()));
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
                .putHeader(Headers.HOST, hostname);

        s3ClientRequest.headers().addAll(populateGetObjectHeaders(getObjectRequest));
        return s3ClientRequest;
    }

    private S3ClientRequest createGetSseRequest(String bucket,
                                             String key,
                                             GetObjectRequest getObjectRequest,
                                             Optional<SseHeadersRequest> sseHeadersRequest,
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
                .putHeader(Headers.HOST, hostname);

        s3ClientRequest.headers().addAll(populateGetObjectHeaders(getObjectRequest));
        sseHeadersRequest.ifPresent(e -> s3ClientRequest.headers().addAll(populateSseHeadersRequest(e)));
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
            headers.add(Headers.RANGE, StringUtils.trim(getObjectRequest.getRange()));
        }
        if (StringUtils.trimToNull(getObjectRequest.getIfModifiedSince()) != null) {
            headers.add(Headers.IF_MODIFIED_SINCE, StringUtils.trim(getObjectRequest.getIfModifiedSince()));
        }
        if (StringUtils.trimToNull(getObjectRequest.getIfUnmodifiedSince()) != null) {
            headers.add(Headers.IF_UNMODIFIED_SINCE, StringUtils.trim(getObjectRequest.getIfUnmodifiedSince()));
        }
        if (StringUtils.trimToNull(getObjectRequest.getIfMatch()) != null) {
            headers.add(Headers.IF_MATCH, StringUtils.trim(getObjectRequest.getIfMatch()));
        }
        if (StringUtils.trimToNull(getObjectRequest.getIfNoneMatch()) != null) {
            headers.add(Headers.IF_NONE_MATCH, StringUtils.trim(getObjectRequest.getIfNoneMatch()));
        }

        return headers;
    }

    private S3ClientRequest createGetAclRequest(String bucket,
                                                String key,
                                                Handler<HttpClientResponse> handler) {
        final HttpClientRequest httpRequest = client.get("/" + bucket + "/" + key + "?acl", handler);
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
                .putHeader(Headers.HOST, hostname);

        return s3ClientRequest;
    }

    private S3ClientRequest createHeadRequest(String bucket,
                                              String key,
                                              HeadObjectRequest headObjectRequest,
                                              Handler<HttpClientResponse> handler) {
        final HttpClientRequest httpRequest = client.head("/" + bucket + "/" + key, handler);
        final S3ClientRequest s3ClientRequest = new S3ClientRequest(
                "HEAD",
                awsRegion,
                awsServiceName,
                httpRequest,
                awsAccessKey,
                awsSecretKey,
                clock,
                signPayload
        )
                .setTimeout(globalTimeout)
                .putHeader(Headers.HOST, hostname);

        s3ClientRequest.headers().addAll(populateHeadObjectHeaders(headObjectRequest));
        return s3ClientRequest;
    }

    private MultiMap populateHeadObjectHeaders(HeadObjectRequest headObjectRequest) {
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap();

        if (StringUtils.trimToNull(headObjectRequest.getRange()) != null) {
            headers.add(Headers.RANGE, StringUtils.trim(headObjectRequest.getRange()));
        }
        if (StringUtils.trimToNull(headObjectRequest.getIfModifiedSince()) != null) {
            headers.add(Headers.IF_MODIFIED_SINCE, StringUtils.trim(headObjectRequest.getIfModifiedSince()));
        }
        if (StringUtils.trimToNull(headObjectRequest.getIfUnmodifiedSince()) != null) {
            headers.add(Headers.IF_UNMODIFIED_SINCE, StringUtils.trim(headObjectRequest.getIfUnmodifiedSince()));
        }
        if (StringUtils.trimToNull(headObjectRequest.getIfMatch()) != null) {
            headers.add(Headers.IF_MATCH, StringUtils.trim(headObjectRequest.getIfMatch()));
        }
        if (StringUtils.trimToNull(headObjectRequest.getIfNoneMatch()) != null) {
            headers.add(Headers.IF_NONE_MATCH, StringUtils.trim(headObjectRequest.getIfNoneMatch()));
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
                .putHeader(Headers.HOST, hostname);

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
                .putHeader(Headers.HOST, hostname);

        s3ClientRequest.headers().addAll(populateDeleteObjectHeaders(deleteObjectRequest));
        return s3ClientRequest;
    }

    private MultiMap populateDeleteObjectHeaders(DeleteObjectRequest deleteObjectRequest) {
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap();

        if (StringUtils.trimToNull(deleteObjectRequest.getAmzMfa()) != null) {
            headers.add(Headers.X_AMZ_MFA, StringUtils.trim(deleteObjectRequest.getAmzMfa()));
        }

        return headers;
    }

    private static class StreamResponseHandler<H extends CommonResponseHeaders> implements Handler<HttpClientResponse> {

        private final String action;
        private final Unmarshaller jaxbUnmarshaller;
        private final ResponseHeaderMapper<H> responseHeaderMapper;
        private final Handler<ResponseWithBody<H, HttpClientResponse>> successHandler;
        private final Handler<Throwable> exceptionHandler;

        private StreamResponseHandler(String action, Unmarshaller jaxbUnmarshaller, ResponseHeaderMapper<H> responseHeaderMapper, Handler<ResponseWithBody<H, HttpClientResponse>> successHandler, Handler<Throwable> exceptionHandler) {
            this.action = action;
            this.jaxbUnmarshaller = jaxbUnmarshaller;
            this.responseHeaderMapper = responseHeaderMapper;
            this.successHandler = successHandler;
            this.exceptionHandler = exceptionHandler;
        }

        @Override
        public void handle(HttpClientResponse response) {
            if (response.statusCode() / 100 != 2) {
                response.bodyHandler(buffer -> {
                    try {
                        log.warn("Error occurred. Status: {}, Message: {}", response.statusCode(), response.statusMessage());
                        logInfoResponse(buffer);

                        exceptionHandler.handle(
                                new HttpErrorException(
                                        response.statusCode(),
                                        response.statusMessage(),
                                        (ErrorResponse) jaxbUnmarshaller.unmarshal(convertToSaxSource(buffer.getBytes())),
                                        "Error occurred during on '" + action + "'"
                                )
                        );
                    } catch (Exception e) {
                        exceptionHandler.handle(e);
                    }
                });
            } else {
                log.info("Request successful. Status: {}, Message: {}", response.statusCode(), response.statusMessage());
                successHandler.handle(new ResponseWithBody<>(responseHeaderMapper.map(response.headers()), response));
            }
        }
    }

    private static class XmlBodyResponseHandler<H extends CommonResponseHeaders, B> implements Handler<HttpClientResponse> {

        private final String action;
        private final Unmarshaller jaxbUnmarshaller;
        private final ResponseHeaderMapper<H> responseHeaderMapper;
        private final Handler<Response<H, B>> successHandler;
        private final Handler<Throwable> exceptionHandler;

        private XmlBodyResponseHandler(String action, Unmarshaller jaxbUnmarshaller, ResponseHeaderMapper<H> responseHeaderMapper, Handler<Response<H, B>> successHandler, Handler<Throwable> exceptionHandler) {
            this.action = action;
            this.jaxbUnmarshaller = jaxbUnmarshaller;
            this.responseHeaderMapper = responseHeaderMapper;
            this.successHandler = successHandler;
            this.exceptionHandler = exceptionHandler;
        }

        @Override
        public void handle(HttpClientResponse event) {
            event.bodyHandler(buffer -> {
                try {
                    if (event.statusCode() / 100 != 2) {
                        log.warn("Error occurred. Status: {}, Message: {}", event.statusCode(), event.statusMessage());
                        logInfoResponse(buffer);

                        exceptionHandler.handle(
                                new HttpErrorException(
                                        event.statusCode(),
                                        event.statusMessage(),
                                        (ErrorResponse) jaxbUnmarshaller.unmarshal(convertToSaxSource(buffer.getBytes())),
                                        "Error occurred on '" + action + "'"
                                )
                        );
                    } else {
                        log.info("Request successful. Status: {}, Message: {}", event.statusCode(), event.statusMessage());
                        logDebugResponse(buffer);
                        successHandler.handle(new ResponseWithBody<>(responseHeaderMapper.map(event.headers()), (B) jaxbUnmarshaller.unmarshal(convertToSaxSource(buffer.getBytes()))));
                    }
                } catch (UnmarshalException e) {
                    final String response = new String(buffer.getBytes(), Charsets.UTF_8);
                    exceptionHandler.handle(
                            new com.hubrick.vertx.s3.exception.UnmarshalException(
                                    response,
                                    "Error unmarshalling response: '" + response + "'"
                            )
                    );
                } catch (Exception e) {
                    exceptionHandler.handle(e);
                }
            });
        }
    }

    private static class HeadersResponseHandler<H extends CommonResponseHeaders> implements Handler<HttpClientResponse> {

        private final String action;
        private final Unmarshaller jaxbUnmarshaller;
        private final ResponseHeaderMapper<H> responseHeaderMapper;
        private final Handler<Response<H, Void>> successHandler;
        private final Handler<Throwable> exceptionHandler;
        private final boolean headOnly;

        private HeadersResponseHandler(String action, Unmarshaller jaxbUnmarshaller, ResponseHeaderMapper<H> responseHeaderMapper, Handler<Response<H, Void>> successHandler, Handler<Throwable> exceptionHandler, boolean headOnly) {
            this.action = action;
            this.jaxbUnmarshaller = jaxbUnmarshaller;
            this.responseHeaderMapper = responseHeaderMapper;
            this.successHandler = successHandler;
            this.exceptionHandler = exceptionHandler;
            this.headOnly = headOnly;
        }

        @Override
        public void handle(HttpClientResponse event) {
            event.bodyHandler(buffer -> {
                try {
                    if (event.statusCode() / 100 != 2) {
                        log.warn("Error occurred. Status: {}, Message: {}", event.statusCode(), event.statusMessage());
                        logInfoResponse(buffer);

                        final ErrorResponse errorResponse;
                        if (headOnly) {
                            errorResponse = null;
                        } else {
                            errorResponse = (ErrorResponse) jaxbUnmarshaller.unmarshal(convertToSaxSource(buffer.getBytes()));
                        }

                        exceptionHandler.handle(
                                new HttpErrorException(
                                        event.statusCode(),
                                        event.statusMessage(),
                                        errorResponse,
                                        "Error occurred on '" + action + "'"
                                )
                        );
                    } else {
                        log.info("Request successful. Status: {}, Message: {}", event.statusCode(), event.statusMessage());
                        if (log.isDebugEnabled()) {
                            log.debug("Response headers: {}", event.headers());
                        }
                        successHandler.handle(new HeaderOnlyResponse<>(responseHeaderMapper.map(event.headers())));
                    }
                } catch (Exception e) {
                    exceptionHandler.handle(e);
                }
            });
        }
    }

    private class GetResponseHeadersMapper implements ResponseHeaderMapper<GetObjectResponseHeaders> {

        @Override
        public GetObjectResponseHeaders map(MultiMap headers) {
            final GetObjectResponseHeaders getResponseHeaders = new GetObjectResponseHeaders();
            populateGetResponseHeaders(headers, getResponseHeaders);
            return getResponseHeaders;
        }
    }

    private void populateGetResponseHeaders(MultiMap headers, GetObjectResponseHeaders getResponseHeaders) {
        populateCommonResponseHeaders(headers, getResponseHeaders);
        populateServerSideEncryptionResponseHeaders(headers, getResponseHeaders);
        getResponseHeaders.setAmzExpiration(Optional.ofNullable(headers.get(Headers.X_AMZ_EXPIRATION)).filter(StringUtils::isNotBlank).orElse(null));
        getResponseHeaders.setAmzReplicationStatus(Optional.ofNullable(headers.get(Headers.X_AMZ_REPLICATION_STATUS)).filter(StringUtils::isNotBlank).map(ReplicationStatus::valueOf).orElse(null));
        getResponseHeaders.setAmzRestore(Optional.ofNullable(headers.get(Headers.X_AMZ_RESTORE)).filter(StringUtils::isNotBlank).orElse(null));
        getResponseHeaders.setAmzStorageClass(Optional.ofNullable(headers.get(Headers.X_AMZ_STORAGE_CLASS)).filter(StringUtils::isNotBlank).map(StorageClass::fromString).orElse(null));
        getResponseHeaders.setAmzTaggingCount(Optional.ofNullable(headers.get(Headers.X_AMZ_TAGGING_COUNT)).filter(StringUtils::isNotBlank).map(Integer::valueOf).orElse(null));
        getResponseHeaders.setAmzWebsiteRedirectLocation(Optional.ofNullable(headers.get(Headers.X_AMZ_WEBSITE_REDIRECT_LOCATION)).filter(StringUtils::isNotBlank).orElse(null));

        final MultiMap amzMeta = MultiMap.caseInsensitiveMultiMap();
        StreamSupport.stream(headers.spliterator(), true).filter(header -> header.getKey().toLowerCase().startsWith(Headers.X_AMZ_META_PREFIX)).forEach(header -> amzMeta.add(header.getKey().replaceFirst(Headers.X_AMZ_META_PREFIX, ""), header.getValue()));
        getResponseHeaders.setAmzMeta(amzMeta);
    }

    private class HeadResponseHeadersMapper implements ResponseHeaderMapper<HeadObjectResponseHeaders> {

        @Override
        public HeadObjectResponseHeaders map(MultiMap headers) {
            final HeadObjectResponseHeaders headResponseHeaders = new HeadObjectResponseHeaders();
            populateHeadResponseHeaders(headers, headResponseHeaders);
            return headResponseHeaders;
        }
    }

    private void populateHeadResponseHeaders(MultiMap headers, HeadObjectResponseHeaders headResponseHeaders) {
        populateCommonResponseHeaders(headers, headResponseHeaders);
        populateServerSideEncryptionResponseHeaders(headers, headResponseHeaders);
        headResponseHeaders.setAmzExpiration(Optional.ofNullable(headers.get(Headers.X_AMZ_EXPIRATION)).filter(StringUtils::isNotBlank).orElse(null));
        headResponseHeaders.setAmzMissingMeta(Optional.ofNullable(headers.get(Headers.X_AMZ_MISSING_META)).filter(StringUtils::isNotBlank).orElse(null));
        headResponseHeaders.setAmzReplicationStatus(Optional.ofNullable(headers.get(Headers.X_AMZ_REPLICATION_STATUS)).filter(StringUtils::isNotBlank).map(ReplicationStatus::valueOf).orElse(null));
        headResponseHeaders.setAmzRestore(Optional.ofNullable(headers.get(Headers.X_AMZ_RESTORE)).filter(StringUtils::isNotBlank).orElse(null));
        headResponseHeaders.setAmzStorageClass(Optional.ofNullable(headers.get(Headers.X_AMZ_STORAGE_CLASS)).filter(StringUtils::isNotBlank).map(StorageClass::fromString).orElse(null));

        final MultiMap amzMeta = MultiMap.caseInsensitiveMultiMap();
        StreamSupport.stream(headers.spliterator(), true).filter(header -> header.getKey().toLowerCase().startsWith(Headers.X_AMZ_META_PREFIX)).forEach(header -> amzMeta.add(header.getKey().replaceFirst(Headers.X_AMZ_META_PREFIX, ""), header.getValue()));
        headResponseHeaders.setAmzMeta(amzMeta);
    }

    private class CopyResponseHeadersMapper implements ResponseHeaderMapper<CopyObjectResponseHeaders> {

        @Override
        public CopyObjectResponseHeaders map(MultiMap headers) {
            final CopyObjectResponseHeaders copyResponseHeaders = new CopyObjectResponseHeaders();
            populateCopyResponseHeaders(headers, copyResponseHeaders);
            return copyResponseHeaders;
        }
    }

    private void populateCopyResponseHeaders(MultiMap headers, CopyObjectResponseHeaders copyResponseHeaders) {
        populateCommonResponseHeaders(headers, copyResponseHeaders);
        populateServerSideEncryptionResponseHeaders(headers, copyResponseHeaders);
        copyResponseHeaders.setAmzExpiration(Optional.ofNullable(headers.get(Headers.X_AMZ_EXPIRATION)).filter(StringUtils::isNotBlank).orElse(null));
        copyResponseHeaders.setAmzCopySourceVersionId(Optional.ofNullable(headers.get(Headers.X_AMZ_COPY_SOURCE_VERSION_ID)).filter(StringUtils::isNotBlank).orElse(null));
    }

    private class PutResponseHeadersMapper implements ResponseHeaderMapper<PutObjectResponseHeaders> {

        @Override
        public PutObjectResponseHeaders map(MultiMap headers) {
            final PutObjectResponseHeaders putResponseHeaders = new PutObjectResponseHeaders();
            populatePutResponseHeaders(headers, putResponseHeaders);
            return putResponseHeaders;
        }
    }

    private void populatePutResponseHeaders(MultiMap headers, PutObjectResponseHeaders putResponseHeaders) {
        populateCommonResponseHeaders(headers, putResponseHeaders);
        populateServerSideEncryptionResponseHeaders(headers, putResponseHeaders);
        putResponseHeaders.setAmzExpiration(Optional.ofNullable(headers.get(Headers.X_AMZ_EXPIRATION)).filter(StringUtils::isNotBlank).orElse(null));
    }

    private class InitMultipartUploadResponseHeadersMapper implements ResponseHeaderMapper<InitMultipartUploadResponseHeaders> {

        @Override
        public InitMultipartUploadResponseHeaders map(MultiMap headers) {
            final InitMultipartUploadResponseHeaders multipartPutObjectResponseHeaders = new InitMultipartUploadResponseHeaders();
            populateInitMultipartUploadResponseHeaders(headers, multipartPutObjectResponseHeaders);
            return multipartPutObjectResponseHeaders;
        }
    }

    private void populateInitMultipartUploadResponseHeaders(MultiMap headers, InitMultipartUploadResponseHeaders initMultipartPutObjectResponseHeaders) {
        populateCommonResponseHeaders(headers, initMultipartPutObjectResponseHeaders);
        populateServerSideEncryptionResponseHeaders(headers, initMultipartPutObjectResponseHeaders);
        initMultipartPutObjectResponseHeaders.setAmzAbortDate(Optional.ofNullable(headers.get(Headers.X_AMZ_ABORT_DATE)).filter(StringUtils::isNotBlank).orElse(null));
        initMultipartPutObjectResponseHeaders.setAmzAbortRuleId(Optional.ofNullable(headers.get(Headers.X_AMZ_ABORT_RULE_ID)).filter(StringUtils::isNotBlank).orElse(null));
    }

    private class ContinueMultipartUploadResponseHeadersMapper implements ResponseHeaderMapper<ContinueMultipartUploadResponseHeaders> {

        @Override
        public ContinueMultipartUploadResponseHeaders map(MultiMap headers) {
            final ContinueMultipartUploadResponseHeaders continueMultipartPutObjectResponseHeaders = new ContinueMultipartUploadResponseHeaders();
            populateContinueMultipartUploadResponseHeaders(headers, continueMultipartPutObjectResponseHeaders);
            return continueMultipartPutObjectResponseHeaders;
        }
    }

    private void populateContinueMultipartUploadResponseHeaders(MultiMap headers, ContinueMultipartUploadResponseHeaders continueMultipartPutObjectResponseHeaders) {
        populateCommonResponseHeaders(headers, continueMultipartPutObjectResponseHeaders);
        populateServerSideEncryptionResponseHeaders(headers, continueMultipartPutObjectResponseHeaders);
    }

    private class CompleteMultipartUploadResponseHeadersMapper implements ResponseHeaderMapper<CompleteMultipartUploadResponseHeaders> {

        @Override
        public CompleteMultipartUploadResponseHeaders map(MultiMap headers) {
            final CompleteMultipartUploadResponseHeaders completeMultipartPutObjectResponseHeaders = new CompleteMultipartUploadResponseHeaders();
            populateCompleteMultipartUploadResponseHeaders(headers, completeMultipartPutObjectResponseHeaders);
            return completeMultipartPutObjectResponseHeaders;
        }
    }

    private void populateCompleteMultipartUploadResponseHeaders(MultiMap headers, CompleteMultipartUploadResponseHeaders completeMultipartPutObjectResponseHeaders) {
        populateCommonResponseHeaders(headers, completeMultipartPutObjectResponseHeaders);
        populateServerSideEncryptionResponseHeaders(headers, completeMultipartPutObjectResponseHeaders);
        completeMultipartPutObjectResponseHeaders.setAmzExpiration(Optional.ofNullable(headers.get(Headers.X_AMZ_EXPIRATION)).filter(StringUtils::isNotBlank).orElse(null));
    }

    private class CommonResponseHeadersMapper implements ResponseHeaderMapper<CommonResponseHeaders> {

        @Override
        public CommonResponseHeaders map(MultiMap headers) {
            final CommonResponseHeaders commonResponseHeaders = new CommonResponseHeaders();
            populateCommonResponseHeaders(headers, commonResponseHeaders);
            return commonResponseHeaders;
        }
    }

    private void populateServerSideEncryptionResponseHeaders(MultiMap headers, ServerSideEncryptionResponseHeaders serverSideEncryptionResponseHeaders) {
        serverSideEncryptionResponseHeaders.setAmzServerSideEncryption(Optional.ofNullable(headers.get(Headers.X_AMZ_SERVER_SIDE_ENCRYPTION)).filter(StringUtils::isNotBlank).orElse(null));
        serverSideEncryptionResponseHeaders.setAmzServerSideEncryptionAwsKmsKeyId(Optional.ofNullable(headers.get(Headers.X_AMZ_SERVER_SIDE_ENCRYPTION_AWS_KMS_KEY_ID)).filter(StringUtils::isNotBlank).orElse(null));
        serverSideEncryptionResponseHeaders.setAmzServerSideEncryptionCustomerAlgorithm(Optional.ofNullable(headers.get(Headers.X_AMZ_SERVER_SIDE_ENCRYPTION_CUSTOMER_ALGORITHM)).filter(StringUtils::isNotBlank).orElse(null));
        serverSideEncryptionResponseHeaders.setAmzServerSideEncryptionCustomerKeyMD5(Optional.ofNullable(headers.get(Headers.X_AMZ_SERVER_SIDE_ENCRYPTION_CUSTOMER_KEY_MD5)).filter(StringUtils::isNotBlank).orElse(null));
    }

    private void populateCommonResponseHeaders(MultiMap headers, CommonResponseHeaders commonResponseHeaders) {
        commonResponseHeaders.setContentType(Optional.ofNullable(headers.get(Headers.CONTENT_TYPE)).filter(StringUtils::isNotBlank).orElse(null));
        commonResponseHeaders.setContentLength(Optional.ofNullable(headers.get(Headers.CONTENT_LENGTH)).filter(StringUtils::isNotBlank).map(Long::valueOf).orElse(null));
        commonResponseHeaders.setDate(Optional.ofNullable(headers.get(Headers.DATE)).filter(StringUtils::isNotBlank).orElse(null));
        commonResponseHeaders.setETag(Optional.ofNullable(headers.get(Headers.ETAG)).filter(StringUtils::isNotBlank).orElse(null));
        commonResponseHeaders.setConnection(Optional.ofNullable(headers.get(Headers.CONNECTION)).filter(StringUtils::isNotBlank).map(Connection::fromString).orElse(null));
        commonResponseHeaders.setServer(Optional.ofNullable(headers.get(Headers.SERVER)).filter(StringUtils::isNotBlank).orElse(null));
        commonResponseHeaders.setAmzDeleteMarker(Optional.ofNullable(headers.get(Headers.X_AMZ_DELETE_MARKER)).filter(StringUtils::isNotBlank).map(Boolean::valueOf).orElse(null));
        commonResponseHeaders.setAmzId2(Optional.ofNullable(headers.get(Headers.X_AMZ_ID_2)).filter(StringUtils::isNotBlank).orElse(null));
        commonResponseHeaders.setAmzRequestId(Optional.ofNullable(headers.get(Headers.X_AMZ_REQUEST_ID)).filter(StringUtils::isNotBlank).orElse(null));
        commonResponseHeaders.setAmzVersionId(Optional.ofNullable(headers.get(Headers.X_AMZ_VERSION_ID)).filter(StringUtils::isNotBlank).orElse(null));
    }

    private interface ResponseHeaderMapper<T extends CommonResponseHeaders> {
        T map(MultiMap headers);

    }

    private JAXBContext createJAXBContext() {
        try {
            return JAXBContext.newInstance(
                    Contents.class,
                    CommonPrefixes.class,
                    GetBucketRespone.class,
                    CopyObjectResponse.class,
                    InitMultipartUploadResponse.class,
                    CompleteMultipartUploadRequest.class,
                    CompleteMultipartUploadResponse.class,
                    AccessControlPolicy.class,
                    Grant.class,
                    Grantee.class,
                    Part.class,
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

    private static SAXSource convertToSaxSource(byte[] payload) throws SAXException {
        //Create an XMLReader to use with our filter
        final XMLReader reader = XMLReaderFactory.createXMLReader();

        //Create the filter to remove all namespaces and set the xmlReader as its parent.
        final NamespaceFilter inFilter = new NamespaceFilter(null, false);
        inFilter.setParent(reader);

        final InputSource inputSource = new InputSource(new ByteArrayInputStream(payload));

        //Create a SAXSource specifying the filter
        return new SAXSource(inFilter, inputSource);
    }

    private static void logDebugResponse(Buffer buffer) {
        if(log.isDebugEnabled()) {
            if(buffer.length() > MAX_LOG_OUTPUT) {
                log.debug("Response: {}", new String(buffer.getBytes(0, MAX_LOG_OUTPUT), Charsets.UTF_8) + "\nRest truncated......");
            } else {
                log.debug("Response: {}", new String(buffer.getBytes(), Charsets.UTF_8));
            }
        }
    }

    private static void logInfoResponse(Buffer buffer) {
        if(log.isInfoEnabled()) {
            if(buffer.length() > MAX_LOG_OUTPUT) {
                log.info("Response: {}", new String(buffer.getBytes(0, MAX_LOG_OUTPUT), Charsets.UTF_8) + "\nRest truncated......");
            } else {
                log.info("Response: {}", new String(buffer.getBytes(), Charsets.UTF_8));
            }
        }
    }
}
