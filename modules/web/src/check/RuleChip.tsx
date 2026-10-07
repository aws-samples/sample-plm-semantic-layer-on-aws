// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import type { Rule } from '../api/types';
import { RULE_LABEL, STATUS_LABEL } from './violations';

export const RuleChip = ({ rule }: { rule: Rule }) => <span className={`chip rule-${rule}`}>{RULE_LABEL[rule]}</span>;

/** Neutral chip of an interface whose hidden side kept the rules from running. */
export const NotEvaluableChip = () => <span className="chip is-not-evaluable">{STATUS_LABEL['not-evaluable']}</span>;
