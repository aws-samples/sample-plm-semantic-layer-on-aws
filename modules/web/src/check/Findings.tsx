// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import type { Interface, Part, PartFinding } from '../api/types';
import { isPart } from '../api/types';

/** A release finding on a part, as the service reports it: the rule, then its message. It never fails an interface. */
export const FindingLine = ({ finding }: { finding: PartFinding }) => (
  <span className="part-finding">
    <span className="mono">{finding.rule}</span>: {finding.message}
  </span>
);

/** The visible parts of an interface that carry a release finding. */
export const flaggedParts = (itf: Interface): Part[] => itf.parts.filter(isPart).filter((p) => p.findings?.length);

interface ChipProps {
  findings: Record<string, number>;
  /** Scrolls to the findings list below and lights it. */
  onShow: () => void;
}

/** What a PLM still owes, in a few words that fit the panel; the rule's name stays on the row and in the chip's title. */
const chipText = (rule: string, n: number) =>
  rule === 'cadMissing' ? `${n === 1 ? 'part' : 'parts'} without published CAD`
    : rule === 'danglingReference' ? `${n === 1 ? 'reference' : 'references'} to no part`
      : rule === 'staleRevision' ? `stale ${n === 1 ? 'revision' : 'revisions'}`
        : rule === 'conflictingLeadTime' ? `lead-time ${n === 1 ? 'conflict' : 'conflicts'}`
          : rule === 'massScale' ? `${n === 1 ? 'mass' : 'masses'} in another unit`
            : rule === 'lifecycleConflict' ? `lifecycle ${n === 1 ? 'conflict' : 'conflicts'}`
              : rule === 'meshModule' ? `gear ${n === 1 ? 'mesh' : 'meshes'} of two modules`
              : `${rule} ${n === 1 ? 'finding' : 'findings'}`;

/** The findings counted at the top of the interfaces answer, beside the tally, opening the list below; nothing when there are none. */
export function FindingsChip({ findings, onShow }: ChipProps) {
  const rules = Object.entries(findings).filter(([, n]) => n > 0);
  if (rules.length === 0) return null;
  return (
    <button type="button" className="findings-chip" title={rules.map(([rule, k]) => `${rule} ×${k}`).join('\n')} onClick={onShow}>
      <i className="swatch swatch-phantom" />
      {rules.map(([rule, n]) => (
        <span key={rule}><b>{n}</b>{chipText(rule, n)}</span>
      ))}
    </button>
  );
}
