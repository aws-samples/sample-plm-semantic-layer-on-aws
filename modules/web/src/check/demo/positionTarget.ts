// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// Which record of a failing position rule is the one to correct; the correction itself is positionProposal.
import type { Feature, Interface } from '../../api/types';
import { features, type Finding } from '../violations';
import type { Axis } from './proposals';

/** The axis a position finding is about, from its first result's detail. */
export function axisOf(finding: Finding): Axis | null {
  const a = finding.violations[0]?.detail.axis;
  return a === 'x' || a === 'y' || a === 'z' ? a : null;
}

/**
 * The record to correct on a failing position rule: the one off the joint plane. The interface's other mated
 * features sit on that plane, so the feature of the pair whose value on the axis none of them shares (within
 * the tolerance) is the outlier. Null when the plane cannot be told: both or neither sit with the others.
 */
export function outlierOf(itf: Interface, pair: [Feature, Feature], axis: Axis): Feature | null {
  const tol = itf.toleranceMm ?? 0;
  const others = features(itf).filter((f) => f.id !== pair[0].id && f.id !== pair[1].id && f.positionMm);
  const onPlane = (f: Feature) => f.positionMm !== null && others.some((o) => Math.abs(o.positionMm![axis] - f.positionMm![axis]) <= tol);
  const [a, b] = pair.map(onPlane);
  if (a === b) return null;
  return a ? pair[1] : pair[0];
}
