// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.federation;

import java.util.List;
import java.util.Set;

/**
 * How a subtree was resolved across the sites. Round 1 asked the owning site for the root's tree; each later round
 * asked the sites the external references of the round before name for the trees of the referenced items.
 *
 * @param root IRI of the root item
 * @param product IRI of the product the root belongs to
 * @param rounds the rounds that added items, in order
 * @param items every item reached, the hidden ones included (each a redacted node whose own items were not expanded)
 * @param hidden the items reached that the viewer may not see
 * @param context the parts outside the subtree on the far side of an interface with a side in it
 * @param unresolved the items referenced from the last round that the bound on rounds left unexpanded
 */
public record Subtree(String root, String product, List<Round> rounds, Set<String> items, Set<String> hidden,
                      Set<String> context, Set<String> unresolved) {
    /** Most rounds a resolution runs; a subtree whose references go deeper reports the items left unresolved. */
    public static final int MAX_ROUNDS = 8;

    /** One round: the roots it asked the sites about, by IRI, and the items it added. */
    public record Round(int round, List<String> roots, int items) {}

    public int depth() {
        return rounds.size();
    }

    /** True when the viewer may not see the root: nothing under it was expanded. */
    public boolean redacted() {
        return hidden.contains(root);
    }
}
