// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { useMemo } from 'react';
import { ALL, useCached, type AppData } from '../api/store';
import type { Interface, VariantDiff, VariantGroup } from '../api/types';
import type { Resource } from '../api/useResource';

/** The interfaces of a configuration, in id order (IF-99 before IF-100) as the service lists them. */
const byId = (a: Interface, b: Interface) => a.id.localeCompare(b.id, 'en', { numeric: true });

/** The base interfaces without those the group's default option is made of, with the option's own, its rules run. */
export function configured(base: Interface[], diff: VariantDiff): Interface[] {
  const out = new Set(diff.removed.interfaces);
  return [...base.filter((i) => !out.has(i.id)), ...diff.interfaces].sort(byId);
}

export interface Configuration {
  /** The group the option belongs to; null for the base product. */
  group: VariantGroup | null;
  /** The option against its group's default; null for the base product. */
  diff: Resource<VariantDiff> | null;
  /** The interfaces on screen: the base product's, or the configuration's once its diff has answered; undefined while either loads. */
  interfaces: Interface[] | undefined;
}

/**
 * The product as the option taken makes it: the option's diff against its group's default (the interfaces only the
 * option holds, with the rules run on them, and the ones it takes out), applied to the base interfaces. An option code
 * that is a group's default, or that no group holds, is the base product.
 */
export function useConfiguration(data: AppData, base: Interface[] | undefined): Configuration {
  const groups = useCached(data.variants, data.product && data.option ? ALL : null);
  const group = groups.state === 'ready' ? groups.data.groups.find((g) => g.options.includes(data.option ?? '')) ?? null : null;
  const taken = group && group.defaultOption !== data.option ? group : null;
  const diff = useCached(data.variantDiff, taken ? `${taken.key}|${data.option}` : null);
  const waiting = data.option !== null && groups.state === 'loading';
  const interfaces = useMemo(() => {
    if (!base || waiting) return undefined;
    if (!taken) return base;
    return diff.state === 'ready' ? configured(base, diff.data) : undefined;
  }, [base, waiting, taken, diff]);
  return { group: taken, diff: taken ? diff : null, interfaces };
}
