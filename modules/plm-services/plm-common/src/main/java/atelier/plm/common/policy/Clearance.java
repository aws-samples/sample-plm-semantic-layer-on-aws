// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.policy;

import java.util.List;

/**
 * What a request may see: the profile it resolved to and that profile's releasability tokens.
 * Echoed as {@code policy} in every filtered response.
 */
public record Clearance(String profile, List<String> releasable) {
}
