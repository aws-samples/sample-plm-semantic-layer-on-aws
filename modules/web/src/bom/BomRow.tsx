// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { useState, type CSSProperties, type MouseEvent } from 'react';
import type { BomItem } from '../api/types';
import type { SelectedPart } from '../viewer/view';
import { BomRelease, type BomReleases, type Conflict } from './BomRelease';
import { EnglishName } from '../check/EnglishName';
import { CORE, cssColour, plmCode } from '../ui/plm';
import { count, mass } from './format';
import { t } from '../i18n';

interface Props {
  item: BomItem;
  depth: number;
  /** Position in the tree (`0.2.1`): an item used under several parents appears once under each. */
  path: string;
  isOpen: (path: string) => boolean;
  onToggle: (path: string) => void;
  /** The lifecycleConflict findings of each item, by `plm|id`: a released item over a working or blocked one. */
  conflicts: Map<string, Conflict>;
  /** Null until the answers the releases need are in. */
  releases: BomReleases | null;
  /** The parent's site: a child of another site came through an external reference. */
  parentPlm?: string;
  /** Roots every screen at an assembly; absent on the root on screen. */
  onOpenSubtree: (id: string) => void;
  /** The subtree root on screen; null when the tree is the product's. */
  root: string | null;
  /** The part the person selected, here or in the viewer, and the way to change it. */
  selectedPart: SelectedPart | null;
  onSelectPart: (part: SelectedPart | null) => void;
}

/** One item and, while it is open, its items; a hidden item is a redaction marker and never opens. */
export function BomRow(props: Props) {
  const { item, depth, path, isOpen, onToggle, conflicts, releases, parentPlm, onOpenSubtree, root, selectedPart, onSelectPart } = props;
  const [correcting, setCorrecting] = useState(false);
  const indent = { '--depth': depth } as CSSProperties;
  if (item.redacted) {
    return (
      <li>
        <div className="bom-row is-redacted" style={indent}>
          <span className="bom-name">
            <span className="bom-toggle" aria-hidden />
            <i className="swatch swatch-hatched" />
            <b className="bom-plm">{plmCode(item.plm)}</b>
            <span className="bom-label">{t('bom.bom-row.anItemNotVisibleToYour')}</span>
          </span>
          <span className="bom-id quiet">{t('bom.bom-row.hidden')}</span>
          <span className="bom-n">{count(item.quantity)}</span>
          <span className="bom-n">{count(item.occurrences)}</span>
          <span className="bom-n quiet" />
          <span className="bom-n quiet" />
        </div>
      </li>
    );
  }
  const open = item.children.length > 0 && isOpen(path);
  const conflict = item.plm ? conflicts.get(`${item.plm}|${item.id}`) : undefined;
  const owner = item.plm ?? CORE;
  const external = parentPlm !== undefined && item.plm !== undefined && item.plm !== parentPlm;
  const kind = item.partType === 'PRODUCT' ? t('bom.bom-row.productRootNoPlmHoldsIt') : depth === 1 && !root ? `${plmCode(owner)} ${t('bom.bom-row.siteKit')}`
    : external ? `${plmCode(owner)} ${t('bom.bom-row.itemByExternalReference')}`
    : item.partType === 'SOFTWARE' ? t('bom.bom-row.softwareNoGeometry') : item.partType === 'DOCUMENT' ? t('bom.bom-row.documentNoGeometry') : null;
  const label = (
    <>
      <i className="swatch" style={{ background: cssColour(owner) }} />
      <span className="bom-names">
        <span className="bom-label" title={item.name}>{item.name}</span>
        <EnglishName name={item.name} nameEn={item.nameEn} className="bom-label-en" />
      </span>
      {kind ? <span className="bom-kind">{kind}</span> : null}
      {conflict ? <span className="chip rule-lifecycleConflict" title={conflict.findings.map((f) => f.message).join('\n')}>{t('bom.bom-row.lifecycleConflict')}</span> : null}
    </>
  );
  const unit = item.unitMassKg;
  const name = item.children.length > 0 ? (
    <button type="button" className="bom-name" aria-expanded={open} onClick={() => onToggle(path)}>
      <span className="bom-toggle" aria-hidden>▾</span>
      {label}
    </button>
  ) : (
    <span className="bom-name">
      <span className="bom-toggle" aria-hidden />
      {label}
    </span>
  );
  const opens = item.partType === 'ASSEMBLY' && item.id !== root;
  // A click on a site's item selects it as a click in the viewer does, again to clear it; the root belongs to no site.
  const plm = item.plm;
  const selected = plm !== undefined && selectedPart?.id === item.id && selectedPart.plm === plm;
  const select = (e: MouseEvent) => {
    if (plm === undefined || (e.target as Element).closest('.bom-open')) return;
    onSelectPart(selected ? null : { id: item.id, plm, name: item.name });
  };
  const corrects = conflict !== undefined && releases !== null;
  return (
    <li>
      <div className={`bom-row is-depth-${Math.min(depth, 2)}${conflict ? ' is-conflict' : ''}${selected ? ' is-selected' : ''}`} style={indent}
        aria-selected={plm === undefined ? undefined : selected} onClick={select}>
        {opens || corrects ? (
          <span className="bom-item">
            {name}
            {corrects ? (
              <button type="button" className="btn btn-small bom-open" aria-expanded={correcting} onClick={() => setCorrecting((c) => !c)}>
                {t('bom.bom-row.correct')}
              </button>
            ) : null}
            {opens ? (
              <button type="button" className="btn btn-small bom-open" title={`${t('bom.bom-row.openAsSubtree')}: ${item.id}`} onClick={() => onOpenSubtree(item.id)}>
                {t('bom.bom-row.openAsSubtree')}
              </button>
            ) : null}
          </span>
        ) : name}
        <span className="bom-id mono" title={item.id}>{item.id}</span>
        <span className="bom-n">{count(item.quantity)}</span>
        <span className="bom-n">{count(item.occurrences)}</span>
        <span className="bom-n">{unit === undefined ? <span className="quiet">{t('bom.bom-row.noMass')}</span> : mass(unit)}</span>
        <span className="bom-n is-strong">{item.extendedMassKg === undefined ? null : mass(item.extendedMassKg)}</span>
      </div>
      {corrects && correcting ? <BomRelease conflict={conflict} releases={releases} depth={depth} /> : null}
      {open ? (
        <ul>
          {item.children.map((c, i) => (
            <BomRow key={`${path}.${i}`} item={c} depth={depth + 1} path={`${path}.${i}`} isOpen={isOpen} onToggle={onToggle}
              conflicts={conflicts} releases={releases} parentPlm={item.plm} onOpenSubtree={onOpenSubtree} root={root}
              selectedPart={selectedPart} onSelectPart={onSelectPart} />
          ))}
        </ul>
      ) : null}
    </li>
  );
}
