// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.uk.web;

import atelier.plm.common.dto.PlugDto;
import atelier.plm.common.policy.PartVisibility;
import atelier.plm.common.policy.Policy;
import atelier.plm.uk.domain.ComponentRepository;
import atelier.plm.uk.domain.FastenerRepository;
import atelier.plm.uk.domain.HarnessConnectorRepository;
import atelier.plm.uk.domain.HydCouplingRepository;
import atelier.plm.uk.dto.CouplingDto;
import atelier.plm.uk.dto.FastenerDto;
import atelier.plm.uk.dto.PartDto;
import atelier.plm.uk.mapper.UkPlmMapper;
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
 * Read-only REST view of the British PLM, in the common DTO shape. Every route answers the profile named
 * in the {@value Policy#HEADER} header with the parts that profile may see (see {@link PartVisibility});
 * features follow their part, and a part the profile may not see is 404, as if unknown.
 */
@RestController
@RequestMapping("/uk")
@Transactional(readOnly = true)
public class UkPlmController {

    private final ComponentRepository parts;
    private final HarnessConnectorRepository plugs;
    private final FastenerRepository fasteners;
    private final HydCouplingRepository couplings;
    private final UkPlmMapper mapper;
    private final PartVisibility visibility;

    public UkPlmController(ComponentRepository parts, HarnessConnectorRepository plugs, FastenerRepository fasteners,
                           HydCouplingRepository couplings, UkPlmMapper mapper, PartVisibility visibility) {
        this.parts = parts;
        this.plugs = plugs;
        this.fasteners = fasteners;
        this.couplings = couplings;
        this.mapper = mapper;
        this.visibility = visibility;
    }

    @GetMapping("/parts")
    public List<PartDto> parts(@RequestHeader(name = Policy.HEADER, required = false) String profile) {
        return mapper.toParts(visibility.keep(parts.findAll(Sort.by("compId")), p -> p.getCompId(), profile));
    }

    @GetMapping("/parts/{id}")
    public PartDto part(@PathVariable String id, @RequestHeader(name = Policy.HEADER, required = false) String profile) {
        return parts.findById(id)
                .filter(p -> visibility.sees(p.getCompId(), profile))
                .map(mapper::toPart)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown part " + id));
    }

    @GetMapping("/plugs")
    public List<PlugDto> plugs(@RequestHeader(name = Policy.HEADER, required = false) String profile) {
        return mapper.toPlugs(visibility.keep(plugs.findAll(Sort.by("connRef")), c -> c.getComponent().getCompId(), profile));
    }

    @GetMapping("/fasteners")
    public List<FastenerDto> fasteners(@RequestHeader(name = Policy.HEADER, required = false) String profile) {
        return mapper.toFasteners(visibility.keep(fasteners.findAll(Sort.by("fastRef")), f -> f.getComponent().getCompId(), profile));
    }

    @GetMapping("/couplings")
    public List<CouplingDto> couplings(@RequestHeader(name = Policy.HEADER, required = false) String profile) {
        return mapper.toCouplings(visibility.keep(couplings.findAll(Sort.by("cplgRef")), c -> c.getComponent().getCompId(), profile));
    }
}
