// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.de.web;

import atelier.plm.common.dto.PlugDto;
import atelier.plm.common.policy.PartVisibility;
import atelier.plm.common.policy.Policy;
import atelier.plm.de.domain.BauteilRepository;
import atelier.plm.de.domain.BefestigerRepository;
import atelier.plm.de.domain.HydraulikkupplungRepository;
import atelier.plm.de.domain.SteckerRepository;
import atelier.plm.de.dto.CouplingDto;
import atelier.plm.de.dto.FastenerDto;
import atelier.plm.de.dto.PartDto;
import atelier.plm.de.mapper.DePlmMapper;
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
 * Read-only REST view of the German PLM, in the common DTO shape. Every route answers the profile named
 * in the {@value Policy#HEADER} header with the parts that profile may see (see {@link PartVisibility});
 * features follow their part, and a part the profile may not see is 404, as if unknown.
 */
@RestController
@RequestMapping("/de")
@Transactional(readOnly = true)
public class DePlmController {

    private final BauteilRepository parts;
    private final SteckerRepository plugs;
    private final BefestigerRepository befestiger;
    private final HydraulikkupplungRepository kupplungen;
    private final DePlmMapper mapper;
    private final PartVisibility visibility;

    public DePlmController(BauteilRepository parts, SteckerRepository plugs, BefestigerRepository befestiger,
                           HydraulikkupplungRepository kupplungen, DePlmMapper mapper, PartVisibility visibility) {
        this.parts = parts;
        this.plugs = plugs;
        this.befestiger = befestiger;
        this.kupplungen = kupplungen;
        this.mapper = mapper;
        this.visibility = visibility;
    }

    @GetMapping("/parts")
    public List<PartDto> parts(@RequestHeader(name = Policy.HEADER, required = false) String profile) {
        return mapper.toParts(visibility.keep(parts.findAll(Sort.by("teilNr")), p -> p.getTeilNr(), profile));
    }

    @GetMapping("/parts/{id}")
    public PartDto part(@PathVariable String id, @RequestHeader(name = Policy.HEADER, required = false) String profile) {
        return parts.findById(id)
                .filter(p -> visibility.sees(p.getTeilNr(), profile))
                .map(mapper::toPart)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown part " + id));
    }

    @GetMapping("/plugs")
    public List<PlugDto> plugs(@RequestHeader(name = Policy.HEADER, required = false) String profile) {
        return mapper.toPlugs(visibility.keep(plugs.findAll(Sort.by("steckerId")), s -> s.getBauteil().getTeilNr(), profile));
    }

    @GetMapping("/fasteners")
    public List<FastenerDto> fasteners(@RequestHeader(name = Policy.HEADER, required = false) String profile) {
        return mapper.toFasteners(visibility.keep(befestiger.findAll(Sort.by("befestigerId")), b -> b.getBauteil().getTeilNr(), profile));
    }

    @GetMapping("/couplings")
    public List<CouplingDto> couplings(@RequestHeader(name = Policy.HEADER, required = false) String profile) {
        return mapper.toCouplings(visibility.keep(kupplungen.findAll(Sort.by("kupplungId")), k -> k.getBauteil().getTeilNr(), profile));
    }
}
