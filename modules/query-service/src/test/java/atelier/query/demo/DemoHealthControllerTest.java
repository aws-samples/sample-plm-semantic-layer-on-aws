// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * The public health of the change feed: 200 for any profile and for none, the repository's
 * released graphs counted (the same files the image bundles), the three business events with their
 * publishers and subscribers, the core service as the writer of Atelier's graphs and the owner of the
 * reset route, and no event bus (this service publishes nothing).
 */
class DemoHealthControllerTest {
    static final ObjectMapper JSON = new ObjectMapper();
    static final ReleasedGraphs RELEASED = new ReleasedGraphs(Path.of("../../data"));

    @Test
    void answersAnyCallerWithTheWiringTheWriterAndTheBundledCounts() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new DemoHealthController(RELEASED)).build();
        long links = triples("links");
        long fileIndex = triples("fileindex");
        assertThat(links).isPositive();
        assertThat(fileIndex).isPositive();

        for (String profile : new String[] {"programme-cleared", null}) {
            MockHttpServletRequestBuilder request = get("/query/demo/health");
            if (profile != null) request.header("x-atelier-profile", profile);
            MockHttpServletResponse response = mvc.perform(request).andReturn().getResponse();

            assertThat(response.getStatus()).as(String.valueOf(profile)).isEqualTo(200);
            assertThat(JSON.readTree(response.getContentAsString())).isEqualTo(JSON.readTree("""
                    {"eventBus":null,"writer":"atelier-core",
                     "publishes":["atelier.plm/part.value.corrected (PLM services, logged by the links loader)",
                                  "atelier.plm/part.cad.published (PLM services)","atelier.graph/interface.link.added (Atelier core service)",
                                  "atelier.graph/equivalence.confirmed (Atelier core service)"],
                     "subscriptions":["atelier.plm/part.value.corrected -> links loader -> atelier_core.demo_change",
                                      "atelier.plm/part.cad.published -> links loader -> file index"],
                     "releasedGraphs":{"links":%d,"fileindex":%d},"changeLog":"atelier_core.demo_change",
                     "reset":"POST /core/demo/reset (export-officer)"}""".formatted(links, fileIndex)));
            assertThat(response.getContentAsString()).as("eventBus is present and null, not omitted").contains("\"eventBus\":null");
        }
    }

    /** The triple count of one released file, parsed here rather than asked of {@link ReleasedGraphs}. */
    private static long triples(String name) {
        Model model = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model, "../../data/" + name + ".ttl", Lang.TURTLE);
        return model.size();
    }
}
