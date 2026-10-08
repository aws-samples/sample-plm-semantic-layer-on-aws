// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import type { Evidence } from '../../api/types';
import type { Tab } from '../../ui/Drawer';
import { RuleChip } from '../RuleChip';
import { localName } from '../violations';
import { markReport, markShape } from './turtle';
import { TurtleView } from './TurtleView';
import { t } from '../../i18n';

export const SHACL_TABS: Tab[] = [
  { id: 'shapes', label: 'Shapes' },
  { id: 'report', label: 'Report' },
];

export function ShaclEvidence({ ev, tab }: { ev: Evidence; tab: string }) {
  if (tab === 'report') return <TurtleView text={ev.shacl.report} mark={markReport} />;
  if (ev.shacl.shapes.length === 0) return <p className="drawer-note">{t('check.shacl-evidence.noShapeReportedForThisInterface')}</p>;
  return (
    <>
      {ev.shacl.shapes.map((s) => (
        <section key={s.shape} className="shape-block">
          <header className="shape-head">
            <RuleChip rule={s.rule} />
            <span className="mono shape-name">{localName(s.shape)}</span>
            <span className="mono quiet">{s.shape}</span>
          </header>
          <TurtleView text={s.turtle} mark={markShape} />
        </section>
      ))}
    </>
  );
}
