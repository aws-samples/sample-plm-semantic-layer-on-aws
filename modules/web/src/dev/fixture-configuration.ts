// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// The product under one option of a variant group, as GET /api/query/parts and /placements answer with `option=` in
// the dev server with VITE_FIXTURES=1, from `extended.variants` of data/products/*.json (docs/contract.md, Variants
// and options): the base without the items the group's default option lists, with the option's own parts and lines.
// Never part of a production build.
import type { Part, Placement } from '../api/types';
import type { JsonPart } from './fixture-json';
import { files, seedPart } from './fixture-seed';
import { FIXTURE_MARKER } from './marker';

export interface OptionLine {
  parent: string;
  child: string;
  quantity: number;
  placements?: Placement[];
}

interface JsonOption {
  parts?: (string | JsonPart)[];
  assemblies?: (string | { id: string; plm: string })[];
  bomLines?: OptionLine[];
}

interface JsonGroup {
  default: string;
  options: Record<string, JsonOption>;
}

export interface Configured {
  /** The items the group's default option is made of, which the option takes out. */
  removed: Set<string>;
  /** The parts only the option holds, as the parts answer lists them. */
  parts: Part[];
  /** The site of every item only the option holds. */
  sites: Map<string, string>;
  lines: OptionLine[];
}

const idOf = (item: string | { id: string }) => (typeof item === 'string' ? item : item.id);

/**
 * The configuration of a product under `option`; null for the base product (no option, or a group's default option).
 * An option no group of the product holds is refused as the service refuses it.
 */
export function configurationOf(product: string | null, option: string | null): Configured | null {
  if (!option || !product) return null;
  const file = files.find((f) => f.product.key === product);
  const groups = (file as unknown as { extended?: { variants?: Record<string, JsonGroup> } } | undefined)?.extended?.variants ?? {};
  const group = Object.values(groups).find((g) => option in g.options);
  if (!group) throw new Error(`HTTP 404: no item option ${option} in ${product} (${FIXTURE_MARKER})`);
  if (group.default === option) return null;
  const base = group.options[group.default];
  const own = group.options[option];
  const objects = [...(own.parts ?? []), ...(own.assemblies ?? [])].filter((p): p is JsonPart => typeof p !== 'string');
  return {
    removed: new Set([...(base.parts ?? []), ...(base.assemblies ?? [])].map(idOf)),
    parts: (own.parts ?? []).filter((p): p is JsonPart => typeof p !== 'string').map(seedPart),
    sites: new Map(objects.map((p) => [p.id, p.plm.toUpperCase()])),
    lines: own.bomLines ?? [],
  };
}

/** The base parts without the option's removed items, with its own. */
export const configuredParts = (base: Part[], c: Configured | null): Part[] => (c ? [...base.filter((p) => !c.removed.has(p.id)), ...c.parts] : base);
