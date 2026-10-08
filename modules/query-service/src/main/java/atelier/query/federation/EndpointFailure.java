// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.federation;

/**
 * A federated endpoint did not answer a request. Names the endpoint as provenance does
 * ({@code ontop-fr}, {@code neptune}), never its URL; the detail is logged where it happened and
 * kept as the cause.
 */
public final class EndpointFailure extends RuntimeException {
    private final String endpoint;

    public EndpointFailure(String endpoint, Throwable cause) {
        super("a federated endpoint failed: " + endpoint, cause);
        this.endpoint = endpoint;
    }

    public String endpoint() {
        return endpoint;
    }
}
