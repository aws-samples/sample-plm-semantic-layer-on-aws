// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { useCallback, useEffect, useMemo, useState } from 'react';
import { getJson } from '../api/client';
import type { FlowResponse, PathsResponse, Step } from '../api/pathTypes';
import { optioned } from '../api/option';
import { rooted } from '../api/subtree';
import { ALL, useCached, usePlacements, type AppData } from '../api/store';
import { isPart, type PartsResponse } from '../api/types';
import { useResource } from '../api/useResource';
import { t } from '../i18n';
import { ErrorState, Loading } from '../ui/ErrorState';
import { NORMAL_VIEW, nextView, type ViewState } from '../viewer/view';
import { Viewer } from '../viewer/Viewer';
import { PathForm } from './PathForm';
import { FlowResult, PathsResult } from './PathResult';
import { pathRequest, type PathQuery } from './pathRoute';

interface Props {
  data: AppData;
  query: PathQuery | null;
  onQuery: (q: PathQuery | null) => void;
}

type Answer = PathsResponse | FlowResponse;
const isFlow = (a: Answer): a is FlowResponse => 'direction' in a;

/** Ids of the parts a set of steps names and of the interfaces it crosses; a hidden part is never named. */
function onSteps(steps: Step[]) {
  const parts = new Set<string>();
  const joints = new Set<string>();
  for (const s of steps) {
    for (const p of [s.from, s.to]) if (!p.redacted) parts.add(p.id);
    if (s.joint) joints.add(s.joint.id);
  }
  return { parts: [...parts], joints };
}

/**
 * Paths through the product: a bill of materials says what contains what, these say how the parts are joined (the
 * interfaces between two parts) and where power and motion go (the functional edges from a part, across the sites).
 * The view isolates the path's parts in their site colours over a ghost of the rest (the viewer's own isolation, the one
 * the agent's isolate_parts drives), with the path's joints marked in their rule status; the path is in the location hash, so a link opens it.
 */
export function PathsScreen({ data, query, onQuery }: Props) {
  const parts = useCached(data.parts, ALL);
  const placements = usePlacements(data);
  const list = useCached(data.interfaces, ALL);
  const product = data.product?.key ?? null;
  const request = query && product ? pathRequest(query, product) : null;
  const [answer, reload] = useResource<Answer>(request ? `${data.profile}|${data.runKey}|${request}` : null,
    () => getJson<Answer>(data.config, request!, data.profile));
  const [shown, setShown] = useState(0);
  const [picked, setPicked] = useState<{ id: string; n: number } | null>(null);
  const pick = useCallback((id: string) => setPicked((p) => ({ id, n: (p?.n ?? 0) + 1 })), []);
  const ask = useCallback((q: PathQuery | null) => {
    setShown(0);
    onQuery(q);
  }, [onQuery]);
  const refetchParts = useCallback(
    () => getJson<PartsResponse>(data.config, optioned(rooted('/query/parts', product, data.root), data.option), data.profile).then((r) => r.parts),
    [data.config, data.profile, product, data.root, data.option],
  );

  const current = answer.state === 'ready' && request ? answer.data : null;
  const on = useMemo(() => onSteps(!current ? [] : isFlow(current) ? current.steps
    : (current.paths[Math.min(shown, current.paths.length - 1)]?.steps ?? [])), [current, shown]);
  const joints = useMemo(() => list.data?.interfaces.filter((i) => on.joints.has(i.id)), [list.data, on]);
  const visible = useMemo(() => (parts.data?.parts ?? []).filter(isPart), [parts.data]);
  // The path's parts in their colours and every other part as their faded context. The isolation is compared by
  // content: a new answer naming the same parts keeps the camera where it is.
  const isolated = useMemo(() => {
    if (!on.parts.length) return '';
    const path = new Set(on.parts);
    return `${on.parts.join(',')}|${visible.map((p) => p.id).filter((id) => !path.has(id)).join(',')}`;
  }, [on, visible]);
  const [view, setView] = useState<ViewState>(NORMAL_VIEW);
  useEffect(() => {
    const [ids, context] = isolated.split('|');
    setView((v) => nextView(v, ids ? { kind: 'isolate', ids: ids.split(','), contextIds: context ? context.split(',') : [], caption: t('paths.paths-screen.pathShown') } : { kind: 'clear' }));
  }, [isolated]);

  return (
    <div className="check paths">
      <div className="check-stage">
        {parts.state === 'error' ? (
          <div className="stage-error">
            <ErrorState title="The product's parts could not be loaded" error={parts.error} onRetry={() => data.parts.retry(ALL)} />
          </div>
        ) : (
          <Viewer
            product={data.product}
            placements={placements}
            parts={parts.data?.parts}
            refetchParts={refetchParts}
            interfaces={joints}
            selected={null}
            onSelect={() => {}}
            onShowFindings={() => {}}
            view={view}
            viewLabel={t('paths.path-result.path')}
            onClearView={() => ask(null)}
            selectedPart={null}
            onSelectPart={(p) => p && pick(p.id)}
          />
        )}
      </div>
      <aside className="panel" aria-label={t('paths.paths-screen.pathsThroughTheProduct')}>
        <p className="eyebrow">{t('paths.paths-screen.pathsThroughTheProduct')}</p>
        <PathForm query={query} parts={visible} picked={picked} onQuery={ask} />
        {!request ? (
          <p className="quiet hint">{t('paths.paths-screen.nameAPart')}</p>
        ) : answer.state === 'error' ? (
          <ErrorState title={t('paths.paths-screen.thePathCouldNotBeFound')} error={answer.error} onRetry={reload} />
        ) : answer.state === 'loading' ? (
          <Loading what={t('paths.paths-screen.walkingTheProduct')} />
        ) : isFlow(answer.data) ? (
          <FlowResult answer={answer.data} />
        ) : (
          <PathsResult answer={answer.data} shown={Math.min(shown, Math.max(0, answer.data.paths.length - 1))} onShow={setShown} />
        )}
        {request ? (
          <button type="button" className="btn btn-small path-clear" onClick={() => ask(null)}>{t('paths.paths-screen.showWholeProduct')}</button>
        ) : null}
      </aside>
    </div>
  );
}
