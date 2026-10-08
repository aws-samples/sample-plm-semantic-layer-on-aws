#!/usr/bin/env bash
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0

# Point this clone's git hooks at .githooks/ (once per clone).
#
#   bash scripts/install-hooks.sh
#
# `core.hooksPath` is per-clone local config, so it cannot be committed — every
# clone has to run this. That is also why the hook must not be the ONLY place a
# check lives: CI runs the same assertions, because a hook nobody installed
# enforces nothing.
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"
git config core.hooksPath .githooks
chmod +x .githooks/* 2>/dev/null || true

echo "hooks installed: core.hooksPath=.githooks"
echo "  pre-commit -> eslint on staged files + the app-code auth assertion (~1s)"
echo "  bypass once with: git commit --no-verify"
echo
echo "The slower tiers stay where they belong:"
echo "  bash scripts/verify.sh          full local gate (lint, typecheck, gate self-test, build, synth+guardrails)"
echo "  bash scripts/verify.sh --fast   skip the synth tier"
echo "  bash scripts/verify.sh --live   also smoke-test the deployed site"
