// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import type { Message } from '@ag-ui/client';

/**
 * The transcript worth replaying: the user's questions and the agent's prose. The frontend tools
 * (highlight_interfaces, open_evidence, render_table) return nothing, so replaying their calls
 * would leave unpaired tool uses in the model's history; the agent re-queries the semantic layer
 * on every turn, so the prose is all a follow-up needs.
 */
export function conversationalHistory(messages: readonly Message[]): Message[] {
  return messages.filter((m) =>
    m.role === 'user' ? true : m.role === 'assistant' && !m.toolCalls?.length && typeof m.content === 'string' && m.content.trim().length > 0,
  );
}
