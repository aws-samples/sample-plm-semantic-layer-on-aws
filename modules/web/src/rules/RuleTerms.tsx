// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import type { RuleTerm } from '../api/types';
import { withRoot } from '../subtree/rootHash';
import { toHash } from '../ui/useHashRoute';
import { t } from '../i18n';

interface Props {
  terms: RuleTerm[];
  /** Terms some PLM catalogue column maps; null while the catalogues are read. */
  mapped: Set<string> | null;
  root: string | null;
}

/** The ontology terms the shape reads; a term a catalogue column maps opens the Data catalogue on that column. */
export function RuleTerms({ terms, mapped, root }: Props) {
  return (
    <dl className="rules-terms">
      {terms.map((term) => (
        <div key={term.term} className="rules-term">
          <dt>
            {mapped?.has(term.term) ? (
              <a className="mono" href={withRoot(toHash({ screen: 'catalogue', term: term.term }), root)} title={t('rules.rule-terms.openTheCatalogueColumnThatMaps')}>
                {term.term}
              </a>
            ) : (
              <span className="mono">{term.term}</span>
            )}
            <span className="rules-term-kind">{t(`rules.term-kind.${term.kind}`)}</span>
          </dt>
          <dd>{term.definition ?? <span className="quiet">{t('rules.rule-terms.noDefinitionInTheOntology')}</span>}</dd>
        </div>
      ))}
    </dl>
  );
}
