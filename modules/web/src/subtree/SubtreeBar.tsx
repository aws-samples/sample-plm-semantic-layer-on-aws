// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import type { Product, Subtree } from '../api/types';
import { t } from '../i18n';
import { plmCode } from '../ui/plm';

/** The product rules, named as the shapes name them; they evaluate on the product root only. */
const PRODUCT_RULES = ['massLimit', 'massScale'] as const;

interface Props {
  product: Product | null;
  root: string;
  /** The rooted answer's subtree block; null until one has arrived. */
  subtree: Subtree | null;
  onWholeProduct: () => void;
}

/** The breadcrumb from the product to the subtree root every screen is narrowed to, with the way back. */
export function SubtreeBar({ product, root, subtree, onWholeProduct }: Props) {
  const name = subtree ? (subtree.name ?? t('subtree.subtree-bar.aRootNotVisibleToYour')) : null;
  return (
    <nav className="subtree-bar" aria-label={t('subtree.subtree-bar.subtree')}>
      <span className="eyebrow">{t('subtree.subtree-bar.subtree')}</span>
      <ol className="subtree-crumbs">
        <li>
          <button type="button" className="subtree-crumb" onClick={onWholeProduct}>{product?.name ?? t('subtree.subtree-bar.product')}</button>
        </li>
        <li aria-current="page">
          {subtree ? <b className="bom-plm">{plmCode(subtree.plm)}</b> : null}
          {name ? <span className="subtree-name">{name}</span> : null}
          <span className="mono">{root}</span>
        </li>
      </ol>
      {subtree ? (
        <span className="quiet subtree-note" title={t('subtree.subtree-bar.productRulesNotEvaluated')}>
          {subtree.items} {subtree.items === 1 ? t('subtree.subtree-bar.item') : t('subtree.subtree-bar.items')}
          {' · '}<span className="mono">{PRODUCT_RULES[0]}</span>, <span className="mono">{PRODUCT_RULES[1]}</span> {t('subtree.subtree-bar.notEvaluatedOnASubtree')}
        </span>
      ) : null}
      <button type="button" className="btn btn-small" onClick={onWholeProduct}>{t('subtree.subtree-bar.wholeProduct')}</button>
    </nav>
  );
}
