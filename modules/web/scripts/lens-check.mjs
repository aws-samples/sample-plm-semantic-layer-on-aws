// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0
// Run: VITE_FIXTURES=1 npx vite --port 5191 --strictPort   then   BASE_URL=http://localhost:5191 node scripts/lens-check.mjs

// Headless check of the viewer lens in fixture mode: a filter fades exactly the parts outside it,
// measured against data/products/<product>.json, the legend counts follow, and the lens survives a
// reload and an interface selection through the hash, next to the subtree root; the supplier filter keeps the rover's parts
// that the chosen supplier built or offers, the second-listed supplier of a part included, and the counts follow.
// PLAYWRIGHT may point at a playwright install's index.mjs when it is not installed here.
import { readFile } from 'node:fs/promises';

const pw = await import(process.env.PLAYWRIGHT ?? 'playwright');
const { chromium } = pw.chromium ? pw : pw.default;
const base = process.env.BASE_URL ?? 'http://localhost:5191';
const PRODUCT = 'ornithopter';
const ROOT = 'FR-ORN-KIT-001';
const RELEASED_WORDS = new Set(['Freigegeben', 'Publié', 'Liberado', 'Released']);

const seed = JSON.parse(await readFile(new URL(`../../../data/products/${PRODUCT}.json`, import.meta.url), 'utf8'));
const truth = new Map(seed.parts.map((p) => [p.id, { site: p.plm.toUpperCase(), released: RELEASED_WORDS.has(p.extended?.lifecycle) }]));

const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
const page = await browser.newPage({ viewport: { width: 1280, height: 800 } });
page.on('pageerror', (e) => console.error('pageerror:', e.message));

const failures = [];
const check = (ok, what) => {
  if (!ok) failures.push(what);
  console.log(`${ok ? 'ok  ' : 'FAIL'} ${what}`);
};
const list = (s) => (s ? s.split(' ') : []);
const host = '.viewer-host';
/** Waits for every STEP file to be drawn, then reads the scene's drawn and faded part ids. */
const scene = async () => {
  await page.waitForFunction((h) => {
    const el = document.querySelector(h);
    return el?.dataset.parts && !document.querySelector('.viewer-status:not(.is-error)');
  }, host, { timeout: 60000 });
  await page.waitForTimeout(300);
  const [parts, faded] = await page.$eval(host, (el) => [el.dataset.parts, el.dataset.lensFaded]);
  return { drawn: list(parts), faded: list(faded) };
};
const same = (a, b) => a.length === b.length && [...a].sort().every((x, i) => x === [...b].sort()[i]);
const expectFaded = async (what, outside) => {
  const { drawn, faded } = await scene();
  const want = drawn.filter((id) => outside(truth.get(id)));
  check(want.length > 0 && want.length < drawn.length && same(faded, want), `${what}: ${faded.length} of ${drawn.length} drawn parts faded, ${want.length} expected`);
  return faded;
};
const legendCount = (site) =>
  page.$eval('.legend', (el, s) => [...el.querySelectorAll('.legend-item')].find((i) => i.textContent.startsWith(s))?.querySelector('.legend-count')?.textContent ?? null, site);

await page.goto(`${base}/#/check`);
await page.waitForSelector('.product select');
await page.selectOption('.product select', PRODUCT);
const plain = await scene();
check(plain.drawn.length > 0 && plain.faded.length === 0, `no lens: ${plain.drawn.length} parts drawn, none faded`);

await page.selectOption('select[data-lens="site"]', 'UK');
await expectFaded('site UK', (p) => p.site !== 'UK');
check((await legendCount('FR')) === '0 parts', 'site UK: the legend counts no FR part');

await page.click('.lens-released');
const faded = await expectFaded('site UK, as released', (p) => p.site !== 'UK' || !p.released);
check(/[?&]site=UK(&|$)/.test(await page.evaluate(() => location.hash)) && /released=1/.test(await page.evaluate(() => location.hash)), 'the hash carries site=UK and released=1');

await page.reload();
await page.waitForSelector('select[data-lens="site"]');
check((await page.$eval('.product select', (s) => s.value)) === PRODUCT, `reload: product still ${PRODUCT}`);
const again = await scene();
check(same(again.faded, faded), `reload: the same ${again.faded.length} parts faded`);
check((await page.$eval('select[data-lens="site"]', (s) => s.value)) === 'UK', 'reload: the site select shows UK');
check((await page.getAttribute('.lens-released', 'aria-pressed')) === 'true', 'reload: As released is on');

await page.evaluate((root) => { location.hash = `#/check?root=${root}&site=UK&released=1`; }, ROOT);
await page.waitForSelector('.cell');
await page.click('.cell >> nth=0');
await page.waitForFunction(() => /^#\/check\/[^?]+\?/.test(location.hash));
const hash = await page.evaluate(() => location.hash);
check(hash.includes(`root=${ROOT}`) && hash.includes('site=UK') && hash.includes('released=1'), `selecting an interface keeps the root and the lens: ${hash}`);

await page.click('.lens-clear');
const cleared = await scene();
check(cleared.faded.length === 0 && !/site=|released=/.test(await page.evaluate(() => location.hash)), 'Clear: nothing faded, the lens leaves the hash');

// The supplier filter: a part is inside when the chosen supplier built it or offers it, wherever that supplier stands
// among its suppliers. The rover's D-38007 is offered by two suppliers; the second is chosen.
const ROVER = JSON.parse(await readFile(new URL('../../../data/products/rover.json', import.meta.url), 'utf8'));
const local = new Map(ROVER.parts.map((p) => [`${p.plm}|${p.extended?.localId ?? p.id}`, p.id]));
const named = new Map(Object.entries(ROVER.extended.suppliers).flatMap(([plm, b]) => b.rows.map((r) => { const v = Object.values(r); return [`${plm}|${v[0]}`, `${v[1]}, ${v[2]}`]; })));
const suppliersOf = new Map(ROVER.parts.map((p) => [p.id, new Set(p.supplier ? [p.supplier] : [])]));
for (const [plm, b] of Object.entries(ROVER.extended.supplierParts)) {
  for (const r of b.rows) {
    const [part, supplier] = Object.values(r);
    suppliersOf.get(local.get(`${plm}|${part}`) ?? local.get(`${plm}|${part}`.replace('/', '-'))).add(named.get(`${plm}|${supplier}`));
  }
}
const SUPPLIER = [...suppliersOf.get('D-38007')].sort()[1];
await page.goto(`${base}/#/check`);
await page.waitForSelector('.product select');
await page.selectOption('.product select', 'rover');
await page.waitForFunction((h) => document.querySelector(h)?.dataset.parts?.split(' ').includes('D-38007'), host, { timeout: 60000 });
const rover = await scene();
const options = await page.$$eval('select[data-lens="supplier"] option', (os) => os.map((o) => o.value).filter(Boolean));
const every = new Set(rover.drawn.flatMap((id) => [...(suppliersOf.get(id) ?? [])]));
check(options.includes(SUPPLIER) && same(options, [...every]), `supplier options: every supplier of the drawn parts (${options.length}), ${SUPPLIER} among them`);
await page.selectOption('select[data-lens="supplier"]', SUPPLIER);
const inside = rover.drawn.filter((id) => suppliersOf.get(id)?.has(SUPPLIER));
const { faded: outside } = await scene();
check(inside.length > 1 && inside.includes('D-38007') && same(outside, rover.drawn.filter((id) => !inside.includes(id))),
  `supplier ${SUPPLIER}: ${rover.drawn.length - outside.length} parts inside, ${inside.length} built or offered by it in the data`);
const counted = await page.$eval('.lens-count b', (b) => Number(b.textContent));
const total = await page.$eval('.legend', (el) => [...el.querySelectorAll('.legend-item')].map((i) => i.textContent).find((x) => /occurrences/.test(x)) ?? '');
const [, parts, occurrences] = /(\d+) parts?, (\d+) occurrences?/.exec(total) ?? [];
check(counted === inside.length && Number(parts) === inside.length && Number(occurrences) >= inside.length,
  `supplier ${SUPPLIER}: the lens counts ${counted} parts, the legend "${total}"`);

await browser.close();
console.log(failures.length ? `lens-check: ${failures.length} FAILED` : 'lens-check: all checks passed');
process.exit(failures.length ? 1 : 0);
