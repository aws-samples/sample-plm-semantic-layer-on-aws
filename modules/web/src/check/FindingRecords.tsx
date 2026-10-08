// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import type { AppData } from '../api/store';
import type { Feature, Interface } from '../api/types';
import { t } from '../i18n';
import { CorrectableRecords } from './demo/CorrectableRecords';
import { matchOffer, positionOffer, unitOffer } from './demo/offers';
import { outlierOf, axisOf } from './demo/positionTarget';
import { isMatchRule } from './demo/proposals';
import { SourceRecords } from './SourceRecords';
import { featureById, type Finding } from './violations';

interface Props {
  data: AppData;
  itf: Interface;
  finding: Finding;
  onReleased: () => void;
}

const AXES = ['x', 'y', 'z'] as const;
const hasPosition = (f: Feature) => AXES.every((a) => f.source[a] !== null);

/** The quantity a unit finding is about, as the ontology names it: the result's quantity, else the position on its axis. */
function quantityOf(finding: Finding): string | null {
  const d = finding.violations[0]?.detail ?? {};
  if (typeof d.quantity === 'string') return d.quantity;
  return typeof d.axis === 'string' ? `position${d.axis.toUpperCase()}` : null;
}

/**
 * The records of a failing interface rule, each with the release its site may make: position moves the record off
 * the joint plane onto its mate, unit states the unit of the record that has none, connector, fastener and
 * hydraulic let either side match the other. Other rules show their records only.
 */
export function FindingRecords({ data, itf, finding, onReleased }: Props) {
  const pair = finding.features.map((id) => featureById(itf, id)).filter((f): f is Feature => !!f);
  if (finding.rule === 'position') {
    const axis = axisOf(finding);
    if (!axis) return <SourceRecords itf={itf} finding={finding} />;
    const outlier = pair.length === 2 ? outlierOf(itf, [pair[0], pair[1]], axis) : null;
    const offerOf = (f: Feature) => {
      if (!hasPosition(f)) return <span className="quiet">{t('check.correctable-records.noStoredPosition')}</span>;
      const mate = featureById(itf, f.matesWith[0]);
      return mate ? positionOffer(itf, f, mate, axis) : null;
    };
    return (
      <CorrectableRecords data={data} itf={itf} finding={finding} offerOf={offerOf} onReleased={onReleased}
        mark={outlier ? { id: outlier.id, label: t('check.finding-records.offTheJointPlane') } : undefined} />
    );
  }
  if (finding.rule === 'unit') {
    const quantity = quantityOf(finding);
    const unitless = new Set(finding.features);
    const offerOf = (f: Feature) => (quantity && unitless.has(f.id) ? unitOffer(itf, f, quantity) : null);
    return (
      <CorrectableRecords data={data} itf={itf} finding={finding} offerOf={offerOf} onReleased={onReleased}
        mark={pair[0] ? { id: pair[0].id, label: t('check.finding-records.noUnitStored') } : undefined} />
    );
  }
  if (isMatchRule(finding.rule) && pair.length === 2) {
    const rule = finding.rule;
    const offerOf = (f: Feature) => {
      const mate = pair.find((m) => m.id !== f.id);
      return mate ? matchOffer(itf, rule, f, mate) : null;
    };
    return <CorrectableRecords data={data} itf={itf} finding={finding} offerOf={offerOf} onReleased={onReleased} />;
  }
  return <SourceRecords itf={itf} finding={finding} />;
}
