// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// Headless check of the Ask panel against a running dev server in fixture mode:
//   VITE_FIXTURES=1 npx vite --port 5173   then   node scripts/check-ask-everywhere.mjs
// Opening Ask on Interface check keeps the viewer's canvas; the example questions name records of the product on
// screen only (every id is in that product's file, no id or name of another product), change with the product and name
// no record hidden from the profile; the panel then sits beside every other tab, as wide as its column, with the same conversation, and the
// top bar's Ask closes it from any of them. Exits 1 on a failed check.
// PLAYWRIGHT may point at a playwright package entry when it is not installed here.
import { readdir, readFile } from 'node:fs/promises';

const pw = await import(process.env.PLAYWRIGHT ?? 'playwright');
const { chromium } = pw.chromium ? pw : pw.default;
const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
let failed = 0;
const check = (ok, what, d = '') => { console.log(`${ok ? 'PASS' : 'FAIL'}  ${what}${d ? `  (${d})` : ''}`); if (!ok) failed++; };
page.on('pageerror', (e) => check(false, 'no page error', e.message));
const base = process.env.BASE_URL ?? 'http://localhost:5173';
await page.goto(`${base}/?a=${Date.now()}#/check`);
await page.waitForSelector('.viewer-host canvas');
await page.evaluate(() => { document.querySelector('.viewer-host canvas').dataset.mark = 'first'; });
await page.click('button:has-text("Ask")');
await page.waitForSelector('.ask');
check(await page.$eval('.viewer-host canvas', (c) => c.dataset.mark === 'first').catch(() => false), 'opening Ask on Interface check keeps the viewer canvas');

// Every string a product file holds: the ids of its parts, features, interfaces and assemblies among them.
const dir = new URL('../../../data/products/', import.meta.url);
const files = new Map();
for (const f of await readdir(dir)) {
  const d = JSON.parse(await readFile(new URL(f, dir), 'utf8'));
  const strings = new Set();
  const walk = (v) => { if (typeof v === 'string') strings.add(v); else if (v && typeof v === 'object') Object.values(v).forEach(walk); };
  walk(d);
  const releasable = [...d.parts, ...(d.extended?.assemblies ?? [])].map((x) => [x.id, x.classification?.releasableTo ?? 'ALL']);
  files.set(d.product.key, { name: d.product.name, strings, releasable });
}
const policy = JSON.parse(await readFile(new URL('../../../ontology/policy.json', import.meta.url), 'utf8')).profiles;
/** The part and assembly ids of a product that `profile` may not see, from the files' classification and the policy. */
const hiddenFrom = (key, profile) => new Set(files.get(key).releasable.filter(([, to]) => !policy[profile].releasable.includes(to)).map(([id]) => id));
const ID_LIKE = /\b[A-Z][A-Z0-9]*-[A-Z0-9](?:[A-Z0-9]|-[A-Z0-9])*\b|\b[A-Z]+\d\d[A-Z0-9]*\b/g;
const examples = () => page.$$eval('.ask-chip', (cs) => cs.map((c) => ({ kind: c.dataset.kind, ids: c.dataset.ids ? c.dataset.ids.split(' ') : [], text: c.textContent })));
/** Waits for the examples built from the answers: a chip names an id, or the list stops changing. */
const settledExamples = async (before) => {
  await page.waitForFunction((b) => {
    const t = [...document.querySelectorAll('.ask-chip')].map((c) => c.textContent).join('|');
    return t !== b && [...document.querySelectorAll('.ask-chip')].some((c) => c.dataset.ids);
  }, before, { timeout: 20000 }).catch(() => null);
  await page.waitForTimeout(400);
  return examples();
};
const asText = (list) => list.map((e) => e.text).join('|');
const productSelect = '.profile.product select';
const profileSelect = '.profile:not(.product) select.profile-select';
const enabled = () => page.waitForSelector(`${productSelect}:not([disabled])`, { timeout: 20000 });
await page.selectOption(profileSelect, 'export-officer');
await enabled();
const keys = await page.$$eval(`${productSelect} option`, (os) => os.map((o) => o.value));
let previous = '';
for (const key of keys) {
  await enabled();
  await page.selectOption(productSelect, key);
  const list = await settledExamples(previous);
  const own = files.get(key);
  const bad = list.flatMap((e) => [
    ...e.ids.filter((id) => !own?.strings.has(id) || !e.text.includes(id)).map((id) => `${e.kind}: ${id} not in ${key}`),
    ...(e.text.match(ID_LIKE) ?? []).filter((t) => !e.ids.includes(t)).map((t) => `${e.kind}: ${t} unlisted`),
    ...[...files].filter(([k, f]) => k !== key && e.text.includes(f.name)).map(([k]) => `${e.kind}: names ${k}`),
  ]);
  check(list.some((e) => e.ids.length) && bad.length === 0, `${key}: every suggested id is a record of ${key}`, bad.join('; '));
  check(asText(list) !== previous, `${key}: switching product changes the examples`);
  console.log(list.map((e) => `      ${e.kind.padEnd(14)} ${e.text}`).join('\n'));
  previous = asText(list);
}
await enabled();
await page.selectOption(productSelect, keys[0]);
const officer = await settledExamples(previous);
await page.selectOption(profileSelect, 'de-engineer');
const engineer = await settledExamples(asText(officer));
// The examples are built from what the profile sees: no example names a record hidden from it, and an example that
// named a record the officer sees and the engineer does not is replaced.
const hidden = hiddenFrom(keys[0], 'de-engineer');
const named = (list) => list.flatMap((e) => e.ids).filter((id) => hidden.has(id));
check(hidden.size > 0 && named(engineer).length === 0, `the examples name none of the ${hidden.size} records hidden from the profile`, named(engineer).join(', '));
check(named(officer).length === 0 || asText(engineer) !== asText(officer),
  `switching profile changes the examples that named a record the new profile may not see${named(officer).length ? ` (${named(officer).join(', ')})` : ''}`,
  `${asText(officer)} / ${asText(engineer)}`);
await page.selectOption(profileSelect, 'programme-cleared');
await settledExamples(asText(engineer));
await page.click('.ask-chip').catch(() => null);
await page.waitForTimeout(3000);
const turns = await page.$$eval('.ask-body > *', (e) => e.length);
for (const [hash, sel] of [['#/bom', '.bom'], ['#/catalogue', '.catalogue, .cat'], ['#/rules', '.rules-item'], ['#/paths', '.paths'], ['#/architecture', '.arch, .architecture'], ['#/flow', '.react-flow, .panel']]) {
  await page.evaluate((h) => { location.hash = h; }, hash);
  await page.waitForSelector(sel, { timeout: 20000 }).catch(() => null);
  await page.waitForTimeout(500);
  const m = await page.evaluate((s) => {
    const ask = document.querySelector('.ask')?.getBoundingClientRect();
    const scr = document.querySelector('.with-ask-screen')?.getBoundingClientRect();
    return { ask: ask && { l: ask.left, w: ask.width, h: ask.height }, screen: scr && { r: scr.right, h: scr.height }, button: document.querySelector('.topbar .btn-ghost')?.getAttribute('aria-pressed'), body: document.querySelectorAll('.ask-body > *').length, docW: document.documentElement.scrollWidth };
  }, sel);
  check(!!m.ask && m.ask.w === 360 && Math.abs(m.screen.r - m.ask.l) < 1 && m.ask.h > 600 && m.docW <= 1440 && m.button === 'true' && m.body === turns,
    `${hash}: the Ask panel sits beside the screen with the same conversation`, JSON.stringify(m));
}
await page.click('.topbar .btn-ghost');
await page.waitForTimeout(300);
check(!(await page.$('.ask')), 'Ask closes from any screen');
await browser.close();
console.log(failed ? `${failed} failed` : 'all checks passed');
process.exit(failed ? 1 : 0);
