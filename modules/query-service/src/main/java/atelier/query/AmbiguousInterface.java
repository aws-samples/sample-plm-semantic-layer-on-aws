// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import java.util.List;

/** An interface id that several products of the merged graph have: the request must name the product. */
public class AmbiguousInterface extends RuntimeException {
    private final String id;
    private final List<String> products;

    public AmbiguousInterface(String id, List<String> products) {
        super("interface " + id + " exists in several products (" + String.join(", ", products) + "): pass product to name one");
        this.id = id;
        this.products = List.copyOf(products);
    }

    public String id() {
        return id;
    }

    /** The keys of the products that have an interface of the id, sorted. */
    public List<String> products() {
        return products;
    }
}
