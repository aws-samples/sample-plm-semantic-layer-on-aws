// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.core.dto;

import atelier.plm.core.domain.Product;

/** A product as GET /core/products returns it: its key, name, coordinate frame and the number of parts placed in it. */
public record ProductDto(String key, String name, String frame, long partCount) {

    public static ProductDto of(Product product, long partCount) {
        return new ProductDto(product.getProductKey(), product.getName(), product.getFrame(), partCount);
    }
}
