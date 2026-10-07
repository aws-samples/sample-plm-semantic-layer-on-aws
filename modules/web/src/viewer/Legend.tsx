// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import type { PartEntry, Placements } from '../api/types';
import { isPart } from '../api/types';
import { PLM_ORDER, SUPPLIER_CSS, cssColour, plmCode } from '../ui/plm';
import { passes, type Lens } from '../lens/lens';
import { KindGlyph } from './KindGlyph';
import { t } from '../i18n';

interface Props {
  parts: PartEntry[] | undefined;
  /** The parts' occurrences; a part the answer does not list counts once. Null draws every part once. */
  placements: Placements | null;
  /** Only the parts inside the lens are counted. */
  lens: Lens;
  /** Coordinate frame of the selected product, shown on the note's tooltip; null until the products have answered. */
  frame: string | null;
  /** The unpublished line: scrolls to the release findings in the results panel. */
  onShowFindings: () => void;
}

export function Legend({ parts: all, placements, lens, frame, onShowFindings }: Props) {
  const drawn = all?.filter((p) => passes(p, lens));
  // The counts are the subtree's own parts; the context parts drawn around it are counted apart.
  const parts = drawn?.filter((p) => !isPart(p) || !p.context);
  const context = drawn ? drawn.length - parts!.length : 0;
  const hidden = parts?.filter((p) => p.redacted).length ?? 0;
  const supplied = parts?.filter((p) => isPart(p) && p.supplier).length ?? 0;
  const unpublished = parts?.filter((p) => isPart(p) && p.cadFile === null).length ?? 0;
  const counts = new Map(placements?.parts.map((p) => [p.id, p.occurrences.length]));
  const numbers = parts?.filter(isPart) ?? [];
  const occurrences = numbers.reduce((n, p) => n + (counts.get(p.id) ?? 1), 0);
  return (
    <div className="legend" aria-label="Legend">
      <div className="legend-group">
        {PLM_ORDER.map((plm) => {
          const n = parts?.filter((p) => isPart(p) && plmCode(p.plm) === plm).length;
          return (
            <span key={plm} className="legend-item">
              <i className="swatch" style={{ background: cssColour(plm) }} />
              {plm}
              {n !== undefined ? <span className="legend-count">{n} {n === 1 ? 'part' : 'parts'}</span> : null}
            </span>
          );
        })}
        {supplied ? (
          <span className="legend-item is-supplier">
            <i className="swatch" style={{ background: SUPPLIER_CSS }} />
            supplier-built
            <span className="legend-count">{supplied} {supplied === 1 ? 'part' : 'parts'}</span>
          </span>
        ) : null}
        {unpublished ? (
          <button type="button" className="legend-item legend-link is-unpublished" title="Show the release findings" onClick={onShowFindings}>
            <i className="swatch swatch-phantom" />
            {unpublished} {unpublished === 1 ? 'part' : 'parts'} without published CAD
          </button>
        ) : null}
        {context ? (
          <span className="legend-item is-context">
            <i className="swatch swatch-context" />
            {context} {context === 1 ? t('viewer.legend.contextPart') : t('viewer.legend.contextParts')}
          </span>
        ) : null}
        {placements && numbers.length ? (
          <span className="legend-item is-total">
            {numbers.length} {t(numbers.length === 1 ? 'viewer.legend.partNumber' : 'viewer.legend.partNumbers')},{' '}
            {occurrences} {t(occurrences === 1 ? 'viewer.legend.occurrence' : 'viewer.legend.occurrences')}
          </span>
        ) : null}
        {hidden ? (
          <span className="legend-item is-redacted">
            <i className="swatch swatch-hatched" />
            {hidden} {hidden === 1 ? 'part' : 'parts'} hidden by export control
          </span>
        ) : null}
      </div>
      <div className="legend-group">
        <span className="legend-item"><KindGlyph kind="plug" status="outline" />plug</span>
        <span className="legend-item"><KindGlyph kind="fastener" status="outline" />fastener</span>
        <span className="legend-item"><KindGlyph kind="coupling" status="outline" />coupling</span>
      </div>
      <div className="legend-group">
        <span className="legend-item"><i className="dot is-pass" />interface passes</span>
        <span className="legend-item"><i className="dot is-fail" />in a violation</span>
        <span className="legend-item"><KindGlyph kind="plug" status="not-evaluable" />not evaluable, other side redacted</span>
      </div>
      <p className="legend-note" title={frame ?? undefined}>{t('viewer.legend.geometryAndFrameOfTheSelected')}</p>
    </div>
  );
}
