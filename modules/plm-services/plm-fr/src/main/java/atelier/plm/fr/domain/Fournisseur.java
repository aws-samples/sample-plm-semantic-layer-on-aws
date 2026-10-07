// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.fr.domain;

import atelier.plm.common.annotation.Describe;
import atelier.plm.common.annotation.Maps;
import atelier.plm.common.annotation.OntologyClass;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A supplier as stored in the native {@code fournisseur} table. */
@Entity
@Table(name = "fournisseur")
@OntologyClass("atelier:Supplier")
@Describe("Fournisseur : a supplier of the French site")
public class Fournisseur {

    @Id
    @Column(name = "code_fournisseur")
    @Describe("Code fournisseur : the supplier's key in the French PLM")
    @Maps("atelier:identifier")
    private String codeFournisseur;

    @Column(name = "raison_sociale")
    @Describe("Raison sociale : the supplier's company name")
    @Maps("atelier:label")
    private String raisonSociale;

    @Column(name = "ville")
    @Describe("Ville : the town the supplier delivers from")
    @Maps("atelier:location")
    private String ville;

    protected Fournisseur() {
    }

    public String getCodeFournisseur() {
        return codeFournisseur;
    }

    public String getRaisonSociale() {
        return raisonSociale;
    }

    public String getVille() {
        return ville;
    }
}
