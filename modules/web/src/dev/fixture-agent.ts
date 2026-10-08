// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// Fake agent for the dev server (VITE_FIXTURES=1): answers the example questions from the same
// seed and policy as the other fixtures, streamed as AG-UI server-sent events through the real
// @ag-ui/client parser. Never part of a production build.
import type { HttpAgentFetchFn } from '@ag-ui/client';
import { PROFILE_HEADER, PROFILES } from '../api/profile';
import { isFeature, isPart, type CandidateMate, type Feature, type FeatureKind, type Interface, type Part, type Policy, type Violation } from '../api/types';
import { RULE_LABEL, rulesOf } from '../check/violations';
import { plmCode } from '../ui/plm';
import { PLMS } from './fixture-evidence';
import { policyFor, redactInterface, visible } from './fixture-policy';
import { interfacesOf, partsOf } from './fixture-products';
import { perUnit } from './fixture-agent-sql';
import { bomWhereUsed, connectorTypes, equivalenceOf, findingOf, mostPins } from './fixture-agent-records';
import { isolateAsWritten, openAssembly, openSubtreeOf, pathBetween, selectedPart, showNamed, type FixtureSelection } from './fixture-agent-view';
import { calls } from './fixtures';
import { FIXTURE_MARKER } from './marker';

type Json = Record<string, unknown>;
interface Frame {
  after: number;
  event: Json;
}
interface ToolStat {
  name: string;
  ms: number;
  sparqlChars: number;
  sqlChars: number;
}

/** The events of one turn: prose streams word by word, tools arrive between paragraphs. */
export class Script {
  readonly frames: Frame[] = [];
  private readonly tools: ToolStat[] = [];
  private words = 0;
  private n = 0;

  constructor(private readonly threadId: string, private readonly runId: string) {
    this.push(0, { type: 'RUN_STARTED', threadId, runId });
  }

  private push(after: number, event: Json) {
    this.frames.push({ after, event });
  }

  say(text: string, pause = 350) {
    const id = `m-${this.n++}`;
    this.push(pause, { type: 'TEXT_MESSAGE_START', messageId: id, role: 'assistant' });
    const words = text.split(/(?<=\s)/);
    words.forEach((w, i) => this.push(i ? 45 : 0, { type: 'TEXT_MESSAGE_CONTENT', messageId: id, delta: w }));
    this.words += words.length;
    this.push(0, { type: 'TEXT_MESSAGE_END', messageId: id });
  }

  /** A tool call, frontend or semantic-layer; returns its id so a result can follow. */
  call(name: string, args: Json): string {
    const id = `t-${this.n++}`;
    this.push(250, { type: 'TOOL_CALL_START', toolCallId: id, toolCallName: name });
    this.push(0, { type: 'TOOL_CALL_ARGS', toolCallId: id, delta: JSON.stringify(args) });
    this.push(0, { type: 'TOOL_CALL_END', toolCallId: id });
    return id;
  }

  result(toolCallId: string, content: unknown) {
    this.push(120, { type: 'TOOL_CALL_RESULT', messageId: `r-${this.n++}`, toolCallId, content: JSON.stringify(content), role: 'tool' });
  }

  /** A semantic-layer tool the agent ran, as atelier.turn reports it. */
  ran(name: string, ms: number, sparqlChars: number, sqlChars = 0) {
    this.tools.push({ name, ms, sparqlChars, sqlChars });
  }

  /**
   * A semantic-layer tool call with its result, as the MCP server answers: compact JSON plus the
   * provenance of the sources read and the policy with the agent as actor.
   */
  tool(name: string, args: Json, ms: number, sparqlChars: number, involved: string[], scale: number, pol: Policy, body: Json = {}) {
    const id = this.call(name, args);
    const c = calls(involved, scale);
    const federationMs = Math.max(...c.map((x) => x.ms)) + 12;
    this.result(id, {
      ...body,
      provenance: { calls: c },
      timings: { totalMs: federationMs + 31, federationMs, validationMs: 27 },
      policy: { profile: pol.profile, releasable: pol.releasable, filter: pol.filter, actor: 'agent' },
    });
    this.ran(name, ms, sparqlChars);
  }

  finish(priced = true) {
    const outputTokens = Math.round(this.words * 1.3) + this.tools.length * 60;
    // As the agent's prompt cache reports a turn: the shared prefix and the earlier steps read, each step's new content written.
    const input = 1400 + this.tools.length * 900;
    const cacheWriteInputTokens = 300 + this.tools.length * 200;
    const inputTokens = 6;
    const cacheReadInputTokens = input - cacheWriteInputTokens - inputTokens;
    const usd = (inputTokens * 2.2 + cacheWriteInputTokens * 2.75 + cacheReadInputTokens * 0.22 + outputTokens * 11) / 1e6;
    const value = { tools: this.tools, usage: { inputTokens, outputTokens, cacheReadInputTokens, cacheWriteInputTokens }, estimatedUsd: priced ? usd : 0 };
    this.push(120, { type: 'CUSTOM', name: 'atelier.turn', value });
    this.push(60, { type: 'RUN_FINISHED', threadId: this.threadId, runId: this.runId });
  }

  error(message: string) {
    this.push(400, { type: 'RUN_ERROR', message });
  }
}

const upper = (q: string) => q.toUpperCase();
/** The part the question names, by native id or by its name as the PLM spells it; the longest id wins over its prefixes. */
const partIn = (q: string, parts: Part[]) =>
  [...parts].sort((a, b) => b.id.length - a.id.length).find((p) => upper(q).includes(upper(p.id))) ?? parts.find((p) => upper(q).includes(upper(p.name)));
const featureIn = (q: string, interfaces: Interface[]): { itf: Interface; f: Feature } | null => {
  for (const itf of interfaces) for (const f of itf.features) if (isFeature(f) && upper(q).includes(upper(f.id))) return { itf, f };
  return null;
};
/** A spare plug the question names: declared on no interface, it is known as the candidate mate of an orphan the profile sees. */
const spareIn = (q: string, seen: Interface[]): { itf: Interface; v: Violation; c: CandidateMate } | null => {
  for (const itf of seen) for (const v of itf.violations) for (const c of v.candidateMates ?? []) if (upper(q).includes(upper(c.id))) return { itf, v, c };
  return null;
};
/** The feature class a question about the UK tables asks for; connectors unless it says otherwise. */
const kindIn = (q: string): FeatureKind => (/fastener/.test(q) ? 'fastener' : /coupling/.test(q) ? 'coupling' : 'plug');
const profileLabel = (id: string) => PROFILES.find((p) => p.id === id)?.label ?? id;

function answer(s: Script, question: string, profile: string, selection: FixtureSelection | undefined) {
  const pol = policyFor(profile);
  // Interface ids repeat across products: the question is about the product on screen.
  const product = selection?.product ?? null;
  const parts = partsOf(product);
  const seen = interfacesOf(product).map((i) => redactInterface(i, pol));
  const q = question.toLowerCase();
  if (q.includes('simulate error')) return s.error('The model endpoint answered HTTP 503 (ThrottlingException).');
  const part = partIn(question, parts);
  const feat = featureIn(question, seen);
  const spare = spareIn(question, seen);
  const itfId = question.match(/\b(IF-\d+)\b/i)?.[1]?.toUpperCase();
  if (/\bthis part\b/.test(q)) return selectedPart(s, selection, pol);
  if (/^isolate .* as written\.?$/.test(q.trim())) return isolateAsWritten(s, question);
  if (/^show the parts named .+ and .+/.test(q.trim()) && product) return showNamed(s, question, product, parts, pol);
  if (/\bshow me\b/.test(q) && openAssembly(s, question, pol)) return;
  if (/\bsubtree of\b/.test(q) && product) return openSubtreeOf(s, question, product, pol);
  if (/per unit/.test(q)) return perUnit(s, seen, pol, kindIn(q));
  const site = question.match(/\b(FR|DE|UK|ES)\b connectors have the most pins/)?.[1];
  if (site) return mostPins(s, seen, site, pol);
  if (/connector types/.test(q)) return connectorTypes(s, seen, pol);
  if (/\bfinding\b/.test(q) && product && findingOf(s, question, product, pol)) return;
  if (/same .* as\b/.test(q) && product && equivalenceOf(s, question, product, pol)) return;
  if (itfId && /\bfail/.test(q) && !/correct/.test(q)) return whyFails(s, seen, itfId, pol);
  if (/\bfail/.test(q) && !/correct/.test(q)) return failing(s, seen, pol);
  if (/\bjoin\b/.test(q) && product) return pathBetween(s, question, product, parts, pol);
  if (/\bwhere\b/.test(q) && product && bomWhereUsed(s, question, product, pol)) return;
  if (part && /where|used|use\b/.test(q)) return whereUsed(s, seen, part, pol);
  if (/releasable|visible|export/.test(q)) return exportStatus(s, question, parts, pol);
  if (feat && /impact|change/.test(q)) return impact(s, seen, feat.itf, feat.f, pol);
  if (spare && /impact|change/.test(q)) return impactOfSpare(s, spare.itf, spare.v, spare.c, pol);
  if (itfId && /sql|evidence|arm|behind/.test(q)) return evidence(s, seen, itfId, question, pol);
  s.say('I can answer about the interfaces, parts, plugs, fasteners and couplings your profile can see, and the rules over them. Name an interface, a part or a feature of this product by its id.');
  s.finish(false);
}

const involvedIn = (itf: Interface) => [...new Set(itf.parts.filter(isPart).map((p) => p.plm))];

function failing(s: Script, seen: Interface[], pol: Policy) {
  const fail = seen.filter((i) => i.status === 'fail');
  const hidden = seen.filter((i) => i.status === 'not-evaluable');
  s.tool('list_interfaces', {}, 412, 1180, PLMS, 1, pol, {
    interfaces: seen.map((i) => ({ id: i.id, label: i.label, status: i.status, rules: rulesOf(i) })),
  });
  s.say(
    `**${fail.length} of ${seen.length} interfaces** break a rule for the ${profileLabel(pol.profile)} profile.` +
      (hidden.length ? ` ${hidden.length} more cannot be evaluated: one of their sides is not visible to your profile, so its rows never left its PLM.` : ''),
  );
  s.call('highlight_interfaces', { ids: fail.map((i) => i.id), caption: `Failing for ${profileLabel(pol.profile)}` });
  s.call('render_table', {
    title: 'Failing interfaces',
    columns: ['Interface', 'Rule', 'Features'],
    rows: fail.map((i) => [i.id, rulesOf(i).map((r) => RULE_LABEL[r]).join(', '), i.violations[0]?.features.join(' / ') ?? '']),
  });
  for (const i of fail.slice(0, 2)) {
    s.tool('interface_check', { interface: i.id }, 380 + i.id.length * 7, 1420, involvedIn(i), 0.15, pol, { interface: { id: i.id, status: i.status } });
  }
  s.say('Each failure names the two mated features from the two PLMs; select an interface to see the records behind it.', 200);
  s.finish();
}

/** interface_check on one interface: the rules it fails for the profile, with their features. */
function whyFails(s: Script, seen: Interface[], itfId: string, pol: Policy) {
  const itf = seen.find((i) => i.id === itfId);
  if (!itf) {
    s.say(`There is no interface ${itfId} in this product.`);
    return s.finish();
  }
  s.tool('interface_check', { interface: itf.id }, 380, 1420, involvedIn(itf), 0.15, pol, { interface: { id: itf.id, status: itf.status } });
  if (itf.status !== 'fail') {
    s.say(`**${itf.id}** (${itf.label}) is ${itf.status === 'pass' ? 'passing' : 'not evaluable'} for the ${profileLabel(pol.profile)} profile.`);
    return s.finish();
  }
  s.say(`**${itf.id}** (${itf.label}) fails the ${rulesOf(itf).map((r) => RULE_LABEL[r]).join(', ')} rule${rulesOf(itf).length === 1 ? '' : 's'} for the ${profileLabel(pol.profile)} profile.`);
  s.call('highlight_interfaces', { ids: [itf.id], caption: `${itf.id}, failing` });
  s.call('render_table', {
    title: `Why ${itf.id} fails`,
    columns: ['Rule', 'Features', 'Message'],
    rows: itf.violations.map((v) => [RULE_LABEL[v.rule], v.features.join(' / '), v.message]),
  });
  s.finish();
}

function whereUsed(s: Script, seen: Interface[], part: Part, pol: Policy) {
  const shown = visible(pol, part);
  // A hidden part comes back as { redacted, plm } and nothing else: no id, no tag values.
  s.tool('where_used', { part: part.id }, 236, 640, shown ? [part.plm] : [], 0.3, pol, { part: shown ? part.id : { redacted: true, plm: part.plm } });
  if (!shown) {
    s.say(`Part \`${part.id}\` is not visible to your profile: the ${plmCode(part.plm)} PLM returned it redacted, so I cannot say where it is used or what it is.`);
    return s.finish();
  }
  const on = seen.filter((i) => i.parts.some((p) => isPart(p) && p.id === part.id));
  s.say(`\`${part.id}\` (${part.name}, ${plmCode(part.plm)} PLM) sits on **${on.length} interfaces**.`);
  s.call('highlight_interfaces', { ids: on.map((i) => i.id), caption: `Interfaces of ${part.id}` });
  s.call('render_table', {
    title: `Where ${part.id} is used`,
    columns: ['Interface', 'Mated part', 'Features', 'Status'],
    rows: on.map((i) => {
      const mate = i.parts.find((p) => !(isPart(p) && p.id === part.id));
      return [i.id, mate && isPart(mate) ? `${mate.id} (${plmCode(mate.plm)})` : 'redacted', i.features.filter((f) => isFeature(f) && f.partId === part.id).length, i.status];
    }),
  });
  s.say('Statuses come from the rules that ran on each interface.', 150);
  s.finish();
}

function impact(s: Script, seen: Interface[], itf: Interface, f: Feature, pol: Policy) {
  const shown = seen.find((i) => i.id === itf.id) ?? itf;
  const mine = shown.features.find((x) => isFeature(x) && x.id === f.id);
  // A feature on a hidden part comes back as { redacted, plm }: no id, no values.
  const hidden = !mine || !isFeature(mine);
  s.tool('impact_of_change', { feature: f.id }, 318, 910, involvedIn(shown), 0.2, pol, { feature: hidden ? { redacted: true, plm: f.plm } : f.id, interfaces: [shown.id] });
  if (!mine || !isFeature(mine)) {
    s.say(`Feature \`${f.id}\` is on a part not visible to your profile: the ${plmCode(f.plm)} PLM returned it redacted.`);
    return s.finish();
  }
  const mates = mine.matesWith;
  s.say(
    `\`${f.id}\` is a ${f.kind} on \`${f.partId}\` (${plmCode(f.plm)} PLM), declared by **${itf.id}** (${itf.label}). Changing it touches its mate${mates.length === 1 ? '' : 's'} ${mates.map((m) => `\`${m}\``).join(', ')} on the other side of the interface, and every rule that compares the pair.`,
  );
  s.call('highlight_interfaces', { ids: [itf.id], caption: `Interface of ${f.id}` });
  // The impacted parts carry the mates; the part that changes is their context.
  const impacted = [...new Set(mates.flatMap((m) => {
    const mf = shown.features.find((x) => isFeature(x) && x.id === m);
    return mf && isFeature(mf) && mf.partId !== f.partId ? [mf.partId] : [];
  }))];
  s.call('isolate_parts', { ids: impacted, caption: `Parts a change to ${f.id} touches`, context_ids: [f.partId] });
  const mateRow = (m: string) => {
    const mf = shown.features.find((x) => isFeature(x) && x.id === m);
    return mf && isFeature(mf) ? [m, plmCode(mf.plm), mf.partId, shown.status] : [m, '?', 'redacted', shown.status];
  };
  s.call('render_table', {
    title: `Impact of a change to ${f.id}`,
    columns: ['Feature', 'PLM', 'Part', 'Current status'],
    rows: [[f.id, plmCode(f.plm), f.partId, shown.status], ...mates.map(mateRow)],
  });
  s.finish();
}

/** impact_of_change on a spare plug: it mates with nothing, so no rule reads it until a link brings it onto its orphan's interface. */
function impactOfSpare(s: Script, itf: Interface, v: Violation, c: CandidateMate, pol: Policy) {
  s.tool('impact_of_change', { feature: c.id }, 318, 910, [c.plm], 0.2, pol, { feature: c.id, interfaces: [] });
  s.say(
    `\`${c.id}\` is a ${c.connectorType ? `${c.connectorType} ` : ''}plug on \`${c.part}\` (${plmCode(c.plm)} PLM) declared on no interface and mated to nothing, so no rule compares it today. ` +
      `It is the candidate mate "Publish link" offers for the orphan \`${v.features[0]}\` on **${itf.id}** (${itf.label}): once linked, the connector and position rules of ${itf.id} read it.`,
  );
  s.call('highlight_interfaces', { ids: [itf.id], caption: `Candidate mate on ${itf.id}` });
  s.finish();
}

function evidence(s: Script, seen: Interface[], itfId: string, question: string, pol: Policy) {
  const itf = seen.find((i) => i.id === itfId);
  if (!itf) {
    s.say(`There is no interface ${itfId}.`);
    return s.finish();
  }
  const arm = question.match(/\b(fr|de|uk|es|core)\b/i)?.[1]?.toLowerCase() ?? itf.parts.find(isPart)?.plm.toLowerCase() ?? 'core';
  const side = itf.parts.find((p) => p.plm.toLowerCase() === arm);
  if (side && !isPart(side)) {
    s.tool('evidence', { interface: itfId, endpoint: `ontop-${arm}` }, 190, 0, [], 0.15, pol, { interfaceId: itfId, arm: { redacted: true, plm: arm } });
    s.say(`The ${arm.toUpperCase()} side of ${itfId} is not visible to your profile, so there is no SQL to show: the query service sent \`ontop-${arm}\` no request for it.`);
    return s.finish();
  }
  s.tool('evidence', { interface: itfId, endpoint: `ontop-${arm}` }, 268, 1180, [arm], 0.15, pol, { interfaceId: itfId, arm: `ontop-${arm}` });
  s.say(`Opening the evidence from \`ontop-${arm}\` for **${itfId}**: the SERVICE arm the query service sent, and the SQL Ontop generated from the R2RML mapping over the ${arm}_plm tables.`);
  s.call('open_evidence', { interface: itfId, endpoint: `ontop-${arm}`, tab: 'sql' });
  s.say(`That SQL is what the ${arm.toUpperCase()} database ran; the rows it returned are under Native rows.`, 200);
  s.finish();
}

/** export_status: the tag of a visible part; a hidden part comes back as { redacted, plm } with visible false and nothing else. */
function exportStatus(s: Script, question: string, parts: Part[], pol: Policy) {
  const part = partIn(question, parts);
  if (!part) {
    s.say('Name the part by its id or its name, and I will read its export-control status.');
    return s.finish(false);
  }
  const shown = visible(pol, part);
  s.tool('export_status', { part: part.id }, 172, 0, shown ? [part.plm] : [], 0.1, pol,
    shown
      ? { part: part.id, plm: part.plm, jurisdiction: part.jurisdiction, releasableTo: part.releasableTo, taggedBy: part.taggedBy, visible: true, cadAvailable: part.cadUrl !== null }
      : { part: { redacted: true, plm: part.plm }, visible: false });
  if (!shown) {
    s.say(`That ${plmCode(part.plm)} part is not visible to your profile: \`export_status\` answers \`visible: false\` and the ${plmCode(part.plm)} PLM returns it redacted, without its tag.`);
    return s.finish();
  }
  s.say(
    `\`${part.id}\` (${part.name}, ${plmCode(part.plm)} PLM) is tagged **${part.jurisdiction}**, releasable to **${part.releasableTo}**, by the ${plmCode(part.taggedBy ?? part.plm)} PLM: visible to the ${profileLabel(pol.profile)} profile${part.cadUrl ? ', CAD available' : ', CAD not published'}.`,
  );
  s.finish();
}

/** Serves POST /agent/invocations from the seed: one AG-UI run per request, as text/event-stream. */
export const fixtureAgentFetch: HttpAgentFetchFn = async (_url, init) => {
  const input = JSON.parse(String(init.body ?? '{}')) as {
    threadId?: string; runId?: string; messages?: { role: string; content?: unknown }[]; forwardedProps?: { selection?: FixtureSelection };
  };
  const profile = new Headers(init.headers).get(PROFILE_HEADER) ?? 'unknown';
  const last = [...(input.messages ?? [])].reverse().find((m) => m.role === 'user');
  const question = typeof last?.content === 'string' ? last.content : '';
  const s = new Script(input.threadId ?? 'thread', input.runId ?? 'run');
  answer(s, question, profile, input.forwardedProps?.selection);
  const enc = new TextEncoder();
  const body = new ReadableStream<Uint8Array>({
    async start(c) {
      c.enqueue(enc.encode(`: ${FIXTURE_MARKER}\n\n`));
      for (const f of s.frames) {
        if (init.signal?.aborted) break;
        if (f.after) await new Promise((r) => setTimeout(r, f.after));
        c.enqueue(enc.encode(`data: ${JSON.stringify(f.event)}\n\n`));
      }
      c.close();
    },
  });
  return new Response(body, { status: 200, headers: { 'content-type': 'text/event-stream' } });
};
