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
package com.hubrick.vertx.s3.signature;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import com.google.common.net.PercentEscaper;
import org.apache.commons.collections4.KeyValue;
import org.apache.commons.collections4.keyvalue.DefaultKeyValue;
import org.apache.commons.lang3.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author marcus
 * @since 1.0.0
 */
public class AWS4SignatureBuilder {

    public final static String UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD";

    private final static Pattern QUERY_STRING_MATCHING_PATTERN = Pattern.compile("([^=]+)=?([^&]*)&?");
    private final static String DEFAULT_ALGORITHM = "AWS4-HMAC-SHA256";
    private final static DateTimeFormatter SIGNATURE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX");
    private final static DateTimeFormatter CREDENTIAL_SCOPE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final static String CREDENTIAL_SCOPE_TERMINATION_STRING = "aws4_request";
    private final static String ESCAPE_SAFE_CHARACTERS = "-_.*~";

    private final static PercentEscaper QUERY_PARAMETER_ESCAPER = new PercentEscaper(ESCAPE_SAFE_CHARACTERS, true);
    private final static PercentEscaper PATH_PARTS_ESCAPER = new PercentEscaper(ESCAPE_SAFE_CHARACTERS + "/", true);

    private final ZonedDateTime date;
    private final String region;
    private final String service;

    private String httpRequestMethod = StringUtils.EMPTY;
    private String canonicalUri = StringUtils.EMPTY;
    private String canonicalQueryString = StringUtils.EMPTY;
    private Multimap<String, String> canonicalHeaders = Multimaps.newListMultimap(Maps.<String, Collection<String>>newTreeMap(), LinkedList::new);
    private String payloadHash;
    private byte[] signingKey;

    private AWS4SignatureBuilder(final ZonedDateTime date, final String region, final String service) {
        Preconditions.checkArgument(date != null, "date must be set");
        Preconditions.checkArgument(StringUtils.isNotBlank(region), "region must be set");
        Preconditions.checkArgument(StringUtils.isNotBlank(service), "service must be set");

        this.date = date;
        this.region = region;
        this.service = service;

        payloadHash = UNSIGNED_PAYLOAD;
    }

    public static AWS4SignatureBuilder builder(final Date date, final String region, final String service) {
        return builder(ZonedDateTime.ofInstant(date.toInstant(),
                ZoneId.systemDefault()), region, service);
    }

    public static AWS4SignatureBuilder builder(final ZonedDateTime date, final String region, final String service) {
        return new AWS4SignatureBuilder(date, region, service);
    }

    private static byte[] hmacSha256(final byte[] key, final String value) {
        try {
            final String algorithm = "HmacSHA256";
            final Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(key, algorithm));
            return mac.doFinal(utf8Bytes(value));
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private static byte[] utf8Bytes(final String s) {
        Preconditions.checkArgument(s != null, "input string must not be null");
        return s.getBytes(Charsets.UTF_8);
    }

    public AWS4SignatureBuilder httpRequestMethod(final CharSequence httpRequestMethod) {
        Preconditions.checkArgument(StringUtils.isNotBlank(httpRequestMethod), "httpRequestMethod must not be blank");

        this.httpRequestMethod = httpRequestMethod.toString();
        return this;
    }

    public AWS4SignatureBuilder canonicalUri(final CharSequence canonicalUri) {
        Preconditions.checkArgument(StringUtils.isNotBlank(canonicalUri), "canonicalUri must not be blank");

        this.canonicalUri = PATH_PARTS_ESCAPER.escape(canonicalUri.toString());
        return this;
    }

    public AWS4SignatureBuilder canonicalQueryString(final String canonicalQueryString) {
        final String defaultString = StringUtils.defaultString(canonicalQueryString);

        // this way of parsing the query string is the only way to satisfy the
        // test-suite, therefore we do it with a matcher:
        final Matcher matcher = QUERY_STRING_MATCHING_PATTERN.matcher(defaultString);
        final List<KeyValue<String, String>> parameters = Lists.newLinkedList();

        while (matcher.find()) {
            final String key = StringUtils.trim(matcher.group(1));
            final String value = StringUtils.trim(matcher.group(2));
            parameters.add(new DefaultKeyValue<>(key, value));
        }

        this.canonicalQueryString = parameters.stream()
                .sorted(Comparator.comparing(KeyValue::getKey))
                .map(kv -> queryParameterEscape(kv.getKey()) + "=" + queryParameterEscape(kv.getValue()))
                .collect(Collectors.joining("&"));

        return this;
    }

    private String queryParameterEscape(final String s) {
        return QUERY_PARAMETER_ESCAPER.escape(s);
    }

    public AWS4SignatureBuilder header(final String headerName, final String headerValue) {
        Preconditions.checkArgument(StringUtils.isNotBlank(headerName), "headerName must not be blank");

        this.canonicalHeaders.put(StringUtils.lowerCase(headerName), StringUtils.trimToEmpty(headerValue).replaceAll(" +", " "));
        return this;
    }

    public AWS4SignatureBuilder payload(final byte[] payload) {
        Preconditions.checkNotNull(payload, "payload must not be null");

        this.payloadHash = Hashing.sha256().hashBytes(payload).toString();
        return this;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public AWS4SignatureBuilder payload(final String payload) {
        Preconditions.checkNotNull(payload, "payload must not be null");

        return this.payload(utf8Bytes(payload));
    }

    public AWS4SignatureBuilder awsSecretKey(final String awsSecretKey) {
        Preconditions.checkArgument(StringUtils.isNotBlank(awsSecretKey), "secret key must not be blank");

        Preconditions.checkState(date != null, "date must be set to create the signing key");
        Preconditions.checkState(StringUtils.isNotBlank(region), "region must be set to create the signing key");
        Preconditions.checkState(StringUtils.isNotBlank(service), "service must be set to create the signing key");

        final byte[] kDate = hmacSha256(utf8Bytes("AWS4" + awsSecretKey), CREDENTIAL_SCOPE_DATE.format(date));
        final byte[] kRegion = hmacSha256(kDate, region);
        final byte[] kService = hmacSha256(kRegion, service);
        final byte[] signing = hmacSha256(kService, CREDENTIAL_SCOPE_TERMINATION_STRING);

        this.signingKey = signing;

        return this;
    }

    private String makeCanonicalHeaderString() {
        final StringBuilder builder = new StringBuilder();
        for (final Map.Entry<String, Collection<String>> entry : canonicalHeaders.asMap().entrySet()) {
            builder.append(entry.getKey()).append(':').append(Joiner.on(',').join(entry.getValue())).append('\n');
        }

        return builder.toString();
    }

    @VisibleForTesting
    public String makeCanonicalRequest() {
        Preconditions.checkState(StringUtils.isNotBlank(httpRequestMethod), "Request method must not be blank");
        Preconditions.checkState(StringUtils.isNotBlank(canonicalUri), "Canonical URI must not be blank");
        Preconditions.checkState(canonicalQueryString != null, "query string must not be null");
        Preconditions.checkState(payloadHash != null, "hashed payload must not be null");


        final StringBuilder builder = new StringBuilder();
        builder.append(httpRequestMethod).append('\n');
        builder.append(canonicalUri).append('\n');
        builder.append(canonicalQueryString).append('\n');
        builder.append(makeCanonicalHeaderString()).append('\n');
        builder.append(makeSignedHeadersString()).append('\n');
        builder.append(payloadHash);

        return builder.toString();
    }

    private String makeSignedHeadersString() {
        return Joiner.on(';').join(canonicalHeaders.keySet());
    }

    private String makeCanonicalRequestContentHash() {
        if (!canonicalHeaders.containsKey("host")) {
            throw new AWS4SignatureException("Headers must include the host header");
        }

        final String signatureBase = makeCanonicalRequest();

        return Hashing.sha256().hashString(signatureBase, Charsets.UTF_8).toString();
    }

    @VisibleForTesting
    String makeSignatureString() {
        Preconditions.checkNotNull(date, "Date must be set to create a valid signature");

        final StringBuilder builder = new StringBuilder();
        builder.append(DEFAULT_ALGORITHM).append('\n');
        builder.append(makeSignatureFormattedDate()).append('\n');
        builder.append(makeCredentialScopeValueString()).append('\n');
        builder.append(makeCanonicalRequestContentHash());

        return builder.toString();
    }

    public String makeSignatureFormattedDate() {
        return SIGNATURE_FORMAT.format(date);
    }

    public String buildAuthorizationHeaderValue(final String awsAccessKey) {
        Preconditions.checkArgument(StringUtils.isNotBlank(awsAccessKey), "awsAccessKey must be set");

        final StringBuilder builder = new StringBuilder();
        builder.append(DEFAULT_ALGORITHM).append(' ');
        builder.append("Credential=").append(awsAccessKey).append('/').append(makeCredentialScopeValueString()).append(", ");
        builder.append("SignedHeaders=").append(makeSignedHeadersString()).append(", ");
        builder.append("Signature=").append(buildSignature());

        return builder.toString();
    }

    public String buildSignature() {
        Preconditions.checkNotNull(signingKey, "SigningKey must be set to create a valid signature, awsSecretKey not set");

        return StringUtils.lowerCase(BaseEncoding.base16().encode(hmacSha256(signingKey, makeSignatureString())));
    }

    private String makeCredentialScopeValueString() {
        Preconditions.checkNotNull(date, "Date must be set to create a credential scope value");
        Preconditions.checkState(StringUtils.isNoneBlank(region), "Region must be set to create a credential scope value");
        Preconditions.checkState(StringUtils.isNoneBlank(service), "Service must be set to create a credential scope value");

        return Joiner.on('/').join(CREDENTIAL_SCOPE_DATE.format(date), region, service, CREDENTIAL_SCOPE_TERMINATION_STRING);
    }

}
