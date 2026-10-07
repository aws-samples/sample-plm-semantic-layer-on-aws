// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// A credential-less synth of the semantic assembly for the template tests: the placeholder account, whose
// availability zones infra/cdk.json holds, as the gates synthesise it.
import { execFileSync } from 'node:child_process';
import * as fs from 'node:fs';
import * as os from 'node:os';
import * as path from 'node:path';

const infra = path.resolve(import.meta.dirname, '..');

export interface Resource { Type: string; Properties: Record<string, unknown> }
export interface Template { Resources: Record<string, Resource> }

/** Synthesises the named stacks once; the templates and the cdk-nag reports are read from the output directory. */
export class SynthFixture {
  private readonly out = fs.mkdtempSync(path.join(os.tmpdir(), 'atelier-synth-'));

  constructor(stacks: string[]) {
    execFileSync('npx', ['cdk', 'synth', ...stacks, '-q', '-o', this.out,
      '-c', 'env=prod', '-c', 'account=111111111111', '-c', 'region=eu-west-1', '-c', 'semantic=true'],
    { cwd: infra, stdio: ['ignore', 'ignore', 'inherit'] });
  }

  template(stack: string): Template {
    return JSON.parse(fs.readFileSync(path.join(this.out, `${stack}.template.json`), 'utf8'));
  }

  /**
   * The cdk-nag validation report of the synth: one plugin report per rule pack, each listing its violations
   * (a compliant synth lists none; an acknowledged finding is not a violation).
   */
  validationReport(): { pluginReports: { violations: unknown[] }[] } {
    return JSON.parse(fs.readFileSync(path.join(this.out, 'validation-report.json'), 'utf8'));
  }

  remove(): void {
    fs.rmSync(this.out, { recursive: true, force: true });
  }
}
