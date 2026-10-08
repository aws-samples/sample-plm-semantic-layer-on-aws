// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// Shapes of data/products/*.json, the files the PLM seeds and the link store are generated from
// (docs/contract.md, "Detailed product structure"); fixture-seed.ts checks the files against them.

/** Rows of data/products/*.json; the typed assignment below checks the files against these shapes. */
export interface JsonRow {
  id: string;
  part: string;
  /** Position as the PLM stores it, in `unit`; null when the row carries no unit. */
  position: { x: string; y: string; z: string };
  unit: string | null;
}
export interface JsonPlug extends JsonRow { connectorType: string; pinCount: number }
export interface JsonFastener extends JsonRow { standard: string; diameter: string; diameterUnit: string; count: number; gripLength: string; gripUnit: string }
export interface JsonCoupling extends JsonRow { standard: string; dashSize: number; rating: string; ratingUnit: string; fluid: string }
export interface JsonInterface {
  id: string;
  label: string;
  toleranceMm: string;
  parts: string[];
  /** Feature pairs by class; an interface without a class omits it or carries null. */
  pairs?: JsonPlug[][] | null;
  fasteners?: JsonFastener[][] | null;
  couplings?: JsonCoupling[][] | null;
  unmatedPlugs?: JsonPlug[] | null;
}
/** A plug declared on no interface and mated to nothing: what "Publish link" may pre-fill for an orphan of its part's mate. */
export type JsonSparePlug = JsonPlug;
export interface JsonPart {
  id: string;
  plm: string;
  name: string;
  cadFile: string;
  classification: { jurisdiction: string; releasableTo: string };
  /** Who built the part when it is not the owning PLM (atelier:builtBy in the file index). */
  supplier?: string | null;
  /** Bucket key without `cad/` and `.stp`. */
  cadPart?: string;
  /** The released file index has no atelier:cadFile for this part: its STEP file waits in the bucket for the PLM's publication event. */
  cadPending?: boolean;
  /** Part attributes in the owning PLM's idiom; `mass` may carry a decimal comma, `type` is PART when absent. */
  extended?: {
    revision?: string | number;
    lifecycle?: string;
    mass?: string | number;
    massUnit?: string;
    material?: string;
    type?: string;
    /** English name, which the layer's labels graph publishes beside the native name. */
    nameEn?: string;
  } | null;
}
export interface JsonProduct {
  product: { key: string; name: string };
  /** The coordinate frame of the product's CAD and feature positions, in words. */
  frame: string;
  parts: JsonPart[];
  interfaces: JsonInterface[];
  sparePlugs?: JsonSparePlug[] | null;
}
