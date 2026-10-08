# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""The show eval: "show me X" questions asked of the real agent, graded on the viewer call it makes.

Each question of show_questions.json names a product, the question and what a right answer does on screen:
`isolate_parts` or `highlight_parts` (the last viewer call is that tool), `view` (any of isolate_parts,
highlight_parts or zoom_to_part), `open_subtree` (the assembly `root` opened, and no part viewer call) or `ask`
(no viewer call, no table, and the answer asks a question). A viewer
answer passes when its part ids hold every `required` id and no `forbidden` one, no table was rendered, and the
caption names each group of `caption` (one of the words of each group). Every question runs `--runs` times
through atelier_agent.runner.run, the code path of POST /agent/invocations, with the product on screen and
nothing selected; the report gives the pass rate, the tool calls, the tokens per turn (uncached input, read
from and written to the prompt cache, output), the time to the first streamed token and the turn duration.

Needs the query service (eval/stack.sh up), Bedrock credentials and MODEL_ID; MCP_URL defaults to the stack's.
Every run calls the model: this is not part of CI.
"""

import argparse
import asyncio
import json
import os
import statistics
import sys
import time
import uuid
from pathlib import Path

HERE = Path(__file__).parent
sys.path.insert(0, str(HERE.parent))

from ag_ui.core import EventType, RunAgentInput, UserMessage  # noqa: E402

from atelier_agent.part_ids import normalised  # noqa: E402
from atelier_agent.runner import run  # noqa: E402

VIEWER = ("isolate_parts", "highlight_parts", "zoom_to_part")


async def ask(question: dict, profile: str) -> dict:
    """One run of the question: the tool calls in order with their arguments, the answer text and the turn's usage."""
    run_input = RunAgentInput(
        thread_id=str(uuid.uuid4()), run_id=str(uuid.uuid4()), state={}, tools=[], context=[],
        messages=[UserMessage(id="m1", role="user", content=question["question"])],
        forwarded_props={"product": {"key": question["product"], "name": question["product"]}},
    )
    calls, args, results, text, turn, error, first = [], {}, {}, [], None, None, None
    start = time.monotonic()
    async for event in run(run_input, profile):
        if first is None and event.type in (EventType.TEXT_MESSAGE_CONTENT, EventType.TOOL_CALL_START):
            first = time.monotonic() - start
        if event.type == EventType.TOOL_CALL_START:
            calls.append({"id": event.tool_call_id, "name": event.tool_call_name})
            args[event.tool_call_id] = ""
        elif event.type == EventType.TOOL_CALL_ARGS:
            args[event.tool_call_id] += event.delta
        elif event.type == EventType.TOOL_CALL_RESULT:
            results[event.tool_call_id] = str(event.content)[:400]
        elif event.type == EventType.TEXT_MESSAGE_CONTENT:
            text.append(event.delta)
        elif event.type == EventType.CUSTOM and event.name == "atelier.turn":
            turn = event.value
        elif event.type == EventType.RUN_ERROR:
            error = event.message
    for call in calls:
        call_id = call.pop("id")
        try:
            call["args"] = json.loads(args.pop(call_id) or "{}")
        except json.JSONDecodeError:
            call["args"] = {}
        if call["name"] in VIEWER and call_id in results:
            call["result"] = results[call_id]
    return {"calls": calls, "text": "".join(text), "turn": turn, "error": error, "firstTokenS": first,
            "durationS": time.monotonic() - start}


def grade(question: dict, result: dict) -> tuple[bool, str]:
    """Pass or fail, with the reason of a failure."""
    if result["error"]:
        return False, f"run error: {result['error']}"
    names = [c["name"] for c in result["calls"]]
    views = [c for c in result["calls"] if c["name"] in VIEWER]
    if question["expect"] == "open_subtree":
        opened = [c["args"].get("root") for c in result["calls"] if c["name"] == "open_subtree"]
        if question["root"] not in opened:
            return False, f"open_subtree roots {opened}, expected {question['root']}"
        if views or "render_table" in names:
            return False, f"opened the subtree and also called {[c['name'] for c in views] or 'render_table'}"
        return True, ""
    if question["expect"] == "ask":
        if views or "render_table" in names:
            return False, f"answered instead of asking: {[c['name'] for c in views] or 'render_table'}"
        return ("?" in result["text"], "" if "?" in result["text"] else "no question in the answer")
    if not views:
        return False, "no viewer call"
    last = views[-1]
    wanted = VIEWER if question["expect"] == "view" else (question["expect"],)
    if last["name"] not in wanted:
        return False, f"last viewer call {last['name']}, expected {question['expect']}"
    # The viewer reads an id through its normalised form (UK3501 is UK-3501), so the grade does too.
    ids = {normalised(i) for i in (last["args"].get("ids") or []) + ([last["args"]["id"]] if last["args"].get("id") else [])}
    missing = [i for i in question.get("required", []) if normalised(i) not in ids]
    wrong = [i for i in question.get("forbidden", []) if normalised(i) in ids]
    if missing or wrong:
        return False, f"missing {missing} wrong {wrong}"
    if "render_table" in names:
        return False, "rendered a table"
    caption = (last["args"].get("caption") or "").lower()
    unnamed = [group for group in question.get("caption", []) if not any(word in caption for word in group)]
    if unnamed:
        return False, f"caption names no {[g[0] for g in unnamed]}: {caption!r}"
    return True, ""


async def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--runs", type=int, default=3, help="runs per question (default 3)")
    parser.add_argument("--only", nargs="*", help="question ids to run (default all)")
    parser.add_argument("--profile", default="programme-cleared", help="viewer profile of the runs")
    parser.add_argument("--parallel", type=int, default=4, help="runs at a time (default 4)")
    parser.add_argument("--out", type=Path, help="write every run's calls, answer and grade to this JSON file")
    options = parser.parse_args()
    os.environ.setdefault("MCP_URL", "http://127.0.0.1:19480/query/mcp")
    questions = [q for q in json.loads((HERE / "show_questions.json").read_text()) if not options.only or q["id"] in options.only]
    gate = asyncio.Semaphore(options.parallel)

    async def one(question: dict, attempt: int) -> dict:
        async with gate:
            result = await ask(question, options.profile)
        passed, reason = grade(question, result)
        return {"id": question["id"], "run": attempt, "passed": passed, "reason": reason, **result}

    runs = await asyncio.gather(*(one(q, n) for q in questions for n in range(1, options.runs + 1)))
    for question in questions:
        mine = [r for r in runs if r["id"] == question["id"]]
        print(f"{question['id']}: {sum(r['passed'] for r in mine)}/{len(mine)}  {question['question']}")
        for r in mine:
            usage = (r["turn"] or {}).get("usage", {})
            tools = " ".join(c["name"] for c in r["calls"])
            print(f"  run {r['run']} {'pass' if r['passed'] else 'FAIL'} in={usage.get('inputTokens')}"
                  f" cacheRead={usage.get('cacheReadInputTokens', 0)} cacheWrite={usage.get('cacheWriteInputTokens', 0)}"
                  f" out={usage.get('outputTokens')} first={r['firstTokenS'] or 0:.1f}s turn={r['durationS']:.1f}s"
                  f" calls={len(r['calls'])} [{tools}] {r['reason']}")
    passed = sum(r["passed"] for r in runs)
    print(f"passed {passed}/{len(runs)} ({100 * passed / len(runs):.0f} %)")
    for field in ("inputTokens", "cacheReadInputTokens", "cacheWriteInputTokens", "outputTokens"):
        values = [((r["turn"] or {}).get("usage") or {}).get(field, 0) for r in runs]
        print(f"  {field} per turn: mean {statistics.mean(values):.0f}, median {statistics.median(values):.0f}")
    for field in ("firstTokenS", "durationS"):
        values = [r[field] or 0 for r in runs]
        print(f"  {field}: mean {statistics.mean(values):.1f}, median {statistics.median(values):.1f}")
    if options.out:
        options.out.write_text(json.dumps(runs, indent=1, ensure_ascii=False))


if __name__ == "__main__":
    asyncio.run(main())
