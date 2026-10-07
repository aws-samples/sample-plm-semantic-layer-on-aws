// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { plmCode } from '../ui/plm';
import { nativeId, plmOfIri } from './evidence/turtle';
import { t } from '../i18n';

/** Parts with no row in atelier_core.part_tag: a finding against their PLM, shown to whoever the policy lets see them. */
export function Untagged({ iris }: { iris: string[] }) {
  if (iris.length === 0) return null;
  const n = iris.length;
  return (
    <aside className="untagged" role="note">
      <span className="flag">{t('check.untagged.untagged')}</span>
      <span>
        {n} {n === 1 ? 'part' : 'parts'} without export-control tag, hidden from every profile except export-control officer
      </span>
      <span className="untagged-ids">
        {iris.map((iri) => (
          <span key={iri} className="mono">
            {plmOfIri(iri) ? <b>{plmCode(plmOfIri(iri)!)} </b> : null}{nativeId(iri)}
          </span>
        ))}
      </span>
    </aside>
  );
}
