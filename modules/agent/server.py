# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""Atelier agent: FastAPI app serving AG-UI over SSE under the `/agent` prefix.

CloudFront forwards the `/agent/*` path unchanged, so the container serves
POST /agent/invocations (an AG-UI RunAgentInput in, the run's events streamed out; the viewer
profile comes from the request's `x-atelier-profile` header, default `unknown`, and is forwarded
to every MCP call of the run) and GET /agent/health (the ALB target group health path).
"""

import hmac
import os
import re

import uvicorn
from ag_ui.core import RunAgentInput
from ag_ui.encoder import EventEncoder
from fastapi import APIRouter, FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse, StreamingResponse

from atelier_agent.mcp_tools import DEFAULT_PROFILE, ORIGIN_HEADER, PROFILE_HEADER, origin_secret
from atelier_agent.runner import run

router = APIRouter(prefix="/agent")

# Profile names as ontology/policy.json spells them; anything else is the empty profile.
PROFILE_PATTERN = re.compile(r"^[a-z][a-z0-9-]{0,31}$")


def profile_of(request: Request) -> str:
    value = request.headers.get(PROFILE_HEADER, DEFAULT_PROFILE).strip()
    return value if PROFILE_PATTERN.match(value) else DEFAULT_PROFILE


def require_origin(request: Request) -> None:
    """CloudFront adds the origin secret on the way to the agent; any other caller reaching the
    internal ALB is refused. A run without a configured secret (local) accepts everything."""
    expected = origin_secret()
    if expected and not hmac.compare_digest(request.headers.get(ORIGIN_HEADER, ""), expected):
        raise HTTPException(status_code=403, detail="origin not verified")


@router.post("/invocations")
async def invocations(run_input: RunAgentInput, request: Request):
    require_origin(request)
    profile = profile_of(request)
    encoder = EventEncoder(accept=request.headers.get("accept"))

    async def events():
        async for event in run(run_input, profile):
            yield encoder.encode(event)

    return StreamingResponse(events(), media_type=encoder.get_content_type())


@router.get("/health")
async def health():
    return JSONResponse({"status": "ok"})


app = FastAPI(title="atelier-agent")
app.include_router(router)


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=int(os.environ.get("PORT", "8080")))  # nosec B104 - reached only through the load balancer of its Fargate task
