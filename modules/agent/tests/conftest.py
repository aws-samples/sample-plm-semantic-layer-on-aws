# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""Test fixtures: a fake query-service MCP server in a subprocess and the agent's environment."""

import socket
import subprocess  # nosec B404 - starts the fake MCP server below with a fixed argument list
import sys
import time
from pathlib import Path

import pytest

HERE = Path(__file__).parent
FAKE_MCP_SERVER = [sys.executable, str(HERE / "fake_mcp_server.py")]


def _free_port() -> int:
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


@pytest.fixture(scope="session")
def fake_mcp_url():
    port = _free_port()
    proc = subprocess.Popen([*FAKE_MCP_SERVER, str(port)])  # nosec B603 - argument list, no shell, the interpreter running the tests
    deadline = time.time() + 20
    while True:
        try:
            socket.create_connection(("127.0.0.1", port), 0.5).close()
            break
        except OSError:
            if proc.poll() is not None or time.time() > deadline:
                proc.kill()
                raise RuntimeError("fake MCP server did not start")
            time.sleep(0.1)
    yield f"http://127.0.0.1:{port}/query/mcp"
    proc.terminate()
    proc.wait(5)


@pytest.fixture(autouse=True)
def agent_env(monkeypatch, fake_mcp_url):
    from atelier_agent.mcp_tools import origin_secret

    monkeypatch.setenv("MCP_URL", fake_mcp_url)
    monkeypatch.setenv("ORIGIN_SECRET", "test-origin-secret")
    monkeypatch.delenv("ORIGIN_SECRET_ARN", raising=False)
    origin_secret.cache_clear()
    yield
    origin_secret.cache_clear()
