// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// Headless check of the option switch on the Interface check against a running dev server in fixture mode:
//   VITE_FIXTURES=1 npx vite --port 5173   then   node scripts/check-variants.mjs
// The ornithopter lists its two variant groups at their defaults; taking the spring return lists its six ports, the
// left spring anchor added and failing the fastener rule, and taking the one-piece crank turns the left crank bearing
// from fail to pass. SHOT=<path> writes a 1280x800 screenshot of the spring return's diff. Exits 1 on a failed assertion.
// PLAYWRIGHT may point at a playwright package entry when it is not installed here.
const pw = await import(process.env.PLAYWRIGHT ?? 'playwright');
const { chromium } = pw.chromium ? pw : pw.default;
const base = process.env.BASE_URL ?? 'http://localhost:5173';

let failed = 0;
const check = (ok, what, detail = '') => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${what}${ok || !detail ? '' : `: ${detail}`}`);
  if (!ok) failed += 1;
};

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1280, height: 800 } });
page.on('pageerror', (e) => check(false, 'no page error', e.message));

await page.goto(`${base}/?check=${Date.now()}#/check`);
await page.waitForSelector('.variants .variant-group', { timeout: 30000 });
const groups = await page.$$eval('.variant-switch span', (els) => els.map((e) => e.textContent));
check(JSON.stringify(groups) === JSON.stringify(['Crank build', 'Wing return']), 'the ornithopter lists its two variant groups', groups.join(', '));
const defaults = await page.$$eval('.variant-switch select', (els) => els.map((s) => s.value));
check(JSON.stringify(defaults) === JSON.stringify(['assembled', 'stirrup-return']), 'each switch shows its default option', defaults.join(', '));
check((await page.$$('.variant-ports')).length === 0, 'no diff while every group is at its default');

const rows = (group) => page.locator('.variant-group', { hasText: group }).locator('.variant-ports tbody tr');
await page.locator('.variant-group', { hasText: 'Wing return' }).locator('select').selectOption('spring-return');
await rows('Wing return').first().waitFor({ state: 'attached', timeout: 30000 });
const spring = await rows('Wing return').allTextContents();
check(spring.length === 6, 'the spring return lists six ports', String(spring.length));
const anchor = spring.find((t) => t.includes('FR-ORN-EMPL-L-001') && t.includes('spring anchor'));
check(Boolean(anchor) && anchor.includes('added') && anchor.includes('fail') && anchor.includes('Fastener'),
  'the left spring anchor is added and fails the fastener rule', anchor ?? 'absent');
const eye = spring.find((t) => t.includes('ROOT-6180-L') && t.includes('return eye'));
check(Boolean(eye) && eye.includes('changed') && eye.includes('SPRG-6210-L') && eye.includes('0.3937 in'),
  'the left return eye changes its mate to the UK spring, 0.3937 in', eye ?? 'absent');
if (process.env.SHOT) {
  await page.locator('.variant-group', { hasText: 'Wing return' }).scrollIntoViewIfNeeded();
  await page.screenshot({ path: process.env.SHOT });
}

await page.locator('.variant-group', { hasText: 'Crank build' }).locator('select').selectOption('one-piece');
await rows('Crank build').first().waitFor({ state: 'attached', timeout: 30000 });
const crank = await rows('Crank build').allTextContents();
const bearing = crank.find((t) => t.includes('crank bearing, left'));
check(crank.length === 3 && Boolean(bearing) && /IF-25\s*fail/.test(bearing) && /IF-184\s*pass/.test(bearing),
  'the one-piece crank turns the left crank bearing from fail to pass', bearing ?? String(crank.length));

await browser.close();
if (failed) {
  console.log(`${failed} check(s) failed`);
  process.exit(1);
}
console.log('variant switch checks passed');
