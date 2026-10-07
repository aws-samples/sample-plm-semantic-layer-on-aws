// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.core.graph;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The released link store: {@code links.ttl}, {@code fileindex.ttl} and {@code labels.ttl} as bundled in the image
 * under {@code RELEASED_GRAPHS_DIR} ({@code /app/data}), each the content of one named graph. Read
 * once at start-up: the Turtle bytes are what the reset PUTs over the live graph, the parsed model
 * gives the triple count the health endpoint reports.
 */
@Component
public class ReleasedGraphs {

    /** One released graph: its short name as answers report it, its named-graph IRI, its Turtle and the model parsed from it. */
    public record Released(String name, String graph, byte[] turtle, Model model) {
        public long triples() {
            return model.size();
        }
    }

    /** Named-graph IRI by short name, in reporting order. */
    static final Map<String, String> GRAPHS = new LinkedHashMap<>();

    static {
        GRAPHS.put("links", Graphs.LINKS);
        GRAPHS.put("fileindex", Graphs.FILE_INDEX);
        GRAPHS.put("labels", Graphs.LABELS);
    }

    private static final Logger log = LoggerFactory.getLogger(ReleasedGraphs.class);

    private final List<Released> released;

    @Autowired
    public ReleasedGraphs(@Value("${RELEASED_GRAPHS_DIR:/app/data}") String dir) {
        this(Path.of(dir));
    }

    public ReleasedGraphs(Path dir) {
        List<Released> read = new ArrayList<>();
        try {
            for (Map.Entry<String, String> graph : GRAPHS.entrySet()) {
                read.add(read(dir.resolve(graph.getKey() + ".ttl"), graph.getKey(), graph.getValue()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the released graphs in " + dir, e);
        }
        released = List.copyOf(read);
        released.forEach(r -> log.info("Released graph {} <{}>: {} triples", r.name(), r.graph(), r.triples()));
    }

    private static Released read(Path file, String name, String graph) throws IOException {
        byte[] turtle = Files.readAllBytes(file);
        Model model = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model, new ByteArrayInputStream(turtle), Lang.TURTLE);
        return new Released(name, graph, turtle, model);
    }

    /** The released graphs, links, file index, then labels. */
    public List<Released> all() {
        return released;
    }

    /** Triple count of each released graph by short name, links, file index, then labels. */
    public Map<String, Long> triples() {
        Map<String, Long> counts = new LinkedHashMap<>();
        released.forEach(r -> counts.put(r.name(), r.triples()));
        return counts;
    }
}
