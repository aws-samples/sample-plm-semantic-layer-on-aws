// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { ownerLabel } from '../ui/plm';

/** A part the file index names no file for: the owning PLM has not published it yet, so no URL is issued and nothing is drawn. */
export const CadUnpublished = ({ plm }: { plm: string }) => (
  <span className="cad-unpublished">
    <i className="swatch swatch-phantom" />
    CAD not published by {ownerLabel(plm)}
  </span>
);
