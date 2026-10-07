// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { useEffect, useMemo, useState } from 'react';
import { useCached, type AppData } from '../api/store';
import type { Catalogue } from '../api/types';
import type { Resource } from '../api/useResource';
import { cssColour, sourcesOf } from '../ui/plm';
import { Annotations } from './Annotations';
import { R2rml } from './R2rml';
import { ColumnTable } from './ColumnTable';
import { coverage } from './coverage';
import { Tree, type Selection } from './Tree';
import { t } from '../i18n';

/** `term`: an ontology term (a prefixed name) the screen opens on, at the first column that maps it; the viewer's picks win afterwards. */
export function DataCatalogue({ data, term = null }: { data: AppData; term?: string | null }) {
  // The four PLM catalogues and the Atelier core store's, which is described like a PLM's.
  const sources = useMemo(() => sourcesOf(data.config.plms), [data.config.plms]);
  const { ensure, get } = data.catalogues;
  useEffect(() => {
    for (const plm of sources) ensure(plm);
  }, [ensure, sources]);
  const catalogues = useMemo(
    () => Object.fromEntries(sources.map((plm) => [plm, get(plm)])) as Record<string, Resource<Catalogue> | undefined>,
    [get, sources],
  );
  const [picked, setPicked] = useState<Selection | null>(null);
  const [columnName, setColumnName] = useState<string | null>(null);

  const ready = useMemo(
    () => sources.flatMap((plm) => (catalogues[plm]?.state === 'ready' ? [catalogues[plm].data as Catalogue] : [])),
    [catalogues, sources],
  );
  const mapped = useMemo(() => (term ? columnMapping(term, sources, catalogues) : null), [term, sources, catalogues]);
  const selection = picked ?? mapped?.selection ?? firstFlagged(sources, catalogues);
  const catalogue = selection ? readyData(catalogues[selection.plm]) : null;
  const entity = catalogue?.entities.find((e) => e.entity === selection!.entity) ?? null;
  const shownColumn = columnName ?? (picked ? null : mapped?.column ?? null);
  const column = entity?.columns.find((c) => c.column === shownColumn)
    ?? entity?.columns.find((c) => c.undescribed || c.unitMissing) ?? entity?.columns[0] ?? null;
  const mappingPlm = catalogue ? selection!.plm : null;
  const mapping = useCached(data.mappings, mappingPlm);
  const cov = coverage(ready);
  const failed = sources.filter((p) => catalogues[p]?.state === 'error');

  return (
    <div className="catalogue">
      <Tree plms={sources} catalogues={catalogues} selection={selection}
        onSelect={(s) => { setPicked(s); setColumnName(null); }} />
      <div className="catalogue-main">
        {entity && selection ? (
          <ColumnTable plm={selection.plm} entity={entity} selectedColumn={column?.column ?? null} onSelectColumn={setColumnName} />
        ) : failed.length === sources.length ? (
          <div className="stage-error"><p className="error-body">{t('catalogue.data-catalogue.noCatalogueAnsweredCheckThePlm')}</p></div>
        ) : (
          <p className="quiet hint">{t('catalogue.data-catalogue.readingTheCataloguesOf')} {data.config.plms.length} PLMs and the Atelier core store.</p>
        )}
      </div>
      <aside className="panel catalogue-side">
        <section className="coverage">
          <span className="eyebrow">{t('catalogue.data-catalogue.columnsDescribed')}</span>
          <div className="coverage-figure">
            <span className="figure-n">{cov.described}</span>
            <span className="coverage-of">/ {cov.total}</span>
          </div>
          {failed.length ? <p className="quiet">{t('catalogue.data-catalogue.notCounted')} {failed.map((p) => p.toUpperCase()).join(', ')} catalogue unavailable.</p> : null}
          <ul className="coverage-plms">
            {ready.map((c) => {
              const v = coverage([c]);
              return (
                <li key={c.plm}>
                  <span className="coverage-plm">{c.plm}</span>
                  <span className="coverage-track">
                    <span style={{ width: `${(v.described / Math.max(1, v.total)) * 100}%`, background: cssColour(c.plm) }} />
                  </span>
                  <span className="mono">{v.described}/{v.total}</span>
                </li>
              );
            })}
          </ul>
        </section>
        {catalogue && entity && column ? (
          <section>
            <h2 className="eyebrow">{t('catalogue.data-catalogue.declaredOnceOnTheOrm')}</h2>
            <Annotations catalogue={catalogue} entity={entity} column={column} />
            <p className="explain">
              {t('catalogue.data-catalogue.theSameAnnotationsProduceThisCatalogue')} <span className="mono">{t('catalogue.data-catalogue.ontop')}{selection!.plm}</span> runs,
              so the mapping cannot drift from the code.
            </p>
          </section>
        ) : null}
        {catalogue && entity && column && mappingPlm ? (
          <R2rml plm={mappingPlm} mapping={mapping} entity={entity.entity} column={column.column} />
        ) : null}
      </aside>
    </div>
  );
}

function readyData(r: Resource<Catalogue> | undefined): Catalogue | null {
  return r?.state === 'ready' ? r.data : null;
}

/** The first source, entity and column, in source order, whose column maps `term`; null when none does. */
function columnMapping(term: string, plms: string[], cats: Record<string, Resource<Catalogue> | undefined>): { selection: Selection; column: string } | null {
  for (const plm of plms) {
    for (const e of readyData(cats[plm])?.entities ?? []) {
      const c = e.columns.find((col) => col.ontologyTerm === term);
      if (c) return { selection: { plm, entity: e.entity }, column: c.column };
    }
  }
  return null;
}

function firstFlagged(plms: string[], cats: Record<string, Resource<Catalogue> | undefined>): Selection | null {
  let first: Selection | null = null;
  for (const plm of plms) {
    const c = readyData(cats[plm]);
    for (const e of c?.entities ?? []) {
      first ??= { plm, entity: e.entity };
      if (e.columns.some((col) => col.undescribed || col.unitMissing)) return { plm, entity: e.entity };
    }
  }
  return first;
}
