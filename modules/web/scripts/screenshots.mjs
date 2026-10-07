// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// Captures 1280x800 screenshots of every screen from a running dev server in fixture mode:
//   VITE_FIXTURES=1 npx vite --port 5173   then   node scripts/screenshots.mjs
// PLAYWRIGHT may point at a playwright package directory when it is not installed here.
import { mkdir } from 'node:fs/promises';

const pw = await import(process.env.PLAYWRIGHT ?? 'playwright');
const { chromium } = pw.chromium ? pw : pw.default;
const base = process.env.BASE_URL ?? 'http://localhost:5173';
const out = new URL('../screenshots/', import.meta.url).pathname;
await mkdir(out, { recursive: true });

const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
const page = await browser.newPage({ viewport: { width: 1280, height: 800 } });
page.on('console', (m) => m.type() === 'error' && console.error('console:', m.text()));
page.on('pageerror', (e) => console.error('pageerror:', e.message));

const settled = () =>
  page.waitForFunction(() => !document.querySelector('.viewer-status:not(.is-error)') && !document.querySelector('.loading'), null, { timeout: 30000 });
/** Switches screen inside the running app so shared state (the last answer) survives. */
const goInApp = async (hash) => {
  await page.evaluate((h) => { location.hash = h; }, hash);
  await page.waitForTimeout(400);
};
/** Changes the viewer profile in the top bar and waits for the refetched answer and geometry. */
const viewAs = async (profile) => {
  await page.selectOption('.profile:not(.product) select.profile-select', profile);
  await page.waitForTimeout(400);
  await settled();
  await page.waitForTimeout(1500);
};
/** Scrolls the results panel so the n-th match of `sel` starts at its top. */
const scrollTo = async (sel, n = 0) => {
  await page.evaluate(([s, i]) => document.querySelectorAll(s)[i]?.scrollIntoView({ block: 'start' }), [sel, n]);
  await page.waitForTimeout(300);
};
const openCard = async (name, tab) => {
  await page.click(`button.hop:has-text("${name}")`);
  await page.waitForSelector('.drawer');
  if (tab) await page.click(`.drawer-tab:has-text("${tab}")`);
  await page.waitForFunction(() => !document.querySelector('.drawer .loading'), null, { timeout: 15000 });
  await page.waitForTimeout(600);
};

/** Opens the Ask panel from the top bar and sends the product's example question of one kind, once the examples name its records. */
const askExample = async (kind) => {
  await page.click('.topbar button:has-text("Ask")');
  await page.waitForSelector('.ask');
  await page.waitForFunction(() => [...document.querySelectorAll('.ask-chip')].some((c) => c.dataset.ids), null, { timeout: 20000 });
  await page.click(`.ask-chip[data-kind="${kind}"]`);
};
/** Opens the Ask panel on its example questions, once they name the product's records. */
const showExamples = async () => {
  await page.click('.topbar button:has-text("Ask")');
  await page.waitForFunction(() => [...document.querySelectorAll('.ask-chip')].some((c) => c.dataset.ids), null, { timeout: 20000 });
  await page.waitForTimeout(600);
};
/** Opens the Ask panel and types a question into the composer. */
const askText = async (q) => {
  await page.click('.topbar button:has-text("Ask")');
  await page.waitForSelector('.ask');
  await page.fill('.ask-input', q);
  await page.press('.ask-input', 'Enter');
};
/** Waits for the current turn to finish (its tools row is on screen). */
const turnDone = async () => {
  await page.waitForSelector('.ask-turn.is-done .ask-path', { timeout: 30000 });
  await page.waitForTimeout(500);
};
/** On IF-13, as `profile`: opens the correction of the record off the joint plane (the primary button). */
const openCorrection = async (profile) => {
  await viewAs(profile);
  await page.click('.btn-release.is-primary');
  await page.waitForSelector('.release input');
  await scrollTo('.finding');
  await settled();
  await page.waitForTimeout(800);
};
/** As the officer on IF-31: opens the head hoop's "Publish CAD file" card with its pre-filled key. */
const openPublishCad = async () => {
  await viewAs('export-officer');
  await page.click('.detail button:has-text("Publish CAD file")');
  await page.waitForSelector('.publish-cad input');
  await scrollTo('.detail');
  await page.waitForTimeout(400);
};
/** Publishes the head hoop's CAD file and waits for the event to land, the parts to be read again and the head hoop to be drawn. */
const publishCad = async () => {
  await openPublishCad();
  await page.click('.publish-cad button[type="submit"]');
  await page.waitForSelector('.change.is-cad', { timeout: 30000 });
  await page.waitForTimeout(600);
  await settled();
  await page.waitForTimeout(1500);
};
/** The three kinds of change: a correction released in its PLM (its log row lands by event), the IF-30 link written on request, the head hoop's CAD file published by event. */
const officerChanges = async () => {
  await openCorrection('export-officer');
  await page.click('.release button[type="submit"]');
  await page.waitForSelector('.change.is-value', { timeout: 40000 });
  await settled();
  await page.waitForTimeout(800);
  await goInApp('#/check/IF-30');
  await settled();
  await page.waitForTimeout(800);
  await page.click('.publish button[type="submit"]');
  await page.waitForSelector('.change.is-link', { timeout: 30000 });
  await settled();
  await page.waitForTimeout(800);
  await goInApp('#/check/IF-31');
  await settled();
  await page.waitForTimeout(800);
  await page.click('.detail button:has-text("Publish CAD file")');
  await page.waitForSelector('.publish-cad input');
  await page.click('.publish-cad button[type="submit"]');
  await page.waitForSelector('.change.is-cad', { timeout: 30000 });
  await page.waitForTimeout(600);
  await settled();
  await page.waitForTimeout(1500);
  await scrollTo('.detail');
};

// The app opens as "Programme cleared"; shots that need another profile switch it after loading.
const shots = [
  ['interface-check.png', '#/check'],
  ['overview-export-officer.png', '#/check', () => viewAs('export-officer')],
  ['overview-de-engineer.png', '#/check', () => viewAs('de-engineer')],
  ['redacted-interface.png', '#/check/IF-43', () => viewAs('de-engineer')],
  ['position-violation.png', '#/check/IF-13'],
  ['position-violation-fr-engineer.png', '#/check/IF-13', () => viewAs('de-engineer')],
  ['unit-hydraulic-violation.png', '#/check/IF-05'],
  ['hydraulic-violation.png', '#/check/IF-02', () => scrollTo('.detail .finding')],
  ['connector-violation.png', '#/check/IF-03'],
  ['fastener-violation.png', '#/check/IF-25', () => scrollTo('.detail')],
  ['orphan-violation.png', '#/check/IF-30'],
  ['supplier-built-part.png', '#/check/IF-01', () => scrollTo('.detail')],
  ['data-catalogue.png', '#/catalogue'],
  ['rules.png', '#/rules/position'],
  ['rules-warning.png', '#/rules/lifecycleConflict'],
  ['bill-of-materials.png', '#/bom'],
  ['bill-of-materials-wind-turbine.png', '#/bom', async () => {
    await page.selectOption('.product select.profile-select', 'wind-turbine');
    await settled();
    for (const name of ['Ensemble pales', 'Pale complète', 'Tronçon de pied']) await page.click(`button.bom-name:has-text("${name}")`);
    await page.waitForTimeout(400);
  }],
  ['paths-wind-turbine-flow.png', '#/paths/flow/ES-3701/mechanical/down', async () => {
    await page.selectOption('.product select.profile-select', 'wind-turbine');
    await page.waitForSelector('.path-ratio');
    await settled();
  }],
  ['sparql-drawer.png', '#/check', async () => { await page.click('button:has-text("Show SPARQL")'); await page.waitForSelector('.drawer'); await page.waitForTimeout(400); }],
  ['data-flow-empty.png', '#/flow'],
  ['evidence-policy-header.png', '#/check/IF-01', () => openCard('ontop-uk', 'SERVICE arm')],
  ['evidence-ontop-uk-sql.png', '#/check/IF-01', () => openCard('ontop-uk', 'Generated SQL')],
  ['evidence-ontop-uk-rows.png', '#/check/IF-01', () => openCard('ontop-uk', 'Native rows')],
  ['evidence-ontop-uk-r2rml.png', '#/check/IF-01', () => openCard('ontop-uk', 'R2RML')],
  ['evidence-neptune-graph.png', '#/check/IF-01', () => openCard('neptune', 'Graph')],
  ['evidence-shacl-shapes.png', '#/check/IF-02', () => openCard('SHACL rules')],
  ['evidence-shacl-report.png', '#/check/IF-25', () => openCard('SHACL rules', 'Report')],
  ['data-flow.png', '#/check/IF-01', async () => { await goInApp('#/flow'); await page.waitForSelector('.react-flow__edge', { state: 'attached' }); await page.waitForTimeout(1200); }],
  ['data-flow-query.png', '#/check/IF-01', async () => {
    await goInApp('#/flow');
    await page.waitForSelector('.react-flow__edge', { state: 'attached' });
    await page.click('.fnode.is-query');
    await page.waitForTimeout(800);
  }],
  ['data-flow-eventbridge.png', '#/check/IF-01', async () => {
    await goInApp('#/flow');
    await page.waitForSelector('.react-flow__edge', { state: 'attached' });
    await page.click('.fnode.is-eventbridge');
    await page.waitForTimeout(800);
  }],
  ['data-flow-cad.png', '#/check/IF-01', async () => {
    await goInApp('#/flow');
    await page.waitForSelector('.react-flow__edge', { state: 'attached' });
    await page.click('.fnode.is-cad');
    await page.waitForTimeout(800);
  }],
  ['data-flow-core.png', '#/check/IF-01', async () => {
    await goInApp('#/flow');
    await page.waitForSelector('.react-flow__edge', { state: 'attached' });
    await page.click('.fnode.is-coredb');
    await page.waitForTimeout(800);
  }],
  ['data-flow-ontop-core.png', '#/check/IF-01', async () => {
    await goInApp('#/flow');
    await page.waitForSelector('.react-flow__edge', { state: 'attached' });
    await page.click('.fnode.is-ontop.is-atelier');
    await page.waitForTimeout(800);
  }],
  ['data-catalogue-core.png', '#/catalogue', async () => {
    await page.click('.tree-plm.is-atelier .tree-entity');
    await page.waitForFunction(() => document.querySelector('.r2rml-code'), null, { timeout: 15000 });
    await page.waitForTimeout(600);
  }],
  ['tag-line-header.png', '#/check/IF-43', async () => { await viewAs('export-officer'); await scrollTo('.detail'); }],
  ['evidence-ontop-core.png', '#/check/IF-01', () => openCard('ontop-core', 'Generated SQL')],
  ['evidence-ontop-core-rows.png', '#/check/IF-01', () => openCard('ontop-core', 'Native rows')],
  ['evidence-idle-arm.png', '#/check/IF-01', () => openCard('ontop-de')],
  ['evidence-neptune-links.png', '#/check/IF-01', () => openCard('neptune', 'Links and file index')],
  ['architecture.png', '#/check/IF-01', async () => { await goInApp('#/architecture'); await page.waitForTimeout(1500); }],
  ['architecture-agents.png', '#/check/IF-01', async () => {
    await goInApp('#/architecture');
    await page.waitForTimeout(1500);
    await page.evaluate(() => document.querySelector('.arch')?.scrollTo(0, 1e6));
    await page.waitForTimeout(300);
  }],
  ['data-flow-question.png', '#/check', async () => {
    await askExample('failing');
    await turnDone();
    await goInApp('#/flow');
    await page.waitForSelector('.react-flow__edge', { state: 'attached' });
    await page.waitForTimeout(1200);
  }],
  ['ask-examples.png', '#/check', async () => { await viewAs('export-officer'); await showExamples(); }],
  ['ask-examples-wind-turbine.png', '#/check', async () => {
    await viewAs('export-officer');
    await page.selectOption('.product select.profile-select', 'wind-turbine');
    await settled();
    await showExamples();
  }],
  ['ask-streaming.png', '#/check', async () => { await askExample('failing'); await page.waitForTimeout(1400); }],
  ['ask-turn.png', '#/check', async () => { await askExample('failing'); await turnDone(); }],
  ['ask-evidence.png', '#/check', async () => { await askExample('evidence'); await turnDone(); await page.waitForSelector('.drawer'); await page.waitForTimeout(600); }],
  ['ask-sql.png', '#/check', async () => { await askExample('perUnit'); await turnDone(); await page.click('.ask-sql summary'); await page.waitForTimeout(400); }],
  ['ask-redacted.png', '#/check', async () => { await viewAs('de-engineer'); await askText('Where is part FR-ORN-NACI-001 used?'); await turnDone(); }],
  ['release-correction.png', '#/check/IF-13', () => openCorrection('de-engineer')],
  ['publish-link.png', '#/check/IF-30', async () => {
    await viewAs('programme-cleared');
    await scrollTo('.publish');
    await page.waitForTimeout(400);
  }],
  ['cad-unpublished.png', '#/check/IF-31', async () => { await viewAs('export-officer'); await scrollTo('.detail'); }],
  ['findings-list.png', '#/check', async () => { await page.click('.findings-chip'); await page.waitForTimeout(1000); }],
  ['findings-row-open.png', '#/check', async () => { await page.click('.fail-row.is-finding'); await settled(); await page.waitForTimeout(1500); }],
  ['publish-cad.png', '#/check/IF-31', openPublishCad],
  ['publish-cad-waiting.png', '#/check/IF-31', async () => {
    await openPublishCad();
    await page.click('.publish-cad button[type="submit"]');
    await page.waitForSelector('.publish-wait');
    await page.waitForTimeout(600);
  }],
  ['cad-published.png', '#/check/IF-31', publishCad],
  ['changes-strip.png', '#/check/IF-13', officerChanges],
  ['data-flow-change.png', '#/check/IF-13', async () => {
    await officerChanges();
    await goInApp('#/flow');
    await page.waitForSelector('.react-flow__edge', { state: 'attached' });
    await page.click('.flow-mode button:has-text("Last change")');
    await page.waitForSelector('.wire-tag.is-event');
    await page.waitForTimeout(1200);
  }],
  ['architecture-events.png', '#/check/IF-01', async () => {
    await goInApp('#/architecture');
    await page.waitForTimeout(1500);
    await page.evaluate(() => document.querySelector('.arch-layer[aria-label="Events"]')?.scrollIntoView({ block: 'center' }));
    await page.waitForTimeout(300);
  }],
  ['reset-confirm.png', '#/check/IF-13', async () => {
    await officerChanges();
    await page.click('button:has-text("Reset demo data")');
    await page.waitForSelector('.changes-confirm');
    await page.waitForTimeout(300);
  }],
  ['architecture-mcp-tools.png', '#/check/IF-01', async () => {
    await goInApp('#/architecture');
    await page.waitForSelector('.arch-toggle');
    await page.click('.arch-toggle');
    await page.waitForSelector('.arch-tools');
    await page.evaluate(() => document.querySelector('.arch')?.scrollTo(0, 1e6));
    await page.waitForTimeout(400);
  }],
  ['data-flow-query-tools.png', '#/flow', async () => {
    await page.waitForSelector('.fnode.is-query');
    await page.click('.fnode.is-query');
    await page.waitForSelector('.fact-list.is-stacked');
    await page.waitForTimeout(800);
  }],
];
// SHOTS=a.png,b.png limits the run to those files.
const only = process.env.SHOTS?.split(',');
for (const [i, [name, hash, act]] of shots.entries()) {
  if (only && !only.includes(name)) continue;
  // A distinct query string per shot makes each goto a full load, so no state leaks between shots.
  await page.goto(`${base}/?shot=${i}${hash}`);
  await settled();
  await page.waitForTimeout(1500);
  if (act) await act();
  await page.screenshot({ path: out + name });
  console.log('wrote', out + name);
}
await browser.close();
