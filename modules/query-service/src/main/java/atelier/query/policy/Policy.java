// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.policy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The export-control policy of ontology/policy.json: viewer profiles and the releasability tokens
 * each may see. The browser names its profile in the {@code x-atelier-profile} header; a missing or
 * unknown name is the {@code unknown} profile. A part whose tag is missing from the Atelier core
 * database is visible to the {@code export-officer} profile only.
 */
@Component
public class Policy {
    public static final String UNKNOWN = "unknown";
    public static final String EXPORT_OFFICER = "export-officer";

    /** Releasability tokens are single upper-case words (ALL, EU, FR, ...), so they are safe inside SPARQL literals. */
    private static final Pattern TOKEN = Pattern.compile("[A-Z]+");
    private static final Logger log = LoggerFactory.getLogger(Policy.class);

    /** One viewer profile: its name, display label and the releasability tokens it may see. */
    public record Profile(String name, String label, List<String> releasable) {
        /** The FILTER pushed into the core arm, on the part's {@code atelier:releasableTo} bound as {@code ?rel}. */
        public String filter() {
            return "FILTER (?rel IN (" + releasable.stream().map(r -> '"' + r + '"').collect(Collectors.joining(", "))
                    + "))";
        }

        /** Whether a part with no tag row in the Atelier core database is visible to this profile. */
        public boolean seesUntagged() {
            return EXPORT_OFFICER.equals(name);
        }
    }

    /** Actor names are echoed as given, trimmed and cut to this length. */
    static final int ACTOR_LENGTH = 64;

    private final Map<String, Profile> profiles;

    @Autowired
    public Policy(@Value("${atelier.policy.file}") String file) {
        this(Path.of(file));
    }

    public Policy(Path file) {
        try {
            JsonNode root = new ObjectMapper().readTree(file.toFile());
            profiles = new LinkedHashMap<>();
            root.path("profiles").fields().forEachRemaining(e -> {
                List<String> releasable = new ArrayList<>();
                e.getValue().path("releasable").forEach(t -> releasable.add(token(t.asText())));
                profiles.put(e.getKey(), new Profile(e.getKey(), e.getValue().path("label").asText(), List.copyOf(releasable)));
            });
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot read the export-control policy " + file, ex);
        }
        if (!profiles.containsKey(UNKNOWN)) {
            throw new IllegalStateException("The export-control policy " + file + " has no '" + UNKNOWN + "' profile");
        }
        log.info("Export-control policy {} loaded: profiles {}", file, profiles.keySet());
    }

    /** The profile named by the {@code x-atelier-profile} header value; {@code unknown} when absent or not in the policy. */
    public Profile profile(String header) {
        return profiles.getOrDefault(header == null ? UNKNOWN : header, profiles.get(UNKNOWN));
    }

    /** The caller of a request: its profile, and the actor of the {@code x-atelier-actor} header ({@code user} when absent). */
    public Caller caller(String profileHeader, String actorHeader) {
        String actor = actorHeader == null || actorHeader.isBlank() ? Caller.USER : actorHeader.strip();
        return new Caller(profile(profileHeader), actor.length() > ACTOR_LENGTH ? actor.substring(0, ACTOR_LENGTH) : actor);
    }

    /** Every profile, in policy order. */
    public List<Profile> profiles() {
        return List.copyOf(profiles.values());
    }

    private static String token(String value) {
        if (!TOKEN.matcher(value).matches()) {
            throw new IllegalStateException("Releasability token '" + value + "' is not an upper-case word");
        }
        return value;
    }
}
