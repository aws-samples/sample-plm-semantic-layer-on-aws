// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import type { ProfileId } from '../api/profile';
import type { Call, Cell } from '../api/types';
import type { ViewCommand } from '../viewer/view';

/** One semantic-layer tool the agent ran during a turn, as the `atelier.turn` event reports it. */
export interface ToolStat {
  name: string;
  ms: number;
  sparqlChars: number;
  sqlChars: number;
}

export interface TurnStats {
  tools: ToolStat[];
  /** inputTokens are the input tokens neither read from nor written to the prompt cache. */
  usage: { inputTokens: number; outputTokens: number; cacheReadInputTokens: number; cacheWriteInputTokens: number };
  /** 0 when the agent has no price per token configured. */
  estimatedUsd: number;
}

/** What the `sql` tool reports back: the statement that actually ran, after export-control rewriting. */
export interface SqlRun {
  sqlExecuted: string;
  rowCount: number;
  ms: number;
  catalogueUsed: string[];
}

export type Block =
  | { kind: 'prose'; id: string; text: string }
  | { kind: 'table'; id: string; title: string; columns: string[]; rows: Cell[][] }
  | { kind: 'highlight'; id: string; ids: string[]; caption: string }
  | { kind: 'evidence'; id: string; interfaceId: string; card: string; tab: string | undefined }
  | { kind: 'view'; id: string; tool: string; action: AgentAction };

/** The screens the agent can switch to, as `open_screen` names them. */
export const SCREENS = ['check', 'bom', 'catalogue', 'flow', 'architecture', 'rules'] as const;
export type ScreenName = (typeof SCREENS)[number];

/** What a viewer or loading tool asks of the screens; the block keeps it so the person can carry it out again. */
export type AgentAction =
  | { kind: 'view'; command: ViewCommand }
  | { kind: 'subtree'; root: string; product: string | null }
  | { kind: 'product'; product: string }
  | { kind: 'screen'; screen: ScreenName };

export type ViewBlock = Extract<Block, { kind: 'view' }>;

export type EvidenceBlock = Extract<Block, { kind: 'evidence' }>;

export interface Turn {
  id: string;
  question: string;
  /** Profile the question was asked under; the agent forwarded it to every tool call. */
  profile: ProfileId;
  blocks: Block[];
  state: 'running' | 'done' | 'error';
  error?: string;
  stats: TurnStats | null;
  sqlRuns: SqlRun[];
  /** Per-endpoint requests, triples and ms summed over the provenance of every tool result of the turn. */
  calls: Call[];
  /** The actor the query service echoed in the tool results' policy (`agent`); null until a result carried one. */
  actor: string | null;
  /** A tool result carried a redaction (export_status not visible, or a { redacted, plm } part): the answer is shown as not evaluable, never as a verdict. */
  redacted: boolean;
}

/** An `open_evidence` call for the Interface check screen to carry out; `n` makes each one distinct. */
export interface EvidenceRequest {
  n: number;
  interfaceId: string;
  card: string;
  tab: string | undefined;
}
