// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// An option of one of the product's variant groups scopes the parts and placements listings to the product as it is
// under that option (docs/contract.md, Variants and options); no option is the base product, every group at its default.

/** Query parameter the query service configures a listing with. */
export const OPTION_PARAM = 'option';

/** The path with `option=code` appended when an option is taken. */
export const optioned = (path: string, option: string | null) =>
  option ? `${path}${path.includes('?') ? '&' : '?'}${OPTION_PARAM}=${encodeURIComponent(option)}` : path;
