// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// The preview of every rule's correction, released nowhere. For the seeded defect of each rule tests/fix-cycle.mjs
// cycles, the screen's proposal is previewed as the export officer (POST /query/preview): the rule's result on the
// defect's interface, part or product is fixed and nothing is newly failing; the stored values preview as still
// failing. The defect still fails afterwards and the change list is as it was: a preview writes nothing. Run by
// modules/query-service/fixtures/fixes.sh against the fixture stack and by tests/smoke.mjs against the deployment.
import { client, seededCases } from './fix-cycle.mjs';
import { previewOf, valid } from '../modules/web/src/check/demo/proposals.ts';

/** The results of the preview's subject the case names: an interface by id, a part by id, the product by key. */
function subjectOf(answer, target) {
  if (target.kind === 'interface') return answer.interfaces.find((i) => i.id === target.id);
  if (target.kind === 'part') return answer.parts.find((p) => p.id === target.id);
  return answer.products.find((p) => p.key === target.id);
}

const rules = (results) => (results ?? []).map((r) => r.rule);

export async function previewCycle({ base, headers = {}, check }) {
  const { send, get } = client(base, headers);
  const changes = async () => (await get('/query/demo/changes')).values;
  const preview = async (body) => {
    const res = await send('POST', '/query/preview', body);
    if (!res.ok) throw new Error(`POST /query/preview: HTTP ${res.status} ${JSON.stringify(res.body)}`);
    return res.body;
  };
  const logged = (await changes()).length;
  for (const c of seededCases(get)) {
    try {
      const planned = await c.plan();
      if (!planned.ok) throw new Error(`no proposal: ${planned.reason}`);
      const { proposal } = planned;
      if (!proposal.cells.every((cell) => valid(cell, cell.value))) throw new Error(`the proposal leaves a value to the engineer: ${proposal.purpose}`);
      const fixed = await preview(previewOf(proposal, proposal.cells.map((cell) => cell.value), c.product));
      const subject = subjectOf(fixed, c.target);
      check(rules(subject?.fixed).includes(c.target.rule) && fixed.tally.newlyFailing === 0,
        `${c.name}: the proposal previews as fixed, nothing newly failing`,
        `${JSON.stringify(fixed.tally)} ${JSON.stringify(subject ?? null)}`);
      const stored = await preview(previewOf(proposal, proposal.cells.map((cell) => cell.before), c.product));
      const still = subjectOf(stored, c.target);
      check(rules(still?.stillFailing).includes(c.target.rule) && !rules(still?.fixed).includes(c.target.rule),
        `${c.name}: the stored values preview as still failing`, JSON.stringify(still ?? null));
      const now = await c.state();
      check(now.fails, `${c.name}: still fails after the previews`, now.detail);
    } catch (err) {
      check(false, `${c.name}: the preview ran`, String(err));
    }
  }
  const after = (await changes()).length;
  check(after === logged, `the change list holds ${logged} row(s) after the previews, as before`, `${after} row(s)`);
}
