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

/** Fastener set on a joint face as stored in the French PLM's native {@code fixation} table. */
@Entity
@Table(name = "fixation")
@OntologyClass("atelier:Fastener")
@Describe("Fixation: a set of identical fasteners (bolts or rivets) on one joint face of a part")
public class Fixation {

    @Id
    @Column(name = "ref_fixation")
    @Describe("Référence fixation: the fastener set's key in the French PLM")
    @Maps("atelier:identifier")
    private String refFixation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ref_piece")
    @Describe("Part carrying the fasteners")
    @Maps("atelier:onPart")
    private Piece piece;

    @Column(name = "pos_x_mm", precision = 12, scale = 3)
    @Describe("Fastener set position along the product X axis")
    @Unit("MilliM")
    @Maps("atelier:positionX")
    private BigDecimal posXMm;

    @Column(name = "pos_y_mm", precision = 12, scale = 3)
    @Describe("Fastener set position along the product Y axis")
    @Unit("MilliM")
    @Maps("atelier:positionY")
    private BigDecimal posYMm;

    @Column(name = "pos_z_mm", precision = 12, scale = 3)
    @Describe("Fastener set position along the product Z axis")
    @Unit("MilliM")
    @Maps("atelier:positionZ")
    private BigDecimal posZMm;

    @Column(name = "norme")
    @Describe("Norme: fastener specification (e.g. EN6115 Hi-Lok, NAS1097 rivet)")
    @Maps("atelier:fastenerStandard")
    private String norme;

    @Column(name = "diametre_mm", precision = 8, scale = 3)
    @Describe("Diamètre: nominal shank diameter of one fastener")
    @Unit("MilliM")
    @Maps("atelier:diameter")
    private BigDecimal diametreMm;

    @Column(name = "nombre")
    @Describe("Nombre: number of fasteners in the set")
    @Maps("atelier:fastenerCount")
    private Integer nombre;

    @Column(name = "longueur_serrage_mm", precision = 8, scale = 3)
    @Describe("Longueur de serrage: grip length of one fastener")
    @Unit("MilliM")
    @Maps("atelier:gripLength")
    private BigDecimal longueurSerrageMm;

    @Column(name = "point_raccordement", length = 64)
    @Describe("Point de raccordement: the connection point of the host part this feature serves, the same under every option")
    @Maps("atelier:port")
    private String pointRaccordement;

    @Column(name = "variante", length = 64)
    @Describe("Variante: the code of the option under which alone this feature exists; empty in the base configuration")
    @Maps("atelier:appliesUnderOption")
    private String variante;

    protected Fixation() {
    }

    public String getRefFixation() {
        return refFixation;
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

    public BigDecimal getDiametreMm() {
        return diametreMm;
    }

    public Integer getNombre() {
        return nombre;
    }

    public BigDecimal getLongueurSerrageMm() {
        return longueurSerrageMm;
    }

    public String getPointRaccordement() {
        return pointRaccordement;
    }

    public String getVariante() {
        return variante;
    }
}
