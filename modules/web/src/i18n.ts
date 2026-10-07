// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import en from './i18n/en.json';

/**
 * Localisation seam of the browser application.
 *
 * Every user-visible string in the components passes through `t` with a stable key of the form
 * `<area>.<component>.<slug>` (`viewer.legend.fitView`): the area is the source directory, the
 * component the file, the slug the opening words of the English sentence. The catalogue
 * `i18n/en.json` maps each key to the sentence the screen shows; English is the only locale
 * today, so there is no locale selection and no library. A key the catalogue does not hold is
 * returned as is, so a missing entry shows on screen instead of failing silently.
 */
const catalogue: Record<string, string> = en;

export function t(key: string): string {
  return catalogue[key] ?? key;
}
