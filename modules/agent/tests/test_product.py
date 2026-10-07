# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""The product on screen: read from the run input, named in the prompt, defaulted into the tools that take it."""

import asyncio
import json

from ag_ui.core import RunAgentInput
from strands.types.tools import AgentTool

from atelier_agent.mcp_tools import mcp_client
from atelier_agent.product import NAME_MAX, Product, ProductDefault, accepts_product, product_of, with_product_default
from atelier_agent.prompt import run_context, system_prompt

ORNITHOPTER = Product(key="ornithopter", name="Ornithopter ground demonstrator (Paris Manuscript B, f. 74v)")


def _run_input(forwarded_props) -> RunAgentInput:
    return RunAgentInput.model_validate(
        {"threadId": "t", "runId": "r", "messages": [], "tools": [], "context": [], "state": {}, "forwardedProps": forwarded_props}
    )


class Recording(AgentTool):
    """A tool that records the input it was streamed with."""

    def __init__(self, name: str, properties: dict):
        super().__init__()
        self.name = name
        self.properties = properties
        self.inputs: list[dict] = []

    @property
    def tool_name(self) -> str:
        return self.name

    @property
    def tool_spec(self):
        return {"name": self.name, "description": "Lists.", "inputSchema": {"json": {"type": "object", "properties": self.properties}}}

    @property
    def tool_type(self) -> str:
        return "python"

    async def stream(self, tool_use, invocation_state, **kwargs):
        self.inputs.append(tool_use["input"])
        yield {"toolUseId": tool_use["toolUseId"], "status": "success", "content": []}


def _collect(stream):
    async def run():
        return [event async for event in stream]

    return asyncio.run(run())


def test_product_is_read_from_forwarded_props_and_malformed_values_are_no_product():
    assert product_of(_run_input({"product": {"key": ORNITHOPTER.key, "name": ORNITHOPTER.name}})) == ORNITHOPTER
    key_only = product_of(_run_input({"product": {"key": "ornithopter"}}))
    assert key_only == Product(key="ornithopter", name="ornithopter"), "the name defaults to the key"
    assert product_of(_run_input({})) is None
    assert product_of(_run_input({"product": "ornithopter"})) is None
    assert product_of(_run_input({"product": {"key": "orni thopter; drop", "name": "x"}})) is None
    assert product_of(_run_input({"product": {"key": "x" * 33}})) is None


def test_the_product_name_is_cut_to_200_characters_where_it_enters_the_run_context():
    assert NAME_MAX == 200
    long_name = "Ornithopter " * 40
    product = product_of(_run_input({"product": {"key": "ornithopter", "name": long_name}}))
    assert product is not None and product.name == long_name.strip()[:200] and len(product.name) == 200
    assert product.context() == f"The person is looking at product `ornithopter` ({long_name.strip()[:200]})."
    assert product_of(_run_input({"product": {"key": "ornithopter", "name": "   "}})) == Product(key="ornithopter", name="ornithopter")


def test_the_run_context_names_the_profile_and_the_product_or_says_none_is_selected():
    with_product = {e["description"]: e["value"] for e in run_context("de-engineer", ORNITHOPTER)}
    assert with_product["Viewer profile"] == "`de-engineer`"
    assert with_product["Product"] == (
        f"The person is looking at product `ornithopter` ({ORNITHOPTER.name})."
        " The tools that take a product argument answer for `ornithopter` without one."
    )
    none = {e["description"]: e["value"] for e in run_context("de-engineer")}
    assert none["Product"] == "No product is selected: the tools answer across every product."
    prompt = system_prompt()
    assert "\n\nPRODUCT. " in prompt and "\n\nTOOLS, in this order." in prompt


def test_tools_with_a_product_argument_default_to_the_product_on_screen():
    scoped = Recording("list_interfaces", {"product": {"type": "string"}})
    by_id = Recording("interface_check", {"interface": {"type": "string"}})
    product = ORNITHOPTER

    tools = with_product_default([scoped, by_id], product)

    assert [type(t).__name__ for t in tools] == ["ProductDefault", "Recording"], "only the tool whose schema has product is wrapped"
    wrapper = tools[0]
    assert isinstance(wrapper, ProductDefault)
    assert wrapper.tool_name == "list_interfaces" and wrapper.tool_type == "python"
    assert wrapper.tool_spec == scoped.tool_spec, "the definition is the server's: the run context names the default"

    _collect(wrapper.stream({"toolUseId": "1", "name": "list_interfaces", "input": {}}, {}))
    _collect(wrapper.stream({"toolUseId": "2", "name": "list_interfaces", "input": {"product": "  "}}, {}))
    _collect(wrapper.stream({"toolUseId": "3", "name": "list_interfaces", "input": {"product": "other"}}, {}))
    assert scoped.inputs == [{"product": "ornithopter"}, {"product": "ornithopter"}, {"product": "other"}], "the model's own key wins"
    assert with_product_default([scoped, by_id], None) == [scoped, by_id], "no product: the tools as they are"


def test_mcp_tools_of_the_query_service_declare_which_take_a_product():
    client = mcp_client("engineer")
    with client:
        tools = {tool.tool_name: tool for tool in client.list_tools_sync()}
        assert accepts_product(tools["list_interfaces"]) and not accepts_product(tools["interface_check"])
        wrapped = with_product_default(list(tools.values()), ORNITHOPTER)
        listing = next(t for t in wrapped if t.tool_name == "list_interfaces")
        [event] = _collect(listing.stream({"toolUseId": "t1", "name": "list_interfaces", "input": {}}, {}))
    answer = json.loads(event.tool_result["content"][0]["text"])
    assert answer["_arguments"] == {"product": "ornithopter"}, "the fake query service received the default product"
    assert answer["_headers"]["x-atelier-profile"] == "engineer"
