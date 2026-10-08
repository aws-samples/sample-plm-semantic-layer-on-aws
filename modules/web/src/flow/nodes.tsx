// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { Fragment, type CSSProperties } from 'react';
import { Handle, Position, type Node, type NodeProps } from '@xyflow/react';
import { cssColour, dbName, isCore, plmCode } from '../ui/plm';
import { AURORA_PAD, COL_W, colX, SLOT_TOP, type Component } from './topology';

export type Tone = 'plain' | 'on' | 'dim' | 'selected';

export interface CardData extends Record<string, unknown> {
  c: Component;
  tone: Tone;
  live?: string;
  plms: string[];
}
export type CardNode = Node<CardData, 'card'>;

export interface LayerData extends Record<string, unknown> {
  label: string;
}
export type LayerNode = Node<LayerData, 'layer'>;

/** A remark pinned beside a component: what did not happen, said where it would have. */
export interface NoteData extends Record<string, unknown> {
  text: string;
}
export type NoteNode = Node<NoteData, 'note'>;

const H = 'react-flow__handle fhandle';

export function CardNode({ data }: NodeProps<CardNode>) {
  const { c, tone } = data;
  const style = { '--plm': c.plm ? cssColour(c.plm) : undefined } as CSSProperties;
  const owned = c.plm ? (isCore(c.plm) ? ' is-atelier' : ' is-site') : '';
  return (
    <div className={`fnode is-${c.variant} tone-${tone}${owned}`} style={style}>
      <Handle type="target" position={Position.Top} id="in" className={H} />
      <Handle type="source" position={Position.Bottom} id="out" className={H} />
      {c.variant === 'browser' ? <Handle type="source" position={Position.Right} id="right" className={H} /> : null}
      {/* Level with the gateway's left handle, so the wire between them runs straight. */}
      {c.variant === 'agent' ? <Handle type="source" position={Position.Right} id="right" className={H} style={{ top: 29 }} /> : null}
      {c.variant === 'cloudfront' ? <Handle type="source" position={Position.Left} id="left" className={H} style={{ left: 0 }} /> : null}
      {c.variant === 'apigw' ? <Handle type="target" position={Position.Left} id="left" className={H} style={{ left: 0 }} /> : null}
      {c.variant === 'plm' || c.variant === 'core' ? (
        <>
          <Handle type="source" position={Position.Left} id="left" className={H} style={{ left: 0 }} />
          {/* The service's event leaves from the top, beside the gateway's wire. */}
          <Handle type="source" position={Position.Top} id="top-right" className={H} style={{ left: '75%' }} />
        </>
      ) : null}
      {c.variant === 'core' ? (
        <>
          {/* The loader's change-log call arrives from below; the graph write leaves from the bottom, beside the gutter its JDBC wires take. */}
          <Handle type="target" position={Position.Bottom} id="bottom" className={H} />
          <Handle type="source" position={Position.Bottom} id="out-right" className={H} style={{ left: '75%' }} />
        </>
      ) : null}
      {/* The rule's delivery arrives at the top centre; the change-log call leaves beside it. */}
      {c.variant === 'loader' ? <Handle type="source" position={Position.Top} id="top-left" className={H} style={{ left: '25%' }} /> : null}
      {c.variant === 'eventbridge' ? (
        <>
          <Handle type="target" position={Position.Bottom} id="in-left" className={H} style={{ left: '25%' }} />
          <Handle type="target" position={Position.Bottom} id="in-right" className={H} style={{ left: '75%' }} />
          <Handle type="source" position={Position.Right} id="right" className={H} style={{ right: 0 }} />
        </>
      ) : null}
      {/* Three ways in: the core service's Graph Store write on the left, the query service's read in the middle, the loader's update on the right. */}
      {c.variant === 'neptune' ? (
        <>
          <Handle type="target" position={Position.Top} id="in-left" className={H} style={{ left: '25%' }} />
          <Handle type="target" position={Position.Top} id="in-right" className={H} style={{ left: '75%' }} />
        </>
      ) : null}
      {c.variant === 'cad' ? <Handle type="target" position={Position.Right} id="right" className={H} style={{ right: 0 }} /> : null}
      {c.variant === 'coredb' || c.variant === 'changelog' ? <Handle type="target" position={Position.Left} id="left" className={H} style={{ left: 0, top: 24 }} /> : null}
      {c.variant === 'aurora' ? (
        <Aurora c={c} plms={data.plms} />
      ) : c.variant === 'coredb' || c.variant === 'changelog' ? (
        <>
          <span className="mono fdb-name"><b className="fnode-plm">{plmCode(c.plm!)}</b>{c.title}</span>
          <span className="fdb-sub">{c.sub}</span>
        </>
      ) : (
        <>
          <div className="fnode-title">
            {c.plm ? <b className="fnode-plm">{plmCode(c.plm)}</b> : null}
            {c.title}
          </div>
          {c.endpoint ? <div className="fnode-id mono">{c.endpoint}</div> : null}
          <div className="fnode-sub">{c.sub}</div>
          {data.live ? <div className="fnode-live mono">{data.live}</div> : null}
        </>
      )}
    </div>
  );
}

/**
 * One cluster, one database slot per PLM; each slot takes SQL from above and JDBC from its left.
 * The Atelier-owned atelier_core database is its own card, drawn over the slot after the last PLM's.
 */
function Aurora({ c, plms }: { c: Component; plms: string[] }) {
  return (
    <>
      {plms.map((plm, i) => (
        <Fragment key={plm}>
          <Handle type="target" position={Position.Top} id={`top-${plm}`} className={H} style={{ left: AURORA_PAD + colX(i) + COL_W / 2, top: SLOT_TOP }} />
          <Handle type="target" position={Position.Left} id={`left-${plm}`} className={H} style={{ left: AURORA_PAD + colX(i), top: SLOT_TOP + 24 }} />
        </Fragment>
      ))}
      <div className="fdb-row">
        {plms.map((plm) => (
          <div key={plm} className="fdb" style={{ '--plm': cssColour(plm) } as CSSProperties}>
            <span className="mono fdb-name">{dbName(plm)}</span>
            <span className="fdb-sub">{plmCode(plm)} native schema</span>
          </div>
        ))}
      </div>
      <div className="fnode-caption">
        <span className="fnode-title">{c.title}</span>
        <span className="fnode-sub">{c.sub}</span>
      </div>
    </>
  );
}

export function LayerNode({ data }: NodeProps<LayerNode>) {
  return <div className="flayer">{data.label}</div>;
}

export function NoteNode({ data }: NodeProps<NoteNode>) {
  return <div className="fnote">{data.text}</div>;
}
