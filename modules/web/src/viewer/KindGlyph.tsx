// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import type { FeatureKind } from '../api/types';
import { glyphShapes, hatch, type Attrs, type MarkerStatus, type Shape } from './overlay';

/** An attribute the shape may not carry: absent stays absent on the element, present is passed as text. */
const str = (v: string | number | undefined): string | undefined => (v === undefined ? undefined : String(v));

/** The paint of a shape as React props; a shape without a stroke keeps no stroke attributes. */
const paint = (a: Attrs) => ({ fill: str(a.fill), stroke: str(a.stroke), strokeWidth: a['stroke-width'], strokeDasharray: str(a['stroke-dasharray']) });

/** One shape of the symbol as the SVG element it names, each attribute passed by name. */
const ShapeElement = ({ name, attrs }: Shape) => {
  const { fill, stroke, strokeWidth, strokeDasharray } = paint(attrs);
  if (name === 'circle') return <circle cx={attrs.cx} cy={attrs.cy} r={attrs.r} fill={fill} stroke={stroke} strokeWidth={strokeWidth} strokeDasharray={strokeDasharray} />;
  if (name === 'rect') return <rect x={attrs.x} y={attrs.y} width={attrs.width} height={attrs.height} fill={fill} stroke={stroke} strokeWidth={strokeWidth} strokeDasharray={strokeDasharray} />;
  return <path d={str(attrs.d)} fill={fill} stroke={stroke} strokeWidth={strokeWidth} strokeDasharray={strokeDasharray} />;
};

/** The viewer's marker symbol, inline, so legends and tables use the exact glyph the scene draws. */
export const KindGlyph = ({ kind, status = 'pass' }: { kind: FeatureKind; status?: MarkerStatus | 'outline' }) => (
  <i className="kind-glyph">
    <svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true">
      {glyphShapes(kind, status).map((shape, i) => <ShapeElement key={i} name={shape.name} attrs={shape.attrs} />)}
    </svg>
  </i>
);

/** Shared SVG definitions the inline glyphs reference; mounted once per page. */
export const SvgDefs = () => (
  <svg className="svg-defs" width="0" height="0" aria-hidden="true">
    <defs>
      <pattern id="hatch-redacted-ui" width={hatch.pattern.width} height={hatch.pattern.height} patternUnits={str(hatch.pattern.patternUnits)} patternTransform={str(hatch.pattern.patternTransform)}>
        <rect width={hatch.rect.width} height={hatch.rect.height} fill={str(hatch.rect.fill)} />
        <path d={str(hatch.path.d)} stroke={str(hatch.path.stroke)} strokeWidth={hatch.path['stroke-width']} />
      </pattern>
    </defs>
  </svg>
);
