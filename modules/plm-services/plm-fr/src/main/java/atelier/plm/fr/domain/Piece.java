// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.fr.domain;

import atelier.plm.common.annotation.Accepts;
import atelier.plm.common.annotation.Describe;
import atelier.plm.common.annotation.Maps;
import atelier.plm.common.annotation.OntologyClass;
import atelier.plm.common.annotation.Unit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** Part as stored in the French PLM's native {@code piece} table. */
@Entity
@Table(name = "piece")
@OntologyClass("atelier:Part")
@Describe("Pièce : a part (assembly or section) managed in the French PLM")
public class Piece {

    @Id
    @Column(name = "ref_piece")
    @Describe("Référence pièce: the part's reference in the French PLM")
    @Maps("atelier:identifier")
    private String refPiece;

    @Column(name = "designation")
    @Describe("Désignation: human-readable part name")
    @Maps("atelier:label")
    private String designation;

    @Column(name = "fichier_cao")
    @Describe("Fichier CAO: the French PLM's own reference to the part's CAD file")
    @Maps("atelier:sourceFileRef")
    private String fichierCao;

    @Column(name = "indice")
    @Describe("Indice: the part's revision index in the French PLM's letter form (A, B)")
    @Accepts(pattern = "[A-Z]")
    @Maps("atelier:revision")
    private String indice;

    @Column(name = "etat")
    @Describe("État: the part's lifecycle state in French (En cours, Publié, Bloqué, Remplacé)")
    @Accepts({"En cours", "Publié", "Bloqué", "Remplacé"})
    @Maps("atelier:lifecycleLabel")
    private String etat;

    @Column(name = "masse_kg")
    @Describe("Masse: the part's mass in kilograms")
    @Unit("KiloGM")
    @Maps("atelier:mass")
    private BigDecimal masseKg;

    @Column(name = "matiere")
    @Describe("Matière: the part's material, named in French")
    @Maps("atelier:material")
    private String matiere;

    @Column(name = "type_piece")
    @Describe("Type de pièce: what the item is, PART, ASSEMBLY, SOFTWARE or DOCUMENT; only a PART has a CAD file")
    @Maps("atelier:partType")
    private String typePiece;

    @Column(name = "norme")
    @Describe("Norme : the standard the purchased item is bought to (NF EN ISO 4762); empty for a piece made in-house")
    @Maps("atelier:standard")
    private String norme;

    @Column(name = "diametre_nominal_mm")
    @Describe("Diamètre nominal : the purchased item's nominal (thread) diameter in millimetres")
    @Unit("MilliM")
    @Maps("atelier:nominalDiameter")
    private BigDecimal diametreNominalMm;

    @Column(name = "longueur_nominale_mm")
    @Describe("Longueur nominale : the purchased item's nominal length in millimetres; empty for a nut")
    @Unit("MilliM")
    @Maps("atelier:nominalLength")
    private BigDecimal longueurNominaleMm;

    @Column(name = "nombre_dents")
    @Describe("Nombre de dents : the number of teeth of a gear; empty for a part with no teeth or a cluster of several gears")
    @Maps("atelier:toothCount")
    private Integer nombreDents;

    @Column(name = "module_mm")
    @Describe("Module : the gear's module in millimetres (pitch diameter over tooth count); empty for a part with no teeth")
    @Unit("MilliM")
    @Maps("atelier:gearModule")
    private BigDecimal moduleMm;

    @Column(name = "travee_debut_mm")
    @Describe("Travée début: the lowest coordinate of the part along the product's station axis, in millimetres, computed from its CAD geometry; empty without stations")
    @Unit("MilliM")
    @Maps("atelier:spanFrom")
    private BigDecimal traveeDebutMm;

    @Column(name = "travee_fin_mm")
    @Describe("Travée fin: the highest coordinate of the part along the product's station axis, in millimetres")
    @Unit("MilliM")
    @Maps("atelier:spanTo")
    private BigDecimal traveeFinMm;

    @Column(name = "classe_article")
    @Describe("Classe d'article: the class of the purchased item (fastener, o-ring, placard, container, tyre, wheel, brake); empty for a part made in-house")
    @Maps("atelier:itemClass")
    private String classeArticle;

    @Column(name = "diametre_interieur_mm")
    @Describe("Diamètre intérieur: an O-ring's inner diameter in millimetres; empty when the standard gives the size")
    @Unit("MilliM")
    @Maps("atelier:innerDiameter")
    private BigDecimal diametreInterieurMm;

    @Column(name = "section_mm")
    @Describe("Section: an O-ring's cross-section in millimetres; empty when the standard gives the size")
    @Unit("MilliM")
    @Maps("atelier:crossSection")
    private BigDecimal sectionMm;

    @Column(name = "melange")
    @Describe("Mélange: an O-ring's elastomer compound with its hardness, in French")
    @Maps("atelier:compound")
    private String melange;

    @Column(name = "legende")
    @Describe("Légende: the text printed on a placard")
    @Maps("atelier:legend")
    private String legende;

    @Column(name = "largeur_mm")
    @Describe("Largeur: a placard's or a wheel's width in millimetres")
    @Unit("MilliM")
    @Maps("atelier:itemWidth")
    private BigDecimal largeurMm;

    @Column(name = "hauteur_mm")
    @Describe("Hauteur: a placard's or a container's height in millimetres")
    @Unit("MilliM")
    @Maps("atelier:itemHeight")
    private BigDecimal hauteurMm;

    @Column(name = "support")
    @Describe("Support: the material a placard is printed on, with its thickness, in French")
    @Maps("atelier:facestock")
    private String support;

    @Column(name = "adhesif")
    @Describe("Adhésif: a placard's adhesive, in French")
    @Maps("atelier:adhesive")
    private String adhesif;

    @Column(name = "largeur_base_mm")
    @Describe("Largeur de base: a container's base width in millimetres")
    @Unit("MilliM")
    @Maps("atelier:baseWidth")
    private BigDecimal largeurBaseMm;

    @Column(name = "profondeur_mm")
    @Describe("Profondeur: a container's depth in millimetres")
    @Unit("MilliM")
    @Maps("atelier:itemDepth")
    private BigDecimal profondeurMm;

    @Column(name = "largeur_contour_mm")
    @Describe("Largeur au contour: a container's width over its contour in millimetres")
    @Unit("MilliM")
    @Maps("atelier:contourWidth")
    private BigDecimal largeurContourMm;

    @Column(name = "materiau_coque")
    @Describe("Matériau de coque: a container's shell material, in French")
    @Maps("atelier:shellMaterial")
    private String materiauCoque;

    @Column(name = "diametre_exterieur_mm")
    @Describe("Diamètre extérieur: a tyre's outer diameter, in millimetres")
    @Unit("MilliM")
    @Maps("atelier:outerDiameter")
    private BigDecimal diametreExterieurMm;

    @Column(name = "largeur_section_mm")
    @Describe("Largeur de section: a tyre's section width, in millimetres")
    @Unit("MilliM")
    @Maps("atelier:sectionWidth")
    private BigDecimal largeurSectionMm;

    @Column(name = "diametre_jante_mm")
    @Describe("Diamètre de jante: a tyre's or a wheel's rim diameter, in millimetres")
    @Unit("MilliM")
    @Maps("atelier:rimDiameter")
    private BigDecimal diametreJanteMm;

    @Column(name = "indice_plis")
    @Describe("Indice de plis: a tyre's ply rating")
    @Maps("atelier:plyRating")
    private Integer indicePlis;

    @Column(name = "diametre_empilage_mm")
    @Describe("Diamètre d'empilage: a brake's heat stack diameter, in millimetres")
    @Unit("MilliM")
    @Maps("atelier:heatStackDiameter")
    private BigDecimal diametreEmpilageMm;

    @Column(name = "nombre_rotors")
    @Describe("Nombre de rotors: the rotors of a brake's heat stack")
    @Maps("atelier:rotorCount")
    private Integer nombreRotors;

    @Column(name = "duree_stockage_mois")
    @Describe("Durée de stockage: the item's shelf life in months; empty when the site states none")
    @Maps("atelier:shelfLifeMonths")
    private Integer dureeStockageMois;

    @Column(name = "variante", length = 64)
    @Describe("Variante: the code of the option under which alone this part exists; empty in the base configuration")
    @Maps("atelier:appliesUnderOption")
    private String variante;

    protected Piece() {
    }

    public String getRefPiece() {
        return refPiece;
    }

    public String getDesignation() {
        return designation;
    }

    public String getFichierCao() {
        return fichierCao;
    }

    public String getIndice() {
        return indice;
    }

    public String getEtat() {
        return etat;
    }

    public BigDecimal getMasseKg() {
        return masseKg;
    }

    public String getMatiere() {
        return matiere;
    }

    public String getTypePiece() {
        return typePiece;
    }

    public String getNorme() {
        return norme;
    }

    public BigDecimal getDiametreNominalMm() {
        return diametreNominalMm;
    }

    public BigDecimal getLongueurNominaleMm() {
        return longueurNominaleMm;
    }

    public Integer getNombreDents() {
        return nombreDents;
    }

    public BigDecimal getModuleMm() {
        return moduleMm;
    }

    public String getClasseArticle() {
        return classeArticle;
    }

    public BigDecimal getDiametreInterieurMm() {
        return diametreInterieurMm;
    }

    public BigDecimal getSectionMm() {
        return sectionMm;
    }

    public String getMelange() {
        return melange;
    }

    public String getLegende() {
        return legende;
    }

    public BigDecimal getLargeurMm() {
        return largeurMm;
    }

    public BigDecimal getHauteurMm() {
        return hauteurMm;
    }

    public String getSupport() {
        return support;
    }

    public String getAdhesif() {
        return adhesif;
    }

    public Integer getDureeStockageMois() {
        return dureeStockageMois;
    }

    public BigDecimal getLargeurBaseMm() {
        return largeurBaseMm;
    }

    public BigDecimal getProfondeurMm() {
        return profondeurMm;
    }

    public BigDecimal getLargeurContourMm() {
        return largeurContourMm;
    }

    public String getMateriauCoque() {
        return materiauCoque;
    }

    public BigDecimal getDiametreExterieurMm() {
        return diametreExterieurMm;
    }

    public BigDecimal getLargeurSectionMm() {
        return largeurSectionMm;
    }

    public BigDecimal getDiametreJanteMm() {
        return diametreJanteMm;
    }

    public Integer getIndicePlis() {
        return indicePlis;
    }

    public BigDecimal getDiametreEmpilageMm() {
        return diametreEmpilageMm;
    }

    public Integer getNombreRotors() {
        return nombreRotors;
    }

    public String getVariante() {
        return variante;
    }
}
