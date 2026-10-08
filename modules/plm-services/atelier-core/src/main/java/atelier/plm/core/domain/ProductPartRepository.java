// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.core.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductPartRepository extends JpaRepository<ProductPart, ProductPartId> {

    long countByProductKey(String productKey);
}
