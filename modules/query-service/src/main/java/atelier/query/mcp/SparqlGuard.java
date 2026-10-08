// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.mcp;

import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QueryParseException;
import org.apache.jena.sparql.algebra.Algebra;
import org.apache.jena.sparql.algebra.OpVisitorBase;
import org.apache.jena.sparql.algebra.op.OpBGP;
import org.apache.jena.sparql.algebra.op.OpPath;
import org.apache.jena.sparql.algebra.op.OpQuad;
import org.apache.jena.sparql.algebra.op.OpQuadPattern;
import org.apache.jena.sparql.algebra.op.OpService;
import org.apache.jena.sparql.algebra.op.OpTriple;
import org.apache.jena.sparql.algebra.walker.Walker;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.sparql.core.TriplePath;
import org.apache.jena.sparql.expr.E_Function;
import org.apache.jena.sparql.expr.ExprFunctionN;
import org.apache.jena.sparql.expr.ExprVisitorBase;
import org.apache.jena.sparql.path.P_NegPropSet;
import org.apache.jena.sparql.path.P_Path0;
import org.apache.jena.sparql.path.P_Path1;
import org.apache.jena.sparql.path.P_Path2;
import org.apache.jena.sparql.path.Path;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.XSD;

/**
 * The guard of the {@code sparql} tool. A query text is parsed with Jena and accepted only when it is
 * a SELECT, ASK or CONSTRUCT whose algebra holds no SERVICE and, in predicate position (including
 * inside property paths) or as the object of {@code rdf:type}, no IRI outside the {@link KnownTerms},
 * and in its expressions (FILTER, BIND, ORDER BY, HAVING, aggregates, EXISTS) no function called by
 * IRI other than the {@code xsd:} casts and the known namespaces: the SPARQL built-ins are the only
 * functions, so no Jena or Java function is ever reached. SELECT and CONSTRUCT queries get
 * {@code LIMIT 200} unless they ask for less. Anything else is rejected with the reason; an unknown
 * term is rejected with the list of known ones.
 */
public final class SparqlGuard {
    public static final int LIMIT = 200;

    /** A query the guard refuses; the message is what the caller is told. */
    public static final class Rejection extends RuntimeException {
        Rejection(String message) {
            super(message);
        }
    }

    private SparqlGuard() {}

    public static Query check(String text, KnownTerms terms) {
        Query query;
        try {
            query = QueryFactory.create(text);
        } catch (QueryParseException e) {
            if (isUpdate(text)) {
                throw new Rejection("update forms are not accepted: the sparql tool is read-only (SELECT, ASK or CONSTRUCT)");
            }
            throw new Rejection("not a valid SPARQL 1.1 query: " + e.getMessage().strip());
        }
        if (!(query.isSelectType() || query.isAskType() || query.isConstructType())) {
            throw new Rejection("only SELECT, ASK or CONSTRUCT queries are accepted");
        }
        Set<String> unknown = new TreeSet<>();
        Set<String> functions = new TreeSet<>();
        boolean[] service = {false};
        Consumer<Triple> check = t -> checkTriple(t, terms, unknown);
        ExprVisitorBase expressions = new ExprVisitorBase() {
            @Override
            public void visit(ExprFunctionN func) {
                if (func instanceof E_Function f && !allowsFunction(f.getFunctionIRI(), terms)) {
                    functions.add(KnownTerms.curie(f.getFunctionIRI()));
                }
            }
        };
        Walker.walk(Algebra.compile(query), new OpVisitorBase() {
            @Override
            public void visit(OpBGP op) {
                op.getPattern().forEach(check);
            }

            @Override
            public void visit(OpTriple op) {
                check.accept(op.getTriple());
            }

            @Override
            public void visit(OpQuadPattern op) {
                op.getBasicPattern().forEach(check);
            }

            @Override
            public void visit(OpQuad op) {
                check.accept(op.getQuad().asTriple());
            }

            @Override
            public void visit(OpPath op) {
                TriplePath path = op.getTriplePath();
                if (path.isTriple()) {
                    check.accept(path.asTriple());
                } else {
                    pathIris(path.getPath(), iri -> {
                        if (!terms.allowsProperty(iri)) unknown.add(KnownTerms.curie(iri));
                    });
                }
            }

            @Override
            public void visit(OpService op) {
                service[0] = true;
            }
        }, expressions);
        if (query.isConstructType()) {
            for (Quad quad : query.getConstructTemplate().getQuads()) check.accept(quad.asTriple());
        }
        if (service[0]) {
            throw new Rejection("SERVICE is not accepted: the query runs on the merged graph in memory, never on the sources");
        }
        if (!unknown.isEmpty()) {
            throw new Rejection("unknown terms in predicate or class position: " + unknown + "; known " + terms.describe());
        }
        if (!functions.isEmpty()) {
            throw new Rejection("functions called by IRI are not accepted: " + functions
                    + "; use the SPARQL built-in functions and xsd: casts");
        }
        if (!query.isAskType() && (!query.hasLimit() || query.getLimit() > LIMIT)) {
            query.setLimit(LIMIT);
        }
        return query;
    }

    /** {@code xsd:} casts and the known namespaces are the only IRIs a function may be called by. */
    private static boolean allowsFunction(String iri, KnownTerms terms) {
        return iri.startsWith(XSD.getURI()) || terms.allowsProperty(iri);
    }

    private static void checkTriple(Triple t, KnownTerms terms, Set<String> unknown) {
        Node p = t.getPredicate();
        if (p.isURI() && !terms.allowsProperty(p.getURI())) unknown.add(KnownTerms.curie(p.getURI()));
        Node o = t.getObject();
        if (RDF.type.asNode().equals(p) && o.isURI() && !terms.allowsClass(o.getURI())) unknown.add(KnownTerms.curie(o.getURI()));
    }

    /** Every IRI a property path names. */
    private static void pathIris(Path path, Consumer<String> iri) {
        if (path instanceof P_NegPropSet set) {
            set.getNodes().forEach(node -> pathIris(node, iri));
        } else if (path instanceof P_Path0 link) {
            if (link.getNode().isURI()) iri.accept(link.getNode().getURI());
        } else if (path instanceof P_Path1 one) {
            pathIris(one.getSubPath(), iri);
        } else if (path instanceof P_Path2 two) {
            pathIris(two.getLeft(), iri);
            pathIris(two.getRight(), iri);
        }
    }

    private static boolean isUpdate(String text) {
        try {
            UpdateFactory.create(text);
            return true;
        } catch (QueryParseException e) {
            return false;
        }
    }
}
