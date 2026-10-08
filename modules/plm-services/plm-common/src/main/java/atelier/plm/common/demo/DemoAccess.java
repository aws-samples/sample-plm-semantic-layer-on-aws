// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.demo;

import atelier.plm.common.policy.Policy;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Locale;

/**
 * Who may act on a demo control (docs/contract.md, "Freshness and the change feed"): the
 * {@value Policy#HEADER} header must name one of the allowed profiles, 403 otherwise, the answer
 * naming them with reason {@value #REASON}. Profile names are the literal keys of
 * {@code ontology/policy.json}, not clearances: a value correction or a CAD publication in a PLM is
 * for that PLM's own engineer ({@code <plm>-engineer}) or the export-control officer; a mating
 * link, Atelier's fact, is for the integration role ({@code programme-cleared}) or the officer; the
 * change list is for every profile but {@code unknown}; the reset is the officer's alone.
 */
public final class DemoAccess {

    public static final String OFFICER = "export-officer";
    public static final String PROGRAMME_CLEARED = "programme-cleared";
    public static final String REASON = "not-allowed";

    private final List<String> profiles;

    private DemoAccess(List<String> profiles) {
        this.profiles = List.copyOf(profiles);
    }

    /** The export-control officer alone. */
    public static DemoAccess officer() {
        return new DemoAccess(List.of(OFFICER));
    }

    /** A PLM's own engineer or the officer. */
    public static DemoAccess plm(String plmCode) {
        return new DemoAccess(List.of(engineer(plmCode), OFFICER));
    }

    /** The integration role or the officer. */
    public static DemoAccess integration() {
        return new DemoAccess(List.of(PROGRAMME_CLEARED, OFFICER));
    }

    /** Every profile of the policy but {@value Policy#UNKNOWN}. */
    public static DemoAccess anyKnown(Policy policy) {
        return new DemoAccess(policy.profiles().keySet().stream().filter(name -> !Policy.UNKNOWN.equals(name)).sorted().toList());
    }

    /** The engineer profile of a PLM: {@code fr-engineer} for {@code fr} or {@code FR}. */
    public static String engineer(String plmCode) {
        return plmCode.toLowerCase(Locale.ROOT) + "-engineer";
    }

    public List<String> profiles() {
        return profiles;
    }

    /** @throws DemoRejectedException 403 unless {@code profile} is one of the allowed profiles */
    public void require(String profile) {
        if (profile == null || !profiles.contains(profile)) {
            throw new DemoRejectedException(HttpStatus.FORBIDDEN, Policy.HEADER + " must be one of " + profiles, REASON);
        }
    }
}
