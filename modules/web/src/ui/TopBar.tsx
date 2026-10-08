// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { PROFILES, type ProfileId } from '../api/profile';
import type { Product, ProductListResponse, ProductsResponse } from '../api/types';
import type { Resource } from '../api/useResource';
import type { Route } from './useHashRoute';
import { toHash } from './useHashRoute';
import { withRoot } from '../subtree/rootHash';
import { t } from '../i18n';

/** The rule name as the shapes name it. */
const MASS_LIMIT_RULE = 'massLimit';

interface Props {
  route: Route;
  /** The subtree root the tabs keep; null is the whole product. */
  root: string | null;
  envName: string | null;
  lastCheckId: string | null;
  profile: ProfileId;
  onProfile: (p: ProfileId) => void;
  /** The products as the query service lists them for the profile. */
  products: Resource<ProductListResponse>;
  /** The product rules' answer when it failed, null otherwise: the switch says the findings are missing. */
  rules: Resource<ProductsResponse> | null;
  /** The product the screens are scoped to; null until the list has answered. */
  product: Product | null;
  onProduct: (key: string) => void;
  onRunRules: () => void;
  askOpen: boolean;
  onToggleAsk: () => void;
}

export function TopBar({ route, root, envName, lastCheckId, profile, onProfile, products, rules, product, onProduct, onRunRules, askOpen, onToggleAsk }: Props) {
  const tab = (target: Route, label: string) => (
    <a
      className={`tab${route.screen === target.screen ? ' is-on' : ''}`}
      aria-current={route.screen === target.screen ? 'page' : undefined}
      href={withRoot(toHash(target), root)}
    >
      {label}
    </a>
  );
  return (
    <header className="topbar">
      <div className="brand">
        <span className="brand-mark">{t('ui.top-bar.atelier')}</span>
        <span className="brand-sub">{t('ui.top-bar.plmSemanticLayer')}</span>
      </div>
      <nav className="tabs" aria-label="Screens">
        {tab({ screen: 'check', interfaceId: lastCheckId }, 'Interface check')}
        {tab({ screen: 'paths', query: null }, 'Paths')}
        {tab({ screen: 'bom' }, 'Bill of materials')}
        {tab({ screen: 'catalogue' }, 'Data catalogue')}
        {tab({ screen: 'rules', ruleName: null }, t('rules.tab.rules'))}
        {tab({ screen: 'flow' }, 'Data flow')}
        {tab({ screen: 'architecture' }, 'Architecture')}
      </nav>
      <div className="topbar-right">
        <ProductSwitch products={products} rules={rules} product={product} onProduct={onProduct} />
        <div className="profile">
          <label className="profile-row">
            <span className="profile-label">{t('ui.top-bar.viewingAs')}</span>
            <select className="profile-select" value={profile} onChange={(e) => onProfile(e.target.value as ProfileId)}>
              {PROFILES.map((p) => (
                <option key={p.id} value={p.id}>{p.label}</option>
              ))}
            </select>
          </label>
          <span className="profile-note" title="profile simulated; production reads identity-provider claims">{t('ui.top-bar.profileSimulatedProductionReadsIdentityProvider')}</span>
        </div>
        {envName ? <span className="env mono">{envName}</span> : null}
        <button type="button" className="btn btn-ghost" aria-pressed={askOpen} onClick={onToggleAsk}>
          {t('ui.top-bar.ask')}
        </button>
        {route.screen === 'check' ? (
          <button type="button" className="btn btn-primary" onClick={onRunRules}>
            {t('ui.top-bar.runRules')}
          </button>
        ) : null}
      </div>
    </header>
  );
}

/**
 * The product the screens are scoped to, styled like the profile switch. The note under it is the
 * product's coordinate frame as the Atelier core states it, or the number of its parts the profile may
 * see when it states none; under that, each finding of the product rules, which no single PLM can evaluate, and the
 * mass limit when it is not evaluable for this profile.
 */
/** The mass limit of a product the profile does not see whole, as an interface with a redacted side. */
const notEvaluable = (hidden: number) =>
  `not evaluable, ${hidden} ${hidden === 1 ? 'item' : 'items'} of the product hidden from this profile`;

function ProductSwitch({ products, rules, product, onProduct }: Pick<Props, 'products' | 'rules' | 'product' | 'onProduct'>) {
  const listed = products.state === 'ready' ? products.data.products : [];
  const note = product?.frame ?? (product ? `${product.partCount} ${product.partCount === 1 ? 'part' : 'parts'} visible to this profile` : null);
  return (
    <div className="profile product">
      <label className="profile-row">
        <span className="profile-label">{t('ui.top-bar.product')}</span>
        <select
          className="profile-select"
          value={product?.key ?? ''}
          disabled={listed.length === 0}
          aria-busy={products.state === 'loading'}
          onChange={(e) => onProduct(e.target.value)}
        >
          {listed.length === 0 ? <option value="">{products.state === 'error' ? 'products unavailable' : 'reading products'}</option> : null}
          {listed.map((p) => (
            <option key={p.key} value={p.key}>{p.name}</option>
          ))}
        </select>
      </label>
      {note ? <span className="profile-note product-note" title={note}>{note}</span> : null}
      {product?.massLimit?.status === 'not-evaluable' ? (
        <span className="profile-note product-finding" title={notEvaluable(product.massLimit.hiddenItems ?? 0)}>
          <span className="mono">{MASS_LIMIT_RULE}</span>: {notEvaluable(product.massLimit.hiddenItems ?? 0)}
        </span>
      ) : null}
      {rules?.state === 'error' ? (
        <span className="profile-note product-finding" title={rules.error.message}>{t('ui.top-bar.productRulesUnavailable')}</span>
      ) : null}
      {product?.findings?.map((f) => (
        <span key={`${f.rule}|${f.message}`} className="profile-note product-finding" title={f.message}>
          <span className="mono">{f.rule}</span>: {f.message}
        </span>
      ))}
    </div>
  );
}
