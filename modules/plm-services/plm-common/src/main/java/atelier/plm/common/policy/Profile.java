// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.policy;

import java.util.List;

/** One viewer profile of the export-control policy and the releasability tokens it may see. */
public record Profile(String label, String nationality, List<String> releasable) {
}
