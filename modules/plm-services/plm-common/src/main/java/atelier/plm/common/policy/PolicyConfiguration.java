// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.policy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * Loads the policy once at startup from the file named by {@code ATELIER_POLICY_FILE}; the image ships
 * {@code ontology/policy.json} at the default location, {@code /app/policy.json}.
 */
@Configuration
public class PolicyConfiguration {

    @Bean
    public Policy policy(@Value("${ATELIER_POLICY_FILE:/app/policy.json}") String file) {
        return Policy.load(Path.of(file));
    }
}
