// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.demo;

import org.springframework.http.HttpStatus;

/**
 * A demo-control request the service refuses: carries the HTTP status to answer with and a stable
 * {@code reason} category; {@link DemoErrorAdvice} turns it into a {@link DemoError} body.
 */
public final class DemoRejectedException extends RuntimeException {

    private final HttpStatus status;
    private final String reason;

    public DemoRejectedException(HttpStatus status, String message, String reason) {
        super(message);
        this.status = status;
        this.reason = reason;
    }

    public HttpStatus status() {
        return status;
    }

    public DemoError toError() {
        return new DemoError(getMessage(), reason);
    }
}
