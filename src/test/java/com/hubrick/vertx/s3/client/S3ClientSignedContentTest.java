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

import com.google.common.collect.ImmutableMap;
import io.vertx.ext.unit.TestContext;
import org.junit.Test;
import org.mockserver.model.Header;

import java.io.IOException;
import java.util.Collections;

/**
 * @author marcus
 * @since 1.0.0
 */
public class S3ClientSignedContentTest extends AbstractS3ClientTest {

    @Override
    protected void augmentClientOptions(final S3ClientOptions clientOptions) {
        addCredentials(clientOptions);

        clientOptions.setSignPayload(true);
    }

    @Test
    public void testGetObject(TestContext testContext) throws IOException {
        mockGetObject(
                Header.header("X-Amz-Date", "20161110T130214Z"),
                Header.header("X-Amz-Content-Sha256", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
                Header.header("Authorization", "AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20161110/us-east-1/service/aws4_request, SignedHeaders=host;x-amz-date, Signature=1312c94db8c2ad1d2d56d4c6bb16cdb7c0485b100b2baf2d8aabbb3fec0075fb")
        );

        verifyGetObject(testContext);
    }

    @Test
    public void testGetObjectError(TestContext testContext) throws IOException {
        mockGetObjectErrorResponse(
                Header.header("X-Amz-Date", "20161110T130214Z"),
                Header.header("X-Amz-Content-Sha256", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
                Header.header("Authorization", "AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20161110/us-east-1/service/aws4_request, SignedHeaders=host;x-amz-date, Signature=1312c94db8c2ad1d2d56d4c6bb16cdb7c0485b100b2baf2d8aabbb3fec0075fb")
        );

        verifyGetObjectErrorResponse(testContext);
    }

    @Test
    public void testPutObject(TestContext testContext) throws IOException {
        mockPutObject(
                Header.header("X-Amz-Date", "20161110T130214Z"),
                Header.header("X-Amz-Content-Sha256", "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"),
                Header.header("Authorization", "AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20161110/us-east-1/service/aws4_request, SignedHeaders=host;x-amz-date, Signature=ec91365b21b715a7f981a89f61e2ef2c0a73cce5998b4272272f68a82d94e055")
        );

        verifyPutObject(testContext);
    }

    @Test
    public void testPutObjectError(TestContext testContext) throws IOException {
        mockPutObjectErrorResponse(
                Header.header("X-Amz-Date", "20161110T130214Z"),
                Header.header("X-Amz-Content-Sha256", "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"),
                Header.header("Authorization", "AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20161110/us-east-1/service/aws4_request, SignedHeaders=host;x-amz-date, Signature=ec91365b21b715a7f981a89f61e2ef2c0a73cce5998b4272272f68a82d94e055")
        );

        verifyPutObjectErrorResponse(testContext);
    }

    @Test
    public void testDeleteObject(TestContext testContext) throws IOException {
        mockDeleteObject(
                Header.header("X-Amz-Date", "20161110T130214Z"),
                Header.header("X-Amz-Content-Sha256", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
                Header.header("Authorization", "AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20161110/us-east-1/service/aws4_request, SignedHeaders=host;x-amz-date, Signature=bc61c03c7fbd958aa69920984856ca9ec5454c6714524b39fa637e1837e96355")
        );

        verifyDeleteObject(testContext);
    }

    @Test
    public void testDeleteObjectError(TestContext testContext) throws IOException {
        mockDeleteObjectErrorResponse(
                Header.header("X-Amz-Date", "20161110T130214Z"),
                Header.header("X-Amz-Content-Sha256", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
                Header.header("Authorization", "AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20161110/us-east-1/service/aws4_request, SignedHeaders=host;x-amz-date, Signature=bc61c03c7fbd958aa69920984856ca9ec5454c6714524b39fa637e1837e96355")
        );

        verifyDeleteObjectErrorResponse(testContext);
    }


    @Test
    public void testCopyObject(TestContext testContext) throws IOException {
        mockCopyObject(
                Header.header("X-Amz-Date", "20161110T130214Z"),
                Header.header("X-Amz-Content-Sha256", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
                Header.header("Authorization", "AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20161110/us-east-1/service/aws4_request, SignedHeaders=host;x-amz-copy-source;x-amz-date, Signature=f852af4f877c4f1522af892d11a4bd9a6a42327a67877916a8d57c3339f46d0b")
        );

        verifyCopyObject(testContext);
    }

    @Test
    public void testCopyObjectError(TestContext testContext) throws IOException {
        mockCopyObjectErrorResponse(
                Header.header("X-Amz-Date", "20161110T130214Z"),
                Header.header("X-Amz-Content-Sha256", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
                Header.header("Authorization", "AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20161110/us-east-1/service/aws4_request, SignedHeaders=host;x-amz-copy-source;x-amz-date, Signature=f852af4f877c4f1522af892d11a4bd9a6a42327a67877916a8d57c3339f46d0b")
        );

        verifyCopyObjectErrorResponse(testContext);
    }

    @Test
    public void testGetBucket(TestContext testContext) throws IOException {
        mockGetBucket(
                ImmutableMap.of(
                        "list-type", Collections.singletonList("2"),
                        "continuation-token", Collections.singletonList("14HF6Dfbr92F1EYlZIrMwxPYKQl5lD/mbwiw5+Nlrn1lYIZX3YGzo16Dgz+dxbxFeNGmLsnzwnbbuQM0CMl0krVwh8TBj8nCmNtq/iQCK6gzln8z3U4C71Mh2HyEMHcMgrZGR/akosVql7/AIctj6rA==")
                ),
                Header.header("X-Amz-Date", "20161110T130214Z"),
                Header.header("X-Amz-Content-Sha256", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
                Header.header("Authorization", "AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20161110/us-east-1/service/aws4_request, SignedHeaders=host;x-amz-date, Signature=62c51118953e03169f4d723c3ffc4fdcc3a30831b898f962dffd423264dae75b")
        );

        verifyGetBucket(testContext);
    }

    @Test
    public void testGetBucketError(TestContext testContext) throws IOException {
        mockGetBucketErrorResponse(
                ImmutableMap.of(
                        "list-type", Collections.singletonList("2"),
                        "continuation-token", Collections.singletonList("14HF6Dfbr92F1EYlZIrMwxPYKQl5lD/mbwiw5+Nlrn1lYIZX3YGzo16Dgz+dxbxFeNGmLsnzwnbbuQM0CMl0krVwh8TBj8nCmNtq/iQCK6gzln8z3U4C71Mh2HyEMHcMgrZGR/akosVql7/AIctj6rA==")
                ),
                Header.header("X-Amz-Date", "20161110T130214Z"),
                Header.header("X-Amz-Content-Sha256", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
                Header.header("Authorization", "AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20161110/us-east-1/service/aws4_request, SignedHeaders=host;x-amz-date, Signature=62c51118953e03169f4d723c3ffc4fdcc3a30831b898f962dffd423264dae75b")
        );

        verifyGetBucketErrorResponse(testContext);
    }
}
