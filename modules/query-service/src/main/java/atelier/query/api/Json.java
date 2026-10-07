// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.api;

import atelier.query.Atelier;
import atelier.query.federation.Federator;
import atelier.query.validation.RuleViolation;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import org.apache.jena.graph.Node;

/** Response shapes of the query service API, as defined in docs/contract.md. */
public final class Json {
    private Json() {}

    /** A part the viewer may see ({@link Part}, or {@link PartRef} where only its PLM and id are reported), or a {@link Redacted} marker for one they may not. */
    public sealed interface PartView permits Part, PartRef, Redacted {}

    /** A visible part named by its PLM and native id, with its supplier when it is supplier-built. */
    public record PartRef(String id, String plm, @JsonInclude(JsonInclude.Include.NON_NULL) String supplier,
                          @JsonInclude(JsonInclude.Include.NON_NULL) Boolean context) implements PartView {
        public PartRef(String id, String plm, String supplier) {
            this(id, plm, supplier, null);
        }
    }

    /** A feature the viewer may see, or a {@link Redacted} marker for one on a hidden part. */
    public sealed interface FeatureView permits Feature, Redacted {}

    /** A part or feature hidden by export control: only the marker and the owning PLM are shown. */
    public record Redacted(boolean redacted, String plm) implements PartView, FeatureView {}

    /**
     * A data-quality finding on a part: the {@code ateliersh:rule} of the sh:Warning shape that
     * reported it, the shape's message and the PLM record the result's sh:value names ({@code value}), absent when
     * sh:value is not a PLM resource. The record lets a client correct the row the finding is about: the dependency
     * part of a lifecycleConflict, the longer offer of a conflictingLeadTime, the external reference of a
     * danglingReference or staleRevision. A finding never changes an interface's status.
     */
    public record Finding(String rule, String message, @JsonInclude(JsonInclude.Include.NON_NULL) RowRef value) {
        /** The finding a SHACL result reports, naming the PLM record of its sh:value when there is one. */
        public static Finding of(RuleViolation result) {
            return new Finding(result.rule(), result.message(), RowRef.of(result.value()));
        }
    }

    /**
     * A row of a PLM's own tables, named by its IRI {@code {plm}/{kind}/{id}}: the lower-case PLM code, the kind
     * segment ({@code part}, {@code offer}, {@code ref}...) and the native key, percent-decoded.
     */
    public record RowRef(String plm, String kind, String id) {
        /** The record a node names; null for a literal, a blank node or an IRI outside the PLMs' resources. */
        public static RowRef of(Node node) {
            if (node == null || !node.isURI()) return null;
            String iri = node.getURI();
            String plm = Atelier.plmOf(iri);
            if (plm == null) return null;
            String rest = iri.substring(Atelier.DATA.length() + plm.length() + 1);
            return new RowRef(plm, rest.substring(0, rest.indexOf('/')), Atelier.nativeId(iri));
        }
    }

    /**
     * A visible part: its PLM description ({@code name}, {@code sourceFileRef}), its CAD file from the
     * file index with a presigned URL, its export-control tag from the Atelier core database
     * ({@code jurisdiction}, {@code releasableTo}, {@code taggedBy}, {@code taggedAt}; all null for an
     * untagged part, which only the export-control officer sees), its {@code supplier} from the
     * file index ({@code atelier:builtBy}), absent unless the part is supplier-built (the first by name when several built it;
     * the supplier-built mark the screens paint and the {@code list_interfaces} tool's part references read), its
     * {@code suppliers}: every supplier that built the part, then every supplier with an offer for it, each marked
     * {@code built} or {@code offered} and named as the file index names a builder ("name, town"), absent when there are none, its
     * data-quality {@code findings} from the shapes, absent when there are none, and the attributes
     * its PLM states, each absent when the PLM states none: {@code revision} in the PLM's own form,
     * {@code lifecycle} in the PLM's own word with {@code lifecycleState} the canonical state the
     * ontology's lifecycle scheme gives that word, {@code mass} as stored with its unit and in kg,
     * {@code material} and {@code partType}, and for a gear {@code toothCount} and {@code gearModule} (as stored, with its
     * unit and in mm). In a subtree's interfaces answer, {@code context} is true for a part on the
     * far side of an interface, outside the subtree: shown with its attributes and features only, absent otherwise.
     * {@code nameEn} is the English name the labels graph gives the part in the parts answer (for a UK part, its native
     * name), absent in the interface answers.
     */
    public record Part(String id, String plm, String name, @JsonInclude(JsonInclude.Include.NON_NULL) String nameEn, String cadFile, String cadUrl, String sourceFileRef,
                       String jurisdiction, String releasableTo, String taggedBy, String taggedAt,
                       @JsonInclude(JsonInclude.Include.NON_NULL) String supplier,
                       @JsonInclude(JsonInclude.Include.NON_EMPTY) List<PartSupplier> suppliers,
                       @JsonInclude(JsonInclude.Include.NON_EMPTY) List<Finding> findings,
                       @JsonInclude(JsonInclude.Include.NON_NULL) String revision,
                       @JsonInclude(JsonInclude.Include.NON_NULL) String lifecycle,
                       @JsonInclude(JsonInclude.Include.NON_NULL) String lifecycleState,
                       @JsonInclude(JsonInclude.Include.NON_NULL) Quantity mass,
                       @JsonInclude(JsonInclude.Include.NON_NULL) String material,
                       @JsonInclude(JsonInclude.Include.NON_NULL) String partType,
                       @JsonInclude(JsonInclude.Include.NON_NULL) Integer toothCount,
                       @JsonInclude(JsonInclude.Include.NON_NULL) Quantity gearModule,
                       @JsonInclude(JsonInclude.Include.NON_NULL) Boolean context) implements PartView {
        public Part(String id, String plm, String name, String cadFile, String cadUrl, String sourceFileRef, String jurisdiction,
                    String releasableTo, String taggedBy, String taggedAt, String supplier, List<Finding> findings, String revision,
                    String lifecycle, String lifecycleState, Quantity mass, String material, String partType) {
            this(id, plm, name, null, cadFile, cadUrl, sourceFileRef, jurisdiction, releasableTo, taggedBy, taggedAt, supplier,
                    supplier == null ? List.of() : List.of(new PartSupplier(supplier, PartSupplier.BUILT)), findings,
                    revision, lifecycle, lifecycleState, mass, material, partType, null, null, null);
        }

        /** This part marked as context of a subtree answer. */
        public Part asContext() {
            return new Part(id, plm, name, nameEn, cadFile, cadUrl, sourceFileRef, jurisdiction, releasableTo, taggedBy, taggedAt, supplier,
                    suppliers, findings, revision, lifecycle, lifecycleState, mass, material, partType, toothCount, gearModule, true);
        }
    }

    /** A supplier of a part: {@code role} is {@code built} for an {@code atelier:builtBy} of the part, {@code offered} for a supplier offer of it. */
    public record PartSupplier(String name, String role) {
        public static final String BUILT = "built";
        public static final String OFFERED = "offered";
    }

    public record Source(Double x, Double y, Double z, String unit) {}

    public record Mm(double x, double y, double z) {}

    /** A measured value as stored, with its unit and its normalised form: mm for lengths, bar for pressures, kg for masses. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Quantity(Double value, String unit, Double mm, Double bar, Double kg) {}

    /** {@code matesWith} holds mate ids, or a {@link Redacted} marker where the mate is on a hidden part. */
    public record Feature(String id, String plm, String partId, String kind, Map<String, Object> properties,
                          Source source, Mm positionMm, List<Object> matesWith) implements FeatureView {}

    /**
     * An unmated feature on the part facing an orphan, of the orphan's kind and within the interface
     * tolerance of its position on every axis after unit conversion: what "Publish link" pre-fills.
     * {@code position} is in mm; {@code connectorType} and {@code pinCount} are those of a plug.
     */
    public record CandidateMate(String id, String part, String plm, String iri, Mm position, String connectorType,
                                Integer pinCount) {}

    /**
     * One rule result on the interface: {@code features} lists the focus feature first, then the mate
     * for a pair rule or every visible mate for {@code doubleMate}; an {@code orphan} result's detail
     * carries {@code interface} and {@code candidateMates} (empty when none).
     */
    public record Violation(String rule, String shape, String message, String focusNode,
                            List<String> features, Map<String, Object> detail) {}

    /**
     * An interface: its id (unique within its product) and the key of its {@code product}, the label the
     * viewer may see, its status, tolerance, parts, features and violations.
     */
    public record Interface(String id, String product, String label, String status, Double toleranceMm, List<PartView> parts,
                            List<FeatureView> features, List<Violation> violations) {}

    public record Provenance(List<Federator.Call> calls) {}

    public record Timings(long totalMs, long federationMs, long validationMs) {}

    /**
     * How an answer scoped to one item's subtree was resolved: the root (its id, site and, when the viewer may see it,
     * name; {@code redacted} when they may not, and nothing under it is expanded), the rounds that added items (round 1
     * the owning site; each later one the sites the references of the round before name) with the roots each asked
     * about, the items the viewer may see, the context parts (the far side of an interface with a side in the subtree),
     * the items referenced beyond the last round ({@code unresolved}, absent when the subtree is complete) and
     * {@code productRules}: the product rules (massLimit, massScale) are not evaluated on a subtree.
     */
    public record Subtree(String root, String plm, String product, String name, boolean redacted, int depth, List<SubtreeRound> rounds,
                          int items, int context, String productRules,
                          @JsonInclude(JsonInclude.Include.NON_EMPTY) List<SubtreeRoot> unresolved) {}

    /** One round of a subtree's resolution: the roots it asked their sites about and the items it added. */
    public record SubtreeRound(int round, List<SubtreeRoot> roots, int items) {}

    /** An item a subtree round starts from, by native id and site. */
    public record SubtreeRoot(String id, String plm) {}

    /**
     * The viewer profile an answer was computed for, the FILTER text pushed into the core arm, the
     * IRIs of the parts the answer named that carry no tag in the Atelier core database (a finding
     * against the owning PLM; such a part is hidden from every profile but the export-control
     * officer), and the actor who asked ({@code user}, or the name an agent gave in {@code x-atelier-actor}).
     */
    public record Policy(String profile, List<String> releasable, String filter, List<String> untagged, String actor) {}

    /** {@code subtree} is present when the answer is scoped to one item's subtree. */
    public record PartsResponse(List<PartView> parts, Provenance provenance, String sparql, Timings timings,
                                Policy policy, @JsonInclude(JsonInclude.Include.NON_NULL) Subtree subtree) {
        public PartsResponse(List<PartView> parts, Provenance provenance, String sparql, Timings timings, Policy policy) {
            this(parts, provenance, sparql, timings, policy, null);
        }
    }

    /** @param findings the data-quality findings of the parts the viewer may see, counted per rule (empty when none) */
    public record InterfacesResponse(List<Interface> interfaces, Provenance provenance, String sparql,
                                     Timings timings, Policy policy, Map<String, Integer> findings,
                                     @JsonInclude(JsonInclude.Include.NON_NULL) Subtree subtree) {
        public InterfacesResponse(List<Interface> interfaces, Provenance provenance, String sparql, Timings timings, Policy policy,
                                  Map<String, Integer> findings) {
            this(interfaces, provenance, sparql, timings, policy, findings, null);
        }
    }

    public record InterfaceResponse(@JsonProperty("interface") Interface _interface, Provenance provenance,
                                    String sparql, Timings timings, Policy policy) {}

    /**
     * A product: its key, name, frame, the number of its parts the viewer may see, the findings of the product rules,
     * and the number of lifecycleConflict findings on its visible items (a released item and a working or blocked item
     * it depends on, counted once per pair).
     */
    public record Product(String key, String name, String frame, int partCount,
                          @JsonInclude(JsonInclude.Include.NON_EMPTY) List<Finding> findings,
                          @JsonInclude(JsonInclude.Include.NON_NULL) MassLimit massLimit, int lifecycleConflicts) {}

    /**
     * A product's mass limit and its status for the viewer: {@code pass} or {@code fail} over every item, or
     * {@code not-evaluable} when {@code hiddenItems} of the product's items are hidden from the viewer, which raises no
     * violation since a hidden item could carry the mass that breaks the limit.
     */
    public record MassLimit(String status, double limitKg, @JsonInclude(JsonInclude.Include.NON_NULL) Integer hiddenItems) {}

    public record ProductsResponse(List<Product> products, Provenance provenance, String sparql, Timings timings,
                                   Policy policy) {}

    /**
     * A product as GET /query/products/list lists it: its key (the last segment of its IRI), name, the coordinate frame of
     * its CAD (null when the core states none) and the number of its parts the viewer may see, as {@link Product} has them.
     */
    public record ProductListing(String key, String name, String frame, int partCount) {}

    public record ProductListResponse(List<ProductListing> products, Provenance provenance, String sparql, Timings timings,
                                      Policy policy) {}

    /** Rows of one native table that an arm's triples came from; keys are percent-decoded. */
    public record Table(String plm, String table, List<String> keys) {}

    /**
     * One endpoint's contribution: the query texts the run sent to it (several requests joined by a
     * blank line), the triples of the validated graph it supplied as Turtle with the native rows
     * behind them, Ontop's SQL for the texts when obtainable, and its measured share of the run
     * ({@code tripleCount}, {@code requests}, {@code ms}: the figures of {@link Provenance}).
     */
    public record Arm(String endpoint, String kind, String sparql, String triples, int tripleCount, int requests,
                      long ms, String sql, List<Table> tables) {}

    public record Merged(int triples, String turtle) {}

    /** A shape that reported for the interface: its rule name, IRI and Turtle source. */
    public record Shape(String rule, String shape, String turtle) {}

    public record Shacl(List<Shape> shapes, String report) {}

    /** The material behind one interface's answer; {@code interfaceId} is the bare id, {@code product} the key of its product. */
    public record Evidence(String interfaceId, String product, String sparql, List<Arm> arms, Merged merged, Shacl shacl,
                           Timings timings, Policy policy) {}

    /** One arm of an interface's evidence, as the {@code evidence} tool returns it. */
    public record EvidenceArm(String interfaceId, String product, Arm arm, Timings timings, Policy policy) {}

    /**
     * An interface as the {@code list_interfaces} tool reports it: id and product key, parts as (id, plm)
     * or redaction markers, the rules failing and, for a failing interface only, up to three rule messages.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InterfaceSummary(String id, String product, String label, String status, List<PartView> parts,
                                   List<String> rulesFailing, List<String> messages) {}

    public record InterfaceSummaries(List<InterfaceSummary> interfaces, Provenance provenance, Timings timings, Policy policy,
                                     @JsonInclude(JsonInclude.Include.NON_NULL) Subtree subtree) {}

    /** One interface a part sits on: the interface's other parts and the part's own features there, counted per kind. */
    public record Use(String id, String label, String status, List<PartView> mates, Map<String, Integer> features) {}

    /**
     * Where a part is used: the interfaces naming it and its features on them per kind. A part the
     * viewer may not see is a {@link Redacted} marker with no interfaces.
     */
    public record WhereUsed(PartView part, List<Use> interfaces, Map<String, Integer> features, Provenance provenance,
                            String sparql, Timings timings, Policy policy) {}

    /** An interface a change touches: its current status and the features concerned (the subject's and their mates). */
    public record Impacted(String id, String label, String status, List<String> rulesFailing, List<FeatureView> features) {}

    /** The impact of a change to a part or to a feature (one of the two is set); a hidden subject is a {@link Redacted} marker with no interfaces. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Impact(PartView part, FeatureView feature, List<Impacted> interfaces, Provenance provenance,
                         String sparql, Timings timings, Policy policy) {}

    /**
     * Export-control status of a part for the viewer: the part with its tag ({@code jurisdiction},
     * {@code releasableTo}, {@code taggedBy}, {@code taggedAt}) and CAD URL when visible, a
     * {@link Redacted} marker otherwise; {@code cadAvailable} is whether a CAD URL was issued.
     */
    public record ExportStatus(PartView part, boolean visible, boolean cadAvailable, Provenance provenance, String sparql,
                               Timings timings, Policy policy) {}

    /** A node of a product's bill of materials: an item the viewer may see ({@link BomNode}) or a {@link BomHidden} marker. */
    public sealed interface BomItem permits BomNode, BomHidden {}

    /**
     * An item of the virtual bill of materials: the product root (partType {@code PRODUCT}, no PLM), a site kit, an
     * assembly or a part, with its native name, its English name ({@code nameEn}, from the labels graph) and attributes, the
     * {@code quantity} its parent uses, its total
     * {@code occurrences} in the product (the quantities multiplied down the tree), its {@code unitMassKg} (a part's own
     * mass plus that of the items it holds; an assembly's items' masses; absent when none is known) and
     * {@code extendedMassKg} (unit mass times occurrences), and its {@code children}, absent below the depth an MCP
     * caller asked for.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BomNode(String id, String plm, String name, String nameEn, String partType, String revision, String lifecycle,
                          String lifecycleState, double quantity, double occurrences, Double unitMassKg, Double extendedMassKg,
                          List<BomItem> children) implements BomItem {}

    /** An item the viewer may not see: its PLM and how many its visible parent uses; its own items are not expanded. */
    public record BomHidden(boolean redacted, String plm, double quantity, double occurrences) implements BomItem {}

    /**
     * Roll-up of one site ({@code plm}) or of the whole product ({@code plm} null): part occurrences, their mass in kg,
     * the part nodes without a mass, and the occurrences of hidden items, counted apart because what they hold is unknown.
     */
    public record BomRollup(String plm, double occurrences, double massKg, int withoutMass, double hiddenOccurrences) {}

    /**
     * GET /query/bom: the product root with the site kits under it, the roll-up per site and in total. Scoped to one
     * item's {@code subtree}, the root is that item, the tree crosses to another site where an item's external reference
     * names an item of the subtree (with the reference's quantity), and the roll-ups count the subtree.
     */
    public record Bom(String product, BomNode root, List<BomRollup> sites, BomRollup total, Provenance provenance,
                      String sparql, Timings timings, Policy policy, @JsonInclude(JsonInclude.Include.NON_NULL) Subtree subtree) {
        public Bom(String product, BomNode root, List<BomRollup> sites, BomRollup total, Provenance provenance, String sparql,
                   Timings timings, Policy policy) {
            this(product, root, sites, total, provenance, sparql, timings, policy, null);
        }
    }

    /**
     * GET /query/placements: every visible geometric part of the product, or of one item's {@code subtree}, with the world
     * placements of its occurrences, the reference occurrence (the identity, where the part's CAD file draws it) first,
     * and the number of occurrences listed.
     */
    public record Placements(String product, List<PartPlacements> parts, int occurrences, Provenance provenance, String sparql,
                             Timings timings, Policy policy, @JsonInclude(JsonInclude.Include.NON_NULL) Subtree subtree) {}

    /**
     * One part's occurrences as {@code [x, y, z, rx, ry, rz]}: translation in millimetres, then rotation in degrees about
     * the fixed x, y and z axes of the product frame, in that order, applied to the part as its CAD file draws it.
     */
    public record PartPlacements(String id, String plm, List<List<Double>> occurrences) {}

    /**
     * One parent a part is used under: the parent's id, PLM, name and part type, the quantity it uses and the part's
     * occurrences under it in the product; a parent the viewer may not see is {@code {redacted: true, plm, quantity}}.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BomUse(String id, String plm, String name, String partType, Boolean redacted, double quantity,
                         Double occurrences) {}

    /**
     * Where a part is used in a product's bill of materials ({@code bom_where_used}): its parents and its total
     * occurrences; a part the viewer may not see is a {@link Redacted} marker with no parents.
     */
    public record BomWhereUsed(String product, PartView part, List<BomUse> usedIn, double occurrences, Provenance provenance,
                               String sparql, Timings timings, Policy policy) {}

    /**
     * An external reference a visible part makes: its own id and PLM, the local {@code part} that makes it, the
     * {@code remoteUrn} as stored, the {@code target} the URN resolves to (absent when it resolves to no part, or to one
     * the viewer may not see), the {@code expectedRevision} and, on a visible target, its {@code currentRevision} and
     * canonical {@code lifecycleState}. {@code status} is {@code ok}, {@code danglingReference}, {@code staleRevision}
     * (with the shape's {@code message}) or {@code not-evaluable} (the target exists but the viewer may not see it).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Reference(String id, String plm, String part, String remoteUrn, PartRef target, Double quantity,
                            String expectedRevision, String currentRevision, String lifecycleState, String status,
                            String message, String note) {}

    /** GET /query/references: every reference of a product's visible parts, and how many have each status. */
    public record References(String product, List<Reference> references, Map<String, Integer> counts, Provenance provenance,
                             String sparql, Timings timings, Policy policy, @JsonInclude(JsonInclude.Include.NON_NULL) Subtree subtree) {
        public References(String product, List<Reference> references, Map<String, Integer> counts, Provenance provenance,
                          String sparql, Timings timings, Policy policy) {
            this(product, references, counts, provenance, sparql, timings, policy, null);
        }
    }

    /**
     * Answer of the guarded {@code sparql} tool: the query as executed (LIMIT applied), its form and
     * result ({@code columns}/{@code rows} for SELECT, {@code result} for ASK, {@code turtle} for
     * CONSTRUCT), the graph size it ran on and the time it took, with the provenance and policy of
     * the federation that built the graph.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SparqlResult(String query, String form, List<String> columns, List<List<Object>> rows,
                               Boolean limitReached, Boolean result, String turtle, Integer limit, long graphTriples,
                               long ms, Provenance provenance, Timings timings, Policy policy) {}

    /** A label in one language. */
    public record TermLabel(String lang, String label) {}

    /**
     * An item whose name holds a label of a term: its id, site and product, its native name, its English name when the
     * site writes another language, and the label found in it ({@code matched}); {@code narrower}, the English label of the
     * narrower concept that label belongs to when it is not the term's own (an O-ring found under seal).
     */
    public record TermPart(String id, String plm, String product, String name, @JsonInclude(JsonInclude.Include.NON_NULL) String nameEn,
                           TermLabel matched, @JsonInclude(JsonInclude.Include.NON_NULL) String narrower) {}

    /**
     * A concept of the products' glossary ({@code atelier:Terms}) that the text names: {@code match} {@code exact} when a
     * label is the text, {@code word} when a label holds its words; its preferred label per language, its further labels,
     * the concept it is a kind of, and the items the viewer may see whose names hold one of its labels or a narrower
     * concept's.
     */
    public record Term(String iri, String match, Map<String, String> labels,
                       @JsonInclude(JsonInclude.Include.NON_EMPTY) List<TermLabel> altLabels,
                       @JsonInclude(JsonInclude.Include.NON_NULL) String broader, List<TermPart> parts) {}

    /** {@code GET /query/terms?q=}: the concepts the text names, exact matches first. */
    public record Terms(String q, List<Term> terms, Provenance provenance, String sparql, Timings timings, Policy policy) {}

    /** An item of a found group: id, site, native name, English name when the site writes another language, occurrences when not one. */
    public record FoundItem(String id, String plm, String name, @JsonInclude(JsonInclude.Include.NON_NULL) String nameEn,
                            @JsonInclude(JsonInclude.Include.NON_NULL) Double occurrences) {}

    /**
     * Why an item matched: {@code via} {@code name} (its native name, in its site's language {@code lang}), {@code nameEn}
     * (its English name), {@code term} (a glossary term, {@code term} its English label, {@code label} the label found in the
     * item's name and {@code lang} its language, {@code narrower} the narrower concept that label belongs to when it is not
     * the term's own) or {@code partType}; {@code label} is the text found.
     */
    public record FoundMatch(String via, String label, @JsonInclude(JsonInclude.Include.NON_NULL) String lang,
                             @JsonInclude(JsonInclude.Include.NON_NULL) String term,
                             @JsonInclude(JsonInclude.Include.NON_NULL) String narrower) {}

    /**
     * Parts that one alternative of the query names, grouped: {@code match} {@code assembly} when the assembly's name holds
     * it (the parts are every part below that assembly), {@code parts} for matching parts under one parent {@code assembly};
     * {@code matched} says why.
     */
    public record FoundGroup(String match, FoundMatch matched, FoundItem assembly, List<FoundItem> parts) {}

    /** The {@code find_parts} answer: the groups, and how many items of the scope the viewer may not see ({@code hidden}). */
    public record FoundParts(String product, String query, List<FoundGroup> groups, int hidden, Provenance provenance, String sparql,
                             Timings timings, Policy policy) {}

    /**
     * One identifying value of a member as its site states it: the text or the number as stored ({@code stored}), the unit of
     * a length and the length in mm, and for a text resolved in a scheme the concept it names ({@code concept},
     * {@code conceptLabel}) with the thickness written inside it in mm. {@code fromStandard} marks a length the size
     * concept of the member's standard supplies, the site stating none.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MemberValue(String attribute, String stored, String unit, Double mm, String concept, String conceptLabel,
                              Boolean fromStandard) {}

    /** A purchased part as its site describes it: its IRI, its standard as written, its identifying values and shelf life. */
    public record EquivalentMember(String plm, String id, String iri, String name,
                                   @JsonInclude(JsonInclude.Include.NON_NULL) String standard, List<MemberValue> values,
                                   @JsonInclude(JsonInclude.Include.NON_NULL) Integer shelfLifeMonths) {}

    /**
     * An identifying attribute of a group with the first member's value: a length in mm, the concept a resolved text
     * names with its label as {@code text} (and the thickness in mm when the text states one), or the text as written.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GroupAttribute(String attribute, String label, Double mm, String concept, String text) {}

    /**
     * What the stores hold: the item stocked under {@code partNumbers} part numbers in {@code sites} sites, so
     * {@code stockLines} lines today, and one line once the members are confirmed as one item.
     */
    public record Stocking(int partNumbers, int sites, int stockLines, int stockLinesOnceConfirmed) {}

    /**
     * Parts of one product that are one purchased item: of one class of the {@code atelier:ItemClasses} scheme
     * ({@code itemClass} its notation, {@code classLabel} its label), agreeing on every identifying attribute the scheme
     * names for the class within its tolerance ({@code attributes}, the first member's values). {@code shelfLifeMonths}
     * is the shortest a member states. {@code confirmed} is true when a user confirmed the members as one item
     * (owl:sameAs between every two of them in the links graph); a group is a proposal otherwise.
     */
    public record EquivalentGroup(String itemClass, String classLabel, List<GroupAttribute> attributes,
                                  @JsonInclude(JsonInclude.Include.NON_NULL) Integer shelfLifeMonths, Stocking stocking,
                                  boolean confirmed, List<EquivalentMember> members) {}

    /** The purchased items a product's parts share across the sites ({@code equivalent_parts}), groups of two members or more. */
    public record Equivalents(String product, List<EquivalentGroup> groups, Provenance provenance, String sparql, Timings timings,
                              Policy policy) {}

    /**
     * One supplier offer: its native key at the site ({@code id}, from {@code {plm}/offer/{id}}), the part offered, the
     * supplier's own number for it, the lead time in days and whether it is preferred.
     */
    public record Offer(String id, String partId, String partName, String supplierPartNumber, Integer leadTimeDays, boolean preferred) {}

    /** A supplier as one site lists it: its key there, its town and its offers for the product's parts. */
    public record SupplierSite(String plm, String id, String location, List<Offer> offers) {}

    /** One supplier, by name, with each site that lists it. */
    public record Supplier(String name, List<SupplierSite> sites) {}

    /** A part with exactly one supplier offer: the supplier's name and the lead time. */
    public record SingleSource(String plm, String id, String name, String supplier, Integer leadTimeDays) {}

    /** A part whose preferred offers disagree on the lead time: the {@code conflictingLeadTime} finding's message. */
    public record LeadTimeConflict(String plm, String id, String name, String message) {}

    /**
     * The suppliers of one product's parts ({@code suppliers}): each supplier with the parts it offers per site, the
     * single-source parts and the parts whose preferred offers disagree on the lead time.
     */
    public record Suppliers(String product, List<Supplier> suppliers, List<SingleSource> singleSource,
                            List<LeadTimeConflict> conflicts, Provenance provenance, String sparql, Timings timings, Policy policy) {}
}
