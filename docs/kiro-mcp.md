# Using the semantic layer from a desktop agent

The query service exposes its question templates, the catalogue-grounded SQL
and the guarded SPARQL as an MCP (Model Context Protocol) server over
streamable HTTP. Any MCP client can use it; this page shows Kiro and Claude
Code. The in-app Ask panel uses the same server.

## Endpoint and headers

| Item | Value |
|---|---|
| URL | `<api endpoint>/api/query/mcp`, where `<api endpoint>` is the API Gateway URL of the deployment |
| `x-origin-verify` | the origin secret; the API Gateway authorizer rejects requests without it |
| `x-atelier-profile` | one of `fr-engineer`, `de-engineer`, `uk-engineer`, `es-engineer`, `programme-cleared`, `export-officer`; absent means `unknown`, which sees only unrestricted parts |

The profile header is the export-control identity for every tool call, exactly
as for the screens: a desktop agent sees what that profile sees, and redactions
come back as "not visible to your profile".

Both values come from the deploy. `infra/scripts/deploy.sh` writes
`cdk-outputs-prod.json`; read the endpoint and the secret from it with the
credentials of the deployment account, and never paste the secret into a file
that is committed or shared:

```sh
jq -r 'first(.[] | to_entries[] | select(.key | endswith("ApiEndpoint")) | .value)' cdk-outputs-prod.json
aws secretsmanager get-secret-value --profile "$DEPLOY_PROFILE" --region "$DEPLOY_REGION" \
  --secret-id "$(jq -r 'first(.[] | to_entries[] | select(.key | endswith("OriginSecretArn")) | .value)' cdk-outputs-prod.json)" \
  --query SecretString --output text
```

## Kiro

`~/.kiro/settings/mcp.json` (user level) or `.kiro/settings/mcp.json` in a
workspace:

```json
{
  "mcpServers": {
    "atelier-semantic-layer": {
      "url": "<api endpoint>/api/query/mcp",
      "headers": {
        "x-origin-verify": "<origin secret>",
        "x-atelier-profile": "programme-cleared"
      },
      "autoApprove": ["products", "list_interfaces", "parts", "interface_check", "where_used", "impact_of_change", "export_status", "evidence", "catalogue", "ontology"]
    }
  }
}
```

`sql` and `sparql` are left to manual approval so the query is shown before it
runs.

## Claude Code

```sh
claude mcp add --transport http atelier-semantic-layer \
  <api endpoint>/api/query/mcp \
  --header "x-origin-verify: <origin secret>" \
  --header "x-atelier-profile: programme-cleared"
```

## Questions that work well

- "Which interfaces fail for my profile and why?" (`list_interfaces`, then `interface_check`)
- "Where is FR-ORN-KEEL-001 used?" (`where_used`)
- "How many parts does each site contribute to the wind turbine and how heavy are they?" (`bom`)
- "Which assemblies of the wind turbine use D-37002?" (`bom_where_used`)
- "Which parts does the wind turbine's hub control hold, across the sites?" (`parts` with `root` UK-3776)
- "Which references of the cart point at a part that does not exist?" (`external_references`)
- "What would a change to WURZ-R-61080 impact?" (`impact_of_change`)
- "Is HMOT-70090 releasable to me?" (`export_status`; switch the profile header to `export-officer` and ask again)
- "Which parts of the rover are the same screw?", "Which O-rings of the wind turbine are one item?" (`equivalent_parts`)
- "Which parts are a Zahnrad, in every product?" (`find_term`)
- "Show both wings of the ornithopter" (`find_parts`)
- "Which rover parts are single-source?" (`suppliers`)
- "How many UK fasteners are stored per unit of measure?" (`catalogue("uk")`, then `sql`)
- "Which UK harness connectors have the most pins?" (`catalogue("uk")`, then `sql` over `harness_connector`)
- "Which connector types are used on parts of more than one PLM, and how many plugs of each?" (`ontology`, then `sparql` over the merged graph; no template or per-PLM SQL answers it)
- "Show me the SQL behind the UK arm of IF-05" (`evidence`)

## Tools

| Tool | What it answers |
|---|---|
| `products` | the products the parts belong to, with the number of parts the profile may see; their keys are the `product` argument of the other tools |
| `list_interfaces` | every interface visible to the profile with status and failing rules; of one product with `product`; with `root` (an item id of that product), the interfaces with a side in that assembly's subtree across the sites, far-side parts marked `context` |
| `parts` | the parts of one product (`product`, required) with their attributes and findings; with `root`, the parts of that assembly's subtree across the sites |
| `interface_check` | one interface (`interface`: its id, or its IRI) with its features, rule results and provenance; `product` names the product the id belongs to |
| `where_used` | the interfaces a part sits on, its mates, feature counts per kind; of one product with `product` |
| `impact_of_change` | interfaces and mated features a change to a part or a feature touches; of one product with `product` |
| `export_status` | jurisdiction, releasability, who tagged it, visibility to the profile, CAD availability |
| `bom` | the whole bill of materials of one product (`product`, required), which no PLM holds: the site kits and their trees with quantities, occurrences and masses, `depth` levels below the root (default 3), and the roll-up per site and in total over the whole tree; with `root`, the tree under that item, crossing to another site through its external references |
| `bom_where_used` | the assemblies a part is built into in one product's bill of materials (`part` and `product`, both required), with quantities and occurrences; `where_used` answers the interfaces instead |
| `path_between` | the shortest interface paths between two parts of one product (`product`, `from`, `to`, all required), each joint with its status |
| `flow_path` | the walk along the functional edges from one part (`product`, `from`, required; `flow`, `direction` optional), with each step's flow and joint, the gear ratio and the rated-speed checks |
| `variant_diff` | one variant option against its group's default (`product`, `group`, `option`, all required), port by port, with both configurations' tallies, occurrences and mass |
| `external_references` | the references one product's parts make to other sites' parts by URN (`product`, required): target, expected and current revision, status `ok`, `danglingReference`, `staleRevision` or `not-evaluable`; with `root`, of that assembly's subtree |
| `equivalent_parts` | the purchased parts of one product (`product`, required) that are one item under different part numbers, units and words, by the item classes of the ontology (fastener: standard and nominal size within 0.1 mm; O-ring: size within 0.01 mm, stated or from its AS568 or ISO 3601-1 size, and compound; placard: legend, size within 0.5 mm, face material and adhesive; container: IATA type, dimensions within 1 mm and shell material; tyre, wheel and brake: dimensions, ply rating, rotors), with the shelf life and the stocking line; each group `confirmed` by a user or a proposal |
| `find_term` | a word in English, German, French or Spanish (`term`, required) resolved to the products' glossary, with the parts of every site whose names hold it, each with its native and English names |
| `find_parts` | the parts of one product (`product` and `query`, required) that the words name, by native or English name, glossary term or part type, alternatives separated by commas, grouped by the assembly they belong to, each group saying why it matched (field, label and language); with `root`, of that assembly's subtree |
| `suppliers` | the suppliers of one product's parts (`product`, required) per site with lead times, the single-source parts and the parts whose preferred offers disagree on the lead time |
| `parts_between_stations` | the parts of one product between two stations (`product`, `from`, `to`, required; `side` optional), with their spans and the stations they cover |
| `section_joints` | the sections of one product with their owners and the parts crossing each joint (`product`, required; `station` optional) |
| `evidence` | the request, generated SQL, triples and native tables behind one arm (`ontop-fr`, `ontop-de`, `ontop-uk`, `ontop-es`, `ontop-core` or `neptune`) of an answer; `product` as for `interface_check` |
| `preview_correction` | what the rules would say if a site released a correction (`product` and `cells`, each `{ plm, table, key, column, value }` in the site's own tables and words): the results fixed, still failing and newly failing; it writes nothing |
| `catalogue` | the ORM-generated catalogue of one source (`fr`, `de`, `uk`, `es` or `core`): tables, columns, units, descriptions, ontology terms |
| `sql` | one `SELECT` over a PLM's native tables, validated against the catalogue, rows filtered by the profile, `LIMIT 200` |
| `ontology` | the classes, properties, named graphs, rules and example patterns the `sparql` tool is checked against; read it first |
| `sparql` | one read query over the profile's merged graph (of one product with `product`), predicates checked against the ontology, `LIMIT 200` |

Interface ids are unique within a product, so `interface_check` and `evidence`
take the product key beside the id: `interface_check(interface="IF-13",
product="ornithopter")`. Without `product`, an id that only one product has
resolves on its own; an id several products have comes back as an error that
lists the candidate products. The in-app agent passes the product on screen as
the `product` argument of every tool that has one; a desktop agent names it
itself.

The same list, with each tool's full description, is served at
`<api endpoint>/api/query/mcp/tools` with the same headers.
