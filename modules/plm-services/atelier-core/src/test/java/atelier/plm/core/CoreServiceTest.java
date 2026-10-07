// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The core service over H2 in PostgreSQL mode: the schema is the shipped versioned migrations, the rows a
 * test copy of the dataset seed (every tagged part placed in the one product). Checks
 * the surfaces the contract names for atelier-core: the catalogue, the generated mapping minting the PLMs'
 * part IRIs and the product IRIs, the tags and products as DTOs, and the unfiltered native rows.
 */
@SpringBootTest(properties = {
        "ATELIER_POLICY_FILE=../../../ontology/policy.json",
        "RELEASED_GRAPHS_DIR=../../../data",
        "spring.datasource.url=jdbc:h2:mem:core;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false"})
@AutoConfigureMockMvc
@Sql(scripts = {"classpath:db/migration/V1__schema.sql", "classpath:db/migration/V2__product_mass_limit.sql", "/core-seed.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class CoreServiceTest {

    @Autowired
    MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    private String getOk(String url, String accept) throws Exception {
        return mvc.perform(get(url).accept(accept)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private JsonNode getJson(String url) throws Exception {
        return json.readTree(getOk(url, "application/json"));
    }

    /** GET as a profile; {@code null} sends no profile header at all. */
    private JsonNode getJson(String url, String profile) throws Exception {
        MockHttpServletRequestBuilder request = get(url).accept("application/json");
        if (profile != null) {
            request.header("x-atelier-profile", profile);
        }
        return json.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static List<String> tagKeys(JsonNode tags) {
        List<String> keys = new ArrayList<>();
        tags.forEach(tag -> keys.add(tag.get("plm").asText() + ":" + tag.get("nativeKey").asText()));
        return keys;
    }

    @Test
    void catalogueListsPartTagWithItsOntologyTerms() throws Exception {
        JsonNode catalogue = getJson("/core/catalogue");

        assertThat(catalogue.get("plm").asText()).isEqualTo("CORE");
        assertThat(catalogue.get("entities")).extracting(e -> e.get("table").asText()).containsExactly("part_tag", "product", "product_part");
        JsonNode entity = catalogue.get("entities").get(0);
        assertThat(entity.get("ontologyClass").asText()).isEqualTo("atelier:Part");
        assertThat(entity.get("description").asText()).contains("served unfiltered by /core/tables");

        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("plm", "-");
        expected.put("native_key", "-");
        expected.put("jurisdiction", "atelier:jurisdiction");
        expected.put("releasable_to", "atelier:releasableTo");
        expected.put("tagged_by", "atelier:taggedBy");
        expected.put("tagged_at", "atelier:taggedAt");
        assertThat(terms(entity)).containsExactlyEntriesOf(expected);

        JsonNode product = catalogue.get("entities").get(1);
        assertThat(product.get("ontologyClass").asText()).isEqualTo("atelier:Product");
        assertThat(terms(product)).containsExactly(Map.entry("product_key", "-"), Map.entry("name", "atelier:label"), Map.entry("frame", "atelier:frame"),
                Map.entry("mass_limit_kg", "atelier:massLimit"));
        JsonNode membership = catalogue.get("entities").get(2);
        assertThat(membership.get("ontologyClass").asText()).isEqualTo("atelier:Part");
        assertThat(terms(membership)).containsExactly(Map.entry("product_key", "atelier:partOf"), Map.entry("plm", "-"), Map.entry("native_key", "-"));
        catalogue.get("entities").forEach(e -> e.get("columns").forEach(column -> assertThat(column.get("undescribed").asBoolean()).isFalse()));
    }

    /** Column name to ontology term ({@code -} when none), in column order. */
    private static Map<String, String> terms(JsonNode entity) {
        Map<String, String> terms = new LinkedHashMap<>();
        entity.get("columns").forEach(column -> terms.put(column.get("column").asText(),
                column.get("ontologyTerm").isNull() ? "-" : column.get("ontologyTerm").asText()));
        return terms;
    }

    @Test
    void mappingMintsThePlmPartIriAndOnlyTheTagProperties() throws Exception {
        String mapping = getOk("/core/mapping", "text/turtle");

        assertThat(mapping).contains("@prefix map: <https://example.com/atelier/core/mapping#> .");
        assertThat(mapping).contains(
                "rr:subjectMap [ rr:template \"https://example.com/atelier/{plm}/part/{native_key}\" ; rr:class atelier:Part ]");
        assertThat(mapping).contains(
                "rr:predicateObjectMap [ rr:predicate atelier:taggedAt ; rr:objectMap [ rr:column \"tagged_at\" ] ]");
        assertThat(mapping).contains("atelier:jurisdiction", "atelier:releasableTo", "atelier:taggedBy");
        assertThat(mapping).contains(
                "rr:subjectMap [ rr:template \"https://example.com/atelier/product/{product_key}\" ; rr:class atelier:Product ]");
        assertThat(mapping).contains(
                "rr:predicateObjectMap [ rr:predicate atelier:partOf ; rr:objectMap [ rr:template \"https://example.com/atelier/product/{product_key}\" ] ]");
        assertThat(mapping).doesNotContain("ownedBy", "atelier:identifier");
        assertThat(mapping).as("the product's mass limit is a QUDT quantity in kilograms").contains(
                "rr:predicateObjectMap [ rr:predicate atelier:massLimit ; rr:objectMap [ rr:template \"https://example.com/atelier/product/{product_key}/mass-limit\" ] ]",
                "rr:predicateObjectMap [ rr:predicate qudt:numericValue ; rr:objectMap [ rr:column \"mass_limit_kg\" ; rr:datatype xsd:decimal ] ]",
                "rr:predicateObjectMap [ rr:predicate qudt:unit ; rr:object unit:KiloGM ]");
    }

    @Test
    void productsAreEveryProductWithItsPartCount() throws Exception {
        JsonNode products = getJson("/core/products");

        assertThat(products).hasSize(1);
        JsonNode ornithopter = products.get(0);
        List<String> fields = new ArrayList<>();
        ornithopter.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactly("key", "name", "frame", "partCount");
        assertThat(ornithopter.get("key").asText()).isEqualTo("ornithopter");
        assertThat(ornithopter.get("name").asText()).isEqualTo("Ornithopter ground demonstrator (Paris Manuscript B, f. 74v)");
        assertThat(ornithopter.get("frame").asText()).startsWith("x aft from the frame nose");
        assertThat(ornithopter.get("partCount").asInt()).as("every tagged part of the seed is in the product").isEqualTo(5);
    }

    @Test
    void nativeRowsOfProductTablesAreServedUnfiltered() throws Exception {
        JsonNode memberships = getJson("/core/tables/product_part?keys=SHARED-1");
        assertThat(memberships.get("keyColumn").asText()).isEqualTo("native_key");
        assertThat(memberships.get("columns")).extracting(JsonNode::asText).containsExactly("product_key", "plm", "native_key");
        List<String> rows = new ArrayList<>();
        memberships.get("rows").forEach(row -> rows.add(row.get(0).asText() + ":" + row.get(1).asText() + ":" + row.get(2).asText()));
        assertThat(rows).containsExactlyInAnyOrder("ornithopter:uk:SHARED-1", "ornithopter:es:SHARED-1");

        JsonNode products = getJson("/core/tables/product?keys=ornithopter");
        assertThat(products.get("keyColumn").asText()).isEqualTo("product_key");
        assertThat(products.get("rows")).hasSize(1);
        assertThat(products.get("rows").get(0).get(1).asText()).isEqualTo("Ornithopter ground demonstrator (Paris Manuscript B, f. 74v)");
    }

    @Test
    void officerGetsEveryTagAsADtoWithTheAgreedFields() throws Exception {
        JsonNode tags = getJson("/core/tags", "export-officer");

        assertThat(tags).hasSize(5);
        assertThat(tagKeys(tags)).containsExactly("de:HMOT-R-61110", "es:SHARED-1", "fr:FR-ORN-PCMD-001", "uk:PNL-ALL", "uk:SHARED-1");
        JsonNode first = tags.get(0);
        List<String> fields = new ArrayList<>();
        first.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactly("plm", "nativeKey", "jurisdiction", "releasableTo", "taggedBy", "taggedAt");
        assertThat(first.get("plm").asText()).isEqualTo("de");
        assertThat(first.get("nativeKey").asText()).isEqualTo("HMOT-R-61110");
        assertThat(first.get("jurisdiction").asText()).isEqualTo("EXPORT-LICENCE");
        assertThat(first.get("releasableTo").asText()).isEqualTo("LICENSED");
        assertThat(first.get("taggedBy").asText()).isEqualTo("DE");
        assertThat(first.get("taggedAt").asText()).matches("2026-09-01T08:05(:00)?(\\.0+)?(Z|\\+00:00)");
    }

    @Test
    void tagsFollowTheProfileLikeThePartsTheyClassify() throws Exception {
        // A German engineer holds ALL, EU and DE: not the French national part, not the licensed one.
        assertThat(tagKeys(getJson("/core/tags", "de-engineer"))).containsExactly("es:SHARED-1", "uk:PNL-ALL", "uk:SHARED-1");
        assertThat(tagKeys(getJson("/core/tags", "fr-engineer"))).containsExactly("es:SHARED-1", "fr:FR-ORN-PCMD-001", "uk:PNL-ALL", "uk:SHARED-1");
        assertThat(tagKeys(getJson("/core/tags", "programme-cleared"))).doesNotContain("de:HMOT-R-61110").hasSize(4);
    }

    @Test
    void missingOrUnknownProfileGetsOnlyTagsReleasableToAll() throws Exception {
        assertThat(tagKeys(getJson("/core/tags", null))).containsExactly("uk:PNL-ALL");
        assertThat(tagKeys(getJson("/core/tags", "nobody"))).containsExactly("uk:PNL-ALL");
    }

    @Test
    void nativeRowsOfPartTagFollowTheProfilesAudience() throws Exception {
        JsonNode unknown = getJson("/core/tables/part_tag?keys=SHARED-1,HMOT-R-61110,PNL-ALL");

        assertThat(unknown.get("keyColumn").asText()).isEqualTo("native_key");
        assertThat(unknown.get("columns")).extracting(JsonNode::asText)
                .containsExactly("plm", "native_key", "jurisdiction", "releasable_to", "tagged_by", "tagged_at");
        assertThat(rowKeys(unknown)).containsExactly("uk:PNL-ALL");
        assertThat(unknown.get("policy").get("profile").asText()).isEqualTo("unknown");
        assertThat(unknown.get("policy").has("error")).isFalse();

        assertThat(rowKeys(getJson("/core/tables/part_tag?keys=SHARED-1,HMOT-R-61110", "de-engineer")))
                .containsExactlyInAnyOrder("uk:SHARED-1", "es:SHARED-1");
        assertThat(rowKeys(getJson("/core/tables/part_tag?keys=SHARED-1,HMOT-R-61110", "export-officer")))
                .containsExactlyInAnyOrder("uk:SHARED-1", "es:SHARED-1", "de:HMOT-R-61110");
    }

    private static List<String> rowKeys(JsonNode body) {
        List<String> keys = new ArrayList<>();
        body.get("rows").forEach(row -> keys.add(row.get(0).asText() + ":" + row.get(1).asText()));
        return keys;
    }

    @Test
    void healthIsUnderTheCorePrefix() throws Exception {
        JsonNode health = getJson("/core/health");
        assertThat(health.get("status").asText()).isEqualTo("UP");
    }
}
