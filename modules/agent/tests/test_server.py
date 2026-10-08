# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""The FastAPI app and the run wrapper, with the model layer replaced by a stub.

The stub stands in for ag-ui-strands' StrandsAgent: it yields a minimal AG-UI run and, like the
real one, creates the per-thread Strands agent during the run and stores it in
`agents_by_thread`. Everything else is real: the MCP client to the fake query service, the
template agent with its tools, the SSE encoding, the `atelier.turn` insertion.
"""

import json
from types import SimpleNamespace

import pytest
from ag_ui.core import (
    EventType,
    RunFinishedEvent,
    RunStartedEvent,
    TextMessageContentEvent,
    TextMessageEndEvent,
    TextMessageStartEvent,
)
from fastapi.testclient import TestClient
from strands.telemetry.metrics import EventLoopMetrics, Trace, Usage

import atelier_agent.runner as runner
from atelier_agent.frontend_tools import (
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
from atelier_agent.product import Product
from atelier_agent.prompt import system_prompt
from server import app

# conftest sets ORIGIN_SECRET to this value; every request through CloudFront carries it.
ORIGIN_OK = {"x-origin-verify": "test-origin-secret"}

PRODUCT = {"key": "ornithopter", "name": "Ornithopter ground demonstrator (Paris Manuscript B, f. 74v)"}

RUN_INPUT = {
    "threadId": "thread-1",
    "runId": "run-1",
    "messages": [{"id": "m1", "role": "user", "content": "Which interfaces fail?"}],
    "tools": [],
    "context": [],
    "state": {},
    "forwardedProps": {},
}


def _thread_metrics() -> EventLoopMetrics:
    metrics = EventLoopMetrics()
    metrics.accumulated_usage = Usage(inputTokens=900, outputTokens=120, totalTokens=1020)
    cycle = Trace("Cycle 1", start_time=10.0)
    metrics.traces.append(cycle)
    call = Trace("Tool: list_interfaces", parent_id=cycle.id, start_time=10.1, raw_name="list_interfaces")
    metrics.add_tool_usage({"toolUseId": "t1", "name": "list_interfaces", "input": {}}, 0.0512, call, True)
    call.end(10.1512)
    cycle.add_child(call)
    return metrics


class StrandsAgentStub:
    captured: dict = {}

    def __init__(self, agent, name, description="", agents_by_thread=None):
        StrandsAgentStub.captured["template"] = agent
        self.agents_by_thread = agents_by_thread

    async def run(self, run_input):
        StrandsAgentStub.captured["input"] = run_input
        ids = {"thread_id": run_input.thread_id, "run_id": run_input.run_id}
        yield RunStartedEvent(type=EventType.RUN_STARTED, **ids)
        self.agents_by_thread[run_input.thread_id] = SimpleNamespace(event_loop_metrics=_thread_metrics())
        yield TextMessageStartEvent(type=EventType.TEXT_MESSAGE_START, message_id="a1", role="assistant")
        yield TextMessageContentEvent(type=EventType.TEXT_MESSAGE_CONTENT, message_id="a1", delta="IF-02 fails.")
        yield TextMessageEndEvent(type=EventType.TEXT_MESSAGE_END, message_id="a1")
        yield RunFinishedEvent(type=EventType.RUN_FINISHED, **ids)


def _context() -> dict[str, str]:
    """The run context the runner handed to ag-ui-strands, by description."""
    return {c.description: c.value for c in StrandsAgentStub.captured["input"].context}


def _sse_events(body: str) -> list[dict]:
    return [json.loads(line[len("data: ") :]) for line in body.splitlines() if line.startswith("data: ")]


@pytest.fixture
def stubbed_model(monkeypatch):
    monkeypatch.setenv("MODEL_ID", "stub-model-id")
    monkeypatch.setenv("AWS_DEFAULT_REGION", "eu-west-1")
    monkeypatch.setattr(runner, "StrandsAgent", StrandsAgentStub)
    profiles = []
    real_mcp_client = runner.mcp_client

    def recording_mcp_client(profile):
        profiles.append(profile)
        return real_mcp_client(profile)

    monkeypatch.setattr(runner, "mcp_client", recording_mcp_client)
    runner.model.cache_clear()
    StrandsAgentStub.captured.clear()
    yield profiles
    runner.model.cache_clear()


def test_health():
    with TestClient(app, headers=ORIGIN_OK) as client:
        response = client.get("/agent/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_frontend_tools_are_the_contract_and_do_nothing_on_the_server():
    tools = (
        highlight_interfaces, open_evidence, render_table,
        highlight_parts, isolate_parts, zoom_to_part, clear_view, open_subtree, open_product, open_screen,
    )
    specs = {tool.tool_name: tool.tool_spec for tool in tools}
    schemas = {name: spec["inputSchema"]["json"] for name, spec in specs.items()}
    params = {name: sorted(schema["properties"]) for name, schema in schemas.items()}
    assert params == {
        "highlight_interfaces": ["caption", "ids"],
        "open_evidence": ["endpoint", "interface", "tab"],
        "render_table": ["columns", "rows", "title"],
        "highlight_parts": ["caption", "ids"],
        "isolate_parts": ["caption", "context_ids", "ids"],
        "zoom_to_part": ["id"],
        "clear_view": [],
        "open_subtree": ["product", "root"],
        "open_product": ["product"],
        "open_screen": ["screen"],
    }
    required = {name: sorted(schema.get("required", [])) for name, schema in schemas.items()}
    assert required["highlight_parts"] == ["ids"] and required["isolate_parts"] == ["ids"]
    assert required["zoom_to_part"] == ["id"] and required["clear_view"] == []
    assert required["open_subtree"] == ["root"] and required["open_product"] == ["product"] and required["open_screen"] == ["screen"]
    assert schemas["isolate_parts"]["properties"]["ids"]["type"] == "array"
    for name in ("highlight_parts", "isolate_parts", "zoom_to_part", "clear_view", "open_subtree", "open_product", "open_screen"):
        assert "FRONTEND tool" in specs[name]["description"], f"{name} tells the model the browser carries it out"
    screens = system_prompt().split("open_screen switches to the", 1)[1].split("screen.", 1)[0]
    for screen in ("check", "paths", "bom", "catalogue", "flow", "architecture", "rules"):
        assert f"'{screen}'" in specs["open_screen"]["description"]
        assert f" {screen}," in screens or f" {screen} " in screens, f"the prompt names the {screen} screen"
    assert highlight_interfaces(ids=["IF-02"], caption="failing") is None
    assert open_evidence(interface="IF-02", endpoint="ontop-uk") is None
    assert render_table(title="t", columns=["id"], rows=[["IF-02"]]) is None
    assert highlight_parts(ids=["D-37011"], caption="gearbox housing") == "Outlined.", "outside a run: no product to check against"
    assert isolate_parts(ids=["D-37011"], caption="c", context_ids=["FR-ORN-KEEL-001"]) == "Isolated."
    assert zoom_to_part(id="D-37011") == "Zoomed." and clear_view() is None
    assert open_subtree(root="D-37073", product="wind-turbine") is None
    assert open_product(product="wind-turbine") is None and open_screen(screen="bom") is None


def test_invocations_streams_run_with_atelier_turn_before_run_finished(stubbed_model):
    with TestClient(app, headers=ORIGIN_OK) as client:
        response = client.post("/agent/invocations", json=RUN_INPUT, headers={"x-atelier-profile": "engineer"})
    assert response.status_code == 200
    assert response.headers["content-type"].startswith("text/event-stream")
    events = _sse_events(response.text)
    assert [e["type"] for e in events] == [
        "RUN_STARTED",
        "TEXT_MESSAGE_START",
        "TEXT_MESSAGE_CONTENT",
        "TEXT_MESSAGE_END",
        "CUSTOM",
        "RUN_FINISHED",
    ]
    turn = events[-2]
    assert turn["name"] == "atelier.turn"
    assert turn["value"] == {
        "tools": [{"name": "list_interfaces", "ms": 51}],
        "usage": {"inputTokens": 900, "outputTokens": 120, "cacheReadInputTokens": 0, "cacheWriteInputTokens": 0},
        "estimatedUsd": 0,
    }


def test_run_builds_agent_with_mcp_and_frontend_tools_for_the_header_profile(stubbed_model):
    with TestClient(app, headers=ORIGIN_OK) as client:
        client.post("/agent/invocations", json=RUN_INPUT, headers={"x-atelier-profile": "engineer"})
    assert stubbed_model == ["engineer"], "the header profile is the one the MCP client sends"
    template = StrandsAgentStub.captured["template"]
    assert sorted(template.tool_names) == [
        "catalogue",
        "clear_view",
        "highlight_interfaces",
        "highlight_parts",
        "interface_check",
        "isolate_parts",
        "list_interfaces",
        "open_evidence",
        "open_product",
        "open_screen",
        "open_subtree",
        "products",
        "render_table",
        "sql",
        "zoom_to_part",
    ]
    assert template.system_prompt == system_prompt(), "the system prompt is the same for every run"
    assert "not visible to your profile" in template.system_prompt
    assert _context() == {
        "Viewer profile": "`engineer`",
        "Product": "No product is selected: the tools answer across every product.",
        "Selection": "Nothing is selected.",
    }
    assert type(template.tool_registry.registry["list_interfaces"]).__name__ == "MCPAgentTool", "no product: the tool as the server gave it"


def test_product_in_the_body_reaches_the_prompt_and_defaults_the_scoped_tools(stubbed_model):
    body = {**RUN_INPUT, "forwardedProps": {"product": PRODUCT}}
    with TestClient(app, headers=ORIGIN_OK) as client:
        client.post("/agent/invocations", json=body, headers={"x-atelier-profile": "engineer"})
    template = StrandsAgentStub.captured["template"]
    assert _context()["Product"].startswith(f"The person is looking at product `ornithopter` ({PRODUCT['name']}).")
    assert "ornithopter" not in template.system_prompt
    registry = template.tool_registry.registry
    assert type(registry["list_interfaces"]).__name__ == "ProductDefault"
    assert registry["list_interfaces"].product == Product(**PRODUCT)
    assert type(registry["interface_check"]).__name__ == "MCPAgentTool", "a tool without a product argument is left alone"
    assert type(registry["highlight_interfaces"]).__name__ != "ProductDefault"


def test_selection_in_the_body_reaches_the_prompt(stubbed_model):
    selection = {
        "product": "wind-turbine",
        "root": "D-37073",
        "part": {"id": "D-37011", "plm": "de", "name": "Getriebegehäuse Planetenstufe"},
        "interface": None,
    }
    body = {**RUN_INPUT, "forwardedProps": {"product": PRODUCT, "selection": selection}}
    with TestClient(app, headers=ORIGIN_OK) as client:
        client.post("/agent/invocations", json=body, headers={"x-atelier-profile": "engineer"})
    selected = _context()["Selection"]
    assert "Product `wind-turbine` is on screen in the subtree of assembly `D-37073`." in selected
    assert "The person has selected part `D-37011` (Getriebegehäuse Planetenstufe, DE PLM)." in selected
    assert "D-37011" not in StrandsAgentStub.captured["template"].system_prompt


def test_missing_profile_header_means_unknown(stubbed_model):
    with TestClient(app, headers=ORIGIN_OK) as client:
        client.post("/agent/invocations", json=RUN_INPUT)
    assert stubbed_model == ["unknown"]
    assert _context()["Viewer profile"] == "`unknown`"


def test_malformed_profile_header_means_unknown(stubbed_model):
    with TestClient(app, headers=ORIGIN_OK) as client:
        client.post("/agent/invocations", json=RUN_INPUT, headers={"x-atelier-profile": "DE Engineer; `drop`"})
    assert stubbed_model == ["unknown"]
    assert _context()["Viewer profile"] == "`unknown`"
    assert "drop" not in StrandsAgentStub.captured["template"].system_prompt


def test_origin_secret_is_required_when_configured(stubbed_model, monkeypatch):
    from atelier_agent import mcp_tools

    monkeypatch.setenv("ORIGIN_SECRET", "test-origin-secret")
    mcp_tools.origin_secret.cache_clear()
    try:
        with TestClient(app) as client:
            denied = client.post("/agent/invocations", json=RUN_INPUT, headers={"x-atelier-profile": "engineer"})
            assert denied.status_code == 403
            wrong = client.post(
                "/agent/invocations", json=RUN_INPUT, headers={"x-atelier-profile": "engineer", "x-origin-verify": "other"}
            )
            assert wrong.status_code == 403
            allowed = client.post(
                "/agent/invocations",
                json=RUN_INPUT,
                headers={"x-atelier-profile": "engineer", "x-origin-verify": "test-origin-secret"},
            )
            assert allowed.status_code == 200
    finally:
        mcp_tools.origin_secret.cache_clear()
    assert stubbed_model == ["engineer"], "only the verified request ran"


def test_unreachable_query_service_is_a_run_error(stubbed_model, monkeypatch):
    monkeypatch.setenv("MCP_URL", "http://127.0.0.1:9/query/mcp")
    with TestClient(app, headers=ORIGIN_OK) as client:
        response = client.post("/agent/invocations", json=RUN_INPUT, headers={"x-atelier-profile": "engineer"})
    events = _sse_events(response.text)
    assert [e["type"] for e in events] == ["RUN_STARTED", "RUN_ERROR"]
    assert events[1]["code"] == "MCP_UNAVAILABLE"
