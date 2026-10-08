// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.preview;

import atelier.query.Atelier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.RDFDataMgr;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Where a native cell surfaces in the merged graph, read from the R2RML each PLM's Ontop endpoint runs
 * ({@code <plm>.r2rml.ttl} in the mappings directory): per table, the row's subject (the IRI template over the key
 * column and the class it states) and, per column, the triples it produces. A column read with {@code rr:column} is a
 * literal of its subject, typed by {@code rr:datatype} (a plain literal when none); a quantity's value column is the
 * {@code qudt:numericValue} of the quantity node minted under the row; a unit column is the {@code qudt:unit} IRI
 * template of every quantity node of the row that names it. A site's mapping is read once, on first use.
 */
@Component
public class CellMappings {
    private static final String RR = "http://www.w3.org/ns/r2rml#";
    private static final String QUANTITY_VALUE = Atelier.QUDT + "QuantityValue";
    private static final String UNIT = Atelier.QUDT + "unit";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^}]+)}");

    /** A row's subject: the IRI template over the table's key column and the class the subject states. */
    public record Row(String template, String keyColumn, String type) {}

    /**
     * One triple a column produces: its subject template, its predicate and either the literal's datatype (null for a
     * plain literal) or, for a unit column, the IRI template the column fills.
     */
    public record Target(String subject, String predicate, String datatype, String iriTemplate) {
        public boolean unit() {
            return iriTemplate != null;
        }
    }

    /** A quantity node: the template of the subject that links it, the linking predicate, and its fixed unit IRI (null when a column holds it). */
    public record Quantity(String parent, String predicate, String unit) {}

    /** One native table: its row subject and the triples of each column. */
    public record Table(Row row, Map<String, List<Target>> columns) {}

    /** One site's mapping: its tables and its quantity nodes by subject template. */
    record Site(Map<String, Table> tables, Map<String, Quantity> quantities) {}

    private final Path dir;
    private final Map<String, Site> sites = new ConcurrentHashMap<>();

    @Autowired
    public CellMappings(@Value("${atelier.ontop.mappings-dir}") String dir) {
        this(Path.of(dir));
    }

    public CellMappings(Path dir) {
        this.dir = dir;
    }

    public Optional<Table> table(String plm, String table) {
        return Optional.ofNullable(site(plm).tables().get(table));
    }

    /** The quantity node minted at {@code template}; empty when the template mints no quantity. */
    public Optional<Quantity> quantity(String plm, String template) {
        return Optional.ofNullable(site(plm).quantities().get(template));
    }

    private Site site(String plm) {
        return sites.computeIfAbsent(plm, p -> {
            Path file = dir.resolve(p + ".r2rml.ttl");
            if (!Files.isRegularFile(file)) throw new IllegalStateException("no R2RML mapping for " + p + " at " + file);
            return read(RDFDataMgr.loadModel(file.toString()));
        });
    }

    static Site read(Model mapping) {
        Property logicalTable = mapping.createProperty(RR + "logicalTable");
        Property tableName = mapping.createProperty(RR + "tableName");
        Property subjectMap = mapping.createProperty(RR + "subjectMap");
        Property template = mapping.createProperty(RR + "template");
        Property rrClass = mapping.createProperty(RR + "class");
        Property pom = mapping.createProperty(RR + "predicateObjectMap");
        Property predicate = mapping.createProperty(RR + "predicate");
        Property objectMap = mapping.createProperty(RR + "objectMap");
        Property object = mapping.createProperty(RR + "object");
        Property column = mapping.createProperty(RR + "column");
        Property datatype = mapping.createProperty(RR + "datatype");

        Map<String, Row> rows = new LinkedHashMap<>();
        Map<String, Map<String, List<Target>>> columns = new LinkedHashMap<>();
        Map<String, String> fixedUnits = new LinkedHashMap<>();
        Map<String, Quantity> links = new LinkedHashMap<>();
        List<Resource> maps = mapping.listSubjectsWithProperty(logicalTable).toList().stream()
                .sorted(java.util.Comparator.comparing(Resource::getURI)).toList();
        for (Resource map : maps) {
            String table = text(map.getPropertyResourceValue(logicalTable), tableName);
            Resource subject = map.getPropertyResourceValue(subjectMap);
            String subjectTemplate = text(subject, template);
            if (table == null || subjectTemplate == null) continue;
            Resource type = subject.getPropertyResourceValue(rrClass);
            String typeIri = type == null ? null : type.getURI();
            List<String> keys = placeholders(subjectTemplate);
            if (typeIri != null && !QUANTITY_VALUE.equals(typeIri) && keys.size() == 1) {
                rows.putIfAbsent(table, new Row(subjectTemplate, keys.get(0), typeIri));
            }
            Map<String, List<Target>> byColumn = columns.computeIfAbsent(table, t -> new LinkedHashMap<>());
            for (Statement s : map.listProperties(pom).toList()) {
                Resource p = s.getResource();
                String pred = p.getPropertyResourceValue(predicate).getURI();
                Resource o = p.getPropertyResourceValue(objectMap);
                if (o == null) {
                    Statement constant = p.getProperty(object);
                    if (UNIT.equals(pred) && constant != null && constant.getObject().isURIResource()) {
                        fixedUnits.put(subjectTemplate, constant.getResource().getURI());
                    }
                    continue;
                }
                String col = text(o, column);
                String iri = text(o, template);
                if (col != null) {
                    Resource dt = o.getPropertyResourceValue(datatype);
                    byColumn.computeIfAbsent(col, c -> new ArrayList<>()).add(new Target(subjectTemplate, pred, dt == null ? null : dt.getURI(), null));
                } else if (iri != null && UNIT.equals(pred)) {
                    for (String c : placeholders(iri)) {
                        byColumn.computeIfAbsent(c, k -> new ArrayList<>()).add(new Target(subjectTemplate, pred, null, iri));
                    }
                } else if (iri != null) {
                    links.put(iri, new Quantity(subjectTemplate, pred, null));
                }
            }
        }
        Map<String, Table> tables = new LinkedHashMap<>();
        rows.forEach((table, row) -> tables.put(table, new Table(row, columns.getOrDefault(table, Map.of()))));
        Map<String, Quantity> quantities = new LinkedHashMap<>();
        maps.forEach(map -> {
            String t = text(map.getPropertyResourceValue(subjectMap), template);
            Resource type = map.getPropertyResourceValue(subjectMap).getPropertyResourceValue(rrClass);
            Quantity link = t == null ? null : links.get(t);
            if (link != null && type != null && QUANTITY_VALUE.equals(type.getURI())) {
                quantities.put(t, new Quantity(link.parent(), link.predicate(), fixedUnits.get(t)));
            }
        });
        return new Site(tables, quantities);
    }

    /** The column names an R2RML template fills, in order. */
    public static List<String> placeholders(String template) {
        List<String> names = new ArrayList<>();
        Matcher m = PLACEHOLDER.matcher(template);
        while (m.find()) names.add(m.group(1));
        return names;
    }

    /** The template with {@code column} filled by {@code value}, percent-encoded as an R2RML template encodes a column value. */
    public static String fill(String template, String column, String value) {
        return template.replace("{" + column + "}", encode(value));
    }

    /** The regular expression of the IRIs a template mints with {@code column} set to {@code value} and every other column free. */
    public static Pattern pattern(String template, String column, String value) {
        StringBuilder out = new StringBuilder();
        Matcher m = PLACEHOLDER.matcher(template);
        int at = 0;
        while (m.find()) {
            out.append(Pattern.quote(template.substring(at, m.start())));
            out.append(m.group(1).equals(column) ? Pattern.quote(encode(value)) : "[^/]+");
            at = m.end();
        }
        return Pattern.compile(out.append(Pattern.quote(template.substring(at))).toString());
    }

    /** Every byte but the unreserved characters percent-encoded, as {@link Atelier#partIri} encodes a key. */
    static String encode(String value) {
        String iri = Atelier.partIri("x", value);
        return iri.substring(iri.indexOf("/part/") + "/part/".length());
    }

    private static String text(Resource node, Property property) {
        if (node == null) return null;
        Statement s = node.getProperty(property);
        RDFNode o = s == null ? null : s.getObject();
        return o != null && o.isLiteral() ? o.asLiteral().getLexicalForm() : null;
    }
}
