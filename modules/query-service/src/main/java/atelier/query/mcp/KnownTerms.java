// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.mcp;

import atelier.query.Atelier;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;

/**
 * The vocabulary the {@code sparql} tool accepts in predicate and class position: the classes and
 * properties ontology/atelier.ttl declares (which include the QUDT terms the data uses) and any IRI of
 * the rdf, rdfs, qudt and unit namespaces. Keeps the loaded ontology, so the {@code ontology} tool
 * lists exactly the model the guard checks against.
 */
public final class KnownTerms {
    public static final String SHACL = "http://www.w3.org/ns/shacl#";
    private static final List<String> NAMESPACES = List.of(RDF.getURI(), RDFS.getURI(), Atelier.QUDT, Atelier.UNIT);
    /** Namespace by prefix, in listing order: the data vocabularies, then those of the rules. */
    public static final Map<String, String> PREFIXES = prefixes();

    private final Model ontology;
    private final SortedSet<String> classes = new TreeSet<>();
    private final SortedSet<String> properties = new TreeSet<>();

    public KnownTerms(Model ontology) {
        this.ontology = ontology;
        ontology.listSubjectsWithProperty(RDF.type, OWL.Class).mapWith(Resource::getURI).forEach(classes::add);
        for (Resource type : List.of(OWL.ObjectProperty, OWL.DatatypeProperty)) {
            ontology.listSubjectsWithProperty(RDF.type, type).mapWith(Resource::getURI).forEach(properties::add);
        }
    }

    /** The terms of the shipped ontology/atelier.ttl. */
    public static KnownTerms load() {
        return new KnownTerms(RDFDataMgr.loadModel("ontology/atelier.ttl"));
    }

    /** The loaded ontology/atelier.ttl. */
    public Model ontology() {
        return ontology;
    }

    public boolean allowsClass(String iri) {
        return classes.contains(iri) || namespaced(iri);
    }

    public boolean allowsProperty(String iri) {
        return properties.contains(iri) || namespaced(iri);
    }

    /** The known classes and properties as prefixed names in alphabetical order, for rejections. */
    public String describe() {
        return "classes: " + curies(classes) + "; properties: " + curies(properties)
                + "; any rdf:, rdfs:, qudt: or unit: IRI";
    }

    /** An IRI as a prefixed name under {@link #PREFIXES}, or in angle brackets when no prefix covers it. */
    public static String curie(String iri) {
        return PREFIXES.entrySet().stream().filter(e -> iri.startsWith(e.getValue()))
                .map(e -> e.getKey() + ":" + iri.substring(e.getValue().length())).findFirst().orElse("<" + iri + ">");
    }

    private static Map<String, String> prefixes() {
        Map<String, String> prefixes = new LinkedHashMap<>();
        prefixes.put("atelier", Atelier.ONT);
        prefixes.put("qudt", Atelier.QUDT);
        prefixes.put("unit", Atelier.UNIT);
        prefixes.put("rdf", RDF.getURI());
        prefixes.put("rdfs", RDFS.getURI());
        prefixes.put("xsd", XSD.getURI());
        prefixes.put("sh", SHACL);
        prefixes.put("ateliersh", Atelier.SHAPES);
        return Collections.unmodifiableMap(prefixes);
    }

    private static boolean namespaced(String iri) {
        return NAMESPACES.stream().anyMatch(iri::startsWith);
    }

    private static String curies(SortedSet<String> iris) {
        return iris.stream().map(KnownTerms::curie).sorted().collect(Collectors.joining(", "));
    }
}
