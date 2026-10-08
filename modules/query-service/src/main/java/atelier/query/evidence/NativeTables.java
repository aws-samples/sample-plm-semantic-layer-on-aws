// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.evidence;

import atelier.query.Atelier;
import atelier.query.api.Json;
import atelier.query.federation.Endpoints;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.jena.graph.Graph;

/**
 * Native rows behind an Ontop arm's triples. For a PLM, a subject {@code .../{plm}/part/{key}} is
 * a row of its part table; {@code .../{plm}/plug|fastener|coupling/{key}} and the value nodes
 * minted under it ({@code .../position/x}, {@code .../diameter}) are a row of the matching feature
 * table; an external reference {@code .../{plm}/ref/{id}} is a row of the PLM's reference table; a bill-of-materials line {@code .../{plm}/bomline/{parent}/{child}} is a row of the PLM's line relation keyed
 * by its child (the DE part row, which names its parent; the FR nomenclature; the ES view over the parent's document,
 * and the parent's pieza row that holds the document; the UK bom_line). For the Atelier core graph, a part subject is a row of {@code part_tag} for its tag predicates
 * and a row of {@code product_part} for its {@code atelier:partOf}, both keyed by the part's native key,
 * and a product subject is a row of {@code product}. Table names are the native schemas of
 * docs/contract.md; keys are percent-decoded.
 */
final class NativeTables {
    static final String PART_TAG = "part_tag";
    static final String PRODUCT_PART = "product_part";
    static final String PRODUCT = "product";

    /** Native table per IRI segment (part, plug, fastener, coupling, ref), per PLM. */
    private static final Map<String, Map<String, String>> TABLES = Map.of(
            "fr", tables("piece", "connecteur", "fixation", "raccord_hydraulique", "reference_externe"),
            "de", tables("bauteil", "stecker", "befestiger", "hydraulikkupplung", "externer_verweis"),
            "uk", tables("component", "harness_connector", "fastener", "hyd_coupling", "external_ref"),
            "es", tables("pieza", "conector", "remache", "acoplamiento", "referencia_externa"));

    /** Native relation of a bill-of-materials line per PLM, keyed by the line's child. */
    private static final Map<String, String> LINES = Map.of(
            "fr", "nomenclature", "de", "bauteil", "uk", "bom_line", "es", "linea_lista_materiales");

    private static final Pattern ROW = Pattern.compile(
            "^" + Pattern.quote(Atelier.DATA) + "([a-z]+)/(part|plug|fastener|coupling|ref)/([^/]+)(?:/.*)?$");
    private static final Pattern LINE = Pattern.compile(
            "^" + Pattern.quote(Atelier.DATA) + "([a-z]+)/bomline/([^/]+)/([^/]+)$");

    private NativeTables() {}

    static List<Json.Table> of(String source, Graph triples) {
        if (Endpoints.CORE.equals(source)) {
            TreeSet<String> tagged = new TreeSet<>();
            TreeSet<String> placed = new TreeSet<>();
            TreeSet<String> products = new TreeSet<>();
            triples.find().forEachRemaining(t -> {
                if (!t.getSubject().isURI()) return;
                String subject = t.getSubject().getURI();
                Matcher m = ROW.matcher(subject);
                if (m.matches() && "part".equals(m.group(2))) {
                    (Atelier.PART_OF.equals(t.getPredicate().getURI()) ? placed : tagged).add(decode(m.group(3)));
                } else if (subject.startsWith(Atelier.PRODUCT)) {
                    products.add(decode(subject.substring(Atelier.PRODUCT.length())));
                }
            });
            List<Json.Table> tables = new ArrayList<>();
            if (!tagged.isEmpty()) tables.add(new Json.Table(Endpoints.CORE, PART_TAG, List.copyOf(tagged)));
            if (!placed.isEmpty()) tables.add(new Json.Table(Endpoints.CORE, PRODUCT_PART, List.copyOf(placed)));
            if (!products.isEmpty()) tables.add(new Json.Table(Endpoints.CORE, PRODUCT, List.copyOf(products)));
            return tables;
        }
        Map<String, TreeSet<String>> keysByTable = new LinkedHashMap<>();
        TABLES.get(source).values().forEach(table -> keysByTable.put(table, new TreeSet<>()));
        keysByTable.putIfAbsent(LINES.get(source), new TreeSet<>());
        rows(triples, (plm, segment, key) -> {
            if (plm.equals(source)) keysByTable.get(TABLES.get(source).get(segment)).add(key);
        });
        triples.find().forEachRemaining(t -> {
            Matcher m = t.getSubject().isURI() ? LINE.matcher(t.getSubject().getURI()) : null;
            if (m == null || !m.matches() || !m.group(1).equals(source)) return;
            keysByTable.get(LINES.get(source)).add(decode(m.group(3)));
            if ("es".equals(source)) keysByTable.get(TABLES.get(source).get("part")).add(decode(m.group(2)));
        });
        List<Json.Table> tables = new ArrayList<>();
        keysByTable.forEach((table, keys) -> {
            if (!keys.isEmpty()) tables.add(new Json.Table(source, table, List.copyOf(keys)));
        });
        return tables;
    }

    private interface Row {
        void accept(String plm, String segment, String key);
    }

    private static void rows(Graph triples, Row row) {
        triples.find().forEachRemaining(t -> {
            if (!t.getSubject().isURI()) return;
            Matcher m = ROW.matcher(t.getSubject().getURI());
            if (m.matches()) row.accept(m.group(1), m.group(2), decode(m.group(3)));
        });
    }

    private static Map<String, String> tables(String part, String plug, String fastener, String coupling, String ref) {
        Map<String, String> bySegment = new LinkedHashMap<>();
        bySegment.put("part", part);
        bySegment.put("plug", plug);
        bySegment.put("fastener", fastener);
        bySegment.put("coupling", coupling);
        bySegment.put("ref", ref);
        return bySegment;
    }

    private static String decode(String segment) {
        return URI.create("x:/" + segment).getPath().substring(1);
    }
}
