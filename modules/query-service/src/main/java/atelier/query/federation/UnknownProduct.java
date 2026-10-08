// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.federation;

/** A run was asked for a product no named part belongs to: unknown, or without an interface. */
public class UnknownProduct extends RuntimeException {
    public UnknownProduct(String key) {
        super("no product " + key);
    }
}
