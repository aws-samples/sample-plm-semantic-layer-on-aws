// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.demo;

import org.springframework.http.HttpStatus;

/**
 * Puts one event on the Atelier event bus and returns its id. The production implementation is
 * {@link EventBridgePublisher}; a service started without {@code AWS_REGION} gets {@link #unconfigured()},
 * which refuses every event with 503, so a service without an event bus never touches AWS. An
 * endpoint whose write stands without its announcement (a PLM's value correction, the core's link
 * write) asks {@link #skipped()} first and reports the reason instead of failing.
 */
public interface EventPublisher {

    String put(String source, String detailType, String detailJson);

    /** Why this publisher puts nothing, or null when it publishes. */
    default String skipped() {
        return null;
    }

    static EventPublisher unconfigured() {
        String reason = "event bus not configured: AWS_REGION is not set";
        return new EventPublisher() {
            @Override
            public String put(String source, String detailType, String detailJson) {
                throw new DemoRejectedException(HttpStatus.SERVICE_UNAVAILABLE, reason, "event-bus-not-configured");
            }

            @Override
            public String skipped() {
                return reason;
            }
        };
    }
}
