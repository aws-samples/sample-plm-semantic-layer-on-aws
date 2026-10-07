// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { useEffect, useState } from 'react';
import { getJson, postJson } from '../../api/client';
import type { AppData } from '../../api/store';
import type { Changes, Part, PublishCadRequest } from '../../api/types';
import { cssColour, ownerLabel, plmCode } from '../../ui/plm';
import { cadLanded } from './changes';
import { t } from '../../i18n';

const POLL_MS = 3000;
const MAX_MS = 60_000;
/** Bucket keys are `cad/<product>/<part>.stp`, lower case. */
const KEY = /^cad\/[a-z0-9-]+\/[a-z0-9-]+\.stp$/;
const toError = (e: unknown) => (e instanceof Error ? e : new Error(String(e)));

/**
 * The bucket key of a part's STEP file. The parts answer carries no `cadPart`; the PLM's own file
 * reference (`sourceFileRef`) names the same part, so when it is a bucket key it is the key, else its
 * base name goes under the selected product's folder. A part with no reference falls back to its id,
 * lower case.
 */
export function suggestedKey(part: Part, productKey: string | null): string {
  const ref = part.sourceFileRef ?? '';
  if (KEY.test(ref)) return ref;
  const base = ref.replace(/^.*\//, '').replace(/\.[^.]+$/, '') || part.id;
  return `cad/${productKey ?? ''}/${base.toLowerCase()}.stp`;
}

type Step =
  | { kind: 'idle' }
  | { kind: 'posting' }
  | { kind: 'waiting'; since: number; elapsed: number }
  | { kind: 'landed'; cadFile: string }
  | { kind: 'timeout' }
  | { kind: 'error'; error: Error };

interface Props {
  data: AppData;
  part: Part;
  onClose: () => void;
  /** Called once the file index holds the file: the parts are read again and the viewer draws the part. */
  onPublished: () => void;
}

/** The "Publish CAD file" action for a part the file index names no file for, on the PLM's own endpoint: one event, then the feed is polled until atelier:cadFile lands. */
export function PublishCad({ data, part, onClose, onPublished }: Props) {
  const owner = ownerLabel(part.plm);
  // The key the user typed; until they edit the field, the suggestion derived from the part.
  const [typed, setTyped] = useState<string | null>(null);
  const key = typed ?? suggestedKey(part, data.product?.key ?? null);
  const [step, setStep] = useState<Step>({ kind: 'idle' });
  const busy = step.kind === 'posting' || step.kind === 'waiting' || step.kind === 'landed';
  const valid = KEY.test(key.trim());

  const publish = async () => {
    if (!valid || busy) return;
    setStep({ kind: 'posting' });
    const body: PublishCadRequest = { part: part.id, cadFile: key.trim() };
    try {
      await postJson(data.config, `/${part.plm.toLowerCase()}/demo/events/cad`, data.profile, body);
      setStep({ kind: 'waiting', since: Date.now(), elapsed: 0 });
    } catch (e) {
      setStep({ kind: 'error', error: toError(e) });
    }
  };

  // Every 3 s until the feed's additions hold a cadFile for the part, for at most 60 s.
  useEffect(() => {
    if (step.kind !== 'waiting') return;
    const { since } = step;
    let live = true;
    const timer = setTimeout(async () => {
      let landed: string | null = null;
      try {
        landed = cadLanded(await getJson<Changes>(data.config, '/query/demo/changes', data.profile), part.id);
      } catch {
        // A failed poll is not a failed event: keep waiting.
      }
      if (!live) return;
      if (landed) {
        setStep({ kind: 'landed', cadFile: landed });
        onPublished();
      } else if (Date.now() - since >= MAX_MS) {
        setStep({ kind: 'timeout' });
      } else {
        setStep({ kind: 'waiting', since, elapsed: Math.round((Date.now() - since) / 1000) });
      }
    }, POLL_MS);
    return () => {
      live = false;
      clearTimeout(timer);
    };
  }, [step, data.config, data.profile, part.id, onPublished]);

  return (
    <form
      className="publish publish-cad"
      style={{ borderLeftColor: cssColour(part.plm) }}
      aria-label={`Publish CAD file from ${owner}`}
      onSubmit={(e) => {
        e.preventDefault();
        void publish();
      }}
    >
      <div className="release-head">
        <span className="eyebrow">{t('check.publish-cad.publishCadFileFrom')} {owner}</span>
        <span className="kind-tag">{t('check.publish-cad.event')}</span>
        <button type="button" className="btn btn-small" onClick={onClose} disabled={step.kind === 'posting' || step.kind === 'waiting'}>{t('check.publish-cad.cancel')}</button>
      </div>
      <p className="publish-from">
        <i className="swatch" style={{ background: cssColour(part.plm) }} />
        {plmCode(part.plm)} part <span className="mono">{part.id}</span> {part.name} <span className="mono quiet">{t('check.publish-cad.atelierCadfile')}</span>
      </p>
      <label className="release-field publish-field">
        <span className="release-label">{t('check.publish-cad.bucketKey')}</span>
        <input className="release-input mono" value={key} onChange={(e) => setTyped(e.target.value)} disabled={busy} spellCheck={false} aria-invalid={!valid} />
      </label>
      <p className="release-note quiet">
        {part.sourceFileRef ? (
          <>Pre-filled from the {owner}&apos;s own file reference <span className="mono">{part.sourceFileRef}</span>. </>
        ) : (
          <>Pre-filled from the part id under the product&apos;s folder, as the bucket names its files: <span className="mono">{t('check.publish-cad.cadProductPartStp')}</span>. </>
        )}
        The STEP file is already in the bucket; the file index has no entry for it. <span className="mono">{t('check.publish-cad.post')}{part.plm.toLowerCase()}/demo/events/cad</span>: the{' '}
        {owner} puts one event, <span className="mono">{t('check.publish-cad.partCadPublished')}</span>; the links loader sets <span className="mono">{t('check.publish-cad.atelierCadfile')}</span> on the part in the
        file index, and the 3D part follows on the next load.
      </p>
      <div className="release-actions">
        <button type="submit" className="btn btn-ink" disabled={busy || !valid}>
          {step.kind === 'posting' ? 'Publishing...' : 'Publish'}
        </button>
        {step.kind === 'waiting' ? (
          <span className="publish-wait" role="status">
            <span className="loading-bar" />
            waiting for the event · {step.elapsed} s
          </span>
        ) : null}
        {step.kind === 'landed' ? (
          <span className="release-done">
            {t('check.publish-cad.publishedFileIndex1TripleCadfile')} <span className="mono">{step.cadFile}</span>); loading the part.
          </span>
        ) : null}
        {step.kind === 'timeout' ? (
          <span className="release-error">
            {t('check.publish-cad.noEventAfter60S')}{' '}
            <button type="button" className="btn btn-small" onClick={() => setStep({ kind: 'waiting', since: Date.now(), elapsed: 0 })}>
              {t('check.publish-cad.keepWaiting')}
            </button>
          </span>
        ) : null}
        {step.kind === 'error' ? <span className="release-error">{step.error.message}</span> : null}
      </div>
    </form>
  );
}
