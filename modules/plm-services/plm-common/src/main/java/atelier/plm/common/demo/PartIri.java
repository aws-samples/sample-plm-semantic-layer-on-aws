// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.demo;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * The part IRI the contract mints for a native part id, {@code https://example.com/atelier/{plm}/part/{id}},
 * the id percent-encoded as R2RML does when Ontop mints the same subject (every character but the
 * unreserved {@code A-Z a-z 0-9 - . _ ~}), so an event names the IRI the file index and the graphs
 * already hold.
 */
final class PartIri {

    static final String BASE = "https://example.com/atelier/";

    private PartIri() {
    }

    static String of(String plmCode, String part) {
        return BASE + plmCode + "/part/" + encode(part);
    }

    static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }
}
