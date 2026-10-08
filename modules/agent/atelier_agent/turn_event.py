# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""The `atelier.turn` CUSTOM event that closes every run: tool timings, token usage, estimated cost."""

import os

from ag_ui.core import CustomEvent, EventType
from strands.telemetry.metrics import EventLoopMetrics

TURN_EVENT = "atelier.turn"
# Bedrock bills Claude prompt-cache writes (5-minute TTL) at 1.25 times and cache reads at 0.1 times the input price.
CACHE_WRITE_FACTOR = 1.25
CACHE_READ_FACTOR = 0.1


def tool_calls(metrics: EventLoopMetrics) -> list[dict]:
    """Every tool call of the run, in the order the calls started, each with its own duration: the tool traces under
    the run's event-loop cycles (tool_metrics sums the calls of a tool under its name)."""
    calls = sorted((t for cycle in metrics.traces for t in cycle.children if "tool_name" in t.metadata), key=lambda t: t.start_time)
    return [{"name": t.metadata["tool_name"], "ms": round((t.duration() or 0) * 1000)} for t in calls]


def turn_summary(metrics: EventLoopMetrics) -> dict:
    """{ tools: [{ name, ms }], usage: { inputTokens, outputTokens, cacheReadInputTokens,
    cacheWriteInputTokens }, estimatedUsd } from the Strands metrics of one run and the prices per 1K
    tokens in env PRICE_PER_1K_INPUT / PRICE_PER_1K_OUTPUT (default 0). inputTokens are the input
    tokens not read from or written to the prompt cache."""
    usage = metrics.accumulated_usage
    input_tokens = usage["inputTokens"]
    output_tokens = usage["outputTokens"]
    cache_read = usage.get("cacheReadInputTokens", 0)
    cache_write = usage.get("cacheWriteInputTokens", 0)
    price_in = float(os.environ.get("PRICE_PER_1K_INPUT", "0"))
    price_out = float(os.environ.get("PRICE_PER_1K_OUTPUT", "0"))
    return {
        "tools": tool_calls(metrics),
        "usage": {
            "inputTokens": input_tokens,
            "outputTokens": output_tokens,
            "cacheReadInputTokens": cache_read,
            "cacheWriteInputTokens": cache_write,
        },
        "estimatedUsd": round(
            (input_tokens + cache_write * CACHE_WRITE_FACTOR + cache_read * CACHE_READ_FACTOR) / 1000 * price_in
            + output_tokens / 1000 * price_out,
            6,
        ),
    }


def turn_event(metrics: EventLoopMetrics) -> CustomEvent:
    return CustomEvent(type=EventType.CUSTOM, name=TURN_EVENT, value=turn_summary(metrics))
