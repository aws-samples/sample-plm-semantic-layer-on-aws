// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.policy;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PolicyTest {

    /** Module directory: surefire's {@code basedir}, or the working directory when run from an IDE. */
    static final Path MODULE = Path.of(System.getProperty("basedir", ".")).toAbsolutePath().normalize();
    static final Path TEST_COPY = MODULE.resolve("src/test/resources/policy.json");
    static final Path SHIPPED = MODULE.resolve("../../../ontology/policy.json").normalize();

    @Test
    void testCopyIsTheShippedPolicyByteForByte() throws IOException {
        assertThat(SHIPPED).exists();
        assertThat(Files.readAllBytes(TEST_COPY)).isEqualTo(Files.readAllBytes(SHIPPED));
    }

    @Test
    void knownProfileGetsItsReleasableTokens() {
        Policy policy = Policy.load(TEST_COPY);
        assertThat(policy.clearance("de-engineer")).isEqualTo(new Clearance("de-engineer", List.of("ALL", "EU", "DE")));
        assertThat(policy.clearance("export-officer").releasable()).containsExactly("ALL", "EU", "FR", "DE", "UK", "ES", "LICENSED");
    }

    @Test
    void missingOrUnlistedProfileIsUnknown() {
        Policy policy = Policy.load(TEST_COPY);
        Clearance unknown = new Clearance("unknown", List.of("ALL"));
        assertThat(policy.clearance(null)).isEqualTo(unknown);
        assertThat(policy.clearance("")).isEqualTo(unknown);
        assertThat(policy.clearance("nobody")).isEqualTo(unknown);
        assertThat(policy.clearance("DE-ENGINEER")).isEqualTo(unknown);
    }

    @Test
    void rejectsAPolicyWithoutUnknownOrWithNothingReleasable() {
        Profile some = new Profile("x", null, List.of("ALL"));
        assertThatIllegalArgumentException().isThrownBy(() -> new Policy(Map.of("fr-engineer", some)));
        assertThatIllegalArgumentException().isThrownBy(() -> new Policy(Map.of(
                "unknown", some, "nobody", new Profile("x", null, List.of()))));
    }
}
