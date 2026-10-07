// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.demo;

import atelier.query.Atelier;
import java.util.Comparator;
import java.util.List;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.sparql.expr.ExprEvalException;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

/**
 * A live named graph against its released content: the triples added and the triples removed,
 * each term shortened the way the answers name things ({@code atelier:matesWith}; a part, feature or
 * interface by its id; a literal by its lexical form; any other IRI in angle brackets), sorted.
 * The IRIs the shortening drops are kept beside the terms, with the PLM code the subject IRI
 * carries, so a reader can tell which PLM a part or feature belongs to. Two triples are the same when subject and predicate are the same term and the objects have the
 * same value: a typed literal by its value ({@code "2.0"^^xsd:decimal} is {@code "2"} and
 * {@code "2.00"}; integers, doubles and booleans likewise, as SPARQL {@code =} decides), a string
 * exactly, an IRI or blank node as a term. A store that canonicalises numerals therefore reports no
 * change.
 */
public final class GraphDiff {
    private GraphDiff() {}

    /**
     * One triple in shortened form. {@code sIri} and {@code oIri} are the full IRIs of the subject
     * and object, null for a literal or blank node; {@code plm} is the lower-case PLM code of the
     * subject IRI ({@code /atelier/{plm}/}), null when the subject is not a PLM's part or feature.
     */
    public record Triple(String s, String p, String o, String sIri, String oIri, String plm) {}

    public record Diff(List<Triple> added, List<Triple> removed) {}

    public static Diff of(Model released, Model live) {
        return new Diff(triples(minus(live, released)), triples(minus(released, live)));
    }

    /** The statements of {@code from} that {@code others} does not hold with the same value. */
    private static List<Statement> minus(Model from, Model others) {
        return from.listStatements().toList().stream()
                .filter(s -> others.listStatements(s.getSubject(), s.getPredicate(), (RDFNode) null).toList().stream()
                        .noneMatch(o -> sameValue(s.getObject(), o.getObject())))
                .toList();
    }

    static boolean sameValue(RDFNode a, RDFNode b) {
        if (!a.isLiteral() || !b.isLiteral()) return a.equals(b);
        try {
            return NodeValue.sameValueAs(NodeValue.makeNode(a.asNode()), NodeValue.makeNode(b.asNode()));
        } catch (ExprEvalException e) {
            return a.equals(b);
        }
    }

    private static List<Triple> triples(List<Statement> statements) {
        return statements.stream().map(GraphDiff::triple)
                .sorted(Comparator.comparing(Triple::s).thenComparing(Triple::p).thenComparing(Triple::o)).toList();
    }

    private static Triple triple(Statement s) {
        String sIri = iri(s.getSubject());
        return new Triple(shorten(s.getSubject()), shorten(s.getPredicate()), shorten(s.getObject()),
                sIri, iri(s.getObject()), sIri == null ? null : Atelier.plmOf(sIri));
    }

    /** The full IRI of a named resource; null for a literal or blank node. */
    private static String iri(RDFNode node) {
        return node.isURIResource() ? node.asResource().getURI() : null;
    }

    static String shorten(RDFNode node) {
        if (node.isLiteral()) return node.asLiteral().getLexicalForm();
        if (node.isAnon()) return "_:" + node.asResource().getId().getLabelString();
        String iri = node.asResource().getURI();
        if (iri.startsWith(Atelier.ONT)) return "atelier:" + iri.substring(Atelier.ONT.length());
        if (iri.startsWith(RDF.getURI())) return "rdf:" + iri.substring(RDF.getURI().length());
        if (iri.startsWith(RDFS.getURI())) return "rdfs:" + iri.substring(RDFS.getURI().length());
        if (Atelier.plmOf(iri) != null || iri.startsWith(Atelier.INTERFACE)) return Atelier.nativeId(iri);
        return "<" + iri + ">";
    }
}
