// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.demo;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResultEntry;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The EventBridge adapter against a fake client: one entry on the named bus, and EventBridge's refusal surfaced as 502. */
class EventBridgePublisherTest {

    /** A client answering every PutEvents with the given result entry and recording the request. */
    static final class FakeClient implements EventBridgeClient {
        final List<PutEventsRequest> requests = new ArrayList<>();
        final PutEventsResponse response;

        FakeClient(PutEventsResponse response) {
            this.response = response;
        }

        @Override
        public PutEventsResponse putEvents(PutEventsRequest request) {
            requests.add(request);
            return response;
        }

        @Override
        public String serviceName() {
            return "eventbridge";
        }

        @Override
        public void close() {
        }
    }

    @Test
    void putsOneEntryOnTheBusAndReturnsTheEventId() {
        FakeClient client = new FakeClient(PutEventsResponse.builder()
                .failedEntryCount(0)
                .entries(PutEventsResultEntry.builder().eventId("11111111-2222-3333-4444-555555555abc").build())
                .build());

        String eventId = new EventBridgePublisher(client, "atelier-bus").put("atelier.plm", "part.cad.published", "{\"plm\":\"UK\"}");

        assertThat(eventId).isEqualTo("11111111-2222-3333-4444-555555555abc");
        assertThat(client.requests).hasSize(1);
        assertThat(client.requests.get(0).entries()).hasSize(1);
        PutEventsRequestEntry entry = client.requests.get(0).entries().get(0);
        assertThat(entry.eventBusName()).isEqualTo("atelier-bus");
        assertThat(entry.source()).isEqualTo("atelier.plm");
        assertThat(entry.detailType()).isEqualTo("part.cad.published");
        assertThat(entry.detail()).isEqualTo("{\"plm\":\"UK\"}");
    }

    @Test
    void aRefusedEntryIs502WithEventBridgesErrorCode() {
        FakeClient client = new FakeClient(PutEventsResponse.builder()
                .failedEntryCount(1)
                .entries(PutEventsResultEntry.builder().errorCode("ThrottlingException").errorMessage("slow down").build())
                .build());

        assertThatThrownBy(() -> new EventBridgePublisher(client, "atelier-bus").put("atelier.plm", "part.cad.published", "{}"))
                .isInstanceOf(DemoRejectedException.class)
                .hasMessage("EventBridge refused the event: ThrottlingException")
                .satisfies(e -> assertThat(((DemoRejectedException) e).status().value()).isEqualTo(502));
    }
}
