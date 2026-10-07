// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import type { ReactNode } from 'react';
import type { Envelope } from '../api/types';
import { Drawer } from '../ui/Drawer';
import { t } from '../i18n';

/** Card id of the validation step; the other cards are named by their endpoint. */
export const SHACL_CARD = 'shacl';

interface Props {
  children?: ReactNode;
  request: string;
  failed: boolean;
  envelope: Envelope | undefined;
  sparqlOpen: boolean;
  onToggleSparql: () => void;
  /** Card whose evidence drawer is open. */
  openCard: string | null;
  /** Null while no interface is selected: cards cannot be inspected. */
  onOpenCard: ((card: string) => void) | null;
}

/** Where the answer on screen came from: one cell per federated endpoint, then validation. */
export function PathStrip({ children, request, failed, envelope, sparqlOpen, onToggleSparql, openCard, onOpenCard }: Props) {
  const calls = envelope?.provenance.calls ?? [];
  const timings = envelope?.timings;
  const longest = Math.max(1, ...calls.map((c) => c.ms));
  const card = (id: string, className: string, body: ReactNode, title: string) => (
    <li key={id}>
      <button
        type="button"
        className={`hop ${className}${openCard === id ? ' is-open' : ''}`}
        aria-expanded={openCard === id}
        disabled={!onOpenCard}
        title={onOpenCard ? title : 'Select an interface to inspect its evidence'}
        onClick={() => onOpenCard?.(id)}
      >
        {body}
      </button>
    </li>
  );
  return (
    <footer className="path-strip" aria-label="Query path">
      {children}
      <div className="path-head">
        <span className="eyebrow">{t('check.path-strip.answerPath')}</span>
        <span className="mono path-request">{t('check.path-strip.get')} {request}</span>
        {timings ? (
          <span className="path-total">
            <b className="mono">{timings.totalMs} ms</b> total, federation <span className="mono">{timings.federationMs} ms</span>
          </span>
        ) : (
          <span className="quiet">{failed ? 'no answer, see the error' : 'waiting for the response'}</span>
        )}
        <button type="button" className="btn btn-small" aria-expanded={sparqlOpen} onClick={onToggleSparql} disabled={!envelope}>
          {sparqlOpen ? 'Hide SPARQL' : 'Show SPARQL'}
        </button>
      </div>
      <ol className="path-calls">
        {calls.map((c) =>
          c.requests === 0
            ? card(c.endpoint, `is-${c.kind} is-idle`, (
              <>
                <span className="hop-name mono">{c.endpoint}</span>
                <span className="hop-ms mono">0 req</span>
                <span className="hop-figures">{t('check.path-strip.notNeededForThisInterface')}</span>
                <span className="hop-kind">{c.kind}</span>
              </>
            ), `${c.endpoint} was not asked: none of its parts is on this interface`)
            : card(c.endpoint, `is-${c.kind}`, (
              <>
                <span className="hop-name mono">{c.endpoint}</span>
                <span className="hop-ms mono">{c.ms} ms</span>
                <span className="hop-bar" style={{ width: `${(c.ms / longest) * 100}%` }} />
                <span className="hop-figures">{c.triples} triples · {c.requests} req</span>
                <span className="hop-kind">{c.kind}</span>
              </>
            ), `Inspect the evidence from ${c.endpoint}`),
        )}
        {timings
          ? card(SHACL_CARD, 'is-validation', (
            <>
              <span className="hop-name">{t('check.path-strip.shaclRules')}</span>
              <span className="hop-ms mono">{timings.validationMs} ms</span>
              <span className="hop-bar" style={{ width: `${Math.min(100, (timings.validationMs / longest) * 100)}%` }} />
              <span className="hop-figures">{t('check.path-strip.validation')}</span>
              <span className="hop-kind">{t('check.path-strip.onTheMergedGraph')}</span>
            </>
          ), 'Inspect the evidence of the rules')
          : null}
      </ol>
      {envelope && !onOpenCard ? <p className="path-hint quiet">{t('check.path-strip.selectAnInterfaceToInspectThe')}</p> : null}
    </footer>
  );
}

const ENHANCER = /<(?:cache|bulk\+\d+):/;

/** SPARQL as the query service sent it, with a note when it carries jena-serviceenhancer hints. */
export function SparqlText({ sparql }: { sparql: string }) {
  return (
    <>
      <pre className="drawer-code mono">{sparql}</pre>
      {ENHANCER.test(sparql) ? (
        <p className="drawer-note sparql-note">
          <span className="mono">{t('check.path-strip.cache')}</span> and <span className="mono">{t('check.path-strip.bulkN')}</span> in a SERVICE IRI are the federator's caching and
          batching hints (jena-serviceenhancer), not part of SPARQL; the endpoint receives the plain query.
        </p>
      ) : null}
    </>
  );
}

export function SparqlDrawer({ request, sparql, onClose }: { request: string; sparql: string; onClose: () => void }) {
  return (
    <Drawer label="Federated SPARQL that ran for" title={<span className="mono">{t('check.path-strip.get')} {request}</span>} onClose={onClose}>
      <SparqlText sparql={sparql} />
    </Drawer>
  );
}
