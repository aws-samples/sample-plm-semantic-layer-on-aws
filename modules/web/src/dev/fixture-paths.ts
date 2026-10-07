// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// The path and flow answers of the fixtures (docs/contract.md, "Paths through a product"): the shortest interface
// paths between two parts over the fixture interfaces, and the walk along the functional edges the product files
// record (`extended.functionalEdges`, with the gears' `extended.gear` and the `ratedSpeeds`), as the query service
// computes them. Never part of a production build.
import ornithopter from '../../../../data/products/ornithopter.json';
import windTurbine from '../../../../data/products/wind-turbine.json';
import rover from '../../../../data/products/rover.json';
import type { FlowKind, FlowResponse, Gear, PathsResponse, Stage, Step, StepPart } from '../api/pathTypes';
import type { Interface, Part, PartEntry, Policy } from '../api/types';
import { isPart } from '../api/types';
import { interfacesOf, partsOf } from './fixture-products';
import { redactInterface, visible } from './fixture-policy';
import { FIXTURE_MARKER } from './marker';

interface Edge { from: string; to: string; flow: FlowKind; via?: string; reaction?: string }
interface FunctionalFile {
  product: { key: string };
  parts: { id: string; plm: string; extended?: { gear?: { teeth: number; module: string } } | null }[];
  extended: { functionalEdges?: Edge[]; ratedSpeeds?: { part: string; rpm: string }[] };
}
const FILES = [ornithopter, windTurbine, rover] as unknown as FunctionalFile[];
const fileOf = (product: string) => FILES.find((f) => f.product.key === product);

const MAX_LENGTH = 12;
const MAX_PATHS = 5;
const HIDDEN_NOTE = 'The walk reaches a part not visible to your profile and does not go past it.';

function gearOf(product: string, id: string): { teeth: number; mm: number } | null {
  const part = fileOf(product)?.parts.find((p) => p.id === id);
  const g = part?.extended?.gear;
  if (!g) return null;
  const module = Number(g.module);
  return { teeth: g.teeth, mm: part.plm === 'UK' ? module * 25.4 : module };
}

/** The visible parts with their gear attributes, and a lookup of what a step may name. */
function viewOf(product: string, pol: Policy) {
  const shown = new Map<string, Part>();
  for (const p of partsOf(product)) {
    if (!visible(pol, p)) continue;
    const g = fileOf(product)?.parts.find((x) => x.id === p.id)?.extended?.gear;
    shown.set(p.id, g ? { ...p, toothCount: g.teeth, gearModule: { value: Number(g.module), unit: p.plm === 'UK' ? 'IN' : 'MilliM', mm: gearOf(product, p.id)!.mm } } : p);
  }
  const plmOf = new Map(partsOf(product).map((p) => [p.id, p.plm]));
  const ref = (id: string): StepPart => (shown.has(id) ? { id, plm: plmOf.get(id)! } : { redacted: true, plm: plmOf.get(id) ?? 'de' });
  const joints = new Map(interfacesOf(product).map((i) => [i.id, redactInterface(i, pol)]));
  const joint = (id: string | undefined) => {
    const i: Interface | undefined = id ? joints.get(id) : undefined;
    return i ? { id: i.id, label: i.label, status: i.status, rules: [...new Set(i.violations.map((v) => v.rule))] } : undefined;
  };
  return { shown, ref, joint, joints };
}

function noPart(id: string): never {
  throw new Error(`HTTP 404: no item ${id} (${FIXTURE_MARKER})`);
}

export function pathsFor(product: string, from: string, to: string, pol: Policy, envelope: object): PathsResponse {
  const { shown, ref, joint, joints } = viewOf(product, pol);
  const all = new Set(partsOf(product).map((p) => p.id));
  if (!all.has(from)) noPart(from);
  if (!all.has(to)) noPart(to);
  const adjacent = new Map<string, { iface: string; to: string }[]>();
  for (const i of joints.values()) {
    const ids = interfacesOf(product).find((x) => x.id === i.id)!.parts.filter(isPart).map((p) => p.id);
    if (ids.length !== 2) continue;
    for (const [a, b] of [[ids[0], ids[1]], [ids[1], ids[0]]]) adjacent.set(a, [...(adjacent.get(a) ?? []), { iface: i.id, to: b }]);
  }
  const shortest = (visibleOnly: boolean) => {
    const depth = new Map([[from, 0]]);
    const into = new Map<string, { iface: string; from: string }[]>();
    const queue = [from];
    while (queue.length) {
      const part = queue.shift()!;
      const d = depth.get(part)!;
      if (d === MAX_LENGTH || part === to || (visibleOnly && part !== from && !shown.has(part))) continue;
      for (const e of [...(adjacent.get(part) ?? [])].sort((x, y) => x.iface.localeCompare(y.iface))) {
        const seen = depth.get(e.to);
        if (seen === undefined) { depth.set(e.to, d + 1); queue.push(e.to); }
        if (seen === undefined || seen === d + 1) into.set(e.to, [...(into.get(e.to) ?? []), { iface: e.iface, from: part }]);
      }
    }
    const out: { iface: string; from: string; to: string }[][] = [];
    const walk = (part: string, tail: { iface: string; from: string; to: string }[]) => {
      if (out.length === MAX_PATHS) return;
      if (part === from) { out.push(tail); return; }
      for (const e of into.get(part) ?? []) walk(e.from, [{ iface: e.iface, from: e.from, to: part }, ...tail]);
    };
    if (depth.has(to)) walk(to, []);
    return out;
  };
  const found = shown.has(from) && shown.has(to) ? shortest(true) : [];
  const any = shortest(false);
  const notes = !shown.has(from) || !shown.has(to) ? ['An end of the path is a part not visible to your profile.']
    : found.length === 0 && any.length ? [`Every path of at most ${MAX_LENGTH} steps between them runs through a part not visible to your profile.`]
      : any.length && found.length && any[0].length < found[0].length ? [`A shorter path of ${any[0].length} steps runs through a part not visible to your profile.`]
        : any.length === 0 ? [`No path of at most ${MAX_LENGTH} steps joins them through the product's interfaces.`] : [];
  const on = new Set(found.flat().flatMap((e) => [e.from, e.to]));
  return {
    product, from: shown.get(from) ?? { redacted: true, plm: ref(from).plm }, to: shown.get(to) ?? { redacted: true, plm: ref(to).plm },
    maxLength: MAX_LENGTH, maxPaths: MAX_PATHS,
    paths: found.map((p) => ({ length: p.length, steps: p.map((e): Step => ({ from: ref(e.from), to: ref(e.to), joint: joint(e.iface) })) })),
    notes, parts: [...on].flatMap((id) => (shown.has(id) ? [shown.get(id)!] : [])), ...envelope,
  } as PathsResponse;
}

export function flowFor(product: string, from: string, flow: FlowKind | null, up: boolean, pol: Policy, envelope: object): FlowResponse {
  const { shown, ref, joint } = viewOf(product, pol);
  if (!partsOf(product).some((p) => p.id === from)) noPart(from);
  const edges = fileOf(product)?.extended.functionalEdges ?? [];
  const gear = (id: string): Gear | null => {
    const g = shown.has(id) ? gearOf(product, id) : null;
    return g ? { id, plm: ref(id).plm, teeth: g.teeth, moduleMm: g.mm } : null;
  };
  const stageOf = (e: Edge): Stage | undefined => {
    const a = gear(e.from);
    const b = gear(e.to);
    const r = e.reaction ? gear(e.reaction) : null;
    if (e.flow !== 'mechanical' || !a?.teeth || !b?.teeth || (e.reaction && !r?.teeth)) return undefined;
    return { driver: a, driven: b, ...(r ? { reaction: r } : {}), ratio: r ? b.teeth / (r.teeth! + b.teeth) : b.teeth / a.teeth };
  };
  const walked: Edge[] = [];
  const reached = [from];
  const by = new Map<string, Edge>();
  const seen = new Set([from]);
  let hidden = false;
  const queue = shown.has(from) ? [from] : [];
  while (queue.length) {
    const part = queue.shift()!;
    for (const e of edges) {
      if ((up ? e.to : e.from) !== part || (flow && e.flow !== flow)) continue;
      walked.push(e);
      const next = up ? e.from : e.to;
      if (seen.has(next)) continue;
      seen.add(next);
      if (!shown.has(next)) { hidden = true; continue; }
      by.set(next, e);
      reached.push(next);
      queue.push(next);
    }
  }
  const ratio = new Map<string, number>([[from, 1]]);
  const stages = new Map<string, Stage[]>([[from, []]]);
  if (!up) {
    for (const part of reached.slice(1)) {
      const e = by.get(part)!;
      if (e.flow !== 'mechanical' || !ratio.has(e.from)) continue;
      const s = stageOf(e);
      ratio.set(part, ratio.get(e.from)! * (s?.ratio ?? 1));
      stages.set(part, s ? [...stages.get(e.from)!, s] : stages.get(e.from)!);
    }
  }
  let end: string | null = null;
  for (const p of reached) if ((stages.get(p)?.length ?? 0) > (end ? stages.get(end)!.length : 0)) end = p;
  const speed = new Map((fileOf(product)?.extended.ratedSpeeds ?? []).map((s) => [s.part, Number(s.rpm)]));
  const rated = reached.filter((p) => speed.has(p) && ratio.has(p));
  const checks = rated.slice(1).map((p) => {
    const predicted = (speed.get(rated[0])! * ratio.get(rated[0])!) / ratio.get(p)!;
    const off = Math.abs(predicted - speed.get(p)!) / speed.get(p)!;
    const holds = off <= 0.01;
    return {
      from: rated[0], fromPlm: ref(rated[0]).plm, fromRpm: speed.get(rated[0])!, to: p, toPlm: ref(p).plm, ratedRpm: speed.get(p)!, predictedRpm: predicted, holds,
      text: `${rated[0]} at ${speed.get(rated[0])} rpm through the gear train turns ${p} at ${predicted.toFixed(1)} rpm; rated ${speed.get(p)} rpm: ${holds ? 'within 1 %' : `off by ${(off * 100).toFixed(1)} %`}.`,
    };
  });
  const chain = end ? stages.get(end)! : [];
  const sites = [...new Set(chain.flatMap((s) => [s.driver.plm, s.driven.plm]).map((p) => p.toUpperCase()))].sort();
  const notes = [...(hidden ? [HIDDEN_NOTE] : []), ...(walked.length === 0 ? [`No functional edge${flow ? ` of flow ${flow}` : ''}${up ? ' ends at ' : ' starts at '}${from}.`] : [])];
  return {
    product, from: shown.get(from) ?? { redacted: true, plm: ref(from).plm }, ...(flow ? { flow } : {}), direction: up ? 'up' : 'down', maxSteps: 60,
    steps: walked.map((e): Step => ({ from: ref(e.from), to: ref(e.to), joint: joint(e.via), flow: e.flow, ...(e.reaction ? { reaction: ref(e.reaction) } : {}), ...(stageOf(e) ? { stage: stageOf(e) } : {}) })),
    ...(end ? {
      ratio: {
        to: end, toPlm: ref(end).plm, ratio: ratio.get(end)!, speedUp: 1 / ratio.get(end)!, stages: chain,
        text: `Gear ratio ${ratio.get(end)!.toFixed(5)} from ${from} to ${end} over ${chain.length} stage${chain.length === 1 ? '' : 's'} of ${sites.join(' and ')} gears: ${end} turns ${(1 / ratio.get(end)!).toFixed(2)} times per turn of ${from}.`,
      },
    } : {}),
    checks, notes,
    parts: [...new Set([...reached, ...edges.filter((e) => walked.includes(e) && e.reaction).map((e) => e.reaction!)])]
      .flatMap((id) => (shown.has(id) ? [withMeshFinding(shown.get(id)!, edges, gear)] : [])),
    ...envelope,
  } as FlowResponse;
}

/**
 * The parts with the meshModule findings the parts answer carries: a gear driven by a gear of another module, both among
 * `parts` (a subtree's gears only, as the query service reads them) and visible to the profile.
 */
export function withMeshFindings<T extends PartEntry>(product: string | null, parts: T[], pol: Policy): T[] {
  const edges = (product ? fileOf(product)?.extended.functionalEdges : undefined) ?? [];
  if (!product || edges.length === 0) return parts;
  const { shown, ref } = viewOf(product, pol);
  const listed = new Set(parts.flatMap((p) => (isPart(p) ? [p.id] : [])));
  const gear = (id: string): Gear | null => {
    const g = shown.has(id) && listed.has(id) ? gearOf(product, id) : null;
    return g ? { id, plm: ref(id).plm, teeth: g.teeth, moduleMm: g.mm } : null;
  };
  return parts.map((p) => (isPart(p) && shown.has(p.id) ? withMeshFinding(p, edges, gear) as T : p));
}

/** The meshModule finding the shapes give a gear driven, by a mechanical edge, by a gear of another module. */
function withMeshFinding(part: Part, edges: Edge[], gear: (id: string) => Gear | null): Part {
  const mine = gear(part.id);
  const found = edges.filter((e) => e.to === part.id && e.flow === 'mechanical').flatMap((e) => {
    const theirs = gear(e.from);
    if (!mine?.moduleMm || !theirs?.moduleMm || Math.abs(mine.moduleMm - theirs.moduleMm) <= 0.01) return [];
    return [{
      rule: 'meshModule',
      message: `${part.plm.toUpperCase()} ${part.id} ${part.name} has module ${mine.moduleMm.toFixed(1)} mm but is driven by ${theirs.plm.toUpperCase()} ${theirs.id}, module ${theirs.moduleMm.toFixed(1)} mm: meshing gears need one module`,
      value: { plm: theirs.plm, kind: 'part', id: theirs.id },
    }];
  });
  return found.length ? { ...part, findings: [...(part.findings ?? []), ...found] } : part;
}
