// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.fr.web;

import atelier.plm.common.dto.PlugDto;
import atelier.plm.common.policy.PartVisibility;
import atelier.plm.common.policy.Policy;
import atelier.plm.fr.domain.ConnecteurRepository;
import atelier.plm.fr.domain.FixationRepository;
import atelier.plm.fr.domain.PieceRepository;
import atelier.plm.fr.domain.RaccordHydrauliqueRepository;
import atelier.plm.fr.dto.CouplingDto;
import atelier.plm.fr.dto.FastenerDto;
import atelier.plm.fr.dto.PartDto;
import atelier.plm.fr.mapper.FrPlmMapper;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Read-only REST view of the French PLM, in the common DTO shape. Every route answers the profile named
 * in the {@value Policy#HEADER} header with the parts that profile may see (see {@link PartVisibility});
 * features follow their part, and a part the profile may not see is 404, as if unknown.
 */
@RestController
@RequestMapping("/fr")
@Transactional(readOnly = true)
public class FrPlmController {

    private final PieceRepository pieces;
    private final ConnecteurRepository connecteurs;
    private final FixationRepository fixations;
    private final RaccordHydrauliqueRepository raccords;
    private final FrPlmMapper mapper;
    private final PartVisibility visibility;

    public FrPlmController(PieceRepository pieces, ConnecteurRepository connecteurs, FixationRepository fixations,
                           RaccordHydrauliqueRepository raccords, FrPlmMapper mapper, PartVisibility visibility) {
        this.pieces = pieces;
        this.connecteurs = connecteurs;
        this.fixations = fixations;
        this.raccords = raccords;
        this.mapper = mapper;
        this.visibility = visibility;
    }

    @GetMapping("/parts")
    public List<PartDto> parts(@RequestHeader(name = Policy.HEADER, required = false) String profile) {
        return mapper.toParts(visibility.keep(pieces.findAll(Sort.by("refPiece")), p -> p.getRefPiece(), profile));
    }

    @GetMapping("/parts/{id}")
    public PartDto part(@PathVariable String id, @RequestHeader(name = Policy.HEADER, required = false) String profile) {
        return pieces.findById(id)
                .filter(p -> visibility.sees(p.getRefPiece(), profile))
                .map(mapper::toPart)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown part " + id));
    }

    @GetMapping("/plugs")
    public List<PlugDto> plugs(@RequestHeader(name = Policy.HEADER, required = false) String profile) {
        return mapper.toPlugs(visibility.keep(connecteurs.findAll(Sort.by("idConnecteur")), c -> c.getPiece().getRefPiece(), profile));
    }

    @GetMapping("/fasteners")
    public List<FastenerDto> fasteners(@RequestHeader(name = Policy.HEADER, required = false) String profile) {
        return mapper.toFasteners(visibility.keep(fixations.findAll(Sort.by("refFixation")), f -> f.getPiece().getRefPiece(), profile));
    }

    @GetMapping("/couplings")
    public List<CouplingDto> couplings(@RequestHeader(name = Policy.HEADER, required = false) String profile) {
        return mapper.toCouplings(visibility.keep(raccords.findAll(Sort.by("refRaccord")), r -> r.getPiece().getRefPiece(), profile));
    }
}
