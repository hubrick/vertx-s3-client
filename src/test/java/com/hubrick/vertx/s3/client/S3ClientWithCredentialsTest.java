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

import io.vertx.ext.unit.TestContext;
import org.junit.Test;
import org.mockserver.model.Header;

import java.io.IOException;

/**
 * @author marcus
 * @since 1.0.0
 */
public class S3ClientWithCredentialsTest extends AbstractS3ClientTest {

    @Override
    protected void augmentClientOptions(final S3ClientOptions clientOptions) {
        addCredentials(clientOptions);
    }

    @Test
    public void testGet(TestContext testContext) throws IOException {
        mockGet(
                Header.header("X-Amz-Date", "20161110T130214Z"),
                Header.header("X-Amz-Content-Sha256", "UNSIGNED-PAYLOAD"),
                Header.header("Authorization", "AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20161110/us-east-1/service/aws4_request, SignedHeaders=host;x-amz-date, Signature=77f53b6d1eb26273da59491e98ea10989a006a57620d7351be44b596a348d699")
        );

        verifyGet(testContext);

    }

    @Test
    public void testPut(TestContext testContext) throws IOException {
        mockPut(
                Header.header("X-Amz-Date", "20161110T130214Z"),
                Header.header("X-Amz-Content-Sha256", "UNSIGNED-PAYLOAD"),
                Header.header("Authorization", "AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20161110/us-east-1/service/aws4_request, SignedHeaders=host;x-amz-date, Signature=3a058beb9174cffbb616894f55a8c5999c376a2ba0851513a635de3952385013")
        );

        verifyPut(testContext);
    }

    @Test
    public void testDelete(TestContext testContext) throws IOException {
        mockDelete(
                Header.header("X-Amz-Date", "20161110T130214Z"),
                Header.header("X-Amz-Content-Sha256", "UNSIGNED-PAYLOAD"),
                Header.header("Authorization", "AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20161110/us-east-1/service/aws4_request, SignedHeaders=host;x-amz-date, Signature=7a119f6c4ce49d2a0eaffd7511d203523b1e2435837f5916d0bd8c08123d4eaa")
        );

        verifyDelete(testContext);
    }

    @Test
    public void testCopy(TestContext testContext) throws IOException {
        mockCopy(
                Header.header("X-Amz-Date", "20161110T130214Z"),
                Header.header("X-Amz-Content-Sha256", "UNSIGNED-PAYLOAD"),
                Header.header("Authorization", "AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20161110/us-east-1/service/aws4_request, SignedHeaders=host;x-amz-copy-source;x-amz-date, Signature=f4fb57b90ec37c3bb9b437cff5f3fd164d81cbe2ff13f9223b20d3c899f50036")
        );

        verifyCopy(testContext);
    }
}
