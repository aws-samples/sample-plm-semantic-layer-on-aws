// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { ALL, useCached, type AppData } from '../api/store';
import { isPart, LIFECYCLE_CONFLICT } from '../api/types';
import { rereadSites } from '../check/demo/reread';
import { usePartOffers } from '../check/demo/usePartOffers';
import type { BomReleases, Conflict } from './BomRelease';
import { ErrorState, Loading } from '../ui/ErrorState';
import { BomTree } from './BomTree';
import { Rollup } from './Rollup';
import { t } from '../i18n';
import type { SelectedPart } from '../viewer/view';

interface Props {
  data: AppData;
  onOpenSubtree: (id: string) => void;
  onRunRules: () => void;
  /** The part the person selected, in the tree or in the viewer: the Ask request carries it. */
  selectedPart: SelectedPart | null;
  onSelectPart: (part: SelectedPart | null) => void;
}

/** The product's bill of materials: no PLM holds it, the layer assembles it from each site's kit. */
export function BillOfMaterials({ data, onOpenSubtree, onRunRules, selectedPart, onSelectPart }: Props) {
  const bom = useCached(data.bom, data.product ? ALL : null);
  const parts = useCached(data.parts, data.product ? ALL : null);
  const product = data.product;
  const offersOf = usePartOffers(data);
  // The lifecycle rule is a finding on the part, so the parts answer marks the nodes; the tree shows them once it arrives.
  const conflicts = new Map<string, Conflict>();
  if (parts.state === 'ready') {
    for (const part of parts.data.parts.filter(isPart)) {
      const findings = (part.findings ?? []).filter((f) => f.rule === LIFECYCLE_CONFLICT);
      if (findings.length) conflicts.set(`${part.plm}|${part.id}`, { part, findings });
    }
  }
  // A release changes a site's lifecycle word: the parts and the tree are read again and the rules run again.
  const releases: BomReleases | null = parts.state === 'ready' ? {
    data, offersOf, onReleased: () => {
      rereadSites(data);
      onRunRules();
    },
  } : null;
  return (
    <div className="bom">
      <header className="bom-head">
        <span className="eyebrow">{t('bom.bill-of-materials.billOfMaterials')}</span>
        <h1 className="bom-title">{product?.name ?? t('bom.bill-of-materials.noProduct')}</h1>
        <p className="explain">{t('bom.bill-of-materials.noPlmHoldsThisList')}</p>
      </header>
      {!product ? (
        <p className="quiet hint">{t('bom.bill-of-materials.noProductIsListedForYour')}</p>
      ) : bom.state === 'error' ? (
        <ErrorState title={t('bom.bill-of-materials.theBillOfMaterialsCouldNot')} error={bom.error} onRetry={() => data.bom.retry(ALL)} />
      ) : bom.state === 'loading' ? (
        <Loading what={t('bom.bill-of-materials.readingTheBillOfMaterials')} />
      ) : bom.data.root.children.length === 0 ? (
        <p className="quiet hint">{t('bom.bill-of-materials.noSiteHoldsItemsOfThis')}</p>
      ) : (
        <>
          <Rollup sites={bom.data.sites} total={bom.data.total} subtree={data.root !== null} />
          <BomTree key={`${data.profile}|${bom.data.product}|${data.root}`} root={bom.data.root} conflicts={conflicts} releases={releases}
            onOpenSubtree={onOpenSubtree} subtreeRoot={data.root} selectedPart={selectedPart} onSelectPart={onSelectPart} />
        </>
      )}
    </div>
  );
}
