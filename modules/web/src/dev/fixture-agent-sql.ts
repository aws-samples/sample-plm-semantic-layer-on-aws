// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// The fake agent's text-to-SQL answer over the UK catalogue (VITE_FIXTURES=1): one class of feature
// grouped by the unit its positions are stored in, counted from the same seed and policy as the
// other fixtures. Never part of a production build.
import { isFeature, type FeatureKind, type Interface, type Policy } from '../api/types';
import { plmCode } from '../ui/plm';
import type { Script } from './fixture-agent';
import { FIXTURE_MARKER } from './marker';

/** The UK table of each feature class, its key column and the noun the answer counts. */
const UK_TABLE: Record<FeatureKind, { table: string; key: string; noun: string }> = {
  plug: { table: 'harness_connector', key: 'conn_ref', noun: 'connectors' },
  fastener: { table: 'fastener', key: 'fast_ref', noun: 'fastener groups' },
  coupling: { table: 'hyd_coupling', key: 'cplg_ref', noun: 'couplings' },
};

/** Text-to-SQL over the UK catalogue: one class of feature grouped by the unit its positions are stored in. */
export function perUnit(s: Script, seen: Interface[], pol: Policy, kind: FeatureKind) {
  const { table, key, noun } = UK_TABLE[kind];
  s.ran('catalogue', 61, 0);
  const cat = s.call('catalogue', { plm: 'uk' });
  s.result(cat, { plm: 'UK', entities: ['harness_connector', 'fastener', 'hyd_coupling', 'component'] });
  const features = seen.flatMap((i) => i.features).filter(isFeature).filter((f) => plmCode(f.plm) === 'UK' && f.kind === kind);
  const byUnit = new Map<string, number>();
  for (const f of features) byUnit.set(f.source.unit ?? 'NULL', (byUnit.get(f.source.unit ?? 'NULL') ?? 0) + 1);
  const rows = [...byUnit].sort((a, b) => b[1] - a[1]);
  const query = `SELECT pos_uom, COUNT(*) AS n\nFROM ${table}\nGROUP BY pos_uom\nORDER BY n DESC`;
  const releasable = pol.releasable.map((r) => `'${r}'`).join(', ');
  const sqlExecuted = [
    `-- ${FIXTURE_MARKER}`,
    'SELECT pos_uom, COUNT(*) AS n',
    'FROM (',
    `  SELECT t.* FROM ${table} t`,
    '  JOIN component c ON c.comp_id = t.comp_id',
    `  WHERE c.releasable_to IN (${releasable})`,
    `) AS ${table}`,
    'GROUP BY pos_uom',
    'ORDER BY n DESC',
    'LIMIT 200',
  ].join('\n');
  const sql = s.call('sql', { plm: 'uk', query, purpose: `count ${noun} per unit of measure` });
  s.result(sql, { sqlExecuted, rowCount: rows.length, ms: 148, catalogueUsed: [`${table}.pos_uom`, `${table}.${key}`, 'component.releasable_to'], rows });
  s.ran('sql', 148, 0, sqlExecuted.length);
  s.say(
    rows.length
      ? `The UK PLM stores **${features.length} ${noun}** visible to your profile, in ${rows.length} unit${rows.length === 1 ? '' : 's'} of measure: ${rows.map(([u, n]) => `${n} in \`${u}\``).join(', ')}. \`pos_uom\` is the catalogue's unit column for the position fields; the semantic layer converts them to millimetres before the rules compare positions, and a NULL unit fails the unit rule.`
      : `No UK ${noun} are visible to your profile, so the query returned no rows.`,
  );
  s.call('render_table', { title: `UK ${noun} per unit of measure`, columns: ['pos_uom', 'n'], rows });
  s.finish();
}
