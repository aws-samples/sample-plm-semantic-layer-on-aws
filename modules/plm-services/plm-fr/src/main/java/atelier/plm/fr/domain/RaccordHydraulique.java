// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.fr.domain;

import atelier.plm.common.annotation.Describe;
import atelier.plm.common.annotation.Maps;
import atelier.plm.common.annotation.OntologyClass;
import atelier.plm.common.annotation.Unit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** Hydraulic line coupling as stored in the French PLM's native {@code raccord_hydraulique} table. */
@Entity
@Table(name = "raccord_hydraulique")
@OntologyClass("atelier:HydraulicCoupling")
@Describe("Raccord hydraulique: a hydraulic line coupling half carried by a part at a joint face")
public class RaccordHydraulique {

    @Id
    @Column(name = "ref_raccord")
    @Describe("Référence raccord: the coupling's key in the French PLM")
    @Maps("atelier:identifier")
    private String refRaccord;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ref_piece")
    @Describe("Part carrying the coupling")
    @Maps("atelier:onPart")
    private Piece piece;

    @Column(name = "pos_x_mm", precision = 12, scale = 3)
    @Describe("Coupling position along the product X axis")
    @Unit("MilliM")
    @Maps("atelier:positionX")
    private BigDecimal posXMm;

    @Column(name = "pos_y_mm", precision = 12, scale = 3)
    @Describe("Coupling position along the product Y axis")
    @Unit("MilliM")
    @Maps("atelier:positionY")
    private BigDecimal posYMm;

    @Column(name = "pos_z_mm", precision = 12, scale = 3)
    @Describe("Coupling position along the product Z axis")
    @Unit("MilliM")
    @Maps("atelier:positionZ")
    private BigDecimal posZMm;

    @Column(name = "norme")
    @Describe("Norme: coupling specification (e.g. AS4395 flareless fitting)")
    @Maps("atelier:couplingStandard")
    private String norme;

    @Column(name = "taille_dash")
    @Describe("Taille dash: tube dash size in sixteenths of an inch (6 = 3/8 in, 8 = 1/2 in)")
    @Maps("atelier:dashSize")
    private Integer tailleDash;

    @Column(name = "pression_bar", precision = 10, scale = 3)
    @Describe("Pression: nominal working pressure")
    @Unit("BAR")
    @Maps("atelier:pressureRating")
    private BigDecimal pressionBar;

    @Column(name = "fluide")
    @Describe("Fluide: hydraulic fluid the line carries")
    @Maps("atelier:fluid")
    private String fluide;

    @Column(name = "point_raccordement", length = 64)
    @Describe("Point de raccordement: the connection point of the host part this feature serves, the same under every option")
    @Maps("atelier:port")
    private String pointRaccordement;

    @Column(name = "variante", length = 64)
    @Describe("Variante: the code of the option under which alone this feature exists; empty in the base configuration")
    @Maps("atelier:appliesUnderOption")
    private String variante;

    protected RaccordHydraulique() {
    }

    public String getRefRaccord() {
        return refRaccord;
    }

    public Piece getPiece() {
        return piece;
    }

    public BigDecimal getPosXMm() {
        return posXMm;
    }

    public BigDecimal getPosYMm() {
        return posYMm;
    }

    public BigDecimal getPosZMm() {
        return posZMm;
    }

    public String getNorme() {
        return norme;
    }

    public Integer getTailleDash() {
        return tailleDash;
    }

    public BigDecimal getPressionBar() {
        return pressionBar;
    }

    public String getFluide() {
        return fluide;
    }

    public String getPointRaccordement() {
        return pointRaccordement;
    }

    public String getVariante() {
        return variante;
    }
}
