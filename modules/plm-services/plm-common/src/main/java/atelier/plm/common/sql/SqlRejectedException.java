// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.sql;

import java.util.List;

/** A statement the endpoint refuses to run; carried to the controller, which answers 400 with a {@link SqlError}. */
public final class SqlRejectedException extends RuntimeException {

    private final String reason;
    private final List<String> suggestions;

    public SqlRejectedException(String message, String reason) {
        this(message, reason, List.of());
    }

    public SqlRejectedException(String message, String reason, List<String> suggestions) {
        super(message);
        this.reason = reason;
        this.suggestions = List.copyOf(suggestions);
    }

    public SqlError toError() {
        return new SqlError(getMessage(), reason, suggestions);
    }
}
