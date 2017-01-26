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
import com.hubrick.vertx.s3.model.ErrorResponse;
import com.hubrick.vertx.s3.model.ListBucketRequest;
import com.hubrick.vertx.s3.model.ListBucketResult;
import com.hubrick.vertx.s3.model.Owner;
import com.hubrick.vertx.s3.model.filter.NamespaceFilter;
import com.hubrick.vertx.s3.util.UrlEncodingUtils;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
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

    public S3Client(Vertx vertx,
                    S3ClientOptions s3ClientOptions) {
        this(vertx, s3ClientOptions, Clock.systemDefaultZone());
    }

    public S3Client(Vertx vertx,
                    S3ClientOptions s3ClientOptions,
                    Clock clock) {
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

    public void get(String bucket,
                    String key,
                    MultiMap headers,
                    Handler<HttpClientResponse> handler,
                    Handler<Throwable> exceptionHandler) {
        final S3ClientRequest request = createGetRequest(bucket, key, headers, new StreamResponseHandler(jaxbUnmarshaller, handler, exceptionHandler));
        request.exceptionHandler(exceptionHandler);
        request.end();
    }

    public void put(String bucket,
                    String key,
                    MultiMap headers,
                    Buffer data,
                    Handler<HttpClientResponse> handler,
                    Handler<Throwable> exceptionHandler) {
        final S3ClientRequest request = createPutRequest(bucket, key, headers, new StreamResponseHandler(jaxbUnmarshaller, handler, exceptionHandler));
        request.exceptionHandler(exceptionHandler);
        request.end(data);
    }

    public void copy(String sourceBucket,
                     String sourceKey,
                     String destinationBucket,
                     String destinationKey,
                     Handler<HttpClientResponse> handler,
                     Handler<Throwable> exceptionHandler) {
        final S3ClientRequest request = createCopyRequest(sourceBucket, sourceKey, destinationBucket, destinationKey, new StreamResponseHandler(jaxbUnmarshaller, handler, exceptionHandler));
        request.exceptionHandler(exceptionHandler);
        request.end();
    }

    public void delete(String bucket,
                       String key,
                       Handler<HttpClientResponse> handler,
                       Handler<Throwable> exceptionHandler) {
        final S3ClientRequest request = createDeleteRequest(bucket, key, new StreamResponseHandler(jaxbUnmarshaller, handler, exceptionHandler));
        request.exceptionHandler(exceptionHandler);
        request.end();
    }

    public void listBucket(String bucket,
                           ListBucketRequest listBucketRequest,
                           Handler<ListBucketResult> handler,
                           Handler<Throwable> exceptionHandler) {
        final S3ClientRequest request = createListBucketRequest(bucket, listBucketRequest, new BodyResponseHandler<>("listBucket", jaxbUnmarshaller, handler, exceptionHandler));
        request.exceptionHandler(exceptionHandler);
        request.end();
    }

    private S3ClientRequest createPutRequest(String bucket,
                                             String key,
                                             MultiMap headers,
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

        s3ClientRequest.headers().addAll(headers);
        return s3ClientRequest;
    }

    private S3ClientRequest createCopyRequest(String sourceBucket,
                                              String sourceKey,
                                              String destinationBucket,
                                              String destinationKey,
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

        return s3ClientRequest.putHeader(S3Headers.COPY_SOURCE_HEADER.getValue(), "/" + sourceBucket + "/" + sourceKey);
    }

    private S3ClientRequest createGetRequest(String bucket,
                                             String key,
                                             MultiMap headers,
                                             Handler<HttpClientResponse> handler) {
        final HttpClientRequest httpRequest = client.get("/" + bucket + "/" + key, handler);
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

        s3ClientRequest.headers().addAll(headers);
        return s3ClientRequest;
    }

    private S3ClientRequest createListBucketRequest(String bucket,
                                                    ListBucketRequest listBucketRequest,
                                                    Handler<HttpClientResponse> handler) {

        final Map<String, String> queryParams = populateListBucketQueryParams(listBucketRequest);
        final HttpClientRequest httpRequest = client.get(UrlEncodingUtils.addParamsSortedToUrl("/" + bucket, queryParams), handler);

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

    private Map<String, String> populateListBucketQueryParams(ListBucketRequest listObjectsRequest) {
        final Map<String, String> queryParams = new HashMap<>();
        queryParams.put("list-type", "2");

        if (listObjectsRequest.getContinuationToken() != null) {
            queryParams.put("continuation-token", listObjectsRequest.getContinuationToken());
        }
        if (listObjectsRequest.getDelimiter() != null) {
            queryParams.put("delimiter", listObjectsRequest.getDelimiter());
        }
        if (listObjectsRequest.getEncodingType() != null) {
            queryParams.put("encoding-type", listObjectsRequest.getEncodingType());
        }
        if (listObjectsRequest.getFetchOwner() != null) {
            queryParams.put("fetch-owner", listObjectsRequest.getFetchOwner());
        }
        if (listObjectsRequest.getMaxKeys() != null) {
            queryParams.put("max-keys", listObjectsRequest.getMaxKeys().toString());
        }
        if (listObjectsRequest.getPrefix() != null) {
            queryParams.put("prefix", listObjectsRequest.getPrefix());
        }
        if (listObjectsRequest.getStartAfter() != null) {
            queryParams.put("start-after", listObjectsRequest.getStartAfter());
        }

        return queryParams;
    }

    private S3ClientRequest createDeleteRequest(String bucket,
                                               String key,
                                               Handler<HttpClientResponse> handler) {
        final HttpClientRequest httpRequest = client.delete("/" + bucket + "/" + key, handler);
        return new S3ClientRequest(
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
    }

    private JAXBContext createJAXBContext() {
        try {
            return JAXBContext.newInstance(
                    Contents.class,
                    CommonPrefixes.class,
                    ListBucketResult.class,
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

        private final Unmarshaller jaxbUnmarshaller;
        private final Handler<HttpClientResponse> successHandler;
        private final Handler<Throwable> exceptionHandler;

        private StreamResponseHandler(Unmarshaller jaxbUnmarshaller, Handler<HttpClientResponse> successHandler, Handler<Throwable> exceptionHandler) {
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
                                        "Error occurred during on 'listBucket'"
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
