// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { plmCode } from '../ui/plm';
import { t } from '../i18n';
import { occurrenceLabel, type SelectedPart, type ViewState } from './view';

interface Props {
  view: ViewState;
  /** The eyebrow of the view note; the agent's view unless named. */
  label?: string;
  onClearView: () => void;
  selectedPart: SelectedPart | null;
  /** The occurrence of the selected part that was clicked, when the part is drawn more than once. */
  occurrence: { occurrence: number; count: number } | null;
  onClearPart: () => void;
}

/** Over the viewer: what the agent is showing and why, and the part the person selected, each with its way back. */
export function ViewNotes({ view, label, onClearView, selectedPart, occurrence, onClearPart }: Props) {
  const showing = view.isolate
    ? counted(view.isolate.ids.length, 'viewer.view-notes.partIsolated', 'viewer.view-notes.partsIsolated')
    : view.highlight.length
      ? counted(view.highlight.length, 'viewer.view-notes.partOutlined', 'viewer.view-notes.partsOutlined')
      : null;
  return (
    <>
      {showing ? (
        <div className="view-note" role="status">
          <span className="eyebrow">{label ?? t('viewer.view-notes.agentView')}</span>
          <span className="view-note-text" title={view.caption ? `${view.caption} · ${showing}` : showing}>
            {view.caption || showing}
            {view.caption ? <span className="quiet"> · {showing}</span> : null}
          </span>
          <button type="button" className="view-note-clear" onClick={onClearView}>{t('viewer.view-notes.normalView')}</button>
        </div>
      ) : null}
      {selectedPart ? (
        <div className="view-selected">
          <span className="eyebrow">{t('viewer.view-notes.selected')}</span>
          <b className="view-selected-plm">{plmCode(selectedPart.plm)}</b>
          <span className="view-selected-name">{selectedPart.name}</span>
          <span className="mono">{selectedPart.id}</span>
          {occurrence ? <span className="view-selected-occurrence">{occurrenceLabel(occurrence.occurrence, occurrence.count)}</span> : null}
          <button type="button" className="view-selected-clear" aria-label={t('viewer.view-notes.clearTheSelection')} onClick={onClearPart}>
            ×
          </button>
        </div>
      ) : null}
    </>
  );
}

const counted = (n: number, one: string, many: string) => `${n} ${t(n === 1 ? one : many)}`;
