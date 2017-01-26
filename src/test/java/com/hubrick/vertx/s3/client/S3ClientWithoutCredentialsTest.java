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

import java.io.IOException;

/**
 * @author marcus
 * @since 1.0.0
 */
public class S3ClientWithoutCredentialsTest extends AbstractS3ClientTest {

    @Override
    protected void augmentClientOptions(final S3ClientOptions clientOptions) {
    }

    @Test
    public void testGet(TestContext testContext) throws IOException {
        mockGet();

        verifyGet(testContext);
    }

    @Test
    public void testPut(TestContext testContext) throws IOException {
        mockPut();

        verifyPut(testContext);
    }

    @Test
    public void testDelete(TestContext testContext) throws IOException {
        mockDelete();

        verifyDelete(testContext);
    }

    @Test
    public void testCopy(TestContext testContext) throws IOException {
        mockCopy();

        verifyCopy(testContext);

    }
}
