// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.neptune;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Signature Version 4 as the specification states it, computed independently of the SDK signer under test: the
 * canonical request (method, path, the query string decoded then encoded per RFC 3986 and sorted, the lowercased
 * sorted headers, the signed header names, the hex SHA-256 of the body), the string to sign, and the HMAC chain over
 * the date, region, service and terminator with the secret key.
 */
final class SigV4Oracle {
    private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private SigV4Oracle() {
    }

    /** The {@code Authorization} header value for a request with exactly {@code headers} signed (the host and the date among them). */
    static String authorization(String method, URI uri, Map<String, String> headers, byte[] body, Instant at,
                                String accessKeyId, String secretKey, String region) {
        String amzDate = AMZ_DATE.format(at);
        String day = amzDate.substring(0, 8);
        TreeMap<String, String> canonicalHeaders = new TreeMap<>();
        headers.forEach((name, value) -> canonicalHeaders.put(name.toLowerCase(Locale.ROOT), value.trim()));
        String signedHeaders = String.join(";", canonicalHeaders.keySet());
        StringBuilder canonicalRequest = new StringBuilder(method).append('\n')
                .append(uri.getRawPath()).append('\n')
                .append(canonicalQuery(uri.getRawQuery())).append('\n');
        canonicalHeaders.forEach((name, value) -> canonicalRequest.append(name).append(':').append(value).append('\n'));
        canonicalRequest.append('\n').append(signedHeaders).append('\n').append(hex(sha256(body)));
        String scope = day + "/" + region + "/neptune-db/aws4_request";
        String stringToSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" + scope + "\n"
                + hex(sha256(canonicalRequest.toString().getBytes(StandardCharsets.UTF_8)));
        byte[] signingKey = hmac(hmac(hmac(hmac(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), day), region), "neptune-db"), "aws4_request");
        return "AWS4-HMAC-SHA256 Credential=" + accessKeyId + "/" + scope + ", SignedHeaders=" + signedHeaders
                + ", Signature=" + hex(hmac(signingKey, stringToSign));
    }

    private static String canonicalQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return "";
        }
        List<String> pairs = new ArrayList<>();
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            String name = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            pairs.add(encode(percentDecode(name)) + "=" + encode(percentDecode(value)));
        }
        return String.join("&", pairs.stream().sorted().toList());
    }

    /** Percent-decoding alone: a '+' in a query string is a literal plus. */
    private static String percentDecode(String s) {
        byte[] out = new byte[s.length()];
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '%' && i + 2 < s.length()) {
                out[n++] = (byte) Integer.parseInt(s.substring(i + 1, i + 3), 16);
                i += 2;
            } else {
                out[n++] = (byte) c;
            }
        }
        return new String(out, 0, n, StandardCharsets.UTF_8);
    }

    /** RFC 3986 encoding: every byte but the unreserved characters is percent-encoded, upper-case hex. */
    private static String encode(String s) {
        StringBuilder out = new StringBuilder();
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xff;
            boolean unreserved = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.' || c == '~';
            if (unreserved) {
                out.append((char) c);
            } else {
                out.append('%').append(String.format(Locale.ROOT, "%02X", c));
            }
        }
        return out.toString();
    }

    /** The hex SHA-256 of a body, the value of the {@code x-amz-content-sha256} header the SDK signer adds and signs. */
    static String sha256Hex(byte[] body) {
        return hex(sha256(body));
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] hmac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }
}
