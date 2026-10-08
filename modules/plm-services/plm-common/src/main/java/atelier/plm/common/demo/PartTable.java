// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.demo;

import atelier.plm.common.annotation.OntologyClass;
import atelier.plm.common.catalogue.AnnotationCatalogue;
import atelier.plm.common.tables.ReleasabilityFilter;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.Metamodel;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

/**
 * The native table holding the parts this service mints IRIs for: the entity of class
 * {@code atelier:Part} with no explicit subject template (the test the tables endpoint applies to decide
 * a part table), looked up by the column ending its subject IRI. Both names come from the entity
 * class, never from the request. A service without such an entity (the Atelier core) has no part table.
 */
record PartTable(String table, String keyColumn) {

    static Optional<PartTable> of(Metamodel metamodel) {
        return metamodel.getEntities().stream()
                .map(ManagedType::getJavaType)
                .filter(PartTable::isPartTable)
                .findFirst()
                .map(type -> new PartTable(AnnotationCatalogue.tableName(type), AnnotationCatalogue.keyColumn(type)));
    }

    private static boolean isPartTable(Class<?> type) {
        OntologyClass ontologyClass = type.getAnnotation(OntologyClass.class);
        return ontologyClass != null && ReleasabilityFilter.PART.equals(ontologyClass.value()) && ontologyClass.subject().isEmpty();
    }

    /** True when a row of the table has the given key; {@code jdbc} reads this service's own database. */
    boolean has(JdbcTemplate jdbc, String key) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + keyColumn + " = ?", Integer.class, key);
        return count != null && count > 0;
    }
}
