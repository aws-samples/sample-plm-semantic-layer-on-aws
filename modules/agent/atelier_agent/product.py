# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""The product the person is looking at, and the tools that answer for it by default.

The browser sends the product under `forwardedProps.product` of the AG-UI run input as
`{"key": ..., "name": ...}`, the key being one `GET /query/products` lists. The run names it in
the prompt and makes it the default `product` argument of every MCP tool whose input schema has
one (the model may still pass another key); a run without a product leaves the tools as they are.
"""

import re
from collections.abc import Sequence
from dataclasses import dataclass
from typing import Any

from ag_ui.core import RunAgentInput
from strands.types.tools import AgentTool, ToolGenerator, ToolSpec, ToolUse
from typing_extensions import override

# Product keys as the core database spells them; anything else is no product.
KEY_PATTERN = re.compile(r"^[A-Za-z0-9._-]{1,32}$")
# The product name is the browser's text, read into the prompt: this many characters of it at most.
NAME_MAX = 200


@dataclass(frozen=True)
class Product:
    key: str
    name: str

    def context(self) -> str:
        """The sentence the prompt carries about the product on screen."""
        return f"The person is looking at product `{self.key}` ({self.name})."


def product_of(run_input: RunAgentInput) -> Product | None:
    """The product of a run, or None when the body carries none or a malformed one."""
    props = run_input.forwarded_props
    value = props.get("product") if isinstance(props, dict) else None
    if not isinstance(value, dict):
        return None
    key = value.get("key")
    if not isinstance(key, str) or not KEY_PATTERN.match(key):
        return None
    name = value.get("name")
    name = name.strip()[:NAME_MAX] if isinstance(name, str) else ""
    return Product(key=key, name=name or key)


def accepts_product(tool: AgentTool) -> bool:
    """Whether a tool's input schema declares a `product` property."""
    schema = tool.tool_spec.get("inputSchema", {}).get("json", {})
    return isinstance(schema, dict) and "product" in schema.get("properties", {})


class ProductDefault(AgentTool):
    """A tool whose `product` argument is the product on screen unless the model names another."""

    def __init__(self, inner: AgentTool, product: Product) -> None:
        super().__init__()
        self.inner = inner
        self.product = product

    @property
    def tool_name(self) -> str:
        return self.inner.tool_name

    @property
    def tool_spec(self) -> ToolSpec:
        # Unchanged, so the tool definitions stay one cached prefix for every product; the run context names the default.
        return self.inner.tool_spec

    @property
    def tool_type(self) -> str:
        return self.inner.tool_type

    @override
    async def stream(self, tool_use: ToolUse, invocation_state: dict[str, Any], **kwargs: Any) -> ToolGenerator:
        arguments = dict(tool_use.get("input") or {})
        if not str(arguments.get("product") or "").strip():
            arguments["product"] = self.product.key
            tool_use = {**tool_use, "input": arguments}
        async for event in self.inner.stream(tool_use, invocation_state, **kwargs):
            yield event


def with_product_default(tools: Sequence[AgentTool], product: Product | None) -> list[AgentTool]:
    """The tools, those taking a `product` argument wrapped to default to the product on screen."""
    if product is None:
        return list(tools)
    return [ProductDefault(tool, product) if accepts_product(tool) else tool for tool in tools]
