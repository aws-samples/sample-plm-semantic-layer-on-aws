// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.de.domain;

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

/** Part as stored in the German PLM's native {@code bauteil} table. */
@Entity
@Table(name = "bauteil")
@OntologyClass("atelier:Part")
@Describe("Bauteil: a part (assembly or section) managed in the German PLM")
public class Bauteil {

    @Id
    @Column(name = "teil_nr")
    @Describe("Teilenummer: the part's number in the German PLM")
    @Maps("atelier:identifier")
    private String teilNr;

    @Column(name = "benennung")
    @Describe("Benennung: human-readable part name")
    @Maps("atelier:label")
    private String benennung;

    @Column(name = "cad_datei")
    @Describe("CAD-Datei: the German PLM's own reference to the part's CAD file")
    @Maps("atelier:sourceFileRef")
    private String cadDatei;

    @Column(name = "revision")
    @Describe("Revision: the part's revision in the German PLM's two-digit form (01, 02)")
    @Accepts(pattern = "\\d{2}")
    @Maps("atelier:revision")
    private String revision;

    @Column(name = "status")
    @Describe("Status: the part's lifecycle state in German (In Arbeit, Freigegeben, Gesperrt, Ersetzt)")
    @Accepts({"In Arbeit", "Freigegeben", "Gesperrt", "Ersetzt"})
    @Maps("atelier:lifecycleLabel")
    private String status;

    @Column(name = "masse_kg")
    @Describe("Masse: the part's mass in kilograms")
    @Unit("KiloGM")
    @Maps("atelier:mass")
    private BigDecimal masseKg;

    @Column(name = "werkstoff")
    @Describe("Werkstoff: the part's material, named in German")
    @Maps("atelier:material")
    private String werkstoff;

    @Column(name = "teileart")
    @Describe("Teileart: what the item is, PART, ASSEMBLY, SOFTWARE or DOCUMENT; only a PART has a CAD file")
    @Maps("atelier:partType")
    private String teileart;

    @Column(name = "parent_id")
    @Describe("Übergeordnetes Teil: the assembly or site kit this item is used in, empty for a site kit; the German PLM's bill of materials is a tree on the part row, so an item has at most one parent")
    @Maps("atelier:parent")
    private String parentId;

    @Column(name = "menge")
    @Describe("Menge: how many of this item its parent uses")
    @Maps("atelier:quantity")
    private BigDecimal menge;

    @Column(name = "norm")
    @Describe("Norm: the standard the purchased item is bought to (DIN 912, ISO 4762); empty for a part made in-house")
    @Maps("atelier:standard")
    private String norm;

    @Column(name = "nenndurchmesser_mm")
    @Describe("Nenndurchmesser: the purchased item's nominal (thread) diameter in millimetres")
    @Unit("MilliM")
    @Maps("atelier:nominalDiameter")
    private BigDecimal nenndurchmesserMm;

    @Column(name = "nennlaenge_mm")
    @Describe("Nennlänge: the purchased item's nominal length in millimetres; empty for a nut")
    @Unit("MilliM")
    @Maps("atelier:nominalLength")
    private BigDecimal nennlaengeMm;

    @Column(name = "zaehnezahl")
    @Describe("Zähnezahl: the number of teeth of a gear; empty for an item with no teeth or a cluster of several gears")
    @Maps("atelier:toothCount")
    private Integer zaehnezahl;

    @Column(name = "modul_mm")
    @Describe("Modul: the gear's module in millimetres (pitch diameter over tooth count); empty for an item with no teeth")
    @Unit("MilliM")
    @Maps("atelier:gearModule")
    private BigDecimal modulMm;

    @Column(name = "spanne_von_mm")
    @Describe("Spanne von: the lowest coordinate of the part along the product's station axis, in millimetres, computed from its CAD geometry; empty without stations")
    @Unit("MilliM")
    @Maps("atelier:spanFrom")
    private BigDecimal spanneVonMm;

    @Column(name = "spanne_bis_mm")
    @Describe("Spanne bis: the highest coordinate of the part along the product's station axis, in millimetres")
    @Unit("MilliM")
    @Maps("atelier:spanTo")
    private BigDecimal spanneBisMm;

    @Column(name = "teileklasse")
    @Describe("Teileklasse: the class of the purchased item (fastener, o-ring, placard, container, tyre, wheel, brake); empty for a part made in-house")
    @Maps("atelier:itemClass")
    private String teileklasse;

    @Column(name = "innendurchmesser_mm")
    @Describe("Innendurchmesser: an O-ring's inner diameter in millimetres; empty when the standard gives the size")
    @Unit("MilliM")
    @Maps("atelier:innerDiameter")
    private BigDecimal innendurchmesserMm;

    @Column(name = "schnurstaerke_mm")
    @Describe("Schnurstärke: an O-ring's cross-section in millimetres; empty when the standard gives the size")
    @Unit("MilliM")
    @Maps("atelier:crossSection")
    private BigDecimal schnurstaerkeMm;

    @Column(name = "mischung")
    @Describe("Mischung: an O-ring's elastomer compound with its hardness, in German")
    @Maps("atelier:compound")
    private String mischung;

    @Column(name = "beschriftung")
    @Describe("Beschriftung: the text printed on a placard")
    @Maps("atelier:legend")
    private String beschriftung;

    @Column(name = "breite_mm")
    @Describe("Breite: a placard's or a wheel's width in millimetres")
    @Unit("MilliM")
    @Maps("atelier:itemWidth")
    private BigDecimal breiteMm;

    @Column(name = "hoehe_mm")
    @Describe("Höhe: a placard's or a container's height in millimetres")
    @Unit("MilliM")
    @Maps("atelier:itemHeight")
    private BigDecimal hoeheMm;

    @Column(name = "traegerwerkstoff")
    @Describe("Trägerwerkstoff: the material a placard is printed on, with its thickness, in German")
    @Maps("atelier:facestock")
    private String traegerwerkstoff;

    @Column(name = "klebstoff")
    @Describe("Klebstoff: a placard's adhesive, in German")
    @Maps("atelier:adhesive")
    private String klebstoff;

    @Column(name = "bodenbreite_mm")
    @Describe("Bodenbreite: a container's base width in millimetres")
    @Unit("MilliM")
    @Maps("atelier:baseWidth")
    private BigDecimal bodenbreiteMm;

    @Column(name = "tiefe_mm")
    @Describe("Tiefe: a container's depth in millimetres")
    @Unit("MilliM")
    @Maps("atelier:itemDepth")
    private BigDecimal tiefeMm;

    @Column(name = "konturbreite_mm")
    @Describe("Konturbreite: a container's width over its contour in millimetres")
    @Unit("MilliM")
    @Maps("atelier:contourWidth")
    private BigDecimal konturbreiteMm;

    @Column(name = "schalenwerkstoff")
    @Describe("Schalenwerkstoff: a container's shell material, in German")
    @Maps("atelier:shellMaterial")
    private String schalenwerkstoff;

    @Column(name = "aussendurchmesser_mm")
    @Describe("Außendurchmesser: a tyre's outer diameter, in millimetres")
    @Unit("MilliM")
    @Maps("atelier:outerDiameter")
    private BigDecimal aussendurchmesserMm;

    @Column(name = "querschnittsbreite_mm")
    @Describe("Querschnittsbreite: a tyre's section width, in millimetres")
    @Unit("MilliM")
    @Maps("atelier:sectionWidth")
    private BigDecimal querschnittsbreiteMm;

    @Column(name = "felgendurchmesser_mm")
    @Describe("Felgendurchmesser: a tyre's or a wheel's rim diameter, in millimetres")
    @Unit("MilliM")
    @Maps("atelier:rimDiameter")
    private BigDecimal felgendurchmesserMm;

    @Column(name = "lagenzahl")
    @Describe("Lagenzahl: a tyre's ply rating")
    @Maps("atelier:plyRating")
    private Integer lagenzahl;

    @Column(name = "waermesenkendurchmesser_mm")
    @Describe("Wärmesenkendurchmesser: a brake's heat stack diameter, in millimetres")
    @Unit("MilliM")
    @Maps("atelier:heatStackDiameter")
    private BigDecimal waermesenkendurchmesserMm;

    @Column(name = "rotorzahl")
    @Describe("Rotorzahl: the rotors of a brake's heat stack")
    @Maps("atelier:rotorCount")
    private Integer rotorzahl;

    @Column(name = "lagerdauer_monate")
    @Describe("Lagerdauer: the item's shelf life in months; empty when the site states none")
    @Maps("atelier:shelfLifeMonths")
    private Integer lagerdauerMonate;

    @Column(name = "variante", length = 64)
    @Describe("Variante: the code of the option under which alone this part exists; empty in the base configuration")
    @Maps("atelier:appliesUnderOption")
    private String variante;

    protected Bauteil() {
    }

    public String getTeilNr() {
        return teilNr;
    }

    public String getBenennung() {
        return benennung;
    }

    public String getCadDatei() {
        return cadDatei;
    }

    public String getRevision() {
        return revision;
    }

    public String getStatus() {
        return status;
    }

    public BigDecimal getMasseKg() {
        return masseKg;
    }

    public String getWerkstoff() {
        return werkstoff;
    }

    public String getTeileart() {
        return teileart;
    }

    public String getParentId() {
        return parentId;
    }

    public BigDecimal getMenge() {
        return menge;
    }

    public String getNorm() {
        return norm;
    }

    public BigDecimal getNenndurchmesserMm() {
        return nenndurchmesserMm;
    }

    public BigDecimal getNennlaengeMm() {
        return nennlaengeMm;
    }

    public Integer getZaehnezahl() {
        return zaehnezahl;
    }

    public BigDecimal getModulMm() {
        return modulMm;
    }

    public String getTeileklasse() {
        return teileklasse;
    }

    public BigDecimal getInnendurchmesserMm() {
        return innendurchmesserMm;
    }

    public BigDecimal getSchnurstaerkeMm() {
        return schnurstaerkeMm;
    }

    public String getMischung() {
        return mischung;
    }

    public String getBeschriftung() {
        return beschriftung;
    }

    public BigDecimal getBreiteMm() {
        return breiteMm;
    }

    public BigDecimal getHoeheMm() {
        return hoeheMm;
    }

    public String getTraegerwerkstoff() {
        return traegerwerkstoff;
    }

    public String getKlebstoff() {
        return klebstoff;
    }

    public Integer getLagerdauerMonate() {
        return lagerdauerMonate;
    }

    public BigDecimal getBodenbreiteMm() {
        return bodenbreiteMm;
    }

    public BigDecimal getTiefeMm() {
        return tiefeMm;
    }

    public BigDecimal getKonturbreiteMm() {
        return konturbreiteMm;
    }

    public String getSchalenwerkstoff() {
        return schalenwerkstoff;
    }

    public BigDecimal getAussendurchmesserMm() {
        return aussendurchmesserMm;
    }

    public BigDecimal getQuerschnittsbreiteMm() {
        return querschnittsbreiteMm;
    }

    public BigDecimal getFelgendurchmesserMm() {
        return felgendurchmesserMm;
    }

    public Integer getLagenzahl() {
        return lagenzahl;
    }

    public BigDecimal getWaermesenkendurchmesserMm() {
        return waermesenkendurchmesserMm;
    }

    public Integer getRotorzahl() {
        return rotorzahl;
    }

    public String getVariante() {
        return variante;
    }
}
