// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.core.dto;

import atelier.plm.common.demo.DemoRejectedException;
import atelier.plm.core.demo.PlmApi;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.regex.Pattern;

/**
 * One row of {@code demo_change} as {@code /core/changes} exchanges it: the PLM, table, key and
 * column of a value correction, its values before and after as text, who asked for it and why, and
 * when it was logged. {@code id} and {@code at} are assigned by the core database and ignored in a
 * POST body. A row is appended only in the form a PLM announces a correction (see {@link #validate()}),
 * because the demo reset sends it back to that PLM.
 */
public record DemoChangeDto(Long id, String plm, String table, String key, String column, String before, String after,
                            String actor, String purpose, OffsetDateTime at) {

    /** A PostgreSQL identifier as the PLM catalogues name tables and columns: lower case, unquoted. */
    static final Pattern IDENTIFIER = Pattern.compile("[a-z_][a-z0-9_]{0,62}");
    /** A native key: up to the key columns' 64 characters, no control character and no path separator. */
    static final Pattern KEY = Pattern.compile("[^\\p{Cntrl}/\\\\]{1,64}");
    /** A profile name of the policy. */
    static final Pattern ACTOR = Pattern.compile("[a-z][a-z0-9-]{0,63}");
    static final int MAX_PURPOSE = 500;
    static final int MAX_VALUE = 1024;

    /**
     * Refuses, 400, a row a PLM would not announce: {@code missing-field} without plm, table, key or
     * column; {@code bad-field} unless plm is one of {@link PlmApi#PLMS}, table and column are lower-case
     * identifiers, the key fits {@link #KEY}, the actor is a profile name, the purpose is at most
     * {@value #MAX_PURPOSE} characters and each value at most {@value #MAX_VALUE}.
     */
    public void validate() {
        if (isBlank(plm) || isBlank(table) || isBlank(key) || isBlank(column)) {
            throw new DemoRejectedException(HttpStatus.BAD_REQUEST, "plm, table, key and column are required", "missing-field");
        }
        if (!PlmApi.PLMS.contains(plm)) {
            throw bad("plm must be one of " + PlmApi.PLMS);
        }
        if (!IDENTIFIER.matcher(table).matches() || !IDENTIFIER.matcher(column).matches()) {
            throw bad("table and column must be lower-case identifiers matching " + IDENTIFIER);
        }
        if (!KEY.matcher(key).matches()) {
            throw bad("key must be 1 to 64 characters without a control character, / or \\");
        }
        if (actor != null && !ACTOR.matcher(actor).matches()) {
            throw bad("actor must be a profile name");
        }
        if (purpose != null && purpose.length() > MAX_PURPOSE) {
            throw bad("purpose must be at most " + MAX_PURPOSE + " characters");
        }
        if (before != null && before.length() > MAX_VALUE || after != null && after.length() > MAX_VALUE) {
            throw bad("before and after must be at most " + MAX_VALUE + " characters");
        }
    }

    private static DemoRejectedException bad(String message) {
        return new DemoRejectedException(HttpStatus.BAD_REQUEST, message, "bad-field");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
