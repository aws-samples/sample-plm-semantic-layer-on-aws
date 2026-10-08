// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { useEffect, useMemo, useRef } from 'react';
import type { Resource } from '../api/useResource';
import { ownerLabel } from '../ui/plm';
import { t } from '../i18n';

interface Props {
  plm: string;
  mapping: Resource<string>;
  entity: string;
  column: string;
}

interface Line {
  text: string;
  inEntity: boolean;
  hit: boolean;
}

/** Marks the TriplesMaps generated from the entity, and within them the lines that read the column. */
const literal = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

function annotate(turtle: string, entity: string, column: string): Line[] {
  const own = new RegExp(`^map:${literal(entity)}(_\\w+)?\\s+a\\s+rr:TriplesMap`); // nosemgrep: detect-non-literal-regexp -- entity is escaped by literal() and comes from the catalogue API
  const reads = new RegExp(`"${literal(column)}"|\\{${literal(column)}\\}`); // nosemgrep: detect-non-literal-regexp -- column is escaped by literal() and comes from the catalogue API
  let inEntity = false;
  return turtle.split('\n').map((text) => {
    if (/^\S/.test(text)) inEntity = own.test(text);
    else if (text.trim() === '') inEntity = false;
    return { text, inEntity, hit: inEntity && reads.test(text) };
  });
}

export function R2rml({ plm, mapping, entity, column }: Props) {
  const box = useRef<HTMLPreElement>(null);
  const lines = useMemo(
    () => (mapping.state === 'ready' ? annotate(mapping.data, entity, column) : []),
    [mapping, entity, column],
  );
  const hits = lines.filter((l) => l.hit).length;

  useEffect(() => {
    const pre = box.current;
    const target = pre?.querySelector<HTMLElement>('.r2rml-own, .r2rml-hit');
    if (pre && target) pre.scrollTop = Math.max(0, target.offsetTop - pre.offsetTop - 24);
  }, [lines]);

  const path = `/api/${plm}/mapping`;
  return (
    <section className="r2rml">
      <div className="r2rml-head">
        <h2 className="eyebrow">{t('catalogue.r2rml.generatedR2rml')}</h2>
        <span className="mono quiet">{t('catalogue.r2rml.get')} {path}</span>
      </div>
      {mapping.state === 'error' ? (
        <div className="r2rml-error" role="alert">{mapping.error.message}</div>
      ) : mapping.state === 'loading' ? (
        <p className="quiet r2rml-note">{t('catalogue.r2rml.readingThe')} {ownerLabel(plm)} mapping.</p>
      ) : (
        <>
          <p className="r2rml-note">
            {hits > 0 ? (
              <><span className="r2rml-key" /> {hits} {hits === 1 ? 'line reads' : 'lines read'} <span className="mono">{column}</span></>
            ) : (
              <><span className="mono">{column}</span> is not read by any TriplesMap</>
            )}
          </p>
          <pre className="r2rml-code mono" ref={box} tabIndex={0} aria-label={`R2RML mapping of the ${ownerLabel(plm)}`}>
            {lines.map((l, i) => (
              <span key={i} className={l.hit ? 'r2rml-hit' : l.inEntity ? 'r2rml-own' : undefined}>
                {l.text || '\u00a0'}
              </span>
            ))}
          </pre>
        </>
      )}
    </section>
  );
}
