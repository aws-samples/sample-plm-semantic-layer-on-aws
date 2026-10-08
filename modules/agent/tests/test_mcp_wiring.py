# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""The MCP client to the query service: tool discovery, header forwarding, origin secret source."""

import json

import boto3

from atelier_agent.mcp_tools import mcp_client, origin_secret


def _text(result: dict) -> dict:
    return json.loads(result["content"][0]["text"])


def test_lists_query_service_tools():
    client = mcp_client("engineer")
    with client:
        names = sorted(tool.tool_name for tool in client.list_tools_sync())
    assert names == ["catalogue", "interface_check", "list_interfaces", "products", "sql"]


def test_forwards_profile_origin_and_actor_headers_on_every_call():
    client = mcp_client("export-officer")
    with client:
        listing = _text(client.call_tool_sync(tool_use_id="t1", name="list_interfaces", arguments={}))
        check = _text(client.call_tool_sync(tool_use_id="t2", name="interface_check", arguments={"interface": "IF-02"}))
        cat = _text(client.call_tool_sync(tool_use_id="t3", name="catalogue", arguments={"plm": "UK"}))
        ran = _text(
            client.call_tool_sync(
                tool_use_id="t4",
                name="sql",
                arguments={
                    "plm": "UK",
                    "query": "SELECT cplg_ref, rating, rating_uom FROM hyd_coupling WHERE comp_id = 'ACTR-6190-L'",
                    "purpose": "pressure ratings of the left wing actuator couplings",
                },
            )
        )
    expected = {"x-atelier-profile": "export-officer", "x-origin-verify": "test-origin-secret", "x-atelier-actor": "agent"}
    assert [answer["_headers"] for answer in (listing, check, cat, ran)] == [expected] * 4
    assert [i["id"] for i in listing["interfaces"]] == ["IF-01", "IF-02"]
    assert check["interface"]["id"] == "IF-02"
    assert cat["plm"] == "UK"
    assert [t["table"] for t in cat["tables"]] == ["component", "harness_connector", "fastener", "hyd_coupling"]
    assert ran["_arguments"]["plm"] == "UK" and ran["_arguments"]["purpose"].startswith("pressure ratings")
    assert ran["rowCount"] == 2 and ran["sql"].startswith("SELECT")


def test_origin_secret_read_from_secrets_manager_when_arn_set(monkeypatch):
    arn = "arn:aws:secretsmanager:eu-west-1:111111111111:secret:atelier/origin-AbCdEf"
    monkeypatch.delenv("ORIGIN_SECRET")
    monkeypatch.setenv("ORIGIN_SECRET_ARN", arn)
    calls = []

    class SecretsManagerStub:
        def get_secret_value(self, SecretId):
            calls.append(SecretId)
            return {"SecretString": "from-secrets-manager"}  # pragma: allowlist secret

    def client(service):
        assert service == "secretsmanager"
        return SecretsManagerStub()

    monkeypatch.setattr(boto3, "client", client)
    origin_secret.cache_clear()
    assert origin_secret() == "from-secrets-manager"
    assert origin_secret() == "from-secrets-manager"
    assert calls == [arn], "the secret is read once per process"


def test_origin_secret_env_wins_and_absence_is_empty(monkeypatch):
    assert origin_secret() == "test-origin-secret"
    monkeypatch.delenv("ORIGIN_SECRET")
    origin_secret.cache_clear()
    assert origin_secret() == ""
