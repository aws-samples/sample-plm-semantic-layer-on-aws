// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { useState } from 'react';
import { useCached, type AppData } from '../../api/store';
import type { EvidenceArm, Interface } from '../../api/types';
import { R2rml } from '../../catalogue/R2rml';
import type { Tab } from '../../ui/Drawer';
import { CORE } from '../../ui/plm';
import { SparqlText } from '../PathStrip';
import { entityOf, violationColumns } from './columns';
import { NativeRows } from './NativeRows';
import { TurtleView } from './TurtleView';
import { t } from '../../i18n';

export function ontopTabs(arm: EvidenceArm): Tab[] {
  return [
    { id: 'arm', label: 'SERVICE arm' },
    ...(arm.sql !== null ? [{ id: 'sql', label: 'Generated SQL' }] : []),
    { id: 'rows', label: 'Native rows' },
    { id: 'triples', label: 'Triples' },
    { id: 'r2rml', label: 'R2RML' },
  ];
}

interface Props {
  data: AppData;
  itf: Interface;
  arm: EvidenceArm;
  tab: string;
}

export function OntopEvidence({ data, itf, arm, tab }: Props) {
  if (tab === 'sql' && arm.sql !== null) return <SqlView sql={arm.sql} policy={arm.endpoint === `ontop-${CORE}`} />;
  if (tab === 'rows') return <NativeRows data={data} itf={itf} arm={arm} />;
  if (tab === 'triples') return <TurtleView text={arm.triples} />;
  if (tab === 'r2rml') return <R2rmlTab data={data} itf={itf} arm={arm} />;
  return <SparqlText sparql={arm.sparql} />;
}

const SELECT = /^\s*SELECT\b/i;
const FROM = /^\s*FROM\b/i;
/** The column the profile's FILTER becomes once pushed into atelier_core's SQL. */
const POLICY = /releasable_to/i;

type Piece = { kind: 'line'; text: string; policy: boolean } | { kind: 'fold'; columns: number; lines: string[] };

/** Lines of the SQL; on the tag arm each SELECT list is one folded piece and the releasability predicate is marked. */
function pieces(sql: string, policy: boolean): Piece[] {
  const lines = sql.split('\n');
  const out: Piece[] = [];
  for (let i = 0; i < lines.length; i++) {
    const from = policy && SELECT.test(lines[i]) ? lines.findIndex((l, k) => k > i && FROM.test(l)) : -1;
    if (from > i) {
      const list = lines.slice(i, from);
      out.push({ kind: 'fold', columns: list.join(' ').replace(SELECT, '').split(',').filter((c) => c.trim()).length, lines: list });
      i = from - 1;
      continue;
    }
    out.push({ kind: 'line', text: lines[i], policy: policy && !/^\s*--/.test(lines[i]) && POLICY.test(lines[i]) });
  }
  return out;
}

/**
 * The SQL as Ontop generated it, long lines wrapped. On the tag arm the SELECT list is folded, so the predicate the
 * profile's FILTER became is the line that stands out.
 */
function SqlView({ sql, policy }: { sql: string; policy: boolean }) {
  const [unfolded, setUnfolded] = useState(false);
  return (
    <pre className="drawer-code mono">
      {pieces(sql, policy).map((p, i) =>
        p.kind === 'line' ? (
          <span key={i} className={`sql-line${p.policy ? ' is-policy' : ''}`}>{p.text || ' '}</span>
        ) : (
          <span key={i}>
            <button type="button" className="sql-fold" aria-expanded={unfolded} onClick={() => setUnfolded((v) => !v)}>
              {t('check.ontop-evidence.select')} <b>{p.columns} columns</b> · {unfolded ? 'fold' : 'show'}
            </button>
            {unfolded ? p.lines.map((l, k) => <span key={k} className="sql-line">{l}</span>) : null}
          </span>
        ),
      )}
    </pre>
  );
}

/** The TriplesMaps of the PLM's mapping that produce the tables this arm read. */
function R2rmlTab({ data, itf, arm }: Omit<Props, 'tab'>) {
  const plm = arm.tables[0]?.plm ?? arm.endpoint.replace(/^ontop-/, '');
  const catalogue = useCached(data.catalogues, plm);
  const mapping = useCached(data.mappings, plm);
  if (arm.tables.length === 0) return <p className="drawer-note">{t('check.ontop-evidence.thisArmReadsNoNativeTable')}</p>;
  return (
    <>
      {arm.tables.map((t) => {
        const entity = entityOf(catalogue.data ?? null, t.table);
        const about = violationColumns(itf, catalogue.data ?? null, t.table).all;
        // The unit column (no ontology term of its own) tells the story better than the position it qualifies.
        const column = entity?.columns.find((c) => about.has(c.column) && !c.ontologyTerm)?.column
          ?? entity?.columns.find((c) => about.has(c.column))?.column ?? entity?.columns[0]?.column ?? '';
        return <R2rml key={t.table} plm={plm} mapping={mapping} entity={entity?.entity ?? t.table} column={column} />;
      })}
    </>
  );
}
