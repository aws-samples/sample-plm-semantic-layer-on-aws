// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.catalogue;

import atelier.plm.common.r2rml.R2rmlGenerator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.ManagedType;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the ORM mapping as a data catalogue at GET /{plm}/catalogue, and the R2RML mapping
 * generated from the same annotations at GET /{plm}/mapping, where {plm} is the service's
 * {@code plm.code} property (e.g. "fr"). The mapping is the one the Ontop image for this PLM
 * is built with; CI fails if the committed copy differs.
 */
@RestController
public class CatalogueController {

    private final EntityManager entityManager;
    private final String plm;
    private final String plmCode;

    public CatalogueController(EntityManager entityManager, @Value("${plm.code}") String plmCode) {
        this.entityManager = entityManager;
        this.plm = plmCode.toUpperCase();
        this.plmCode = plmCode.toLowerCase();
    }

    @GetMapping("/${plm.code}/catalogue")
    public Catalogue catalogue() {
        return AnnotationCatalogue.fromMetamodel(plm, entityManager.getMetamodel());
    }

    @GetMapping(value = "/${plm.code}/mapping", produces = "text/turtle")
    public String mapping() {
        List<Class<?>> classes = entityManager.getMetamodel().getEntities().stream()
                .map(ManagedType::getJavaType)
                .filter(Objects::nonNull)
                .<Class<?>>map(c -> c)
                .toList();
        return R2rmlGenerator.generate(plmCode, classes);
    }
}
