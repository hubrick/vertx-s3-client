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
package com.hubrick.vertx.s3.model;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * @author Emir Dizdarevic
 * @since 2.0.0
 */
public class CopyObjectRequest {

    private String amzMetadataDirective;
    private String amzCopySourceIfMatch;
    private String amzCopySourceIfNoneMatch;
    private String amzCopySourceIfUnmodifiedSince;
    private String amzCopySourceIfModifiedSince;
    private String amzStorageClass;
    private String amzTaggingDirective;
    private String amzWebsiteRedirectLocation;

    public CopyObjectRequest withAmzMetadataDirective(String amzMetadataDirective) {
        this.amzMetadataDirective = amzMetadataDirective;
        return this;
    }

    public CopyObjectRequest withAmzCopySourceIfMatch(String amzCopySourceIfMatch) {
        this.amzCopySourceIfMatch = amzCopySourceIfMatch;
        return this;
    }

    public CopyObjectRequest withAmzCopySourceIfNoneMatch(String amzCopySourceIfNoneMatch) {
        this.amzCopySourceIfNoneMatch = amzCopySourceIfNoneMatch;
        return this;
    }

    public CopyObjectRequest withAmzCopySourceIfUnmodifiedSince(String amzCopySourceIfUnmodifiedSince) {
        this.amzCopySourceIfUnmodifiedSince = amzCopySourceIfUnmodifiedSince;
        return this;
    }

    public CopyObjectRequest withAmzCopySourceIfModifiedSince(String amzCopySourceIfModifiedSince) {
        this.amzCopySourceIfModifiedSince = amzCopySourceIfModifiedSince;
        return this;
    }

    public CopyObjectRequest withAmzStorageClass(String amzStorageClass) {
        this.amzStorageClass = amzStorageClass;
        return this;
    }

    public CopyObjectRequest withAmzTaggingDirective(String amzTaggingDirective) {
        this.amzTaggingDirective = amzTaggingDirective;
        return this;
    }

    public CopyObjectRequest withAmzWebsiteRedirectLocation(String amzWebsiteRedirectLocation) {
        this.amzWebsiteRedirectLocation = amzWebsiteRedirectLocation;
        return this;
    }

    public String getAmzMetadataDirective() {
        return amzMetadataDirective;
    }

    public String getAmzCopySourceIfMatch() {
        return amzCopySourceIfMatch;
    }

    public String getAmzCopySourceIfNoneMatch() {
        return amzCopySourceIfNoneMatch;
    }

    public String getAmzCopySourceIfUnmodifiedSince() {
        return amzCopySourceIfUnmodifiedSince;
    }

    public String getAmzCopySourceIfModifiedSince() {
        return amzCopySourceIfModifiedSince;
    }

    public String getAmzStorageClass() {
        return amzStorageClass;
    }

    public String getAmzTaggingDirective() {
        return amzTaggingDirective;
    }

    public String getAmzWebsiteRedirectLocation() {
        return amzWebsiteRedirectLocation;
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
