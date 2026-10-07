// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// Flat ESLint config (ESLint 9) for Atelier.
//
// Deliberately small: this is a demo, and a lint config nobody reads is a lint
// config that gets `--no-verify`d. It catches the two classes that actually bite
// in generated code — unused symbols and accidental `any` sprawl — and stays
// type-unaware so it runs in ~1s.
//
// The root `npm run lint` script is `eslint .`, which FAILS if this file is
// missing. That matters: a `lint` job wired to `--workspaces --if-present` with
// no scripts present passes while checking nothing.
import js from '@eslint/js';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  {
    ignores: [
      // Local experiments kept outside the sample (ignored by git).
      'spikes/**',
      '**/node_modules/**',
      '**/dist/**',
      '**/cdk.out*/**',
      'infra/dist/**',
      // The agent is Python; its virtualenv carries vendored JavaScript.
      'modules/agent/**',
      '**/.venv/**',
      'modules/web/scripts/**',
      'modules/plm-services/**',
      'modules/query-service/**',
      'modules/ontop/**',
      'site/**',
      'public/**',
    ],
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    // Plain JS/MJS files (tests/smoke.mjs, any node script) are parsed as
    // JavaScript, where `no-undef` is ACTIVE — so the Node globals have to be
    // declared or every `process`/`fetch`/`console` is an error. TypeScript files
    // don't need this: typescript-eslint disables `no-undef` for them because tsc
    // already checks it, which is why a TS-only repo passes and adding one .mjs
    // suddenly fails.
    files: ['**/*.mjs', '**/*.cjs', '**/*.js'],
    languageOptions: {
      ecmaVersion: 2023,
      sourceType: 'module',
      globals: {
        process: 'readonly',
        console: 'readonly',
        Buffer: 'readonly',
        fetch: 'readonly',
        URL: 'readonly',
        URLSearchParams: 'readonly',
        TextEncoder: 'readonly',
        TextDecoder: 'readonly',
        setTimeout: 'readonly',
        clearTimeout: 'readonly',
        structuredClone: 'readonly',
        AbortSignal: 'readonly',
      },
    },
  },
  {
    rules: {
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
      '@typescript-eslint/no-explicit-any': 'warn',
    },
  },
);
