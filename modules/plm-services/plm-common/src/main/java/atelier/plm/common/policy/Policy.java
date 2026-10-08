// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.policy;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * The export-control policy of {@code ontology/policy.json}: the viewer profiles and the
 * releasability tokens each may see. A request names its profile in the {@value #HEADER} header;
 * a missing or unknown value resolves to the {@value #UNKNOWN} profile.
 */
public record Policy(Map<String, Profile> profiles) {

    public static final String HEADER = "x-atelier-profile";
    public static final String UNKNOWN = "unknown";

    public Policy {
        profiles = Map.copyOf(profiles);
        if (!profiles.containsKey(UNKNOWN)) {
            throw new IllegalArgumentException("policy has no '" + UNKNOWN + "' profile");
        }
        profiles.forEach((name, profile) -> {
            if (profile.releasable() == null || profile.releasable().isEmpty()) {
                throw new IllegalArgumentException("profile '" + name + "' has no releasable tokens");
            }
        });
    }

    public static Policy load(Path file) {
        try {
            return new ObjectMapper().readValue(Files.readAllBytes(file), Policy.class);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read policy file " + file, e);
        }
    }

    /** The clearance of a {@value #HEADER} header value; null or an unlisted name is {@value #UNKNOWN}. */
    public Clearance clearance(String profile) {
        String name = profile != null && profiles.containsKey(profile) ? profile : UNKNOWN;
        return new Clearance(name, profiles.get(name).releasable());
    }
}
