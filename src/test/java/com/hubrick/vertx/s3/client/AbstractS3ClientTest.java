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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.hubrick.vertx.s3.AbstractFunctionalTest;
import com.hubrick.vertx.s3.S3TestCredentials;
import com.hubrick.vertx.s3.exception.HttpErrorException;
import com.hubrick.vertx.s3.model.AccessControlPolicy;
import com.hubrick.vertx.s3.model.CannedAcl;
import com.hubrick.vertx.s3.model.ErrorCode;
import com.hubrick.vertx.s3.model.Part;
import com.hubrick.vertx.s3.model.Response;
import com.hubrick.vertx.s3.model.header.CommonResponseHeaders;
import com.hubrick.vertx.s3.model.header.CompleteMultipartUploadResponseHeaders;
import com.hubrick.vertx.s3.model.header.ContinueMultipartUploadResponseHeaders;
import com.hubrick.vertx.s3.model.header.InitMultipartUploadResponseHeaders;
import com.hubrick.vertx.s3.model.request.AbortMultipartUploadRequest;
import com.hubrick.vertx.s3.model.request.AclHeadersRequest;
import com.hubrick.vertx.s3.model.request.CompleteMultipartUploadRequest;
import com.hubrick.vertx.s3.model.request.ContinueMultipartUploadRequest;
import com.hubrick.vertx.s3.model.request.CopyObjectRequest;
import com.hubrick.vertx.s3.model.request.DeleteObjectRequest;
import com.hubrick.vertx.s3.model.request.GetBucketRequest;
import com.hubrick.vertx.s3.model.request.GetObjectRequest;
import com.hubrick.vertx.s3.model.request.HeadObjectRequest;
import com.hubrick.vertx.s3.model.request.InitMultipartUploadRequest;
import com.hubrick.vertx.s3.model.request.PutObjectAclRequest;
import com.hubrick.vertx.s3.model.request.PutObjectRequest;
import com.hubrick.vertx.s3.model.response.CompleteMultipartUploadResponse;
import com.hubrick.vertx.s3.model.response.MultipartUploadWriteStream;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.Before;
import org.mockserver.model.BinaryBody;
import org.mockserver.model.Body;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.StringBody;
import org.mockserver.model.XmlBody;

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
import static org.hamcrest.Matchers.nullValue;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author marcus
 * @since 1.0.0
 */
public abstract class AbstractS3ClientTest extends AbstractFunctionalTest {

    public static final String HOSTNAME = "localhost";

    protected S3Client s3Client;

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
                (getObjectResponse) -> {
                    assertThat(testContext, getObjectResponse.getHeader(), notNullValue());
                    assertThat(testContext, getObjectResponse.getData(), notNullValue());

                    getObjectResponse.getData().handler(buffer -> {
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
                    assertThat(testContext, httpErrorException.getErrorResponse().getCode(), is(ErrorCode.SIGNATURE_DOES_NOT_MATCH));
                    async.complete();
                }
        );
    }

    void mockGetObjectAcl(AccessControlPolicy accessControlPolicy, Header... expectedHeaders) throws IOException {
        mock(
                ImmutableMap.of("acl", ImmutableList.of("")),
                "GET",
                "/bucket/key",
                200,
                null,
                new StringBody("<AccessControlPolicy><Owner><ID>" + accessControlPolicy.getOwner().getId() + "</ID><DisplayName>" + accessControlPolicy.getOwner().getDisplayName() + "</DisplayName></Owner>" +
                        "<AccessControlList><Grant><Grantee><ID>" + accessControlPolicy.getGrants().get(0).getGrantee().getId() + "</ID><DisplayName>" + accessControlPolicy.getGrants().get(0).getGrantee().getDisplayName() + "</DisplayName></Grantee><Permission>" + accessControlPolicy.getGrants().get(0).getPermission() + "</Permission></Grant></AccessControlList></AccessControlPolicy>"
                ),
                Collections.emptyList(),
                expectedHeaders
        );
    }

    void mockGetObjectAclErrorResponse(Header... expectedHeaders) throws IOException {
        mock(
                ImmutableMap.of("acl", ImmutableList.of("")),
                "GET",
                "/bucket/key",
                403,
                null,
                new BinaryBody(Resources.toByteArray(Resources.getResource(AbstractS3ClientTest.class, "/response/errorResponse.xml"))),
                Collections.emptyList(),
                expectedHeaders
        );
    }

    void verifyGetObjectAcl(TestContext testContext, AccessControlPolicy accessControlPolicy) {
        final Async async = testContext.async();
        s3Client.getObjectAcl("bucket", "key",
                (getObjectResponse) -> {
                    assertThat(testContext, getObjectResponse.getHeader(), notNullValue());
                    assertThat(testContext, getObjectResponse.getData(), notNullValue());
                    assertThat(testContext, getObjectResponse.getData(), is(accessControlPolicy));

                    async.complete();
                },
                testContext::fail);
    }

    void verifyGetObjectAclErrorResponse(final TestContext testContext) {

        final Async async = testContext.async();
        s3Client.getObjectAcl("bucket", "key",
                (result) -> {
                    testContext.fail("Exceptions should be thrown");
                },
                error -> {
                    assertThat(testContext, error, instanceOf(HttpErrorException.class));

                    final HttpErrorException httpErrorException = (HttpErrorException) error;
                    assertThat(testContext, httpErrorException.getStatus(), is(403));
                    assertThat(testContext, httpErrorException.getErrorResponse().getCode(), is(ErrorCode.SIGNATURE_DOES_NOT_MATCH));
                    async.complete();
                }
        );
    }

    void mockHeadObject(Header... expectedHeaders) throws IOException {
        mock(
                Collections.emptyMap(),
                "HEAD",
                "/bucket/key",
                200,
                "0".getBytes(),
                expectedHeaders
        );
    }

    void mockHeadObjectErrorResponse(Header... expectedHeaders) throws IOException {
        mock(
                Collections.emptyMap(),
                "HEAD",
                "/bucket/key",
                403,
                "0".getBytes(),
                expectedHeaders
        );
    }

    void verifyHeadObject(TestContext testContext) {
        final Async async = testContext.async();
        s3Client.headObject("bucket", "key", new HeadObjectRequest(),
                (response) -> {
                    assertThat(testContext, response, notNullValue());
                    async.complete();
                },
                testContext::fail
        );
    }

    void verifyHeadObjectErrorResponse(final TestContext testContext) {
        final Async async = testContext.async();
        s3Client.headObject("bucket", "key", new HeadObjectRequest(),
                (result) -> {
                    testContext.fail("Exceptions should be thrown");
                },
                error -> {
                    assertThat(testContext, error, instanceOf(HttpErrorException.class));

                    final HttpErrorException httpErrorException = (HttpErrorException) error;
                    assertThat(testContext, httpErrorException.getStatus(), is(403));
                    assertThat(testContext, httpErrorException.getErrorResponse(), nullValue());
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
                new StringBody("test"),
                new StringBody("<>"),
                Collections.emptyList(),
                expectedHeaders
        );
    }

    void mockPutObjectErrorResponse(Header... expectedHeaders) throws IOException {
        mock(
                Collections.emptyMap(),
                "PUT",
                "/bucket/key",
                403,
                new StringBody("test"),
                new BinaryBody(Resources.toByteArray(Resources.getResource(AbstractS3ClientTest.class, "/response/errorResponse.xml"))),
                Collections.emptyList(),
                expectedHeaders
        );
    }

    void verifyPutObject(final TestContext testContext) {

        final Async async = testContext.async();
        s3Client.putObject("bucket", "key", new PutObjectRequest(Buffer.buffer("test")),
                (putResponseHeaders) -> {
                    assertThat(testContext, putResponseHeaders, notNullValue());
                    async.complete();
                },
                testContext::fail);
    }

    void verifyPutObjectErrorResponse(final TestContext testContext) {

        final Async async = testContext.async();
        s3Client.putObject("bucket", "key", new PutObjectRequest(Buffer.buffer("test")),
                (result) -> {
                    testContext.fail("Exceptions should be thrown");
                },
                error -> {
                    assertThat(testContext, error, instanceOf(HttpErrorException.class));

                    final HttpErrorException httpErrorException = (HttpErrorException) error;
                    assertThat(testContext, httpErrorException.getStatus(), is(403));
                    assertThat(testContext, httpErrorException.getErrorResponse().getCode(), is(ErrorCode.SIGNATURE_DOES_NOT_MATCH));
                    async.complete();
                }
        );
    }

    void mockPutObjectAclWithHeaders(Header... expectedHeaders) throws IOException {
        mock(
                ImmutableMap.of("acl", ImmutableList.of("")),
                "PUT",
                "/bucket/key",
                200,
                "test".getBytes(),
                Collections.emptyList(),
                expectedHeaders
        );
    }

    void verifyPutObjectAclWithHeaders(final TestContext testContext) {

        final Async async = testContext.async();
        s3Client.putObjectAcl("bucket", "key", new PutObjectAclRequest(new AclHeadersRequest().withAmzAcl(CannedAcl.PRIVATE)),
                (putResponseHeaders) -> {
                    assertThat(testContext, putResponseHeaders, notNullValue());
                    async.complete();
                },
                testContext::fail);
    }

    void mockPutObjectAclWithHeadersErrorResponse(Header... expectedHeaders) throws IOException {
        mock(
                ImmutableMap.of("acl", ImmutableList.of("")),
                "PUT",
                "/bucket/key",
                403,
                Resources.toByteArray(Resources.getResource(AbstractS3ClientTest.class, "/response/errorResponse.xml")),
                Collections.emptyList(),
                expectedHeaders
        );
    }

    void verifyPutObjectAclWithHeadersErrorResponse(final TestContext testContext) {

        final Async async = testContext.async();
        s3Client.putObjectAcl("bucket", "key", new PutObjectAclRequest(new AclHeadersRequest().withAmzAcl(CannedAcl.PRIVATE)),
                (result) -> {
                    testContext.fail("Exceptions should be thrown");
                },
                error -> {
                    assertThat(testContext, error, instanceOf(HttpErrorException.class));

                    final HttpErrorException httpErrorException = (HttpErrorException) error;
                    assertThat(testContext, httpErrorException.getStatus(), is(403));
                    assertThat(testContext, httpErrorException.getErrorResponse().getCode(), is(ErrorCode.SIGNATURE_DOES_NOT_MATCH));
                    async.complete();
                }
        );
    }

    void mockPutObjectAclWithBody(AccessControlPolicy accessControlPolicy, Header... expectedHeaders) throws IOException {
        mock(
                ImmutableMap.of("acl", ImmutableList.of("")),
                "PUT",
                "/bucket/key",
                200,
                new XmlBody("<AccessControlPolicy><Owner><ID>" + accessControlPolicy.getOwner().getId() + "</ID><DisplayName>" + accessControlPolicy.getOwner().getDisplayName() + "</DisplayName></Owner>" +
                        "<AccessControlList><Grant><Grantee><ID>" + accessControlPolicy.getGrants().get(0).getGrantee().getId() + "</ID><DisplayName>" + accessControlPolicy.getGrants().get(0).getGrantee().getDisplayName() + "</DisplayName></Grantee><Permission>" + accessControlPolicy.getGrants().get(0).getPermission() + "</Permission></Grant></AccessControlList></AccessControlPolicy>"
                ),
                new StringBody("<>"),
                Collections.emptyList(),
                expectedHeaders
        );
    }

    void verifyPutObjectAclWithBody(AccessControlPolicy accessControlPolicy, final TestContext testContext) {

        final Async async = testContext.async();
        s3Client.putObjectAcl("bucket", "key", new PutObjectAclRequest(accessControlPolicy),
                (putResponseHeaders) -> {
                    assertThat(testContext, putResponseHeaders, notNullValue());
                    async.complete();
                },
                testContext::fail);
    }

    void mockPutObjectAclWithBodyErrorResponse(AccessControlPolicy accessControlPolicy, Header... expectedHeaders) throws IOException {
        mock(
                ImmutableMap.of("acl", ImmutableList.of("")),
                "PUT",
                "/bucket/key",
                403,
                new XmlBody("<AccessControlPolicy><Owner><ID>" + accessControlPolicy.getOwner().getId() + "</ID><DisplayName>" + accessControlPolicy.getOwner().getDisplayName() + "</DisplayName></Owner>" +
                        "<AccessControlList><Grant><Grantee><ID>" + accessControlPolicy.getGrants().get(0).getGrantee().getId() + "</ID><DisplayName>" + accessControlPolicy.getGrants().get(0).getGrantee().getDisplayName() + "</DisplayName></Grantee><Permission>" + accessControlPolicy.getGrants().get(0).getPermission() + "</Permission></Grant></AccessControlList></AccessControlPolicy>"
                ),
                new BinaryBody(Resources.toByteArray(Resources.getResource(AbstractS3ClientTest.class, "/response/errorResponse.xml"))),
                Collections.emptyList(),
                expectedHeaders
        );
    }

    void verifyPutObjectAclWithBodyErrorResponse(AccessControlPolicy accessControlPolicy, final TestContext testContext) {

        final Async async = testContext.async();
        s3Client.putObjectAcl("bucket", "key", new PutObjectAclRequest(accessControlPolicy),
                (result) -> {
                    testContext.fail("Exceptions should be thrown");
                },
                error -> {
                    assertThat(testContext, error, instanceOf(HttpErrorException.class));

                    final HttpErrorException httpErrorException = (HttpErrorException) error;
                    assertThat(testContext, httpErrorException.getStatus(), is(403));
                    assertThat(testContext, httpErrorException.getErrorResponse().getCode(), is(ErrorCode.SIGNATURE_DOES_NOT_MATCH));
                    async.complete();
                }
        );
    }

    void mockInitMultipartUpload(String uploadId, Header... expectedHeaders) throws IOException {
        mock(
                ImmutableMap.of("uploads", ImmutableList.of("")),
                "POST",
                "/bucket/key",
                200,
                ("<InitiateMultipartUploadResult><Bucket>bucket</Bucket><Key>key</Key><UploadId>" + uploadId + "</UploadId></InitiateMultipartUploadResult>").getBytes(),
                expectedHeaders
        );
    }

    void mockInitMultipartUploadErrorResponse(Header... expectedHeaders) throws IOException {
        mock(
                ImmutableMap.of("uploads", ImmutableList.of("")),
                "POST",
                "/bucket/key",
                403,
                Resources.toByteArray(Resources.getResource(AbstractS3ClientTest.class, "/response/errorResponse.xml")),
                expectedHeaders
        );
    }

    void verifyInitMultipartUpload(final TestContext testContext) {

        final Async async = testContext.async();
        callInitMultipartUpload(testContext, event -> {
            assertThat(testContext, event.getData(), notNullValue());
            assertThat(testContext, event.getHeader(), notNullValue());

            async.complete();
        });
    }

    void verifyInitMultipartUploadErrorResponse(final TestContext testContext) {

        final Async async = testContext.async();
        s3Client.initMultipartUpload("bucket", "key", new InitMultipartUploadRequest(),
                (result) -> {
                    testContext.fail("Exceptions should be thrown");
                },
                error -> {
                    assertThat(testContext, error, instanceOf(HttpErrorException.class));

                    final HttpErrorException httpErrorException = (HttpErrorException) error;
                    assertThat(testContext, httpErrorException.getStatus(), is(403));
                    assertThat(testContext, httpErrorException.getErrorResponse().getCode(), is(ErrorCode.SIGNATURE_DOES_NOT_MATCH));
                    async.complete();
                }
        );
    }

    void callInitMultipartUpload(final TestContext testContext, Handler<Response<InitMultipartUploadResponseHeaders, MultipartUploadWriteStream>> handler) {

        s3Client.initMultipartUpload(
                "bucket",
                "key",
                new InitMultipartUploadRequest(),
                handler,
                testContext::fail
        );
    }


    void mockContinueMultipartUpload(Integer partNumber, String uploadId, Header... expectedHeaders) throws IOException {
        mock(
                ImmutableMap.of("partNumber", ImmutableList.of(partNumber.toString()), "uploadId", ImmutableList.of(uploadId)),
                "PUT",
                "/bucket/key",
                200,
                "<>".getBytes(),
                ImmutableList.of(new Header("ETag", "someetag")),
                expectedHeaders
        );
    }

    void mockContinueMultipartUploadErrorResponse(Integer partNumber, String uploadId, Header... expectedHeaders) throws IOException {
        mock(
                ImmutableMap.of("partNumber", ImmutableList.of(partNumber.toString()), "uploadId", ImmutableList.of(uploadId)),
                "PUT",
                "/bucket/key",
                403,
                Resources.toByteArray(Resources.getResource(AbstractS3ClientTest.class, "/response/errorResponse.xml")),
                expectedHeaders
        );
    }

    void verifyContinueMultipartUpload(final TestContext testContext, Integer partNumber, String uploadId) {

        final Async async = testContext.async();
        callContinueMultipartUpload(testContext, partNumber, uploadId, event -> {
            assertThat(testContext, event.getData(), nullValue());
            assertThat(testContext, event.getHeader(), notNullValue());

            async.complete();
        });
    }

    void verifyContinueMultipartUploadErrorResponse(final TestContext testContext, Integer partNumber, String uploadId) {

        final Async async = testContext.async();
        s3Client.continueMultipartUpload(
                "bucket",
                "key",
                new ContinueMultipartUploadRequest(Buffer.buffer("whatever".getBytes()), partNumber, uploadId),
                (result) -> {
                    testContext.fail("Exceptions should be thrown");
                },
                error -> {
                    assertThat(testContext, error, instanceOf(HttpErrorException.class));

                    final HttpErrorException httpErrorException = (HttpErrorException) error;
                    assertThat(testContext, httpErrorException.getStatus(), is(403));
                    assertThat(testContext, httpErrorException.getErrorResponse().getCode(), is(ErrorCode.SIGNATURE_DOES_NOT_MATCH));
                    async.complete();
                }
        );
    }

    void callContinueMultipartUpload(final TestContext testContext, Integer partNumber, String uploadId, Handler<Response<ContinueMultipartUploadResponseHeaders, Void>> handler) {

        s3Client.continueMultipartUpload(
                "bucket",
                "key",
                new ContinueMultipartUploadRequest(Buffer.buffer("some data".getBytes()), partNumber, uploadId),
                handler,
                testContext::fail
        );
    }

    void mockCompleteMultipartUpload(String uploadId, Header... expectedHeaders) throws IOException {
        mock(
                ImmutableMap.of("uploadId", ImmutableList.of(uploadId)),
                "POST",
                "/bucket/key",
                200,
                "<CompleteMultipartUploadResult><Location>whatever</Location><Bucket>bucket</Bucket><Key>key</Key><ETag>whatever</ETag></CompleteMultipartUploadResult>".getBytes(),
                expectedHeaders
        );
    }

    void mockCompleteMultipartUploadErrorResponse(String uploadId, Header... expectedHeaders) throws IOException {
        mock(
                ImmutableMap.of("uploadId", ImmutableList.of(uploadId)),
                "POST",
                "/bucket/key",
                403,
                Resources.toByteArray(Resources.getResource(AbstractS3ClientTest.class, "/response/errorResponse.xml")),
                expectedHeaders
        );
    }

    void verifyCompleteMultipartUpload(final TestContext testContext, String uploadId) {

        final Async async = testContext.async();
        callCompleteMultipartUpload(testContext, Collections.emptyList(), uploadId, event -> {
            assertThat(testContext, event.getData(), notNullValue());
            assertThat(testContext, event.getHeader(), notNullValue());

            async.complete();
        });
    }

    void verifyCompleteMultipartUploadErrorResponse(final TestContext testContext, String uploadId) {

        final Async async = testContext.async();
        s3Client.completeMultipartUpload(
                "bucket",
                "key",
                new CompleteMultipartUploadRequest(uploadId, Collections.emptyList()),
                (result) -> {
                    testContext.fail("Exceptions should be thrown");
                },
                error -> {
                    assertThat(testContext, error, instanceOf(HttpErrorException.class));

                    final HttpErrorException httpErrorException = (HttpErrorException) error;
                    assertThat(testContext, httpErrorException.getStatus(), is(403));
                    assertThat(testContext, httpErrorException.getErrorResponse().getCode(), is(ErrorCode.SIGNATURE_DOES_NOT_MATCH));
                    async.complete();
                }
        );
    }

    void callCompleteMultipartUpload(final TestContext testContext, List<Part> parts, String uploadId, Handler<Response<CompleteMultipartUploadResponseHeaders, CompleteMultipartUploadResponse>> handler) {

        s3Client.completeMultipartUpload(
                "bucket",
                "key",
                new CompleteMultipartUploadRequest(uploadId, parts),
                handler,
                testContext::fail
        );
    }

    void mockAbortMultipartUpload(String uploadId, Header... expectedHeaders) throws IOException {
        mock(
                ImmutableMap.of("uploadId", ImmutableList.of(uploadId)),
                "DELETE",
                "/bucket/key",
                200,
                "<>".getBytes(),
                expectedHeaders
        );
    }

    void mockAbortMultipartUploadErrorResponse(String uploadId, Header... expectedHeaders) throws IOException {
        mock(
                ImmutableMap.of("uploadId", ImmutableList.of(uploadId)),
                "DELETE",
                "/bucket/key",
                403,
                Resources.toByteArray(Resources.getResource(AbstractS3ClientTest.class, "/response/errorResponse.xml")),
                expectedHeaders
        );
    }

    void verifyAbortMultipartUpload(final TestContext testContext, String uploadId) {
        final Async async = testContext.async();
        callAbortMultipartUpload(testContext, uploadId, event -> {
            assertThat(testContext, event.getData(), nullValue());
            assertThat(testContext, event.getHeader(), notNullValue());

            async.complete();
        });
    }

    void verifyAbortMultipartUploadErrorResponse(final TestContext testContext, String uploadId) {

        final Async async = testContext.async();
        s3Client.abortMultipartUpload(
                "bucket",
                "key",
                new AbortMultipartUploadRequest(uploadId),
                (result) -> {
                    testContext.fail("Exceptions should be thrown");
                },
                error -> {
                    assertThat(testContext, error, instanceOf(HttpErrorException.class));

                    final HttpErrorException httpErrorException = (HttpErrorException) error;
                    assertThat(testContext, httpErrorException.getStatus(), is(403));
                    assertThat(testContext, httpErrorException.getErrorResponse().getCode(), is(ErrorCode.SIGNATURE_DOES_NOT_MATCH));
                    async.complete();
                }
        );
    }

    void callAbortMultipartUpload(final TestContext testContext, String uploadId, Handler<Response<CommonResponseHeaders, Void>> handler) {

        s3Client.abortMultipartUpload(
                "bucket",
                "key",
                new AbortMultipartUploadRequest(uploadId),
                handler,
                testContext::fail
        );
    }

    void mockDeleteObject(Header... expectedHeaders) throws IOException {
        mock(
                Collections.emptyMap(),
                "DELETE",
                "/bucket/key",
                200,
                "<>".getBytes(),
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
                (commonResponseHeaders) -> {
                    assertThat(testContext, commonResponseHeaders, notNullValue());
                    async.complete();
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
                    assertThat(testContext, httpErrorException.getErrorResponse().getCode(), is(ErrorCode.SIGNATURE_DOES_NOT_MATCH));
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
                "<CopyObjectResult></CopyObjectResult>".getBytes(),
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
                (copyObjectResponse) -> {
                    assertThat(testContext, copyObjectResponse.getHeader(), notNullValue());
                    assertThat(testContext, copyObjectResponse.getData(), notNullValue());
                    async.complete();
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
                    assertThat(testContext, httpErrorException.getErrorResponse().getCode(), is(ErrorCode.SIGNATURE_DOES_NOT_MATCH));
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
                    assertThat(testContext, getBucketRespone.getHeader().getContentType(), is("application/xml;charset=UTF-8"));
                    assertThat(testContext, getBucketRespone.getData().getContentsList(), hasSize(5));
                    assertThat(testContext, getBucketRespone.getData().getName(), is("bucket"));

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
                    assertThat(testContext, httpErrorException.getErrorResponse().getCode(), is(ErrorCode.SIGNATURE_DOES_NOT_MATCH));
                    async.complete();
                }
        );
    }

    void mock(Map<String, List<String>> expectedQueryParams, String method, String path, Integer statusCode, byte[] responseBody, Header... expectedHeaders) throws IOException {
        mock(expectedQueryParams, method, path, statusCode, null, new BinaryBody(responseBody), Collections.emptyList(), expectedHeaders);
    }

    void mock(Map<String, List<String>> expectedQueryParams, String method, String path, Integer statusCode, byte[] responseBody, List<Header> responseHeaders, Header... expectedHeaders) throws IOException {
        mock(expectedQueryParams, method, path, statusCode, null, new BinaryBody(responseBody), responseHeaders, expectedHeaders);
    }

    void mock(Map<String, List<String>> expectedQueryParams, String method, String path, Integer statusCode, Body requestBody, Body responseBody, List<Header> responseHeaders, Header... expectedHeaders) throws IOException {
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
                        .withHeaders(responseHeaders)
                        .withHeader(Header.header("Content-Type", "application/xml;charset=UTF-8"))
                        .withBody(responseBody)
        );
    }
}
