// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

/** Native ids in reading order, as the service lists them: the number in an id compares by value, so IF-99 comes before IF-100. */
export const byId = (a: string, b: string) => a.localeCompare(b, 'en', { numeric: true });
