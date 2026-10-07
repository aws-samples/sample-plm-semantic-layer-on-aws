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

/** A supplier's offer for a part, native {@code article_fournisseur} table. */
@Entity
@Table(name = "article_fournisseur")
@OntologyClass("atelier:SupplierOffer")
@Describe("Article fournisseur : one supplier's offer for a piece of the French PLM; a piece may have several")
public class ArticleFournisseur {

    @Id
    @Column(name = "id")
    @Describe("Numéro : the offer's key")
    private Integer id;

    @Column(name = "ref_piece")
    @Describe("Référence pièce : the piece offered")
    @Maps("atelier:offersPart")
    private String refPiece;

    @Column(name = "code_fournisseur")
    @Describe("Code fournisseur : the supplier making the offer")
    @Maps("atelier:fromSupplier")
    private String codeFournisseur;

    @Column(name = "reference_fournisseur")
    @Describe("Référence fournisseur : the supplier's own reference for the item")
    @Maps("atelier:supplierPartNumber")
    private String referenceFournisseur;

    @Column(name = "delai_jours")
    @Describe("Délai : the lead time in days")
    @Maps("atelier:leadTimeDays")
    private Integer delaiJours;

    @Column(name = "prefere")
    @Describe("Préféré : whether the site orders from this offer first")
    @Maps("atelier:preferred")
    private Boolean prefere;

    protected ArticleFournisseur() {
    }

    public Integer getId() {
        return id;
    }

    public String getRefPiece() {
        return refPiece;
    }

    public String getCodeFournisseur() {
        return codeFournisseur;
    }

    public String getReferenceFournisseur() {
        return referenceFournisseur;
    }

    public Integer getDelaiJours() {
        return delaiJours;
    }

    public Boolean getPrefere() {
        return prefere;
    }
}
