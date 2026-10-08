# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""A fake of the query service's MCP server for local runs and tests.

Serves `products`, `list_interfaces`, `interface_check`, `catalogue` and `sql` from tests/fixtures/*.json
over streamable HTTP at /query/mcp. Each answer carries, under `_headers`, the `x-atelier-profile`
and `x-origin-verify` values the request arrived with, and `list_interfaces` and `sql` echo their
arguments under `_arguments`, so a test can assert what the agent forwarded.

Run: python tests/fake_mcp_server.py <port>
"""

import json
import sys
from pathlib import Path

from mcp.server.mcpserver import Context, MCPServer

FIXTURES = Path(__file__).parent / "fixtures"
ECHOED_HEADERS = ("x-atelier-profile", "x-origin-verify", "x-atelier-actor")

server = MCPServer("atelier-fake-query-service")

PRODUCTS = [
    {"key": "ornithopter", "name": "Ornithopter ground demonstrator (Paris Manuscript B, f. 74v)",
     "frame": "x aft from the frame nose, y to the pilot's right, z up with z = 0 on the keel beam axis; mm", "partCount": 76},
    {"key": "aerial-screw", "name": "Aerial screw working replica (Paris Manuscript B, f. 83v)",
     "frame": "z up on the mast axis with z = 0 on the floor, x towards the hydraulic skid, y to its left; mm", "partCount": 36},
]


def _answer(fixture: str, ctx: Context) -> dict:
    payload = json.loads((FIXTURES / fixture).read_text())
    headers = ctx.headers or {}
    payload["_headers"] = {name: headers.get(name) for name in ECHOED_HEADERS}
    return payload


@server.tool()
def products(ctx: Context) -> str:
    """The products the parts are assembled into: key, name, frame, partCount visible to the caller's profile."""
    payload = _answer("list_interfaces.json", ctx)
    return json.dumps({"products": PRODUCTS,
                       "provenance": payload.get("provenance"), "policy": payload.get("policy"), "_headers": payload["_headers"]})


@server.tool()
def list_interfaces(ctx: Context, product: str | None = None) -> str:
    """Every interface visible to the caller's profile: id, label, parts (plm, id), status, rules failing.
    Optional argument: product, the key of one product as the products tool lists it."""
    payload = _answer("list_interfaces.json", ctx)
    payload["_arguments"] = {"product": product}
    return json.dumps(payload)


@server.tool()
def interface_check(interface: str, ctx: Context) -> str:
    """The Interface JSON of one interface: parts, features, violations, provenance, sparql."""
    payload = _answer("interface_check.json", ctx)
    if payload["interface"]["id"] != interface:
        return json.dumps({"error": f"unknown interface {interface}", "_headers": payload["_headers"]})
    return json.dumps(payload)


@server.tool()
def catalogue(plm: str, ctx: Context) -> str:
    """The ORM catalogue of one PLM: its tables, key columns and columns with types and units."""
    payload = _answer("catalogue.json", ctx)
    payload["plm"] = plm
    return json.dumps(payload)


@server.tool()
def sql(plm: str, query: str, purpose: str, ctx: Context) -> str:
    """Run one SELECT against a PLM's catalogue tables; returns the SQL that ran, rows, rowCount, ms."""
    payload = _answer("sql.json", ctx)
    payload["_arguments"] = {"plm": plm, "query": query, "purpose": purpose}
    return json.dumps(payload)


if __name__ == "__main__":
    server.run(
        transport="streamable-http",
        host="127.0.0.1",
        port=int(sys.argv[1]),
        streamable_http_path="/query/mcp",
        stateless_http=True,
    )
