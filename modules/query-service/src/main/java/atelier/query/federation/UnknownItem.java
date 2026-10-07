// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.federation;

/** A subtree was asked for under an id that names no item of the product. */
public class UnknownItem extends RuntimeException {
    public UnknownItem(String id, String product) {
        super("no item " + id + " in " + product);
    }
}
