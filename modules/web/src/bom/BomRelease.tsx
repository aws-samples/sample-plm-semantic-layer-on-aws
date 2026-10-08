// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { useState, type CSSProperties } from 'react';
import type { AppData } from '../api/store';
import type { Part, PartFinding } from '../api/types';
import type { Offer } from '../check/demo/offer';
import { OfferRelease } from '../check/demo/OfferRelease';
import { ReleaseButton } from '../check/demo/ReleaseButton';

/** A node's lifecycleConflict findings with the part row the parts answer holds for it. */
export interface Conflict {
  part: Part;
  findings: PartFinding[];
}

/** What a row needs to offer a release: the data, the offers of a finding and what runs once one has landed. */
export interface BomReleases {
  data: AppData;
  offersOf: (part: Part, finding: PartFinding) => Offer[];
  onReleased: () => void;
}

interface Props {
  conflict: Conflict;
  releases: BomReleases;
  depth: number;
}

/**
 * Under a node whose release conflicts with a dependency: each finding, and its two releases, by the dependency's site
 * (it releases the dependency) and by the node's (it blocks the node); the form opens below them.
 */
export function BomRelease({ conflict, releases, depth }: Props) {
  const [open, setOpen] = useState<string | null>(null);
  const each = conflict.findings.map((f) => ({ finding: f, offers: releases.offersOf(conflict.part, f) }));
  const offer = each.flatMap((e) => e.offers).find((o) => o.key === open);
  return (
    <div className="bom-release" style={{ '--depth': depth } as CSSProperties}>
      {each.map(({ finding, offers }) => (
        <div key={finding.message} className="bom-release-finding">
          <span className="part-finding"><span className="mono">{finding.rule}</span>: {finding.message}</span>
          <span className="bom-release-offers">
            {offers.map((o) => (
              <ReleaseButton key={o.key} data={releases.data} offer={o} open={open === o.key} onToggle={() => setOpen((k) => (k === o.key ? null : o.key))} />
            ))}
          </span>
        </div>
      ))}
      {offer ? <OfferRelease key={offer.key} data={releases.data} offer={offer} onClose={() => setOpen(null)} onReleased={releases.onReleased} /> : null}
    </div>
  );
}
