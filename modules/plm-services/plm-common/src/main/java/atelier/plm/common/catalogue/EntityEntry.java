// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.catalogue;

import java.util.List;

/** Catalogue view of one JPA entity and the native table it maps. */
public record EntityEntry(
        String entity,
        String table,
        String ontologyClass,
        String description,
        List<ColumnEntry> columns) {
}
