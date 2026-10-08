// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.core.dto;

import atelier.plm.common.demo.DemoRejectedException;
import atelier.plm.core.graph.Graphs;
import org.springframework.http.HttpStatus;

import java.util.regex.Pattern;

/**
 * Body of {@code POST /core/links}: the two feature IRIs to mate, each
 * {@code https://example.com/atelier/{fr|de|uk|es}/{plug|fastener|coupling}/{id}} with an id safe inside a
 * Turtle IRI, naming two different features.
 */
public record LinkRequest(String from, String to) {

    static final String SHAPE = Graphs.DATA + "{fr|de|uk|es}/{plug|fastener|coupling}/{id}";
    static final Pattern FEATURE = Pattern.compile("^" + Pattern.quote(Graphs.DATA) + "(fr|de|uk|es)/(plug|fastener|coupling)/[^/\\s<>\"{}|^`\\\\]+$");

    public void requireDistinctFeatures() {
        requireFeature("from", from);
        requireFeature("to", to);
        if (from.equals(to)) {
            throw new DemoRejectedException(HttpStatus.BAD_REQUEST, "from and to name the same feature", "same-feature");
        }
    }

    private static void requireFeature(String field, String iri) {
        if (iri == null || !FEATURE.matcher(iri).matches()) {
            throw new DemoRejectedException(HttpStatus.BAD_REQUEST,
                    field + " is not a feature IRI: expected " + SHAPE + ", got '" + iri + "'", "bad-feature-iri");
        }
    }
}
