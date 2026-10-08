// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import type { ReactNode } from 'react';
import type { Policy } from '../api/types';
import { t } from '../i18n';

export interface Tab {
  id: string;
  label: string;
}

interface Props {
  label: string;
  title: ReactNode;
  meta?: ReactNode;
  /** The export-control policy the service applied to what the drawer shows. */
  policy?: Policy;
  tabs?: Tab[];
  tab?: string;
  onTab?: (id: string) => void;
  onClose: () => void;
  tall?: boolean;
  children: ReactNode;
}

/** Dark panel that rises from the answer path; with tabs when it has more than one view. */
export function Drawer({ label, title, meta, policy, tabs, tab, onTab, onClose, tall, children }: Props) {
  return (
    <aside className={`drawer${tall ? ' is-tall' : ''}`} aria-label={label}>
      <header className="drawer-head">
        <span className="eyebrow">{label}</span>
        <span className="drawer-title">{title}</span>
        {meta ? <span className="drawer-meta">{meta}</span> : null}
        <button type="button" className="btn btn-small" onClick={onClose}>{t('ui.drawer.close')}</button>
      </header>
      {policy ? (
        <div className="policy-line">
          <span className="eyebrow">{t('ui.drawer.policy')}</span>
          <span>{t('ui.drawer.profile')} <span className="mono">{policy.profile}</span></span>
          <span>{t('ui.drawer.releasableTo')} <span className="mono">{policy.releasable.join(', ')}</span></span>
          <span className="mono policy-filter" title={policy.filter}>{policy.filter}</span>
        </div>
      ) : null}
      {tabs && tabs.length > 1 ? (
        <nav className="drawer-tabs" role="tablist">
          {tabs.map((t) => (
            <button key={t.id} type="button" role="tab" aria-selected={t.id === tab} className={`drawer-tab${t.id === tab ? ' is-on' : ''}`} onClick={() => onTab?.(t.id)}>
              {t.label}
            </button>
          ))}
        </nav>
      ) : null}
      <div className="drawer-body">{children}</div>
    </aside>
  );
}
