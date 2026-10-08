// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

export interface RuntimeConfig {
  envName: string;
  apiBase: string;
  plms: string[];
  /** The product the screens open on; the first listed when absent or not deployed. */
  defaultProduct?: string;
}

export async function loadConfig(): Promise<RuntimeConfig> {
  const res = await fetch('/config.json', { cache: 'no-store' });
  if (!res.ok) throw new Error(`/config.json answered HTTP ${res.status}`);
  // A missing config.json comes back from the CDN as index.html with a 200;
  // parsing it as JSON turns that into an error instead of an empty config.
  return (await res.json()) as RuntimeConfig;
}
