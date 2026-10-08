// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { t } from '../i18n';

/**
 * The English name the layer's labels graph gives an item, under its native name: the site writes only its own
 * language. Nothing when the native name is the English one (a UK item) or the layer holds none.
 */
export function EnglishName({ name, nameEn, className = 'name-en' }: { name: string; nameEn?: string; className?: string }) {
  if (!nameEn || nameEn === name) return null;
  return (
    <span className={className} title={t('check.english-name.title')}>
      <span className="name-en-lang">EN</span>
      {nameEn}
    </span>
  );
}
