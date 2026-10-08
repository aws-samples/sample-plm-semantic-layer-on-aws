// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { useCallback, useState } from 'react';
import type { BomNode } from '../api/types';
import type { BomReleases, Conflict } from './BomRelease';
import { BomRow } from './BomRow';
import type { SelectedPart } from '../viewer/view';
import { t } from '../i18n';

/** The root and the site kits start open: a site's own tree is one click away, the product stays on one screen. */
const openByDefault = (path: string) => path.split('.').length <= 2;

interface Props {
  root: BomNode;
  /** The lifecycleConflict findings of each item, by `plm|id`. */
  conflicts: Map<string, Conflict>;
  releases: BomReleases | null;
  onOpenSubtree: (id: string) => void;
  /** The subtree root on screen; null when the tree is the product's. */
  subtreeRoot: string | null;
  selectedPart: SelectedPart | null;
  onSelectPart: (part: SelectedPart | null) => void;
}

export function BomTree({ root, conflicts, releases, onOpenSubtree, subtreeRoot, selectedPart, onSelectPart }: Props) {
  const [toggled, setToggled] = useState<Set<string>>(() => new Set());
  const isOpen = useCallback((path: string) => openByDefault(path) !== toggled.has(path), [toggled]);
  const onToggle = useCallback((path: string) => setToggled((s) => {
    const next = new Set(s);
    if (!next.delete(path)) next.add(path);
    return next;
  }), []);
  return (
    <div className="bom-tree">
      <div className="bom-row bom-header" aria-hidden>
        <span>{t('bom.bom-tree.item')}</span>
        <span>{t('bom.bom-tree.id')}</span>
        <span className="bom-n">{t('bom.bom-tree.qty')}</span>
        <span className="bom-n">{t('bom.bom-tree.occurrences')}</span>
        <span className="bom-n">{t('bom.bom-tree.unitMass')}</span>
        <span className="bom-n">{t('bom.bom-tree.mass')}</span>
      </div>
      <ul className="bom-list" aria-label={t('bom.bom-tree.billOfMaterials')}>
        <BomRow item={root} depth={0} path="0" isOpen={isOpen} onToggle={onToggle} conflicts={conflicts} releases={releases} onOpenSubtree={onOpenSubtree} root={subtreeRoot}
          selectedPart={selectedPart} onSelectPart={onSelectPart} />
      </ul>
    </div>
  );
}
