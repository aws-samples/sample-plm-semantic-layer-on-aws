# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""The `atelier.turn` event built from Strands run metrics."""

from ag_ui.core import EventType
from strands.telemetry.metrics import EventLoopMetrics, Trace, Usage

from atelier_agent.turn_event import TURN_EVENT, turn_event


def _call(metrics: EventLoopMetrics, cycle: Trace, tool_use_id: str, name: str, start: float, seconds: float) -> None:
    """One tool call of a cycle, as the Strands tool executor records it."""
    trace = Trace(f"Tool: {name}", parent_id=cycle.id, start_time=start, raw_name=name)
    metrics.add_tool_usage({"toolUseId": tool_use_id, "name": name, "input": {}}, seconds, trace, True)
    trace.end(start + seconds)
    cycle.add_child(trace)


def _metrics() -> EventLoopMetrics:
    metrics = EventLoopMetrics()
    metrics.accumulated_usage = Usage(inputTokens=1200, outputTokens=300, totalTokens=1500)
    first, second = Trace("Cycle 1", start_time=100.0), Trace("Cycle 2", start_time=101.0)
    metrics.traces += [first, second]
    _call(metrics, first, "t1", "list_interfaces", 100.1, 0.0804)
    _call(metrics, second, "t2", "interface_check", 101.1, 0.1204)
    _call(metrics, second, "t3", "interface_check", 101.3, 0.13)
    return metrics


def test_turn_event_shape_and_cost(monkeypatch):
    monkeypatch.setenv("PRICE_PER_1K_INPUT", "0.003")
    monkeypatch.setenv("PRICE_PER_1K_OUTPUT", "0.015")
    event = turn_event(_metrics())
    assert event.type == EventType.CUSTOM
    assert event.name == TURN_EVENT
    assert event.value == {
        "tools": [{"name": "list_interfaces", "ms": 80}, {"name": "interface_check", "ms": 120}, {"name": "interface_check", "ms": 130}],
        "usage": {"inputTokens": 1200, "outputTokens": 300, "cacheReadInputTokens": 0, "cacheWriteInputTokens": 0},
        "estimatedUsd": 0.0081,
    }


def test_prices_default_to_zero(monkeypatch):
    monkeypatch.delenv("PRICE_PER_1K_INPUT", raising=False)
    monkeypatch.delenv("PRICE_PER_1K_OUTPUT", raising=False)
    assert turn_event(_metrics()).value["estimatedUsd"] == 0


def test_run_without_tools_or_usage():
    value = turn_event(EventLoopMetrics()).value
    assert value == {
        "tools": [],
        "usage": {"inputTokens": 0, "outputTokens": 0, "cacheReadInputTokens": 0, "cacheWriteInputTokens": 0},
        "estimatedUsd": 0,
    }


def test_cache_reads_and_writes_are_priced_at_their_share_of_the_input_price(monkeypatch):
    monkeypatch.setenv("PRICE_PER_1K_INPUT", "0.0022")
    monkeypatch.setenv("PRICE_PER_1K_OUTPUT", "0.011")
    metrics = EventLoopMetrics()
    metrics.accumulated_usage = Usage(
        inputTokens=1000, outputTokens=100, totalTokens=1100, cacheReadInputTokens=20000, cacheWriteInputTokens=4000
    )
    value = turn_event(metrics).value
    assert value["usage"]["cacheReadInputTokens"] == 20000 and value["usage"]["cacheWriteInputTokens"] == 4000
    # 1000 uncached + 4000 x 1.25 written + 20000 x 0.1 read = 8000 input-price tokens, plus 100 output.
    assert value["estimatedUsd"] == round(8000 / 1000 * 0.0022 + 100 / 1000 * 0.011, 6)


def test_every_call_is_listed_in_order_with_its_own_duration():
    metrics = EventLoopMetrics()
    cycles = [Trace(f"Cycle {n}", start_time=200.0 + n) for n in (1, 2, 3)]
    metrics.traces += cycles
    _call(metrics, cycles[0], "a", "find_parts", 201.1, 0.4)
    _call(metrics, cycles[0], "b", "find_parts", 201.05, 0.6)
    _call(metrics, cycles[2], "c", "isolate_parts", 203.1, 0.002)
    assert turn_event(metrics).value["tools"] == [
        {"name": "find_parts", "ms": 600}, {"name": "find_parts", "ms": 400}, {"name": "isolate_parts", "ms": 2}]
