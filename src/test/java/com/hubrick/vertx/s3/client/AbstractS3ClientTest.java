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

import com.google.common.io.Resources;
import com.hubrick.vertx.s3.AbstractFunctionalTest;
import com.hubrick.vertx.s3.S3TestCredentials;
import com.hubrick.vertx.s3.exception.HttpErrorException;
import com.hubrick.vertx.s3.model.CopyObjectRequest;
import com.hubrick.vertx.s3.model.DeleteObjectRequest;
import com.hubrick.vertx.s3.model.GetBucketRequest;
import com.hubrick.vertx.s3.model.GetObjectRequest;
import com.hubrick.vertx.s3.model.PutObjectRequest;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.Before;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.hubrick.vertx.s3.VertxMatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.hasSize;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author marcus
 * @since 1.0.0
 */
public abstract class AbstractS3ClientTest extends AbstractFunctionalTest {

    public static final String HOSTNAME = "localhost";

    private S3Client s3Client;

    @Before
    public void setUp() throws Exception {
        final S3ClientOptions clientOptions = new S3ClientOptions();
        clientOptions.setDefaultHost(HOSTNAME);
        clientOptions.setDefaultPort(MOCKSERVER_PORT);
        clientOptions.setMaxPoolSize(10);
        clientOptions.setAwsRegion(S3TestCredentials.REGION);
        clientOptions.setAwsServiceName(S3TestCredentials.SERVICE_NAME);
        clientOptions.setHostnameOverride(HOSTNAME);

        augmentClientOptions(clientOptions);

        s3Client = new S3Client(
                vertx,
                clientOptions,
                Clock.fixed(Instant.ofEpochSecond(1478782934), ZoneId.of("UTC")));

    }

    protected abstract void augmentClientOptions(final S3ClientOptions clientOptions);

    void addCredentials(final S3ClientOptions clientOptions) {
        clientOptions.setAwsAccessKey(S3TestCredentials.AWS_ACCESS_KEY);
        clientOptions.setAwsSecretKey(S3TestCredentials.AWS_SECRET_KEY);
    }

    void mockGetObject(Header... expectedHeaders) throws IOException {
        mock(
                Collections.emptyMap(),
                "GET",
                "/bucket/key",
                200,
                "response".getBytes(),
                expectedHeaders
        );
    }


    void mockGetObjectErrorResponse(Header... expectedHeaders) throws IOException {
        mock(
                Collections.emptyMap(),
                "GET",
                "/bucket/key",
                403,
                Resources.toByteArray(Resources.getResource(AbstractS3ClientTest.class, "/response/errorResponse.xml")),
                expectedHeaders
        );
    }

    void verifyGetObject(TestContext testContext) {
        final Async async = testContext.async();
        s3Client.getObject("bucket", "key", new GetObjectRequest(),
                (response) -> {
                    assertThat(testContext, response.statusCode(), is(200));

                    response.bodyHandler(buffer -> {
                        assertThat(testContext, new String(buffer.getBytes(), StandardCharsets.UTF_8), is("response"));
                        async.complete();

                    });
                },
                testContext::fail);
    }

    void verifyGetObjectErrorResponse(final TestContext testContext) {

        final Async async = testContext.async();
        s3Client.getObject("bucket", "key", new GetObjectRequest(),
                (result) -> {
                    testContext.fail("Exceptions should be thrown");
                },
                error -> {
                    assertThat(testContext, error, instanceOf(HttpErrorException.class));

                    final HttpErrorException httpErrorException = (HttpErrorException) error;
                    assertThat(testContext, httpErrorException.getStatus(), is(403));
                    assertThat(testContext, httpErrorException.getErrorResponse().getCode(), is("SignatureDoesNotMatch"));
                    async.complete();
                }
        );
    }

    void mockPutObject(Header... expectedHeaders) throws IOException {
        mock(
                Collections.emptyMap(),
                "PUT",
                "/bucket/key",
                200,
                "test".getBytes(),
                "response".getBytes(),
                expectedHeaders
        );
    }

    void mockPutObjectErrorResponse(Header... expectedHeaders) throws IOException {
        mock(
                Collections.emptyMap(),
                "PUT",
                "/bucket/key",
                403,
                "test".getBytes(),
                Resources.toByteArray(Resources.getResource(AbstractS3ClientTest.class, "/response/errorResponse.xml")),
                expectedHeaders
        );
    }

    void verifyPutObject(final TestContext testContext) {

        final Async async = testContext.async();
        s3Client.putObject("bucket", "key", new PutObjectRequest(),
                Buffer.buffer("test"),
                (response) -> {
                    assertThat(testContext, response.statusCode(), is(200));

                    response.bodyHandler(buffer -> {
                        assertThat(testContext, new String(buffer.getBytes(), StandardCharsets.UTF_8), is("response"));
                        async.complete();

                    });
                },
                testContext::fail);
    }

    void verifyPutObjectErrorResponse(final TestContext testContext) {

        final Async async = testContext.async();
        s3Client.putObject("bucket", "key", new PutObjectRequest(),
                Buffer.buffer("test"),
                (result) -> {
                    testContext.fail("Exceptions should be thrown");
                },
                error -> {
                    assertThat(testContext, error, instanceOf(HttpErrorException.class));

                    final HttpErrorException httpErrorException = (HttpErrorException) error;
                    assertThat(testContext, httpErrorException.getStatus(), is(403));
                    assertThat(testContext, httpErrorException.getErrorResponse().getCode(), is("SignatureDoesNotMatch"));
                    async.complete();
                }
        );
    }

    void mockDeleteObject(Header... expectedHeaders) throws IOException {
        mock(
                Collections.emptyMap(),
                "DELETE",
                "/bucket/key",
                200,
                "response".getBytes(),
                expectedHeaders
        );
    }

    void mockDeleteObjectErrorResponse(Header... expectedHeaders) throws IOException {
        mock(
                Collections.emptyMap(),
                "DELETE",
                "/bucket/key",
                403,
                Resources.toByteArray(Resources.getResource(AbstractS3ClientTest.class, "/response/errorResponse.xml")),
                expectedHeaders
        );
    }

    void verifyDeleteObject(final TestContext testContext) {

        final Async async = testContext.async();
        s3Client.deleteObject("bucket", "key", new DeleteObjectRequest(),
                (response) -> {
                    assertThat(testContext, response.statusCode(), is(200));

                    response.bodyHandler(buffer -> {
                        assertThat(testContext, new String(buffer.getBytes(), StandardCharsets.UTF_8), is("response"));
                        async.complete();

                    });
                },
                testContext::fail);
    }

    void verifyDeleteObjectErrorResponse(final TestContext testContext) {

        final Async async = testContext.async();
        s3Client.deleteObject(
                "bucket", "key", new DeleteObjectRequest(),
                (result) -> {
                    testContext.fail("Exceptions should be thrown");
                },
                error -> {
                    assertThat(testContext, error, instanceOf(HttpErrorException.class));

                    final HttpErrorException httpErrorException = (HttpErrorException) error;
                    assertThat(testContext, httpErrorException.getStatus(), is(403));
                    assertThat(testContext, httpErrorException.getErrorResponse().getCode(), is("SignatureDoesNotMatch"));
                    async.complete();
                }
        );
    }

    void mockCopyObject(Header... expectedHeaders) throws IOException {
        mock(
                Collections.emptyMap(),
                "PUT",
                "/destinationBucket/destinationKey",
                200,
                "response".getBytes(),
                ArrayUtils.add(expectedHeaders, Header.header("X-Amz-Copy-Source", "/sourceBucket/sourceKey"))
        );
    }

    void mockCopyObjectErrorResponse(Header... expectedHeaders) throws IOException {
        mock(
                Collections.emptyMap(),
                "PUT",
                "/destinationBucket/destinationKey",
                403,
                Resources.toByteArray(Resources.getResource(AbstractS3ClientTest.class, "/response/errorResponse.xml")),
                ArrayUtils.add(expectedHeaders, Header.header("X-Amz-Copy-Source", "/sourceBucket/sourceKey"))
        );
    }

    void verifyCopyObject(final TestContext testContext) {

        final Async async = testContext.async();
        s3Client.copyObject(
                "sourceBucket", "sourceKey",
                "destinationBucket", "destinationKey",
                new CopyObjectRequest(),
                (response) -> {
                    assertThat(testContext, response.statusCode(), is(200));

                    response.bodyHandler(buffer -> {
                        assertThat(testContext, new String(buffer.getBytes(), StandardCharsets.UTF_8), is("response"));
                        async.complete();

                    });
                },
                testContext::fail);
    }

    void verifyCopyObjectErrorResponse(final TestContext testContext) {

        final Async async = testContext.async();
        s3Client.copyObject(
                "sourceBucket", "sourceKey",
                "destinationBucket", "destinationKey",
                new CopyObjectRequest(),
                (result) -> {
                    testContext.fail("Exceptions should be thrown");
                },
                error -> {
                    assertThat(testContext, error, instanceOf(HttpErrorException.class));

                    final HttpErrorException httpErrorException = (HttpErrorException) error;
                    assertThat(testContext, httpErrorException.getStatus(), is(403));
                    assertThat(testContext, httpErrorException.getErrorResponse().getCode(), is("SignatureDoesNotMatch"));
                    async.complete();
                }
        );
    }

    void mockGetBucket(Map<String, List<String>> expectedQueryParams, Header... expectedHeaders) throws IOException {
        mock(
                expectedQueryParams,
                "GET",
                "/sourceBucket",
                200,
                Resources.toByteArray(Resources.getResource(AbstractS3ClientTest.class, "/response/listBucketResult.xml")),
                expectedHeaders
        );
    }

    void mockGetBucketErrorResponse(Map<String, List<String>> expectedQueryParams, Header... expectedHeaders) throws IOException {
        mock(
                expectedQueryParams,
                "GET",
                "/sourceBucket",
                403,
                Resources.toByteArray(Resources.getResource(AbstractS3ClientTest.class, "/response/errorResponse.xml")),
                expectedHeaders
        );
    }

    void verifyGetBucket(final TestContext testContext) {

        final Async async = testContext.async();
        s3Client.getBucket(
                "sourceBucket",
                new GetBucketRequest()
                        .withContinuationToken("14HF6Dfbr92F1EYlZIrMwxPYKQl5lD/mbwiw5+Nlrn1lYIZX3YGzo16Dgz+dxbxFeNGmLsnzwnbbuQM0CMl0krVwh8TBj8nCmNtq/iQCK6gzln8z3U4C71Mh2HyEMHcMgrZGR/akosVql7/AIctj6rA=="),
                (getBucketRespone) -> {
                    assertThat(testContext, getBucketRespone, notNullValue());
                    assertThat(testContext, getBucketRespone.getContentsList(), hasSize(5));
                    assertThat(testContext, getBucketRespone.getName(), is("bucket"));

                    async.complete();
                },
                testContext::fail
        );
    }

    void verifyGetBucketErrorResponse(final TestContext testContext) {

        final Async async = testContext.async();
        s3Client.getBucket(
                "sourceBucket",
                new GetBucketRequest()
                        .withContinuationToken("14HF6Dfbr92F1EYlZIrMwxPYKQl5lD/mbwiw5+Nlrn1lYIZX3YGzo16Dgz+dxbxFeNGmLsnzwnbbuQM0CMl0krVwh8TBj8nCmNtq/iQCK6gzln8z3U4C71Mh2HyEMHcMgrZGR/akosVql7/AIctj6rA=="),
                (result) -> {
                    testContext.fail("Exceptions should be thrown");
                },
                error -> {
                    assertThat(testContext, error, instanceOf(HttpErrorException.class));

                    final HttpErrorException httpErrorException = (HttpErrorException) error;
                    assertThat(testContext, httpErrorException.getStatus(), is(403));
                    assertThat(testContext, httpErrorException.getErrorResponse().getCode(), is("SignatureDoesNotMatch"));
                    async.complete();
                }
        );
    }

    void mock(Map<String, List<String>> expectedQueryParams, String method, String path, Integer statusCode, byte[] responseBody, Header... expectedHeaders) throws IOException {
        mock(expectedQueryParams, method, path, statusCode, null, responseBody, expectedHeaders);
    }

    void mock(Map<String, List<String>> expectedQueryParams, String method, String path, Integer statusCode, byte[] requestBody, byte[] responseBody, Header... expectedHeaders) throws IOException {
        final HttpRequest httpRequest = request()
                .withMethod(method)
                .withPath(path)
                .withHeaders(expectedHeaders)
                .withQueryStringParameters(expectedQueryParams);

        if (requestBody != null) {
            httpRequest.withBody(requestBody);
        }

        getMockServerClient().when(
                httpRequest
        ).respond(
                response()
                        .withStatusCode(statusCode)
                        .withHeader(Header.header("Content-Type", "application/xml;charset=UTF-8"))
                        .withBody(responseBody)
        );
    }
}
