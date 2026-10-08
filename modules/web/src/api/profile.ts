// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// Viewer profiles stand in for identity-provider claims (docs/contract.md, "Export control").
// Ids and labels are those of ontology/policy.json; the dev fixtures check they still agree.

export type ProfileId = 'fr-engineer' | 'de-engineer' | 'uk-engineer' | 'es-engineer' | 'programme-cleared' | 'export-officer';

export const PROFILES: { id: ProfileId; label: string }[] = [
  { id: 'fr-engineer', label: 'FR engineer' },
  { id: 'de-engineer', label: 'DE engineer' },
  { id: 'uk-engineer', label: 'UK engineer' },
  { id: 'es-engineer', label: 'ES engineer' },
  { id: 'programme-cleared', label: 'Programme cleared' },
  { id: 'export-officer', label: 'Export-control officer' },
];

export const DEFAULT_PROFILE: ProfileId = 'programme-cleared';

/** The demo operator: the one profile that may reset the demo data; the services answer 403 to the others. */
export const OFFICER: ProfileId = 'export-officer';

/** Request header the services read the profile from. */
export const PROFILE_HEADER = 'x-atelier-profile';

// Who may act (docs/contract.md, "Freshness and the change feed"): the system that owns a fact writes it, so its
// role releases it; the officer, the demo operator, may do everything. The services check the same rule again.

/** The engineer profile of a PLM: `fr-engineer` for a French row. */
export const engineerOf = (plm: string) => `${plm.toLowerCase()}-engineer`;
/** A value correction in a PLM's own table. */
export const mayCorrect = (profile: ProfileId, plm: string) => profile === OFFICER || profile === engineerOf(plm);
/** A CAD publication by a PLM: the PLM's fact, the same rule as a correction. */
export const mayPublishCad = mayCorrect;
/** A mating link, Atelier's fact: the integration role. */
export const mayPublishLink = (profile: ProfileId) => profile === OFFICER || profile === 'programme-cleared';
export const mayReset = (profile: ProfileId) => profile === OFFICER;
