// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import static org.assertj.core.api.Assertions.assertThat;

import atelier.query.cad.CadUrls;
import atelier.query.evidence.NoOntopSql;
import atelier.query.evidence.OntopReformulators;
import atelier.query.federation.Endpoints;
import atelier.query.federation.FederatedQueries;
import atelier.query.federation.Federator;
import atelier.query.policy.Caller;
import atelier.query.policy.Policy;
import atelier.query.validation.RuleValidator;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Reformulators built from the repository's own inputs (modules/ontop/mappings, ontology/atelier.ttl,
 * modules/ontop/metadata; paths relative to this module, Surefire's working directory) and from
 * the fixture stack's (fixtures/generate.py: PLM databases without classification columns, the
 * atelier_core database with part_tag).
 */
class OntopReformulatorsTest {
    private static final String MAPPINGS = "../ontop/mappings";
    private static final String ONTOLOGY = "../../ontology/atelier.ttl";
    private static final String METADATA = "../ontop/metadata";
    private static final String FIXTURE_ONTOP = "fixtures/generated/ontop";
    private static final Policy.Profile UK = new Policy.Profile("uk-engineer", "UK engineer", List.of("ALL", "EU", "UK"));
    private static final String INNER_SPAR = "https://example.com/atelier/uk/part/SPAR-6110-L";

    private static final String UK_PLUG_POSITION = """
            SELECT * WHERE {
              <https://example.com/atelier/uk/plug/PL%206180-01> a <https://example.com/atelier/ontology#Plug> ;
                  <https://example.com/atelier/ontology#onPart> ?onPart ;
                  <https://example.com/atelier/ontology#positionX> ?qx .
              ?qx <http://qudt.org/schema/qudt/numericValue> ?x
              OPTIONAL { ?qx <http://qudt.org/schema/qudt/unit> ?ux }
            }
            """;

    private static final String INTERFACES = """
            SELECT * WHERE { ?if a <https://example.com/atelier/ontology#Interface> }
            """;

    private final Endpoints endpoints = new Endpoints("http://fr/sparql", "http://de/sparql", "http://uk/sparql",
            "http://es/sparql", "http://core/sparql", "http://links/query", Duration.ofSeconds(5), Duration.ofSeconds(30));

    @Test
    void plugPositionArmForUkReadsTheHarnessConnectorTable() {
        OntopReformulators reformulators = new OntopReformulators(endpoints, MAPPINGS, ONTOLOGY, METADATA);

        String sql = reformulators.reformulate("uk", UK_PLUG_POSITION);

        assertThat(sql).startsWith("SELECT").contains("\"harness_connector\"").contains("\"pos_x\"")
                .contains("PL 6180-01").doesNotContain("\"component\"");
        assertThat(reformulators.reformulate("uk", UK_PLUG_POSITION)).isSameAs(sql);
    }

    @Test
    void partsArmOfEachPlmReadsItsPartTable() {
        OntopReformulators reformulators = new OntopReformulators(endpoints, MAPPINGS, ONTOLOGY, METADATA);
        String parts = "SELECT * WHERE { ?part a <https://example.com/atelier/ontology#Part> ; "
                + "<https://example.com/atelier/ontology#label> ?name }";

        assertThat(reformulators.reformulate("fr", parts)).contains("\"piece\"").contains("\"designation\"");
        assertThat(reformulators.reformulate("de", parts)).contains("\"bauteil\"");
        assertThat(reformulators.reformulate("uk", parts)).contains("\"component\"");
        assertThat(reformulators.reformulate("es", parts)).contains("\"pieza\"");
    }

    /**
     * The core requests for one part's tag: who tagged it, from the tag row alone, then its releasable tag, the profile FILTER
     * a single condition on the releasability column that keeps a hidden part's row in the database.
     */
    @Test
    void coreRequestPushesTheReleasabilityFilterIntoThePartTagTableOnce() {
        OntopReformulators reformulators = new OntopReformulators(endpoints, FIXTURE_ONTOP, ONTOLOGY, FIXTURE_ONTOP);
        List<String> tags = FederatedQueries.tags(UK, List.of(INNER_SPAR));
        assertThat(tags).hasSize(2);

        String tagged = reformulators.reformulate("core", tags.get(0));
        assertThat(tagged).startsWith("SELECT").contains("\"part_tag\"").contains("\"tagged_by\"").contains("SPAR-6110-L")
                .doesNotContain("\"releasable_to\"").doesNotContain("\"jurisdiction\"");

        String released = reformulators.reformulate("core", tags.get(1));
        assertThat(released).startsWith("SELECT").contains("\"part_tag\"").contains("\"tagged_at\"").contains("\"jurisdiction\"")
                .contains("SPAR-6110-L");
        assertThat(released).as("the policy FILTER becomes one condition on the releasability column")
                .contains("'EU'").contains("'UK'").doesNotContain("'FR'");
        assertThat(java.util.regex.Pattern.compile("\"releasable_to\" = 'EU'").matcher(released).results().count())
                .as("the releasability test appears once: %s", released).isEqualTo(1);
    }

    /** The core products request joins the membership table to the product table under the production core mapping and schema. */
    @Test
    void coreProductsRequestReadsTheProductTables() {
        OntopReformulators reformulators = new OntopReformulators(endpoints, MAPPINGS, ONTOLOGY, METADATA);

        String sql = reformulators.reformulate("core", FederatedQueries.products(List.of(INNER_SPAR)));

        assertThat(sql).startsWith("SELECT").contains("\"product_part\"").contains("\"product\"").contains("\"frame\"")
                .contains("SPAR-6110-L").doesNotContain("\"part_tag\"").doesNotContain("UNION");
    }

    /** Each request to a PLM reads the one native table of its concern, with no UNION in the SQL. */
    @Test
    void eachRequestToUkReadsOneNativeTable() {
        OntopReformulators reformulators = new OntopReformulators(endpoints, FIXTURE_ONTOP, ONTOLOGY, FIXTURE_ONTOP);
        Map<String, String> tableByRequest = new LinkedHashMap<>();
        tableByRequest.put(FederatedQueries.parts(List.of(INNER_SPAR)), "component");
        tableByRequest.put(FederatedQueries.features("plug", List.of(INNER_SPAR)), "harness_connector");
        tableByRequest.put(FederatedQueries.features("fastener", List.of(INNER_SPAR)), "fastener");
        tableByRequest.put(FederatedQueries.features("coupling", List.of(INNER_SPAR)), "hyd_coupling");

        tableByRequest.forEach((request, table) -> {
            String sql = reformulators.reformulate("uk", request);
            assertThat(sql).as(table).startsWith("SELECT").contains("\"" + table + "\"").contains("SPAR-6110-L")
                    .doesNotContain("UNION").doesNotContain("PL 6");
            tableByRequest.values().stream().filter(other -> !other.equals(table))
                    .forEach(other -> assertThat(sql).as(table + " does not read " + other).doesNotContain("\"" + other + "\""));
            assertThat(sql.length()).as(table).isLessThan(16_000);
        });
    }

    @Test
    void partsRequestReadsThePartTableWithItsFileColumnAsSourceFileRef() {
        OntopReformulators reformulators = new OntopReformulators(endpoints, FIXTURE_ONTOP, ONTOLOGY, FIXTURE_ONTOP);

        assertThat(reformulators.reformulate("uk", FederatedQueries.parts(List.of(INNER_SPAR)))).contains("\"component\"")
                .contains("\"cad_file\"").contains("SPAR-6110-L").doesNotContain("export_class").doesNotContain("releasable_to");
    }

    /** Every request a run sends to an Ontop source translates under that source's production mapping and schema. */
    @Test
    void everyRequestOfEveryProfileReformulatesUnderTheProductionMappings() {
        OntopReformulators production = new OntopReformulators(endpoints, MAPPINGS, ONTOLOGY, METADATA);
        FixtureFederator federator = new FixtureFederator();
        QueryService service = new QueryService(federator, new RuleValidator(), new NoOntopSql(), new CadUrls(null, null));
        for (Policy.Profile profile : EvidenceTest.POLICY.profiles()) {
            service.interfaces(Caller.user(profile));
            service.parts(Caller.user(profile));
        }

        int translated = 0;
        for (Federator.Result run : federator.results) {
            for (Map.Entry<String, List<String>> sent : run.queries().entrySet()) {
                String source = Endpoints.sourceOf(sent.getKey());
                if (source == null) continue;
                for (String request : sent.getValue()) {
                    translated++;
                    assertThat(production.reformulate(source, request)).as(sent.getKey() + ":\n" + request).startsWith("SELECT");
                }
            }
        }
        assertThat(translated).isGreaterThan(50);
    }

    /**
     * Each site's bill-of-materials idiom, read through the fixture stack's copy of its generated mapping and schema:
     * the line requests for an item of the dataset become SQL over the site's own relation, with no UNION.
     */
    @Test
    void germanLinesReadThePartRowAndItsParentColumn() {
        String sql = bomSql("de", "https://example.com/atelier/de/part/D-37070");
        assertThat(sql).contains("\"bauteil\"").contains("\"parent_id\"").contains("\"menge\"");
    }

    @Test
    void frenchLinesReadTheNomenclatureLinkTable() {
        String sql = bomSql("fr", "https://example.com/atelier/fr/part/FR3770");
        assertThat(sql).contains("\"nomenclature\"").contains("\"enfant\"").contains("\"quantite\"").doesNotContain("\"piece\"");
    }

    /** The ES document is read through the view over it: the mapping names the view, never the JSON column. */
    @Test
    void spanishLinesReadTheViewOverTheListaMaterialesDocument() {
        String sql = bomSql("es", "https://example.com/atelier/es/part/ES-3770");
        assertThat(sql).contains("\"linea_lista_materiales\"").contains("\"cantidad\"").doesNotContain("\"lista_materiales\"")
                .doesNotContain("jsonb");
        OntopReformulators production = new OntopReformulators(endpoints, MAPPINGS, ONTOLOGY, METADATA);
        assertThat(production.reformulate("es", FederatedQueries.bomLinesTo(List.of("https://example.com/atelier/es/part/ES-3701"))))
                .as("the view is in the production schema metadata").contains("\"linea_lista_materiales\"");
    }

    @Test
    void britishLinesReadTheIndentedBomLineRows() {
        String sql = bomSql("uk", "https://example.com/atelier/uk/part/UK-3770");
        assertThat(sql).contains("\"bom_line\"").contains("\"parent_part_no\"").contains("\"qty\"").doesNotContain("\"component\"");
    }

    /** The lines from and to the item under the fixture stack's mapping and schema, as one text; each is one SELECT without UNION. */
    private String bomSql(String plm, String item) {
        OntopReformulators fixture = new OntopReformulators(endpoints, FIXTURE_ONTOP, ONTOLOGY, FIXTURE_ONTOP);
        StringBuilder both = new StringBuilder();
        for (String request : List.of(FederatedQueries.bomLinesFrom(List.of(item)), FederatedQueries.bomLinesTo(List.of(item)))) {
            String sql = fixture.reformulate(plm, request);
            assertThat(sql).as(plm).startsWith("SELECT").doesNotContain("UNION").contains(Atelier.nativeId(item));
            both.append(sql).append('\n');
        }
        return both.toString();
    }

    /**
     * A subtree request names its roots only ({@code VALUES ?root} through atelier:contains): under each site's production
     * mapping and schema it becomes SQL over that site's closure table, joined to the part table, with the root's id as a
     * condition, so the site's database selects the tree. One SELECT without UNION, and the part table read once.
     */
    @Test
    void subtreeRequestsBecomeSqlOverEachSitesClosureTable() {
        OntopReformulators production = new OntopReformulators(endpoints, MAPPINGS, ONTOLOGY, METADATA);
        Map<String, List<String>> sites = Map.of(
                "de", List.of("https://example.com/atelier/de/part/D-37073", "teilestruktur", "bauteil"),
                "fr", List.of("https://example.com/atelier/fr/part/FR3770", "nomenclature_fermeture", "piece"),
                "es", List.of("https://example.com/atelier/es/part/ES-3770", "cierre_lista_materiales", "pieza"),
                "uk", List.of("https://example.com/atelier/uk/part/UK-3776", "bom_closure", "component"));
        sites.forEach((plm, site) -> {
            List<String> root = List.of(site.get(0));
            String id = Atelier.nativeId(site.get(0));
            String parts = production.reformulate(plm, FederatedQueries.parts(FederatedQueries.Selection.under(root)));
            assertThat(parts).as(plm).startsWith("SELECT").contains("\"" + site.get(1) + "\"").contains("\"" + site.get(2) + "\"")
                    .contains("'" + id + "'").doesNotContain("UNION");
            String structure = production.reformulate(plm, FederatedQueries.structure(root));
            assertThat(structure).as(plm + " structure").startsWith("SELECT").contains("\"" + site.get(1) + "\"").contains("'" + id + "'");
            String alone = production.reformulate(plm, FederatedQueries.references(FederatedQueries.Selection.only(root)));
            assertThat(alone).as(plm + " the root alone").contains("\"" + site.get(1) + "\"").contains("'" + id + "'");
        });
    }

    @Test
    void conceptNoPlmMapsIsEmpty() {
        OntopReformulators reformulators = new OntopReformulators(endpoints, MAPPINGS, ONTOLOGY, METADATA);

        assertThat(reformulators.reformulate("uk", INTERFACES)).isEqualTo("EMPTY");
    }

    @Test
    void missingMetadataYieldsNullWithoutFailingConstruction(@TempDir Path noMetadata) {
        OntopReformulators reformulators = new OntopReformulators(endpoints, MAPPINGS, ONTOLOGY,
                noMetadata.toString());

        assertThat(reformulators.reformulate("uk", UK_PLUG_POSITION)).isNull();
        assertThat(reformulators.reformulate("core", INTERFACES)).isNull();
        assertThat(reformulators.reformulate("neptune", INTERFACES)).isNull();
    }
}
