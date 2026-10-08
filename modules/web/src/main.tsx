// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import '@fontsource/barlow-condensed/500.css';
import '@fontsource/barlow-condensed/600.css';
import '@fontsource/barlow/400.css';
import '@fontsource/barlow/500.css';
import '@fontsource/barlow/600.css';
import '@fontsource/jetbrains-mono/400.css';
import '@fontsource/jetbrains-mono/600.css';
import './styles/tokens.css';
import './styles/shell.css';
import './styles/check.css';
import './styles/changes.css';
import './styles/viewer.css';
import './styles/lens.css';
import './styles/bom.css';
import './styles/catalogue.css';
import './styles/evidence.css';
import './styles/flow.css';
import './styles/architecture.css';
import './styles/ask.css';
import './styles/subtree.css';
import './styles/rules.css';
import './styles/paths.css';
import { App } from './App';
import { loadConfig } from './config';

const root = createRoot(document.getElementById('root')!);
const render = (config: Parameters<typeof App>[0]['config'], configError: Error | null) =>
  root.render(
    <StrictMode>
      <App config={config} configError={configError} />
    </StrictMode>,
  );

render(null, null);
loadConfig().then(
  (config) => render(config, null),
  (e: unknown) => render(null, e instanceof Error ? e : new Error(String(e))),
);
