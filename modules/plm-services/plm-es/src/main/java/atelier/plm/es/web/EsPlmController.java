// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.es.web;

import atelier.plm.common.dto.PlugDto;
import atelier.plm.common.policy.PartVisibility;
import atelier.plm.common.policy.Policy;
import atelier.plm.es.domain.AcoplamientoRepository;
import atelier.plm.es.domain.ConectorRepository;
import atelier.plm.es.domain.PiezaRepository;
import atelier.plm.es.domain.RemacheRepository;
import atelier.plm.es.dto.CouplingDto;
import atelier.plm.es.dto.FastenerDto;
import atelier.plm.es.dto.PartDto;
import atelier.plm.es.mapper.EsPlmMapper;
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
 * Read-only REST view of the Spanish PLM, in the common DTO shape. Every route answers the profile named
 * in the {@value Policy#HEADER} header with the parts that profile may see (see {@link PartVisibility});
 * features follow their part, and a part the profile may not see is 404, as if unknown.
 */
@RestController
@RequestMapping("/es")
@Transactional(readOnly = true)
public class EsPlmController {

    private final PiezaRepository parts;
    private final ConectorRepository plugs;
    private final RemacheRepository remaches;
    private final AcoplamientoRepository acoplamientos;
    private final EsPlmMapper mapper;
    private final PartVisibility visibility;

    public EsPlmController(PiezaRepository parts, ConectorRepository plugs, RemacheRepository remaches,
                           AcoplamientoRepository acoplamientos, EsPlmMapper mapper, PartVisibility visibility) {
        this.parts = parts;
        this.plugs = plugs;
        this.remaches = remaches;
        this.acoplamientos = acoplamientos;
        this.mapper = mapper;
        this.visibility = visibility;
    }

    @GetMapping("/parts")
    public List<PartDto> parts(@RequestHeader(name = Policy.HEADER, required = false) String profile) {
        return mapper.toParts(visibility.keep(parts.findAll(Sort.by("codPieza")), p -> p.getCodPieza(), profile));
    }

    @GetMapping("/parts/{id}")
    public PartDto part(@PathVariable String id, @RequestHeader(name = Policy.HEADER, required = false) String profile) {
        return parts.findById(id)
                .filter(p -> visibility.sees(p.getCodPieza(), profile))
                .map(mapper::toPart)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown part " + id));
    }

    @GetMapping("/plugs")
    public List<PlugDto> plugs(@RequestHeader(name = Policy.HEADER, required = false) String profile) {
        return mapper.toPlugs(visibility.keep(plugs.findAll(Sort.by("codConector")), c -> c.getPieza().getCodPieza(), profile));
    }

    @GetMapping("/fasteners")
    public List<FastenerDto> fasteners(@RequestHeader(name = Policy.HEADER, required = false) String profile) {
        return mapper.toFasteners(visibility.keep(remaches.findAll(Sort.by("codRemache")), r -> r.getPieza().getCodPieza(), profile));
    }

    @GetMapping("/couplings")
    public List<CouplingDto> couplings(@RequestHeader(name = Policy.HEADER, required = false) String profile) {
        return mapper.toCouplings(visibility.keep(acoplamientos.findAll(Sort.by("codAcoplamiento")), a -> a.getPieza().getCodPieza(), profile));
    }
}
