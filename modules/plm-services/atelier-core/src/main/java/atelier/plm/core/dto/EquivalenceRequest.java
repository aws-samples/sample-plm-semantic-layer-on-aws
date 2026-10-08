// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.core.dto;

import atelier.plm.common.demo.DemoRejectedException;
import atelier.plm.core.graph.Graphs;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Body of {@code POST /core/equivalences}: the part IRIs a user confirms as one item, each
 * {@code https://example.com/atelier/{fr|de|uk|es}/part/{id}} with an id safe inside a Turtle IRI, two to
 * {@value #MAX_PARTS} of them, all different.
 */
public record EquivalenceRequest(List<String> parts) {

    static final int MAX_PARTS = 16;
    static final String SHAPE = Graphs.DATA + "{fr|de|uk|es}/part/{id}";
    static final Pattern PART = Pattern.compile("^" + Pattern.quote(Graphs.DATA) + "(fr|de|uk|es)/part/[^/\\s<>\"{}|^`\\\\]+$");

    public void requireDistinctParts() {
        if (parts == null || parts.size() < 2 || parts.size() > MAX_PARTS) {
            throw new DemoRejectedException(HttpStatus.BAD_REQUEST,
                    "parts must name 2 to " + MAX_PARTS + " part IRIs", "bad-parts");
        }
        for (String iri : parts) {
            if (iri == null || !PART.matcher(iri).matches()) {
                throw new DemoRejectedException(HttpStatus.BAD_REQUEST,
                        "not a part IRI: expected " + SHAPE + ", got '" + iri + "'", "bad-part-iri");
            }
        }
        if (parts.stream().distinct().count() != parts.size()) {
            throw new DemoRejectedException(HttpStatus.BAD_REQUEST, "parts names a part twice", "same-part");
        }
    }
}
