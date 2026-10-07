// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { useState } from 'react';
import { useCached, type AppData } from '../../api/store';
import type { Envelope, Evidence, Interface, Policy } from '../../api/types';
import { Drawer, type Tab } from '../../ui/Drawer';
import { ErrorState, Loading } from '../../ui/ErrorState';
import { SHACL_CARD } from '../PathStrip';
import { NEPTUNE_TABS, NeptuneEvidence } from './NeptuneEvidence';
import { OntopEvidence, ontopTabs } from './OntopEvidence';
import { SHACL_TABS, ShaclEvidence } from './ShaclEvidence';
import { t } from '../../i18n';

interface Props {
  data: AppData;
  itf: Interface;
  card: string;
  envelope: Envelope | undefined;
  /** A tab asked for from outside (the Ask panel's open_evidence); each request applies once. */
  tabRequest?: TabRequest | null;
  onClose: () => void;
}

export interface TabRequest {
  tab: string;
  n: number;
}

/** What one answer-path card contributed to the selected interface's answer. */
export function EvidenceDrawer({ data, itf, card, envelope, tabRequest, onClose }: Props) {
  const ev = useCached(data.evidence, itf.id);
  const arm = ev.data?.arms.find((a) => a.endpoint === card);
  const call = envelope?.provenance.calls.find((c) => c.endpoint === card);
  const shacl = card === SHACL_CARD;
  /** The source was not asked: none of the interface's parts is its own. */
  const idle = !shacl && (arm ? arm.requests === 0 : call?.requests === 0);

  const kind = shacl ? 'validation' : (arm?.kind ?? call?.kind);
  const ms = shacl ? (ev.data?.timings ?? envelope?.timings)?.validationMs : arm?.ms;
  const meta = (
    <>
      {kind ? <span className="kind-tag">{kind}</span> : null}
      {shacl && ev.data ? <span>{ev.data.merged.triples} triples validated</span> : null}
      {idle ? <span>0 requests</span> : arm ? <span>{arm.tripleCount} triples · {arm.requests} req</span> : null}
      {idle ? null : ms !== undefined ? <b className="mono">{ms} ms</b> : ev.state === 'loading' ? <span>{t('check.evidence-drawer.measuring')}</span> : null}
    </>
  );
  const tabs: Tab[] = shacl ? SHACL_TABS : idle ? [] : card === 'neptune' ? NEPTUNE_TABS : arm ? ontopTabs(arm) : [];
  const policy = ev.data?.policy ?? envelope?.policy;

  return (
    <CardDrawer key={card} label={shacl ? 'Evidence of' : 'Evidence from'} title={shacl ? 'SHACL rules' : <span className="mono">{card}</span>} meta={meta} tabs={tabs} policy={policy} tabRequest={tabRequest} onClose={onClose}>
      {(tab) =>
        idle ? (
          <p className="drawer-note">
            <span className="mono">{card}</span> was not needed for {itf.id}: none of the parts on this interface is its own, so the query
            service sent it no request and it contributed no triples.
          </p>
        ) : ev.state === 'error' ? (
          <ErrorState title="The evidence could not be loaded" error={ev.error} onRetry={() => data.evidence.retry(itf.id)} />
        ) : ev.state === 'loading' ? (
          <Loading what={`Collecting what each component contributed to ${itf.id}`} />
        ) : shacl ? (
          <ShaclEvidence ev={ev.data} tab={tab} />
        ) : !arm ? (
          <p className="drawer-note">{t('check.evidence-drawer.theEvidenceFor')} {itf.id} has no arm for {card}.</p>
        ) : card === 'neptune' ? (
          <NeptuneEvidence arm={arm} itf={itf} tab={tab} />
        ) : (
          <OntopEvidence data={data} itf={itf} arm={arm} tab={tab} />
        )
      }
    </CardDrawer>
  );
}

interface CardDrawerProps {
  label: string;
  title: React.ReactNode;
  meta: React.ReactNode;
  tabs: Tab[];
  policy: Policy | undefined;
  tabRequest?: TabRequest | null;
  onClose: () => void;
  children: (tab: string) => React.ReactNode;
}

function CardDrawer({ label, title, meta, tabs, policy, tabRequest, onClose, children }: CardDrawerProps) {
  // The tab the user clicked, remembered with the request it answered: a new request opens its own tab until the next click.
  const [clicked, setClicked] = useState<{ tab: string; request: TabRequest | null | undefined } | null>(null);
  const picked = clicked && clicked.request === tabRequest ? clicked.tab : (tabRequest?.tab ?? null);
  const tab = tabs.some((t) => t.id === picked) ? picked! : (tabs[0]?.id ?? '');
  return (
    <Drawer label={label} title={title} meta={meta} tabs={tabs} tab={tab} onTab={(id) => setClicked({ tab: id, request: tabRequest })} policy={policy} onClose={onClose} tall>
      {children(tab)}
    </Drawer>
  );
}

export type { Evidence };
