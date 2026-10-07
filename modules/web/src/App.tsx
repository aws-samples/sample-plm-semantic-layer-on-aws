// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { lazy, Suspense, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { getJson } from './api/client';
import { pickProduct, withRules } from './api/product';
import { DEFAULT_PROFILE, type ProfileId } from './api/profile';
import { useAppData } from './api/store';
import type { Product, ProductListResponse, ProductsResponse, Subtree } from './api/types';
import { useResource } from './api/useResource';
import { Architecture } from './architecture/Architecture';
import type { AgentAction, EvidenceRequest } from './ask/types';
import { AskPanel } from './ask/AskPanel';
import { lastCompletedTurn, useAsk } from './ask/useAsk';
import { BillOfMaterials } from './bom/BillOfMaterials';
import { DataCatalogue } from './catalogue/DataCatalogue';
import { InterfaceCheck } from './check/InterfaceCheck';
import { PathsScreen } from './paths/PathsScreen';
import type { RuntimeConfig } from './config';
import { ErrorState, Loading } from './ui/ErrorState';
import { TopBar } from './ui/TopBar';
import { ROUTED_SCREENS, useHashRoute, type Route } from './ui/useHashRoute';
import { SubtreeBar } from './subtree/SubtreeBar';
import { subtreeOf } from './subtree/subtreeOf';
import { useSubtreeRoot } from './subtree/useSubtreeRoot';
import { lensOf, withLens } from './lens/lensHash';
import { useOption } from './variant/useOption';
import { SvgDefs } from './viewer/KindGlyph';
import { NORMAL_VIEW, nextView, type SelectedPart, type Selection, type ViewCommand, type ViewState } from './viewer/view';
import { t } from './i18n';

// React Flow only downloads when the Data flow tab opens.
const DataFlow = lazy(() => import('./flow/DataFlow').then((m) => ({ default: m.DataFlow })));
// The Rules screen carries the seeded lists of every product file; it downloads when its tab opens.
const RulesScreen = lazy(() => import('./rules/RulesScreen').then((m) => ({ default: m.RulesScreen })));

export function App({ config, configError }: { config: RuntimeConfig | null; configError: Error | null }) {
  const [route, go] = useHashRoute();
  const [runKey, setRunKey] = useState(0);
  const [profile, setProfile] = useState<ProfileId>(DEFAULT_PROFILE);
  // The products with the number of parts this profile may see, read again on each run; the selection outlives a profile change.
  const listKey = config ? `${profile}|${runKey}` : null;
  const [list, reloadList] = useResource(listKey, () => getJson<ProductListResponse>(config!, '/query/products/list', profile));
  // While a profile's list is read again the screens stay on the last list, so a profile change
  // keeps them mounted: the viewer's scene and every cached answer survive it.
  const [listing, setListing] = useState<ProductListResponse | null>(null);
  // The key the last list answered for: just after a profile change or a run, `list` still holds the previous answer.
  const [listedKey, setListedKey] = useState<string | null>(null);
  if (list.state === 'ready' && list.data !== listing) {
    setListing(list.data);
    setListedKey(listKey);
  }
  // The product rules' findings follow this key's list, so neither the list nor the screens wait for the rules.
  const [ruled] = useResource(listKey !== null && listedKey === listKey ? listKey : null, () => getJson<ProductsResponse>(config!, '/query/products', profile));
  const rules = ruled.state === 'ready' && ruled.data.policy.profile === profile ? ruled.data : null;
  const listed = useMemo(() => (listing ? withRules(listing.products, rules?.products ?? []) : null), [listing, rules]);
  // The viewer's pick wins; before one, the product the deployment names in config.json
  // (which arrives after the first render), else the first listed.
  const [selected, setSelected] = useState<string | null>(null);
  const product = useMemo(
    () => (listed ? pickProduct(listed, selected ?? config?.defaultProduct ?? null) : null),
    [listed, selected, config],
  );
  const [root, setRoot] = useSubtreeRoot();
  const [option, setOption] = useOption();
  // The root and the option belong to the product: another product opens whole and at its defaults, unless the agent
  // switched product to open one of its subtrees. The first product to arrive keeps the root and option a link or a reload named.
  const shown = useRef<string | null>(null);
  const rootAfterSwitch = useRef<string | null>(null);
  useEffect(() => {
    const key = product?.key ?? null;
    if (key === null || key === shown.current) return;
    if (shown.current !== null) {
      setRoot(rootAfterSwitch.current);
      setOption(null);
    }
    rootAfterSwitch.current = null;
    shown.current = key;
  }, [product, setRoot, setOption]);
  // What the agent shows in the viewer and the part the person picked belong to what is loaded: a
  // product or subtree change puts back the normal view and drops the selection.
  const [view, setView] = useState<ViewState>(NORMAL_VIEW);
  const [selectedPart, setSelectedPart] = useState<SelectedPart | null>(null);
  const command = useCallback((c: ViewCommand) => setView((v) => nextView(v, c)), []);
  const resetView = useCallback(() => {
    setView((v) => (v.highlight.length || v.isolate || v.zoom ? nextView(v, { kind: 'clear' }) : v));
    setSelectedPart(null);
  }, []);
  // A subtree is of the base product: opening one puts every variant group back at its default.
  const changeRoot = useCallback((next: string | null) => {
    resetView();
    setRoot(next);
    if (next) setOption(null);
  }, [resetView, setRoot, setOption]);
  const changeProduct = useCallback((key: string) => {
    if (key === product?.key) return;
    resetView();
    setSelected(key);
  }, [product, resetView]);
  // The agent's open_subtree and open_product; a product the list does not hold is no switch.
  const keys = useMemo(() => new Set(listed?.map((p) => p.key) ?? []), [listed]);
  const openSubtree = useCallback((next: string, key: string | null) => {
    if (key && keys.has(key) && key !== product?.key) {
      rootAfterSwitch.current = next;
      changeProduct(key);
    } else changeRoot(next);
  }, [keys, product, changeProduct, changeRoot]);
  const openProduct = useCallback((key: string) => {
    if (!keys.has(key)) return;
    if (key !== product?.key) changeProduct(key);
    else changeRoot(null);
  }, [keys, product, changeProduct, changeRoot]);
  const [subtree, setSubtree] = useState<Subtree | null>(null);
  const [lastCheckId, setLastCheckId] = useState<string | null>(null);
  const [askOpen, setAskOpen] = useState(false);
  const runRules = useCallback(() => setRunKey((k) => k + 1), []);
  // A record opened from another screen names its product and, for a part, its root: marking the
  // product as shown first keeps the root the hash names through the product switch. The lens stays, as on a tab change.
  const openRecord = useCallback((productKey: string, hash: string) => {
    resetView();
    shown.current = productKey;
    setSelected(productKey);
    location.hash = withLens(hash, lensOf(location.hash));
  }, [resetView]);
  useEffect(() => {
    if (route.screen === 'check') setLastCheckId(route.interfaceId);
  }, [route]);

  return (
    <div className={`app${root ? ' has-subtree' : ''}`}>
      <SvgDefs />
      <TopBar
        route={route}
        root={root}
        envName={config?.envName ?? null}
        lastCheckId={lastCheckId}
        profile={profile}
        onProfile={setProfile}
        products={list}
        rules={ruled.state === 'error' ? ruled : null}
        product={product}
        onProduct={changeProduct}
        onRunRules={runRules}
        askOpen={askOpen}
        onToggleAsk={() => setAskOpen((o) => !o)}
      />
      {root ? <SubtreeBar product={product} root={root} subtree={subtree?.root === root ? subtree : null} onWholeProduct={() => changeRoot(null)} /> : null}
      <main className="app-main">
        {configError ? (
          <div className="stage-error">
            <ErrorState title="This site's runtime configuration is missing" error={configError} onRetry={() => location.reload()} />
          </div>
        ) : !config ? null : list.state === 'error' ? (
          <div className="stage-error">
            <ErrorState title="The products could not be loaded" error={list.error} onRetry={reloadList} />
          </div>
        ) : !listed ? (
          <div className="stage-error">
            <Loading what="Reading the products" />
          </div>
        ) : (
          <Screens
            config={config}
            route={route}
            go={go}
            runKey={runKey}
            onRunRules={runRules}
            onOpenRecord={openRecord}
            profile={profile}
            products={listed}
            product={product}
            root={root}
            onRoot={changeRoot}
            option={root ? null : option}
            onOption={setOption}
            onSubtree={setSubtree}
            view={view}
            onView={command}
            selectedPart={selectedPart}
            onSelectPart={setSelectedPart}
            onOpenSubtree={openSubtree}
            onOpenProduct={openProduct}
            lastCheckId={lastCheckId}
            askOpen={askOpen}
            onCloseAsk={() => setAskOpen(false)}
          />
        )}
      </main>
    </div>
  );
}

interface ScreensProps {
  config: RuntimeConfig;
  route: Route;
  go: (r: Route) => void;
  runKey: number;
  onRunRules: () => void;
  /** Selects `productKey` and opens `hash` (a route with its root, if any) without the product switch clearing the root. */
  onOpenRecord: (productKey: string, hash: string) => void;
  profile: ProfileId;
  products: Product[];
  product: Product | null;
  root: string | null;
  onRoot: (root: string | null) => void;
  /** The variant option the product is shown under; null is the base product. */
  option: string | null;
  onOption: (option: string | null) => void;
  /** Reports the subtree block of the rooted answers on screen, for the breadcrumb. */
  onSubtree: (s: Subtree | null) => void;
  lastCheckId: string | null;
  askOpen: boolean;
  onCloseAsk: () => void;
  view: ViewState;
  onView: (c: ViewCommand) => void;
  selectedPart: SelectedPart | null;
  onSelectPart: (p: SelectedPart | null) => void;
  onOpenSubtree: (root: string, product: string | null) => void;
  onOpenProduct: (product: string) => void;
}

function Screens(props: ScreensProps) {
  const { config, route, go, runKey, onRunRules, onOpenRecord, profile, products, product, root, onRoot, option, onOption, onSubtree, lastCheckId, askOpen, onCloseAsk } = props;
  const { view, onView, selectedPart, onSelectPart, onOpenSubtree, onOpenProduct } = props;
  const select = useCallback((id: string | null) => go({ screen: 'check', interfaceId: id }), [go]);
  // The viewer and loading tools of the agent: what the viewer shows, the subtree, the product, the screen.
  const onAction = useCallback((a: AgentAction) => {
    if (a.kind === 'view') onView(a.command);
    else if (a.kind === 'subtree') onOpenSubtree(a.root, a.product);
    else if (a.kind === 'product') onOpenProduct(a.product);
    else if (a.screen === 'check') go({ screen: 'check', interfaceId: lastCheckId });
    else if (ROUTED_SCREENS.includes(a.screen)) go({ screen: a.screen } as Route);
  }, [onView, onOpenSubtree, onOpenProduct, go, lastCheckId]);
  // Every question carries what is on screen and selected, so "this part" and "here" resolve.
  const interfaceId = route.screen === 'check' ? route.interfaceId : null;
  const selection = useMemo<Selection>(() => ({
    product: product?.key ?? null,
    ...(root ? { root } : {}),
    part: selectedPart,
    interface: interfaceId ? { id: interfaceId } : null,
  }), [product, root, selectedPart, interfaceId]);
  // The conversation outlives the Interface check screen; its answers select interfaces by route,
  // and its last completed turn is the store's, so the Data flow tab draws the same object the panel shows.
  const [evidenceRequest, setEvidenceRequest] = useState<EvidenceRequest | null>(null);
  const ask = useAsk(profile, product, selection, { onSelect: select, onEvidence: setEvidenceRequest, onAction });
  const lastTurn = useMemo(() => lastCompletedTurn(ask.turns, profile), [ask.turns, profile]);
  const data = useAppData(config, runKey, profile, product, root, option, lastCheckId, lastTurn);
  const subtree = subtreeOf(data);
  useEffect(() => onSubtree(subtree), [onSubtree, subtree]);
  const screen = (() => {
    if (route.screen === 'paths') return <PathsScreen data={data} query={route.query} onQuery={(query) => go({ screen: 'paths', query })} />;
    if (route.screen === 'bom') return <BillOfMaterials data={data} onOpenSubtree={onRoot} onRunRules={onRunRules} selectedPart={selectedPart} onSelectPart={onSelectPart} />;
    if (route.screen === 'catalogue') return <DataCatalogue key={route.term ?? ''} data={data} term={route.term} />;
    if (route.screen === 'rules') {
      return (
        <Suspense fallback={<div className="stage-error"><Loading what={t('rules.tab.loadingTheRules')} /></div>}>
          <RulesScreen data={data} products={products} ruleName={route.ruleName} onOpenRecord={onOpenRecord} />
        </Suspense>
      );
    }
    if (route.screen === 'architecture') return <Architecture data={data} />;
    if (route.screen === 'flow') {
      return (
        <Suspense fallback={<div className="panel"><Loading what="Loading the data flow diagram" /></div>}>
          <DataFlow data={data} />
        </Suspense>
      );
    }
    return (
      <InterfaceCheck
        data={data}
        selectedId={route.interfaceId}
        onSelect={select}
        evidenceRequest={evidenceRequest}
        onRunRules={onRunRules}
        onOption={onOption}
        view={view}
        onClearView={() => onView({ kind: 'clear' })}
        selectedPart={selectedPart}
        onSelectPart={onSelectPart}
      />
    );
  })();
  // The Ask panel is a column beside whichever screen is open: the conversation and its answers stay as the tabs change,
  // and opening it keeps the screen mounted (the viewer's scene and camera with it).
  return (
    <div className={`with-ask${askOpen ? ' is-open' : ''}`}>
      <div className="with-ask-screen">{screen}</div>
      {askOpen ? <AskPanel ask={ask} data={data} profile={profile} selectedId={interfaceId} onSelect={select} onClose={onCloseAsk} /> : null}
    </div>
  );
}
