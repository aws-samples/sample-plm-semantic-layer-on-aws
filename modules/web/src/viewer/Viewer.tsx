// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { useEffect, useMemo, useRef, useState } from 'react';
import { isPart, type Interface, type PartEntry, type Placements, type Product } from '../api/types';
import { failingFeatures, featureById, features, findings, num } from '../check/violations';
import { inScene, isActive, NO_LENS, passes, type Lens } from '../lens/lens';
import type { Dimension, Marker, MarkerStatus } from './overlay';
import { ProductScene, type LoadProgress } from './scene';
import { Legend } from './Legend';
import { ViewNotes } from './ViewNotes';
import type { Hit } from './picking';
import { resolveView } from './ids';
import type { SelectedPart, ViewState } from './view';
import { t } from '../i18n';

interface Props {
  /** The product on screen, for the view's labels and the legend's frame note; null until the products have answered. */
  product: Product | null;
  parts: PartEntry[] | undefined;
  /**
   * The world placements of the parts; undefined while they load, null when there are none (the
   * answer failed or no product is selected), and then every part draws once, as authored.
   */
  placements: Placements | null | undefined;
  /** Fresh parts, with new presigned URLs, when a CAD fetch fails. */
  refetchParts: () => Promise<PartEntry[]>;
  interfaces: Interface[] | undefined;
  selected: Interface | null;
  onSelect: (id: string | null) => void;
  /** The legend's unpublished line: scrolls to the release findings in the results panel. */
  onShowFindings: () => void;
  /** What the agent asked the viewer to show. */
  view: ViewState;
  onClearView: () => void;
  /** The part the person clicked, and the way to change it. */
  selectedPart: SelectedPart | null;
  onSelectPart: (part: SelectedPart | null) => void;
  /** Who the view note says is showing the view; the agent unless named. */
  viewLabel?: string;
  /** The parts outside the lens are faded and left out of the legend's counts; no lens fades none. */
  lens?: Lens;
}

export function Viewer({ product, parts: listed, placements, refetchParts, interfaces, selected, onSelect, onShowFindings, view: asked, onClearView, selectedPart, onSelectPart, viewLabel, lens = NO_LENS }: Props) {
  const parts = useMemo(() => listed?.filter(inScene), [listed]);
  // The agent's ids resolved against the loaded parts: UK3501 shows UK-3501.
  const known = useMemo(() => new Set((parts ?? []).filter(isPart).map((p) => p.id)), [parts]);
  const view = useMemo(() => resolveView(asked, known), [asked, known]);
  const name = product?.name ?? 'product';
  const host = useRef<HTMLDivElement>(null);
  const loupe = useRef<HTMLDivElement>(null);
  const [scene, setScene] = useState<ProductScene | null>(null);
  const [progress, setProgress] = useState<LoadProgress | null>(null);
  /** The occurrence of the selected part the person clicked. */
  const [clicked, setClicked] = useState<Hit | null>(null);

  useEffect(() => {
    const s = new ProductScene(host.current!);
    setScene(s);
    return () => s.dispose();
  }, []);

  useEffect(() => {
    if (scene) scene.onPlugClick = (id) => onSelect(id);
  }, [scene, onSelect]);

  useEffect(() => {
    if (!scene) return;
    scene.onPartClick = (hit) => {
      const p = parts?.find((x) => isPart(x) && x.id === hit.id);
      if (!p || !isPart(p)) return;
      // Another occurrence of the selected part moves the selection to it; the same one clears it.
      const again = p.id === selectedPart?.id && clicked?.id === hit.id && clicked.occurrence === hit.occurrence;
      setClicked(again ? null : hit);
      onSelectPart(again ? null : { id: p.id, plm: p.plm, name: p.name });
    };
  }, [scene, parts, selectedPart, clicked, onSelectPart]);

  // The parts wait for their placements, so a part is drawn once, at all its occurrences.
  useEffect(() => {
    if (scene && parts && placements !== undefined) scene.loadParts(parts, placements, setProgress, refetchParts);
  }, [scene, parts, placements, refetchParts]);

  useEffect(() => {
    scene?.selectPart(selectedPart?.id ?? null);
  }, [scene, selectedPart]);

  useEffect(() => {
    scene?.setLens(isActive(lens) ? (p) => passes(p, lens) : null);
  }, [scene, lens]);

  // A new scene gets the whole view; a running one carries out the command that came last.
  const applied = useRef<{ scene: ProductScene; n: number } | null>(null);
  useEffect(() => {
    if (!scene || (applied.current?.scene === scene && applied.current.n === view.n)) return;
    const fresh = applied.current?.scene !== scene;
    applied.current = { scene, n: view.n };
    if (fresh) {
      if (view.highlight.length) scene.highlightParts(view.highlight);
      if (view.isolate) scene.isolateParts(view.isolate.ids, view.isolate.contextIds);
      else if (view.zoom) scene.zoomToPart(view.zoom);
      return;
    }
    if (view.last === 'clear') scene.clearView();
    else if (view.last === 'highlight') scene.highlightParts(view.highlight);
    else if (view.last === 'isolate' && view.isolate) scene.isolateParts(view.isolate.ids, view.isolate.contextIds);
    else if (view.last === 'zoom' && view.zoom) scene.zoomToPart(view.zoom);
  }, [scene, view]);

  // Joints of hidden parts would float in empty space: an isolated view marks the interfaces whose drawn parts it all shows.
  const marked = useMemo(() => {
    if (!view.isolate || !interfaces) return interfaces ?? [];
    const drawn = new Set([...view.isolate.ids, ...view.isolate.contextIds]);
    return interfaces.filter((i) => i.parts.every((p) => !isPart(p) || drawn.has(p.id)));
  }, [interfaces, view.isolate]);
  const markers = useMemo(() => buildMarkers(marked, selected), [marked, selected]);
  const dimension = useMemo(() => (selected ? positionDimension(selected) : null), [selected]);

  useEffect(() => {
    scene?.setMarkers(markers);
  }, [scene, markers]);

  useEffect(() => {
    if (!scene) return;
    scene.setLoupeElement(dimension ? loupe.current : null);
    const points = selected ? features(selected).flatMap((f) => (f.positionMm ? [f.positionMm] : [])) : [];
    scene.focus(selected ? { points, dimension } : null);
  }, [scene, selected, dimension]);

  const loading = progress && progress.loaded + progress.failed.length < progress.total;

  return (
    <section className="viewer" aria-label={`${name} 3D view`}>
      <div className="viewer-host" ref={host} />
      <Legend parts={parts} placements={placements ?? null} lens={lens} frame={product?.frame ?? null} onShowFindings={onShowFindings} />
      <div className="viewer-tools">
        <button type="button" className="tool" onClick={() => (selected ? onSelect(null) : scene?.home())}>
          {selected ? 'Show whole product' : 'Fit view'}
        </button>
      </div>
      <div className="viewer-foot">
        <ViewNotes view={view} label={viewLabel} onClearView={onClearView} selectedPart={selectedPart}
          occurrence={clicked && clicked.id === selectedPart?.id && clicked.count > 1 ? clicked : null} onClearPart={() => onSelectPart(null)} />
        {loading ? (
          <div className="viewer-status" role="status">
            {t('viewer.viewer.loadingGeometry')} <span className="mono">{progress.loaded}/{progress.total}</span> STEP files
          </div>
        ) : null}
        {progress && progress.failed.length > 0 ? (
          <div className="viewer-status is-error" role="alert">
            {progress.failed.map((f) => (
              <div key={f.cadFile}>
                <span className="mono">{f.cadFile}</span> did not load: {f.message}
              </div>
            ))}
          </div>
        ) : null}
      </div>
      {dimension && selected ? (
        <div className="loupe" ref={loupe}>
          <div className="loupe-title">
            <span className="loupe-letter">A</span>
            <span>{t('viewer.viewer.detailTrueScaleViewedSquareTo')} <span className="mono">{dimension.axis}</span></span>
          </div>
        </div>
      ) : null}
    </section>
  );
}

/**
 * Features named by a violation are red. A feature whose position has no unit cannot be
 * drawn, so its mate stands in for it at the joint and is drawn red too. On an interface the
 * profile cannot evaluate, the visible features are drawn hatched: their mates are redacted.
 */
function buildMarkers(interfaces: Interface[], selected: Interface | null): Marker[] {
  return interfaces.flatMap((itf) => {
    const bad = failingFeatures(itf);
    const shown = features(itf);
    const unplaced = new Set(shown.filter((f) => !f.positionMm && bad.has(f.id)).map((f) => f.id));
    const isSel = selected?.id === itf.id;
    const redacted = itf.status === 'not-evaluable';
    const hiddenPlm = [...new Set(itf.features.filter((f) => f.redacted).map((f) => f.plm.toUpperCase()))].join(', ');
    let explained = false;
    return shown.flatMap((f): Marker[] => {
      if (!f.positionMm) return [];
      const standIn = f.matesWith.find((m) => unplaced.has(m));
      const fail = bad.has(f.id) || standIn !== undefined;
      const status: MarkerStatus = redacted ? 'not-evaluable' : fail ? 'fail' : 'pass';
      let label: string | null = null;
      if (isSel && redacted) {
        label = explained ? f.id : `${f.id}, ${hiddenPlm} side not visible to your profile`;
        explained = true;
      } else if (isSel && fail) {
        label = standIn ? `${f.id}, mate ${standIn} has no position in mm` : f.id;
      }
      return [{
        id: `${itf.id}/${f.plm}/${f.id}`,
        interfaceId: itf.id,
        kind: f.kind,
        status,
        pos: f.positionMm,
        emphasis: selected ? (isSel ? 'selected' : 'dim') : 'none',
        label,
      }];
    });
  });
}

function positionDimension(itf: Interface): Dimension | null {
  const f = findings(itf).find((x) => x.rule === 'position');
  if (!f || f.features.length < 2) return null;
  const d = f.violations[0].detail;
  const [a, b] = f.features.map((id) => featureById(itf, id)?.positionMm);
  const axis = d.axis;
  const delta = num(d.deltaMm);
  if (!a || !b || delta === null || (axis !== 'x' && axis !== 'y' && axis !== 'z')) return null;
  return { a, b, aId: f.features[0], axis, deltaMm: delta, toleranceMm: num(d.toleranceMm) ?? itf.toleranceMm };
}
