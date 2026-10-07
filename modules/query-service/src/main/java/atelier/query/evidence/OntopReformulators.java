// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.evidence;

import atelier.query.federation.Endpoints;
import it.unibz.inf.ontop.answering.reformulation.QueryReformulator;
import it.unibz.inf.ontop.injection.OntopSQLOWLAPIConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * One Ontop {@link QueryReformulator} per Ontop source with an endpoint (each PLM and the Atelier
 * core graph), built from the R2RML mapping that source's Ontop endpoint runs
 * ({@code <source>.r2rml.ttl}), the ontology and the source's serialised database metadata
 * ({@code <source>-db-metadata.json}). Building runs in the background, started once the
 * application is ready (or by the first translation request, if earlier), so startup, health and
 * answers never wait for it; only the evidence endpoint waits, and only for the source it
 * translates. No database connection is opened: the JDBC URL exists solely so that Ontop selects
 * the PostgreSQL dialect; {@link JdbcComponents} writes it once it has accepted the source code
 * as a database name. A source whose reformulator cannot be built (its files are
 * absent, for instance) reports {@code null} SQL.
 */
@Component
public class OntopReformulators implements OntopSql {
    private static final Logger log = LoggerFactory.getLogger(OntopReformulators.class);

    private final List<String> sources;
    private final Path mappingsDir;
    private final Path ontology;
    private final Path metadataDir;
    private final Map<String, String> sqlByQuery = new ConcurrentHashMap<>();
    private Map<String, CompletableFuture<QueryReformulator>> reformulators;

    public OntopReformulators(Endpoints endpoints,
                              @Value("${atelier.ontop.mappings-dir}") String mappingsDir,
                              @Value("${atelier.ontop.ontology}") String ontology,
                              @Value("${atelier.ontop.metadata-dir}") String metadataDir) {
        this.sources = endpoints.sources().stream().filter(source -> endpoints.ontop(source) != null).toList();
        this.mappingsDir = Path.of(mappingsDir);
        this.ontology = Path.of(ontology);
        this.metadataDir = Path.of(metadataDir);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startLoading() {
        reformulators();
    }

    @Override
    public String reformulate(String source, String sparql) {
        CompletableFuture<QueryReformulator> loading = reformulators().get(source);
        QueryReformulator reformulator = loading == null ? null : loading.join();
        if (reformulator == null) return null;
        String key = source + '\n' + sparql;
        String sql = sqlByQuery.get(key);
        if (sql == null) {
            sql = NativeSql.of(reformulator, sparql);
            if (sql != null) sqlByQuery.put(key, sql);
        }
        return sql;
    }

    /** The per-source builds, started on first call: one daemon thread per source, released once built. */
    private synchronized Map<String, CompletableFuture<QueryReformulator>> reformulators() {
        if (reformulators == null) {
            ExecutorService loaders = Executors.newFixedThreadPool(Math.max(1, sources.size()),
                    Thread.ofPlatform().daemon().name("ontop-loader-", 0).factory());
            reformulators = new LinkedHashMap<>();
            for (String source : sources) {
                reformulators.put(source, CompletableFuture.supplyAsync(() -> load(source), loaders));
            }
            loaders.shutdown();
        }
        return reformulators;
    }

    private QueryReformulator load(String source) {
        Path metadata = metadataDir.resolve(source + "-db-metadata.json");
        if (!Files.isRegularFile(metadata)) {
            log.warn("No Ontop reformulator for {}: metadata file {} is missing", source, metadata);
            return null;
        }
        String database = JdbcComponents.database(source, "Ontop source");
        long start = System.nanoTime();
        try {
            QueryReformulator reformulator = OntopSQLOWLAPIConfiguration.defaultBuilder()
                    .r2rmlMappingFile(mappingsDir.resolve(source + ".r2rml.ttl").toFile())
                    .ontologyFile(ontology.toFile())
                    .dbMetadataFile(metadata.toFile())
                    .jdbcUrl(JdbcComponents.placeholderUrl(database))
                    .jdbcUser("none")
                    .jdbcPassword("none")
                    .jdbcDriver("org.postgresql.Driver")
                    .build()
                    .loadQueryReformulator();
            log.info("Ontop reformulator for {} ready in {} ms", source, (System.nanoTime() - start) / 1_000_000);
            return reformulator;
        } catch (Exception e) {
            log.warn("No Ontop reformulator for {}: {}", source, e.toString());
            return null;
        }
    }
}
