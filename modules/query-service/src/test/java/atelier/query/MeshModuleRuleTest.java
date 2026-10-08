// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import static org.assertj.core.api.Assertions.assertThat;

import atelier.query.validation.RuleValidator;
import atelier.query.validation.RuleViolation;
import java.math.BigDecimal;
import java.util.List;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The meshModule shape: two gears joined by a mechanical functional edge, each module in its site's unit, compared in
 * millimetres within 0.01 mm; the result is a warning on the driven gear naming the driving one.
 */
class MeshModuleRuleTest {
    private static final RuleValidator VALIDATOR = new RuleValidator();

    private static Resource gear(Model model, String plm, String id, String module, String unit) {
        Resource part = model.createResource(Atelier.partIri(plm, id)).addProperty(RDF.type, model.createResource(Atelier.ONT + "Part"))
                .addProperty(model.createProperty(Atelier.ONT + "label"), "Gear " + id);
        Resource q = model.createResource(part.getURI() + "/module").addProperty(RDF.type, model.createResource(Atelier.QUDT + "QuantityValue"))
                .addLiteral(model.createProperty(Atelier.QUDT + "numericValue"), model.createTypedLiteral(new BigDecimal(module)))
                .addProperty(model.createProperty(Atelier.QUDT + "unit"), model.createResource(Atelier.UNIT + unit));
        return part.addProperty(model.createProperty(Atelier.ONT + "gearModule"), q);
    }

    private static List<RuleViolation> mesh(String driverModule, String drivenModule, String drivenUnit, String flow) {
        Model model = ModelFactory.createDefaultModel();
        Resource driver = gear(model, "de", "D-1", driverModule, "MilliM");
        Resource driven = gear(model, "uk", "UK-2", drivenModule, drivenUnit);
        model.createResource(Atelier.DRIVE + "p/D01").addProperty(RDF.type, model.createResource(Atelier.ONT + "Drive"))
                .addProperty(model.createProperty(Atelier.ONT + "driver"), driver)
                .addProperty(model.createProperty(Atelier.ONT + "driven"), driven)
                .addProperty(model.createProperty(Atelier.ONT + "flow"), flow);
        return VALIDATOR.validate(model).stream().filter(r -> r.rule().equals("meshModule")).toList();
    }

    @ParameterizedTest(name = "{0} mm against {1} {2}: flagged {3}")
    @CsvSource({
            "2.5, 0.0984, IN, false",     // 2.49936 mm
            "2.5, 0.1, IN, true",         // 2.54 mm
            "2.5, 2.509, MilliM, false",
            "2.5, 2.511, MilliM, true"})
    void modulesAreComparedInMillimetresWithinAHundredth(String driver, String driven, String unit, boolean flagged) {
        assertThat(mesh(driver, driven, unit, "mechanical")).hasSize(flagged ? 1 : 0);
    }

    @Test
    void theFindingIsOnTheDrivenGearAndNamesTheDriver() {
        RuleViolation result = mesh("2.5", "0.1", "IN", "mechanical").get(0);
        assertThat(result.severity()).isEqualTo("Warning");
        assertThat(result.focusNode()).isEqualTo(Atelier.partIri("uk", "UK-2"));
        assertThat(result.value().getURI()).isEqualTo(Atelier.partIri("de", "D-1"));
        assertThat(result.message()).isEqualTo(
                "UK UK-2 Gear UK-2 has module 2.54 mm but is driven by DE D-1 Gear D-1, module 2.5 mm: meshing gears need one module");
    }

    @Test
    void onlyAMechanicalEdgeIsAMesh() {
        assertThat(mesh("2.5", "0.1", "IN", "electrical")).isEmpty();
    }
}
