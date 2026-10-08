// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import type { McpTools } from './types';

export type McpTool = McpTools['tools'][number];

/** The description up to its first sentence end; the whole text when it has none. */
export function firstSentence(description: string): string {
  return /^(.*?[.!?])(?:\s|$)/.exec(description)?.[1] ?? description;
}

/**
 * What the description names after "Argument:" or "Arguments:", to the end of that sentence;
 * null when it names none (a tool without arguments, or one whose description does not list them).
 */
export function argumentHint(description: string): string | null {
  return /\bArguments?:\s*(.+?)\.(?:\s|$)/.exec(description)?.[1] ?? null;
}
