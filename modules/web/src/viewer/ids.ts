// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// The part ids an agent's viewer command names, resolved against the parts of what is loaded: exactly, or else by
// the normalised form (upper case, without hyphens or spaces) when exactly one loaded part has it, so UK3501 shows
// UK-3501. An id that matches no part, or two, is left out. The agent resolves the same way and tells the model.
import type { ViewState } from './view';

const normalised = (id: string) => id.replace(/[\s\-‐-―]/g, '').toUpperCase();

export function resolveIds(ids: string[], known: ReadonlySet<string>): string[] {
  const byForm = new Map<string, string[]>();
  for (const k of known) byForm.set(normalised(k), [...(byForm.get(normalised(k)) ?? []), k]);
  return [...new Set(ids.flatMap((id) => {
    if (known.has(id)) return [id];
    const same = byForm.get(normalised(id)) ?? [];
    return same.length === 1 ? same : [];
  }))];
}

/** The view with every part id it names resolved against `known`; with nothing loaded yet, the view as it came. */
export function resolveView(view: ViewState, known: ReadonlySet<string>): ViewState {
  if (known.size === 0) return view;
  const zoom = view.zoom ? resolveIds([view.zoom], known)[0] ?? null : null;
  return {
    ...view,
    highlight: resolveIds(view.highlight, known),
    isolate: view.isolate ? { ids: resolveIds(view.isolate.ids, known), contextIds: resolveIds(view.isolate.contextIds, known) } : null,
    zoom,
  };
}
