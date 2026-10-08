// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.query.mcp;

import atelier.query.Atelier;
import atelier.query.InterfaceRef;
import atelier.query.QueryService;
import atelier.query.api.Json;
import atelier.query.api.QueryController;
import atelier.query.api.StationsJson;
import atelier.query.policy.Caller;
import atelier.query.policy.Policy;
import atelier.query.preview.PreviewJson;
import atelier.query.preview.PreviewService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The MCP tools over the query service. Each call runs for the profile named by the caller's
 * {@code x-atelier-profile} header and echoes the actor of its {@code x-atelier-actor} header, both read
 * from the HTTP request that carried the call (the transport captures them per request), and
 * answers one text content holding the JSON the REST API gives without the federation request
 * texts ({@code sparql}): provenance keeps each endpoint's requests, triples and ms, and the
 * {@code evidence} tool carries an endpoint's request text and SQL when asked. A tool that names an
 * interface takes its id and, since ids are unique within a product only, an optional {@code product}
 * key; an id several products have is an error naming them. A rejected
 * argument, an unknown id, a refused SPARQL query, a PLM service
 * rejection or a failing endpoint is an error result carrying the reason; a failing endpoint is
 * named as in provenance ({@code ontop-fr}, {@code neptune}), its detail stays in the server log.
 */
@Component
public class AtelierTools {
    private final QueryService service;
    private final Policy policy;
    private final ObjectMapper json;
    private final PlmApi plm;
    private final PreviewService preview;
    private final KnownTerms terms;
    private final OntologyCatalogue ontology;
    private final List<McpSchema.Tool> tools;

    @Autowired
    public AtelierTools(QueryService service, Policy policy, ObjectMapper json, PlmApi plm, PreviewService preview) {
        this(service, policy, json, plm, KnownTerms.load(), preview);
    }

    /** Without a preview service: the preview_correction tool reports itself unavailable. */
    public AtelierTools(QueryService service, Policy policy, ObjectMapper json, PlmApi plm) {
        this(service, policy, json, plm, KnownTerms.load());
    }

    public AtelierTools(QueryService service, Policy policy, ObjectMapper json, PlmApi plm, KnownTerms terms) {
        this(service, policy, json, plm, terms, null);
    }

    public AtelierTools(QueryService service, Policy policy, ObjectMapper json, PlmApi plm, KnownTerms terms, PreviewService preview) {
        this.service = service;
        this.policy = policy;
        this.json = json;
        this.plm = plm;
        this.preview = preview;
        this.terms = terms;
        this.ontology = OntologyCatalogue.of(terms);
        this.tools = ToolCatalogue.tools();
    }

    public List<McpSchema.Tool> tools() {
        return tools;
    }

    /** The ontology the sparql tool is checked against, as the ontology tool and GET /query/ontology answer it. */
    public OntologyCatalogue ontology() {
        return ontology;
    }

    public List<McpServerFeatures.SyncToolSpecification> specifications() {
        return tools.stream().map(tool -> McpServerFeatures.SyncToolSpecification.builder().tool(tool)
                .callHandler((exchange, request) -> call(tool.name(), request.arguments(), callerOf(exchange))).build()).toList();
    }

    /** The caller of the request that carried the call: profile {@code unknown} and actor {@code user} when the headers are absent. */
    Caller callerOf(McpSyncServerExchange exchange) {
        Object profile = exchange.transportContext().get(QueryController.PROFILE_HEADER);
        Object actor = exchange.transportContext().get(QueryController.ACTOR_HEADER);
        return policy.caller(profile == null ? null : profile.toString(), actor == null ? null : actor.toString());
    }

    public McpSchema.CallToolResult call(String tool, Map<String, Object> arguments, Caller caller) {
        Map<String, Object> args = arguments == null ? Map.of() : arguments;
        try {
            return switch (tool) {
                case "products" -> text(service.products(caller));
                case "list_interfaces" -> text(listInterfaces(caller, optional(args, "product"), root(args)));
                case "parts" -> parts(service.parts(caller, arg(args, "product"), root(args)));
                case "interface_check" -> text(interfaceCheck(args, caller));
                case "where_used" -> text(service.whereUsed(arg(args, "part"), caller, optional(args, "product"))
                        .orElseThrow(() -> new IllegalArgumentException("no interface names part " + arg(args, "part"))));
                case "impact_of_change" -> text(impact(args, caller));
                case "export_status" -> text(service.exportStatus(arg(args, "part"), caller)
                        .orElseThrow(() -> new IllegalArgumentException("no interface names part " + arg(args, "part"))));
                case "bom" -> text(bom(args, caller));
                case "bom_where_used" -> text(service.bomWhereUsed(arg(args, "part"), caller, arg(args, "product"))
                        .orElseThrow(() -> new IllegalArgumentException("no item " + arg(args, "part") + " in " + arg(args, "product"))));
                case "path_between" -> text(service.paths(caller, arg(args, "product"), item(args, "from"), item(args, "to")));
                case "flow_path" -> text(service.flow(caller, arg(args, "product"), item(args, "from"), optional(args, "flow"),
                        "up".equals(optional(args, "direction"))));
                case "external_references" -> text(service.references(caller, arg(args, "product"), root(args)));
                case "variant_diff" -> text(service.variantDiff(caller, arg(args, "product"), arg(args, "group"), arg(args, "option")));
                case "equivalent_parts" -> text(service.equivalents(caller, arg(args, "product")));
                case "find_term" -> text(service.terms(caller, arg(args, "term")));
                case "find_parts" -> text(service.findParts(caller, arg(args, "product"), arg(args, "query"), root(args)));
                case "suppliers" -> text(service.suppliers(caller, arg(args, "product")));
                case "parts_between_stations" -> text(service.stations(caller, arg(args, "product"), arg(args, "from"), arg(args, "to"),
                        optional(args, "side")));
                case "section_joints" -> text(sectionJoints(args, caller));
                case "evidence" -> text(evidence(args, caller));
                case "ontology" -> text(ontology.describe());
                case "sparql" -> text(SparqlRunner.run(SparqlGuard.check(arg(args, "query"), terms),
                        service.answer(caller, optional(args, "product"))));
                case "preview_correction" -> text(previewCorrection(args, caller));
                case "catalogue" -> plmReply(plm.catalogue(source(args), caller));
                case "sql" -> plmReply(plm.sql(source(args), arg(args, "query"), arg(args, "purpose"), caller));
                default -> error("unknown tool " + tool);
            };
        } catch (RuntimeException e) {
            return error(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    /** Every interface (of one product when {@code product} is non-null) in five fields, plus up to three rule messages when it fails. */
    private Json.InterfaceSummaries listInterfaces(Caller caller, String product, String root) {
        Json.InterfacesResponse full = service.interfaces(caller, product, root);
        List<Json.InterfaceSummary> summaries = full.interfaces().stream().map(i -> new Json.InterfaceSummary(i.id(), i.product(), i.label(),
                i.status(), i.parts().stream().<Json.PartView>map(p -> p instanceof Json.Part q
                        ? new Json.PartRef(q.id(), q.plm(), q.supplier(), q.context()) : p).toList(),
                i.violations().stream().map(Json.Violation::rule).distinct().toList(),
                i.violations().isEmpty() ? null : i.violations().stream().map(Json.Violation::message).limit(3).toList())).toList();
        return new Json.InterfaceSummaries(summaries, full.provenance(), full.timings(), full.policy(), full.subtree());
    }

    /** The preview of the cells the {@code cells} argument lists, for the product (and root) the arguments name. */
    private PreviewJson.Answer previewCorrection(Map<String, Object> args, Caller caller) {
        if (preview == null) throw new IllegalStateException("preview_correction is not available here");
        if (!(args.get("cells") instanceof List<?>)) throw new IllegalArgumentException("argument 'cells' is required: a list of {plm, table, key, column, value}");
        List<PreviewJson.Cell> cells = json.convertValue(args.get("cells"), new TypeReference<List<PreviewJson.Cell>>() {});
        return preview.preview(caller, new PreviewJson.Request(arg(args, "product"), optional(args, "root"), cells));
    }

    /** The product's bill of materials, its tree listed to {@code depth} levels below the root; the roll-ups are the whole tree's. */
    private Json.Bom bom(Map<String, Object> args, Caller caller) {
        String depthArg = optional(args, "depth");
        int depth;
        try {
            depth = depthArg == null ? ToolCatalogue.BOM_DEPTH : (int) Double.parseDouble(depthArg);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("depth must be a number of levels, got " + depthArg);
        }
        if (depth < 1) throw new IllegalArgumentException("depth must be at least 1");
        Json.Bom full = service.bom(caller, arg(args, "product"), root(args));
        return new Json.Bom(full.product(), cut(full.root(), depth), full.sites(), full.total(), full.provenance(), full.sparql(),
                full.timings(), full.policy(), full.subtree());
    }

    /** The node with its children listed to {@code depth} more levels; below that its children are left out. */
    private static Json.BomNode cut(Json.BomNode node, int depth) {
        List<Json.BomItem> children = depth == 0 ? null : node.children().stream()
                .map(c -> c instanceof Json.BomNode n ? (Json.BomItem) cut(n, depth - 1) : c).toList();
        return new Json.BomNode(node.id(), node.plm(), node.name(), node.nameEn(), node.partType(), node.revision(), node.lifecycle(),
                node.lifecycleState(), node.quantity(), node.occurrences(), node.unitMassKg(), node.extendedMassKg(), children);
    }

    /** The sections answer, its joints cut to those at one station when the station argument names one. */
    private StationsJson.Sections sectionJoints(Map<String, Object> args, Caller caller) {
        StationsJson.Sections all = service.sections(caller, arg(args, "product"));
        String station = optional(args, "station");
        if (station == null) return all;
        if (all.stations().stream().noneMatch(s -> s.id().equals(station))) {
            throw new IllegalArgumentException("unknown station " + station);
        }
        return new StationsJson.Sections(all.product(), all.axis(), all.stations(), all.sections(),
                all.joints().stream().filter(j -> j.station().id().equals(station)).toList(), all.notes(), all.provenance(),
                all.sparql(), all.timings(), all.policy());
    }

    private Json.Impact impact(Map<String, Object> args, Caller caller) {
        String part = optional(args, "part");
        String feature = optional(args, "feature");
        if ((part == null) == (feature == null)) throw new IllegalArgumentException("name either part or feature");
        return service.impact(part, feature, caller, optional(args, "product")).orElseThrow(() -> new IllegalArgumentException(
                part != null ? "no interface names part " + part : "no interface declares feature " + feature));
    }

    private Json.EvidenceArm evidence(Map<String, Object> args, Caller caller) {
        String endpoint = arg(args, "endpoint");
        if (!ToolCatalogue.ENDPOINTS.contains(endpoint)) {
            throw new IllegalArgumentException("unknown endpoint " + endpoint + "; one of " + ToolCatalogue.ENDPOINTS);
        }
        InterfaceRef ref = interfaceRef(args);
        Json.Evidence evidence = service.evidence(ref, caller)
                .orElseThrow(() -> new IllegalArgumentException("no interface " + ref));
        Json.Arm arm = evidence.arms().stream().filter(a -> a.endpoint().equals(endpoint)).findFirst().orElseThrow();
        return new Json.EvidenceArm(evidence.interfaceId(), evidence.product(), arm, evidence.timings(), evidence.policy());
    }

    private Json.InterfaceResponse interfaceCheck(Map<String, Object> args, Caller caller) {
        InterfaceRef ref = interfaceRef(args);
        return service.interfaceById(ref, caller).orElseThrow(() -> new IllegalArgumentException("no interface " + ref));
    }

    private static McpSchema.CallToolResult plmReply(PlmApi.Reply reply) {
        return reply.ok() ? McpSchema.CallToolResult.builder().addTextContent(reply.body()).build()
                : error("the PLM service answered " + reply.status() + ": " + reply.body());
    }

    /**
     * The parts answer without what no question of the tool needs and every part repeats: the presigned CAD URL, the
     * source file reference (the CAD file again) and the tagging time.
     */
    private McpSchema.CallToolResult parts(Json.PartsResponse answer) {
        ObjectNode node = json.valueToTree(answer);
        node.remove("sparql");
        node.path("parts").forEach(p -> ((ObjectNode) p).remove(List.of("cadUrl", "sourceFileRef", "taggedAt")));
        return McpSchema.CallToolResult.builder().addTextContent(node.toString()).build();
    }

    /** The answer as compact JSON without the federation request texts ({@code sparql}). */
    private McpSchema.CallToolResult text(Object answer) {
        ObjectNode node = json.valueToTree(answer);
        node.remove("sparql");
        return McpSchema.CallToolResult.builder().addTextContent(node.toString()).build();
    }

    private static McpSchema.CallToolResult error(String message) {
        return McpSchema.CallToolResult.builder().isError(true).addTextContent(message).build();
    }

    /**
     * The interface the {@code interface} argument names: an id such as {@code IF-07} with the optional
     * {@code product} argument, or an IRI, which carries its product itself.
     */
    private static InterfaceRef interfaceRef(Map<String, Object> args) {
        String value = arg(args, "interface");
        if (value.startsWith(Atelier.INTERFACE)) return new InterfaceRef(Atelier.nativeId(value), Atelier.productOf(value));
        return new InterfaceRef(value, optional(args, "product"));
    }

    private static String source(Map<String, Object> args) {
        String plm = arg(args, "plm").toLowerCase();
        if (!ToolCatalogue.SOURCES.contains(plm)) throw new IllegalArgumentException("unknown plm " + plm + "; one of " + ToolCatalogue.SOURCES);
        return plm;
    }

    /** A required part id, checked as the REST API checks a part id. */
    private static String item(Map<String, Object> args, String name) {
        return QueryController.root(arg(args, name));
    }

    /** The optional root argument, checked as the REST API checks it. */
    private static String root(Map<String, Object> args) {
        return QueryController.root(optional(args, "root"));
    }

    private static String arg(Map<String, Object> args, String name) {
        return Optional.ofNullable(optional(args, name))
                .orElseThrow(() -> new IllegalArgumentException("argument '" + name + "' is required"));
    }

    private static String optional(Map<String, Object> args, String name) {
        Object value = args.get(name);
        return value == null || value.toString().isBlank() ? null : value.toString().strip();
    }
}
