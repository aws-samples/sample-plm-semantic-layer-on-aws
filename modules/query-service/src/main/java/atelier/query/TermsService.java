// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import atelier.query.api.Json;
import atelier.query.federation.Federator;
import atelier.query.mapping.PartsFinder;
import atelier.query.mapping.TermsMapper;
import atelier.query.policy.Caller;
import atelier.query.policy.Redaction;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A term in any of the four languages, resolved to the concepts of the products' glossary and to the items whose names
 * hold them ({@link TermsMapper}), over the labels graph redacted for the caller ({@link Federator#terms}).
 */
final class TermsService {
    /** A term is a few words: letters, digits, spaces, hyphens and apostrophes. */
    private static final Pattern TERM = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N} '’-]{0,63}");

    private final Federator federator;

    TermsService(Federator federator) {
        this.federator = federator;
    }

    /** The concepts each text names, resolved over one redacted labels federation ({@code result}). */
    record Resolved(Map<String, List<Json.Term>> terms, Federator.Result result) {}

    Json.Terms terms(Caller caller, String q) {
        String text = term(q);
        long start = System.nanoTime();
        Federator.Result raw = federator.terms(caller.profile());
        Federator.Result result = redacted(raw, caller);
        long mapping = System.nanoTime();
        var terms = new TermsMapper(result.model()).resolve(text);
        return new Json.Terms(text, terms, new Json.Provenance(result.calls()), result.sparql(),
                new Json.Timings((System.nanoTime() - start) / 1_000_000, result.ms(), (System.nanoTime() - mapping) / 1_000_000),
                caller.json(Redaction.untagged(raw.model())));
    }

    /** Each of {@code texts} resolved to the concepts it names, or its singular names, over one labels federation. */
    Resolved resolve(Caller caller, List<String> texts) {
        texts.forEach(TermsService::term);
        Federator.Result result = redacted(federator.terms(caller.profile()), caller);
        TermsMapper mapper = new TermsMapper(result.model());
        Map<String, List<Json.Term>> terms = new LinkedHashMap<>();
        for (String text : texts) {
            List<Json.Term> found = mapper.resolve(text);
            terms.put(text, found.isEmpty() ? mapper.resolve(PartsFinder.singular(text)) : found);
        }
        return new Resolved(terms, result);
    }

    private static Federator.Result redacted(Federator.Result raw, Caller caller) {
        return raw.edited(m -> Redaction.apply(m, caller.profile().releasable(), caller.profile().seesUntagged()));
    }

    private static String term(String q) {
        String text = q == null ? "" : q.strip();
        if (!TERM.matcher(text).matches()) {
            throw new IllegalArgumentException("q must be a term of 1 to 64 letters, digits, spaces, hyphens or apostrophes");
        }
        return text;
    }
}
