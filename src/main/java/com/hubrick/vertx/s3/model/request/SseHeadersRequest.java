package com.hubrick.vertx.s3.model.request;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * @author gruboter
 * @since 3.3.2
 */
public class SseHeadersRequest<T extends SseHeadersRequest> {

    private String amzServerSideEncryptionCustomerAlgorithm = "AES256";
    private String amzServerSideEncryptionCustomerKey;
    private String amzServerSideEncryptionCustomerKeyMD5;

    public T withAmzServerSideEncryptionCustomerAlgorithm(String amzServerSideEncryptionCustomerAlgorithm) {
        this.amzServerSideEncryptionCustomerAlgorithm = amzServerSideEncryptionCustomerAlgorithm;
        return (T) this;
    }

    public T withAmzServerSideEncryptionCustomerKey(String amzServerSideEncryptionCustomerKey) {
        this.amzServerSideEncryptionCustomerKey = amzServerSideEncryptionCustomerKey;
        return (T) this;
    }

    public T withAmzServerSideEncryptionCustomerKeyMD5(String amzServerSideEncryptionCustomerKeyMD5) {
        this.amzServerSideEncryptionCustomerKeyMD5 = amzServerSideEncryptionCustomerKeyMD5;
        return (T) this;
    }

    public String getAmzServerSideEncryptionCustomerAlgorithm() {
        return amzServerSideEncryptionCustomerAlgorithm;
    }

    public String getAmzServerSideEncryptionCustomerKey() {
        return amzServerSideEncryptionCustomerKey;
    }

    public String getAmzServerSideEncryptionCustomerKeyMD5() {
        return amzServerSideEncryptionCustomerKeyMD5;
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
