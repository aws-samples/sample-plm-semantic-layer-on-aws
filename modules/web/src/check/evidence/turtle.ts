// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// Reads the Turtle subset the link store emits: prefixes, IRIs, prefixed names,
// literals, `;` and `,` lists, and blank-node brackets.
import type { FeatureKind } from '../../api/types';

export interface Triple {
  s: string;
  p: string;
  o: string;
  literal: boolean;
}

export const RDF_TYPE = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type';
export const Atelier = 'https://example.com/atelier/ontology#';
export const RDFS_LABEL = 'http://www.w3.org/2000/01/rdf-schema#label';

// Both directive forms: Turtle `@prefix p: <iri> .` and the SPARQL-style
// `PREFIX p: <iri>` that Jena writes.
const PREFIX_LINE = /^\s*(?:@prefix|PREFIX)\s+([\w-]*):\s*<([^>]*)>\s*\.?\s*$/i;
// Every token but a string literal; a literal is scanned by `literalAt`, whatever its length.
const TOKEN = /(#[^\n]*)|(<[^>]*>)|([\w-]*:[\w%./-]*[\w%/-])|([+-]?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)|\b(true|false)\b|\b(a)\b|([;,.[\]])/g;
const SUFFIX = /(?:\^\^(?:<[^>]*>|[\w-]*:[\w-]*)|@[\w-]+)?/y;

/** One token: a comment, an IRI, a string literal with its quotes, a prefixed name, a number, a boolean, `a` or punctuation. */
export type Token = [comment?: string, iri?: string, str?: string, prefixed?: string, num?: string, bool?: string, a?: string, punct?: string];

/** End of the string literal opening at `at` (past its closing quote), or -1 when none closes there. */
function literalAt(text: string, at: number): number {
  if (text.startsWith('"""', at)) {
    const close = text.indexOf('"""', at + 3);
    if (close >= 0) return close + 3;
  }
  for (let i = at + 1; i < text.length; i++) {
    const c = text[i];
    if (c === '"') return i + 1;
    if (c === '\n') return -1;
    if (c === '\\') {
      if (i + 1 >= text.length || '\n\r\u2028\u2029'.includes(text[i + 1])) return -1;
      i++;
    }
  }
  return -1;
}

/**
 * The tokens of a Turtle body, left to right. A string literal is found by a character scan: a regex alternation repeated
 * over the literal backtracks once per character, which some engines bound by their stack.
 */
export function* tokens(text: string): Generator<Token> {
  let at = 0;
  while (at < text.length) {
    TOKEN.lastIndex = at;
    const m = TOKEN.exec(text);
    let quote = text.indexOf('"', at);
    while (quote >= 0 && (m === null || quote < m.index)) {
      const end = literalAt(text, quote);
      if (end >= 0) break;
      quote = text.indexOf('"', quote + 1);
    }
    if (quote >= 0 && (m === null || quote < m.index)) {
      const end = literalAt(text, quote);
      SUFFIX.lastIndex = end;
      SUFFIX.exec(text);
      yield [undefined, undefined, text.slice(quote, end)];
      at = SUFFIX.lastIndex;
      continue;
    }
    if (m === null) return;
    const [, comment, iri, prefixed, num, bool, a, punct] = m;
    yield [comment, iri, undefined, prefixed, num, bool, a, punct];
    at = m.index + m[0].length;
  }
}

export function parseTriples(turtle: string): Triple[] {
  const prefixes = new Map<string, string>();
  const body = turtle
    .split('\n')
    .filter((line) => {
      const m = PREFIX_LINE.exec(line);
      if (m) prefixes.set(m[1], m[2]);
      return !m;
    })
    .join('\n');
  const expand = (name: string) => {
    const i = name.indexOf(':');
    const ns = prefixes.get(name.slice(0, i));
    return ns === undefined ? name : ns + name.slice(i + 1);
  };

  const out: Triple[] = [];
  const stack: [string | null, string | null][] = [];
  let s: string | null = null;
  let p: string | null = null;
  let blanks = 0;
  let closed: string | null = null;
  const term = (value: string, literal: boolean) => {
    if (s === null) s = value;
    else if (p === null) p = value;
    else out.push({ s, p, o: value, literal });
  };

  for (const [comment, iri, str, prefixed, num, bool, a, punct] of tokens(body)) {
    if (comment) continue;
    if (iri) term(iri.slice(1, -1), false);
    else if (str !== undefined) term(str.startsWith('"""') ? str.slice(3, -3) : str.slice(1, -1), true);
    else if (prefixed) term(expand(prefixed), false);
    else if (num) term(num, true);
    else if (bool) term(bool, true);
    else if (a) term(RDF_TYPE, false);
    else if (punct === '[') {
      const b = `_:b${blanks++}`;
      if (s !== null && p !== null) out.push({ s, p, o: b, literal: false });
      stack.push([s, p]);
      s = b;
      p = null;
    } else if (punct === ']') {
      closed = s;
      [s, p] = stack.pop() ?? [null, null];
      if (s === null) s = closed;
    } else if (punct === ';') p = null;
    else if (punct === '.') {
      s = null;
      p = null;
    }
  }
  return out;
}

const RESOURCE = /\/atelier\/([a-z]+)\/(plug|fastener|coupling|part)\//;

/** PLM segment of a feature or part IRI, lower case. */
export const plmOfIri = (iri: string) => RESOURCE.exec(iri)?.[1] ?? null;

/** Feature class of a feature IRI, from its path segment. */
export const kindOfIri = (iri: string): FeatureKind | null => {
  const seg = RESOURCE.exec(iri)?.[2];
  return seg === 'plug' || seg === 'fastener' || seg === 'coupling' ? seg : null;
};

/** Native key of a feature, part or interface IRI, as the PLM stores it. */
export const nativeId = (iri: string) => decodeURIComponent(iri.replace(/^.*\/(?:plug|fastener|coupling|part|interface)\//, ''));

/** IRI of a feature as the virtual graphs and the links graph name it: `.../{plm}/{plug|fastener|coupling}/{id}` (docs/contract.md). */
export const featureIri = (f: { plm: string; kind: FeatureKind; id: string }) =>
  `https://example.com/atelier/${f.plm.toLowerCase()}/${f.kind}/${encodeURIComponent(f.id)}`;

/** Lines inside a SHACL result blank node, and the message and focus node within it. */
export function markReport(line: string, depth: number): string | undefined {
  if (depth <= 0) return undefined;
  if (line.includes('sh:resultMessage')) return 'is-result is-message';
  if (line.includes('sh:focusNode')) return 'is-result is-focus';
  return 'is-result';
}

/** The message template of a shape and the rule it implements. */
export function markShape(line: string): string | undefined {
  if (line.includes('sh:message')) return 'is-message';
  if (line.includes('ateliersh:rule')) return 'is-focus';
  return undefined;
}
