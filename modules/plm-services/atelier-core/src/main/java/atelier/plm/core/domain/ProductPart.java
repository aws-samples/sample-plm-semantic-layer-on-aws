// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.core.domain;

import atelier.plm.common.annotation.Describe;
import atelier.plm.common.annotation.Maps;
import atelier.plm.common.annotation.OntologyClass;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * The membership of one PLM part in one product, as stored in the core {@code product_part} table.
 * Its subject is the PLM's own part IRI, keyed like {@link PartTag}, so {@code atelier:partOf} joins the
 * part in the virtual graph; a part may sit in several products.
 */
@Entity
@Table(name = "product_part")
@IdClass(ProductPartId.class)
@OntologyClass(value = "atelier:Part", subject = "https://example.com/atelier/{plm}/part/{native_key}")
@Describe("Product part: places a part published by a PLM in a product, one row per product and part; served "
        + "unfiltered by /core/tables, as product membership is Atelier's structure and not PLM data")
public class ProductPart {

    @Id
    @Column(name = "product_key", length = 32)
    @Describe("Product key: the product the part belongs to, the last segment of the product IRI")
    @Maps("atelier:partOf")
    private String productKey;

    @Id
    @Column(name = "plm", length = 2)
    @Describe("PLM: lower-case code of the PLM owning the part (fr, de, uk, es), as in the part IRI")
    private String plm;

    @Id
    @Column(name = "native_key", length = 64)
    @Describe("Native key: the part's key in the owning PLM")
    private String nativeKey;

    protected ProductPart() {
    }

    public String getProductKey() {
        return productKey;
    }

    public String getPlm() {
        return plm;
    }

    public String getNativeKey() {
        return nativeKey;
    }
}
