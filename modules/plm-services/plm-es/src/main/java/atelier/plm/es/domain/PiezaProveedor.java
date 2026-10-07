// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.es.domain;

import atelier.plm.common.annotation.Describe;
import atelier.plm.common.annotation.Maps;
import atelier.plm.common.annotation.OntologyClass;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A supplier's offer for a part, native {@code pieza_proveedor} table. */
@Entity
@Table(name = "pieza_proveedor")
@OntologyClass("atelier:SupplierOffer")
@Describe("Pieza de proveedor: one supplier's offer for a piece of the Spanish PLM; a piece may have several")
public class PiezaProveedor {

    @Id
    @Column(name = "id")
    @Describe("Número: the offer's key")
    private Integer id;

    @Column(name = "cod_pieza")
    @Describe("Código de pieza: the piece offered")
    @Maps("atelier:offersPart")
    private String codPieza;

    @Column(name = "cod_proveedor")
    @Describe("Código de proveedor: the supplier making the offer")
    @Maps("atelier:fromSupplier")
    private String codProveedor;

    @Column(name = "referencia_proveedor")
    @Describe("Referencia de proveedor: the supplier's own reference for the item")
    @Maps("atelier:supplierPartNumber")
    private String referenciaProveedor;

    @Column(name = "plazo_dias")
    @Describe("Plazo: the lead time in days")
    @Maps("atelier:leadTimeDays")
    private Integer plazoDias;

    @Column(name = "preferido")
    @Describe("Preferido: whether the site orders from this offer first")
    @Maps("atelier:preferred")
    private Boolean preferido;

    protected PiezaProveedor() {
    }

    public Integer getId() {
        return id;
    }

    public String getCodPieza() {
        return codPieza;
    }

    public String getCodProveedor() {
        return codProveedor;
    }

    public String getReferenciaProveedor() {
        return referenciaProveedor;
    }

    public Integer getPlazoDias() {
        return plazoDias;
    }

    public Boolean getPreferido() {
        return preferido;
    }
}
