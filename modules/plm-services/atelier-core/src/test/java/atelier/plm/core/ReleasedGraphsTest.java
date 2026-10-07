// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.core;

import atelier.plm.core.graph.Graphs;
import atelier.plm.core.graph.ReleasedGraphs;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The repository's released graphs ({@code data/links.ttl}, {@code data/fileindex.ttl}, {@code data/labels.ttl}), the bytes
 * the reset PUTs over the live graphs: the head hoop {@code FR-ORN-CERC-001} is a part of the
 * released links (its interfaces declare it) but has no {@code atelier:cadFile} in the released file
 * index, so a reset removes the CAD file the demo published for it. The triple counts pin the
 * generated files.
 */
class ReleasedGraphsTest {

    static final Resource HEAD_HOOP = ResourceFactory.createResource("https://example.com/atelier/fr/part/FR-ORN-CERC-001");
    static final Property CAD_FILE = ResourceFactory.createProperty(Graphs.ONT + "cadFile");
    static final Property BETWEEN_PART = ResourceFactory.createProperty(Graphs.ONT + "betweenPart");

    @Test
    void theReleasedFileIndexHasNoCadFileForTheHeadHoopWhileTheReleasedLinksDeclareIt() {
        ReleasedGraphs released = new ReleasedGraphs(Path.of("../../../data"));
        assertThat(released.all()).extracting(ReleasedGraphs.Released::name).containsExactly("links", "fileindex", "labels");
        assertThat(released.all()).extracting(ReleasedGraphs.Released::graph).containsExactly(Graphs.LINKS, Graphs.FILE_INDEX, Graphs.LABELS);
        Model links = released.all().get(0).model();
        Model fileIndex = released.all().get(1).model();
        Model labels = released.all().get(2).model();

        assertThat(links.contains(null, BETWEEN_PART, HEAD_HOOP)).as("the head hoop is a released part").isTrue();
        assertThat(fileIndex.contains(HEAD_HOOP, CAD_FILE)).as("no CAD file for the head hoop").isFalse();
        assertThat(fileIndex.listSubjectsWithProperty(CAD_FILE).toList()).as("other parts do have one").isNotEmpty();
        assertThat(fileIndex.contains(ResourceFactory.createResource("https://example.com/atelier/fr/part/FR-ORN-KEEL-001"), CAD_FILE)).isTrue();
        assertThat(new String(released.all().get(1).turtle(), StandardCharsets.UTF_8)).as("the bytes the reset PUTs").doesNotContain("FR-ORN-CERC-001");

        assertThat(released.triples()).containsExactly(
                java.util.Map.entry("links", links.size()), java.util.Map.entry("fileindex", fileIndex.size()),
                java.util.Map.entry("labels", labels.size()));
    }
}
