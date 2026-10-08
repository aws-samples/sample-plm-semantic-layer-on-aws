// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.core.domain;

import atelier.plm.common.annotation.Describe;
import atelier.plm.common.annotation.Maps;
import atelier.plm.common.annotation.OntologyClass;
import atelier.plm.common.annotation.Unit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * A product the PLMs' parts are assembled into, as stored in the core {@code product} table. Atelier mints
 * its IRI, {@code https://example.com/atelier/product/{product_key}}; the mapping carries no
 * {@code atelier:ownedBy}, since a product belongs to the integrator, not to a PLM.
 */
@Entity
@Table(name = "product")
@OntologyClass(value = "atelier:Product", subject = "https://example.com/atelier/product/{product_key}")
@Describe("Product: one product the PLMs' parts are assembled into, with the coordinate frame its CAD and feature "
        + "positions are expressed in; Atelier's own structure, served unfiltered by /core/tables")
public class Product {

    @Id
    @Column(name = "product_key", length = 32)
    @Describe("Product key: the product's key, the last segment of its IRI")
    private String productKey;

    @Column(name = "name", length = 120)
    @Describe("Name: the product's display name")
    @Maps("atelier:label")
    private String name;

    @Column(name = "frame", columnDefinition = "text")
    @Describe("Frame: the coordinate frame of the product's CAD and feature positions, as the viewer shows it")
    @Maps("atelier:frame")
    private String frame;

    @Column(name = "mass_limit_kg", precision = 12, scale = 3)
    @Describe("Mass limit: the most the product may weigh, in kilograms, summed over every site's parts; empty when the product has none")
    @Unit("KiloGM")
    @Maps("atelier:massLimit")
    private BigDecimal massLimitKg;

    protected Product() {
    }

    public String getProductKey() {
        return productKey;
    }

    public String getName() {
        return name;
    }

    public String getFrame() {
        return frame;
    }

    public BigDecimal getMassLimitKg() {
        return massLimitKg;
    }
}
