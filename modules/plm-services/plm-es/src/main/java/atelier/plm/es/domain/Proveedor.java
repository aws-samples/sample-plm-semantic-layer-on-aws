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

/** A supplier as stored in the native {@code proveedor} table. */
@Entity
@Table(name = "proveedor")
@OntologyClass("atelier:Supplier")
@Describe("Proveedor: a supplier of the Spanish site")
public class Proveedor {

    @Id
    @Column(name = "cod_proveedor")
    @Describe("Código de proveedor: the supplier's key in the Spanish PLM")
    @Maps("atelier:identifier")
    private String codProveedor;

    @Column(name = "nombre")
    @Describe("Nombre: the supplier's company name")
    @Maps("atelier:label")
    private String nombre;

    @Column(name = "ciudad")
    @Describe("Ciudad: the town the supplier delivers from")
    @Maps("atelier:location")
    private String ciudad;

    protected Proveedor() {
    }

    public String getCodProveedor() {
        return codProveedor;
    }

    public String getNombre() {
        return nombre;
    }

    public String getCiudad() {
        return ciudad;
    }
}
