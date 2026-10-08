// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { useEffect, useRef, useState } from 'react';
import type { Interface, Part, PartFinding } from '../api/types';
import { builtBy } from '../api/types';
import { ownerLabel, plmCode } from '../ui/plm';
import { bringIntoView } from '../ui/scroll';
import { flaggedParts } from './Findings';
import { t } from '../i18n';

interface Props {
  interfaces: Interface[];
  selectedId: string | null;
  /** Selects the interface the part sits on and brings its detail, where the part's warning line is, into view. */
  onOpen: (id: string) => void;
  /** Bumped by the tally chip and the legend line: the list scrolls into view and lights up. */
  reveal: number;
}

/** One finding on one part, with every interface the part sits on. */
interface Row {
  part: Part;
  finding: PartFinding;
  interfaces: Interface[];
}

const rowKey = (part: Part, finding: PartFinding) => `${part.plm}|${part.id}|${finding.rule}`;

function rowsOf(interfaces: Interface[]): Row[] {
  const byKey = new Map<string, Row>();
  for (const itf of interfaces) {
    for (const part of flaggedParts(itf)) {
      for (const finding of part.findings ?? []) {
        const row = byKey.get(rowKey(part, finding)) ?? { part, finding, interfaces: [] };
        row.interfaces.push(itf);
        byKey.set(rowKey(part, finding), row);
      }
    }
  }
  return [...byKey.values()];
}

const LIT_MS = 1600;

/** Release findings on the visible parts, after the interface lists: something a PLM still owes, never a verdict. Nothing when there are none. */
export function FindingsList({ interfaces, selectedId, onOpen, reveal }: Props) {
  const rows = rowsOf(interfaces);
  const ref = useRef<HTMLElement>(null);
  const [lit, setLit] = useState(false);
  useEffect(() => {
    if (!reveal) return;
    bringIntoView(ref.current);
    setLit(true);
    const t = setTimeout(() => setLit(false), LIT_MS);
    return () => clearTimeout(t);
  }, [reveal]);
  if (rows.length === 0) return null;
  return (
    <section ref={ref} className={`findings-list${lit ? ' is-lit' : ''}`} aria-label="Release findings">
      <h2 className="eyebrow">{t('check.findings-list.releaseFindings')}</h2>
      <ul className="fail-list is-findings">
        {rows.map(({ part, finding, interfaces: on }) => {
          const selected = on.some((i) => i.id === selectedId);
          return (
            <li key={rowKey(part, finding)}>
              <button
                type="button"
                className={`fail-row is-finding${selected ? ' is-selected' : ''}`}
                aria-pressed={selected}
                onClick={() => onOpen(on[0].id)}
              >
                <span className="fail-id mono">{on.map((i) => <span key={i.id}>{i.id}</span>)}</span>
                <span className="finding-part">
                  <span>
                    <b>{plmCode(part.plm)}</b> {part.name} <span className="mono quiet">{part.id}</span>
                  </span>
                  {part.supplier ? <span className="finding-supplier">{t('check.findings-list.builtBy')} {builtBy(part)}, integrated by {ownerLabel(part.plm)}</span> : null}
                  <span className="finding-message">{finding.message}</span>
                </span>
                <span className="fail-rules"><span className="chip is-finding">{finding.rule}</span></span>
              </button>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
