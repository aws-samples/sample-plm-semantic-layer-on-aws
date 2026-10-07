// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.cad;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * Presigned S3 GET URLs for the CAD files of parts the viewer may see, valid 15 minutes. The
 * bucket is {@code CAD_BUCKET}; the key is the part's {@code atelier:cadFile}. Credentials come from
 * the default chain (the ECS task role in production) and are resolved once at startup, then
 * refreshed in the background, so presigning is pure signing and never calls the network while
 * a request is served. Without {@code CAD_BUCKET} (local runs) every URL is null.
 */
@Component
public class CadUrls {
    static final Duration VALIDITY = Duration.ofMinutes(15);

    private static final Logger log = LoggerFactory.getLogger(CadUrls.class);

    private final String bucket;
    private final S3Presigner presigner;

    @Autowired
    public CadUrls(@Value("${CAD_BUCKET:}") String bucket) {
        this(bucket.isBlank() ? null : bucket, bucket.isBlank() ? null : presigner());
        if (this.presigner == null) {
            log.info("CAD_BUCKET is not set: parts carry cadUrl null");
        }
    }

    /** @param presigner null when CAD files are not served. */
    public CadUrls(String bucket, S3Presigner presigner) {
        this.bucket = bucket;
        this.presigner = presigner;
    }

    /** Presigned GET URL for a CAD key, or null when CAD files are not served. */
    public String presign(String cadFile) {
        if (presigner == null) return null;
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(VALIDITY)
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(cadFile).build())
                .build()).url().toExternalForm();
    }

    private static S3Presigner presigner() {
        AwsCredentialsProvider credentials = DefaultCredentialsProvider.builder()
                .asyncCredentialUpdateEnabled(true).build();
        credentials.resolveCredentials();
        return S3Presigner.builder().credentialsProvider(credentials).build();
    }
}
