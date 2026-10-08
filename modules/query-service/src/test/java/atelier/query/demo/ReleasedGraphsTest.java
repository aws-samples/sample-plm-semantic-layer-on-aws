// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.demo;

import static org.assertj.core.api.Assertions.assertThat;

import atelier.query.Atelier;
import java.nio.file.Path;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.junit.jupiter.api.Test;

/**
 * The repository's released graphs ({@code data/links.ttl}, {@code data/fileindex.ttl}), the content
 * the change feed diffs the live graphs against: the head hoop {@code FR-ORN-CERC-001} is a part of
 * the released links (its interfaces and features are declared) but has no {@code atelier:cadFile} in
 * the released file index, so a CAD file the demo publishes for it shows as an added triple until
 * the core service restores the release.
 */
class ReleasedGraphsTest {
    static final Resource HEAD_HOOP = ResourceFactory.createResource("https://example.com/atelier/fr/part/FR-ORN-CERC-001");
    static final Resource KEEL_BEAM = ResourceFactory.createResource("https://example.com/atelier/fr/part/FR-ORN-KEEL-001");
    static final Property CAD_FILE = ResourceFactory.createProperty(Atelier.CAD_FILE);
    static final Property BETWEEN_PART = ResourceFactory.createProperty(Atelier.ONT + "betweenPart");

    @Test
    void theReleasedFileIndexHasNoCadFileForTheHeadHoopWhileTheReleasedLinksDeclareIt() {
        ReleasedGraphs released = new ReleasedGraphs(Path.of("../../data"));
        assertThat(released.all()).extracting(ReleasedGraphs.Released::name).containsExactly("links", "fileindex");
        Model links = released.all().get(0).model();
        Model fileIndex = released.all().get(1).model();

        assertThat(links.contains(null, BETWEEN_PART, HEAD_HOOP)).as("the head hoop is a released part").isTrue();
        assertThat(fileIndex.contains(HEAD_HOOP, CAD_FILE)).as("no CAD file for the head hoop").isFalse();
        assertThat(fileIndex.listSubjectsWithProperty(CAD_FILE).toList()).as("other parts do have one").isNotEmpty();
        assertThat(fileIndex.getProperty(KEEL_BEAM, CAD_FILE).getString()).isEqualTo("cad/ornithopter/fr-keel-beam.stp");
    }
}
