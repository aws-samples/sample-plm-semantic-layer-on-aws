// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.core.domain;

import java.io.Serializable;
import java.util.Objects;

/** Composite key of a product membership: the product's key, the owning PLM's code and the part's key in that PLM. */
public class ProductPartId implements Serializable {

    private String productKey;
    private String plm;
    private String nativeKey;

    protected ProductPartId() {
    }

    public ProductPartId(String productKey, String plm, String nativeKey) {
        this.productKey = productKey;
        this.plm = plm;
        this.nativeKey = nativeKey;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ProductPartId that && productKey.equals(that.productKey) && plm.equals(that.plm)
                && nativeKey.equals(that.nativeKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productKey, plm, nativeKey);
    }
}
