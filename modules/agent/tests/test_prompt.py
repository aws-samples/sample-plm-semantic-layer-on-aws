# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

"""The prompt rules on languages and on equivalences, which the contract states."""

from atelier_agent.prompt import system_prompt


def test_prompt_answers_in_the_users_language_with_the_native_name_beside_it():
    prompt = system_prompt()
    assert "find_term(term)" in prompt
    assert "Answer in the language the user writes in" in prompt
    assert "native name of the owning site" in prompt


def test_prompt_lets_the_agent_propose_an_equivalence_and_only_the_user_confirm_it():
    prompt = system_prompt()
    assert "only when the user confirms it" in prompt
    assert "confirmed: true" in prompt


def test_prompt_names_the_station_tools_isolates_the_range_and_labels_inferred_stations():
    prompt = system_prompt()
    assert "parts_between_stations(product, from, to, side)" in prompt
    assert "section_joints(product, station)" in prompt
    assert "say that its position is inferred" in prompt
    assert "the parts inside as ids and the crossing parts as context_ids" in prompt


def test_prompt_names_the_variant_diff_with_its_arguments():
    prompt = system_prompt()
    assert "variant_diff(product, group, option)" in prompt
