// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.mcp;

import atelier.query.Atelier;
import atelier.query.preview.PreviewService;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The twenty-five tools of the MCP server, as docs/contract.md lists them: name, description (what it
 * returns, the question templates it answers, its arguments) and input schema. Every description
 * ends with the same guidance on choosing a tool.
 */
final class ToolCatalogue {
    /** The optional product argument of the tools that answer across interfaces: the key of a product the products tool lists. */
    static final String PRODUCT_ARGUMENT = " Optional argument: product, the key of one product as the products tool lists it, to answer"
            + " for that product's interfaces only; absent means every product.";
    /** The optional product argument of the tools that name one interface: interface ids are unique within a product only. */
    static final String INTERFACE_PRODUCT_ARGUMENT = " Optional argument: product, the key of the product the interface belongs to, as the"
            + " products tool lists it; interface ids are unique within a product, so it is required when several products have"
            + " an interface of that id (the error then names them) and may be omitted when only one does.";
 /** The optional root argument of the tools that can answer for one assembly. */
    static final String ROOT_ARGUMENT = " Optional argument: root, the id of an item of the product (an assembly, a site kit or a part):"
            + " scopes the answer to that assembly across the sites, its own site's tree and, through the external references"
            + " out of it, the trees of the items they name on the other sites, resolved in rounds (subtree reports the root,"
            + " the rounds with the roots each asked about, depth and items); the product rules (massLimit, massScale) are not"
            + " evaluated on a subtree. The product's key as root, or no root, is the whole product.";
    static final String PREFER = "Prefer a named tool; use sparql only when no named tool answers.";
    static final String ANSWER = " Read-only; returns compact JSON with provenance (endpoint, kind, requests, triples, ms,"
            + " requestBytes and largestRequestBytes per source) and policy (profile, actor); the federated request texts and SQL are not included,"
            + " evidence(interface, endpoint) returns them when asked.";
    static final List<String> ENDPOINTS = List.of("ontop-fr", "ontop-de", "ontop-uk", "ontop-es", "ontop-core", "neptune");
    static final List<String> SOURCES = List.of("fr", "de", "uk", "es", "core");
    /** Levels of the bill of materials the bom tool lists by default: the root, the site kits and their first level. */
    static final int BOM_DEPTH = 3;

    private static final McpSchema.ToolAnnotations READ_ONLY = new McpSchema.ToolAnnotations(null, true, false, true, false, null);

    private ToolCatalogue() {}

    static List<McpSchema.Tool> tools() {
        return List.of(
                tool("products", "The products the parts are assembled into, as the Atelier core states them: key, name,"
                        + " frame (the coordinate frame of the product's CAD and positions) and partCount, the number of its"
                        + " parts the caller's profile may see, and findings, the product rules no PLM can evaluate alone:"
                        + " massLimit (the sites' items weigh more than the product's limit), massScale (a site stores its"
                        + " masses in another unit), and massLimit {status, limitKg, hiddenItems}: pass or fail over every item,"
                        + " or not-evaluable when items of the product are hidden from the profile, which then raises neither"
                        + " rule, and lifecycleConflicts, how many of its visible items are released while an item they depend"
                        + " on (a bill-of-materials child or an external reference's target) is working or blocked, the"
                        + " lifecycleConflict finding each such part carries. Keys are what the product argument of the other tools"
                        + " takes. Templates: 'Which products are there?', 'How many parts of each product can I"
                        + " see?', 'Is the cubesat within its mass limit?', 'Which products release parts over unreleased ones?'." + ANSWER, Map.of(), List.of()),
                tool("list_interfaces", "Every interface as the caller's profile sees it: id, label, parts"
                        + " (id, plm, and supplier for a supplier-built part; a part the profile may not see is"
                        + " {redacted: true, plm}), status (pass, fail,"
                        + " not-evaluable) and rulesFailing. Templates: 'Which interfaces fail and on which rules?',"
                        + " 'How many interfaces pass, fail or are not evaluable?', 'Which joints involve the DE PLM?'."
                        + PRODUCT_ARGUMENT + " With root (product then required), the interfaces with a side in that assembly's"
                        + " subtree; a part on the far side, outside it, is marked context: true." + ROOT_ARGUMENT + ANSWER,
                        Map.of("product", string("Product key, as the products tool lists it"),
                                "root", string("Id of an item of the product whose subtree the answer is about")), List.of()),
                tool("parts", "The parts of one product as the caller's profile sees them, each with id, plm, name (native, in the"
                        + " site's language), nameEn (the layer's English name), cadFile,"
                        + " jurisdiction, releasableTo, supplier, revision, lifecycle and lifecycleState, mass, material,"
                        + " partType and data-quality findings; an item the profile may not see is {redacted: true, plm}."
                        + " Templates: 'Which parts does the gearbox hold, across the sites?', 'Which parts of the hub control"
                        + " have no CAD file?'. Argument: product, required." + ROOT_ARGUMENT + ANSWER,
                        Map.of("product", string("Product key, as the products tool lists it"),
                                "root", string("Id of an item of the product whose subtree the answer is about")),
                        List.of("product")),
                tool("interface_check", "The full check of one interface, the Interface JSON of GET /query/interfaces/{id}:"
                        + " status, toleranceMm, parts, every feature (plug, fastener, coupling) with its stored and"
                        + " normalised values and mates, and the violations with measured detail (deltaMm, pinCount,"
                        + " diameterMm, ratingBar). Templates: 'Does IF-07 pass?', 'Why does IF-13 fail?', 'What are the"
                        + " plug positions on IF-01?'. Argument: interface, an id such as IF-07, or its IRI, which names its"
                        + " product itself." + INTERFACE_PRODUCT_ARGUMENT + ANSWER,
                        Map.of("interface", string("Interface id, e.g. IF-07, or its IRI"),
                                "product", string("Product key the interface belongs to, as the products tool lists it")),
                        List.of("interface")),
                tool("where_used", "Where a part is used at interfaces (joints between parts): the interfaces naming it with their status, the other parts"
                        + " (mates) on each, and the part's features per kind (plug, fastener, coupling) per interface"
                        + " and in total. A part the profile may not see is {redacted: true, plm} with no interfaces."
                        + " Templates: 'Where is FR-ORN-KEEL-001 used?', 'Which interfaces does the left inner spar sit on"
                        + " and with which parts?', 'How many plugs does part X carry at its joints?'. Argument: part, a"
                        + " native id (FR-ORN-KEEL-001, SPAR-6110-L) or IRI. For the assemblies a part is built into, use"
                        + " bom_where_used." + PRODUCT_ARGUMENT + ANSWER,
                        Map.of("part", string("Part native id or IRI"), "product", string("Product key, as the products tool lists it")),
                        List.of("part")),
                tool("impact_of_change", "What a change would touch: the interfaces concerned with their current status"
                        + " and rulesFailing, and the features concerned on both sides (the subject's features and their"
                        + " mates; a mate on a hidden part is {redacted: true, plm}). Templates: 'What does changing plug"
                        + " FR-ORN-PCMD-001-J03 affect?', 'Which interfaces and mated features are touched if WURZ-R-61080 changes?'."
                        + " Arguments: exactly one of part or feature, each a native id or IRI." + PRODUCT_ARGUMENT + ANSWER,
                        Map.of("part", string("Part native id or IRI"), "feature", string("Feature native id or IRI"),
                                "product", string("Product key, as the products tool lists it")), List.of()),
                tool("export_status", "Export-control status of a part for the caller's profile: visible (whether the"
                        + " profile may see it); when visible, part carries jurisdiction, releasableTo, taggedBy, taggedAt,"
                        + " supplier (supplier-built parts only), cadFile and a presigned cadUrl, and cadAvailable says"
                        + " whether that URL exists; a hidden part is"
                        + " {redacted: true, plm} with visible false. Templates: 'Is HMOT-70090 releasable to me?', 'Who"
                        + " tagged part X and under which jurisdiction?', 'Can I open the CAD of part X?'. Argument: part,"
                        + " a native id or IRI." + ANSWER,
                        Map.of("part", string("Part native id or IRI")), List.of("part")),
                tool("bom", "The bill of materials of one product, which no PLM holds: the product root, under it each"
                        + " site's kit (FR, DE, UK, ES), then each site's tree read from its own idiom; each node with id, plm,"
                        + " name (native), partType (PRODUCT, ASSEMBLY, PART), revision, lifecycle and lifecycleState, quantity"
                        + " per parent, occurrences (quantities multiplied down the tree), unitMassKg and extendedMassKg; an item"
                        + " the profile may not see is {redacted: true, plm, quantity, occurrences} and is not expanded. Also the"
                        + " roll-up per site and in total: part occurrences, massKg, withoutMass (part nodes without a mass) and"
                        + " hiddenOccurrences. Templates: 'What is the whole bill of materials of the wind turbine?', 'How many"
                        + " parts does each site contribute and how heavy are they?', 'What does the FR rotor kit contain?'."
                        + " Arguments: product, required; depth, optional, the levels below the root to list (default "
                        + BOM_DEPTH + "; the roll-ups always cover the whole tree). With root, the tree under that item, crossing"
                        + " to another site where an item's external reference names an item of the subtree." + ROOT_ARGUMENT + ANSWER,
                        Map.of("product", string("Product key, as the products tool lists it"),
                                "depth", Map.of("type", "integer", "description", "Levels below the root to list", "minimum", 1),
                                "root", string("Id of an item of the product whose subtree the answer is about")),
                        List.of("product")),
                tool("bom_where_used", "Where a part is used in a product's bill of materials: its parents (the assemblies"
                        + " or site kit that hold it, each with id, plm, name, partType, the quantity it uses and the part's"
                        + " occurrences under it; a parent the profile may not see is {redacted: true, plm, quantity}) and"
                        + " its total occurrences in the product. Templates: 'Which assemblies use D-37002?', 'How many of"
                        + " part X does the product hold in total?'. For the interfaces (joints) a part sits on, use"
                        + " where_used. Arguments: part, a native id or IRI, and product, both required." + ANSWER,
                        Map.of("part", string("Part native id or IRI"), "product", string("Product key, as the products tool lists it")),
                        List.of("part", "product")),
                tool("path_between", "The shortest paths of parts joined by interfaces between two parts of one product, which"
                        + " no PLM can follow since the interfaces join parts of different sites: at most 5 paths of at most 12"
                        + " steps through parts the profile may see, each step with from and to ({id, plm}) and joint (the"
                        + " interface's id, label, status for the profile: pass, fail or not-evaluable, and the rules it fails);"
                        + " notes say when a part the profile may not see breaks or shortens the paths; parts describes every part"
                        + " on them. Templates: 'How is the left drive spring connected to the right wheel?', 'Which joints lie"
                        + " between the hub and the generator?'. Arguments: product, from and to, native part ids, all required."
                        + " Then show the path with the frontend tool isolate_parts." + ANSWER,
                        Map.of("product", string("Product key, as the products tool lists it"), "from", string("Native id of the first part"),
                                "to", string("Native id of the last part")), List.of("product", "from", "to")),
                tool("flow_path", "Function through one product: the walk along its functional edges (atelier:drives, the"
                        + " layer's own knowledge of where power or motion goes, across the sites) from one part, downstream or,"
                        + " with direction up, upstream for root cause; every flow kind or one of mechanical, electrical,"
                        + " hydraulic, steam. Each step with from, to, flow, joint (the interface joining them, with its status"
                        + " for the profile; absent for an edge no interface joins), reaction (the fixed ring of a planetary stage)"
                        + " and stage (a gear mesh: driver and driven gear with teeth and module in mm, and its ratio). Downstream"
                        + " through meshing gears, ratio is the overall gear ratio (input over output speed, speedUp its inverse)"
                        + " with the gears and sites involved, and checks carry a rated speed through the train against the rated"
                        + " speed of a part downstream, within 1 %. parts describe every part reached, with their findings"
                        + " (meshModule on a gear driven by a gear of another module). Templates: 'What does the cart's drive"
                        + " spring drive?', 'What is the wind turbine's gear ratio from the hub to the generator?', 'What feeds"
                        + " the pitch cylinder?' (direction up). Arguments: product and from, required; flow and direction,"
                        + " optional. Then show the flow with the frontend tool isolate_parts." + ANSWER,
                        Map.of("product", string("Product key, as the products tool lists it"), "from", string("Native id of the part to start from"),
                                "flow", enumOf("Only edges of this flow kind", List.of("mechanical", "electrical", "hydraulic", "steam")),
                                "direction", enumOf("down (the default): what the part drives; up: what drives it", List.of("down", "up"))),
                        List.of("product", "from")),
                tool("variant_diff", "One option of a product's variant group against the group's default option, which the base"
                        + " product is built with: what changes on the mating interfaces of the parts both configurations hold"
                        + " (the hosts), port by port. Each port with host ({id, plm}), port (the connection point the host's feature"
                        + " serves, null for a feature paired by its id), change (added, removed, changed, same, or not-modelled: a"
                        + " port one option has that the data does not model, never compared), differences (interface, status, mate,"
                        + " then host.<attribute> or mate.<attribute>, such as mate.diameter or mate.position), and against and"
                        + " option, each the interface using the port, its status for the profile, the rules it fails, the host's"
                        + " feature, the mated part and its feature with every attribute as stored and converted. Also removed and"
                        + " added (the items and interfaces only one side holds), configuration and baseline (interfaces and"
                        + " their tally pass/fail/not evaluable, part occurrences per site, mass in kg, product findings: the"
                        + " interface rules run on each configuration) and interfaces (the option's own, as interface_check"
                        + " reports them). An unknown group or option is an error listing the product's groups with their"
                        + " options, the default first. Templates: 'What changes on the mating interfaces if the ornithopter takes the spring return?'"
                        + " (group wing-return, option spring-return), 'What if the crank is forged in one piece?'. Arguments:"
                        + " product, group and option, required. Then show the option's parts with the frontend tool"
                        + " isolate_parts." + ANSWER,
                        Map.of("product", string("Product key, as the products tool lists it"), "group", string("Variant group key"),
                                "option", string("Option code, not the group's default")), List.of("product", "group", "option")),
                tool("external_references", "The external references of one product's visible parts: a site never copies"
                        + " another site's part, it names it by URN (urn:plm:<site>:part:<local id>) with the revision it expects."
                        + " Each reference with id, plm, part (the local part that makes it), remoteUrn as stored, target (id"
                        + " and plm of the part the URN resolves to, absent when it resolves to none or to a part the profile"
                        + " may not see), quantity, expectedRevision, currentRevision and lifecycleState of a visible target,"
                        + " note, and status: ok, danglingReference (the URN is malformed or resolves to no part of any PLM),"
                        + " staleRevision (the target's revision differs or it is SUPERSEDED; message says how) or"
                        + " not-evaluable (the target is hidden); counts per status. Templates: 'Which references of the"
                        + " cart point at nothing?', 'Which parts expect a revision that has moved on?'. Argument: product,"
                        + " required." + ROOT_ARGUMENT + ANSWER,
                        Map.of("product", string("Product key, as the products tool lists it"),
                                "root", string("Id of an item of the product whose subtree the answer is about")), List.of("product")),
                tool("equivalent_parts", "Purchased parts of one product that are the same item under different part numbers"
                        + " at different sites. The classes it knows are those of the ontology's ItemClasses scheme:"
                        + " fastener (standard resolved in the FastenerStandard scheme, so DIN 912, NF EN ISO 4762 and"
                        + " BS EN ISO 4762 are one socket head cap screw, nominal diameter and length within 0.1 mm),"
                        + " o-ring (inner diameter and cross-section within 0.01 mm, stated or given by an AS568 dash size or"
                        + " ISO 3601-1 code, and the compound resolved in the Materials scheme in any language), placard"
                        + " (legend ignoring case and spaces, width and height within 0.5 mm, face material with its thickness"
                        + " and adhesive resolved in the Materials scheme), container (IATA type resolved in the ULDTypes"
                        + " scheme, base width, depth, height and contour width within 1 mm, shell material in the Materials"
                        + " scheme), tyre (outer diameter and section width within 1 mm, rim diameter within 0.5 mm, ply rating),"
                        + " wheel (rim diameter within 0.5 mm, width within 1 mm) and brake (heat stack diameter within 1 mm,"
                        + " rotors). Each group has itemClass, classLabel, attributes"
                        + " (the identifying values: mm, concept, text), shelfLifeMonths when a member states one, stocking"
                        + " (partNumbers and sites stocking it today, stockLines, and 1 once confirmed), confirmed (true once a"
                        + " user confirmed the members as one item on the Purchasing screen; a proposal otherwise, which only the"
                        + " user confirms) and its members, each with plm, id, iri, name (native), standard as the site writes"
                        + " it, values as stored with unit, mm and resolved concept, and shelfLifeMonths. Templates:"
                        + " 'Which parts of the rover are the same screw?', 'Which O-rings of the wind turbine are one item, and"
                        + " how many stock lines would that save?'. Argument: product, required." + ANSWER,
                        Map.of("product", string("Product key, as the products tool lists it")), List.of("product")),
                tool("find_term", "A term in English, German, French or Spanish resolved against the products' glossary (the"
                        + " atelier:Terms scheme of the layer's labels graph): the concepts it names, exact matches first (match"
                        + " exact or word), each with its labels in en, de, fr and es, its further labels, the concept it is a"
                        + " kind of (broader), and the parts of every product and site, as the profile sees them, whose names"
                        + " hold one of its labels or a narrower concept's: id, plm, product, name (native, in the site's"
                        + " language), nameEn and the label matched. Each site names its parts in its own language only, so"
                        + " this is how one word finds the Zahnrad, the pignon and the gear. Templates: 'Which parts are gears?',"
                        + " 'What is a Zahnrad?', 'Where are the roues dentées?', 'Show me the cranks of every product'."
                        + " Argument: term, required." + ANSWER,
                        Map.of("term", string("A term in English, German, French or Spanish, e.g. gear, Zahnrad, engrenage")), List.of("term")),
                tool("find_parts", "The parts of one product that a query names by what they are, for 'show me X' and"
                        + " 'where is X': query is matched against every item's native name and English name, the glossary's"
                        + " terms in English, German, French and Spanish (a Flügel is a wing, a roue dentée a gear) and part"
                        + " types (software, document), without case, accents or plural endings; the matching is lexical, so"
                        + " pass the word in each language a site may write it in, as alternatives separated by commas (wing,"
                        + " Flügel, aile, ala). Answers groups: match assembly when an assembly's name holds the query, with every"
                        + " part below it (the innermost assembly that matches); match parts for the matching parts under one"
                        + " parent assembly. Each group has matched, why its items matched: via (name: the site's native name,"
                        + " nameEn: the English name, term: a glossary term, partType), label (the text or label found), lang"
                        + " (its language), term (the glossary term's English label) and narrower (the narrower term the label"
                        + " belongs to: seals find O-rings, fasteners screws and nuts); assembly {id, plm, name, nameEn}; and"
                        + " parts [{id, plm, name, nameEn, occurrences when not one}]. Items the profile may not see are never"
                        + " named; hidden counts them. Choose from the groups the parts the user means, and pass their ids to"
                        + " isolate_parts or highlight_parts. Templates: 'Show both wings', 'Show the gearbox and the generator' (one call per"
                        + " group), 'Where are the fasteners?' (query 'screw, bolt, nut, washer, rivet, pin': a family is"
                        + " its kinds, alternatives separated by commas). Arguments: product, required; query, required."
                        + ROOT_ARGUMENT + ANSWER,
                        Map.of("product", string("Product key, as the products tool lists it"),
                                "query", string("What the parts are, alternatives separated by commas, in the four languages"
                                        + " when the sites may write it differently, e.g. gearbox, Getriebe, boîte de vitesses, multiplicadora"),
                                "root", string("Id of an item of the product whose subtree the answer is about")),
                        List.of("product", "query")),
                tool("suppliers", "The suppliers of one product's parts, from each site's own supplier list: per supplier"
                        + " (by name) the sites that list it and, per site, its offers: the offer id (its key at the site), the part, supplierPartNumber,"
                        + " leadTimeDays and preferred; the singleSource parts (exactly one offer) with their supplier; and"
                        + " the conflicts, parts whose preferred offers disagree on the lead time (the conflictingLeadTime"
                        + " finding). Templates: 'Which suppliers does the rover depend on?', 'Which parts are single-source?',"
                        + " 'Which parts have conflicting lead times?'. Argument: product, required." + ANSWER,
                        Map.of("product", string("Product key, as the products tool lists it")), List.of("product")),
                tool("parts_between_stations", "What lies between two stations of one product (wing stations, fuselage"
                        + " frames: positions along the product's station axis, which no PLM holds): the visible parts whose span"
                        + " along the axis overlaps the range by more than 1 mm, inside it or crossing one of its ends, each with"
                        + " its span as its PLM stores it (span, unit: IN for the UK site) and in mm, position (inside or"
                        + " crossing), the stations it covers and, for a part placed several times, occurrenceCount and the"
                        + " occurrences in the range (index, fromMm, toMm, position). Spans are computed from the CAD geometry."
                        + " On a symmetric axis station n is at +n on the right and -n on the left; side left, right or both"
                        + " (default). Every station carries its basis (printed, measured, joint, inferred) and notes name the"
                        + " inferred stations: say so when an answer rests on one. Templates: 'What sits between WS 1500 and WS"
                        + " 3600?', 'What sits between WS 3000 and WS 4100 on the right wing?', 'Which parts cross WS 3000?'."
                        + " Arguments: product, from and to (station ids such as WS 1500), required; side, optional. Then show"
                        + " the range with the frontend tool isolate_parts: the parts inside as the selection, the crossing parts"
                        + " as context." + ANSWER,
                        Map.of("product", string("Product key, as the products tool lists it"), "from", string("Station id, e.g. WS 1500"),
                                "to", string("Station id, e.g. WS 3600"),
                                "side", enumOf("The side of the plane of symmetry: left, right or both (the default)", List.of("left", "right", "both"))),
                        List.of("product", "from", "to")),
                tool("section_joints", "The sections of one product and the joints between them: each section (id, name,"
                        + " owner site, side, stations in order, the part it is when one part is the section) with the visible"
                        + " parts overlapping it and its foreign parts (owned by another site), and each joint, a station two"
                        + " sections share, with its signed position in mm, the two sections, their owners and the visible parts"
                        + " crossing it (span more than 1 mm on both sides), each with its site. Every station carries its basis"
                        + " and notes name the inferred ones. Templates: 'Which parts cross the right wing root joint, and who"
                        + " owns each side?', 'Which sections hold parts of another site?', 'Who owns the outer wing?'."
                        + " Argument: product, required; station, optional, the joints at that station only." + ANSWER,
                        Map.of("product", string("Product key, as the products tool lists it"), "station", string("Station id, e.g. WS 700")),
                        List.of("product")),
                tool("evidence", "One endpoint's contribution to an interface's answer, from the same run: the request"
                        + " text sent to it, Ontop's SQL for it (Ontop endpoints), the triples it supplied as Turtle and"
                        + " the native table rows behind them, with requests, tripleCount and ms. Templates: 'What SQL did"
                        + " the UK PLM run for IF-07?', 'Which native rows back IF-02?', 'What did Neptune contribute to"
                        + " IF-09?'. Arguments: interface (an id or its IRI) and endpoint, one of " + ENDPOINTS + "."
                        + INTERFACE_PRODUCT_ARGUMENT + ANSWER,
                        Map.of("interface", string("Interface id, e.g. IF-07, or its IRI"), "endpoint", enumOf("Endpoint name", ENDPOINTS),
                                "product", string("Product key the interface belongs to, as the products tool lists it")),
                        List.of("interface", "endpoint")),
                tool("ontology", "The ontology the sparql tool is checked against: classes, properties with domain and"
                        + " range, the named graphs, the rules, and example patterns. Read it before writing SPARQL.",
                        Map.of(), List.of()),
                tool("sparql", "Guarded free-form SPARQL over the merged graph of the interfaces the caller's profile may"
                        + " see: the graph is in memory after export-control redaction and the query never reaches the"
                        + " sources. Rules: SELECT, ASK or CONSTRUCT only; no SERVICE and no update forms; call ontology"
                        + " first; every class and predicate must be one it lists (an unknown IRI in predicate position or"
                        + " as the object of rdf:type is rejected with the list of known ones, never answered with empty"
                        + " rows); LIMIT 200 is enforced. Prefixes: atelier: <https://example.com/atelier/ontology#>, qudt:"
                        + " <http://qudt.org/schema/qudt/>, unit: <http://qudt.org/vocab/unit/>, rdfs:"
                        + " <http://www.w3.org/2000/01/rdf-schema#>. IRIs: parts <https://example.com/atelier/{plm}/part/{id}>,"
                        + " features .../{plm}/plug|fastener|coupling/{id}, interfaces <https://example.com/atelier/interface/{product}/{id}>"
                        + " (an interface's product is its atelier:ofProduct; its id is unique within that product),"
                        + " products <https://example.com/atelier/product/{key}> (a part's product is its atelier:partOf);"
                        + " {plm} is fr, de, uk or es. Quantity values (positions, diameter, gripLength, pressureRating)"
                        + " are nodes with qudt:numericValue and qudt:unit. Templates: 'How many plugs does each PLM own?',"
                        + " 'Which features have a position without a unit?', 'Which interfaces have a tolerance above 2"
                        + " mm?'. Returns query (as run, LIMIT applied), form, columns and rows (SELECT), result (ASK) or"
                        + " turtle (CONSTRUCT), limitReached, graphTriples, ms." + PRODUCT_ARGUMENT + ANSWER,
                        Map.of("query", string("SPARQL 1.1 SELECT, ASK or CONSTRUCT"),
                                "product", string("Product key, as the products tool lists it")), List.of("query")),
                tool("preview_correction", "What the rules would say if a correction were released, without releasing it:"
                        + " the cells as a site's release form builds them, each {plm, table, key, column, value} in that site's own"
                        + " tables and words (read catalogue(plm) for the tables, columns and accepted values), written into a copy"
                        + " of the product's merged graph as the site's R2RML maps them, then every rule run before and after."
                        + " Returns cells (the triples each rewrote), interfaces (status before and after), parts and products,"
                        + " each with fixed, stillFailing and newlyFailing results, and the tally. A cell outside the site's"
                        + " vocabulary, on a key or foreign-key column, or on a record the profile may not see is refused with the"
                        + " reason. A preview never writes: nothing reaches any PLM, the link store or the change log; a release"
                        + " is the owning site's action from the screen. Templates: 'What if the DE rail were at revision 02?',"
                        + " 'Would aligning P21 on x fix IF-02?'. Arguments: product, required; cells, at most "
                        + PreviewService.MAX_CELLS + "." + ROOT_ARGUMENT + ANSWER,
                        Map.of("product", string("Product key, as the products tool lists it"),
                                "root", string("Id of an item of the product whose subtree the preview is about"),
                                "cells", cells()),
                        List.of("product", "cells")),
                tool("catalogue", "The ORM-generated data catalogue of one PLM (GET /{plm}/catalogue): its tables with"
                        + " columns, Java types, units (fixed, or the per-row unit column), descriptions, ontology terms"
                        + " and key columns. Read this before writing SQL: tables, columns, units, descriptions, ontology"
                        + " terms, key columns. Templates: 'Which tables does the UK PLM have?', 'What does column pos_uom"
                        + " hold?', 'Which column of the ES conector table is undescribed?'. Argument: plm, one of "
                        + SOURCES + ". Returns the PLM service's JSON as it came. " + PREFER,
                        Map.of("plm", enumOf("PLM code", SOURCES)), List.of("plm")),
                tool("sql", "Catalogue-grounded text-to-SQL over one PLM's native tables, validated and run by that PLM"
                        + " service (POST /{plm}/sql). Guard, enforced there for every caller: one SELECT statement (CTEs"
                        + " allowed), no update, DDL, COPY, function definition, pg_* or information_schema; only tables"
                        + " and columns of that PLM's catalogue (an unknown name is rejected with the closest catalogue"
                        + " entries); export-control rows filtered server-side for the caller's profile; LIMIT 200; 5 s"
                        + " timeout; read-only transaction. The answer includes the SQL that actually ran (after the"
                        + " export-control rewrite), the rows, the row count, ms and the catalogue entries used. Call"
                        + " catalogue(plm) first and write SQL only against the listed tables and columns. Templates:"
                        + " 'How many connectors does the FR PLM store per part?', 'Which UK harness connectors have a"
                        + " NULL pos_uom?'. Arguments: plm, one of " + SOURCES + "; query, the SELECT; purpose, one"
                        + " sentence on the question it serves. Returns the PLM service's JSON as it came; a rejection"
                        + " is an error carrying the PLM's reason and suggestions. " + PREFER,
                        Map.of("plm", enumOf("PLM code", SOURCES), "query", string("One SQL SELECT statement"),
                                "purpose", string("What the question is, one sentence")),
                        List.of("plm", "query", "purpose")));
    }

    private static McpSchema.Tool tool(String name, String description, Map<String, Object> properties, List<String> required) {
        String text = description.endsWith(PREFER) ? description : description + " " + PREFER;
        return McpSchema.Tool.builder().name(name).description(text)
                .inputSchema(new McpSchema.JsonSchema("object", new LinkedHashMap<>(properties), required, false, null, null))
                .annotations(READ_ONLY).build();
    }

    private static Map<String, Object> string(String description) {
        return Map.of("type", "string", "description", description);
    }

    /** The cells of a preview: each names a site, a native table, a row key and a column, with the value (null clears it). */
    private static Map<String, Object> cells() {
        Map<String, Object> cell = new LinkedHashMap<>();
        cell.put("plm", enumOf("PLM code of the site that owns the record", Atelier.PLMS));
        cell.put("table", string("Native table, as the site's catalogue lists it"));
        cell.put("key", string("Key of the row"));
        cell.put("column", string("Native column"));
        cell.put("value", Map.of("type", List.of("number", "string", "boolean", "null"), "description", "The value as the column takes it"));
        return Map.of("type", "array", "description", "The cells of the correction", "items",
                Map.of("type", "object", "properties", cell, "required", List.of("plm", "table", "key", "column", "value")));
    }

    private static Map<String, Object> enumOf(String description, List<String> values) {
        return Map.of("type", "string", "description", description, "enum", values);
    }
}
