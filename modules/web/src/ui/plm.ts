// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// Owning-source identity used by the viewer, legends, records and the data flow.
// The four PLMs each have a site colour; the Atelier core store is a fifth source
// that is not a PLM, so it takes the ink family and the Atelier owner mark instead.
export const PLM_ORDER = ['FR', 'DE', 'UK', 'ES'] as const;
export type PlmCode = (typeof PLM_ORDER)[number];

/** Route segment and owner code of the Atelier core store (`/api/core/*`, `ontop-core`, `atelier_core`). */
export const CORE = 'core';

export const isCore = (owner: string) => owner.toLowerCase() === CORE || owner.toUpperCase() === 'ATELIER';

export const PLM_COLOUR: Record<string, number> = {
  FR: 0x8474d6,
  DE: 0xd9a441,
  UK: 0x5a91c8,
  ES: 0x49aaa2,
};

/** Slate of the ink family: the colour of everything Atelier owns. */
export const CORE_COLOUR = 0x394855;

/** Primer green: a supplier-built part arrives at the line in anti-corrosion primer, whichever PLM integrates it. */
export const SUPPLIER_COLOUR = 0xa3ad4a;
export const SUPPLIER_CSS = `#${SUPPLIER_COLOUR.toString(16)}`;

export const PLM_NAME: Record<string, string> = {
  FR: 'France',
  DE: 'Germany',
  UK: 'United Kingdom',
  ES: 'Spain',
  Atelier: 'Atelier core',
};

export const plmCode = (plm: string) => (isCore(plm) ? 'Atelier' : plm.toUpperCase());
export const cssColour = (plm: string) =>
  `#${(isCore(plm) ? CORE_COLOUR : (PLM_COLOUR[plmCode(plm)] ?? 0x8b959e)).toString(16).padStart(6, '0')}`;

/** "FR PLM" for a site source, "Atelier core" for the Atelier-owned one. */
export const ownerLabel = (plm: string) => (isCore(plm) ? 'Atelier core' : `${plmCode(plm)} PLM`);

/** Database name of a source on the shared Aurora cluster. */
export const dbName = (plm: string) => (isCore(plm) ? 'atelier_core' : `${plm.toLowerCase()}_plm`);

/** Every source the browser reads a catalogue and a mapping from: the configured PLMs, then the Atelier core store. */
export const sourcesOf = (plms: string[]) => [...plms, CORE];
