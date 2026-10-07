// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// The fake agent's answers that drive the screens (VITE_FIXTURES=1): an assembly named in the
// question opens as a subtree, and "this part" resolves to the part the request's selection
// carries. Never part of a production build.
import type { BomItem, BomNode, Part, Policy } from '../api/types';
import { plmCode } from '../ui/plm';
import type { Script } from './fixture-agent';
import { PLMS } from './fixture-evidence';
import { bomOf } from './fixture-bom';
import { pathsFor } from './fixture-paths';
import { productsFor } from './fixture-products';

/** `forwardedProps.selection` as the browser sends it. */
export interface FixtureSelection {
  product?: string | null;
  root?: string;
  part?: { id: string; plm: string; name: string } | null;
  interface?: { id: string } | null;
}

/** English names of the assemblies the fixture questions name, as their sites spell them. */
const ASSEMBLY_WORDS: Record<string, string[]> = { gearbox: ['Getriebe'], nacelle: ['Gondel', 'nacelle'], rotor: ['Rotor'] };

function assemblyIn(item: BomItem, match: (n: BomNode) => boolean): BomNode | null {
  if (item.redacted) return null;
  if (item.partType === 'ASSEMBLY' && match(item)) return item;
  for (const c of item.children) {
    const hit = assemblyIn(c, match);
    if (hit) return hit;
  }
  return null;
}

/** "Show me the gearbox of the wind turbine": bom finds the assembly, open_subtree loads it. False when the question names no assembly and product the fixtures know. */
export function openAssembly(s: Script, question: string, pol: Policy): boolean {
  const q = question.toLowerCase();
  const word = Object.keys(ASSEMBLY_WORDS).find((w) => q.includes(w));
  const product = productsFor(pol).find((p) => q.includes(p.key.replace(/-/g, ' ')) || q.includes(p.name.toLowerCase()));
  if (!word || !product) return false;
  const tree = bomOf(product.key);
  const node = assemblyIn(tree.root, (n) => ASSEMBLY_WORDS[word].some((w) => n.name.toLowerCase() === w.toLowerCase()));
  s.tool('bom', { product: product.key }, 284, 1420, PLMS, 0.3, pol, { product: product.key, root: { id: tree.root.id } });
  if (!node) {
    s.say(`The bill of materials of ${product.name} holds no ${word} your profile can see.`);
    s.finish();
    return true;
  }
  s.say(`The ${word} of ${product.name} is \`${node.id}\` (${node.name}${node.plm ? `, ${plmCode(node.plm)} PLM` : ''}). Opening its subtree: its parts, and the parts across its interfaces faded as context.`);
  s.call('open_subtree', { root: node.id, product: product.key });
  s.finish();
  return true;
}

/** "Open the subtree of D-36100": bom finds the assembly of the product on screen by id, open_subtree loads it. */
export function openSubtreeOf(s: Script, question: string, product: string, pol: Policy) {
  const tree = bomOf(product);
  const ids: string[] = [];
  assemblyIn(tree.root, (n) => { ids.push(n.id); return false; });
  const id = ids.sort((a, b) => b.length - a.length).find((x) => question.toUpperCase().includes(x.toUpperCase()));
  const node = id ? assemblyIn(tree.root, (n) => n.id === id) : null;
  s.tool('bom', { product }, 284, 1420, PLMS, 0.3, pol, { product, root: { id: tree.root.id } });
  if (!node) {
    s.say('The bill of materials of this product holds no such assembly your profile can see.');
    return s.finish();
  }
  s.say(`\`${node.id}\` is ${node.name}${node.plm ? `, an assembly of the ${plmCode(node.plm)} PLM` : ''}. Opening its subtree: its parts, and the parts across its interfaces faded as context.`);
  s.call('open_subtree', { root: node.id, product });
  s.finish();
}

/** "Which interfaces join A to B?": path_between, then the parts on the first path isolated with their joints. */
export function pathBetween(s: Script, question: string, product: string, parts: Part[], pol: Policy) {
  const q = question.toUpperCase();
  const named = parts
    .map((p) => ({ id: p.id, at: q.indexOf(p.id.toUpperCase()) }))
    .filter((p) => p.at >= 0 && !parts.some((o) => o.id.length > p.id.length && q.includes(o.id.toUpperCase()) && o.id.toUpperCase().includes(p.id.toUpperCase())))
    .sort((a, b) => a.at - b.at);
  if (named.length < 2) {
    s.say('Name the two parts by their ids, and I will find the interfaces that join them.');
    return s.finish(false);
  }
  const [from, to] = [named[0].id, named[1].id];
  const answer = pathsFor(product, from, to, pol, {});
  s.tool('path_between', { product, from, to }, 342, 1650, PLMS, 0.4, pol, { paths: answer.paths.length, notes: answer.notes });
  const first = answer.paths[0];
  if (!first) {
    s.say(answer.notes[0] ?? `No path joins \`${from}\` to \`${to}\`.`);
    return s.finish();
  }
  const joints = first.steps.flatMap((st) => (st.joint ? [st.joint] : []));
  s.say(`\`${from}\` reaches \`${to}\` in **${first.length} steps** over ${joints.map((j) => `**${j.id}**`).join(', ')}${answer.paths.length > 1 ? `; ${answer.paths.length - 1} more path${answer.paths.length === 2 ? '' : 's'} of that length` : ''}.`);
  s.call('isolate_parts', { ids: answer.parts.map((p) => p.id), caption: `From ${from} to ${to}` });
  s.call('render_table', {
    title: `From ${from} to ${to}`,
    columns: ['Interface', 'Status', 'Rules'],
    rows: joints.map((j) => [j.id, j.status, j.rules.join(', ')]),
  });
  s.finish();
}

/** "What is this part": the selection names it; with no part selected, ask which. */
export function selectedPart(s: Script, selection: FixtureSelection | undefined, pol: Policy) {
  const part = selection?.part;
  if (!part) {
    s.say('No part is selected. Click a part in the viewer, or name it by its id, and I will tell you what it is.');
    return s.finish(false);
  }
  s.tool('parts', { root: part.id }, 141, 620, [part.plm.toLowerCase()], 0.1, pol);
  s.say(`\`${part.id}\` is ${part.name}, a part of the ${plmCode(part.plm)} PLM${selection?.product ? ` in product \`${selection.product}\`` : ''}.`);
  s.call('highlight_parts', { ids: [part.id], caption: `${part.name}, the selected part` });
  s.finish();
}

/** "Isolate WURZR61080 as written": the ids exactly as the question writes them, so the viewer's id resolution is what shows them. */
export function isolateAsWritten(s: Script, question: string) {
  const ids = question.replace(/^\s*isolate\s+/i, '').replace(/\s+as written\.?\s*$/i, '').split(/[\s,]+/).filter(Boolean);
  s.say(`Isolating ${ids.join(', ')}.`);
  s.call('isolate_parts', { ids, caption: `Parts ${ids.join(', ')}` });
  s.finish();
}

/**
 * "Show the parts named A and B": find_parts once per word, as the agent calls it once per group, then one isolate_parts
 * with the parts both found; the turn lists every call, the same tool twice included.
 */
export function showNamed(s: Script, question: string, product: string, parts: Part[], pol: Policy) {
  const words = question.replace(/^\s*show the parts named\s+/i, '').replace(/\.\s*$/, '').split(/\s+and\s+/i).filter(Boolean);
  const found = words.map((w) => parts.filter((p) => !p.redacted && [p.name, p.nameEn].some((n) => n?.toLowerCase().includes(w.toLowerCase()))));
  words.forEach((w, i) => s.tool('find_parts', { product, query: w }, 180 + 40 * i, 0, PLMS, 0.2, pol, { groups: [{ match: 'name', parts: found[i].map((p) => ({ id: p.id })) }] }));
  const ids = [...new Set(found.flat().map((p) => p.id))];
  s.say(`Isolating the ${ids.length} parts named ${words.join(' and ')}.`);
  s.call('isolate_parts', { ids, caption: `Parts named ${words.join(' and ')}` });
  s.ran('isolate_parts', 2, 0);
  s.finish();
}
