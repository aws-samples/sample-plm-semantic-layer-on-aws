// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// Headless check of the Rules screen against a running dev server in fixture mode:
//   VITE_FIXTURES=1 npx vite --port 5173   then   node scripts/check-rules.mjs
// Every rule of the fixture is listed. The failing and seeded lists name the product on screen alone, a product switch
// replaces them, and a note counts the other products where the rule fails, as the profile's own answer counts them; a
// failing interface record and a failing part record each open the Interface check screen on that record, the lens
// parameters of the hash kept. SHOTS names a directory for a screenshot. Exits 1 on a failed assertion.
// PLAYWRIGHT may point at a playwright package entry when it is not installed here.
import { readFile } from 'node:fs/promises';

const pw = await import(process.env.PLAYWRIGHT ?? 'playwright');
const { chromium } = pw.chromium ? pw : pw.default;
const base = process.env.BASE_URL ?? 'http://localhost:5173';
const fixture = JSON.parse(await readFile(new URL('../src/dev/fixture-rules.json', import.meta.url), 'utf8'));

let failed = 0;
const check = (ok, what, detail = '') => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${what}${ok || !detail ? '' : `: ${detail}`}`);
  if (!ok) failed += 1;
};

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1280, height: 800 } });
page.on('pageerror', (e) => check(false, 'no page error', e.message));

const same = (a, b) => a.length === b.length && [...a].sort().every((x, i) => x === [...b].sort()[i]);
const productShown = () => page.$eval('.product select', (s) => s.options[s.selectedIndex]?.text ?? '');
const open = async (hash) => {
  await page.goto(`${base}/?check=${Date.now()}${hash}`);
  await page.waitForSelector('.rules-item');
  await page.waitForSelector('.rules-aside .rules-row');
};

await open('#/rules');
const listed = await page.$$eval('.rules-item-name', (els) => els.map((e) => e.textContent));
const missing = fixture.rules.map((r) => r.name).filter((n) => !listed.includes(n));
check(missing.length === 0 && listed.length === fixture.rules.length, `all ${fixture.rules.length} fixture rules listed`, `missing ${missing.join(', ')}; listed ${listed.length}`);

/** The fixture's own failing records for a profile, every product: what the screen must agree with. */
const truth = (profile) => page.evaluate(async (p) => {
  const { failuresFor } = await import('/src/dev/fixture-rules.ts');
  const { policyFor } = await import('/src/dev/fixture-policy.ts');
  return failuresFor(policyFor(p));
}, profile);
const select = async (product) => {
  await page.selectOption('.product select', product);
  await page.waitForFunction((key) => {
    const rows = [...document.querySelectorAll('.rules-aside .rules-section:first-child .rules-row')];
    return !document.querySelector('.rules-aside .loading') && (rows.length === 0 || rows.every((r) => r.dataset.product === key));
  }, product, { timeout: 15000 }).catch(() => null);
  await page.waitForTimeout(300);
};
const failingShown = () => page.$$eval('.rules-aside .rules-section:first-child .rules-row', (rows) =>
  rows.map((r) => ({ product: r.dataset.product, ids: [...r.querySelectorAll('a.rules-id')].map((a) => a.lastChild.textContent) })));
const seededShown = () => page.$$eval('.rules-aside .rules-section:nth-child(2) .rules-row', (rows) => rows.map((r) => r.dataset.product));
const noteShown = () => page.$eval('.rules-elsewhere', (e) => e.textContent).catch(() => '');
const othersIn = (all, rule, product) => new Set(all.filter((f) => f.rule === rule && f.product !== product).map((f) => f.product)).size;
const nameOf = (key) => page.$eval('.product select', (s, k) => [...s.options].find((o) => o.value === k)?.text ?? k, key);

// Scoped to the product on screen: the failing and seeded lists name it alone, the note counts the other products.
const cleared = await truth('programme-cleared');
for (const [rule, product] of [['position', 'ornithopter'], ['position', 'rover'], ['meshModule', 'wind-turbine']]) {
  await open(`#/rules/${rule}`);
  await select(product);
  const rows = await failingShown();
  const want = cleared.filter((f) => f.rule === rule && f.product === product).map((f) => f.id);
  check(rows.length === 1 && rows[0].product === product && same(rows[0].ids, want), `${rule} on ${product}: only ${product}'s ${want.length} records listed`, JSON.stringify(rows));
  const seeded = await seededShown();
  check(seeded.every((k) => k === product), `${rule} on ${product}: the seeded list names ${product} alone`, seeded.join(', '));
  const n = othersIn(cleared, rule, product);
  const note = await noteShown();
  if (rule === 'meshModule') check(rows[0]?.ids.includes('D-37031'), 'meshModule fails live on the wind turbine gear D-37031', rows[0]?.ids.join(', '));
  check(n === 0 ? note === '' : note === `also failing in ${n} other product${n === 1 ? '' : 's'}`, `${rule} on ${product}: the note counts ${n} other products, no names`, note);
}
await open('#/rules/position');
await select('ornithopter');
const orni = await failingShown();
await select('rover');
const rover = await failingShown();
check(orni[0]?.product === 'ornithopter' && rover[0]?.product === 'rover' && !same(orni[0].ids, rover[0].ids), 'a product switch replaces the records', `${JSON.stringify(orni)} -> ${JSON.stringify(rover)}`);
if (process.env.SHOTS) await page.screenshot({ path: `${process.env.SHOTS}/rules-position-rover.png` });

// The count is the profile's own: a rule whose other products differ between two profiles shows each profile's count.
const de = await truth('de-engineer');
const rules = [...new Set(cleared.map((f) => f.rule))];
const pick = rules.flatMap((r) => ['ornithopter', 'rover', 'wind-turbine'].map((p) => [r, p])).find(([r, p]) => othersIn(cleared, r, p) !== othersIn(de, r, p));
if (!pick) check(false, 'a rule whose count of other products differs between programme-cleared and de-engineer');
else {
  const [rule, product] = pick;
  await open(`#/rules/${rule}`);
  await select(product);
  const asCleared = await noteShown();
  await page.selectOption('.profile:not(.product) select.profile-select', 'de-engineer');
  await page.waitForTimeout(1500);
  const asDe = await noteShown();
  const say = (n) => (n === 0 ? '' : `also failing in ${n} other product${n === 1 ? '' : 's'}`);
  check(asCleared === say(othersIn(cleared, rule, product)) && asDe === say(othersIn(de, rule, product)) && asCleared !== asDe,
    `${rule} on ${product}: the count follows the profile`, `"${asCleared}" -> "${asDe}"`);
  await page.selectOption('.profile:not(.product) select.profile-select', 'programme-cleared');
}

/** Opens the rule on the product and clicks its first failing record whose link matches. */
async function follow(rule, product, linkFilter, lens = '') {
  await open(`#/rules/${rule}${lens}`);
  await select(product);
  const hrefs = await page.$$eval('.rules-aside .rules-section:first-child a.rules-id', (as) => as.map((a) => [a.getAttribute('href'), a.lastChild.textContent]));
  const j = hrefs.findIndex(([h]) => linkFilter.test(h));
  if (j < 0) {
    check(false, `${rule} on ${product}: a failing record whose link matches ${linkFilter}`);
    return null;
  }
  await page.locator('.rules-aside .rules-section:first-child a.rules-id').nth(j).click();
  await page.waitForTimeout(1500);
  return { id: hrefs[j][1], name: await nameOf(product) };
}

const itf = await follow('position', 'rover', /^#\/check\//);
if (itf) {
  await page.waitForSelector('.detail-id', { timeout: 15000 }).catch(() => null);
  const hash = await page.evaluate(() => location.hash);
  check(hash === `#/check/${encodeURIComponent(itf.id)}`, `interface record: hash is #/check/${itf.id}`, hash);
  check((await productShown()) === itf.name, `interface record: the product stays ${itf.name}`, await productShown());
  const shown = await page.$eval('.detail-id', (e) => e.textContent).catch(() => null);
  check(shown === itf.id, `interface record: Interface check detail shows ${itf.id}`, String(shown));
}

// The lens rides in the hash next to the root: a record opened from the Rules screen keeps it.
const lifecycle = cleared.find((f) => f.rule === 'lifecycleConflict' && f.kind === 'part');
const part = await follow('lifecycleConflict', lifecycle.product, /\?root=/, '?site=FR&released=1');
if (part) {
  const lensed = new URLSearchParams((await page.evaluate(() => location.hash)).split('?')[1] ?? '');
  check(lensed.get('site') === 'FR' && lensed.get('released') === '1' && lensed.get('root') === part.id, 'a part record keeps the lens beside its root', lensed.toString());
  await page.waitForSelector('.subtree-bar', { timeout: 15000 }).catch(() => null);
  check((await productShown()) === part.name, `part record: the product stays ${part.name}`, await productShown());
  const crumb = await page.$eval('.subtree-bar [aria-current="page"] .mono', (e) => e.textContent).catch(() => null);
  check(crumb === part.id, `part record: subtree bar shows ${part.id}`, String(crumb));
  await page.waitForFunction(() => !document.querySelector('.loading'), null, { timeout: 15000 }).catch(() => null);
  const errors = await page.$$eval('.error-state .error-body', (els) => els.map((e) => e.textContent));
  check(errors.length === 0, 'part record: the rooted Interface check answers', errors.join('; '));
  if (process.env.SHOT) await page.screenshot({ path: process.env.SHOT });
}

await browser.close();
console.log(failed ? `${failed} check(s) failed` : 'all checks passed');
process.exit(failed ? 1 : 0);
