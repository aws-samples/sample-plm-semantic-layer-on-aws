// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;

/**
 * A product file of data/products as its sources hold it, for {@link FixtureFederator}: every part, assembly, site kit and
 * software or document item a row of its site's part table (label, part type, the mass as data/generate.py writes it,
 * the stored number in the column's unit, pounds in the British PLM and kilograms elsewhere), its core membership and
 * tag (the owning site as {@code atelier:ownedBy}, as every part mapping states it), the product with its name and mass limit, and every bill-of-materials line, the non-geometric items under their
 * site kit. Items are normalised as data/items.py does: a local id UK/3511 is the row UK-3511, a version is part of the name.
 */
final class ProductFixture {
    private static final String ONT = Atelier.ONT;
    private static final String QUDT = "http://qudt.org/schema/qudt/";

    private ProductFixture() {}

    static Model of(String key) {
        JsonNode data;
        try {
            data = new ObjectMapper().readTree(Path.of("../../data/products/" + key + ".json").toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Model model = ModelFactory.createDefaultModel();
        Resource product = model.createResource(Atelier.productIri(key)).addProperty(RDF.type, model.createResource(ONT + "Product"))
                .addProperty(p(model, "label"), data.path("product").path("name").asText());
        JsonNode extended = data.path("extended");
        if (extended.has("massLimitKg")) {
            Resource limit = quantity(model, product.getURI() + "/mass-limit", extended.path("massLimitKg").asText(), "KiloGM");
            product.addProperty(p(model, "massLimit"), limit);
        }
        for (JsonNode part : data.path("parts")) {
            JsonNode ext = part.path("extended");
            Resource r = item(model, product, part.path("plm").asText(), part.path("id").asText(), part.path("name").asText(),
                    ext.path("type").asText("PART"), part.path("classification").path("releasableTo").asText());
            if (ext.hasNonNull("mass")) {
                String unit = "UK".equals(part.path("plm").asText()) ? "LB" : "KiloGM";
                r.addProperty(p(model, "mass"), quantity(model, r.getURI() + "/mass", ext.path("mass").asText().replace(',', '.'), unit));
            }
        }
        for (JsonNode a : extended.path("assemblies")) {
            item(model, product, a.path("plm").asText(), a.path("id").asText(), a.path("name").asText(), "ASSEMBLY", "ALL");
        }
        for (JsonNode line : extended.path("bomLines")) {
            line(model, line.path("parent").asText(), line.path("child").asText(), line.path("quantity").asText());
        }
        for (JsonNode i : extended.path("nonGeometricItems")) {
            String id = i.path("localId").asText().replace('/', '-');
            String name = i.path("name").asText() + (i.hasNonNull("version") ? " " + i.path("version").asText() : "");
            item(model, product, i.path("plm").asText(), id, name, i.path("type").asText(),
                    i.path("classification").path("releasableTo").asText("ALL"));
            line(model, i.path("siteKit").asText().replace('/', '-'), id, "1");
        }
        return model;
    }

    private static Resource item(Model model, Resource product, String plm, String id, String name, String type, String releasableTo) {
        return model.createResource(iri(plm, id)).addProperty(RDF.type, model.createResource(ONT + "Part"))
                .addProperty(p(model, "label"), name).addProperty(p(model, "partType"), type).addProperty(p(model, "ownedBy"), plm)
                .addProperty(p(model, "partOf"), product)
                .addProperty(p(model, "taggedBy"), plm).addProperty(p(model, "releasableTo"), releasableTo)
                .addProperty(p(model, "jurisdiction"), "NONE")
                .addProperty(p(model, "taggedAt"), model.createTypedLiteral("2026-09-01T08:00:00Z", XSDDatatype.XSDdateTime));
    }

    private static void line(Model model, String parent, String child, String quantity) {
        String plm = plmOf(model, parent);
        model.createResource(Atelier.DATA + plm + "/bomline/" + parent + "/" + child)
                .addProperty(RDF.type, model.createResource(Atelier.BOM_LINE))
                .addProperty(p(model, "parent"), model.createResource(iri(plm, parent)))
                .addProperty(p(model, "child"), model.createResource(iri(plm, child)))
                .addLiteral(p(model, "quantity"), model.createTypedLiteral(new BigDecimal(quantity)));
    }

    /** The site of an item already in the model; lines name items of one site. */
    private static String plmOf(Model model, String id) {
        for (String plm : Atelier.PLMS) {
            if (model.containsResource(model.createResource(iri(plm, id)))) return plm;
        }
        throw new IllegalArgumentException("no item " + id);
    }

    private static Resource quantity(Model model, String iri, String value, String unit) {
        return model.createResource(iri).addProperty(RDF.type, model.createResource(QUDT + "QuantityValue"))
                .addLiteral(model.createProperty(QUDT + "numericValue"), model.createTypedLiteral(new BigDecimal(value)))
                .addProperty(model.createProperty(QUDT + "unit"), model.createResource(Atelier.UNIT + unit));
    }

    static String iri(String plm, String id) {
        return Atelier.DATA + plm.toLowerCase() + "/part/" + id.replace(" ", "%20");
    }

    private static Property p(Model model, String local) {
        return model.createProperty(ONT + local);
    }
}
