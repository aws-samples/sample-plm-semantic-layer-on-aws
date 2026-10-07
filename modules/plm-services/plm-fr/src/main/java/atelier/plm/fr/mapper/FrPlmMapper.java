// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.fr.mapper;

import atelier.plm.common.dto.PlugDto;
import atelier.plm.fr.domain.Connecteur;
import atelier.plm.fr.domain.Fixation;
import atelier.plm.fr.domain.Piece;
import atelier.plm.fr.domain.RaccordHydraulique;
import atelier.plm.fr.dto.CouplingDto;
import atelier.plm.fr.dto.FastenerDto;
import atelier.plm.fr.dto.PartDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/** Maps the French native entities to the PLM-neutral DTOs; coordinates and lengths stay in millimetres, pressures in bar. */
@Mapper
public interface FrPlmMapper {

    @Mapping(target = "id", source = "refPiece")
    @Mapping(target = "name", source = "designation")
    @Mapping(target = "cadFile", source = "fichierCao")
    @Mapping(target = "plm", constant = "FR")
    PartDto toPart(Piece piece);

    List<PartDto> toParts(List<Piece> pieces);

    @Mapping(target = "id", source = "idConnecteur")
    @Mapping(target = "partId", source = "piece.refPiece")
    @Mapping(target = "x", source = "posXMm")
    @Mapping(target = "y", source = "posYMm")
    @Mapping(target = "z", source = "posZMm")
    @Mapping(target = "unit", constant = "MilliM")
    @Mapping(target = "connectorType", source = "typeConnecteur")
    @Mapping(target = "pinCount", source = "nbBroches")
    @Mapping(target = "plm", constant = "FR")
    PlugDto toPlug(Connecteur connecteur);

    List<PlugDto> toPlugs(List<Connecteur> connecteurs);

    @Mapping(target = "id", source = "refFixation")
    @Mapping(target = "partId", source = "piece.refPiece")
    @Mapping(target = "x", source = "posXMm")
    @Mapping(target = "y", source = "posYMm")
    @Mapping(target = "z", source = "posZMm")
    @Mapping(target = "unit", constant = "MilliM")
    @Mapping(target = "standard", source = "norme")
    @Mapping(target = "diameter", source = "diametreMm")
    @Mapping(target = "diameterUnit", constant = "MilliM")
    @Mapping(target = "count", source = "nombre")
    @Mapping(target = "gripLength", source = "longueurSerrageMm")
    @Mapping(target = "gripUnit", constant = "MilliM")
    @Mapping(target = "plm", constant = "FR")
    FastenerDto toFastener(Fixation fixation);

    List<FastenerDto> toFasteners(List<Fixation> fixations);

    @Mapping(target = "id", source = "refRaccord")
    @Mapping(target = "partId", source = "piece.refPiece")
    @Mapping(target = "x", source = "posXMm")
    @Mapping(target = "y", source = "posYMm")
    @Mapping(target = "z", source = "posZMm")
    @Mapping(target = "unit", constant = "MilliM")
    @Mapping(target = "standard", source = "norme")
    @Mapping(target = "dashSize", source = "tailleDash")
    @Mapping(target = "rating", source = "pressionBar")
    @Mapping(target = "ratingUnit", constant = "BAR")
    @Mapping(target = "fluid", source = "fluide")
    @Mapping(target = "plm", constant = "FR")
    CouplingDto toCoupling(RaccordHydraulique raccordHydraulique);

    List<CouplingDto> toCouplings(List<RaccordHydraulique> raccordsHydrauliques);
}
