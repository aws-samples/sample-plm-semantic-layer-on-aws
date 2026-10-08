// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import static org.assertj.core.api.Assertions.assertThat;

import atelier.query.cad.CadUrls;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class CadUrlsTest {
    // A placeholder bucket name: never created, and distinct from any real bucket, so it cannot be
    // squatted or confused with one. The test only asserts on the generated URL.
    private static final String BUCKET = "example-fixture-bucket-not-created";

    // The presigner reads the shared AWS config file for S3 settings even with
    // static credentials; a CI runner's file may not parse, so point at an empty one.
    private static Path emptyConfig;

    @BeforeAll
    static void isolateFromSharedAwsConfig() throws IOException {
        emptyConfig = Files.createTempFile("aws-config-empty", ".ini");
        System.setProperty("aws.configFile", emptyConfig.toString());
        System.setProperty("aws.sharedCredentialsFile", emptyConfig.toString());
    }

    @AfterAll
    static void restore() throws IOException {
        System.clearProperty("aws.configFile");
        System.clearProperty("aws.sharedCredentialsFile");
        Files.deleteIfExists(emptyConfig);
    }

    @Test
    void presignsAFifteenMinuteGetForTheBucketAndKey() {
        S3Presigner presigner = S3Presigner.builder().region(Region.EU_WEST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("EXAMPLEKEY0000000000", "example-secret-key-not-a-real-credential")))
                .build();

        String url = new CadUrls(BUCKET, presigner).presign("cad/ornithopter/fr-keel-beam.stp");

        assertThat(url).startsWith("https://" + BUCKET + ".s3.eu-west-1.amazonaws.com/cad/ornithopter/fr-keel-beam.stp?")
                .contains("X-Amz-Expires=900").contains("X-Amz-Signature=");
    }

    @Test
    void withoutABucketEveryUrlIsNull() {
        assertThat(new CadUrls(null, null).presign("cad/ornithopter/fr-keel-beam.stp")).isNull();
    }
}
