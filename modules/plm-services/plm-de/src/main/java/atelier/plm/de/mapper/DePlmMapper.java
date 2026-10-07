// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.de.mapper;

import atelier.plm.common.dto.PlugDto;
import atelier.plm.de.domain.Bauteil;
import atelier.plm.de.domain.Befestiger;
import atelier.plm.de.domain.Hydraulikkupplung;
import atelier.plm.de.domain.Stecker;
import atelier.plm.de.dto.CouplingDto;
import atelier.plm.de.dto.FastenerDto;
import atelier.plm.de.dto.PartDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/** Maps the German native entities to the PLM-neutral DTOs; coordinates and lengths stay in millimetres, pressures in bar. */
@Mapper
public interface DePlmMapper {

    @Mapping(target = "id", source = "teilNr")
    @Mapping(target = "name", source = "benennung")
    @Mapping(target = "cadFile", source = "cadDatei")
    @Mapping(target = "plm", constant = "DE")
    PartDto toPart(Bauteil bauteil);

    List<PartDto> toParts(List<Bauteil> bauteils);

    @Mapping(target = "id", source = "steckerId")
    @Mapping(target = "partId", source = "bauteil.teilNr")
    @Mapping(target = "x", source = "posXMm")
    @Mapping(target = "y", source = "posYMm")
    @Mapping(target = "z", source = "posZMm")
    @Mapping(target = "unit", constant = "MilliM")
    @Mapping(target = "connectorType", source = "typ")
    @Mapping(target = "pinCount", source = "polzahl")
    @Mapping(target = "plm", constant = "DE")
    PlugDto toPlug(Stecker stecker);

    List<PlugDto> toPlugs(List<Stecker> steckers);

    @Mapping(target = "id", source = "befestigerId")
    @Mapping(target = "partId", source = "bauteil.teilNr")
    @Mapping(target = "x", source = "posXMm")
    @Mapping(target = "y", source = "posYMm")
    @Mapping(target = "z", source = "posZMm")
    @Mapping(target = "unit", constant = "MilliM")
    @Mapping(target = "standard", source = "norm")
    @Mapping(target = "diameter", source = "durchmesserMm")
    @Mapping(target = "diameterUnit", constant = "MilliM")
    @Mapping(target = "count", source = "anzahl")
    @Mapping(target = "gripLength", source = "klemmlaengeMm")
    @Mapping(target = "gripUnit", constant = "MilliM")
    @Mapping(target = "plm", constant = "DE")
    FastenerDto toFastener(Befestiger befestiger);

    List<FastenerDto> toFasteners(List<Befestiger> befestiger);

    @Mapping(target = "id", source = "kupplungId")
    @Mapping(target = "partId", source = "bauteil.teilNr")
    @Mapping(target = "x", source = "posXMm")
    @Mapping(target = "y", source = "posYMm")
    @Mapping(target = "z", source = "posZMm")
    @Mapping(target = "unit", constant = "MilliM")
    @Mapping(target = "standard", source = "norm")
    @Mapping(target = "dashSize", source = "dashGroesse")
    @Mapping(target = "rating", source = "nenndruckBar")
    @Mapping(target = "ratingUnit", constant = "BAR")
    @Mapping(target = "fluid", source = "fluid")
    @Mapping(target = "plm", constant = "DE")
    CouplingDto toCoupling(Hydraulikkupplung hydraulikkupplung);

    List<CouplingDto> toCouplings(List<Hydraulikkupplung> hydraulikkupplungen);
}
