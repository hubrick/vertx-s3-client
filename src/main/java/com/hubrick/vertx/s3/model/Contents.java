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

import com.hubrick.vertx.s3.model.adapter.InstantAdapter;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.time.Instant;

/**
 * @author Emir Dizdarevic
 * @since 2.0.0
 */
@XmlRootElement(name = "Contents")
@XmlAccessorType(XmlAccessType.FIELD)
public class Contents {

    @XmlElement(name = "Key", required = true)
    private String key;

    @XmlJavaTypeAdapter(InstantAdapter.class)
    @XmlElement(name = "LastModified", required = true)
    private Instant lastModified;

    @XmlElement(name = "ETag", required = true)
    private String eTag;

    @XmlElement(name = "Size", required = true)
    private Long size;

    @XmlElement(name = "StorageClass", required = true)
    private StorageClass storageClass;

    @XmlElement(name = "Owner")
    private Owner owner;

    public String getKey() {
        return key;
    }

    public Instant getLastModified() {
        return lastModified;
    }

    public String geteTag() {
        return eTag;
    }

    public Long getSize() {
        return size;
    }

    public StorageClass getStorageClass() {
        return storageClass;
    }

    public Owner getOwner() {
        return owner;
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
