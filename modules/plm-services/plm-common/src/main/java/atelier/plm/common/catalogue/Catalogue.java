// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.catalogue;

import java.util.List;

/** Data catalogue of one PLM, derived from its ORM mapping. */
public record Catalogue(String plm, List<EntityEntry> entities) {
}
