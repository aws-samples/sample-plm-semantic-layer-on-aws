// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { useMemo } from 'react';

const TOKEN = /"(?:[^"\\]|\\.)*"|<[^>\s]*>|#.*$|[?$][A-Za-z_]\w*|\b(?:SELECT|WHERE|FILTER|NOT|EXISTS|OPTIONAL|BIND|VALUES|AS|UNION|GROUP|BY|HAVING|ORDER|MINUS|DISTINCT|COUNT|SUM)\b/g;

/** A SPARQL query in the colours of the Turtle view: strings, IRIs, comments, variables and keywords. */
export function SparqlView({ query }: { query: string }) {
  const lines = useMemo(() => query.split('\n').map((line) => {
    const out: (string | JSX.Element)[] = [];
    let at = 0;
    for (const m of line.matchAll(TOKEN)) {
      if (m.index! > at) out.push(line.slice(at, m.index));
      const tok = m[0];
      const cls = tok.startsWith('"') ? 'tt-str' : tok.startsWith('<') ? 'tt-iri' : tok.startsWith('#') ? 'tt-com' : /^[?$]/.test(tok) ? 'sq-var' : 'sq-kw';
      out.push(<i key={m.index} className={cls}>{tok}</i>);
      at = m.index! + tok.length;
    }
    if (at < line.length) out.push(line.slice(at));
    return out;
  }), [query]);
  return (
    <pre className="drawer-code turtle mono">
      {lines.map((l, i) => <span key={i}>{l.length ? l : ' '}</span>)}
    </pre>
  );
}
