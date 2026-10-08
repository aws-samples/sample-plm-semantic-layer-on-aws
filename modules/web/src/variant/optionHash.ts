// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// The option taken rides in the location hash next to the subtree root and the lens (`#/check?option=spring-return`),
// so a link or a reload opens the same configuration.
import { OPTION_PARAM } from '../api/option';
import { queryOf, withQuery } from '../subtree/rootHash';

/** The option a hash names; null for the base product. */
export const optionOf = (hash: string): string | null => queryOf(hash).get(OPTION_PARAM);

/** The hash with its option replaced by `option`, or removed when it is null; the route and the other parameters stay. */
export function withOption(hash: string, option: string | null): string {
  const query = queryOf(hash);
  if (option) query.set(OPTION_PARAM, option);
  else query.delete(OPTION_PARAM);
  return withQuery(hash, query);
}
