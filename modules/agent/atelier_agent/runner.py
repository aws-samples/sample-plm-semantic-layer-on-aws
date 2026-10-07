# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""One AG-UI run over the semantic layer.

Per run: an MCP client to the query service carrying the caller's profile, a Strands agent
holding those tools plus the frontend tools (the tools that take a product argument defaulting to
the product the run input names) and the system prompt every run shares, the run context (profile,
product, selection) that ag-ui-strands writes at the head of the question, the ag-ui-strands wrapper
streaming AG-UI events, and the `atelier.turn` CUSTOM event inserted just before RUN_FINISHED.
"""

import asyncio
import json
import os
import uuid
from collections.abc import AsyncIterator
from functools import lru_cache

from ag_ui.core import BaseEvent, Context, EventType, RunAgentInput, RunErrorEvent, RunStartedEvent
from ag_ui_strands import StrandsAgent
from strands import Agent
from strands.models import BedrockModel, CacheConfig
from strands.tools.mcp import MCPClient

from .frontend_tools import (
    clear_view,
    highlight_interfaces,
    highlight_parts,
    isolate_parts,
    open_evidence,
    open_product,
    open_screen,
    open_subtree,
    render_table,
    zoom_to_part,
)
from .mcp_tools import mcp_client
from .part_ids import RUN_PART_IDS, PartIds
from .product import Product, product_of, with_product_default
from .prompt import run_context, system_prompt
from .selection import Selection, selection_of
from .turn_event import turn_event

FRONTEND_TOOLS = [
    highlight_interfaces,
    open_evidence,
    render_table,
    highlight_parts,
    isolate_parts,
    zoom_to_part,
    clear_view,
    open_subtree,
    open_product,
    open_screen,
]


@lru_cache(maxsize=1)
def model() -> BedrockModel:
    """The model, from env MODEL_ID (a Bedrock model id or inference profile), with prompt caching.

    Three cache points, of the four Bedrock allows: after the tool definitions, after the system prompt (both the
    same bytes for every run) and after the latest user message (the question, then each tool result), so every
    step of a turn reads the steps before it from the cache.
    """
    # Current Claude models on Bedrock reject an explicit temperature; consistency comes from the
    # prompt and the tools' exact answers.
    return BedrockModel(model_id=os.environ["MODEL_ID"], max_tokens=16384, cache_config=CacheConfig(strategy="auto", tools_ttl=True))


def build_agent(tools: list) -> Agent:
    """The template agent ag-ui-strands copies per thread: model, the system prompt shared by every run, tools."""
    return Agent(model=model(), system_prompt=system_prompt(), tools=tools, callback_handler=None)


def with_run_context(run_input: RunAgentInput, profile: str, product: Product | None, selection: Selection | None) -> RunAgentInput:
    """The run input carrying the run's context (profile, product, selection) in place of any the browser sent.

    ag-ui-strands writes it at the head of the latest question for each model call, after the cached prefix.
    """
    entries = [Context(**entry) for entry in run_context(profile, product, selection)]
    return run_input.model_copy(update={"context": entries})


def _part_ids(client: MCPClient, product: str) -> set[str] | None:
    """The ids of the product's parts the run's profile may see, from the query service's parts tool; None when it fails."""
    result = client.call_tool_sync(str(uuid.uuid4()), "parts", {"product": product})
    if result.get("status") != "success":
        return None
    answer = json.loads(result["content"][0]["text"])
    return {p["id"] for p in answer.get("parts", []) if isinstance(p, dict) and "id" in p}


def _start_and_list(client: MCPClient) -> list:
    """Start the client and list its tools; on failure the client is left stopped."""
    client.start()
    try:
        return list(client.list_tools_sync())
    except Exception:
        client.stop(None, None, None)
        raise


async def run(run_input: RunAgentInput, profile: str) -> AsyncIterator[BaseEvent]:
    """Stream the AG-UI events of one run made on behalf of the caller's profile.

    ag-ui-strands offers no hook for adding an event at the end of a run, so its stream is
    wrapped: RUN_FINISHED is held, `atelier.turn` is yielded first, then RUN_FINISHED. It also
    builds the Strands agent that actually runs per thread from the template agent; the
    `agents_by_thread` dict handed to it is where that agent, and so this run's
    `event_loop_metrics`, is read back.
    """
    client = mcp_client(profile)
    try:
        mcp_tools = await asyncio.to_thread(_start_and_list, client)
    except Exception as exc:
        yield RunStartedEvent(type=EventType.RUN_STARTED, thread_id=run_input.thread_id, run_id=run_input.run_id)
        yield RunErrorEvent(
            type=EventType.RUN_ERROR, message=f"Query service MCP unavailable: {exc}", code="MCP_UNAVAILABLE"
        )
        return
    try:
        product = product_of(run_input)
        RUN_PART_IDS.set(PartIds(lambda key: _part_ids(client, key), product.key if product else None))
        agents_by_thread: dict[str, Agent] = {}
        agui = StrandsAgent(
            agent=build_agent([*with_product_default(mcp_tools, product), *FRONTEND_TOOLS]),
            name="atelier_agent",
            description="Answers questions on the Atelier semantic layer through the query service's MCP tools.",
            agents_by_thread=agents_by_thread,
        )
        async for event in agui.run(with_run_context(run_input, profile, product, selection_of(run_input))):
            if event.type == EventType.RUN_FINISHED:
                yield turn_event(agents_by_thread[run_input.thread_id or "default"].event_loop_metrics)
            yield event
    finally:
        await asyncio.to_thread(client.stop, None, None, None)
