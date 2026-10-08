// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// Response shapes of the path and flow answers (docs/contract.md, "Paths through a product").
import type { Part, QueryEnvelope, RedactedPart, Status } from './types';

/** The flow kinds a functional edge carries. */
export const FLOWS = ['mechanical', 'electrical', 'hydraulic', 'steam'] as const;
export type FlowKind = (typeof FLOWS)[number];

/** A part a step names: its site and id, or the marker of a part the profile may not see. */
export type StepPart = { id: string; plm: string; redacted?: false } | RedactedPart;

/** The interface of a step with its status for the profile and the rules it fails. */
export interface Joint {
  id: string;
  label: string | null;
  status: Status;
  rules: string[];
}

export interface Gear {
  id: string;
  plm: string;
  teeth: number | null;
  moduleMm: number | null;
}

/** A gear mesh: input speed over output speed is `ratio`; a planetary stage names its fixed ring as `reaction`. */
export interface Stage {
  driver: Gear;
  driven: Gear;
  reaction?: Gear;
  ratio: number;
}

export interface Step {
  from: StepPart;
  to: StepPart;
  /** Absent for a flow edge no interface joins: a gear mesh, inside one site or between two, or a shaft carrying a wheel inside one site. */
  joint?: Joint;
  flow?: FlowKind;
  reaction?: StepPart;
  stage?: Stage;
}

export interface PathOption {
  length: number;
  steps: Step[];
}

export interface PathsResponse extends QueryEnvelope {
  product: string;
  from: Part | RedactedPart;
  to: Part | RedactedPart;
  maxLength: number;
  maxPaths: number;
  paths: PathOption[];
  notes: string[];
  parts: Part[];
}

export interface Ratio {
  to: string;
  toPlm: string;
  ratio: number;
  speedUp: number;
  stages: Stage[];
  text: string;
}

export interface SpeedCheck {
  from: string;
  fromPlm: string;
  fromRpm: number;
  to: string;
  toPlm: string;
  ratedRpm: number;
  predictedRpm: number;
  holds: boolean;
  text: string;
}

export interface FlowResponse extends QueryEnvelope {
  product: string;
  from: Part | RedactedPart;
  flow?: FlowKind;
  direction: 'down' | 'up';
  maxSteps: number;
  steps: Step[];
  ratio?: Ratio;
  checks: SpeedCheck[];
  notes: string[];
  parts: Part[];
}
