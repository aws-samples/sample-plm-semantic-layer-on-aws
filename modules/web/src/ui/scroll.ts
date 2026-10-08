// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

/** Scrolls an element to the top of its scroll container, smoothly unless the reader prefers reduced motion. */
export const bringIntoView = (el: Element | null | undefined) =>
  el?.scrollIntoView({ block: 'start', behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' });
