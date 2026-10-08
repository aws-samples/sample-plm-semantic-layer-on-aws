// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { useEffect, useRef, useState } from 'react';
import type { Interface, Status } from '../api/types';
import { FindingsChip, flaggedParts } from './Findings';
import { STATUS_LABEL } from './violations';

interface Props {
  interfaces: Interface[];
  /** Release findings over the visible parts, per rule, as the answer counts them. */
  findings: Record<string, number>;
  selectedId: string | null;
  onSelect: (id: string) => void;
  /** The findings chip: scrolls to the findings list below. */
  onShowFindings: () => void;
}

const STATUSES: Status[] = ['pass', 'fail', 'not-evaluable'];

/**
 * Counts computed from the interfaces response, the findings beside them, and one cell per interface. Sticky at
 * the top of the results; with an interface selected the cells fold into one scrollable row, the selected one kept
 * in view, so the detail below has the room. "Show all" opens the grid until the next selection.
 */
export function Tally({ interfaces, findings, selectedId, onSelect, onShowFindings }: Props) {
  const count = (s: Status) => interfaces.filter((i) => i.status === s).length;
  const [showAll, setShowAll] = useState(false);
  const row = useRef<HTMLDivElement>(null);
  useEffect(() => setShowAll(false), [selectedId]);
  const folded = selectedId !== null && !showAll;
  useEffect(() => {
    const r = row.current;
    const cell = r?.querySelector<HTMLElement>('.cell.is-selected');
    if (folded && r && cell) r.scrollLeft = cell.offsetLeft - r.clientWidth / 2 + cell.offsetWidth / 2;
  }, [folded, selectedId]);
  return (
    <div className="tally">
      <div className="tally-figures">
        {STATUSES.map((s) => <Figure key={s} n={count(s)} status={s} />)}
      </div>
      <div className="tally-findings">
        <FindingsChip findings={findings} onShow={onShowFindings} />
      </div>
      <div className={`cells-wrap${folded ? ' is-folded' : ''}`}>
        <div ref={row} className="cells" role="list" aria-label="All interfaces">
          {interfaces.map((i) => {
            const flagged = flaggedParts(i);
            const title = [`${i.id} ${i.label}: ${STATUS_LABEL[i.status]}`, ...flagged.map((p) => `release finding on ${p.id} ${p.name}`)].join('\n');
            return (
              <button
                key={i.id}
                type="button"
                role="listitem"
                className={`cell is-${i.status}${i.id === selectedId ? ' is-selected' : ''}${flagged.length ? ' has-finding' : ''}`}
                title={title}
                onClick={() => onSelect(i.id)}
              >
                {i.id.replace(/^\D+/, '')}
              </button>
            );
          })}
        </div>
        {selectedId !== null ? (
          <button type="button" className="cells-toggle" aria-expanded={!folded} onClick={() => setShowAll((v) => !v)}>
            {folded ? `Show all ${interfaces.length}` : 'Fold'}
          </button>
        ) : null}
      </div>
    </div>
  );
}

function Figure({ n, status }: { n: number; status: Status }) {
  return (
    <div className={`figure is-${status}`}>
      <span className="figure-n">{n}</span>
      <span className="figure-label">{STATUS_LABEL[status]}</span>
    </div>
  );
}
