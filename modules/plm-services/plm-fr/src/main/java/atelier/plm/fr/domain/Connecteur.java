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

/** Electrical connector as stored in the French PLM's native {@code connecteur} table. */
@Entity
@Table(name = "connecteur")
@OntologyClass("atelier:Plug")
@Describe("Connecteur: an electrical connector carried by a part")
public class Connecteur {

    @Id
    @Column(name = "id_connecteur")
    @Describe("Identifiant connecteur: the connector's key in the French PLM")
    @Maps("atelier:identifier")
    private String idConnecteur;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ref_piece")
    @Describe("Part carrying the connector")
    @Maps("atelier:onPart")
    private Piece piece;

    @Column(name = "pos_x_mm", precision = 12, scale = 3)
    @Describe("Connector position along the product X axis")
    @Unit("MilliM")
    @Maps("atelier:positionX")
    private BigDecimal posXMm;

    @Column(name = "pos_y_mm", precision = 12, scale = 3)
    @Describe("Connector position along the product Y axis")
    @Unit("MilliM")
    @Maps("atelier:positionY")
    private BigDecimal posYMm;

    @Column(name = "pos_z_mm", precision = 12, scale = 3)
    @Describe("Connector position along the product Z axis")
    @Unit("MilliM")
    @Maps("atelier:positionZ")
    private BigDecimal posZMm;

    @Column(name = "type_connecteur")
    @Describe("Type connecteur: connector standard / part number")
    @Maps("atelier:connectorType")
    private String typeConnecteur;

    @Column(name = "nb_broches")
    @Describe("Nombre de broches: pin count")
    @Maps("atelier:pinCount")
    private Integer nbBroches;

    @Column(name = "point_raccordement", length = 64)
    @Describe("Point de raccordement: the connection point of the host part this feature serves, the same under every option")
    @Maps("atelier:port")
    private String pointRaccordement;

    @Column(name = "variante", length = 64)
    @Describe("Variante: the code of the option under which alone this feature exists; empty in the base configuration")
    @Maps("atelier:appliesUnderOption")
    private String variante;

    protected Connecteur() {
    }

    public String getIdConnecteur() {
        return idConnecteur;
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

    public String getTypeConnecteur() {
        return typeConnecteur;
    }

    public Integer getNbBroches() {
        return nbBroches;
    }

    public String getPointRaccordement() {
        return pointRaccordement;
    }

    public String getVariante() {
        return variante;
    }
}
