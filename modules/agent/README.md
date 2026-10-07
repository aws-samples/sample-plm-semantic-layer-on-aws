# Atelier agent

The in-app agent of the sample: a Strands agent whose tools are the query service's MCP tools
(`products`, `list_interfaces`, `parts`, `interface_check`, `where_used`, `impact_of_change`, `export_status`,
`bom`, `bom_where_used`, `path_between`, `flow_path`, `variant_diff`, `external_references`, `equivalent_parts`, `find_term`, `find_parts`, `suppliers`, `parts_between_stations`, `section_joints`, `evidence`, `ontology`, `sparql`, `preview_correction`, `catalogue`, `sql`, discovered at each run) plus the ten frontend
tools of the contract: `highlight_interfaces`, `open_evidence` and `render_table` for the
results, and `highlight_parts`, `isolate_parts`, `zoom_to_part`, `clear_view`, `open_subtree`,
`open_product` and `open_screen` for the 3D viewer and what is loaded. The prompt
orders them: named tools first; for native PLM data, `catalogue(plm)` then one `SELECT` with
`sql`; `sparql` last. It is served by FastAPI as AG-UI over SSE
(`ag-ui-strands`) and runs in a Fargate task behind the CloudFront `/agent/*` behaviour.
CloudFront forwards the path unchanged, so the routes carry the `/agent` prefix.

| Endpoint | Purpose |
|---|---|
| `POST /agent/invocations` | AG-UI `RunAgentInput` in, SSE stream of AG-UI events out |
| `GET /agent/health` | `{"status":"ok"}`; the ALB target group health path |

## Layout

```
server.py                  FastAPI app: /agent/invocations, /agent/health
atelier_agent/runner.py        one run: MCP client, template agent, ag-ui-strands stream, atelier.turn
atelier_agent/mcp_tools.py     MCPClient to the query service with x-origin-verify + x-atelier-profile
atelier_agent/frontend_tools.py the ten frontend tools: results, viewer and loading (return None)
atelier_agent/product.py       the product on screen (forwardedProps.product) and the tools defaulting to it
atelier_agent/selection.py     what is selected on screen (forwardedProps.selection)
atelier_agent/prompt.py        system prompt (product and selection on screen, templates first, exact ids, redactions, screen and viewer tools)
atelier_agent/turn_event.py    the atelier.turn CUSTOM event from Strands run metrics
tests/                     pytest suite and the fake query-service MCP server
eval/                      the show eval: "show me X" questions asked of the real model over the fixture stack
```

## Environment

| Variable | Meaning |
|---|---|
| `MODEL_ID` | Bedrock model id or inference profile, e.g. `global.anthropic.claude-sonnet-4-5-20250929-v1:0`; `infra/scripts/deploy.sh` sets it from `BEDROCK_MODEL_ID` |
| `MCP_URL` | the query service's MCP endpoint, e.g. `https://<api>/api/query/mcp` |
| `ORIGIN_SECRET` | value sent as `x-origin-verify`; or |
| `ORIGIN_SECRET_ARN` | Secrets Manager secret holding it (read once at first use) |
| `PRICE_PER_1K_INPUT`, `PRICE_PER_1K_OUTPUT` | USD per 1K tokens for `estimatedUsd` (default 0) |
| `AWS_REGION` | region of the Bedrock and Secrets Manager calls |
| `PORT` | listening port when run as `python server.py` (default 8080) |

## How the profile flows

The browser sends its viewer profile in the `x-atelier-profile` header of `POST /agent/invocations`
(absent: `unknown`). The header value is used twice in that run and nowhere else:

1. the `MCPClient` of the run is created with headers `x-origin-verify` and `x-atelier-profile`,
   so every MCP call of the run reaches the query service filtered for that profile;
2. the run context names the profile so redactions are reported as "not visible to your
   profile (`<profile>`)".

The agent never changes the profile. A new MCP client and Strands agent are built for each
run; the conversation history comes back from the browser in `RunAgentInput.messages`.

## How the product flows

The browser sends the product on screen as `forwardedProps.product` of the run input,
`{ "key": "ornithopter", "name": "Ornithopter ground demonstrator (Paris Manuscript B, f. 74v)" }`, the key being one `GET /api/query/products` lists
(`atelier_agent/product.py`). The run uses it twice:

1. the run context states "The person is looking at product `ornithopter` (Ornithopter ground demonstrator (Paris Manuscript B, f. 74v))", so the agent
   names the product when an answer depends on it;
2. every MCP tool whose input schema has a `product` property (all but `products`,
   `export_status`, `ontology`, `catalogue` and `sql`) is wrapped so that a call without a product
   argument is sent with the product on screen; a key the model passes itself wins. `bom`,
   `bom_where_used`, `external_references`, `equivalent_parts`, `find_parts` and `suppliers` require a product, so on screen they answer for the product shown.

A run without a product, or with a malformed one, names no product and leaves the tools as they
are: they answer across every product.

## How the selection flows

Every run input carries what is on screen and selected as `forwardedProps.selection`:
`{ "product": "wind-turbine", "root": "D-37073", "part": { "id": "D-37011", "plm": "de", "name": "Getriebegehäuse Planetenstufe" }, "interface": null }`,
`root` present in subtree mode only, `part` the part clicked in the viewer or the bill-of-materials tree, `interface` the
interface selected on the Interface check screen (`atelier_agent/selection.py`). Ids, keys and the
PLM code are checked against the forms the sources use and the part name is stripped of markup
before the run context names them: "The person has selected part `D-37011` (Getriebegehäuse
Planetenstufe, DE PLM)". The prompt makes "this part", "this interface" and "here" resolve to the
selection and, with nothing selected, has the agent ask which one is meant.

## The run context and the prompt cache

The system prompt (`atelier_agent/prompt.py`) and the tool definitions are the same bytes for every user,
product and turn: the wrapped tools keep the query service's descriptions. What differs per run is the run
context, three `RunAgentInput.context` entries the runner sets in place of any the browser sends (viewer
profile, product, selection), which `ag-ui-strands` writes at the head of the latest question for each model
call ("Context provided by the application: ...").

The Bedrock model runs with `CacheConfig(strategy="auto", tools_ttl=True)`: three cache points of the four
Bedrock allows per request, after the tool definitions, after the system prompt, and after the latest user
message (the question, then each tool result). The prefix is about 21,000 tokens, above Claude Sonnet 5's
minimum of 1,024 per cache point; entries live 5 minutes from their last read. The first call after a cold
cache writes the prefix; every later call, and every later step of a turn, reads it, so a turn of two
tool steps sends a handful of uncached input tokens. Through an `eu.` inference profile a request may land in
another region of the geography and write the prefix again there.

## The viewer tools

`highlight_parts`, `isolate_parts` and `zoom_to_part` resolve the ids they name against the parts of the
product on screen (read once per product through the `parts` tool, `atelier_agent/part_ids.py`): an id
matches exactly, or else by its normalised form (upper case, without hyphens or spaces) when exactly one
part has it, so `UK3501` shows `UK-3501`. The tool answers an acknowledgement naming the ids it read
otherwise and the ids no part has ("call again with the exact ids a tool returned"); the browser resolves
the same way (`modules/web/src/viewer/ids.ts`) and leaves an unresolved id out.

The prompt names the seven viewer and loading tools in one sentence each and sets when to call
them. A request to show, display, highlight, isolate or locate parts is a viewer request:
`find_parts` with the words naming them, expanded by the model into the four languages and their
synonyms because the matching is lexical (one call per group). When the request names one thing and
one group is an assembly that is that thing ("show me the gearbox"), `open_subtree` with that
assembly; for a group of parts or several things (the O-rings, the six wheels, both wings, the
gearbox and the generator), `isolate_parts` with the parts the user means and a caption naming each
group, or `highlight_parts` when the user asks for context or where they are; a family of parts is its kinds as alternatives (fasteners: screw, bolt,
nut, washer, rivet, pin); "class" is a seat or cabin class when the subject is seats or the cabin,
a purchased item's class only when it is purchasing, stock or equivalence; an ambiguous request
gets a one-line question; the answer is short, with no table unless asked. After naming parts in
another answer, `highlight_parts`; for a question about what one assembly holds, `open_subtree`
with its id; for a cross-site answer (a bill-of-materials roll-up, where a part is used),
`isolate_parts` with the parts of the answer and the far side as `context_ids`; after
`impact_of_change`, `isolate_parts` with the impacted parts and the changed part as context.

## The `atelier.turn` event

`ag-ui-strands` has no hook for adding an event at the end of a run, so `runner.run` wraps its
stream: when `RUN_FINISHED` arrives it first yields a `CUSTOM` event named `atelier.turn`, then
`RUN_FINISHED`. Its value is

```json
{ "tools": [{ "name": "interface_check", "ms": 250 }],
  "usage": { "inputTokens": 12, "outputTokens": 300, "cacheReadInputTokens": 42000, "cacheWriteInputTokens": 2600 },
  "estimatedUsd": 0.026886 }
```

built from the Strands `EventLoopMetrics` of the agent that ran (`tool_metrics[*].total_time`,
`accumulated_usage`) and the two price variables. `inputTokens` are the input tokens neither read from
nor written to the cache; cache writes are priced at 1.25 times and cache reads at 0.1 times
`PRICE_PER_1K_INPUT`, as Bedrock bills them for Claude with the 5-minute TTL. `ag-ui-strands` creates the Strands agent it
actually runs per thread from the template agent; the `agents_by_thread` dict passed to its
constructor is where the runner reads that agent back.

## Run locally

```bash
python3.12 -m venv .venv && .venv/bin/pip install -r requirements-dev.txt

# a fake query service serving list_interfaces / interface_check / catalogue / sql from tests/fixtures
.venv/bin/python tests/fake_mcp_server.py 8765 &

MODEL_ID=global.anthropic.claude-sonnet-4-5-20250929-v1:0 \
MCP_URL=http://127.0.0.1:8765/query/mcp ORIGIN_SECRET=dev \
AWS_REGION=us-west-2 .venv/bin/python server.py

curl -N -X POST localhost:8080/agent/invocations -H 'content-type: application/json' \
  -H 'x-atelier-profile: engineer' -d '{"threadId":"t1","runId":"r1","messages":[{"id":"m1","role":"user",
  "content":"Which interfaces fail and why?"}],"tools":[],"context":[],"state":{},"forwardedProps":{}}'
```

Against the deployed query service, set `MCP_URL` to `https://<api>/api/query/mcp` and
`ORIGIN_SECRET_ARN` to the origin secret's ARN.

## Tests

```bash
.venv/bin/pytest
```

The suite starts the fake MCP server in a subprocess and covers: tool discovery, forwarding
of `x-atelier-profile` and `x-origin-verify` on every MCP call, the origin secret read from env
or Secrets Manager, the frontend tools' parameter names, `atelier.turn` construction, and the
`/agent/invocations` stream (event order, `atelier.turn` before `RUN_FINISHED`, header profile reaching
the MCP client and the prompt, `MCP_UNAVAILABLE` run error) with the model layer stubbed.
Nothing in the suite calls Bedrock; the show eval below does.

## The show eval

`eval/show_eval.py` asks the real model the "show me X" questions of `eval/show_questions.json`
(public products only) and grades the viewer call each run makes: the expected tool, every
required part id and no forbidden one, no table, a caption naming each group asked for, or for an
ambiguous request a question and no viewer call. It prints the pass rate, the tool calls, the tokens
per turn (uncached input, read from and written to the prompt cache, output), the time to the first
streamed token and the turn duration. It calls Bedrock on every run, so it runs on demand, never in CI:

```bash
eval/stack.sh up      # the fixture stack on ports 19478 (link store) and 19480 (query service)
AWS_PROFILE=<profile> AWS_REGION=eu-west-1 AWS_DEFAULT_REGION=eu-west-1 MODEL_ID=eu.anthropic.claude-sonnet-5 \
  .venv/bin/python eval/show_eval.py --runs 3
eval/stack.sh down
```

`--only <id>...` runs some questions, `--out <file>` keeps every run's calls and answer.

## Container

```bash
finch build -t atelieragent-local -f modules/agent/Dockerfile .   # from the repository root
finch run --rm -p 8080:8080 -e MODEL_ID=... -e MCP_URL=... -e ORIGIN_SECRET=... \
  -e AWS_REGION=us-west-2 atelieragent-local
curl localhost:8080/agent/health
```

The Dockerfile is plain (kaniko-compatible), runs as a non-root user and builds for arm64 or
amd64 from the same file (kaniko: `--custom-platform=linux/arm64`).
