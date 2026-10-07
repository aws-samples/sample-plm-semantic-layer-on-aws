#!/usr/bin/env python3
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""Calls the query service's MCP endpoint as an MCP client over streamable HTTP, one session per viewer
profile (the x-atelier-profile header, and x-atelier-actor: fixture), and writes tools/list and each tools/call
result to <out dir>/mcp-*.json for check.py: {"isError": bool, "bytes": <text length>, "content": <the tool's JSON, or its text>}.
    uv run --with mcp==2.1.1 mcp_client.py <mcp url> <out dir>"""
import asyncio
import json
import sys

from mcp import ClientSession
from mcp.client.streamable_http import create_mcp_http_client, streamable_http_client

URL, OUT = sys.argv[1], sys.argv[2]
PREFIX = "PREFIX atelier: <https://example.com/atelier/ontology#> "
CALLS = {
    "programme-cleared": [
        ("list-interfaces", "list_interfaces", {}),
        ("interface-check", "interface_check", {"interface": "IF-05", "product": "ornithopter"}),
        ("where-used", "where_used", {"part": "FR-ORN-KEEL-001"}),
        ("impact", "impact_of_change", {"feature": "HL 6180-02"}),
        ("evidence", "evidence", {"interface": "IF-05", "endpoint": "ontop-uk", "product": "ornithopter"}),
        ("products", "products", {}),
        ("ontology", "ontology", {}),
        ("sparql-select", "sparql", {"query": PREFIX + "SELECT ?owner (COUNT(?f) AS ?plugs) WHERE { ?f a atelier:Plug ; atelier:ownedBy ?owner } GROUP BY ?owner ORDER BY ?owner"}),
        ("sparql-limit", "sparql", {"query": "SELECT * WHERE { ?s ?p ?o }"}),
        ("sparql-unknown", "sparql", {"query": PREFIX + "SELECT * WHERE { ?s atelier:weight ?o }"}),
        ("sparql-update", "sparql", {"query": PREFIX + "INSERT DATA { <urn:a> atelier:label \"x\" }"}),
        ("catalogue", "catalogue", {"plm": "uk"}),
        ("export-status-supplier", "export_status", {"part": "KNKL-6130-L"}),
        ("bom", "bom", {"product": "wind-turbine"}),
        ("parts-subtree", "parts", {"product": "wind-turbine", "root": "D-37073"}),
        ("bom-subtree", "bom", {"product": "wind-turbine", "root": "UK-3776"}),
        ("stations", "parts_between_stations", {"product": "ornithopter", "from": "WS 1500", "to": "WS 3600"}),
        ("section-joints", "section_joints", {"product": "ornithopter", "station": "WS 700"}),
        ("variant-diff", "variant_diff", {"product": "steam-engine", "group": "acting", "option": "single-acting"}),
        ("find-parts", "find_parts", {"product": "ornithopter", "query": "both wings"}),
        ("find-parts-de", "find_parts", {"product": "rover", "query": "Getriebemotoren"}),
    ],
    "de-engineer": [("export-status-de", "export_status", {"part": "HMOT-70090"}),
                    ("find-parts-hidden", "find_parts", {"product": "wind-turbine", "query": "blade, pale"})],
    "export-officer": [("export-status-officer", "export_status", {"part": "HMOT-70090"}),
                       ("list-interfaces-officer", "list_interfaces", {})],
}


def write(name, data):
    with open(f"{OUT}/mcp-{name}.json", "w", encoding="utf-8") as f:
        json.dump(data, f, indent=1)


async def session(profile, calls):
    http = create_mcp_http_client(headers={"x-atelier-profile": profile, "x-atelier-actor": "fixture"})
    async with streamable_http_client(URL, http_client=http) as (read, write_stream):
        async with ClientSession(read, write_stream) as s:
            await s.initialize()
            if profile == "programme-cleared":
                tools = await s.list_tools()
                write("tools", [{"name": t.name, "description": t.description, "inputSchema": t.input_schema} for t in tools.tools])
                print(f"  tools/list: {[t.name for t in tools.tools]}")
            for name, tool, args in calls:
                result = await s.call_tool(tool, args)
                text = "".join(c.text for c in result.content if c.type == "text")
                try:
                    content = json.loads(text)
                except ValueError:
                    content = text
                is_error = bool(result.is_error)  # nosemgrep: is-function-without-parentheses -- a pydantic field of the MCP SDK's CallToolResult, not a method
                write(name, {"isError": is_error, "bytes": len(text), "content": content})
                print(f"  tools/call {tool} {args} as {profile}: {'error' if is_error else 'ok'}, {len(text)} B")


async def main():
    for profile, calls in CALLS.items():
        await session(profile, calls)


asyncio.run(main())
