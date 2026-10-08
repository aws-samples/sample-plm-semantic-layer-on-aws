// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { useMemo, type ReactNode } from 'react';

interface Props {
  text: string;
  /** Class for a line, given the line and its blank-node depth. */
  mark?: (line: string, depth: number) => string | undefined;
}

const INLINE = /"""|"(?:[^"\\]|\\.)*"|<[^>\s]*>|#.*$/g;

/** Turtle with IRIs, strings and comments coloured, one block per line. */
export function TurtleView({ text, mark }: Props) {
  const lines = useMemo(() => colour(text, mark), [text, mark]);
  return (
    <pre className="drawer-code turtle mono">
      {lines.map((l, i) => (
        <span key={i} className={l.cls}>{l.nodes.length ? l.nodes : ' '}</span>
      ))}
    </pre>
  );
}

function colour(text: string, mark?: Props['mark']) {
  let inString = false;
  let depth = 0;
  return text.split('\n').map((line) => {
    const nodes: ReactNode[] = [];
    let rest = line;
    let k = 0;
    while (rest.length) {
      if (inString) {
        const end = rest.indexOf('"""');
        const take = end < 0 ? rest : rest.slice(0, end + 3);
        nodes.push(<i key={k++} className="tt-str">{take}</i>);
        rest = rest.slice(take.length);
        if (end >= 0) inString = false;
        continue;
      }
      INLINE.lastIndex = 0;
      const m = INLINE.exec(rest);
      if (!m) {
        nodes.push(rest);
        break;
      }
      if (m.index > 0) nodes.push(rest.slice(0, m.index));
      const tok = m[0];
      if (tok === '"""') {
        inString = true;
        nodes.push(<i key={k++} className="tt-str">{tok}</i>);
      } else if (tok.startsWith('"')) nodes.push(<i key={k++} className="tt-str">{tok}</i>);
      else if (tok.startsWith('<')) nodes.push(<i key={k++} className="tt-iri">{tok}</i>);
      else nodes.push(<i key={k++} className="tt-com">{tok}</i>);
      rest = rest.slice(m.index + tok.length);
    }
    const bare = line.replace(/"(?:[^"\\]|\\.)*"/g, '');
    const opens = (bare.match(/\[/g) ?? []).length;
    const closes = (bare.match(/\]/g) ?? []).length;
    const cls = mark?.(line, depth + opens - Math.max(0, closes - opens));
    depth += opens - closes;
    return { nodes, cls };
  });
}
