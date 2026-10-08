// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.evidence;

import com.google.common.collect.ImmutableMap;
import it.unibz.inf.ontop.answering.reformulation.QueryReformulator;
import it.unibz.inf.ontop.evaluator.QueryContext;
import it.unibz.inf.ontop.exception.OntopKGQueryException;
import it.unibz.inf.ontop.exception.OntopReformulationException;
import it.unibz.inf.ontop.iq.IQ;
import it.unibz.inf.ontop.iq.IQTree;
import it.unibz.inf.ontop.iq.node.NativeNode;
import it.unibz.inf.ontop.query.SPARQLQuery;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The SQL of one SPARQL query (SELECT or CONSTRUCT) under a reformulator. Ontop's plan carries the
 * SQL in a single {@link NativeNode}; the nodes above it (IRI templating, datatype tagging, the
 * CONSTRUCT template) run in Java. A plan without a native node means the mapping exposes none of
 * the queried concepts: {@link #EMPTY}.
 */
final class NativeSql {
    static final String EMPTY = "EMPTY";

    private static final Logger log = LoggerFactory.getLogger(NativeSql.class);

    private NativeSql() {}

    /** SQL, {@link #EMPTY}, or null when Ontop rejects the query. */
    static String of(QueryReformulator reformulator, String sparql) {
        try {
            SPARQLQuery<?> query = reformulator.getInputQueryFactory().createSPARQLQuery(sparql);
            QueryContext context = reformulator.getQueryContextFactory().create(ImmutableMap.of());
            IQ plan = reformulator.reformulateIntoNativeQuery(query, context,
                    reformulator.getQueryLoggerFactory().create(context));
            return nativeNode(plan.getTree()).map(node -> node.getNativeQueryString().strip()).orElse(EMPTY);
        } catch (OntopKGQueryException | OntopReformulationException e) {
            log.warn("Ontop could not reformulate a query: {}", e.toString());
            return null;
        }
    }

    private static Optional<NativeNode> nativeNode(IQTree tree) {
        if (tree.getRootNode() instanceof NativeNode node) return Optional.of(node);
        for (IQTree child : tree.getChildren()) {
            Optional<NativeNode> node = nativeNode(child);
            if (node.isPresent()) return node;
        }
        return Optional.empty();
    }
}
