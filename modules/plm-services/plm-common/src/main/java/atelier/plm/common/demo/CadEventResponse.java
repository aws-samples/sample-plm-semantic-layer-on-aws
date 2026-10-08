// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.demo;

import java.time.OffsetDateTime;

/**
 * Answer of {@code POST /{plm}/demo/events/cad}: the EventBridge event id and the detail that was put
 * ({@code plm, part, partIri, cadFile, at}).
 */
public record CadEventResponse(String eventId, String plm, String part, String partIri, String cadFile, OffsetDateTime at) {
}
