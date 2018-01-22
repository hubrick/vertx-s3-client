package com.hubrick.vertx.s3.client;

import com.google.common.io.Resources;
import com.hubrick.vertx.s3.exception.HttpErrorException;
import com.hubrick.vertx.s3.model.request.GetObjectRequest;
import com.hubrick.vertx.s3.model.request.PutObjectRequest;
import com.hubrick.vertx.s3.model.request.SseHeadersRequest;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import org.junit.Test;
import org.mockserver.model.BinaryBody;
import org.mockserver.model.Header;
import org.mockserver.model.StringBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static com.hubrick.vertx.s3.VertxMatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.*;

/**
 * @author gruboter
 * @since 3.3.2
 */
public class S3ClientWithSseTest extends AbstractS3ClientTest {
    static final String AES_256 = "AES256";
    static final String SSE_KEY = "kD5dHUuV+qOECwfQ7TzCarHx5R3X5FQevPqQxuVrOAA=";
    static final String SSE_KEY_MD5 = "YV2YxLW3WYxKJ6qVhD+D8Q==";
    static final String TEXT_PLAIN = "text/plain";
    static final String TEST_FILE_CONTENT = "test-file-content";

    @Override
    protected void augmentClientOptions(final S3ClientOptions clientOptions) {
    }

    // ----  OK Tests

    @Test
    public void testPutObjectSse(TestContext testContext) throws IOException {
        mockPutObjectSse();

        verifyPutObjectSse(testContext);
    }

    @Test
    public void testGetObjectSse(TestContext testContext) throws IOException {
        mockGetObjectSse();

        verifyGetObjectSse(testContext);
    }

    // ----  ERROR Tests

    @Test
    public void testGetObjectSseError(TestContext testContext) throws IOException {
        mockGetObjectSseErrorResponse();

        verifyGetObjectSseError(testContext);
    }

    @Test
    public void testPutObjectSseError(TestContext testContext) throws IOException {
        mockPutObjectSseErrorResponse();

        verifyPutObjectSseError(testContext);
    }

    // ---- GetObjectSse

    private void verifyGetObjectSse(final TestContext testContext) {
        final Async async = testContext.async();
        s3Client.getObjectSse(
                "bucket",
                "key",
                new GetObjectRequest()
                        .withResponseContentType(TEXT_PLAIN),
                new SseHeadersRequest<>()
                        .withAmzServerSideEncryptionCustomerAlgorithm(AES_256)
                        .withAmzServerSideEncryptionCustomerKey(SSE_KEY)
                        .withAmzServerSideEncryptionCustomerKeyMD5(SSE_KEY_MD5),
                (getObjectResponse) -> {
                    assertThat(testContext, getObjectResponse.getHeader(), notNullValue());
                    assertThat(testContext, getObjectResponse.getData(), notNullValue());
                    getObjectResponse.getData().handler(buffer -> {
                        assertThat(testContext, new String(buffer.getBytes(), StandardCharsets.UTF_8), is(TEST_FILE_CONTENT));
                        async.complete();

                    });
                },
                testContext::fail);
    }


    private void mockGetObjectSse(Header... expectedHeaders) throws IOException {
        mock(
                Collections.emptyMap(),
                "GET",
                "/bucket/key",
                200,
                TEST_FILE_CONTENT.getBytes(),
                expectedHeaders
        );
    }

    // ---- PutObjectSse

    private void verifyPutObjectSse(final TestContext testContext) {
        final Async async = testContext.async();
        s3Client.putObjectSse(
                "bucket",
                "key",
                new PutObjectRequest(Buffer.buffer(TEST_FILE_CONTENT))
                        .withContentType(TEXT_PLAIN),
                new SseHeadersRequest<>()
                        .withAmzServerSideEncryptionCustomerAlgorithm(AES_256)
                        .withAmzServerSideEncryptionCustomerKey(SSE_KEY)
                        .withAmzServerSideEncryptionCustomerKeyMD5(SSE_KEY_MD5),
                (putResponseHeaders) -> {
                    assertThat(testContext, putResponseHeaders, notNullValue());
                    async.complete();
                },
                testContext::fail);
    }

    private void mockPutObjectSse(Header... expectedHeaders) throws IOException {
        mock(
                Collections.emptyMap(),
                "PUT",
                "/bucket/key",
                200,
                new StringBody(TEST_FILE_CONTENT),
                new StringBody("<>"),
                Collections.emptyList(),
                expectedHeaders
        );
    }

    // ---- GetObjectSseError

    private void verifyGetObjectSseError(final TestContext testContext) {
        final Async async = testContext.async();
        s3Client.getObjectSse(
                "bucket",
                "key",
                new GetObjectRequest()
                        .withResponseContentType(TEXT_PLAIN),
                new SseHeadersRequest<>()
                        .withAmzServerSideEncryptionCustomerAlgorithm(AES_256)
                        .withAmzServerSideEncryptionCustomerKey(SSE_KEY)
                        .withAmzServerSideEncryptionCustomerKeyMD5(SSE_KEY_MD5),
                (result) -> {
                    testContext.fail("Exceptions should be thrown");
                },
                error -> {
                    assertThat(testContext, error, instanceOf(HttpErrorException.class));

                    final HttpErrorException httpErrorException = (HttpErrorException) error;
                    assertThat(testContext, httpErrorException.getStatus(), is(404));
                    async.complete();
                }
        );
    }


    private void mockGetObjectSseErrorResponse(Header... expectedHeaders) throws IOException {
        mock(
                Collections.emptyMap(),
                "GET",
                "/bucket/key",
                404, // not found
                Resources.toByteArray(Resources.getResource(AbstractS3ClientTest.class, "/response/errorResponse.xml")),
                expectedHeaders
        );
    }

    // ---- PutObjectSseError

    private void verifyPutObjectSseError(final TestContext testContext) {
        final Async async = testContext.async();
        s3Client.putObjectSse(
                "bucket",
                "key",
                new PutObjectRequest(Buffer.buffer(TEST_FILE_CONTENT))
                        .withContentType(TEXT_PLAIN),
                new SseHeadersRequest<>()
                        .withAmzServerSideEncryptionCustomerAlgorithm(AES_256)
                        .withAmzServerSideEncryptionCustomerKey(SSE_KEY)
                        .withAmzServerSideEncryptionCustomerKeyMD5(SSE_KEY_MD5),
                (result) -> {
                    testContext.fail("Exceptions should be thrown");
                },
                error -> {
                    assertThat(testContext, error, instanceOf(HttpErrorException.class));
                    final HttpErrorException httpErrorException = (HttpErrorException) error;
                    assertThat(testContext, httpErrorException.getStatus(), is(403));
                    async.complete();
                }
        );
    }

    void mockPutObjectSseErrorResponse(Header... expectedHeaders) throws IOException {
        mock(
                Collections.emptyMap(),
                "PUT",
                "/bucket/key",
                403,
                new StringBody(""),
                new BinaryBody(Resources.toByteArray(Resources.getResource(AbstractS3ClientTest.class, "/response/errorResponse.xml"))),
                Collections.emptyList(),
                expectedHeaders
        );
    }


}
