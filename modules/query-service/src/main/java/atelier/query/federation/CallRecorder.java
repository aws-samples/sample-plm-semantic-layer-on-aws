// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.federation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Requests, elapsed time and request texts per endpoint for one run, keyed by endpoint name.
 * Every endpoint of the federation is listed from the start, so one that received no request
 * reports zero requests. Safe to call from the concurrent PLM requests.
 */
public final class CallRecorder {
    public record Counts(int requests, long nanos) {}

    private final Map<String, String> namesByUrl;
    private final Map<String, Counts> countsByName = new LinkedHashMap<>();
    private final Map<String, List<String>> queriesByName = new LinkedHashMap<>();

    public CallRecorder(Map<String, String> namesByUrl) {
        this.namesByUrl = namesByUrl;
        namesByUrl.values().forEach(name -> {
            countsByName.put(name, new Counts(0, 0));
            queriesByName.put(name, new ArrayList<>());
        });
    }

    synchronized void record(String url, long nanos) {
        countsByName.merge(nameOf(url), new Counts(1, nanos),
                (a, b) -> new Counts(a.requests() + b.requests(), a.nanos() + b.nanos()));
    }

    synchronized void recordQuery(String url, String query) {
        queriesByName.computeIfAbsent(nameOf(url), k -> new ArrayList<>()).add(query);
    }

    public synchronized Map<String, Counts> counts() {
        return new LinkedHashMap<>(countsByName);
    }

    /** Request texts sent to each endpoint, in sending order. */
    public synchronized Map<String, List<String>> queries() {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        queriesByName.forEach((name, queries) -> copy.put(name, List.copyOf(queries)));
        return copy;
    }

    private String nameOf(String url) {
        return namesByUrl.getOrDefault(url, url);
    }
}
