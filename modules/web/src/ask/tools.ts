// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// The frontend tools of docs/contract.md ("Agents on the semantic layer"), the `sql` tool's
// result and the `atelier.turn` event, read from the agent's events. The agent is another system:
// its values are coerced here, once, so the panel renders whatever shape it sent.
import type { Call, Cell } from '../api/types';
import { SHACL_CARD } from '../check/PathStrip';
import { SCREENS, type AgentAction, type Block, type EvidenceBlock, type EvidenceRequest, type ScreenName, type SqlRun, type TurnStats, type ViewBlock } from './types';

type Args = Record<string, unknown>;

const str = (v: unknown, fallback = '') => (typeof v === 'string' ? v : fallback);
const num = (v: unknown) => (typeof v === 'number' && Number.isFinite(v) ? v : 0);
const strings = (v: unknown): string[] => (Array.isArray(v) ? v.filter((x): x is string => typeof x === 'string') : []);
const cell = (v: unknown): Cell =>
  typeof v === 'string' || typeof v === 'number' || typeof v === 'boolean' || v === null ? v : v === undefined ? null : JSON.stringify(v);

export function tableBlock(id: string, a: Args): Block {
  const rows = Array.isArray(a.rows) ? a.rows.map((r: unknown) => (Array.isArray(r) ? r.map(cell) : [cell(r)])) : [];
  return { kind: 'table', id, title: str(a.title), columns: strings(a.columns), rows };
}

export function highlightBlock(id: string, a: Args): Extract<Block, { kind: 'highlight' }> {
  return { kind: 'highlight', id, ids: strings(a.ids), caption: str(a.caption) };
}

/** Answer-path card of an `open_evidence` endpoint: the rules' card, else the endpoint's name. */
const cardOf = (endpoint: string) => (/shacl|rules|validation/i.test(endpoint) ? SHACL_CARD : endpoint.trim().toLowerCase());

/** Evidence drawer tab ids, from the names an agent is likely to use for them. */
const TAB_ID: Record<string, string> = {
  arm: 'arm', sparql: 'arm', service: 'arm', 'service arm': 'arm',
  sql: 'sql', 'generated sql': 'sql',
  rows: 'rows', 'native rows': 'rows', records: 'rows', tables: 'rows',
  triples: 'triples', turtle: 'triples',
  r2rml: 'r2rml', mapping: 'r2rml',
  links: 'links', 'links and file index': 'links', 'file index': 'links',
  graph: 'graph',
  shapes: 'shapes', report: 'report',
};

export const TAB_LABEL: Record<string, string> = {
  arm: 'SERVICE arm', sql: 'Generated SQL', rows: 'Native rows', triples: 'Triples', r2rml: 'R2RML',
  links: 'Links and file index', graph: 'Graph', shapes: 'Shapes', report: 'Report',
};

export function evidenceBlock(id: string, a: Args): EvidenceBlock {
  const tab = typeof a.tab === 'string' ? TAB_ID[a.tab.trim().toLowerCase()] : undefined;
  return { kind: 'evidence', id, interfaceId: str(a.interface).trim(), card: cardOf(str(a.endpoint)), tab };
}

export const toRequest = (b: EvidenceBlock, n: number): EvidenceRequest => ({ n, interfaceId: b.interfaceId, card: b.card, tab: b.tab });

const ids = (v: unknown) => strings(v).map((x) => x.trim()).filter(Boolean);

/** The action of a viewer or loading tool call; null for any other tool, or for a call missing what it needs. */
export function agentAction(name: string, a: Args): AgentAction | null {
  switch (name) {
    case 'highlight_parts':
      return { kind: 'view', command: { kind: 'highlight', ids: ids(a.ids), caption: str(a.caption).trim() } };
    case 'isolate_parts':
      return { kind: 'view', command: { kind: 'isolate', ids: ids(a.ids), contextIds: ids(a.context_ids), caption: str(a.caption).trim() } };
    case 'zoom_to_part': {
      const id = str(a.id).trim();
      return id ? { kind: 'view', command: { kind: 'zoom', id } } : null;
    }
    case 'clear_view':
      return { kind: 'view', command: { kind: 'clear' } };
    case 'open_subtree': {
      const root = str(a.root).trim();
      return root ? { kind: 'subtree', root, product: str(a.product).trim() || null } : null;
    }
    case 'open_product': {
      const product = str(a.product).trim();
      return product ? { kind: 'product', product } : null;
    }
    case 'open_screen': {
      const screen = str(a.screen).trim().toLowerCase();
      return SCREENS.includes(screen as ScreenName) ? { kind: 'screen', screen: screen as ScreenName } : null;
    }
    default:
      return null;
  }
}

export const viewBlock = (id: string, tool: string, action: AgentAction): ViewBlock => ({ kind: 'view', id, tool, action });

/** The input tokens a turn processed: uncached, read from the prompt cache and written to it. */
export const inputProcessed = (u: TurnStats['usage']) => u.inputTokens + u.cacheReadInputTokens + u.cacheWriteInputTokens;

/** The value of the `atelier.turn` CUSTOM event. */
export function turnStats(v: unknown): TurnStats {
  const o = (v ?? {}) as Args;
  const usage = (o.usage ?? {}) as Args;
  const tools = Array.isArray(o.tools)
    ? (o.tools as Args[]).map((t) => ({ name: str(t.name, '?'), ms: num(t.ms), sparqlChars: num(t.sparqlChars), sqlChars: num(t.sqlChars) }))
    : [];
  return {
    tools,
    usage: {
      inputTokens: num(usage.inputTokens),
      outputTokens: num(usage.outputTokens),
      cacheReadInputTokens: num(usage.cacheReadInputTokens),
      cacheWriteInputTokens: num(usage.cacheWriteInputTokens),
    },
    estimatedUsd: num(o.estimatedUsd),
  };
}

/** The `sql` tool's result, from a TOOL_CALL_RESULT's content. */
export function sqlRun(content: unknown): SqlRun | null {
  const o = unwrap(content, 'sqlExecuted');
  if (!o) return null;
  const used = Array.isArray(o.catalogueUsed)
    ? o.catalogueUsed.map((x: unknown) => (typeof x === 'string' ? x : entryName(x)))
    : [];
  return { sqlExecuted: str(o.sqlExecuted), rowCount: num(o.rowCount), ms: num(o.ms), catalogueUsed: used };
}

/** A catalogue entry as `table.column`, or `table`, when the tool returns entries rather than names. */
function entryName(x: unknown): string {
  const o = (x ?? {}) as Args;
  return [o.table, o.column].filter((s) => typeof s === 'string').join('.') || JSON.stringify(x);
}

/** What a semantic-layer tool's result says about the sources it read and who asked. */
export interface ToolProvenance {
  calls: Call[];
  actor: string | null;
}

const KINDS = new Set<Call['kind']>(['virtual', 'materialized']);

/** The provenance and policy actor of a TOOL_CALL_RESULT's content; null for a result without provenance. */
export function toolProvenance(content: unknown): ToolProvenance | null {
  const o = unwrap(content, 'provenance');
  if (!o) return null;
  const raw = (o.provenance as Args | null)?.calls;
  const calls = Array.isArray(raw)
    ? (raw as Args[]).filter((c) => typeof c.endpoint === 'string').map((c): Call => ({
      endpoint: c.endpoint as string,
      kind: KINDS.has(c.kind as Call['kind']) ? (c.kind as Call['kind']) : 'virtual',
      requests: num(c.requests),
      triples: num(c.triples),
      ms: num(c.ms),
    }))
    : [];
  const actor = (o.policy as Args | null)?.actor;
  return { calls, actor: typeof actor === 'string' ? actor : null };
}

/** Per-endpoint sums of two provenances, endpoints in order of first appearance. */
export function mergeCalls(a: Call[], b: Call[]): Call[] {
  const out = a.map((c) => ({ ...c }));
  for (const c of b) {
    const seen = out.find((x) => x.endpoint === c.endpoint);
    if (seen) {
      seen.requests += c.requests;
      seen.triples += c.triples;
      seen.ms += c.ms;
    } else out.push({ ...c });
  }
  return out;
}

/** The result object holding `key`, whether it came as JSON, a JSON string, or MCP text content wrapping one. */
function unwrap(v: unknown, key: string, depth = 0): Args | null {
  if (depth > 3 || v === null || v === undefined) return null;
  if (typeof v === 'string') {
    try {
      return unwrap(JSON.parse(v), key, depth + 1);
    } catch {
      return null;
    }
  }
  if (Array.isArray(v)) {
    for (const x of v) {
      const r = unwrap(x, key, depth + 1);
      if (r) return r;
    }
    return null;
  }
  if (typeof v !== 'object') return null;
  const o = v as Args;
  if (o[key] !== undefined && o[key] !== null) return o;
  if ('text' in o) return unwrap(o.text, key, depth + 1);
  if ('content' in o) return unwrap(o.content, key, depth + 1);
  return null;
}

/**
 * A tool result carried a redaction: `export_status` answers `visible: false`, or a part comes back as
 * `{ redacted: true, plm }` anywhere in the result. Read from the results, never from the prose.
 */
export function redactionIn(content: unknown, toolName: string | undefined): boolean {
  if (toolName === 'export_status') {
    const o = unwrap(content, 'visible');
    if (o?.visible === false) return true;
  }
  return hasRedacted(content, 0);
}

function hasRedacted(v: unknown, depth: number): boolean {
  if (depth > 6 || v === null || v === undefined) return false;
  if (typeof v === 'string') {
    if (!/^\s*[[{]/.test(v)) return false;
    try {
      return hasRedacted(JSON.parse(v), depth + 1);
    } catch {
      return false;
    }
  }
  if (Array.isArray(v)) return v.some((x) => hasRedacted(x, depth + 1));
  if (typeof v !== 'object') return false;
  const o = v as Args;
  return o.redacted === true || Object.values(o).some((x) => hasRedacted(x, depth + 1));
}
