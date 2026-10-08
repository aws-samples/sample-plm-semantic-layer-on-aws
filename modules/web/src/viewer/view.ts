// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// What the agent has asked the viewer to show, and what the person has picked in it. The view is
// cumulative (an outline stays while a zoom moves the camera) until a clear, a product change or a
// subtree change puts back the normal view; `n` tells each command from the one before.
import { t } from '../i18n';

/** One viewer command, as the agent's highlight_parts, isolate_parts, zoom_to_part and clear_view carry it. */
export type ViewCommand =
  | { kind: 'highlight'; ids: string[]; caption: string }
  | { kind: 'isolate'; ids: string[]; contextIds: string[]; caption: string }
  | { kind: 'zoom'; id: string }
  | { kind: 'clear' };

export interface ViewState {
  n: number;
  /** The command applied last; the viewer carries it out when `n` changes. */
  last: ViewCommand['kind'];
  highlight: string[];
  isolate: { ids: string[]; contextIds: string[] } | null;
  zoom: string | null;
  caption: string;
}

export const NORMAL_VIEW: ViewState = { n: 0, last: 'clear', highlight: [], isolate: null, zoom: null, caption: '' };

export function nextView(s: ViewState, c: ViewCommand): ViewState {
  const n = s.n + 1;
  if (c.kind === 'clear') return { ...NORMAL_VIEW, n };
  if (c.kind === 'highlight') return { ...s, n, last: c.kind, highlight: c.ids, caption: c.caption || s.caption };
  if (c.kind === 'isolate') return { ...s, n, last: c.kind, isolate: { ids: c.ids, contextIds: c.contextIds }, caption: c.caption || s.caption };
  return { ...s, n, last: c.kind, zoom: c.id };
}

/** "occurrence 3 of 6": which of a part's occurrences, 1 being the reference occurrence. */
export const occurrenceLabel = (index: number, count: number) => `${t('viewer.view.occurrence')} ${index} ${t('viewer.view.of')} ${count}`;

/** The part the person clicked in the viewer, as the Ask request carries it. */
export interface SelectedPart {
  id: string;
  plm: string;
  name: string;
}

/** What is on screen and selected, sent with every Ask request as `forwardedProps.selection`. */
export interface Selection {
  product: string | null;
  /** Present in subtree mode only. */
  root?: string;
  part: SelectedPart | null;
  interface: { id: string } | null;
}
