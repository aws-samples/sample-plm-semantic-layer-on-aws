// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.tables;

import atelier.plm.common.policy.Clearance;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * The clearance the rows were filtered with, echoed as {@code policy} in a {@link TableRows}
 * response. {@code error} names why every filtered row was withheld (the tag store the policy
 * reads is not configured) and is absent when the policy was evaluated.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TablePolicy(String profile, List<String> releasable, String error) {

    static TablePolicy of(Clearance clearance, String error) {
        return new TablePolicy(clearance.profile(), clearance.releasable(), error);
    }
}
