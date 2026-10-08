// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { Turn } from '../ask/types';
import type { RuntimeConfig } from '../config';
import { getJson, getTurtle } from './client';
import { optioned } from './option';
import { scoped } from './product';
import { rooted } from './subtree';
import type { ProfileId } from './profile';
import type {
  Bom, Catalogue, Changes, DemoHealth, Envelope, Evidence, Health, InterfaceResponse, InterfacesResponse, McpTools, PartsResponse, Placements, Product, ReferencesResponse, TableRows, TagsResponse,
  EquivalentsResponse, RuleFailuresResponse, RulesResponse, SuppliersResponse, VariantDiff, VariantGroups,
} from './types';
import type { Resource } from './useResource';

/**
 * Keyed responses that outlive the screen that asked for them; a key loads once until
 * retried. Entries live under a scope (the profile and the product, and for rule answers the
 * run), so changing profile or product never serves an answer filtered for another one.
 */
export interface Cache<T> {
  get: (key: string) => Resource<T> | undefined;
  ensure: (key: string) => void;
  retry: (key: string) => void;
}

const LOADING: Resource<never> = { state: 'loading' };
const toError = (e: unknown) => (e instanceof Error ? e : new Error(String(e)));

function useCache<T>(scope: string, load: (key: string) => Promise<T>): Cache<T> {
  const [map, setMap] = useState<Record<string, Resource<T>>>({});
  const started = useRef(new Set<string>());
  const loader = useRef(load);
  loader.current = load;
  const full = useCallback((key: string) => `${scope}\u001f${key}`, [scope]);
  const retry = useCallback((key: string) => {
    const k = full(key);
    started.current.add(k);
    setMap((m) => ({ ...m, [k]: { state: 'loading' } }));
    loader.current(key).then(
      (data) => setMap((m) => ({ ...m, [k]: { state: 'ready', data } })),
      (e: unknown) => setMap((m) => ({ ...m, [k]: { state: 'error', error: toError(e) } })),
    );
  }, [full]);
  const ensure = useCallback((key: string) => {
    if (!started.current.has(full(key))) retry(key);
  }, [full, retry]);
  const get = useCallback((key: string) => map[full(key)], [map, full]);
  return useMemo(() => ({ get, ensure, retry }), [get, ensure, retry]);
}

/** Starts loading `key` on first render and returns its resource; a null key loads nothing. */
export function useCached<T>(cache: Cache<T>, key: string | null): Resource<T> {
  const { ensure } = cache;
  useEffect(() => {
    if (key !== null) ensure(key);
  }, [ensure, key]);
  return key === null ? LOADING : (cache.get(key) ?? LOADING);
}

/**
 * The world placements the viewer draws the parts at: undefined while they load, null without a
 * product or when the answer failed, which draws every part once, as authored.
 */
export function usePlacements(data: AppData): Placements | null | undefined {
  const r = useCached(data.placements, data.product ? ALL : null);
  if (!data.product || r.state === 'error') return null;
  return r.state === 'ready' ? r.data : undefined;
}

/** The one key of the parts and interfaces caches. */
export const ALL = 'all';

export interface AppData {
  config: RuntimeConfig;
  /** Bumped by "Run rules"; part of the rule answers' scope so the rules run again. */
  runKey: number;
  /** Viewer profile sent on every call; part of every filtered cache's scope. */
  profile: ProfileId;
  /** Product the listings and the single-interface lookups are scoped to (sent as `?product=`); null means every product. Part of the parts, interface and rule answers' scope. */
  product: Product | null;
  /** Subtree root the parts, interfaces, bill of materials and references are rooted at; null is the whole product. Part of their scope. */
  root: string | null;
  /** The variant option the parts and placements are configured by (sent as `?option=`); null is the base product. Part of their scope. */
  option: string | null;
  parts: Cache<PartsResponse>;
  /** The world placements of the parts listed, under the key ALL, scoped as the parts are. */
  placements: Cache<Placements>;
  /** The product's bill of materials, under the key ALL. */
  bom: Cache<Bom>;
  /** Keyed by product key: the external references of the product's visible parts, read again on each run. */
  references: Cache<ReferencesResponse>;
  /** The product's purchased items shared across the sites, under the key ALL. */
  equivalents: Cache<EquivalentsResponse>;
  /** The suppliers of the product's parts, under the key ALL. */
  suppliers: Cache<SuppliersResponse>;
  /** The product's variant groups, under the key ALL. */
  variants: Cache<VariantGroups>;
  /** Keyed by `group|option`: that option of the product against its group's default, read again on each run. */
  variantDiff: Cache<VariantDiff>;
  interfaces: Cache<InterfacesResponse>;
  one: Cache<InterfaceResponse>;
  evidence: Cache<Evidence>;
  tables: Cache<TableRows>;
  /** Export-control tags of the parts the profile may see, from the Atelier core service. */
  tags: Cache<TagsResponse>;
  /** Keyed by source segment (a PLM code or `core`): the catalogue and mapping its service publishes. */
  catalogues: Cache<Catalogue>;
  mappings: Cache<string>;
  /** Keyed by service segment (`query`, a PLM code or `core`): its /health answer. */
  health: Cache<Health>;
  /** The agent behind CloudFront /agent/*, same origin as the site: its /health answer, under the key ALL. */
  agentHealth: Cache<Health>;
  /** The MCP server's tool list from the query service, under the key ALL. */
  mcpTools: Cache<McpTools>;
  /** Under the key ALL: every shape of the rules, described; the policy does not filter it. */
  rules: Cache<RulesResponse>;
  /** Under the key ALL: the failing records of every rule and product for the profile, read again on each run. */
  ruleFailures: Cache<RuleFailuresResponse>;
  /** Under the key ALL: what changed since the released dataset; every profile but unknown may read it. */
  changes: Cache<Changes>;
  /** Public, under the key ALL: the event bus, its events and the released graph sizes; 404 when the change feed is not deployed. */
  demoHealth: Cache<DemoHealth>;
  /** Interface selected on the Interface check screen, remembered across tabs. */
  lastCheckId: string | null;
  /** Last completed Ask turn asked under this profile, with the tools it ran and the provenance its tool results carried. */
  lastTurn: Turn | null;
}

export function useAppData(config: RuntimeConfig, runKey: number, profile: ProfileId, product: Product | null, root: string | null, option: string | null, lastCheckId: string | null, lastTurn: Turn | null): AppData {
  const key = product?.key ?? null;
  const run = `${profile}|${key}|${runKey}`;
  const parts = useCache(`${profile}|${key}|${root}|${option}`, () => getJson<PartsResponse>(config, optioned(rooted('/query/parts', key, root), option), profile));
  const placements = useCache(`${profile}|${key}|${root}|${option}`, () => getJson<Placements>(config, optioned(rooted('/query/placements', key, root), option), profile));
  const bom = useCache(`${profile}|${key}|${root}`, () => getJson<Bom>(config, rooted('/query/bom', key, root), profile));
  const references = useCache(`${profile}|${runKey}|${root}`, (productKey) => getJson<ReferencesResponse>(config, rooted('/query/references', productKey, root), profile));
  const equivalents = useCache(`${profile}|${key}`, () => getJson<EquivalentsResponse>(config, scoped('/query/equivalents', key), profile));
  const suppliers = useCache(`${profile}|${key}`, () => getJson<SuppliersResponse>(config, scoped('/query/suppliers', key), profile));
  const variants = useCache(`${key}`, () => getJson<VariantGroups>(config, scoped('/query/variants', key), profile));
  const variantDiff = useCache(run, (pick) => {
    const [group, option] = pick.split('|');
    return getJson<VariantDiff>(config, scoped(`/query/variant-diff?group=${encodeURIComponent(group)}&option=${encodeURIComponent(option)}`, key), profile);
  });
  const interfaces = useCache(`${run}|${root}`, () => getJson<InterfacesResponse>(config, rooted('/query/interfaces', key, root), profile));
  // An interface id is unique within a product only, so the lookup names the product too, and the
  // product in the scope keeps one product's answer for an id from standing in for another's.
  const one = useCache(run, (id) => getJson<InterfaceResponse>(config, scoped(`/query/interfaces/${encodeURIComponent(id)}`, key), profile));
  const evidence = useCache(run, (id) => getJson<Evidence>(config, scoped(`/query/interfaces/${encodeURIComponent(id)}/evidence`, key), profile));
  // Native rows are read again after each run: a correction released to a PLM changes them.
  const tables = useCache(run, (path) => getJson<TableRows>(config, path, profile));
  // The core service returns a plain array; older fixtures wrapped it in { tags }.
  const tags = useCache(profile, () =>
    getJson<TagsResponse | TagsResponse['tags']>(config, '/core/tags', profile).then((r) =>
      Array.isArray(r) ? { tags: r } : r,
    ),
  );
  // Catalogues, mappings and health describe the services, not rows: the policy does not filter them.
  const catalogues = useCache('', (plm) => getJson<Catalogue>(config, `/${plm}/catalogue`, profile));
  const mappings = useCache('', (plm) => getTurtle(config, `/${plm}/mapping`, profile));
  const health = useCache('', (service) => getJson<Health>(config, `/${service}/health`, profile));
  // The agent is not behind the API base: CloudFront routes /agent/* to it on the site's own origin.
  const agentHealth = useCache('', () => getJson<Health>({ ...config, apiBase: '' }, '/agent/health', profile));
  const mcpTools = useCache('', () => getJson<McpTools>(config, '/query/mcp/tools', profile));
  const rules = useCache('', () => getJson<RulesResponse>(config, '/query/rules', profile));
  const ruleFailures = useCache(`${profile}|${runKey}|${key}|${root}|${option}`, () =>
    getJson<RuleFailuresResponse>(config, optioned(rooted('/query/rules/failures', key, root), option), profile));
  const changes = useCache(run, () => getJson<Changes>(config, '/query/demo/changes', profile));
  const demoHealth = useCache('', () => getJson<DemoHealth>(config, '/query/demo/health', profile));
  return useMemo(
    () => ({ config, runKey, profile, product, root, option, parts, placements, bom, references, equivalents, suppliers, variants, variantDiff, interfaces, one, evidence, tables, tags, catalogues, mappings, health, agentHealth, mcpTools, rules, ruleFailures, changes, demoHealth, lastCheckId, lastTurn }),
    [config, runKey, profile, product, root, option, parts, placements, bom, references, equivalents, suppliers, variants, variantDiff, interfaces, one, evidence, tables, tags, catalogues, mappings, health, agentHealth, mcpTools, rules, ruleFailures, changes, demoHealth, lastCheckId, lastTurn],
  );
}

export interface Answer {
  request: string;
  interfaceId: string | null;
  envelope: Envelope;
}

/** The answer the Interface check screen last received: the selected interface's, else the list's. */
export function lastAnswer(data: AppData): Answer | null {
  if (data.lastCheckId) {
    const r = data.one.get(data.lastCheckId);
    if (r?.state === 'ready') return { request: scoped(`/api/query/interfaces/${data.lastCheckId}`, data.product?.key ?? null), interfaceId: data.lastCheckId, envelope: r.data };
  }
  const list = data.interfaces.get(ALL);
  if (list?.state === 'ready') return { request: rooted('/api/query/interfaces', data.product?.key ?? null, data.root), interfaceId: null, envelope: list.data };
  return null;
}
