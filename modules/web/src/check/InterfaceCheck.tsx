// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { getJson } from '../api/client';
import { optioned } from '../api/option';
import { scoped } from '../api/product';
import { rooted } from '../api/subtree';
import { ALL, useCached, usePlacements, type AppData } from '../api/store';
import type { Envelope, InterfaceResponse, PartsResponse } from '../api/types';
import type { Resource } from '../api/useResource';
import type { EvidenceRequest } from '../ask/types';
import { ErrorState, Loading } from '../ui/ErrorState';
import { bringIntoView } from '../ui/scroll';
import { withContext } from '../subtree/contextParts';
import { LensStrip } from '../lens/LensStrip';
import { useLens } from '../lens/useLens';
import { Viewer } from '../viewer/Viewer';
import type { SelectedPart, ViewState } from '../viewer/view';
import { ChangesStrip } from './demo/ChangesStrip';
import { EvidenceDrawer } from './evidence/EvidenceDrawer';
import { FailList } from './FailList';
import { FindingsList } from './FindingsList';
import { InterfaceDetail } from './InterfaceDetail';
import { PathStrip, SparqlDrawer } from './PathStrip';
import { ProductFindings } from './ProductFindings';
import { Purchasing } from './Purchasing';
import { Tally } from './Tally';
import { ReferencesSummary } from './PartReferences';
import { Untagged } from './Untagged';
import { Variants } from './Variants';
import { useConfiguration } from '../variant/useConfiguration';
import { t } from '../i18n';

interface Props {
  data: AppData;
  selectedId: string | null;
  onSelect: (id: string | null) => void;
  evidenceRequest: EvidenceRequest | null;
  /** Runs the rules again: the top bar's Run rules, and the demo controls once they have written. */
  onRunRules: () => void;
  /** Takes an option of a variant group; null puts every group back at its default. */
  onOption: (option: string | null) => void;
  /** What the agent asked the viewer to show, and the part the person selected in it. */
  view: ViewState;
  onClearView: () => void;
  selectedPart: SelectedPart | null;
  onSelectPart: (p: SelectedPart | null) => void;
}

const SPARQL = 'sparql';

export function InterfaceCheck({ data, selectedId, onSelect, evidenceRequest, onRunRules, onOption, view, onClearView, selectedPart, onSelectPart }: Props) {
  const parts = useCached(data.parts, ALL);
  const placements = usePlacements(data);
  const list = useCached(data.interfaces, ALL);
  // Under an option the screen shows its configuration: the base interfaces with the option's own in place of the
  // default option's. An option interface is answered by the diff, which ran its rules; the base ones by their own request.
  const configuration = useConfiguration(data, list.data?.interfaces);
  const diff = configuration.diff?.state === 'ready' ? configuration.diff.data : null;
  const ownItf = diff && selectedId !== null ? diff.interfaces.find((i) => i.id === selectedId) ?? null : null;
  const fetched = useCached(data.one, ownItf ? null : selectedId);
  const one: Resource<InterfaceResponse> = ownItf && diff
    ? { state: 'ready', data: { interface: ownItf, provenance: diff.provenance, sparql: diff.sparql, timings: diff.timings, policy: diff.policy } }
    : fetched;
  /** The SPARQL drawer, or the answer-path card whose evidence is open. */
  const [open, setOpen] = useState<string | null>(null);
  /** The evidence tab the agent asked for; applied once per request. */
  const [tabRequest, setTabRequest] = useState<{ tab: string; n: number } | null>(null);
  const handled = useRef<number | null>(null);
  // The agent's open_evidence: the route already selects the interface; open its card and tab.
  useEffect(() => {
    if (!evidenceRequest || handled.current === evidenceRequest.n) return;
    handled.current = evidenceRequest.n;
    setOpen(evidenceRequest.card);
    setTabRequest(evidenceRequest.tab ? { tab: evidenceRequest.tab, n: evidenceRequest.n } : null);
  }, [evidenceRequest]);
  const toggle = useCallback((id: string | null) => onSelect(id === selectedId ? null : id), [onSelect, selectedId]);
  /** Fresh presigned CAD URLs for the viewer once the cached ones have expired, for the product or subtree on screen. */
  const productKey = data.product?.key ?? null;
  const root = data.root;
  const option = data.option;
  const refetchParts = useCallback(
    () => getJson<PartsResponse>(data.config, optioned(rooted('/query/parts', productKey, root), option), data.profile).then((r) => r.parts),
    [data.config, data.profile, productKey, root, option],
  );
  /** Bumped by the tally chip and the legend line: the findings list scrolls into view and lights up. */
  const [reveal, setReveal] = useState(0);
  const revealFindings = useCallback(() => setReveal((n) => n + 1), []);
  const panel = useRef<HTMLElement>(null);
  const [lens, setLens] = useLens();
  const interfaces = configuration.interfaces;
  const loaded = interfaces !== undefined;
  // A subtree's view draws the far side of its interfaces too, faded; it waits for the interfaces so
  // the scene loads once.
  const drawn = useMemo(() => (root && !interfaces ? undefined : withContext(parts.data?.parts, interfaces)), [root, parts.data, interfaces]);
  // The tally is sticky: whatever scrolls into view lands under it, by its measured height (--tally-h, read by the scroll margins).
  useEffect(() => {
    const p = panel.current;
    const tally = p?.querySelector<HTMLElement>('.tally');
    if (!p || !tally) return;
    const measure = () => p.style.setProperty('--tally-h', `${tally.offsetHeight}px`);
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(tally);
    return () => observer.disconnect();
  }, [loaded]);
  // A selection is for its records: the detail comes under the tally, past the lists, as soon as it is on screen.
  useEffect(() => {
    if (selectedId !== null && loaded) bringIntoView(panel.current?.querySelector('.detail'));
  }, [selectedId, loaded]);
  const openFinding = useCallback((id: string) => {
    if (id === selectedId) bringIntoView(panel.current?.querySelector('.detail'));
    else onSelect(id);
  }, [onSelect, selectedId]);
  // Reset puts the released file index back: the parts are read again (a published part loses its file) and the rules run again.
  const reset = useCallback(() => {
    data.parts.retry(ALL);
    onRunRules();
  }, [data.parts, onRunRules]);

  const selected = interfaces?.find((i) => i.id === selectedId) ?? null;
  // The selection belongs to the product on screen: an id its interface list does not hold (the
  // product changed, or a link named another product's interface) is cleared once the list has answered.
  useEffect(() => {
    if (interfaces && selectedId !== null && !interfaces.some((i) => i.id === selectedId)) onSelect(null);
  }, [interfaces, selectedId, onSelect]);
  const diffRequest = configuration.group ? scoped(`/api/query/variant-diff?group=${configuration.group.key}&option=${option}`, productKey) : null;
  const request = ownItf && diffRequest ? diffRequest
    : selected ? scoped(`/api/query/interfaces/${selected.id}`, productKey) : diffRequest ?? rooted('/api/query/interfaces', productKey, root);
  const envelope: Envelope | undefined = selected ? (one.state !== 'error' ? one.data : undefined) : diff ?? list.data;
  const openCard = open && open !== SPARQL && selected ? open : null;
  const toggleCard = (card: string) => setOpen((o) => (o === card ? null : card));

  return (
    <div className="check">
      <div className="check-stage">
        <LensStrip parts={drawn} lens={lens} onLens={setLens} />
        {parts.state === 'error' ? (
          <div className="stage-error">
            <ErrorState title="The product's parts could not be loaded" error={parts.error} onRetry={() => data.parts.retry(ALL)} />
          </div>
        ) : (
          <Viewer
            product={data.product}
            placements={placements}
            parts={drawn}
            refetchParts={refetchParts}
            interfaces={interfaces}
            selected={selected}
            onSelect={toggle}
            onShowFindings={revealFindings}
            view={view}
            onClearView={onClearView}
            selectedPart={selectedPart}
            onSelectPart={onSelectPart}
            lens={lens}
          />
        )}
        <ChangesStrip data={data} onReset={reset} />
        <PathStrip
          request={request}
          failed={(selected ? one : list).state === 'error'}
          envelope={envelope}
          sparqlOpen={open === SPARQL}
          onToggleSparql={() => setOpen((o) => (o === SPARQL ? null : SPARQL))}
          openCard={openCard}
          onOpenCard={selected ? toggleCard : null}
        >
          {open === SPARQL && envelope ? (
            <SparqlDrawer request={request} sparql={envelope.sparql} onClose={() => setOpen(null)} />
          ) : openCard && selected ? (
            <EvidenceDrawer data={data} itf={selected} card={openCard} envelope={envelope} tabRequest={tabRequest} onClose={() => setOpen(null)} />
          ) : null}
        </PathStrip>
      </div>
      <aside ref={panel} className="panel" aria-label="Interface check results">
        {list.state === 'error' ? (
          <ErrorState title="The interface check did not run" error={list.error} onRetry={() => data.interfaces.retry(ALL)} />
        ) : configuration.diff?.state === 'error' && configuration.group ? (
          <ErrorState title={t('check.variants.diffFailed')} error={configuration.diff.error}
            onRetry={() => data.variantDiff.retry(`${configuration.group!.key}|${option}`)} />
        ) : !interfaces ? (
          <Loading what="Running the ontology rules over the four PLMs" />
        ) : (
          <>
            <Tally interfaces={interfaces} findings={list.data?.findings ?? {}} selectedId={selectedId} onSelect={toggle} onShowFindings={revealFindings} />
            <ReferencesSummary data={data} product={data.product?.key ?? null} />
            <Untagged iris={list.data?.policy.untagged ?? []} />
            <FailList interfaces={interfaces} selectedId={selectedId} onSelect={toggle} />
            <ProductFindings data={data} onRerun={onRunRules} />
            <FindingsList interfaces={interfaces} selectedId={selectedId} onOpen={openFinding} reveal={reveal} />
            <Purchasing data={data} onRerun={onRunRules} />
            <Variants data={data} onOption={onOption} />
            {selected ? (
              <InterfaceDetail itf={selected} detail={one} data={data} onRerun={onRunRules} />
            ) : (
              <p className="quiet hint">{t('check.interface-check.selectAnInterfaceToSeeWhich')}</p>
            )}
          </>
        )}
      </aside>
    </div>
  );
}
