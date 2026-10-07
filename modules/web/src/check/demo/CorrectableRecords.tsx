// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { useState, type ReactNode } from 'react';
import type { AppData } from '../../api/store';
import type { Feature, Interface } from '../../api/types';
import { SourceRecords, type RecordRow } from '../SourceRecords';
import { featureById, type Finding } from '../violations';
import type { Offer } from './offer';
import { OfferRelease } from './OfferRelease';
import { ReleaseButton } from './ReleaseButton';

interface Props {
  data: AppData;
  itf: Interface;
  finding: Finding;
  /** The release a record offers, or a note why it offers none; null leaves its cell empty. */
  offerOf: (f: Feature) => Offer | ReactNode;
  /** The record the finding tells is the one to correct: marked in the table, its button primary. */
  mark?: { id: string; label: string };
  /** Runs the rules again, as Run rules does. */
  onReleased: () => void;
}

const isOffer = (o: unknown): o is Offer => typeof o === 'object' && o !== null && 'plan' in o && 'plm' in o;

/**
 * The records of a failing rule with one release per record that can be corrected, in the column of the site that
 * owns it. A button shows only to that site's engineer and to the officer; the form opens below the table.
 */
export function CorrectableRecords({ data, itf, finding, offerOf, mark, onReleased }: Props) {
  const [open, setOpen] = useState<string | null>(null);
  const shown = recordsOf(itf, finding);
  const each = new Map(shown.map((f) => [f.id, offerOf(f)]));
  const offer = [...each.values()].filter(isOffer).find((o) => o.key === open);
  const extra: RecordRow = {
    key: 'correction',
    label: 'Correction',
    cell: (f) => {
      const o = each.get(f.id);
      if (!isOffer(o)) return o;
      return <ReleaseButton data={data} offer={o} open={open === o.key} primary={mark?.id === f.id} onToggle={() => setOpen((k) => (k === o.key ? null : o.key))} />;
    },
  };
  return (
    <>
      <SourceRecords itf={itf} finding={finding} extra={extra} mark={mark} />
      {offer ? <OfferRelease key={offer.key} data={data} offer={offer} onClose={() => setOpen(null)} onReleased={onReleased} /> : null}
    </>
  );
}

/** The records SourceRecords shows: the finding's features, and the mate of a lone one. */
function recordsOf(itf: Interface, finding: Finding): Feature[] {
  const fs = finding.features.map((id) => featureById(itf, id)).filter((f): f is Feature => !!f);
  const mate = fs.length === 1 && fs[0].matesWith[0] ? featureById(itf, fs[0].matesWith[0]) : undefined;
  return mate ? [...fs, mate] : fs;
}
