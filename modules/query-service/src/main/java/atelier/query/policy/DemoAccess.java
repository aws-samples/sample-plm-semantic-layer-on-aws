// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.policy;

import atelier.query.api.QueryController;
import java.util.List;

/**
 * Who may read the change list ({@code GET /query/demo/changes}; docs/contract.md, "Freshness and
 * the change feed"): every profile of {@code ontology/policy.json} but {@code unknown}, 403
 * otherwise, the answer naming the allowed profiles. The demo's writes are the PLM and core
 * services' own routes; they check the contract's table there.
 */
public final class DemoAccess {
    /** A caller outside the allowed profiles; the message names them. */
    public static final class Refused extends RuntimeException {
        Refused(String message) {
            super(message);
        }
    }

    private final List<String> profiles;

    private DemoAccess(List<String> profiles) {
        this.profiles = List.copyOf(profiles);
    }

    /** Every profile of the policy but {@value Policy#UNKNOWN}. */
    public static DemoAccess anyKnown(Policy policy) {
        return new DemoAccess(policy.profiles().stream().map(Policy.Profile::name).filter(name -> !Policy.UNKNOWN.equals(name))
                .sorted().toList());
    }

    public List<String> profiles() {
        return profiles;
    }

    /** @throws Refused unless the caller's profile is one of the allowed profiles */
    public void require(Caller caller) {
        if (!profiles.contains(caller.profile().name())) {
            throw new Refused(QueryController.PROFILE_HEADER + " must be one of " + profiles);
        }
    }
}
