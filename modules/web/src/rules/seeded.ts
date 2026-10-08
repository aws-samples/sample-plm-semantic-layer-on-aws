// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// The defects each product file of data/products seeds, read at build time. Each glob imports one
// named key of the JSON files, so the bundle carries these lists and not the product data.

interface Named { key: string; name: string }
type Entry = { rule: string } & Record<string, unknown>;

const named = import.meta.glob<Named>('../../../../data/products/*.json', { eager: true, import: 'product' });
const defects = import.meta.glob<Entry[] | undefined>('../../../../data/products/*.json', { eager: true, import: 'seededDefects' });
const references = import.meta.glob<Entry[] | undefined>('../../../../data/products/*.json', { eager: true, import: 'seededReferenceDefects' });
const lifecycle = import.meta.glob<Entry[] | undefined>('../../../../data/products/*.json', { eager: true, import: 'seededLifecycleDefects' });
const meshes = import.meta.glob<Entry[] | undefined>('../../../../data/products/*.json', { eager: true, import: 'seededMeshDefects' });

export interface Seeded {
  product: string;
  name: string;
  /** The records the product file seeds a defect of the rule on: interfaces, local parts, items or driven gears. */
  ids: string[];
}

/** The record an entry of each list names. */
const ID_FIELD = [[defects, 'interface'], [references, 'localPart'], [lifecycle, 'item'], [meshes, 'driven']] as const;

/** Per product file, the records seeded for `rule`, of `product` alone when one is named; products that seed none are left out. */
export function seededFor(rule: string, product: string | null): Seeded[] {
  return Object.keys(named).sort().filter((file) => product === null || named[file].key === product).flatMap((file) => {
    const ids = ID_FIELD.flatMap(([list, field]) => (list[file] ?? []).filter((e) => e.rule === rule).map((e) => String(e[field])));
    return ids.length ? [{ product: named[file].key, name: named[file].name, ids }] : [];
  });
}
