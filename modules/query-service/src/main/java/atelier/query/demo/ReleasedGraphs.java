// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.demo;

import atelier.query.Atelier;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The released link store: {@code links.ttl} and {@code fileindex.ttl} as bundled in the image
 * under {@code RELEASED_GRAPHS_DIR} ({@code /app/data}; the repository's {@code data/} for local
 * runs), each the content of one named graph. Parsed once at start-up: the model is what the change
 * feed diffs the live graph against and what the health reports the size of. The same files are
 * bundled in the core service's image, which restores them on reset.
 */
@Component
public class ReleasedGraphs {
    /** One released graph: its short name as answers report it, its named-graph IRI and the model parsed from its file. */
    public record Released(String name, String graph, Model model) {
        public long triples() {
            return model.size();
        }
    }

    /** Named-graph IRI by short name, in reporting order. */
    static final Map<String, String> GRAPHS = new LinkedHashMap<>();

    static {
        GRAPHS.put("links", Atelier.LINKS_GRAPH);
        GRAPHS.put("fileindex", Atelier.FILE_INDEX_GRAPH);
    }

    private static final Logger log = LoggerFactory.getLogger(ReleasedGraphs.class);

    private final List<Released> released;

    @Autowired
    public ReleasedGraphs(@Value("${RELEASED_GRAPHS_DIR:../../data}") String dir) {
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
        Model model = ModelFactory.createDefaultModel();
        try (InputStream in = Files.newInputStream(file)) {
            RDFDataMgr.read(model, in, Lang.TURTLE);
        }
        return new Released(name, graph, model);
    }

    /** The released graphs, links then file index. */
    public List<Released> all() {
        return released;
    }
}
