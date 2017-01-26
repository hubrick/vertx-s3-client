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
import com.hubrick.vertx.s3.model.ListBucketRequest;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import org.junit.Before;
import org.mockserver.model.Header;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
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

    void mockGet(Header... expectedHeaders) {
        getMockServerClient().when(
                request()
                        .withMethod("GET")
                        .withPath("/bucket/key")
                        .withHeaders(expectedHeaders)

        ).respond(
                response()
                        .withStatusCode(200)
                        .withHeader(Header.header("Content-Type", "application/json;charset=UTF-8"))
                        .withBody("response")
        );
    }


    void verifyGet(TestContext testContext) {
        final Async async = testContext.async();
        s3Client.get("bucket", "key",
                (response) -> {
                    assertThat(testContext, response.statusCode(), is(200));

                    response.bodyHandler(buffer -> {
                        assertThat(testContext, new String(buffer.getBytes(), StandardCharsets.UTF_8), is("response"));
                        async.complete();

                    });
                },
                testContext::fail);
    }

    void mockPut(Header... expectedHeaders) {
        getMockServerClient().when(
                request()
                        .withMethod("PUT")
                        .withPath("/bucket/key")
                        .withHeaders(expectedHeaders)
                        .withBody("test")

        ).respond(
                response()
                        .withStatusCode(200)
                        .withHeader(Header.header("Content-Type", "application/json;charset=UTF-8"))
                        .withBody("response")
        );
    }

    void verifyPut(final TestContext testContext) {

        final Async async = testContext.async();
        s3Client.put("bucket", "key",
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

    void mockDelete(Header... expectedHeaders) {
        getMockServerClient().when(
                request()
                        .withMethod("DELETE")
                        .withPath("/bucket/key")
                        .withHeaders(expectedHeaders)

        ).respond(
                response()
                        .withStatusCode(200)
                        .withHeader(Header.header("Content-Type", "application/json;charset=UTF-8"))
                        .withBody("response")
        );
    }

    void verifyDelete(final TestContext testContext) {

        final Async async = testContext.async();
        s3Client.delete("bucket", "key",
                (response) -> {
                    assertThat(testContext, response.statusCode(), is(200));

                    response.bodyHandler(buffer -> {
                        assertThat(testContext, new String(buffer.getBytes(), StandardCharsets.UTF_8), is("response"));
                        async.complete();

                    });
                },
                testContext::fail);
    }

    void mockCopy(Header... expectedHeaders) {
        getMockServerClient().when(
                request()
                        .withMethod("PUT")
                        .withPath("/destinationBucket/destinationKey")
                        .withHeaders(expectedHeaders)
                        .withHeader("X-Amz-Copy-Source", "/sourceBucket/sourceKey")

        ).respond(
                response()
                        .withStatusCode(200)
                        .withHeader(Header.header("Content-Type", "application/json;charset=UTF-8"))
                        .withBody("response")
        );
    }

    void verifyCopy(final TestContext testContext) {

        final Async async = testContext.async();
        s3Client.copy(
                "sourceBucket", "sourceKey",
                "destinationBucket", "destinationKey",
                (response) -> {
                    assertThat(testContext, response.statusCode(), is(200));

                    response.bodyHandler(buffer -> {
                        assertThat(testContext, new String(buffer.getBytes(), StandardCharsets.UTF_8), is("response"));
                        async.complete();

                    });
                },
                testContext::fail);
    }

    void mockListBucket(Map<String, List<String>> expectedQueryParams, Header... expectedHeaders) throws IOException {
        mockListBucket(expectedQueryParams, "/response/listBucketResult.xml", 200, expectedHeaders);
    }

    void mockListBucketErrorResponse(Map<String, List<String>> expectedQueryParams, Header... expectedHeaders) throws IOException {
        mockListBucket(expectedQueryParams, "/response/errorResponse.xml", 403, expectedHeaders);
    }

    private void mockListBucket(Map<String, List<String>> expectedQueryParams, String response, Integer statusCode, Header... expectedHeaders) throws IOException {
        getMockServerClient().when(
                request()
                        .withMethod("GET")
                        .withPath("/sourceBucket")
                        .withHeaders(expectedHeaders)
                        .withQueryStringParameters(expectedQueryParams)
        ).respond(
                response()
                        .withStatusCode(statusCode)
                        .withHeader(Header.header("Content-Type", "application/json;charset=UTF-8"))
                        .withBody(Resources.toByteArray(Resources.getResource(AbstractS3ClientTest.class, response)))
        );
    }

    void verifyListBucket(final TestContext testContext) {

        final Async async = testContext.async();
        s3Client.listBucket(
                "sourceBucket",
                new ListBucketRequest(),
                (listBucketResult) -> {
                    assertThat(testContext, listBucketResult, notNullValue());
                    assertThat(testContext, listBucketResult.getContentsList(), hasSize(5));
                    assertThat(testContext, listBucketResult.getName(), is("bucket"));

                    async.complete();
                },
                testContext::fail
        );
    }

    void verifyListBucketErrorResponse(final TestContext testContext) {

        final Async async = testContext.async();
        s3Client.listBucket(
                "sourceBucket",
                new ListBucketRequest(),
                (listBucketResult) -> {
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

}
