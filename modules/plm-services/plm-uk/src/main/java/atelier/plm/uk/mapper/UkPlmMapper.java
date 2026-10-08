// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.uk.mapper;

import atelier.plm.common.dto.PlugDto;
import atelier.plm.uk.domain.Component;
import atelier.plm.uk.domain.Fastener;
import atelier.plm.uk.domain.HarnessConnector;
import atelier.plm.uk.domain.HydCoupling;
import atelier.plm.uk.dto.CouplingDto;
import atelier.plm.uk.dto.FastenerDto;
import atelier.plm.uk.dto.PartDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/** Maps the British native entities to the PLM-neutral DTOs; quantities and their units are passed through as stored (inches and psi, units from the *_uom columns). */
@Mapper
public interface UkPlmMapper {

    @Mapping(target = "id", source = "compId")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "cadFile", source = "cadFile")
    @Mapping(target = "plm", constant = "UK")
    PartDto toPart(Component component);

    List<PartDto> toParts(List<Component> components);

    @Mapping(target = "id", source = "connRef")
    @Mapping(target = "partId", source = "component.compId")
    @Mapping(target = "x", source = "posX")
    @Mapping(target = "y", source = "posY")
    @Mapping(target = "z", source = "posZ")
    @Mapping(target = "unit", source = "posUom")
    @Mapping(target = "connectorType", source = "shellType")
    @Mapping(target = "pinCount", source = "pinQty")
    @Mapping(target = "plm", constant = "UK")
    PlugDto toPlug(HarnessConnector harnessConnector);

    List<PlugDto> toPlugs(List<HarnessConnector> harnessConnectors);

    @Mapping(target = "id", source = "fastRef")
    @Mapping(target = "partId", source = "component.compId")
    @Mapping(target = "x", source = "posX")
    @Mapping(target = "y", source = "posY")
    @Mapping(target = "z", source = "posZ")
    @Mapping(target = "unit", source = "posUom")
    @Mapping(target = "standard", source = "standard")
    @Mapping(target = "diameter", source = "dia")
    @Mapping(target = "diameterUnit", source = "diaUom")
    @Mapping(target = "count", source = "qty")
    @Mapping(target = "gripLength", source = "grip")
    @Mapping(target = "gripUnit", source = "gripUom")
    @Mapping(target = "plm", constant = "UK")
    FastenerDto toFastener(Fastener fastener);

    List<FastenerDto> toFasteners(List<Fastener> fasteners);

    @Mapping(target = "id", source = "cplgRef")
    @Mapping(target = "partId", source = "component.compId")
    @Mapping(target = "x", source = "posX")
    @Mapping(target = "y", source = "posY")
    @Mapping(target = "z", source = "posZ")
    @Mapping(target = "unit", source = "posUom")
    @Mapping(target = "standard", source = "standard")
    @Mapping(target = "dashSize", source = "dash")
    @Mapping(target = "rating", source = "rating")
    @Mapping(target = "ratingUnit", source = "ratingUom")
    @Mapping(target = "fluid", source = "fluid")
    @Mapping(target = "plm", constant = "UK")
    CouplingDto toCoupling(HydCoupling hydCoupling);

    List<CouplingDto> toCouplings(List<HydCoupling> hydCouplings);
}
