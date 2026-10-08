// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { MarkerType } from '@xyflow/react';
import type { Turn } from '../ask/types';
import { cssColour } from '../ui/plm';
import { changePaths, type ChangePaths } from './changePaths';
import { AGENT_REQUEST, callsOf, plural, toolMs, toolsOf, type Drawn } from './drawn';
import type { WireData, WireEdge, WireTone } from './edges';
import type { CardNode, LayerNode, NoteNode, Tone } from './nodes';
import { COL_W, SLOT_GAP, SLOT_H, SLOT_TOP, type Link, type Topology } from './topology';

const INK = '#15202a';
const INK_2 = '#394855';
const RULE = '#cdd4d9';
const RULE_2 = '#e2e7ea';
/** Tool lines on the agent's wire before the rest is counted. */
const TOOL_LINES = 5;

/** Every link upstream and downstream of one component, and the components they touch. */
function pathOf(all: Link[], id: string) {
  const links = new Set<string>();
  const nodes = new Set<string>([id]);
  const walk = (from: string, dir: 'up' | 'down') => {
    for (const l of all) {
      const next = dir === 'down' ? (l.from === from ? l.to : null) : (l.to === from ? l.from : null);
      if (next === null || links.has(l.id)) continue;
      links.add(l.id);
      nodes.add(next);
      walk(next, dir);
    }
  };
  walk(id, 'up');
  walk(id, 'down');
  return { links, nodes };
}

type Tag = Pick<WireData, 'label' | 'labelKind'>;

/** The agent lane of a question: browser to CloudFront to the agent to the gateway to the MCP server, with the tools that ran. */
function lane(turn: Turn): Map<string, Tag> {
  const tools = toolsOf(turn);
  const lines = tools.slice(0, TOOL_LINES).map((t) => `${t.name} ${t.ms} ms`);
  if (tools.length > TOOL_LINES) lines.push(`+${tools.length - TOOL_LINES} more`);
  return new Map<string, Tag>([
    ['browser>cloudfront', { label: `${AGENT_REQUEST} · SSE`, labelKind: 'live' }],
    ['cloudfront>agent', { label: '/agent/*', labelKind: 'live' }],
    ['agent>apigw', { label: lines.length ? lines.join('\n') : 'answered without a tool', labelKind: 'stack' }],
    ['apigw>query', { label: `/api/query/mcp · actor ${turn.actor ?? 'agent'}`, labelKind: 'live' }],
  ]);
}

/** What the cards say about the request drawn: the query's timings, the agent's tools, or what each store took from the change feed. */
function liveText(variant: string, drawn: Drawn | null, lit: ChangePaths | null): string | undefined {
  if (!drawn) return undefined;
  if (drawn.kind === 'click') {
    const t = drawn.answer.envelope.timings;
    return variant === 'query' ? `${t.totalMs} ms total\nfederation ${t.federationMs} ms · SHACL ${t.validationMs} ms` : undefined;
  }
  if (drawn.kind === 'change') {
    if (!lit) return undefined;
    const { values, links, cad } = lit.counts;
    const g = drawn.changes.graph.triples;
    switch (variant) {
      case 'neptune': return links || cad ? [links ? `links +${2 * links}` : '', cad ? `file index +${cad}` : ''].filter(Boolean).join(' · ') : `${g.links} links · ${g.fileindex} file index`;
      case 'eventbridge': return values + links + cad ? `${plural(values + links + cad, 'event')} put` : undefined;
      case 'loader': return values + cad ? `${plural(values + cad, 'event')} delivered` : undefined;
      case 'changelog': return `${plural(values, 'row')}`;
      default: return undefined;
    }
  }
  const n = toolsOf(drawn.turn).length;
  if (variant === 'agent') return `${plural(n, 'tool call')} · ${toolMs(drawn.turn)} ms in tools`;
  if (variant === 'query') {
    const asked = drawn.turn.calls.filter((c) => c.requests > 0);
    const triples = asked.reduce((s, c) => s + c.triples, 0);
    return `MCP server · ${plural(n, 'tool call')}\n${triples} triples from ${plural(asked.length, 'source')}`;
  }
  return undefined;
}

export function buildGraph(topo: Topology, selected: string | null, drawn: Drawn | null, plms: string[]) {
  const lit = drawn?.kind === 'change' ? changePaths(drawn.changes, topo) : null;
  const links = topo.links;
  const path = selected ? pathOf(links, selected) : null;
  // With the feed drawn and nothing selected, the lit paths are the selection.
  const focus = !path && lit !== null && lit.nodes.size > 0;
  const calls = new Map(callsOf(drawn).map((c) => [c.endpoint, c]));
  const agentLane = drawn?.kind === 'question' ? lane(drawn.turn) : null;

  const nodes: (CardNode | LayerNode | NoteNode)[] = topo.layers.map((l, i) => ({
    id: `layer-${i}`, type: 'layer', position: { x: -180, y: l.y - 8 }, data: { label: l.label },
    width: 140, height: 16, draggable: false, selectable: false, focusable: false, className: 'flayer-node',
  }));
  for (const c of topo.components) {
    const tone: Tone = path
      ? c.id === selected ? 'selected' : path.nodes.has(c.id) ? 'on' : 'dim'
      : focus ? (lit.nodes.has(c.id) ? 'on' : 'dim') : 'plain';
    nodes.push({
      id: c.id, type: 'card', position: { x: c.x, y: c.y }, data: { c, tone, live: liveText(c.variant, drawn, lit), plms },
      width: c.w, height: c.h, draggable: false, selectable: false,
      // The atelier_core and demo_change cards sit inside the Aurora cluster card and must take the click.
      zIndex: c.variant === 'coredb' || c.variant === 'changelog' ? 2 : c.variant === 'aurora' ? 0 : 1,
    });
  }
  // A corrected value changed a PLM's database and no graph: said under that database's slot.
  const aurora = topo.components.find((c) => c.variant === 'aurora');
  for (const plm of lit?.corrected ?? []) {
    const svc = topo.components.find((c) => c.id === `plm-${plm}`);
    if (!svc || !aurora) continue;
    nodes.push({
      id: `note-${plm}`, type: 'note', position: { x: svc.x, y: aurora.y + SLOT_TOP + SLOT_H + SLOT_GAP + 8 }, data: { text: 'no graph change' },
      width: COL_W, height: 22, draggable: false, selectable: false, focusable: false, className: 'fnote-node', zIndex: 2,
    });
  }

  const edges: WireEdge[] = links.map((l) => {
    const target = topo.components.find((c) => c.id === l.to)!;
    const call = l.from === 'query' ? calls.get(target.endpoint ?? '') : undefined;
    // A source the answer did not need (0 requests) is drawn like a plain wire, tagged as such.
    const asked = call !== undefined && call.requests > 0;
    const laneTag = agentLane?.get(l.id);
    const litTag = lit?.wires.get(l.id);
    const onPath = path ? path.links.has(l.id) : true;
    // A read of the feed is on the lit picture but thinner than the paths the changes took.
    const tone: WireTone = !onPath ? 'off' : litTag?.read ? 'on' : asked || laneTag || litTag ? 'live' : path ? 'on' : focus ? 'off' : 'plain';
    const colour = tone === 'live' || litTag?.read ? (litTag?.colour ?? (target.plm ? cssColour(target.plm) : INK_2)) : tone === 'on' ? INK : tone === 'off' ? RULE_2 : RULE;
    const tag: Tag = !onPath
      ? {}
      : asked
        ? { label: `${call.triples} triples, ${call.requests} req, ${call.ms} ms`, labelKind: 'live' }
        : laneTag ?? litTag ?? { label: call ? 'not needed' : path && !l.tagless ? l.protocol : undefined, labelKind: 'protocol' };
    return {
      id: l.id, type: 'wire', source: l.from, sourceHandle: l.fromHandle ?? 'out', target: l.to, targetHandle: l.toHandle ?? 'in',
      animated: tone === 'live' || tone === 'on', zIndex: tone === 'off' ? 0 : tone === 'plain' ? 1 : 2,
      className: `wire tone-${tone}`, focusable: false, selectable: false,
      data: { route: l.route, tone, colour, ...tag },
      markerEnd: { type: MarkerType.ArrowClosed, color: colour, width: 14, height: 14 },
    };
  });
  return { nodes, edges };
}
