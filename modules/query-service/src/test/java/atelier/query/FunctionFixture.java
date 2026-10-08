// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

/**
 * A product of data/products as {@link ProductFixture} holds it, with the link store's content as data/generate.py writes
 * it (data/links.ttl: the interfaces, labelled as the fixture federator reads them, the functional edges and rated
 * speeds) and each gear's tooth count and module on its part row, in its site's unit, from the product file.
 */
final class FunctionFixture {
    private FunctionFixture() {}

    static Model of(String key) {
        Model model = ProductFixture.of(key);
        Model links = RDFDataMgr.loadModel("../../data/links.ttl");
        links.listSubjectsWithProperty(RDF.type, links.createResource(Atelier.ONT + "Interface")).toList()
                .forEach(i -> links.add(i, links.createProperty(Atelier.ONT + "label"), i.getRequiredProperty(RDFS.label).getObject()));
        model.add(links);
        JsonNode data;
        try {
            data = new ObjectMapper().readTree(Path.of("../../data/products/" + key + ".json").toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (JsonNode part : data.path("parts")) {
            JsonNode gear = part.path("extended").path("gear");
            if (gear.isMissingNode()) continue;
            Resource r = model.createResource(ProductFixture.iri(part.path("plm").asText(), part.path("id").asText()));
            r.addLiteral(model.createProperty(Atelier.ONT + "toothCount"), model.createTypedLiteral(gear.path("teeth").asInt()));
            String unit = "UK".equals(part.path("plm").asText()) ? "IN" : "MilliM";
            Resource q = model.createResource(r.getURI() + "/module").addProperty(RDF.type, model.createResource(Atelier.QUDT + "QuantityValue"))
                    .addLiteral(model.createProperty(Atelier.QUDT + "numericValue"), model.createTypedLiteral(new BigDecimal(gear.path("module").asText())))
                    .addProperty(model.createProperty(Atelier.QUDT + "unit"), model.createResource(Atelier.UNIT + unit));
            r.addProperty(model.createProperty(Atelier.ONT + "gearModule"), q);
        }
        return model;
    }
}
