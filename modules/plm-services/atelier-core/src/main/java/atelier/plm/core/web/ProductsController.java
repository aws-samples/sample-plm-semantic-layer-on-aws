// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.core.web;

import atelier.plm.core.domain.ProductPartRepository;
import atelier.plm.core.domain.ProductRepository;
import atelier.plm.core.dto.ProductDto;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only REST view of the products: every product with the number of parts placed in it, ordered by
 * key. Product membership is Atelier's structure, so the list does not depend on the viewer's profile.
 */
@RestController
@RequestMapping("/core")
@Transactional(readOnly = true)
public class ProductsController {

    private final ProductRepository products;
    private final ProductPartRepository parts;

    public ProductsController(ProductRepository products, ProductPartRepository parts) {
        this.products = products;
        this.parts = parts;
    }

    @GetMapping("/products")
    public List<ProductDto> products() {
        return products.findAll(Sort.by("productKey")).stream()
                .map(product -> ProductDto.of(product, parts.countByProductKey(product.getProductKey())))
                .toList();
    }
}
