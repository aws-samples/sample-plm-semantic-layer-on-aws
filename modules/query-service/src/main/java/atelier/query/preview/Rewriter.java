// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.preview;

import atelier.query.Atelier;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;

/**
 * The checked cells written into a copy of the merged graph, each to the triples the site's R2RML produces for it
 * ({@link CellMappings}): a text, number or flag column replaces its literal; a quantity's value column replaces the
 * {@code qudt:numericValue} of the row's quantity node (minting the node, its link and its fixed unit when the stored
 * value was empty, removing them when the cell is cleared, as Ontop states no node for an empty value); a unit column
 * replaces the {@code qudt:unit} of every quantity node of the row that reads it. A cell names its row by key; a row the
 * caller may not see is not in the redacted graph and is refused, in the words used for a row that does not exist.
 */
final class Rewriter {
    private static final List<String> HOLDERS = List.of("onPart", "fromPart", "offersPart");

    /** A cell whose value its site's catalogue accepts, as the column takes it. */
    record Checked(String where, PreviewJson.Cell cell, Object value) {}

    /** The records the cells rewrote, the parts holding them, and whether a part's mass changed. */
    record Touched(Set<String> records, Set<String> parts, boolean masses) {}

    record Outcome(List<PreviewJson.Rewrite> rewrites, Touched touched) {}

    private final Model model;
    private final CellMappings mappings;
    private final String profile;
    private final Set<String> records = new LinkedHashSet<>();
    private final Set<String> parts = new LinkedHashSet<>();
    private boolean masses;

    private Rewriter(Model model, CellMappings mappings, String profile) {
        this.model = model;
        this.mappings = mappings;
        this.profile = profile;
    }

    /** Applies {@code cells} to {@code model} in order; a refused cell leaves the model partly rewritten, and the caller discards it. */
    static Outcome apply(Model model, CellMappings mappings, String profile, List<Checked> cells) {
        Rewriter rewriter = new Rewriter(model, mappings, profile);
        List<PreviewJson.Rewrite> rewrites = new ArrayList<>();
        for (Checked cell : cells) rewrites.add(rewriter.rewrite(cell));
        return new Outcome(rewrites, new Touched(Set.copyOf(rewriter.records), Set.copyOf(rewriter.parts), rewriter.masses));
    }

    /**
     * The table of a cell whose column reaches the merged graph; refused when the site's mapping states no subject for the
     * table's rows, or the column is the row's key, or it produces no triple (a foreign key, an unmapped column).
     */
    static CellMappings.Table mapped(CellMappings mappings, Checked checked) {
        PreviewJson.Cell cell = checked.cell();
        CellMappings.Table table = mappings.table(cell.plm(), cell.table()).orElseThrow(() -> Vocabulary.refused(checked.where(),
                "the " + cell.plm().toUpperCase() + " mapping states no subject for the rows of " + cell.table()));
        if (cell.column().equals(table.row().keyColumn())) throw Vocabulary.refused(checked.where(), cell.column() + " is the key column of " + cell.table());
        if (table.columns().getOrDefault(cell.column(), List.of()).isEmpty()) {
            throw Vocabulary.refused(checked.where(), cell.column() + " of " + cell.table() + " reaches no triple the rules read (a foreign key or an unmapped column)");
        }
        return table;
    }

    private PreviewJson.Rewrite rewrite(Checked checked) {
        PreviewJson.Cell cell = checked.cell();
        CellMappings.Table table = mapped(mappings, checked);
        CellMappings.Row row = table.row();
        List<CellMappings.Target> targets = table.columns().get(cell.column());
        Resource record = model.createResource(CellMappings.fill(row.template(), row.keyColumn(), cell.key()));
        if (!model.contains(record, RDF.type)) {
            throw Vocabulary.refused(checked.where(), "no row " + cell.key() + " of " + cell.table() + " the " + profile + " profile may see");
        }
        records.add(record.getURI());
        parts.add(holder(record));
        List<PreviewJson.Triple> triples = new ArrayList<>();
        for (CellMappings.Target target : targets) {
            for (Resource subject : subjects(target, row, cell.key())) {
                if (target.unit()) unit(subject, target, cell, checked.value(), triples);
                else literal(subject, target, row, cell, checked.value(), triples);
            }
        }
        return new PreviewJson.Rewrite(cell.plm(), cell.table(), cell.key(), cell.column(), cell.value(), triples);
    }

    /** The part a record belongs to: itself for a part, else the part its feature, reference or offer names. */
    private String holder(Resource record) {
        if (record.hasProperty(RDF.type, model.createResource(Atelier.ONT + "Part"))) return record.getURI();
        for (String name : HOLDERS) {
            Statement s = record.getProperty(model.createProperty(Atelier.ONT + name));
            if (s != null && s.getObject().isURIResource()) return s.getResource().getURI();
        }
        return record.getURI();
    }

    /** The subjects of the row a target mints: one IRI over the key, or the matching subjects of a template over more columns. */
    private List<Resource> subjects(CellMappings.Target target, CellMappings.Row row, String key) {
        if (List.of(row.keyColumn()).equals(CellMappings.placeholders(target.subject()))) {
            return List.of(model.createResource(CellMappings.fill(target.subject(), row.keyColumn(), key)));
        }
        Pattern pattern = CellMappings.pattern(target.subject(), row.keyColumn(), key);
        return model.listSubjects().toList().stream().filter(s -> s.isURIResource() && pattern.matcher(s.getURI()).matches()).toList();
    }

    private void unit(Resource node, CellMappings.Target target, PreviewJson.Cell cell, Object value, List<PreviewJson.Triple> triples) {
        Property numeric = model.createProperty(Atelier.QUDT + "numericValue");
        if (!node.hasProperty(numeric)) return;
        Property unit = model.createProperty(target.predicate());
        String before = shown(node, unit);
        node.removeAll(unit);
        String after = value == null ? null : CellMappings.fill(target.iriTemplate(), cell.column(), value.toString());
        if (after != null) node.addProperty(unit, model.createResource(after));
        triples.add(new PreviewJson.Triple(node.getURI(), target.predicate(), before, after));
    }

    private void literal(Resource subject, CellMappings.Target target, CellMappings.Row row, PreviewJson.Cell cell, Object value,
                         List<PreviewJson.Triple> triples) {
        Property predicate = model.createProperty(target.predicate());
        String before = shown(subject, predicate);
        CellMappings.Quantity quantity = mappings.quantity(cell.plm(), target.subject()).orElse(null);
        if (quantity != null && model.contains(null, model.createProperty(Atelier.MASS), subject)) masses = true;
        if (quantity != null && value == null) {
            model.removeAll(subject, null, null);
            model.removeAll(null, null, subject);
        } else {
            if (quantity != null && !subject.hasProperty(RDF.type)) {
                Resource parent = model.createResource(CellMappings.fill(quantity.parent(), row.keyColumn(), cell.key()));
                parent.addProperty(model.createProperty(quantity.predicate()), subject);
                subject.addProperty(RDF.type, model.createResource(Atelier.QUDT + "QuantityValue"));
                if (quantity.unit() != null) subject.addProperty(model.createProperty(Atelier.QUDT + "unit"), model.createResource(quantity.unit()));
                if (model.contains(null, model.createProperty(Atelier.MASS), subject)) masses = true;
            }
            subject.removeAll(predicate);
            if (value != null) subject.addLiteral(predicate, literal(value, target.datatype()));
        }
        triples.add(new PreviewJson.Triple(subject.getURI(), target.predicate(), before, value == null ? null : lexical(value, target.datatype())));
    }

    private Literal literal(Object value, String datatype) {
        String lexical = lexical(value, datatype);
        return datatype == null ? model.createLiteral(lexical) : model.createTypedLiteral(lexical, TypeMapper.getInstance().getSafeTypeByName(datatype));
    }

    private static String lexical(Object value, String datatype) {
        if (value instanceof BigDecimal number) {
            return datatype != null && datatype.endsWith("#integer") ? number.toBigIntegerExact().toString() : number.toPlainString();
        }
        return value.toString();
    }

    private static String shown(Resource subject, Property predicate) {
        Statement s = subject.getProperty(predicate);
        if (s == null) return null;
        RDFNode o = s.getObject();
        return o.isLiteral() ? o.asLiteral().getLexicalForm() : o.isURIResource() ? o.asResource().getURI() : null;
    }
}
