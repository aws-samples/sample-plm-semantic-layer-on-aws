// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.core.domain;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PartTagRepository extends JpaRepository<PartTag, PartTagId> {

    /** The tags whose audience is one of {@code releasableTo}: the parts a clearance holding those tokens may see. */
    List<PartTag> findByReleasableToIn(Collection<String> releasableTo, Sort sort);
}
