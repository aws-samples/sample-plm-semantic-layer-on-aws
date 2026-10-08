// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// The fake agent's answers read from one product's records (VITE_FIXTURES=1): the connectors with the most pins, the
// connector types shared across sites, a part's release finding, the stocking line of an equivalence group and a
// part's parents in the bill of materials. Never part of a production build.
import { isFeature, isPart, type BomItem, type Feature, type Interface, type Policy } from '../api/types';
import { plmCode } from '../ui/plm';
import type { Script } from './fixture-agent';
import { assemblyParts, bomOf } from './fixture-bom';
import { PLMS } from './fixture-evidence';
import { withMeshFindings } from './fixture-paths';
import { redactParts } from './fixture-policy';
import { partsOf } from './fixture-products';
import { equivalentsOf } from './fixture-purchasing';

const plugsOf = (seen: Interface[]): Feature[] => seen.flatMap((i) => i.features).filter(isFeature).filter((f) => f.kind === 'plug');

/** list_interfaces, then the site's connectors by pin count. */
export function mostPins(s: Script, seen: Interface[], site: string, pol: Policy) {
  const plugs = plugsOf(seen).filter((f) => plmCode(f.plm) === site.toUpperCase()).sort((a, b) => Number(b.properties.pinCount) - Number(a.properties.pinCount) || a.id.localeCompare(b.id));
  s.tool('list_interfaces', {}, 412, 1180, [site.toLowerCase()], 0.5, pol, { plugs: plugs.length });
  if (!plugs.length) {
    s.say(`No ${site.toUpperCase()} connectors of this product are visible to your profile.`);
    return s.finish();
  }
  const top = plugs[0];
  s.say(`The ${site.toUpperCase()} connector with the most pins is \`${top.id}\` on \`${top.partId}\`: ${top.properties.pinCount} pins, ${top.properties.connectorType}.`);
  s.call('render_table', { title: `${site.toUpperCase()} connectors by pin count`, columns: ['Plug', 'Part', 'Connector type', 'Pins'], rows: plugs.slice(0, 8).map((f) => [f.id, f.partId, f.properties.connectorType, f.properties.pinCount]) });
  s.finish();
}

/** The connector types on plugs of more than one site, with the plugs of each. */
export function connectorTypes(s: Script, seen: Interface[], pol: Policy) {
  const byType = new Map<string, Feature[]>();
  for (const f of plugsOf(seen)) byType.set(String(f.properties.connectorType), [...(byType.get(String(f.properties.connectorType)) ?? []), f]);
  const shared = [...byType].filter(([, fs]) => new Set(fs.map((f) => plmCode(f.plm))).size > 1).sort((a, b) => b[1].length - a[1].length || a[0].localeCompare(b[0]));
  s.tool('list_interfaces', {}, 412, 1180, PLMS, 0.5, pol, { connectorTypes: shared.length });
  s.say(shared.length
    ? `**${shared.length} connector types** are used on parts of more than one PLM.`
    : 'No connector type of this product is used on parts of more than one PLM your profile can see.');
  if (shared.length) {
    s.call('render_table', { title: 'Connector types across sites', columns: ['Connector type', 'Sites', 'Plugs'], rows: shared.map(([t, fs]) => [t, [...new Set(fs.map((f) => plmCode(f.plm)))].sort().join(', '), fs.length]) });
  }
  s.finish();
}

/** parts, then the findings the part carries. */
export function findingOf(s: Script, question: string, product: string, pol: Policy): boolean {
  const listed = [...withMeshFindings(product, redactParts(partsOf(product), pol), pol), ...assemblyParts(product)].filter(isPart);
  const q = question.toUpperCase();
  const part = [...listed].sort((a, b) => b.id.length - a.id.length).find((p) => q.includes(p.id.toUpperCase()));
  if (!part) return false;
  s.tool('parts', { product }, 141, 620, [part.plm.toLowerCase()], 0.3, pol, { part: part.id, findings: part.findings ?? [] });
  const findings = part.findings ?? [];
  s.say(findings.length
    ? `\`${part.id}\` (${part.name}, ${plmCode(part.plm)} PLM) carries ${findings.map((f) => `**${f.rule}**: ${f.message}`).join('; ')}.`
    : `\`${part.id}\` carries no finding for your profile.`);
  s.call('highlight_parts', { ids: [part.id], caption: `Findings of ${part.id}` });
  s.finish();
  return true;
}

/** equivalent_parts, then the group of the part the question names and the stock lines it would save. */
export function equivalenceOf(s: Script, question: string, product: string, pol: Policy): boolean {
  const q = question.toUpperCase();
  const group = equivalentsOf(product).groups.find((g) => g.members.some((m) => q.includes(m.id.toUpperCase())));
  if (!group) return false;
  s.tool('equivalent_parts', { product }, 296, 1380, PLMS, 0.3, pol, { groups: 1 });
  const { stockLines, stockLinesOnceConfirmed, sites } = group.stocking;
  s.say(`${group.members.length} parts across ${sites} sites are one ${group.classLabel.toLowerCase()}: ${group.members.map((m) => `\`${m.id}\` (${plmCode(m.plm)})`).join(', ')}. Confirmed as one item they would take ${stockLinesOnceConfirmed} stock line instead of ${stockLines}.`);
  s.call('highlight_parts', { ids: group.members.map((m) => m.id), caption: `One ${group.classLabel.toLowerCase()}` });
  s.finish();
  return true;
}

/** bom_where_used: the parents of a part in the product's bill of materials, when it has more than one. */
export function bomWhereUsed(s: Script, question: string, product: string, pol: Policy): boolean {
  const q = question.toUpperCase();
  const parents = new Map<string, { parent: string; quantity: number }[]>();
  const walk = (node: BomItem) => {
    if (node.redacted) return;
    for (const c of node.children) {
      if (c.redacted) continue;
      parents.set(c.id, [...(parents.get(c.id) ?? []), { parent: node.id, quantity: c.quantity }]);
      walk(c);
    }
  };
  try {
    walk(bomOf(product).root);
  } catch {
    return false;
  }
  const id = [...parents.keys()].sort((a, b) => b.length - a.length).find((x) => q.includes(x.toUpperCase()));
  const under = id ? parents.get(id)! : [];
  if (!id || under.length < 2) return false;
  s.tool('bom_where_used', { part: id, product }, 238, 980, PLMS, 0.3, pol, { part: id, parents: under.length });
  s.say(`\`${id}\` is built into **${under.length} assemblies**: ${under.map((u) => `\`${u.parent}\` (${u.quantity} each)`).join(', ')}.`);
  s.call('highlight_parts', { ids: [id], caption: `Where ${id} is used` });
  s.finish();
  return true;
}
