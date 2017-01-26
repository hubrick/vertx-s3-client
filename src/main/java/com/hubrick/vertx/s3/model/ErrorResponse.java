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

import javax.xml.bind.Element;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Emir Dizdarevic
 * @since 2.0.0
 */
@XmlRootElement(name = "Error")
@XmlAccessorType(XmlAccessType.FIELD)
public class ErrorResponse {

    @XmlElement(name = "Code", required = true)
    private String code;

    @XmlElement(name = "Message", required = true)
    private String message;

    @XmlElement(name = "Resource", required = true)
    private String resource;

    @XmlElement(name = "RequestId")
    private String requestId;

    @XmlElement(name = "HostId")
    private String hostId;

    @XmlElement(name = "AWSAccessKeyId")
    private String awsAccessKeyId;

    @XmlAnyElement
    private List<Element> rest = new LinkedList<>();

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getResource() {
        return resource;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getHostId() {
        return hostId;
    }

    public String getAwsAccessKeyId() {
        return awsAccessKeyId;
    }

    public List<Element> getRest() {
        return rest;
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
