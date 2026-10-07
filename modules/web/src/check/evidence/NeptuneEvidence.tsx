// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import type { EvidenceArm, Interface } from '../../api/types';
import type { Tab } from '../../ui/Drawer';
import { LinksGraph } from './LinksGraph';
import { TurtleView } from './TurtleView';

/** The Turtle carries what both named graphs contributed: the links and the file index entries of the parts. */
export const NEPTUNE_TABS: Tab[] = [
  { id: 'links', label: 'Links and file index' },
  { id: 'graph', label: 'Graph' },
];

export function NeptuneEvidence({ arm, itf, tab }: { arm: EvidenceArm; itf: Interface; tab: string }) {
  return tab === 'graph' ? <LinksGraph turtle={arm.triples} itf={itf} /> : <TurtleView text={arm.triples} />;
}
