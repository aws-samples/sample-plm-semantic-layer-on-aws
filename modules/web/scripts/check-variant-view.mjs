// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0
// Run: VITE_FIXTURES=1 npx vite --port 5179 --strictPort   then   BASE_URL=http://localhost:5179 node scripts/check-variant-view.mjs

// Headless check of the viewer under a variant option, in fixture mode. Taking the ornithopter's spring return draws
// the two return springs in place of the two return cords, every part at as many instances as the configuration's
// lines give it (data/products/ornithopter.json), the tally equal to the configuration's figures in the diff, the
// option's interfaces marked and the default option's gone; the hash carries the option, so a reload opens the same
// configuration; a click on a spring selects it, isolating it hides the rest, the site lens fades the parts of the
// other sites, and a click on the left spring anchor's marker opens IF-181, failing its fastener rule; the port table's
// IF-181 row reads both sides of that failure, the UK pin's diameter against the FR bore's. Taking the
// default again restores the base view exactly: the same parts, instances, markers and tally. The wind turbine's
// offshore foundation does the same with its transition piece and monopile, the platform bolt kit at its 48 places.
// Then every row of the port table is scrolled into view and must sit wholly below the sticky tally and above the
// panel's bottom edge. Screenshots go to SHOTS (default ./screenshots/variant-view). Reads the scene through
// window.viewerProbe (development only). PLAYWRIGHT may point at a playwright install's index.mjs.
import { mkdir, readFile } from 'node:fs/promises';

const pw = await import(process.env.PLAYWRIGHT ?? 'playwright');
const { chromium } = pw.chromium ? pw : pw.default;
const base = process.env.BASE_URL ?? 'http://localhost:5179';
const shots = process.env.SHOTS ?? new URL('../screenshots/variant-view/', import.meta.url).pathname;
await mkdir(shots, { recursive: true });

const failures = [];
const check = (ok, what) => {
  if (!ok) failures.push(what);
  console.log(`${ok ? 'ok  ' : 'FAIL'} ${what}`);
};

/** What the screen's profile, programme-cleared, may see (ontology/policy.json). */
const releasable = new Set(JSON.parse(await readFile(new URL('../../../ontology/policy.json', import.meta.url), 'utf8')).profiles['programme-cleared'].releasable);

/** A product's file: its geometric parts, lines and site kits, and its variant groups. */
async function productFile(key) {
  return JSON.parse(await readFile(new URL(`../../../data/products/${key}.json`, import.meta.url), 'utf8'));
}

/**
 * The occurrences of every geometric part under `option` (null: the base product), counted down the lines from the
 * site kits: the lines from and to the items the group's default option lists leave, the option's own lines join.
 */
function expected(file, option) {
  const ext = file.extended;
  let lines = ext.bomLines;
  // A part whose file the file index does not name yet is not drawn, nor one the profile may not see.
  const drawn = (p) => (p.extended?.type ?? 'PART') === 'PART' && !p.cadPending && releasable.has(p.classification.releasableTo);
  let parts = file.parts.filter(drawn).map((p) => p.id);
  const removed = new Set();
  if (option) {
    const group = Object.values(ext.variants).find((g) => option in g.options);
    const fallback = group.options[group.default];
    for (const id of [...(fallback.parts ?? []), ...(fallback.assemblies ?? [])]) removed.add(typeof id === 'string' ? id : id.id);
    const own = group.options[option];
    lines = [...lines.filter((l) => !removed.has(l.parent) && !removed.has(l.child)), ...(own.bomLines ?? [])];
    parts = [...parts.filter((id) => !removed.has(id)), ...(own.parts ?? []).filter((p) => typeof p !== 'string' && drawn(p)).map((p) => p.id)];
  }
  const kids = new Map();
  for (const l of lines) kids.set(l.parent, [...(kids.get(l.parent) ?? []), l]);
  const geometric = new Set(parts);
  const counts = new Map();
  const walk = (id, k) => {
    for (const l of kids.get(id) ?? []) {
      const n = k * (l.placements?.length ?? 1);
      if (geometric.has(l.child)) counts.set(l.child, (counts.get(l.child) ?? 0) + n);
      walk(l.child, n);
    }
  };
  ext.assemblies.filter((a) => a.kind === 'SITE_KIT').forEach((a) => walk(a.id, 1));
  return { counts, removed };
}

const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
const page = await browser.newPage({ viewport: { width: 1280, height: 800 } });
page.on('pageerror', (e) => check(false, `no page error: ${e.message}`));
/** The scene's probe functions, each called in the page by name. */
const probe = {
  parts: () => page.evaluate(() => window.viewerProbe.parts()),
  centres: (id) => page.evaluate((id) => window.viewerProbe.centres(id), id),
  screenPoint: (id, occurrence) => page.evaluate(([id, k]) => window.viewerProbe.screenPoint(id, k), [id, occurrence]),
  isolate: (ids) => page.evaluate((ids) => window.viewerProbe.isolate(ids), ids),
  zoom: (id) => page.evaluate((id) => window.viewerProbe.zoom(id), id),
  clear: () => page.evaluate(() => window.viewerProbe.clear()),
  frames: () => page.evaluate(() => window.viewerProbe.frames()),
};

/** Waits until the parts drawn are `ids` and no STEP file is loading, then for the camera's tween. */
async function settled(ids) {
  await page.waitForFunction((want) => {
    const drawn = document.querySelector('.viewer-host')?.dataset.parts ?? '';
    return drawn === want && !document.querySelector('.viewer-status:not(.is-error)') && document.querySelector('.tally');
  }, [...ids].sort().join(' '), { timeout: 240000 });
  await page.waitForTimeout(1200);
}

/** What the screen shows: the instances per part, the tally figures, the interfaces marked, the hash. */
async function screen() {
  const parts = await probe.parts();
  const tally = await page.$$eval('.tally-figures .figure-n', (els) => els.map((e) => Number(e.textContent)));
  const markers = await page.$$eval('.viewer-overlay [data-interface]', (els) => [...new Set(els.map((e) => e.getAttribute('data-interface')))].sort());
  const instances = Object.fromEntries(Object.entries(parts).sort(([a], [b]) => (a < b ? -1 : 1)).map(([id, p]) => [id, p.count]));
  return { instances, tally, markers, hash: await page.evaluate(() => location.hash) };
}

const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);

/** The configuration's figures as the diff states them: pass, fail, not evaluable. */
async function configurationTally(group) {
  const text = await page.locator('.variant-group', { hasText: group }).locator('.variant-figures dd').first().textContent();
  const m = /(\d+) pass · (\d+) fail · (\d+) not evaluable/i.exec(text ?? '');
  return m ? m.slice(1).map(Number) : text;
}

/** Takes `option` in `group`, then checks the view against the configuration and returns what it shows. */
async function take(file, group, option, label) {
  const want = expected(file, option);
  await page.locator('.variant-group', { hasText: group }).locator('select').selectOption(option);
  await settled(want.counts.keys());
  await page.locator('.variant-group', { hasText: group }).locator('.variant-figures').waitFor({ timeout: 60000 });
  const shown = await screen();
  const wrong = [...want.counts].filter(([id, n]) => shown.instances[id] !== n);
  check(wrong.length === 0, `${label}: ${Object.keys(shown.instances).length} parts at ${Object.values(shown.instances).reduce((a, b) => a + b, 0)} instances, each as many as the configuration's lines give it${wrong.length ? `; wrong: ${wrong.slice(0, 5).map(([id, n]) => `${id} ${shown.instances[id]}/${n}`).join(', ')}` : ''}`);
  const gone = [...want.removed].filter((id) => id in shown.instances);
  check(gone.length === 0, `${label}: none of the ${want.removed.size} items the default option lists is drawn${gone.length ? `; drawn: ${gone.join(', ')}` : ''}`);
  const figures = await configurationTally(group);
  check(same(shown.tally, figures), `${label}: the tally ${shown.tally.join('/')} is the configuration's ${Array.isArray(figures) ? figures.join('/') : figures}`);
  check(shown.hash.includes(`option=${option}`), `${label}: the hash carries the option (${shown.hash})`);
  return { shown, want };
}

/** Puts the group back at its default, then checks the view is the base view exactly. */
async function restore(group, fallback, before, label) {
  await page.locator('.variant-group', { hasText: group }).locator('select').selectOption(fallback);
  await settled(Object.keys(before.instances));
  const after = await screen();
  check(same(after.instances, before.instances), `${label}: back at ${fallback}, the same ${Object.keys(after.instances).length} parts at the same instances`);
  check(same(after.markers, before.markers) && same(after.tally, before.tally), `${label}: the same ${after.markers.length} interfaces marked and the tally ${after.tally.join('/')}`);
  check(!after.hash.includes('option='), `${label}: the hash names no option (${after.hash})`);
}

/** The interfaces the default option and the option list in the file. */
function interfacesOf(file, option) {
  const group = Object.values(file.extended.variants).find((g) => option in g.options);
  return {
    out: group.options[group.default].interfaces,
    own: group.options[option].interfaces.filter((i) => typeof i !== 'string').map((i) => i.id),
  };
}

// The ornithopter, at its defaults.
const ornithopter = await productFile('ornithopter');
await page.goto(`${base}/?check=${Date.now()}#/check`);
await page.waitForSelector('.variants .variant-group', { timeout: 60000 });
await settled(expected(ornithopter, null).counts.keys());
const ornBase = await screen();
await page.screenshot({ path: `${shots}ornithopter-stirrup-return.png` });

const spring = await take(ornithopter, 'Wing return', 'spring-return', 'ornithopter spring return');
check(['SPRG-6210-L', 'FEDR-R-61110'].every((id) => spring.shown.instances[id] === 1) && !('CRET-L-6075' in spring.shown.instances) && !('CRET-R-6085' in spring.shown.instances),
  'ornithopter spring return: the UK and DE springs are drawn, the ES return cords are not');
const orn = interfacesOf(ornithopter, 'spring-return');
check(orn.own.every((id) => spring.shown.markers.includes(id)) && orn.out.every((id) => !spring.shown.markers.includes(id)),
  `ornithopter spring return: ${orn.own.join(', ')} marked, ${orn.out.join(', ')} not`);
await page.screenshot({ path: `${shots}ornithopter-spring-return.png` });

// A link to the configuration opens it.
await page.reload();
await settled(spring.want.counts.keys());
const reloaded = await screen();
check(same(reloaded.instances, spring.shown.instances) && same(reloaded.tally, spring.shown.tally),
  `ornithopter spring return: a reload of the hash opens the same configuration (${Object.keys(reloaded.instances).length} parts, tally ${reloaded.tally.join('/')})`);

// Picking, isolation and the lens act on the option's parts.
await probe.zoom('SPRG-6210-L');
await page.waitForTimeout(1200);
const at = await probe.screenPoint('SPRG-6210-L', 1);
if (at) await page.mouse.click(at.x, at.y);
const picked = at !== null;
await page.waitForSelector('.view-selected', { timeout: 5000 }).catch(() => null);
const chip = await page.$eval('.view-selected', (el) => el.textContent).catch(() => '');
check(picked && chip.includes('SPRG-6210-L'), `ornithopter spring return: a click on the left spring selects it: "${chip}"`);
await page.click('.view-selected-clear').catch(() => null);
await probe.isolate(['SPRG-6210-L', 'FEDR-R-61110']);
await page.waitForTimeout(1200);
const isolated = await probe.parts();
check(isolated['SPRG-6210-L']?.shown && isolated['FEDR-R-61110']?.shown && Object.entries(isolated).filter(([, p]) => p.shown).length === 2,
  'ornithopter spring return: isolating the two springs hides every other part');
await page.screenshot({ path: `${shots}ornithopter-springs-isolated.png` });
await probe.clear();
await page.selectOption('.lens-select[data-lens="site"]', 'UK');
await page.waitForTimeout(600);
const faded = (await page.$eval('.viewer-host', (el) => el.dataset.lensFaded ?? '')).split(' ');
check(!faded.includes('SPRG-6210-L') && faded.includes('FEDR-R-61110'), 'ornithopter spring return: the UK lens keeps the UK spring and fades the DE one');
await page.selectOption('.lens-select[data-lens="site"]', '');
await page.waitForTimeout(600);

// The left spring anchor's marker opens IF-181, the option's interface, failing its fastener rule.
// The overlay is drawn again each frame, so the marker is clicked where it is drawn.
const host = await page.$eval('.viewer-host', (el) => { const r = el.getBoundingClientRect(); return { x: r.left, y: r.top }; });
const marker = await page.$eval('.viewer-overlay [data-interface="IF-181"] circle', (c) => ({ x: Number(c.getAttribute('cx')), y: Number(c.getAttribute('cy')) }));
await page.mouse.click(host.x + marker.x, host.y + marker.y);
await page.waitForSelector('.detail[aria-label="Interface IF-181"]', { timeout: 30000 });
const detail = await page.locator('.detail').innerText();
const path = await page.locator('.path-strip, .answer-path').first().innerText().catch(() => '');
check(/fail/i.test(detail) && /fastener/i.test(detail), 'ornithopter spring return: the IF-181 marker opens its detail, failing the fastener rule');
check(path.includes('variant-diff'), 'ornithopter spring return: the answer path of IF-181 is the variant diff');
check((await page.evaluate(() => location.hash)).startsWith('#/check/IF-181?') , 'ornithopter spring return: the route selects IF-181 and keeps the option');
// The port table's IF-181 row reads both sides of the failing fastener: the UK pin's diameter and the FR bore it fails against.
const anchorRow = (await page.locator('.variant-group', { hasText: 'Wing return' }).locator('.variant-ports tbody tr', { hasText: 'IF-181' }).innerText()).replace(/\s+/g, ' ');
check(['HL 6210-02', '0.315 in (8 mm)', 'FR-ORN-EMPL-L-001-F02', '10 mm'].every((s) => anchorRow.includes(s)),
  `ornithopter spring return: the IF-181 row shows the UK pin 0.315 in (8 mm) against the FR bore FR-ORN-EMPL-L-001-F02 at 10 mm ("${anchorRow}")`);
await page.screenshot({ path: `${shots}ornithopter-if-181.png` });
await page.locator('.viewer-tools .tool').click();
await page.waitForTimeout(800);

// Every row of the port table can be brought wholly into view below the sticky tally.
const rows = page.locator('.variant-group', { hasText: 'Wing return' }).locator('.variant-ports tbody tr');
const count = await rows.count();
const hidden = [];
for (let i = 0; i < count; i++) {
  for (const block of ['start', 'nearest', 'end']) {
    const box = await rows.nth(i).evaluate((row, b) => new Promise((done) => {
      row.scrollIntoView({ block: b });
      requestAnimationFrame(() => requestAnimationFrame(() => {
        const r = row.getBoundingClientRect();
        const tally = document.querySelector('.tally').getBoundingClientRect();
        const panel = document.querySelector('.panel').getBoundingClientRect();
        done({ top: r.top, bottom: r.bottom, under: tally.bottom, edge: panel.bottom });
      }));
    }), block);
    if (box.top < box.under - 0.5 || box.bottom > box.edge + 0.5) hidden.push(`row ${i + 1} ${block}: ${Math.round(box.top)}-${Math.round(box.bottom)} against ${Math.round(box.under)}-${Math.round(box.edge)}`);
  }
}
check(count === 6 && hidden.length === 0, `ornithopter spring return: each of the ${count} port rows scrolls wholly into view below the tally${hidden.length ? `; hidden: ${hidden.join('; ')}` : ''}`);
await rows.nth(count - 1).evaluate((row) => row.scrollIntoView({ block: 'start' }));
await page.waitForTimeout(400);
await page.screenshot({ path: `${shots}ornithopter-port-row-scrolled.png` });

await page.locator('.variant-group', { hasText: 'Wing return' }).scrollIntoViewIfNeeded();
await restore('Wing return', 'stirrup-return', ornBase, 'ornithopter');

// The wind turbine's offshore foundation.
const turbine = await productFile('wind-turbine');
await page.selectOption('.product select', 'wind-turbine');
await page.waitForSelector('.variants .variant-group', { timeout: 60000 });
await settled(expected(turbine, null).counts.keys());
const wtBase = await screen();
await page.screenshot({ path: `${shots}wind-turbine-onshore.png` });
const offshore = await take(turbine, 'Foundation', 'offshore', 'wind turbine offshore');
check(offshore.shown.instances['ES-3741'] === 48 && !('ES-3727' in offshore.shown.instances) && offshore.shown.instances['ES-3739'] === 1,
  'wind turbine offshore: the platform bolt kit at its 48 places and the lower monopile drawn, the anchor cage not');
const wt = interfacesOf(turbine, 'offshore');
check(wt.own.every((id) => offshore.shown.markers.includes(id)) && wt.out.every((id) => !offshore.shown.markers.includes(id)),
  `wind turbine offshore: ${wt.own.join(', ')} marked, ${wt.out.join(', ')} not`);
await page.screenshot({ path: `${shots}wind-turbine-offshore.png` });
await page.locator('.variant-group', { hasText: 'Foundation' }).scrollIntoViewIfNeeded();
await restore('Foundation', 'onshore', wtBase, 'wind turbine');

await browser.close();
console.log(failures.length ? `check-variant-view: ${failures.length} FAILED` : 'check-variant-view: all checks passed');
process.exit(failures.length ? 1 : 0);
