// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.es.mapper;

import atelier.plm.common.dto.PlugDto;
import atelier.plm.es.domain.Acoplamiento;
import atelier.plm.es.domain.Conector;
import atelier.plm.es.domain.Pieza;
import atelier.plm.es.domain.Remache;
import atelier.plm.es.dto.CouplingDto;
import atelier.plm.es.dto.FastenerDto;
import atelier.plm.es.dto.PartDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/** Maps the Spanish native entities to the PLM-neutral DTOs; coordinates and lengths stay in millimetres, pressures in bar. */
@Mapper
public interface EsPlmMapper {

    @Mapping(target = "id", source = "codPieza")
    @Mapping(target = "name", source = "denominacion")
    @Mapping(target = "cadFile", source = "ficheroCad")
    @Mapping(target = "plm", constant = "ES")
    PartDto toPart(Pieza pieza);

    List<PartDto> toParts(List<Pieza> piezas);

    @Mapping(target = "id", source = "codConector")
    @Mapping(target = "partId", source = "pieza.codPieza")
    @Mapping(target = "x", source = "posXMm")
    @Mapping(target = "y", source = "posYMm")
    @Mapping(target = "z", source = "posZMm")
    @Mapping(target = "unit", constant = "MilliM")
    @Mapping(target = "connectorType", source = "tipo")
    @Mapping(target = "pinCount", source = "numContactos")
    @Mapping(target = "plm", constant = "ES")
    PlugDto toPlug(Conector conector);

    List<PlugDto> toPlugs(List<Conector> conectors);

    @Mapping(target = "id", source = "codRemache")
    @Mapping(target = "partId", source = "pieza.codPieza")
    @Mapping(target = "x", source = "posXMm")
    @Mapping(target = "y", source = "posYMm")
    @Mapping(target = "z", source = "posZMm")
    @Mapping(target = "unit", constant = "MilliM")
    @Mapping(target = "standard", source = "norma")
    @Mapping(target = "diameter", source = "diametroMm")
    @Mapping(target = "diameterUnit", constant = "MilliM")
    @Mapping(target = "count", source = "cantidad")
    @Mapping(target = "gripLength", source = "longitudAprieteMm")
    @Mapping(target = "gripUnit", constant = "MilliM")
    @Mapping(target = "plm", constant = "ES")
    FastenerDto toFastener(Remache remache);

    List<FastenerDto> toFasteners(List<Remache> remaches);

    @Mapping(target = "id", source = "codAcoplamiento")
    @Mapping(target = "partId", source = "pieza.codPieza")
    @Mapping(target = "x", source = "posXMm")
    @Mapping(target = "y", source = "posYMm")
    @Mapping(target = "z", source = "posZMm")
    @Mapping(target = "unit", constant = "MilliM")
    @Mapping(target = "standard", source = "norma")
    @Mapping(target = "dashSize", source = "tamanoDash")
    @Mapping(target = "rating", source = "presionBar")
    @Mapping(target = "ratingUnit", constant = "BAR")
    @Mapping(target = "fluid", source = "fluido")
    @Mapping(target = "plm", constant = "ES")
    CouplingDto toCoupling(Acoplamiento acoplamiento);

    List<CouplingDto> toCouplings(List<Acoplamiento> acoplamientos);
}
