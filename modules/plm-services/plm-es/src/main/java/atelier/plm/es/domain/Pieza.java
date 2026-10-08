// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.es.domain;

import atelier.plm.common.annotation.Accepts;
import atelier.plm.common.annotation.Describe;
import atelier.plm.common.annotation.Maps;
import atelier.plm.common.annotation.OntologyClass;
import atelier.plm.common.annotation.ReadThrough;
import atelier.plm.common.annotation.Unit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

/** Part as stored in the Spanish PLM's native {@code pieza} table. */
@Entity
@Table(name = "pieza")
@OntologyClass("atelier:Part")
@Describe("Pieza: a part (assembly or section) managed in the Spanish PLM")
public class Pieza {

    @Id
    @Column(name = "cod_pieza")
    @Describe("Código de pieza: the part's code in the Spanish PLM")
    @Maps("atelier:identifier")
    private String codPieza;

    @Column(name = "denominacion")
    @Describe("Denominación: human-readable part name")
    @Maps("atelier:label")
    private String denominacion;

    @Column(name = "fichero_cad")
    @Describe("Fichero CAD: the Spanish PLM's own reference to the part's CAD file")
    @Maps("atelier:sourceFileRef")
    private String ficheroCad;

    @Column(name = "revision")
    @Describe("Revisión: the part's revision in the Spanish PLM, an integer (1, 2)")
    @Maps("atelier:revision")
    private Integer revision;

    @Column(name = "estado")
    @Describe("Estado: the part's lifecycle state in Spanish (Borrador, Liberado, Bloqueado, Sustituido)")
    @Accepts({"Borrador", "Liberado", "Bloqueado", "Sustituido"})
    @Maps("atelier:lifecycleLabel")
    private String estado;

    @Column(name = "masa_kg")
    @Describe("Masa: the part's mass in kilograms")
    @Unit("KiloGM")
    @Maps("atelier:mass")
    private BigDecimal masaKg;

    @Column(name = "material")
    @Describe("Material: the part's material, named in Spanish")
    @Maps("atelier:material")
    private String material;

    @Column(name = "tipo")
    @Describe("Tipo: what the item is, PART, ASSEMBLY, SOFTWARE or DOCUMENT; only a PART has a CAD file")
    @Maps("atelier:partType")
    private String tipo;

    @Column(name = "lista_materiales", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    @ReadThrough("linea_lista_materiales")
    @Describe("Lista de materiales: the piece's bill of materials as a versioned JSON document, {\"version\": n, \"lineas\": [{\"referencia\", \"cantidad\"}]}, on an assembly or the site kit; read as rows through the view linea_lista_materiales")
    private String listaMateriales;

    @Column(name = "norma")
    @Describe("Norma: the standard the purchased item is bought to (UNE-EN ISO 4762, ASME B18.3); empty for a piece made in-house")
    @Maps("atelier:standard")
    private String norma;

    @Column(name = "diametro_nominal")
    @Describe("Diámetro nominal: the purchased item's nominal (thread) diameter, in the unit of unidad_medida")
    @Unit(column = "unidad_medida", stores = {"MilliM", "IN"})
    @Maps("atelier:nominalDiameter")
    private BigDecimal diametroNominal;

    @Column(name = "longitud_nominal")
    @Describe("Longitud nominal: the purchased item's nominal length, in the unit of unidad_medida; empty for a nut")
    @Unit(column = "unidad_medida", stores = {"MilliM", "IN"})
    @Maps("atelier:nominalLength")
    private BigDecimal longitudNominal;

    @Column(name = "unidad_medida")
    @Describe("Unidad de medida: the unit of the nominal size, that of the catalogue the item is bought from, as a QUDT unit local name (MilliM, IN = inch)")
    private String unidadMedida;

    @Column(name = "numero_dientes")
    @Describe("Número de dientes: the number of teeth of a gear; empty for a part with no teeth or a cluster of several gears")
    @Maps("atelier:toothCount")
    private Integer numeroDientes;

    @Column(name = "modulo_mm")
    @Describe("Módulo: the gear's module in millimetres (pitch diameter over tooth count); empty for a part with no teeth")
    @Unit("MilliM")
    @Maps("atelier:gearModule")
    private BigDecimal moduloMm;

    @Column(name = "tramo_desde_mm")
    @Describe("Tramo desde: the lowest coordinate of the part along the product's station axis, in millimetres, computed from its CAD geometry; empty without stations")
    @Unit("MilliM")
    @Maps("atelier:spanFrom")
    private BigDecimal tramoDesdeMm;

    @Column(name = "tramo_hasta_mm")
    @Describe("Tramo hasta: the highest coordinate of the part along the product's station axis, in millimetres")
    @Unit("MilliM")
    @Maps("atelier:spanTo")
    private BigDecimal tramoHastaMm;

    @Column(name = "clase_articulo")
    @Describe("Clase de artículo: the class of the purchased item (fastener, o-ring, placard, container, tyre, wheel, brake); empty for a part made in-house")
    @Maps("atelier:itemClass")
    private String claseArticulo;

    @Column(name = "diametro_interior")
    @Describe("Diámetro interior: an O-ring's inner diameter, in the unit of unidad_medida; empty when the standard gives the size")
    @Unit(column = "unidad_medida", stores = {"MilliM", "IN"})
    @Maps("atelier:innerDiameter")
    private BigDecimal diametroInterior;

    @Column(name = "seccion")
    @Describe("Sección: an O-ring's cross-section, in the unit of unidad_medida; empty when the standard gives the size")
    @Unit(column = "unidad_medida", stores = {"MilliM", "IN"})
    @Maps("atelier:crossSection")
    private BigDecimal seccion;

    @Column(name = "compuesto")
    @Describe("Compuesto: an O-ring's elastomer compound with its hardness, in Spanish")
    @Maps("atelier:compound")
    private String compuesto;

    @Column(name = "leyenda")
    @Describe("Leyenda: the text printed on a placard")
    @Maps("atelier:legend")
    private String leyenda;

    @Column(name = "ancho")
    @Describe("Ancho: a placard's or a wheel's width, in the unit of unidad_medida")
    @Unit(column = "unidad_medida", stores = {"MilliM", "IN"})
    @Maps("atelier:itemWidth")
    private BigDecimal ancho;

    @Column(name = "alto")
    @Describe("Alto: a placard's or a container's height, in the unit of unidad_medida")
    @Unit(column = "unidad_medida", stores = {"MilliM", "IN"})
    @Maps("atelier:itemHeight")
    private BigDecimal alto;

    @Column(name = "soporte")
    @Describe("Soporte: the material a placard is printed on, with its thickness, in Spanish")
    @Maps("atelier:facestock")
    private String soporte;

    @Column(name = "adhesivo")
    @Describe("Adhesivo: a placard's adhesive, in Spanish")
    @Maps("atelier:adhesive")
    private String adhesivo;

    @Column(name = "ancho_base")
    @Describe("Ancho de base: a container's base width, in the unit of unidad_medida")
    @Unit(column = "unidad_medida", stores = {"MilliM", "IN"})
    @Maps("atelier:baseWidth")
    private BigDecimal anchoBase;

    @Column(name = "fondo")
    @Describe("Fondo: a container's depth, in the unit of unidad_medida")
    @Unit(column = "unidad_medida", stores = {"MilliM", "IN"})
    @Maps("atelier:itemDepth")
    private BigDecimal fondo;

    @Column(name = "ancho_contorno")
    @Describe("Ancho de contorno: a container's width over its contour, in the unit of unidad_medida")
    @Unit(column = "unidad_medida", stores = {"MilliM", "IN"})
    @Maps("atelier:contourWidth")
    private BigDecimal anchoContorno;

    @Column(name = "material_casco")
    @Describe("Material del casco: a container's shell material, in Spanish")
    @Maps("atelier:shellMaterial")
    private String materialCasco;

    @Column(name = "diametro_exterior")
    @Describe("Diámetro exterior: a tyre's outer diameter, in the unit of unidad_medida")
    @Unit(column = "unidad_medida", stores = {"MilliM", "IN"})
    @Maps("atelier:outerDiameter")
    private BigDecimal diametroExterior;

    @Column(name = "ancho_seccion")
    @Describe("Ancho de sección: a tyre's section width, in the unit of unidad_medida")
    @Unit(column = "unidad_medida", stores = {"MilliM", "IN"})
    @Maps("atelier:sectionWidth")
    private BigDecimal anchoSeccion;

    @Column(name = "diametro_llanta")
    @Describe("Diámetro de llanta: a tyre's or a wheel's rim diameter, in the unit of unidad_medida")
    @Unit(column = "unidad_medida", stores = {"MilliM", "IN"})
    @Maps("atelier:rimDiameter")
    private BigDecimal diametroLlanta;

    @Column(name = "indice_telas")
    @Describe("Índice de telas: a tyre's ply rating")
    @Maps("atelier:plyRating")
    private Integer indiceTelas;

    @Column(name = "diametro_disipador")
    @Describe("Diámetro del disipador: a brake's heat stack diameter, in the unit of unidad_medida")
    @Unit(column = "unidad_medida", stores = {"MilliM", "IN"})
    @Maps("atelier:heatStackDiameter")
    private BigDecimal diametroDisipador;

    @Column(name = "numero_rotores")
    @Describe("Número de rotores: the rotors of a brake's heat stack")
    @Maps("atelier:rotorCount")
    private Integer numeroRotores;

    @Column(name = "vida_util_meses")
    @Describe("Vida útil: the item's shelf life in months; empty when the site states none")
    @Maps("atelier:shelfLifeMonths")
    private Integer vidaUtilMeses;

    @Column(name = "opcion", length = 64)
    @Describe("Opción: the code of the option under which alone this part exists; empty in the base configuration")
    @Maps("atelier:appliesUnderOption")
    private String opcion;

    protected Pieza() {
    }

    public String getCodPieza() {
        return codPieza;
    }

    public String getDenominacion() {
        return denominacion;
    }

    public String getFicheroCad() {
        return ficheroCad;
    }

    public Integer getRevision() {
        return revision;
    }

    public String getEstado() {
        return estado;
    }

    public BigDecimal getMasaKg() {
        return masaKg;
    }

    public String getMaterial() {
        return material;
    }

    public String getTipo() {
        return tipo;
    }

    public String getListaMateriales() {
        return listaMateriales;
    }

    public String getNorma() {
        return norma;
    }

    public BigDecimal getDiametroNominal() {
        return diametroNominal;
    }

    public BigDecimal getLongitudNominal() {
        return longitudNominal;
    }

    public String getUnidadMedida() {
        return unidadMedida;
    }

    public Integer getNumeroDientes() {
        return numeroDientes;
    }

    public BigDecimal getModuloMm() {
        return moduloMm;
    }

    public String getClaseArticulo() {
        return claseArticulo;
    }

    public BigDecimal getDiametroInterior() {
        return diametroInterior;
    }

    public BigDecimal getSeccion() {
        return seccion;
    }

    public String getCompuesto() {
        return compuesto;
    }

    public String getLeyenda() {
        return leyenda;
    }

    public BigDecimal getAncho() {
        return ancho;
    }

    public BigDecimal getAlto() {
        return alto;
    }

    public String getSoporte() {
        return soporte;
    }

    public String getAdhesivo() {
        return adhesivo;
    }

    public Integer getVidaUtilMeses() {
        return vidaUtilMeses;
    }

    public BigDecimal getAnchoBase() {
        return anchoBase;
    }

    public BigDecimal getFondo() {
        return fondo;
    }

    public BigDecimal getAnchoContorno() {
        return anchoContorno;
    }

    public String getMaterialCasco() {
        return materialCasco;
    }

    public BigDecimal getDiametroExterior() {
        return diametroExterior;
    }

    public BigDecimal getAnchoSeccion() {
        return anchoSeccion;
    }

    public BigDecimal getDiametroLlanta() {
        return diametroLlanta;
    }

    public Integer getIndiceTelas() {
        return indiceTelas;
    }

    public BigDecimal getDiametroDisipador() {
        return diametroDisipador;
    }

    public Integer getNumeroRotores() {
        return numeroRotores;
    }

    public String getOpcion() {
        return opcion;
    }
}
