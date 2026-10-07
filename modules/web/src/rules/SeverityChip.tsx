// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import type { RuleInfo } from '../api/types';
import { t } from '../i18n';

/** Violation in the fail red, Warning in the finding amber, as the Interface check screen shows them. */
export const SeverityChip = ({ severity }: { severity: RuleInfo['severity'] }) => (
  <span className={`chip${severity === 'Warning' ? ' is-warning' : ''}`} title={t(severity === 'Warning' ? 'rules.severity.warningMeans' : 'rules.severity.violationMeans')}>
    {t(severity === 'Warning' ? 'rules.severity.warning' : 'rules.severity.violation')}
  </span>
);
