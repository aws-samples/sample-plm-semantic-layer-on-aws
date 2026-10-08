// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { t } from '../i18n';

export function ErrorState({ title, error, onRetry }: { title: string; error: Error; onRetry?: () => void }) {
  return (
    <div className="error-state" role="alert">
      <div className="error-title">{title}</div>
      <p className="error-body">{error.message}</p>
      {onRetry ? (
        <button type="button" className="btn" onClick={onRetry}>
          {t('ui.error-state.tryAgain')}
        </button>
      ) : null}
    </div>
  );
}

export function Loading({ what }: { what: string }) {
  return (
    <div className="loading" role="status">
      <span className="loading-bar" />
      {what}
    </div>
  );
}
