// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { ALL, type AppData } from '../../api/store';

/**
 * Once a release has landed, the answers holding the sites' own values are read again where they were read: the parts,
 * the bill of materials and the suppliers. The rule answers, references and change list follow the next run.
 */
export function rereadSites(data: AppData) {
  for (const cache of [data.parts, data.bom, data.suppliers]) if (cache.get(ALL)) cache.retry(ALL);
}
