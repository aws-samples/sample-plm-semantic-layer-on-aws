// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.demo;

import java.util.function.Supplier;

import org.springframework.http.HttpStatus;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResultEntry;

/**
 * {@link EventPublisher} over the AWS SDK v2 {@link EventBridgeClient}: one {@code PutEvents} with
 * one entry on the named bus. An entry EventBridge refuses (the response's failed count) is 502
 * with EventBridge's error code; the caller's credentials must allow {@code events:PutEvents} on
 * the bus.
 */
public final class EventBridgePublisher implements EventPublisher {

    private final Supplier<EventBridgeClient> clientSupplier;
    private final String bus;
    private volatile EventBridgeClient client;

    public EventBridgePublisher(EventBridgeClient client, String bus) {
        this(() -> client, bus);
        this.client = client;
    }

    /** The client is built on the first publish, so a broken AWS config surfaces as a 502 there. */
    public EventBridgePublisher(Supplier<EventBridgeClient> clientSupplier, String bus) {
        this.clientSupplier = clientSupplier;
        this.bus = bus;
    }

    private EventBridgeClient client() {
        EventBridgeClient c = client;
        if (c == null) {
            synchronized (this) {
                if (client == null) {
                    try {
                        client = clientSupplier.get();
                    } catch (SdkClientException | IllegalArgumentException e) {
                        throw new DemoRejectedException(HttpStatus.BAD_GATEWAY, "EventBridge client unavailable: " + e.getMessage(), "event-client");
                    }
                }
                c = client;
            }
        }
        return c;
    }

    @Override
    public String put(String source, String detailType, String detailJson) {
        EventBridgeClient client = client();
        PutEventsRequestEntry entry = PutEventsRequestEntry.builder()
                .eventBusName(bus)
                .source(source)
                .detailType(detailType)
                .detail(detailJson)
                .build();
        PutEventsResponse response = client.putEvents(PutEventsRequest.builder().entries(entry).build());
        PutEventsResultEntry result = response.entries().isEmpty() ? null : response.entries().get(0);
        if (result == null || result.eventId() == null || (response.failedEntryCount() != null && response.failedEntryCount() > 0)) {
            String code = result == null || result.errorCode() == null ? "no result entry" : result.errorCode();
            throw new DemoRejectedException(HttpStatus.BAD_GATEWAY, "EventBridge refused the event: " + code, "event-refused");
        }
        return result.eventId();
    }
}
