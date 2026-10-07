// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.common.r2rml;

import atelier.plm.common.catalogue.AnnotationCatalogue;
import atelier.plm.common.catalogue.ColumnEntry;
import atelier.plm.common.catalogue.EntityEntry;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Generates a W3C R2RML mapping (Turtle) for one service from its annotated JPA entities, following
 * the IRI templates and ontology terms of docs/contract.md:
 * <ul>
 *   <li>an entity with {@code @OntologyClass} {@code atelier:Part}, {@code atelier:Plug}, {@code atelier:Fastener}
 *       or {@code atelier:HydraulicCoupling} becomes a TriplesMap whose subject is
 *       {@code https://example.com/atelier/{plm}/part|plug|fastener|coupling/{id column}} and carries the
 *       constant {@code atelier:ownedBy "<PLM>"}; an entity of class {@code atelier:BomLine} (a link table, a view
 *       or indented rows) has the subject {@code https://example.com/atelier/{plm}/bomline/{parent column}/{child column}}
 *       over its columns mapped to {@code atelier:parent} and {@code atelier:child}; an entity of class
 *       {@code atelier:ExternalReference} has the subject {@code https://example.com/atelier/{plm}/ref/{id column}};
 *       an entity of class {@code atelier:Occurrence} (one placement of a line's child) has the subject
 *       {@code https://example.com/atelier/{plm}/occurrence/{parent column}/{child column}/{index column}}: its columns mapped
 *       to {@code atelier:parent} and {@code atelier:child} name its line, {@code atelier:ofLine}
 *       {@code .../bomline/{parent column}/{child column}}, and are not stated on the occurrence itself;
 *       any other class is rejected;</li>
 *   <li>an entity of class {@code atelier:Supplier} or {@code atelier:SupplierOffer} has the subject
 *       {@code https://example.com/atelier/{plm}/supplier|offer/{id column}} and carries {@code atelier:ownedBy};
 *       an offer's column mapped to {@code atelier:offersPart} becomes that property with the part IRI and its column
 *       mapped to {@code atelier:fromSupplier} that property with the supplier IRI {@code .../{plm}/supplier/{column}};</li>
 *   <li>a Part entity with a column mapped to {@code atelier:parent} holds its bill of materials on the part row (a
 *       self-referencing tree): that column and the one mapped to {@code atelier:quantity} become a second TriplesMap
 *       over the same table, the line {@code .../bomline/{parent column}/{id column}} from the parent to the row's part;
 *       a row without a parent (a site kit) yields no line;</li>
 *   <li>a column mapped to {@code atelier:parent}, {@code atelier:child}, {@code atelier:fromPart} or {@code atelier:contains}
 *       becomes that property with the part IRI {@code https://example.com/atelier/{plm}/part/{column}};</li>
 *   <li>an entity with a column mapped to {@code atelier:contains} (a bill-of-materials closure table, with a declared
 *       subject template over its ancestor column) states no class: its subjects are parts the part table already
 *       types, and a class there would make every request for {@code atelier:Part} read the closure as well;</li>
 *   <li>an entity with {@code @OntologyClass(subject = "...")} uses that IRI template verbatim as its
 *       subject (R2RML percent-encodes the column values), whatever its class, and carries no
 *       {@code atelier:ownedBy}: its rows describe subjects minted by another service, e.g. the part tags
 *       of {@code atelier-core} on the PLMs' part IRIs;</li>
 *   <li>each {@code @Maps} column becomes a literal of that property, typed by its Java type
 *       (numbers and booleans; timestamps carry no declared datatype so the virtual graph uses the
 *       column's natural type, which Ontop needs to type UNION branches consistently);</li>
 *   <li>a feature's reference to a Part entity (a {@code @ManyToOne} join column) or a column mapped
 *       to {@code atelier:onPart} becomes {@code atelier:onPart} with the part IRI;</li>
 *   <li>a column mapped to {@code atelier:partOf} becomes {@code atelier:partOf} with the product IRI the Atelier
 *       core mints, {@code https://example.com/atelier/product/{column}};</li>
 *   <li>a column mapped to {@code atelier:appliesUnderOption} (a row that exists only under one option of a variant)
 *       becomes {@code atelier:appliesUnderOption} with the option IRI {@code https://example.com/atelier/option/{column}};
 *       a NULL code, a row of the base configuration, yields no triple;</li>
 *   <li>each quantity column ({@code atelier:positionX|Y|Z}, {@code atelier:diameter}, {@code atelier:gripLength},
 *       {@code atelier:pressureRating}, {@code atelier:mass}, {@code atelier:massLimit}, {@code atelier:nominalDiameter},
 *       {@code atelier:nominalLength}, {@code atelier:gearModule}, {@code atelier:translationX|Y|Z}, {@code atelier:spanFrom|To}) becomes its own
 *       TriplesMap minting the {@code qudt:QuantityValue}
 *       {@code <subject>/position/{x|y|z}}, {@code <subject>/diameter}, {@code <subject>/grip},
 *       {@code <subject>/rating}, {@code <subject>/mass}, {@code <subject>/mass-limit}, {@code <subject>/nominal-diameter},
 *       {@code <subject>/nominal-length}, {@code <subject>/module}, {@code <subject>/translation/{x|y|z}} or {@code <subject>/span-from|to} with
 *       {@code qudt:numericValue} (the stored value, untransformed,
 *       xsd:decimal) and {@code qudt:unit}: a constant for {@code @Unit("X")}, an IRI template over
 *       the unit column for {@code @Unit(column = "c")} (a NULL unit yields no unit triple), none
 *       without {@code @Unit}.</li>
 * </ul>
 */
public final class R2rmlGenerator {

    static final String BASE = "https://example.com/atelier/";
    static final String UNIT_NAMESPACE = "http://qudt.org/vocab/unit/";
    private static final String ON_PART = "atelier:onPart";
    private static final String PART_OF = "atelier:partOf";
    private static final String APPLIES_UNDER_OPTION = "atelier:appliesUnderOption";
    private static final String BOM_LINE = "atelier:BomLine";
    private static final String PARENT = "atelier:parent";
    private static final String CHILD = "atelier:child";
    private static final String QUANTITY = "atelier:quantity";
    private static final String EXTERNAL_REFERENCE = "atelier:ExternalReference";
    private static final String FROM_PART = "atelier:fromPart";
    private static final String CONTAINS = "atelier:contains";
    private static final String OCCURRENCE = "atelier:Occurrence";
    private static final String OF_LINE = "atelier:ofLine";
    private static final String INDEX = "atelier:index";

    private static final Map<String, String> PREFIXES = Map.of(
            "rr", "http://www.w3.org/ns/r2rml#",
            "atelier", "https://example.com/atelier/ontology#",
            "rdfs", "http://www.w3.org/2000/01/rdf-schema#",
            "xsd", "http://www.w3.org/2001/XMLSchema#",
            "qudt", "http://qudt.org/schema/qudt/",
            "unit", UNIT_NAMESPACE);
    private static final List<String> PREFIX_ORDER = List.of("rr", "atelier", "rdfs", "xsd", "qudt", "unit");

    /** Ontology class to the IRI path segment of its subjects; sorted so error messages are stable. */
    private static final Map<String, String> SUBJECT_SEGMENT = new TreeMap<>(Map.of(
            "atelier:Part", "part",
            "atelier:Plug", "plug",
            "atelier:Fastener", "fastener",
            "atelier:HydraulicCoupling", "coupling",
            BOM_LINE, "bomline",
            OCCURRENCE, "occurrence",
            EXTERNAL_REFERENCE, "ref",
            "atelier:Supplier", "supplier",
            "atelier:SupplierOffer", "offer"));

    /** A column naming another subject of the same PLM: its property to the class of that subject. */
    private static final Map<String, String> REFERENCES = Map.of(
            "atelier:offersPart", "atelier:Part",
            "atelier:fromSupplier", "atelier:Supplier");

    /** Quantity property to the path of its qudt:QuantityValue node under the subject IRI. */
    private static final Map<String, String> QUANTITY_PATHS = Map.ofEntries(
            Map.entry("atelier:positionX", "position/x"),
            Map.entry("atelier:positionY", "position/y"),
            Map.entry("atelier:positionZ", "position/z"),
            Map.entry("atelier:diameter", "diameter"),
            Map.entry("atelier:gripLength", "grip"),
            Map.entry("atelier:pressureRating", "rating"),
            Map.entry("atelier:mass", "mass"),
            Map.entry("atelier:nominalDiameter", "nominal-diameter"),
            Map.entry("atelier:nominalLength", "nominal-length"),
            Map.entry("atelier:massLimit", "mass-limit"),
            Map.entry("atelier:gearModule", "module"),
            Map.entry("atelier:translationX", "translation/x"),
            Map.entry("atelier:translationY", "translation/y"),
            Map.entry("atelier:translationZ", "translation/z"),
            Map.entry("atelier:innerDiameter", "inner-diameter"),
            Map.entry("atelier:crossSection", "cross-section"),
            Map.entry("atelier:itemWidth", "width"),
            Map.entry("atelier:itemHeight", "height"),
            Map.entry("atelier:baseWidth", "base-width"),
            Map.entry("atelier:itemDepth", "depth"),
            Map.entry("atelier:contourWidth", "contour-width"),
            Map.entry("atelier:outerDiameter", "outer-diameter"),
            Map.entry("atelier:sectionWidth", "section-width"),
            Map.entry("atelier:rimDiameter", "rim-diameter"),
            Map.entry("atelier:heatStackDiameter", "heat-stack-diameter"),
            Map.entry("atelier:spanFrom", "span-from"),
            Map.entry("atelier:spanTo", "span-to"));

    private static final Map<String, String> DATATYPES = Map.ofEntries(
            Map.entry("BigDecimal", "xsd:decimal"),
            Map.entry("Integer", "xsd:integer"), Map.entry("int", "xsd:integer"),
            Map.entry("Long", "xsd:integer"), Map.entry("long", "xsd:integer"),
            Map.entry("Short", "xsd:integer"), Map.entry("short", "xsd:integer"),
            Map.entry("BigInteger", "xsd:integer"),
            Map.entry("Double", "xsd:double"), Map.entry("double", "xsd:double"),
            Map.entry("Float", "xsd:double"), Map.entry("float", "xsd:double"),
            Map.entry("Boolean", "xsd:boolean"), Map.entry("boolean", "xsd:boolean"));

    private R2rmlGenerator() {
    }

    /** Usage: {@code R2rmlGenerator <plm> <output.ttl>}; scans {@code atelier.plm.<plm>.domain}. */
    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("usage: R2rmlGenerator <plm> <output.r2rml.ttl>");
            System.exit(2);
        }
        String plm = args[0].toLowerCase(Locale.ROOT);
        String domainPackage = domainPackage(plm);
        List<Class<?>> entities = scanEntities(domainPackage);
        if (entities.isEmpty()) {
            throw new IllegalStateException("no @Entity classes found in package " + domainPackage);
        }
        Path output = Path.of(args[1]);
        Files.writeString(output, generate(plm, entities), StandardCharsets.UTF_8);
        System.out.println("wrote " + output + " from " + entities.size() + " entities of " + domainPackage);
    }

    /** Maven module holding the entities of a service code: {@code plm-<code>} for a PLM, {@code atelier-core} for {@code core}. */
    static String moduleName(String plm) {
        return "core".equals(plm) ? "atelier-core" : "plm-" + plm;
    }

    static String domainPackage(String plm) {
        return "atelier.plm." + plm + ".domain";
    }

    static List<Class<?>> scanEntities(String basePackage) {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        List<Class<?>> classes = new ArrayList<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents(basePackage)) {
            try {
                // nosemgrep: unsafe-reflection -- class names come from Spring's classpath scan of this module's domain package, not from input
                classes.add(Class.forName(candidate.getBeanClassName(), false, R2rmlGenerator.class.getClassLoader()));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("cannot load " + candidate.getBeanClassName(), e);
            }
        }
        return classes;
    }

    /** R2RML Turtle for the given entity classes of one service, entities in name order. */
    public static String generate(String plmCode, Collection<Class<?>> entityClasses) {
        String plm = plmCode.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();
        // The tree-wide licence header, so a regenerated mapping file keeps it.
        out.append("# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.\n")
                .append("# SPDX-License-Identifier: MIT-0\n\n");
        out.append("# Generated from the JPA entity annotations of ").append(moduleName(plm))
                .append(" (package ").append(domainPackage(plm)).append(") by ")
                .append(R2rmlGenerator.class.getName()).append(".\n")
                .append("# Do not edit: change the annotations and regenerate with modules/ontop/scripts/generate-mappings.sh.\n\n");
        for (String prefix : PREFIX_ORDER) {
            out.append("@prefix ").append(prefix).append(": <").append(PREFIXES.get(prefix)).append("> .\n");
        }
        out.append("@prefix map: <").append(BASE).append(plm).append("/mapping#> .\n");

        entityClasses.stream()
                .filter(type -> AnnotationCatalogue.describeEntity(type).ontologyClass() != null)
                .sorted(Comparator.comparing(Class::getSimpleName))
                .forEach(type -> new EntityMapping(plm, type).appendTo(out));
        return out.toString();
    }

    /** The TriplesMaps of one entity: its subject map plus one map per quantity column. */
    private static final class EntityMapping {

        private final String plm;
        private final Class<?> type;
        private final EntityEntry entry;
        private final String subjectTemplate;
        /** True when the entity declares its subject template: its rows describe another service's subjects. */
        private final boolean foreignSubject;

        EntityMapping(String plm, Class<?> type) {
            this.plm = plm;
            this.type = type;
            this.entry = AnnotationCatalogue.describeEntity(type);
            Optional<String> declared = AnnotationCatalogue.subjectTemplate(type);
            this.foreignSubject = declared.isPresent();
            this.subjectTemplate = declared.map(this::checkedTemplate).orElseGet(() -> subjectTemplate(plm, type, entry));
        }

        void appendTo(StringBuilder out) {
            String name = "map:" + type.getSimpleName();
            List<String> predicates = new ArrayList<>();
            if (!foreignSubject) {
                predicates.add("rr:predicateObjectMap [ rr:predicate atelier:ownedBy ; rr:object "
                        + literal(plm.toUpperCase(Locale.ROOT)) + " ]");
            }

            List<ColumnEntry> quantities = new ArrayList<>();
            boolean treeOnPartRow = treeOnPartRow();
            boolean occurrence = OCCURRENCE.equals(entry.ontologyClass());
            if (occurrence) {
                predicates.add(predicate(OF_LINE, "rr:template " + literal(BASE + plm + "/bomline/{"
                        + mapped(type, entry, PARENT) + "}/{" + mapped(type, entry, CHILD) + "}")));
            }
            for (ColumnEntry column : entry.columns()) {
                Field field = field(type, column.field());
                if (treeOnPartRow && (PARENT.equals(column.ontologyTerm()) || QUANTITY.equals(column.ontologyTerm()))) {
                    continue;
                } else if (occurrence && (PARENT.equals(column.ontologyTerm()) || CHILD.equals(column.ontologyTerm()))) {
                    continue;
                } else if (PARENT.equals(column.ontologyTerm()) || CHILD.equals(column.ontologyTerm())
                        || FROM_PART.equals(column.ontologyTerm()) || CONTAINS.equals(column.ontologyTerm())) {
                    predicates.add(predicate(column.ontologyTerm(), "rr:template " + literal(partTemplate(plm, column.column()))));
                } else if (column.ontologyTerm() != null && REFERENCES.containsKey(column.ontologyTerm())) {
                    predicates.add(predicate(column.ontologyTerm(), "rr:template "
                            + literal(BASE + plm + "/" + SUBJECT_SEGMENT.get(REFERENCES.get(column.ontologyTerm())) + "/{" + column.column() + "}")));
                } else if (isPartReference(field, column)) {
                    predicates.add(predicate(ON_PART, "rr:template " + literal(partTemplate(plm, column.column()))));
                } else if (PART_OF.equals(column.ontologyTerm())) {
                    predicates.add(predicate(PART_OF, "rr:template " + literal(productTemplate(column.column()))));
                } else if (APPLIES_UNDER_OPTION.equals(column.ontologyTerm())) {
                    predicates.add(predicate(APPLIES_UNDER_OPTION, "rr:template " + literal(BASE + "option/{" + column.column() + "}")));
                } else if (column.ontologyTerm() == null) {
                    continue;
                } else if (QUANTITY_PATHS.containsKey(column.ontologyTerm())) {
                    quantities.add(column);
                    predicates.add(predicate(column.ontologyTerm(), "rr:template " + literal(quantityTemplate(column))));
                } else {
                    predicates.add(predicate(column.ontologyTerm(), "rr:column " + literal(column.column())
                            + datatype(entry, column.column())));
                }
            }

            out.append('\n').append(name).append(" a rr:TriplesMap ;\n")
                    .append("  rr:logicalTable [ rr:tableName ").append(literal(entry.table())).append(" ] ;\n")
                    .append("  rr:subjectMap [ rr:template ").append(literal(subjectTemplate));
            if (column(CONTAINS).isEmpty()) {
                out.append(" ; rr:class ").append(curie(entry.ontologyClass()));
            }
            out.append(" ]");
            for (String predicate : predicates) {
                out.append(" ;\n  ").append(predicate);
            }
            out.append(" .\n");

            for (ColumnEntry quantity : quantities) {
                appendQuantity(out, name, quantity);
            }
            if (treeOnPartRow) {
                appendTreeLines(out, name);
            }
        }

        /** True for a Part entity whose rows name their parent: its bill of materials is a tree on the part table. */
        private boolean treeOnPartRow() {
            return "atelier:Part".equals(entry.ontologyClass()) && !foreignSubject && column(PARENT).isPresent();
        }

        /** The lines of a tree on the part row: one per row with a parent, from the parent to the row's part. */
        private void appendTreeLines(StringBuilder out, String entityMap) {
            String id = idColumn(type, entry);
            String parent = column(PARENT).orElseThrow();
            String quantity = column(QUANTITY).orElseThrow(() -> new IllegalStateException(type.getSimpleName()
                    + ": a column mapped to " + PARENT + " needs one mapped to " + QUANTITY));
            out.append('\n').append(entityMap).append("_BomLine a rr:TriplesMap ;\n")
                    .append("  rr:logicalTable [ rr:tableName ").append(literal(entry.table())).append(" ] ;\n")
                    .append("  rr:subjectMap [ rr:template ").append(literal(BASE + plm + "/bomline/{" + parent + "}/{" + id + "}"))
                    .append(" ; rr:class ").append(BOM_LINE).append(" ] ;\n  ")
                    .append("rr:predicateObjectMap [ rr:predicate atelier:ownedBy ; rr:object ")
                    .append(literal(plm.toUpperCase(Locale.ROOT))).append(" ] ;\n  ")
                    .append(predicate(PARENT, "rr:template " + literal(partTemplate(plm, parent)))).append(" ;\n  ")
                    .append(predicate(CHILD, "rr:template " + literal(partTemplate(plm, id)))).append(" ;\n  ")
                    .append(predicate(QUANTITY, "rr:column " + literal(quantity) + datatype(entry, quantity))).append(" .\n");
        }

        private Optional<String> column(String term) {
            return entry.columns().stream().filter(c -> term.equals(c.ontologyTerm())).map(ColumnEntry::column).findFirst();
        }

        private void appendQuantity(StringBuilder out, String entityMap, ColumnEntry column) {
            out.append('\n').append(entityMap).append('_').append(localName(column.ontologyTerm()))
                    .append(" a rr:TriplesMap ;\n")
                    .append("  rr:logicalTable [ rr:tableName ").append(literal(entry.table())).append(" ] ;\n")
                    .append("  rr:subjectMap [ rr:template ").append(literal(quantityTemplate(column)))
                    .append(" ; rr:class qudt:QuantityValue ] ;\n")
                    .append("  rr:predicateObjectMap [ rr:predicate qudt:numericValue ; rr:objectMap [ rr:column ")
                    .append(literal(column.column())).append(" ; rr:datatype xsd:decimal ] ]");
            if (column.unit() != null && column.unitColumn() != null) {
                throw new IllegalStateException(type.getSimpleName() + "." + column.field()
                        + ": @Unit declares both a fixed unit and a unit column");
            }
            if (column.unit() != null) {
                out.append(" ;\n  rr:predicateObjectMap [ rr:predicate qudt:unit ; rr:object unit:")
                        .append(column.unit()).append(" ]");
            } else if (column.unitColumn() != null) {
                out.append(" ;\n  rr:predicateObjectMap [ rr:predicate qudt:unit ; rr:objectMap [ rr:template ")
                        .append(literal(UNIT_NAMESPACE + "{" + column.unitColumn() + "}")).append(" ] ]");
            }
            out.append(" .\n");
        }

        private String quantityTemplate(ColumnEntry column) {
            return subjectTemplate + "/" + QUANTITY_PATHS.get(column.ontologyTerm());
        }

        /** The declared template, after checking every {column} it names is a column of the entity. */
        private String checkedTemplate(String template) {
            List<String> known = entry.columns().stream().map(ColumnEntry::column).toList();
            List<String> named = AnnotationCatalogue.templateColumns(template);
            if (named.isEmpty()) {
                throw new IllegalStateException(type.getSimpleName() + ": subject template names no column: " + template);
            }
            for (String column : named) {
                if (!known.contains(column)) {
                    throw new IllegalStateException(type.getSimpleName() + ": subject template names column "
                            + column + ", which the entity does not map (columns: " + known + ")");
                }
            }
            return template;
        }

        private static String predicate(String predicate, String objectMap) {
            return "rr:predicateObjectMap [ rr:predicate " + curie(predicate) + " ; rr:objectMap [ " + objectMap + " ] ]";
        }
    }

    private static String subjectTemplate(String plm, Class<?> type, EntityEntry entry) {
        String segment = SUBJECT_SEGMENT.get(entry.ontologyClass());
        if (segment == null) {
            throw new IllegalStateException(type.getSimpleName() + ": unsupported @OntologyClass "
                    + entry.ontologyClass() + " (expected one of " + SUBJECT_SEGMENT.keySet() + ")");
        }
        if (BOM_LINE.equals(entry.ontologyClass())) {
            return BASE + plm + "/" + segment + "/{" + mapped(type, entry, PARENT) + "}/{" + mapped(type, entry, CHILD) + "}";
        }
        if (OCCURRENCE.equals(entry.ontologyClass())) {
            return BASE + plm + "/" + segment + "/{" + mapped(type, entry, PARENT) + "}/{" + mapped(type, entry, CHILD) + "}/{"
                    + mapped(type, entry, INDEX) + "}";
        }
        return BASE + plm + "/" + segment + "/{" + idColumn(type, entry) + "}";
    }

    private static String idColumn(Class<?> type, EntityEntry entry) {
        return entry.columns().stream()
                .filter(c -> field(type, c.field()).isAnnotationPresent(Id.class))
                .map(ColumnEntry::column)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(type.getSimpleName() + " has no @Id column"));
    }

    /** The column mapped to {@code term}; a line entity names its parent and its child. */
    private static String mapped(Class<?> type, EntityEntry entry, String term) {
        return entry.columns().stream().filter(c -> term.equals(c.ontologyTerm())).map(ColumnEntry::column).findFirst()
                .orElseThrow(() -> new IllegalStateException(type.getSimpleName() + ": an " + entry.ontologyClass()
                        + " entity needs a column mapped to " + term));
    }

    /** {@code " ; rr:datatype xsd:..."} for the Java type of the column, empty when the type carries none. */
    private static String datatype(EntityEntry entry, String column) {
        String javaType = entry.columns().stream().filter(c -> c.column().equals(column)).map(ColumnEntry::javaType)
                .findFirst().orElseThrow();
        String datatype = DATATYPES.get(javaType);
        return datatype == null ? "" : " ; rr:datatype " + datatype;
    }

    private static String partTemplate(String plm, String column) {
        return BASE + plm + "/part/{" + column + "}";
    }

    /** The IRI of the product a row places its subject in; products are Atelier's, so no PLM segment. */
    private static String productTemplate(String column) {
        return BASE + "product/{" + column + "}";
    }

    private static boolean isPartReference(Field field, ColumnEntry column) {
        if (ON_PART.equals(column.ontologyTerm())) {
            return true;
        }
        boolean joined = field.isAnnotationPresent(ManyToOne.class) || field.isAnnotationPresent(JoinColumn.class);
        return joined && "atelier:Part".equals(AnnotationCatalogue.describeEntity(field.getType()).ontologyClass());
    }

    private static Field field(Class<?> type, String name) {
        try {
            return type.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(type.getSimpleName() + " has no field " + name, e);
        }
    }

    /** The CURIE unchanged, after checking its prefix is declared in the generated document. */
    static String curie(String curie) {
        int colon = curie.indexOf(':');
        if (colon < 0 || !PREFIXES.containsKey(curie.substring(0, colon))) {
            throw new IllegalStateException("unknown prefix in ontology term " + curie);
        }
        return curie;
    }

    /** The part of a CURIE after its prefix, e.g. {@code positionX} for {@code atelier:positionX}. */
    private static String localName(String curie) {
        return curie.substring(curie.indexOf(':') + 1);
    }

    private static String literal(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
