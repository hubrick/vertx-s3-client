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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Emir Dizdarevic
 * @since 2.0.0
 */
@XmlRootElement(name = "ListBucketResult")
@XmlAccessorType(XmlAccessType.FIELD)
public class GetBucketRespone {

    @XmlElement(name = "Name", required = true)
    private String name;

    @XmlElement(name = "Prefix", required = true)
    private String prefix;

    @XmlElement(name = "Marker", required = true)
    private String marker;

    @XmlElement(name = "MaxKeys", required = true)
    private Integer maxKeys;

    @XmlElement(name = "IsTruncated", required = true)
    private Boolean isTruncated;

    @XmlElement(name = "Delimiter", required = true)
    private String delimiter;

    @XmlElement(name = "Encoding-Type", required = true)
    private String encodingType;

    @XmlElement(name = "ContinuationToken")
    private String continuationToken;

    @XmlElement(name = "NextContinuationToken")
    private String nextContinuationToken;

    @XmlElement(name = "StartAfter")
    private String startAfter;

    @XmlElement(name = "Contents", type = Contents.class)
    private List<Contents> contentsList = new LinkedList<>();

    @XmlElement(name = "CommonPrefixes", type = CommonPrefixes.class)
    private List<CommonPrefixes> commonPrefixes = new LinkedList<>();

    public String getName() {
        return name;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getMarker() {
        return marker;
    }

    public Integer getMaxKeys() {
        return maxKeys;
    }

    public Boolean getTruncated() {
        return isTruncated;
    }

    public String getDelimiter() {
        return delimiter;
    }

    public String getEncodingType() {
        return encodingType;
    }

    public String getContinuationToken() {
        return continuationToken;
    }

    public String getNextContinuationToken() {
        return nextContinuationToken;
    }

    public String getStartAfter() {
        return startAfter;
    }

    public List<Contents> getContentsList() {
        return contentsList;
    }

    public List<CommonPrefixes> getCommonPrefixes() {
        return commonPrefixes;
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
