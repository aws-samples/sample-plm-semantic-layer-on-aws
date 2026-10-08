# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""The query service's MCP tools, reached over streamable HTTP.

Every MCP request carries the origin secret (API Gateway checks it) and the caller's viewer
profile, so the agent sees exactly what the browser sees.
"""

import os
from functools import lru_cache

import boto3
from strands.tools.mcp import MCPClient

PROFILE_HEADER = "x-atelier-profile"
ORIGIN_HEADER = "x-origin-verify"
# Provenance tells an agent's call from a person's: the query service echoes this actor.
ACTOR_HEADER = "x-atelier-actor"
ACTOR = "agent"
DEFAULT_PROFILE = "unknown"


@lru_cache(maxsize=1)
def origin_secret() -> str:
    """The origin secret: env ORIGIN_SECRET, else the Secrets Manager secret at ORIGIN_SECRET_ARN,
    else empty (local runs against a server that does not check it)."""
    value = os.environ.get("ORIGIN_SECRET")
    if value:
        return value
    arn = os.environ.get("ORIGIN_SECRET_ARN")
    if not arn:
        return ""
    return boto3.client("secretsmanager").get_secret_value(SecretId=arn)["SecretString"]


def mcp_client(profile: str) -> MCPClient:
    """An MCP client for the query service (env MCP_URL) sending the origin secret, the profile and the actor."""
    return MCPClient(
        url=os.environ["MCP_URL"],
        headers={ORIGIN_HEADER: origin_secret(), PROFILE_HEADER: profile, ACTOR_HEADER: ACTOR},
    )
