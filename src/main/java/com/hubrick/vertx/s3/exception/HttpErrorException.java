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
package com.hubrick.vertx.s3.exception;

import com.hubrick.vertx.s3.model.ErrorResponse;

/**
 * @author Emir Dizdarevic
 * @since 2.0.0
 */
public class HttpErrorException extends S3ClientException {

    private final Integer status;
    private final String statusMessage;
    private final ErrorResponse errorResponse;

    public HttpErrorException(Integer status, String statusMessage, ErrorResponse errorResponse, String message) {
        super(message);
        this.status = status;
        this.statusMessage = statusMessage;
        this.errorResponse = errorResponse;
    }

    public HttpErrorException(Integer status, String statusMessage, ErrorResponse errorResponse, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.statusMessage = statusMessage;
        this.errorResponse = errorResponse;
    }

    public Integer getStatus() {
        return status;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public ErrorResponse getErrorResponse() {
        return errorResponse;
    }
}
