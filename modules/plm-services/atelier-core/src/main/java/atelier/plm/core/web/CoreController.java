// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.core.web;

import atelier.plm.common.policy.Policy;
import atelier.plm.core.domain.PartTagRepository;
import atelier.plm.core.dto.PartTagDto;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only REST view of the part tags, ordered by PLM then native key: the tags of the parts the profile
 * named in the {@value Policy#HEADER} header may see, that is the rows whose {@code releasable_to} is one
 * of the profile's releasability tokens, the same rule the PLM services apply to their rows. The export
 * officer holds every token and sees every tag; an absent or unknown profile sees the tags released to all.
 */
@RestController
@RequestMapping("/core")
@Transactional(readOnly = true)
public class CoreController {

    private final PartTagRepository tags;
    private final Policy policy;

    public CoreController(PartTagRepository tags, Policy policy) {
        this.tags = tags;
        this.policy = policy;
    }

    @GetMapping("/tags")
    public List<PartTagDto> tags(@RequestHeader(name = Policy.HEADER, required = false) String profile) {
        return tags.findByReleasableToIn(policy.clearance(profile).releasable(), Sort.by("plm", "nativeKey"))
                .stream().map(PartTagDto::of).toList();
    }
}
