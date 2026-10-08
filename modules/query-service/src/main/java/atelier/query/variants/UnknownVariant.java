// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.variants;

import atelier.query.federation.UnknownItem;
import java.util.List;

/** A variant group or option the product does not have; the message lists the groups it has, each with its options. */
public class UnknownVariant extends UnknownItem {
    public UnknownVariant(String product, String group, String option, List<String> groups) {
        super("variant " + group + " option " + option, of(product, groups));
    }

    /** An option code no variant group of the product has. */
    public UnknownVariant(String product, String option, List<String> groups) {
        super("option " + option, of(product, groups));
    }

    private static String of(String product, List<String> groups) {
        return product + (groups.isEmpty() ? ", which has no variant groups" : "; its variant groups: " + String.join("; ", groups));
    }
}
