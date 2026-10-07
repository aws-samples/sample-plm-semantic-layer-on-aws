// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { Fragment, type ReactNode } from 'react';
import type { Part, Quantity } from '../api/types';
import { t } from '../i18n';

const UNIT_LABEL: Record<string, string> = { KiloGM: 'kg', LB: 'lb' };

/** Mass in kg; the stored value and unit beside it when the PLM stores another unit. */
function massText(mass: Quantity): string {
  const stored = `${mass.value} ${mass.unit ? (UNIT_LABEL[mass.unit] ?? mass.unit) : ''}`.trim();
  if (typeof mass.kg !== 'number') return stored;
  const kg = `${Number(mass.kg.toFixed(3))} kg`;
  return mass.unit === 'KiloGM' ? kg : `${kg} (${stored} ${t('check.part-attributes.asStored')})`;
}

/** A gear's module in mm; the stored value in inches beside it when the PLM stores inches. */
function moduleText(module: Quantity): string {
  const label = t('check.part-attributes.module');
  const mm = typeof module.mm === 'number' ? `${label} ${Number(module.mm.toFixed(3))} mm` : `${label} ${module.value} ${module.unit ?? ''}`.trim();
  return module.unit === 'IN' ? `${mm} (${module.value} in ${t('check.part-attributes.asStored')})` : mm;
}

/**
 * The attributes the owning PLM states about a part: revision in its own form, lifecycle in its own word with the
 * canonical state the ontology's lifecycle scheme gives that word, mass, material, part type and, for a gear, its tooth
 * count and module. Nothing when the
 * PLM states none.
 */
export function PartAttributes({ part }: { part: Part }) {
  const items: ReactNode[] = [];
  if (part.revision) items.push(<span className="mono">{t('check.part-attributes.revision')} {part.revision}</span>);
  if (part.lifecycle) {
    items.push(
      <>
        {part.lifecycle}
        {part.lifecycleState ? <span className="mono quiet" title={t('check.part-attributes.canonicalState')}> {part.lifecycleState}</span> : null}
      </>,
    );
  }
  if (part.mass) items.push(massText(part.mass));
  if (part.material) items.push(part.material);
  if (part.partType) items.push(part.partType);
  if (part.toothCount !== undefined) items.push(`${part.toothCount} ${t('check.part-attributes.teeth')}`);
  if (part.gearModule) items.push(moduleText(part.gearModule));
  if (items.length === 0) return null;
  return (
    <span className="built-by">
      {items.map((item, i) => (
        <Fragment key={i}>
          {i > 0 ? ' · ' : null}
          {item}
        </Fragment>
      ))}
    </span>
  );
}
