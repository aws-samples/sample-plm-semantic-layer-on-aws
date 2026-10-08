// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.jena.query.Query;
import org.junit.jupiter.api.Test;

/** The sparql tool's guard over the shipped ontology/atelier.ttl. */
class SparqlGuardTest {
    private static final KnownTerms TERMS = KnownTerms.load();
    private static final String PREFIX = "PREFIX atelier: <https://example.com/atelier/ontology#> PREFIX qudt: <http://qudt.org/schema/qudt/> ";

    @Test
    void acceptsSelectAskAndConstructAndAppliesTheLimit() {
        Query select = SparqlGuard.check(PREFIX + "SELECT ?p (COUNT(?f) AS ?n) WHERE { ?f a atelier:Plug ; atelier:onPart ?p } GROUP BY ?p", TERMS);
        assertThat(select.isSelectType()).isTrue();
        assertThat(select.getLimit()).as("no LIMIT asked: 200 applied").isEqualTo(200);
        assertThat(SparqlGuard.check(PREFIX + "SELECT * WHERE { ?s ?p ?o } LIMIT 5000", TERMS).getLimit()).isEqualTo(200);
        assertThat(SparqlGuard.check(PREFIX + "SELECT * WHERE { ?s ?p ?o } LIMIT 5", TERMS).getLimit()).as("a smaller LIMIT stays").isEqualTo(5);

        Query ask = SparqlGuard.check(PREFIX + "ASK { ?i a atelier:Interface ; atelier:toleranceMm ?t }", TERMS);
        assertThat(ask.isAskType()).isTrue();
        assertThat(ask.hasLimit()).isFalse();

        Query construct = SparqlGuard.check(PREFIX + "CONSTRUCT { ?f atelier:onPart ?p } WHERE { ?f atelier:onPart ?p }", TERMS);
        assertThat(construct.isConstructType()).isTrue();
        assertThat(construct.getLimit()).isEqualTo(200);
    }

    @Test
    void acceptsPropertyPathsQudtAndRdfsTerms() {
        SparqlGuard.check(PREFIX + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> SELECT ?f ?v WHERE { "
                + "?f a atelier:InterfaceFeature ; atelier:positionX/qudt:numericValue ?v . OPTIONAL { ?f rdfs:label ?l } }", TERMS);
        SparqlGuard.check(PREFIX + "SELECT ?i ?p WHERE { ?i atelier:betweenPart|atelier:declaresFeature ?p }", TERMS);
    }

    @Test
    void rejectsUnknownTermsWithTheKnownOnes() {
        assertThatThrownBy(() -> SparqlGuard.check(PREFIX + "SELECT * WHERE { ?s atelier:weight ?o }", TERMS))
                .isInstanceOf(SparqlGuard.Rejection.class)
                .hasMessageContaining("unknown terms in predicate or class position: [atelier:weight]")
                .hasMessageContaining("known classes: atelier:BomLine, atelier:Drive, atelier:ExternalReference, atelier:Fastener, atelier:HydraulicCoupling, atelier:Interface, atelier:InterfaceFeature, atelier:Occurrence, atelier:Option, atelier:Part, atelier:Plug, atelier:Product, atelier:Section, atelier:Station, atelier:Supplier, atelier:SupplierOffer, atelier:Variant, qudt:QuantityValue")
                .hasMessageContaining("atelier:betweenPart").hasMessageContaining("atelier:partOf").hasMessageContaining("qudt:numericValue");
        assertThatThrownBy(() -> SparqlGuard.check(PREFIX + "SELECT * WHERE { ?s a atelier:Bolt }", TERMS))
                .hasMessageContaining("[atelier:Bolt]");
        assertThatThrownBy(() -> SparqlGuard.check(PREFIX + "SELECT * WHERE { ?s atelier:onPart/atelier:madeBy ?o }", TERMS))
                .as("inside a property path").hasMessageContaining("[atelier:madeBy]");
        assertThatThrownBy(() -> SparqlGuard.check(PREFIX + "CONSTRUCT { ?s atelier:weight ?o } WHERE { ?s atelier:pinCount ?o }", TERMS))
                .as("in the CONSTRUCT template").hasMessageContaining("[atelier:weight]");
        assertThatThrownBy(() -> SparqlGuard.check(PREFIX + "SELECT * WHERE { ?s <http://schema.org/name> ?o }", TERMS))
                .hasMessageContaining("[<http://schema.org/name>]");
    }

    @Test
    void acceptsBuiltInFunctionsAndCastsButNoFunctionIri() {
        SparqlGuard.check(PREFIX + "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> SELECT ?i (xsd:integer(?t) AS ?n) WHERE {"
                + " ?i a atelier:Interface ; atelier:label ?l ; atelier:toleranceMm ?t . FILTER(regex(str(?l), \"wing\", \"i\") && ?t > 1.5) }"
                + " ORDER BY DESC(?t)", TERMS);
        SparqlGuard.check(PREFIX + "SELECT ?p (COUNT(?f) AS ?n) WHERE { ?f atelier:onPart ?p } GROUP BY ?p HAVING (COUNT(?f) > 2)", TERMS);
        assertThatThrownBy(() -> SparqlGuard.check(PREFIX + "SELECT * WHERE { ?s atelier:label ?o . FILTER(<java:atelier.query.Boom>(?o)) }", TERMS))
                .isInstanceOf(SparqlGuard.Rejection.class)
                .hasMessage("functions called by IRI are not accepted: [<java:atelier.query.Boom>]; use the SPARQL built-in functions and xsd: casts");
        assertThatThrownBy(() -> SparqlGuard.check(PREFIX + "SELECT ?s (<http://jena.apache.org/ARQ/function#sha1>(?o) AS ?h) WHERE { ?s atelier:label ?o }", TERMS))
                .hasMessageContaining("[<http://jena.apache.org/ARQ/function#sha1>]");
        assertThatThrownBy(() -> SparqlGuard.check(PREFIX + "SELECT * WHERE { ?s atelier:label ?o . FILTER EXISTS { ?s atelier:pinCount ?n . FILTER(<java:x.Y>(?n)) } }", TERMS))
                .as("inside EXISTS too").hasMessageContaining("[<java:x.Y>]");
    }

    @Test
    void rejectsUpdatesServiceAndOtherForms() {
        assertThatThrownBy(() -> SparqlGuard.check(PREFIX + "INSERT DATA { <urn:a> atelier:label \"x\" }", TERMS))
                .hasMessage("update forms are not accepted: the sparql tool is read-only (SELECT, ASK or CONSTRUCT)");
        assertThatThrownBy(() -> SparqlGuard.check(PREFIX + "DELETE WHERE { ?s atelier:label ?o }", TERMS))
                .hasMessageContaining("update forms are not accepted");
        assertThatThrownBy(() -> SparqlGuard.check(PREFIX + "SELECT * WHERE { SERVICE <http://example.org/sparql> { ?s atelier:label ?o } }", TERMS))
                .hasMessage("SERVICE is not accepted: the query runs on the merged graph in memory, never on the sources");
        assertThatThrownBy(() -> SparqlGuard.check(PREFIX + "DESCRIBE <https://example.com/atelier/interface/ornithopter/IF-01>", TERMS))
                .hasMessage("only SELECT, ASK or CONSTRUCT queries are accepted");
        assertThatThrownBy(() -> SparqlGuard.check("SELECT * WHERE { ?s ?p }", TERMS))
                .hasMessageContaining("not a valid SPARQL 1.1 query");
    }
}
