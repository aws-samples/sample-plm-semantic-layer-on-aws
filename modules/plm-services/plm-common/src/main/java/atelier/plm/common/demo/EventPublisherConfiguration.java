// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.demo;

import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;

/**
 * The event publisher of the demo endpoints (value correction, CAD publication, link): an EventBridge client in the region named by
 * {@code AWS_REGION}, putting on the bus named by {@code ATELIER_EVENT_BUS} ({@code default} when unset),
 * built and its credentials resolved at the first publish, never while the application starts (the
 * SDK reads the AWS config files when the client is built, and a malformed file must not stop the
 * service). Without {@code AWS_REGION} the publisher is unconfigured: the CAD endpoint answers 503, the
 * endpoints whose write stands without its event report {@code eventSkipped}.
 */
@Configuration
public class EventPublisherConfiguration {

    @Bean
    public EventPublisher eventPublisher(@Value("${AWS_REGION:}") String region, @Value("${ATELIER_EVENT_BUS:default}") String bus) {
        if (region.isBlank()) {
            return EventPublisher.unconfigured();
        }
        Supplier<EventBridgeClient> client = () -> EventBridgeClient.builder()
                .region(Region.of(region))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
        return new EventBridgePublisher(client, bus);
    }
}
