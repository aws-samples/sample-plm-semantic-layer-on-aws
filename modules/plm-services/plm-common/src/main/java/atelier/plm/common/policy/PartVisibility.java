// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.policy;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Which of this PLM's parts a request may see, decided from the {@value Policy#HEADER} header and the
 * part tags of the Atelier core database: a part is visible when its tag releases it to the profile's
 * clearance, a feature follows its part, an absent or unknown profile sees the parts released to all,
 * and an unconfigured tag store makes every part invisible. This is the decision
 * {@link atelier.plm.common.tables.ReleasabilityFilter} binds into SQL for {@code /{plm}/tables} and
 * {@code /{plm}/sql}; the plain read routes apply it to the entity rows their repositories load, so
 * every route of a PLM service answers the same profile with the same parts.
 */
@Component
public final class PartVisibility {

    private final Policy policy;
    private final PartTagStore tags;

    public PartVisibility(Policy policy, PartTagStore tags) {
        this.policy = policy;
        this.tags = tags;
    }

    /** The native keys of this PLM's parts the profile may see. */
    public Set<String> parts(String profile) {
        return Set.copyOf(tags.visibleKeys(policy.clearance(profile)));
    }

    /** Whether the profile may see the part with that native key. */
    public boolean sees(String nativeKey, String profile) {
        return !tags.visible(List.of(nativeKey), policy.clearance(profile)).isEmpty();
    }

    /** The rows whose part, named by {@code partKey}, the profile may see, in the order given. */
    public <T> List<T> keep(List<T> rows, Function<T, String> partKey, String profile) {
        Set<String> visible = parts(profile);
        return rows.stream().filter(row -> visible.contains(partKey.apply(row))).toList();
    }
}
