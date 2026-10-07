// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import type { RuleFocus } from '../api/types';

const PREFIXES: [prefix: string, namespace: string][] = [
  ['ateliersh', 'https://example.com/atelier/shapes#'],
  ['atelier', 'https://example.com/atelier/ontology#'],
];

/** The IRI as a prefixed name when its namespace is one the shapes declare, else the IRI. */
export function prefixed(iri: string): string {
  const hit = PREFIXES.find(([, ns]) => iri.startsWith(ns));
  return hit ? `${hit[0]}:${iri.slice(hit[1].length)}` : iri;
}

/** The order the list groups the rules in. */
export const FOCUS_ORDER: RuleFocus[] = ['interface', 'part', 'reference', 'product'];
