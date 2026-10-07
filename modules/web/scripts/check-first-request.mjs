// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0
// Run: VITE_FIXTURES=1 npx vite --port 5179 --strictPort   then   BASE_URL=http://localhost:5179 node scripts/check-first-request.mjs

// Headless check that the screens ask for a product's data as soon as the product is known, in fixture mode (the
// fixtures record each request in page time). On the first load, the parts and placements are requested within
// LIMIT_MS of the product list, before the product rules' answer (GET /query/products), which the fixtures answer later
// than the list as the service does; with an option in the hash, so is the variants list, beside the configured parts
// and placements. On a switch to another product, made while the wind turbine's STEP files are still loading, the
// first request for the new product leaves within LIMIT_MS of the selection. A switch to the export-control officer on the CubeSat,
// whose mass-limit finding (seen by the officer alone) needs its bill of materials, reads the product list and the product rules once each, and the bill
// of materials leaves within LIMIT_MS of that one rules answer; Run rules reads each of the two once too. Exits 1 on a
// failed assertion.
// PLAYWRIGHT may point at a playwright package entry when it is not installed here.
const pw = await import(process.env.PLAYWRIGHT ?? 'playwright');
const { chromium } = pw.chromium ? pw : pw.default;
const base = process.env.BASE_URL ?? 'http://localhost:5179';
const LIMIT_MS = 100;

let failed = 0;
const check = (ok, what, detail = '') => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${what}${detail ? `  (${detail})` : ''}`);
  if (!ok) failed += 1;
};

const browser = await chromium.launch();
const requests = (page) => page.evaluate(() => globalThis.__fixtureRequests ?? []);
const ms = (n) => (n === undefined ? 'never' : `${n.toFixed(0)} ms`);

/** Opens the site at `hash` and returns the requests the first product's screens made, timed from the products answer. */
async function firstLoad(hash) {
  const page = await browser.newPage({ viewport: { width: 1280, height: 800 } });
  page.on('pageerror', (e) => check(false, 'no page error', e.message));
  await page.goto(`${base}/?check=${Date.now()}${hash}`);
  await page.waitForFunction(() => (globalThis.__fixtureRequests ?? []).some((r) => r.path.startsWith('/query/parts')), null, { timeout: 30000 });
  await page.waitForTimeout(500);
  const all = await requests(page);
  const answered = all.find((r) => r.path.startsWith('/query/products/list'))?.answeredAt;
  const ruled = all.find((r) => r.path === '/query/products')?.answeredAt;
  const after = (prefix) => {
    const r = all.find((x) => x.path.startsWith(prefix) && x.at >= answered);
    return r && r.at - answered;
  };
  return { page, after, beforeRules: (prefix) => all.some((x) => x.path.startsWith(prefix) && (ruled === undefined || x.at < ruled)) };
}

{
  const { page, after, beforeRules } = await firstLoad('#/check');
  for (const path of ['/query/parts', '/query/placements']) {
    check(after(path) !== undefined && after(path) <= LIMIT_MS, `first load: ${path} within ${LIMIT_MS} ms of the product list`, ms(after(path)));
    check(beforeRules(path), `first load: ${path} leaves before the product rules' answer`);
  }
  await page.close();
}
{
  const { page, after } = await firstLoad('#/check?option=spring-return');
  for (const path of ['/query/parts', '/query/placements', '/query/variants']) {
    check(after(path) !== undefined && after(path) <= LIMIT_MS, `option in the hash: ${path} within ${LIMIT_MS} ms of the product list`, ms(after(path)));
  }
  await page.close();
}
{
  const { page } = await firstLoad('#/check');
  const select = page.locator('.topbar select').first();
  await select.selectOption('wind-turbine');
  await page.waitForTimeout(1500);
  await page.evaluate(() => {
    document.querySelector('.topbar select').addEventListener('change', () => { globalThis.__selectedAt = performance.now(); }, { capture: true, once: true });
  });
  await select.selectOption('rover');
  await page.waitForFunction(() => (globalThis.__fixtureRequests ?? []).some((r) => r.path.includes('product=rover')), null, { timeout: 30000 });
  const selectedAt = await page.evaluate(() => globalThis.__selectedAt);
  const first = (await requests(page)).find((r) => r.path.includes('product=rover'));
  const delay = first.at - selectedAt;
  check(delay <= LIMIT_MS, `switch from the loading wind turbine to the rover: first request within ${LIMIT_MS} ms of the selection`, `${first.path.split('?')[0]} after ${ms(delay)}`);
  await page.close();
}

{
  const { page } = await firstLoad('#/check');
  await page.locator('.topbar select').first().selectOption('cubesat');
  await page.waitForFunction(() => (globalThis.__fixtureRequests ?? []).some((r) => r.path === '/query/products' && r.answeredAt
    && (globalThis.__fixtureRequests ?? []).some((p) => p.path === '/query/parts?product=cubesat' && p.answeredAt)), null, { timeout: 30000 });
  await page.waitForTimeout(1000);
  // Only the officer sees every part, so only the officer's rules answer carries the CubeSat's mass-limit finding.
  const profile = 'export-officer';
  await page.locator('.profile:not(.product) select.profile-select').selectOption(profile);
  await page.waitForFunction((p) => (globalThis.__fixtureRequests ?? []).some((r) => r.profile === p && r.path.startsWith('/query/bom')), profile, { timeout: 30000 });
  await page.waitForTimeout(3000);
  const mine = (await requests(page)).filter((r) => r.profile === profile);
  const count = (path) => mine.filter((r) => r.path === path).length;
  check(count('/query/products/list') === 1 && count('/query/products') === 1,
    'a profile switch reads the product list once and the product rules once', `list ${count('/query/products/list')}, rules ${count('/query/products')}`);
  const ruled = mine.find((r) => r.path === '/query/products')?.answeredAt;
  const bom = mine.find((r) => r.path.startsWith('/query/bom'));
  const delay = bom && ruled !== undefined ? bom.at - ruled : undefined;
  check(delay !== undefined && delay <= LIMIT_MS, `after a profile switch the CubeSat's bill of materials leaves within ${LIMIT_MS} ms of the first rules answer`, ms(delay));
  const before = (await requests(page)).length;
  await page.getByRole('button', { name: /run rules/i }).first().click();
  await page.waitForTimeout(3000);
  const run = (await requests(page)).slice(before);
  const runCount = (path) => run.filter((r) => r.path === path).length;
  check(runCount('/query/products/list') === 1 && runCount('/query/products') === 1,
    'Run rules reads the product list once and the product rules once', `list ${runCount('/query/products/list')}, rules ${runCount('/query/products')}`);
  await page.close();
}

await browser.close();
if (failed) {
  console.log(`${failed} check(s) failed`);
  process.exit(1);
}
console.log('check-first-request: all checks passed');
