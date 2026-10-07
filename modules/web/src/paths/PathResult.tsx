// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { Fragment } from 'react';
import type { FlowResponse, Gear, Joint, PathsResponse, Ratio, SpeedCheck, Stage, Step, StepPart } from '../api/pathTypes';
import type { Part } from '../api/types';
import { STATUS_LABEL } from '../check/violations';
import { t } from '../i18n';
import { cssColour, plmCode } from '../ui/plm';

/** A part of a step: site swatch, site, name and id; the redaction marker for a part the profile may not see. */
function PartCell({ part, parts }: { part: StepPart; parts: Map<string, Part> }) {
  if (part.redacted) {
    return (
      <span className="path-part is-redacted">
        <i className="swatch" style={{ background: 'var(--hatch)' }} />
        <b>{plmCode(part.plm)}</b> {t('paths.path-result.notVisibleToYourProfile')}
      </span>
    );
  }
  const described = parts.get(`${part.plm}|${part.id}`);
  return (
    <span className="path-part">
      <i className="swatch" style={{ background: cssColour(part.plm) }} />
      <b>{plmCode(part.plm)}</b> {described?.name ?? part.id} <span className="mono quiet">{part.id}</span>
      {described?.findings?.filter((f) => f.rule === 'meshModule').map((f) => (
        <span key={f.message} className="path-finding" title={f.message}>
          <span className="chip rule-meshModule">{t('paths.path-result.meshModule')}</span> {f.message}
        </span>
      ))}
    </span>
  );
}

/** The interface of a step: its id, label and status for the profile, the rules it fails. */
function JointCell({ joint }: { joint: Joint }) {
  return (
    <span className={`path-joint is-${joint.status}`}>
      <span className="mono path-joint-id">{joint.id}</span>
      <span className={`status is-${joint.status}`}>{STATUS_LABEL[joint.status]}</span>
      {joint.rules.map((r) => <span key={r} className={`chip rule-${r}`}>{r}</span>)}
      {joint.label ? <span className="path-joint-label">{joint.label}</span> : null}
    </span>
  );
}

const gearText = (g: Gear) => `${g.id} ${g.teeth ?? '?'} ${t('paths.path-result.teeth')}${g.moduleMm !== null ? `, m ${Number(g.moduleMm.toFixed(3))} mm` : ''}`;

/** A stage's figure as the ratio card writes it: output turns per input turn, the inverse of the stage's input-over-output ratio. */
const stageFigure = (stage: Stage) => `×${Number((1 / stage.ratio).toFixed(2))}`;

/** A gear stage: driver, driven and the fixed ring of a planetary stage, with the stage's figure in the ratio card's convention. */
function StageCell({ stage }: { stage: Stage }) {
  return (
    <span className="path-stage mono">
      {gearText(stage.driver)} → {gearText(stage.driven)}
      {stage.reaction ? ` ${t('paths.path-result.againstFixedRing')} ${gearText(stage.reaction)}` : ''} · {stageFigure(stage)}
    </span>
  );
}

/**
 * The words of a step no interface joins, from what the product records of its two parts: inside one site's assembly when
 * both belong to one site; between two sites, a gear mesh when the step carries one, else nothing declared.
 */
function internalLabel(step: Step): string {
  if (step.from.plm === step.to.plm) return t('paths.path-result.noInterface');
  return t(step.stage ? 'paths.path-result.meshNoInterface' : 'paths.path-result.noInterfaceDeclared');
}

/** One connecting link of a path or flow: the interface or, for an edge no interface joins, what the product records of it; the edge's flow and gear stage. */
function Link({ step }: { step: Step }) {
  return (
    <li className={`path-link${step.joint ? '' : ' is-internal'}`}>
      {step.flow ? <span className={`path-flow flow-${step.flow}`}>{step.flow}</span> : null}
      {step.joint ? <JointCell joint={step.joint} /> : <span className="quiet path-internal">{internalLabel(step)}</span>}
      {step.stage ? <StageCell stage={step.stage} /> : null}
    </li>
  );
}

const byKey = (parts: Part[]) => new Map(parts.map((p) => [`${p.plm}|${p.id}`, p]));

/** The steps as a chain: a part, the link to the next, the next part, and so on. */
function Chain({ steps, parts }: { steps: Step[]; parts: Map<string, Part> }) {
  if (steps.length === 0) return null;
  return (
    <ol className="path-chain">
      <li className="path-node"><PartCell part={steps[0].from} parts={parts} /></li>
      {steps.map((s, i) => (
        <Fragment key={i}>
          <Link step={s} />
          <li className="path-node"><PartCell part={s.to} parts={parts} /></li>
        </Fragment>
      ))}
    </ol>
  );
}

const Notes = ({ notes }: { notes: string[] }) => (notes.length ? <ul className="path-notes">{notes.map((n) => <li key={n}>{n}</li>)}</ul> : null);

interface PathsProps {
  answer: PathsResponse;
  shown: number;
  onShow: (i: number) => void;
}

/** The shortest paths between two parts, one on screen at a time. */
export function PathsResult({ answer, shown, onShow }: PathsProps) {
  const parts = byKey(answer.parts);
  const path = answer.paths[shown];
  return (
    <section className="path-result" aria-label={t('paths.path-result.paths')}>
      <p className="path-summary">
        {answer.paths.length === 0 ? t('paths.path-result.noPath')
          : `${answer.paths.length} ${answer.paths.length === 1 ? t('paths.path-result.shortestPath') : t('paths.path-result.shortestPaths')} · ${path.length} ${path.length === 1 ? t('paths.path-result.step') : t('paths.path-result.steps')}`}
      </p>
      {answer.paths.length > 1 ? (
        <div className="path-tabs" role="tablist">
          {answer.paths.map((_, i) => (
            <button key={i} type="button" role="tab" aria-selected={i === shown} className={`path-tab${i === shown ? ' is-on' : ''}`} onClick={() => onShow(i)}>
              {t('paths.path-result.path')} {i + 1}
            </button>
          ))}
        </div>
      ) : null}
      <Notes notes={answer.notes} />
      {path ? <Chain steps={path.steps} parts={parts} /> : null}
    </section>
  );
}

/** The overall gear ratio of a flow, its stages and sites. */
function RatioCard({ ratio }: { ratio: Ratio }) {
  return (
    <div className="path-ratio">
      <span className="eyebrow">{t('paths.path-result.gearRatio')}</span>
      <span className="path-ratio-figure">×{Number(ratio.speedUp.toFixed(2))}</span>
      <span className="path-ratio-text">{ratio.text}</span>
    </div>
  );
}

const CheckLine = ({ check }: { check: SpeedCheck }) => (
  <p className={`path-check ${check.holds ? 'is-pass' : 'is-fail'}`}>
    <span className={`status is-${check.holds ? 'pass' : 'fail'}`}>{check.holds ? t('paths.path-result.holds') : t('paths.path-result.doesNotHold')}</span> {check.text}
  </p>
);

/** The walk along the functional edges: every edge in walking order, each from a part to the next. */
export function FlowResult({ answer }: { answer: FlowResponse }) {
  const parts = byKey(answer.parts);
  const n = answer.steps.length;
  return (
    <section className="path-result" aria-label={t('paths.path-result.flow')}>
      <p className="path-summary">
        {n} {n === 1 ? t('paths.path-result.edge') : t('paths.path-result.edges')} · {answer.parts.length} {t('paths.path-result.partsReached')}
        {' · '}{answer.direction === 'up' ? t('paths.path-result.upstream') : t('paths.path-result.downstream')}
        {answer.flow ? `, ${answer.flow}` : ''}
      </p>
      {answer.ratio ? <RatioCard ratio={answer.ratio} /> : null}
      {answer.checks.map((c) => <CheckLine key={c.to} check={c} />)}
      <Notes notes={answer.notes} />
      <ol className="path-edges">
        {runs(answer.steps).map((run, i) => (
          <li key={i} className="path-edge"><Chain steps={run} parts={parts} /></li>
        ))}
      </ol>
    </section>
  );
}

const same = (a: StepPart, b: StepPart) => !a.redacted && !b.redacted && a.plm === b.plm && a.id === b.id;

/** The walk's edges as runs: an edge that leaves the part the previous edge reached continues its run; any other starts a branch. */
function runs(steps: Step[]): Step[][] {
  const out: Step[][] = [];
  for (const s of steps) {
    const last = out[out.length - 1];
    if (last && same(last[last.length - 1].to, s.from)) last.push(s);
    else out.push([s]);
  }
  return out;
}
