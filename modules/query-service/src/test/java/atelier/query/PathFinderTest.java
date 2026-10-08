// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query;

import static org.assertj.core.api.Assertions.assertThat;

import atelier.query.paths.PathFinder;
import atelier.query.paths.Steps;
import java.util.List;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;

/** The breadth-first search over interfaces: its bounds (12 steps, 5 paths) and a hidden part breaking a path. */
class PathFinderTest {
    private final Model model = ModelFactory.createDefaultModel();

    private String part(String id, boolean visible) {
        Resource r = model.createResource(Atelier.partIri("fr", id));
        if (visible) r.addProperty(RDF.type, model.createResource(Atelier.ONT + "Part"));
        return r.getURI();
    }

    private void joint(String id, String a, String b) {
        model.createResource(Atelier.interfaceIri("p", id))
                .addProperty(model.createProperty(Atelier.ONT + "betweenPart"), model.createResource(a))
                .addProperty(model.createProperty(Atelier.ONT + "betweenPart"), model.createResource(b));
    }

    private PathFinder.Found between(String a, String b) {
        return new PathFinder(model, new Steps(model, List.of())).between(a, b);
    }

    @Test
    void aChainLongerThanTwelveStepsHasNoPath() {
        String previous = part("P00", true);
        String first = previous;
        for (int i = 1; i <= 13; i++) {
            String next = part(String.format("P%02d", i), true);
            joint(String.format("IF-%02d", i), previous, next);
            previous = next;
        }
        assertThat(between(first, previous).paths()).isEmpty();
        assertThat(between(first, part("P12", true)).paths()).singleElement().satisfies(p -> assertThat(p.length()).isEqualTo(12));
    }

    @Test
    void atMostFiveShortestPathsInInterfaceOrder() {
        String a = part("A", true);
        String b = part("B", true);
        for (int i = 1; i <= 7; i++) {
            String middle = part("M" + i, true);
            joint("IF-A" + i, a, middle);
            joint("IF-B" + i, middle, b);
        }
        joint("IF-LONG1", a, part("L", true));
        PathFinder.Found found = between(a, b);
        assertThat(found.paths()).hasSize(5).allSatisfy(p -> assertThat(p.length()).isEqualTo(2));
        assertThat(found.paths()).extracting(p -> p.steps().get(0).joint().id()).containsExactly("IF-A1", "IF-A2", "IF-A3", "IF-A4", "IF-A5");
    }

    @Test
    void aHiddenPartBreaksOrLengthensThePathAndIsNeverNamed() {
        String a = part("A", true);
        String b = part("B", true);
        String hidden = part("SECRET", false);
        joint("IF-1", a, hidden);
        joint("IF-2", hidden, b);
        PathFinder.Found broken = between(a, b);
        assertThat(broken.paths()).isEmpty();
        assertThat(broken.notes()).singleElement().satisfies(n -> assertThat(n).contains("runs through a part not visible to your profile"));
        String c = part("C", true);
        String d = part("D", true);
        joint("IF-3", a, c);
        joint("IF-4", c, d);
        joint("IF-5", d, b);
        PathFinder.Found longer = between(a, b);
        assertThat(longer.paths()).singleElement().satisfies(p -> assertThat(p.length()).isEqualTo(3));
        assertThat(longer.notes()).singleElement().satisfies(n -> assertThat(n).contains("A shorter path of 2 steps"));
        assertThat(longer.toString()).doesNotContain("SECRET");
    }
}
