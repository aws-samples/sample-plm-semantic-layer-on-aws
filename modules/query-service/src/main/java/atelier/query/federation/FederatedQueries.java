// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.federation;

import atelier.query.Atelier;
import atelier.query.policy.Policy;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Texts of the requests the federation sends, one CONSTRUCT per source and concern. The WHERE of
 * every request is a single basic graph pattern with a VALUES clause naming the parts it is about
 * and OPTIONAL patterns for values that may be absent: no request contains UNION, MINUS or BIND,
 * so an Ontop endpoint never reconciles the type of one variable across branches. VALUES lists are
 * sorted, which makes a request text a function of the profile's visible parts alone; a warm Ontop
 * endpoint therefore answers every later request of a profile from its translation cache.
 *
 * Requests of a run: the link store twice (the interface facts, including the product each interface belongs to, or for the parts and
 * bill-of-materials answers only the parts the interfaces name; then the file index, every part's CAD file and supplier), the core graph
 * three times (every product membership, so a part no interface names is named too; the tags of the named parts,
 * the profile FILTER inside the OPTIONAL so a part the viewer may not see comes back with
 * atelier:taggedBy alone and its tag never leaves the database; then the products the named parts
 * belong to, with each product's name and frame), then per PLM its description of its
 * visible parts and, per feature kind, the features on those parts with their quantity values.
 * Link stores may declare features with atelier:declaresFeature or, for plugs, atelier:declaresPlug; both
 * are read and both become atelier:declaresFeature. A bill-of-materials run asks each PLM, after its parts, for
 * the lines from its visible items (which places a hidden child under a visible parent) and the lines to them
 * (which tells an item under a hidden parent from its site kit, the one item no line reaches). The interfaces and parts
 * answers ask each PLM, after its parts, for the external references its visible parts make: the remote URN as
 * stored, which the shapes resolve. The path and flow answers ask the link store once more, after the core, for the
 * product's functional edges and the rated speeds of their parts ({@link #drives}).
 *
 * A subtree run names assemblies instead of parts ({@link Selection}): a PLM request carries {@code VALUES ?root} and
 * joins through {@code atelier:contains}, the site's closure of its own bill of materials, so the site's database
 * selects the trees and the request text grows with the number of roots, never with the number of parts. The structure
 * request ({@link #structure}) reads only which items each root contains and the lines between them; the facts of the
 * visible items are then read by the roots of the trees with no hidden item ({@link Selection#under}) and, for a
 * visible item above a hidden one, by that item alone ({@link Selection#only}), so a hidden item's row never leaves
 * its database. The link store is asked about the subtree's items in {@code VALUES} lists ({@link #interfaceFacts(Collection)},
 * {@link #fileIndex(Collection)}): it evaluates them natively, where an Ontop endpoint would expand them into one SQL
 * branch per IRI.
 */
public final class FederatedQueries {
    /** Prefix declarations of every request text. */
    public static final String PREFIXES = """
            PREFIX atelier:  <https://example.com/atelier/ontology#>
            PREFIX qudt: <http://qudt.org/schema/qudt/>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            """;

    private static final String INTERFACE_FACTS = """
            CONSTRUCT {
              ?if a atelier:Interface ; atelier:label ?ifLabel ; atelier:toleranceMm ?tol ; atelier:betweenPart ?part ;
                  atelier:declaresFeature ?f ; atelier:ofProduct ?ifProduct .
              ?f atelier:matesWith ?mate .
            }
            WHERE {
              GRAPH <%1$s> {
                ?if atelier:toleranceMm ?tol ; atelier:betweenPart ?part ; atelier:declaresFeature|atelier:declaresPlug ?f .
                OPTIONAL { ?f atelier:matesWith ?mate }
                OPTIONAL { ?if rdfs:label ?ifLabel }
                OPTIONAL { ?if atelier:ofProduct ?ifProduct }
              }
            }
            """.formatted(Atelier.LINKS_GRAPH);

    private static final String PART_FACTS = """
            CONSTRUCT { ?if atelier:betweenPart ?part ; atelier:ofProduct ?ifProduct . }
            WHERE {
              GRAPH <%1$s> { ?if atelier:betweenPart ?part . OPTIONAL { ?if atelier:ofProduct ?ifProduct } }
            }
            """.formatted(Atelier.LINKS_GRAPH);

    private static final String FILE_INDEX = """
            CONSTRUCT { ?part atelier:cadFile ?cad ; atelier:builtBy ?supplier . }
            WHERE {
              GRAPH <%1$s> {
                ?part atelier:cadFile|atelier:builtBy ?entry .
                OPTIONAL { ?part atelier:cadFile ?cad }
                OPTIONAL { ?part atelier:builtBy ?supplier }
              }
            }
            """.formatted(Atelier.FILE_INDEX_GRAPH);

    /** What the layer states about the items: their names from the labels graph and the equivalences users confirmed. */
    private static final String LAYER = """
            CONSTRUCT {
              ?part <http://www.w3.org/2004/02/skos/core#prefLabel> ?name ; <http://www.w3.org/2004/02/skos/core#altLabel> ?english ;
                    <http://www.w3.org/2002/07/owl#sameAs> ?same .
            }
            WHERE {
            %%s  OPTIONAL { GRAPH <%1$s> { ?part <http://www.w3.org/2004/02/skos/core#prefLabel> ?name } }
              OPTIONAL { GRAPH <%1$s> { ?part <http://www.w3.org/2004/02/skos/core#altLabel> ?english } }
              OPTIONAL { GRAPH <%2$s> { ?part <http://www.w3.org/2002/07/owl#sameAs> ?same } }
            }
            """.formatted(Atelier.LABELS_GRAPH, Atelier.LINKS_GRAPH);

    private static final String LABELS = """
            CONSTRUCT { ?s ?p ?o }
            WHERE { GRAPH <%1$s> { ?s ?p ?o } }
            """.formatted(Atelier.LABELS_GRAPH);

    private static final String MEMBERS = """
            CONSTRUCT { ?part atelier:partOf ?product . }
            WHERE { ?part atelier:partOf ?product . }
            """;

    /** The memberships of one product. */
    private static final String MEMBERS_OF_PRODUCT = """
            CONSTRUCT { ?part atelier:partOf <%1$s> . }
            WHERE { ?part atelier:partOf <%1$s> . }
            """;

    /** The memberships of the parts a VALUES list names. */
    private static final String MEMBERS_OF_PARTS = """
            CONSTRUCT { ?part atelier:partOf ?product . }
            WHERE {
            %s  ?part atelier:partOf ?product .
            }
            """;

    /** Who tagged each part: what tells a hidden part, which keeps only this, from an untagged one. */
    private static final String TAGGED = """
            CONSTRUCT { ?part atelier:taggedBy ?taggedBy . }
            WHERE {
            %s  ?part atelier:taggedBy ?taggedBy .
            }
            """;

    /** The tags the profile may read, the profile FILTER on the whole pattern, so the SQL tests the releasability once. */
    private static final String RELEASES = """
            CONSTRUCT { ?part atelier:releasableTo ?rel ; atelier:jurisdiction ?jur ; atelier:taggedAt ?taggedAt . }
            WHERE {
            %s  ?part atelier:taggedBy ?taggedBy ; atelier:releasableTo ?rel ; atelier:jurisdiction ?jur ; atelier:taggedAt ?taggedAt .
              %s
            }
            """;

    private static final String PRODUCTS = """
            CONSTRUCT {
              ?part atelier:partOf ?product .
              ?product a atelier:Product ; atelier:label ?productName ; atelier:frame ?frame ; atelier:massLimit ?ql .
              ?ql a qudt:QuantityValue ; qudt:numericValue ?limit ; qudt:unit ?ul .
            }
            WHERE {
            %s  ?part atelier:partOf ?product .
              ?product atelier:label ?productName .
              OPTIONAL { ?product atelier:frame ?frame }
              OPTIONAL { ?product atelier:massLimit ?ql . ?ql qudt:numericValue ?limit . OPTIONAL { ?ql qudt:unit ?ul } }
            }
            """;

    /** The product facts of {@link #PRODUCTS}, of every product, read once per product rather than once per part. */
    private static final String PRODUCT_FACTS = """
            CONSTRUCT {
              ?product a atelier:Product ; atelier:label ?productName ; atelier:frame ?frame ; atelier:massLimit ?ql .
              ?ql a qudt:QuantityValue ; qudt:numericValue ?limit ; qudt:unit ?ul .
            }
            WHERE {
            %s  ?product atelier:label ?productName .
              OPTIONAL { ?product atelier:frame ?frame }
              OPTIONAL { ?product atelier:massLimit ?ql . ?ql qudt:numericValue ?limit . OPTIONAL { ?ql qudt:unit ?ul } }
            }
            """;

    private static final String PARTS = """
            CONSTRUCT {
              ?part a atelier:Part ; atelier:label ?partName ; atelier:ownedBy ?owner ; atelier:sourceFileRef ?sourceFileRef ;
                    atelier:revision ?revision ; atelier:lifecycleLabel ?lifecycle ; atelier:material ?material ;
                    atelier:partType ?partType ; atelier:mass ?qm ;
                    atelier:standard ?standard ; atelier:nominalDiameter ?qd ; atelier:nominalLength ?ql ;
                    atelier:toothCount ?teeth ; atelier:gearModule ?qg %2$s.
              ?qm a qudt:QuantityValue ; qudt:numericValue ?m ; qudt:unit ?um .
              ?qg a qudt:QuantityValue ; qudt:numericValue ?g ; qudt:unit ?ug .
              ?qd a qudt:QuantityValue ; qudt:numericValue ?d ; qudt:unit ?ud .
              ?ql a qudt:QuantityValue ; qudt:numericValue ?l ; qudt:unit ?ul .
            }
            WHERE {
            %1$s  ?part a atelier:Part ; atelier:label ?partName .
              OPTIONAL { ?part atelier:ownedBy ?owner }
              OPTIONAL { ?part atelier:sourceFileRef ?sourceFileRef }
              OPTIONAL { ?part atelier:revision ?revision }
              OPTIONAL { ?part atelier:lifecycleLabel ?lifecycle }
              OPTIONAL { ?part atelier:material ?material }
              OPTIONAL { ?part atelier:partType ?partType }
              OPTIONAL { ?part atelier:mass ?qm . ?qm qudt:numericValue ?m . OPTIONAL { ?qm qudt:unit ?um } }
              OPTIONAL { ?part atelier:standard ?standard }
              OPTIONAL { ?part atelier:nominalDiameter ?qd . ?qd qudt:numericValue ?d . OPTIONAL { ?qd qudt:unit ?ud } }
              OPTIONAL { ?part atelier:nominalLength ?ql . ?ql qudt:numericValue ?l . OPTIONAL { ?ql qudt:unit ?ul } }
              OPTIONAL { ?part atelier:toothCount ?teeth }
              OPTIONAL { ?part atelier:gearModule ?qg . ?qg qudt:numericValue ?g . OPTIONAL { ?qg qudt:unit ?ug } }
            %3$s}
            """;

    /**
     * The attributes of a purchased item's class, which the equivalents answer adds to {@link #PARTS}: its class, an
     * O-ring's size and compound, a placard's legend, size, face material and adhesive, a container's dimensions and
     * shell material, a tyre's dimensions and ply rating, a wheel's rim and width, a brake's heat stack and rotors, and
     * the shelf life.
     */
    private static final String ITEM_TRIPLES = """
            ;
                    atelier:itemClass ?itemClass ; atelier:innerDiameter ?qi ; atelier:crossSection ?qc ; atelier:compound ?compound ;
                    atelier:legend ?legend ; atelier:itemWidth ?qw ; atelier:itemHeight ?qh ; atelier:facestock ?facestock ;
                    atelier:adhesive ?adhesive ; atelier:baseWidth ?qb ; atelier:itemDepth ?qe ; atelier:contourWidth ?qo ;
                    atelier:shellMaterial ?shellMaterial ; atelier:outerDiameter ?qt ; atelier:sectionWidth ?qs ;
                    atelier:rimDiameter ?qr ; atelier:plyRating ?plyRating ; atelier:heatStackDiameter ?qk ;
                    atelier:rotorCount ?rotors ; atelier:shelfLifeMonths ?shelfLife .
              ?qt a qudt:QuantityValue ; qudt:numericValue ?t ; qudt:unit ?ut .
              ?qs a qudt:QuantityValue ; qudt:numericValue ?sw ; qudt:unit ?us .
              ?qr a qudt:QuantityValue ; qudt:numericValue ?r ; qudt:unit ?ur .
              ?qk a qudt:QuantityValue ; qudt:numericValue ?k ; qudt:unit ?uk .
              ?qb a qudt:QuantityValue ; qudt:numericValue ?b ; qudt:unit ?ub .
              ?qe a qudt:QuantityValue ; qudt:numericValue ?e ; qudt:unit ?ue .
              ?qo a qudt:QuantityValue ; qudt:numericValue ?o ; qudt:unit ?uo .
              ?qi a qudt:QuantityValue ; qudt:numericValue ?i ; qudt:unit ?ui .
              ?qc a qudt:QuantityValue ; qudt:numericValue ?c ; qudt:unit ?uc .
              ?qw a qudt:QuantityValue ; qudt:numericValue ?w ; qudt:unit ?uw .
              ?qh a qudt:QuantityValue ; qudt:numericValue ?h ; qudt:unit ?uh """;

    private static final String ITEM_PATTERNS = """
              OPTIONAL { ?part atelier:itemClass ?itemClass }
              OPTIONAL { ?part atelier:innerDiameter ?qi . ?qi qudt:numericValue ?i . OPTIONAL { ?qi qudt:unit ?ui } }
              OPTIONAL { ?part atelier:crossSection ?qc . ?qc qudt:numericValue ?c . OPTIONAL { ?qc qudt:unit ?uc } }
              OPTIONAL { ?part atelier:compound ?compound }
              OPTIONAL { ?part atelier:legend ?legend }
              OPTIONAL { ?part atelier:itemWidth ?qw . ?qw qudt:numericValue ?w . OPTIONAL { ?qw qudt:unit ?uw } }
              OPTIONAL { ?part atelier:itemHeight ?qh . ?qh qudt:numericValue ?h . OPTIONAL { ?qh qudt:unit ?uh } }
              OPTIONAL { ?part atelier:facestock ?facestock }
              OPTIONAL { ?part atelier:adhesive ?adhesive }
              OPTIONAL { ?part atelier:baseWidth ?qb . ?qb qudt:numericValue ?b . OPTIONAL { ?qb qudt:unit ?ub } }
              OPTIONAL { ?part atelier:itemDepth ?qe . ?qe qudt:numericValue ?e . OPTIONAL { ?qe qudt:unit ?ue } }
              OPTIONAL { ?part atelier:contourWidth ?qo . ?qo qudt:numericValue ?o . OPTIONAL { ?qo qudt:unit ?uo } }
              OPTIONAL { ?part atelier:shellMaterial ?shellMaterial }
              OPTIONAL { ?part atelier:outerDiameter ?qt . ?qt qudt:numericValue ?t . OPTIONAL { ?qt qudt:unit ?ut } }
              OPTIONAL { ?part atelier:sectionWidth ?qs . ?qs qudt:numericValue ?sw . OPTIONAL { ?qs qudt:unit ?us } }
              OPTIONAL { ?part atelier:rimDiameter ?qr . ?qr qudt:numericValue ?r . OPTIONAL { ?qr qudt:unit ?ur } }
              OPTIONAL { ?part atelier:plyRating ?plyRating }
              OPTIONAL { ?part atelier:heatStackDiameter ?qk . ?qk qudt:numericValue ?k . OPTIONAL { ?qk qudt:unit ?uk } }
              OPTIONAL { ?part atelier:rotorCount ?rotors }
              OPTIONAL { ?part atelier:shelfLifeMonths ?shelfLife }
            """;


    private static final String DRIVES = """
            CONSTRUCT {
              ?d a atelier:Drive ; atelier:ofProduct ?product ; atelier:driver ?a ; atelier:driven ?b ; atelier:flow ?flow ;
                 atelier:viaInterface ?if ; atelier:reactionPart ?r .
              ?a atelier:drives ?b ; atelier:ratedSpeedRpm ?ra .
              ?b atelier:ratedSpeedRpm ?rb .
            }
            WHERE {
              GRAPH <%1$s> {
                %2$s?d a atelier:Drive ; atelier:ofProduct ?product ; atelier:driver ?a ; atelier:driven ?b ; atelier:flow ?flow .
                OPTIONAL { ?d atelier:viaInterface ?if }
                OPTIONAL { ?d atelier:reactionPart ?r }
                OPTIONAL { ?a atelier:ratedSpeedRpm ?ra }
                OPTIONAL { ?b atelier:ratedSpeedRpm ?rb }
              }
            }
            """;

    /** A variant group of the options graph: the group's own facts and its options'. */
    private static final String VARIANT = """
            CONSTRUCT { ?variant ?vp ?vo . ?option ?op ?oo . }
            WHERE {
              GRAPH <%1$s> {
                VALUES ?variant { <%2$s> }
                ?variant ?vp ?vo .
                OPTIONAL { ?option atelier:optionOf ?variant ; ?op ?oo }
              }
            }
            """;

    /** The variant groups of a product and their options' codes, from the options graph. */
    private static final String GROUPS = """
            CONSTRUCT {
              ?variant atelier:ofProduct ?product ; atelier:defaultOption ?default ; rdfs:label ?name ; rdfs:comment ?selects .
              ?option atelier:optionOf ?variant .
            }
            WHERE {
              GRAPH <%1$s> {
                VALUES ?product { <%2$s> }
                ?variant atelier:ofProduct ?product ; atelier:defaultOption ?default .
                ?option atelier:optionOf ?variant .
                OPTIONAL { ?variant rdfs:label ?name }
                OPTIONAL { ?variant rdfs:comment ?selects }
              }
            }
            """;

    /**
     * What applies under an option of a variant group: the base items of the default option, and the items, interfaces,
     * lines, line occurrences and functional edges only another option holds, with the mates of the option interfaces'
     * features, the option interfaces' labels as the interface facts read them, and the translation values of the occurrences.
     */
    private static final String UNDER_OPTIONS = """
            CONSTRUCT { ?s ?p ?o . ?s atelier:label ?ifLabel . ?f atelier:matesWith ?mate . ?o qudt:numericValue ?value ; qudt:unit ?unit . }
            WHERE {
              GRAPH <%1$s> {
                VALUES ?variant { <%2$s> }
                ?option atelier:optionOf ?variant .
                ?s atelier:appliesUnderOption ?option ; ?p ?o .
                OPTIONAL { ?s a atelier:Interface ; rdfs:label ?ifLabel }
                OPTIONAL { ?s atelier:declaresFeature ?f . ?f atelier:matesWith ?mate }
                OPTIONAL { ?o qudt:numericValue ?value ; qudt:unit ?unit }
              }
            }
            """;

    private static final String OFFERS = """
            CONSTRUCT {
              ?offer a atelier:SupplierOffer ; atelier:offersPart ?part ; atelier:fromSupplier ?supplier ;
                     atelier:supplierPartNumber ?number ; atelier:leadTimeDays ?days ; atelier:preferred ?preferred .
              ?supplier a atelier:Supplier ; atelier:label ?name ; atelier:location ?location .
            }
            WHERE {
            %s  ?offer a atelier:SupplierOffer ; atelier:offersPart ?part ; atelier:fromSupplier ?supplier ;
                     atelier:leadTimeDays ?days ; atelier:preferred ?preferred .
              OPTIONAL { ?offer atelier:supplierPartNumber ?number }
              OPTIONAL { ?supplier atelier:label ?name }
              OPTIONAL { ?supplier atelier:location ?location }
            }
            """;

    private static final String FEATURES = """
            CONSTRUCT {
              ?f a %1$s ; atelier:identifier ?fid ; atelier:ownedBy ?owner ; atelier:onPart ?part ;
                 atelier:positionX ?qx ; atelier:positionY ?qy ; atelier:positionZ ?qz ;
                 atelier:port ?port ; atelier:appliesUnderOption ?option ;
            %2$s  ?qx a qudt:QuantityValue ; qudt:numericValue ?x ; qudt:unit ?ux .
              ?qy a qudt:QuantityValue ; qudt:numericValue ?y ; qudt:unit ?uy .
              ?qz a qudt:QuantityValue ; qudt:numericValue ?z ; qudt:unit ?uz .
            %3$s}
            WHERE {
            %4$s  ?f a %1$s ; atelier:identifier ?fid ; atelier:ownedBy ?owner ; atelier:onPart ?part .
              OPTIONAL { ?f atelier:positionX ?qx . ?qx qudt:numericValue ?x . OPTIONAL { ?qx qudt:unit ?ux } }
              OPTIONAL { ?f atelier:positionY ?qy . ?qy qudt:numericValue ?y . OPTIONAL { ?qy qudt:unit ?uy } }
              OPTIONAL { ?f atelier:positionZ ?qz . ?qz qudt:numericValue ?z . OPTIONAL { ?qz qudt:unit ?uz } }
              OPTIONAL { ?f atelier:port ?port }
              OPTIONAL { ?f atelier:appliesUnderOption ?option }
            %5$s}
            """;

    private static final String BOM_LINES = """
            CONSTRUCT {
              ?line a atelier:BomLine ; atelier:parent ?parent ; atelier:child ?child ; atelier:quantity ?quantity .
            }
            WHERE {
            %s  ?line a atelier:BomLine ; atelier:%s ?part ; atelier:parent ?parent ; atelier:child ?child ; atelier:quantity ?quantity .
            }
            """;

    private static final String REFERENCES = """
            CONSTRUCT {
              ?ref a atelier:ExternalReference ; atelier:fromPart ?part ; atelier:remoteUrn ?urn ; atelier:quantity ?quantity ;
                   atelier:expectedRevision ?expected ; rdfs:comment ?note .
            }
            WHERE {
            %s  ?ref a atelier:ExternalReference ; atelier:fromPart ?part ; atelier:remoteUrn ?urn .
              OPTIONAL { ?ref atelier:quantity ?quantity }
              OPTIONAL { ?ref atelier:expectedRevision ?expected }
              OPTIONAL { ?ref rdfs:comment ?note }
            }
            """;

    private static final String STRUCTURE = """
            CONSTRUCT {
              ?root atelier:contains ?part .
              ?line a atelier:BomLine ; atelier:parent ?part ; atelier:child ?child ; atelier:quantity ?quantity .
            }
            WHERE {
            %s  OPTIONAL { ?line a atelier:BomLine ; atelier:parent ?part ; atelier:child ?child ; atelier:quantity ?quantity }
            }
            """;

    /**
     * The occurrences of the lines from the items the selection binds, with their placements and the line's ends, the
     * children the viewer may not see left in the database ({@code %2$s}: a FILTER on the hidden children, or empty).
     */
    private static final String PLACEMENTS = """
            CONSTRUCT {
              ?line atelier:parent ?part ; atelier:child ?child .
              ?occ a atelier:Occurrence ; atelier:ofLine ?line ; atelier:index ?index ;
                   atelier:translationX ?qx ; atelier:translationY ?qy ; atelier:translationZ ?qz ;
                   atelier:rotationX ?rx ; atelier:rotationY ?ry ; atelier:rotationZ ?rz .
              ?qx qudt:numericValue ?x ; qudt:unit ?ux .
              ?qy qudt:numericValue ?y ; qudt:unit ?uy .
              ?qz qudt:numericValue ?z ; qudt:unit ?uz .
            }
            WHERE {
            %1$s  ?line atelier:parent ?part ; atelier:child ?child .
              ?occ atelier:ofLine ?line ; atelier:index ?index ;
                   atelier:translationX ?qx ; atelier:translationY ?qy ; atelier:translationZ ?qz ;
                   atelier:rotationX ?rx ; atelier:rotationY ?ry ; atelier:rotationZ ?rz .
              ?qx qudt:numericValue ?x . OPTIONAL { ?qx qudt:unit ?ux }
              ?qy qudt:numericValue ?y . OPTIONAL { ?qy qudt:unit ?uy }
              ?qz qudt:numericValue ?z . OPTIONAL { ?qz qudt:unit ?uz }
            %2$s}
            """;

    /** The stations and sections of one product and its station axis, from the links graph ({@code %2$s}: the product). */
    private static final String STATIONS = """
            PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
            PREFIX dcterms: <http://purl.org/dc/terms/>
            CONSTRUCT {
              ?product atelier:stationAxis ?axis ; atelier:stationsSymmetric ?symmetric ; atelier:stationMeasure ?measure .
              ?station a atelier:Station ; skos:notation ?id ; atelier:sectionOf ?product ; atelier:stationPosition ?qp ;
                       atelier:stationBasis ?basis ; dcterms:source ?source ; atelier:stationTolerance ?qt .
              ?qp qudt:numericValue ?at ; qudt:unit ?up .
              ?qt qudt:numericValue ?tolerance ; qudt:unit ?ut .
              ?section a atelier:Section ; skos:notation ?sid ; rdfs:label ?name ; atelier:sectionOf ?product ;
                       atelier:ownedBy ?owner ; atelier:sectionSide ?side ; atelier:hasStation ?bound ; atelier:sectionPart ?part .
            }
            WHERE {
              VALUES ?product { <%2$s> }
              GRAPH <%1$s> {
                ?product atelier:stationAxis ?axis ; atelier:stationsSymmetric ?symmetric .
                OPTIONAL { ?product atelier:stationMeasure ?measure }
                {
                  ?station a atelier:Station ; skos:notation ?id ; atelier:sectionOf ?product ; atelier:stationPosition ?qp ;
                           atelier:stationBasis ?basis .
                  ?qp qudt:numericValue ?at . OPTIONAL { ?qp qudt:unit ?up }
                  OPTIONAL { ?station dcterms:source ?source }
                  OPTIONAL { ?station atelier:stationTolerance ?qt . ?qt qudt:numericValue ?tolerance . OPTIONAL { ?qt qudt:unit ?ut } }
                } UNION {
                  ?section a atelier:Section ; skos:notation ?sid ; atelier:sectionOf ?product ; atelier:ownedBy ?owner ;
                           atelier:sectionSide ?side ; atelier:hasStation ?bound .
                  OPTIONAL { ?section rdfs:label ?name }
                  OPTIONAL { ?section atelier:sectionPart ?part }
                }
              }
            }
            """;

    /** The stored span of the parts the selection binds, in the PLM's unit. */
    private static final String SPANS = """
            CONSTRUCT {
              ?part atelier:spanFrom ?qf ; atelier:spanTo ?qt .
              ?qf a qudt:QuantityValue ; qudt:numericValue ?f ; qudt:unit ?uf .
              ?qt a qudt:QuantityValue ; qudt:numericValue ?t ; qudt:unit ?ut .
            }
            WHERE {
            %s  ?part atelier:spanFrom ?qf ; atelier:spanTo ?qt .
              ?qf qudt:numericValue ?f . OPTIONAL { ?qf qudt:unit ?uf }
              ?qt qudt:numericValue ?t . OPTIONAL { ?qt qudt:unit ?ut }
            }
            """;

    /** The stored span of the occurrences of the lines from the items the selection binds, the hidden children left out. */
    private static final String OCCURRENCE_SPANS = """
            CONSTRUCT {
              ?occ atelier:spanFrom ?qf ; atelier:spanTo ?qt .
              ?qf a qudt:QuantityValue ; qudt:numericValue ?f ; qudt:unit ?uf .
              ?qt a qudt:QuantityValue ; qudt:numericValue ?t ; qudt:unit ?ut .
            }
            WHERE {
            %1$s  ?line atelier:parent ?part ; atelier:child ?child .
              ?occ atelier:ofLine ?line ; atelier:spanFrom ?qf ; atelier:spanTo ?qt .
              ?qf qudt:numericValue ?f . OPTIONAL { ?qf qudt:unit ?uf }
              ?qt qudt:numericValue ?t . OPTIONAL { ?qt qudt:unit ?ut }
            %2$s}
            """;

    /**
     * The parts a request is about, as the head of its WHERE body binding {@code ?part}: parts named one by one,
     * every item a root contains, each root alone, or the parts of a product.
     */
    public record Selection(String clause) {
        /** {@code VALUES ?part { ... }}: the parts themselves. */
        public static Selection parts(Collection<String> parts) {
            return new Selection(values("part", parts));
        }

        /** Every item each root contains at any depth of its site's bill of materials, the root included. */
        public static Selection under(Collection<String> roots) {
            return new Selection(values("root", roots) + "  ?root atelier:contains ?part .\n");
        }

        /** Each root alone, read through the closure's pair of the item with itself. */
        public static Selection only(Collection<String> roots) {
            return new Selection(values("root", roots) + "  ?root atelier:contains ?part .\n  FILTER (?part = ?root)\n");
        }

        /** Every part the source holds: no constant at all, so the text is the same whatever the parts. */
        public static Selection everyPart() {
            return new Selection("");
        }

        /** The parts of a product, by the core graph's membership: one constant, however many parts the product has. */
        public static Selection ofProduct(String productIri) {
            return new Selection("  ?part atelier:partOf <" + productIri + "> .\n");
        }
    }

    /** Per feature kind: what the template says about the feature, its quantity nodes, and the OPTIONAL patterns read. */
    private record Kind(String constructed, String quantities, String read) {}

    private static final Map<String, Kind> KINDS = Map.of(
            "plug", new Kind("""
                    atelier:connectorType ?type ; atelier:pinCount ?pins .
                    """, "", """
                    OPTIONAL { ?f atelier:connectorType ?type }
                    OPTIONAL { ?f atelier:pinCount ?pins }
                    """),
            "fastener", new Kind("""
                    atelier:fastenerStandard ?fstd ; atelier:fastenerCount ?fcount ; atelier:diameter ?qd ; atelier:gripLength ?qg .
                    """, """
                    ?qd a qudt:QuantityValue ; qudt:numericValue ?d ; qudt:unit ?ud .
                    ?qg a qudt:QuantityValue ; qudt:numericValue ?g ; qudt:unit ?ug .
                    """, """
                    OPTIONAL { ?f atelier:fastenerStandard ?fstd }
                    OPTIONAL { ?f atelier:fastenerCount ?fcount }
                    OPTIONAL { ?f atelier:diameter ?qd . ?qd qudt:numericValue ?d . OPTIONAL { ?qd qudt:unit ?ud } }
                    OPTIONAL { ?f atelier:gripLength ?qg . ?qg qudt:numericValue ?g . OPTIONAL { ?qg qudt:unit ?ug } }
                    """),
            "coupling", new Kind("""
                    atelier:couplingStandard ?cstd ; atelier:dashSize ?dash ; atelier:pressureRating ?qr ; atelier:fluid ?fluid .
                    """, """
                    ?qr a qudt:QuantityValue ; qudt:numericValue ?r ; qudt:unit ?ur .
                    """, """
                    OPTIONAL { ?f atelier:couplingStandard ?cstd }
                    OPTIONAL { ?f atelier:dashSize ?dash }
                    OPTIONAL { ?f atelier:pressureRating ?qr . ?qr qudt:numericValue ?r . OPTIONAL { ?qr qudt:unit ?ur } }
                    OPTIONAL { ?f atelier:fluid ?fluid }
                    """));

    private FederatedQueries() {}

    /** The link store's interface facts: label, product, tolerance, parts, declared features and their mates. */
    public static String interfaceFacts() {
        return PREFIXES + INTERFACE_FACTS;
    }

    /** The parts the link store's interfaces name, and the product of each interface. */
    public static String partFacts() {
        return PREFIXES + PART_FACTS;
    }

    /** The file index: every part's CAD file and supplier, whether or not an interface names the part. */
    public static String fileIndex() {
        return PREFIXES + FILE_INDEX;
    }

    /**
     * The link store's interface facts of the interfaces with at least one side among {@code sides}: a subtree run asks
     * once, with the subtree's items, so the store returns the subtree's interfaces and not the product's.
     */
    public static String interfaceFacts(Collection<String> sides) {
        return PREFIXES + scoped(INTERFACE_FACTS, "?if atelier:betweenPart ?side .", "side", sides);
    }

    /** The file index of the parts named: their CAD files and suppliers. */
    public static String fileIndex(Collection<String> parts) {
        return PREFIXES + scoped(FILE_INDEX, "", "part", parts);
    }

    /**
     * The link store's names of {@code parts} (native and English, from the labels graph) and the parts users confirmed
     * each one to be ({@code owl:sameAs}, from the links graph). Asked about the visible items only, so a hidden item's
     * names never leave the store.
     */
    public static String layer(Collection<String> parts) {
        return PREFIXES + LAYER.formatted(values(parts));
    }

    /** The whole labels graph: the glossary's concepts and every item's names. */
    public static String labels() {
        return PREFIXES + LABELS;
    }

    /**
     * A link-store template restricted by {@code VALUES} over the IRIs, with {@code pattern} as the first pattern of its
     * graph. The link store evaluates the VALUES natively, so the list costs it a join, not one request per IRI.
     */
    private static String scoped(String template, String pattern, String variable, Collection<String> iris) {
        String graph = template.substring(template.indexOf("  GRAPH <"), template.indexOf("{", template.indexOf("  GRAPH <")) + 1);
        String head = "WHERE {\n" + values(variable, iris) + graph + (pattern.isEmpty() ? "" : "\n    " + pattern);
        return template.replace("WHERE {\n" + graph, head);
    }

    /**
     * The link store's functional edges of the product (atelier:Drive, with atelier:drives beside each) and the rated speeds
     * of their parts; of every product when {@code productIri} is null.
     */
    public static String drives(String productIri) {
        String product = productIri == null ? "" : "VALUES ?product { <" + productIri + "> }\n                ";
        return PREFIXES + DRIVES.formatted(Atelier.LINKS_GRAPH, product);
    }

    /** The variant groups of the product of that IRI with their options, from the options graph. */
    public static String groups(String productIri) {
        return PREFIXES + GROUPS.formatted(Atelier.OPTIONS_GRAPH, productIri);
    }

    /** The variant group of that IRI and its options, from the options graph. */
    public static String variant(String variantIri) {
        return PREFIXES + VARIANT.formatted(Atelier.OPTIONS_GRAPH, variantIri);
    }

    /** What applies under the options of the variant group of that IRI, from the options graph ({@link #UNDER_OPTIONS}). */
    public static String underOptions(String variantIri) {
        return PREFIXES + UNDER_OPTIONS.formatted(Atelier.OPTIONS_GRAPH, variantIri);
    }

    /** Every product membership of the core graph (atelier:partOf): the parts of a product, whether or not an interface names them. */
    public static String members() {
        return PREFIXES + MEMBERS;
    }

    /** The memberships of the product of that IRI: its parts, whether or not an interface names them. */
    public static String members(String productIri) {
        return PREFIXES + MEMBERS_OF_PRODUCT.formatted(productIri);
    }

    /** The memberships of {@code parts}: every product each belongs to. */
    public static String membersOf(Collection<String> parts) {
        return PREFIXES + MEMBERS_OF_PARTS.formatted(values(parts));
    }

    /**
     * The core graph's tags of {@code parts}, in two requests: who tagged each part, then the profile's releasable tags with
     * their jurisdiction and date, the FILTER on the whole pattern; a hidden part keeps only atelier:taggedBy.
     */
    public static List<String> tags(Policy.Profile profile, Collection<String> parts) {
        return tags(profile, Selection.parts(parts));
    }

    /** As {@link #tags(Policy.Profile, Collection)}, of the parts {@code selection} binds. */
    public static List<String> tags(Policy.Profile profile, Selection selection) {
        return List.of(PREFIXES + TAGGED.formatted(selection.clause()), PREFIXES + RELEASES.formatted(selection.clause(), profile.filter()));
    }

    /** The core graph's products of {@code parts} (atelier:partOf), each with its name and, when stated, its frame and mass limit. */
    public static String products(Collection<String> parts) {
        return products(Selection.parts(parts));
    }

    /**
     * Every product with its name and, when stated, its frame and mass limit: with the memberships {@link #members()} reads,
     * what {@link #products(Collection)} of every member reads, but for the products that hold no part.
     */
    public static String productFacts() {
        return PREFIXES + PRODUCT_FACTS.formatted("");
    }

    /** The facts of the product of that IRI alone: with {@link #members(String)}, what a run scoped to it keeps of {@link #products}. */
    public static String productFacts(String productIri) {
        return PREFIXES + PRODUCT_FACTS.formatted(values("product", List.of(productIri)));
    }

    /** As {@link #products(Collection)}, of the parts {@code selection} binds. */
    public static String products(Selection selection) {
        return PREFIXES + PRODUCTS.formatted(selection.clause());
    }

    /**
     * A PLM's description (label, owning site, source file reference), attributes (revision, lifecycle word, mass,
     * material, part type), for a purchased item standard and nominal size, and for a gear tooth count and module of
     * {@code parts}.
     */
    public static String parts(Collection<String> parts) {
        return parts(Selection.parts(parts));
    }

    /** As {@link #parts(Collection)}, about the parts {@code selection} binds. */
    public static String parts(Selection selection) {
        return PREFIXES + PARTS.formatted(selection.clause(), "", "");
    }

    /** As {@link #parts(Collection)}, with the attributes of each purchased item's class. */
    public static String purchased(Collection<String> parts) {
        return PREFIXES + PARTS.formatted(Selection.parts(parts).clause(), ITEM_TRIPLES, ITEM_PATTERNS);
    }

    /** A PLM's features of one kind ({@link Atelier#KINDS}) on {@code parts}, with their positions and kind-specific values. */
    public static String features(String kind, Collection<String> parts) {
        return features(kind, Selection.parts(parts));
    }

    /** As {@link #features(String, Collection)}, on the parts {@code selection} binds. */
    public static String features(String kind, Selection selection) {
        Kind k = KINDS.get(kind);
        String cls = "atelier:" + Atelier.localName(Atelier.FEATURE_CLASSES.get(kind));
        return PREFIXES + FEATURES.formatted(cls, indent(k.constructed(), 5), indent(k.quantities(), 2), selection.clause(),
                indent(k.read(), 2));
    }

    /** A PLM's supplier offers for {@code parts}, each with its supplier's name and town. */
    public static String offers(Collection<String> parts) {
        return offers(Selection.parts(parts));
    }

    /** As {@link #offers(Collection)}, for the parts {@code selection} binds. */
    public static String offers(Selection selection) {
        return PREFIXES + OFFERS.formatted(selection.clause());
    }

    /** A PLM's bill-of-materials lines whose parent is one of {@code parts}: parent, child and quantity. */
    public static String bomLinesFrom(Collection<String> parts) {
        return PREFIXES + BOM_LINES.formatted(values(parts), "parent");
    }

    /** A PLM's bill-of-materials lines whose child is one of {@code parts}: parent, child and quantity. */
    public static String bomLinesTo(Collection<String> parts) {
        return PREFIXES + BOM_LINES.formatted(values(parts), "child");
    }

    /** A PLM's external references made by {@code parts}: the remote URN as stored, quantity, expected revision and note. */
    public static String references(Collection<String> parts) {
        return references(Selection.parts(parts));
    }

    /** As {@link #references(Collection)}, made by the parts {@code selection} binds. */
    public static String references(Selection selection) {
        return PREFIXES + REFERENCES.formatted(selection.clause());
    }

    /**
     * A PLM's line placements under {@code roots}: the occurrences of every line from an item a root contains, with their
     * translations and rotations, except those of the {@code hidden} children, which stay in the site's database. One
     * request per site whatever the size of the trees: the roots select through the closure, the hidden children are
     * the only IRIs listed.
     */
    public static String placements(Collection<String> roots, Collection<String> hidden) {
        String filter = hidden.isEmpty() ? "" : "  FILTER (?child NOT IN (" + hidden.stream().sorted().map(iri -> "<" + iri + ">")
                .collect(Collectors.joining(", ")) + "))\n";
        return PREFIXES + PLACEMENTS.formatted(Selection.under(roots).clause(), filter);
    }

    /** The stations and sections of {@code productIri} and its station axis, from the link store's links graph. */
    public static String stations(String productIri) {
        return PREFIXES + STATIONS.formatted(Atelier.LINKS_GRAPH, productIri);
    }

    /** A PLM's stored spans of {@code parts} along the product's station axis. */
    public static String spans(Collection<String> parts) {
        return PREFIXES + SPANS.formatted(Selection.parts(parts).clause());
    }

    /**
     * A PLM's stored spans of the occurrences under {@code roots}, selected as {@link #placements} selects them, the
     * {@code hidden} children left in the site's database.
     */
    public static String occurrenceSpans(Collection<String> roots, Collection<String> hidden) {
        String filter = hidden.isEmpty() ? "" : "  FILTER (?child NOT IN (" + hidden.stream().sorted().map(iri -> "<" + iri + ">")
                .collect(Collectors.joining(", ")) + "))\n";
        return PREFIXES + OCCURRENCE_SPANS.formatted(Selection.under(roots).clause(), filter);
    }

    /**
     * A PLM's trees under {@code roots}: every item each root contains ({@code ?root atelier:contains ?part}, the root
     * included) and the bill-of-materials lines from those items, ids and quantities only.
     */
    public static String structure(Collection<String> roots) {
        return PREFIXES + STRUCTURE.formatted(Selection.under(roots).clause());
    }

    /** {@code VALUES ?part { ... }} over the IRIs, sorted, one per line, indented as the WHERE body. */
    private static String values(Collection<String> parts) {
        return values("part", parts);
    }

    private static String values(String variable, Collection<String> iris) {
        return "  VALUES ?" + variable + " {\n" + iris.stream().sorted().map(iri -> "    <" + iri + ">\n").collect(Collectors.joining())
                + "  }\n";
    }

    private static String indent(String block, int spaces) {
        if (block.isEmpty()) return "";
        String pad = " ".repeat(spaces);
        return block.lines().map(line -> line.isEmpty() ? line : pad + line).collect(Collectors.joining("\n", "", "\n"));
    }
}
