// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0
// Run: VITE_FIXTURES=1 npx vite --port 5179 --strictPort   then   BASE_URL=http://localhost:5179 node scripts/check-paths.mjs

// Headless check of the Paths screen, Follow the flow, in fixture mode. Each step the walk shows is read as a link between
// its two parts: its interface, or the words standing for none, and its gear stage. Along the ornithopter's mechanical
// flow from the crank shaft, the step from the Spanish lantern pinion to the French peg wheel is a gear mesh between two
// sites that no interface joins: it says so, and its stage carries the ratio in the ratio card's convention, output turns
// per input turn (8 teeth driving 32: 0.25), the card reading the same figure. On the wind turbine, a mesh inside the
// German gearbox is a step inside one site's assembly, and the yaw pinion's edge onto the French slewing ring, two sites
// and no mesh recorded, declares no interface. SHOTS names a directory for screenshots (default ./screenshots/paths).
// PLAYWRIGHT may point at a playwright install's index.mjs.
import { mkdir, readFile } from 'node:fs/promises';

const pw = await import(process.env.PLAYWRIGHT ?? 'playwright');
const { chromium } = pw.chromium ? pw : pw.default;
const base = process.env.BASE_URL ?? 'http://localhost:5179';
const shots = process.env.SHOTS ?? new URL('../screenshots/paths/', import.meta.url).pathname;
await mkdir(shots, { recursive: true });

const failures = [];
const check = (ok, what) => {
  if (!ok) failures.push(what);
  console.log(`${ok ? 'ok  ' : 'FAIL'} ${what}`);
};

/** The gears of a product's file (data/products): tooth count and module in mm, by part id. */
async function gearsOf(key) {
  const file = JSON.parse(await readFile(new URL(`../../../data/products/${key}.json`, import.meta.url), 'utf8'));
  return new Map(file.parts.filter((p) => p.extended?.gear).map((p) => [p.id, { teeth: p.extended.gear.teeth, mm: Number(p.extended.gear.module) * (p.plm === 'UK' ? 25.4 : 1) }]));
}

/** The words a step without an interface shows, by what the product records of its two parts. */
const SAME_SITE = "no interface: inside one site's assembly";
const CROSS_SITE_MESH = 'gear mesh, no interface declared';
const CROSS_SITE = 'no interface declared';

/** The ratio card's figure, as the card and a stage write it: "×0.25" is 0.25 output turns per input turn. */
const figureOf = (text) => /×(\d+(?:\.\d+)?)\s*$/.exec(text ?? '')?.[1] ?? null;
const speedUp = (gears, driver, driven) => String(Number((gears.get(driver).teeth / gears.get(driven).teeth).toFixed(2)));

const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
const page = await browser.newPage({ viewport: { width: 1280, height: 800 } });
page.on('pageerror', (e) => check(false, `no page error: ${e.message}`));

/** The links on screen: each between the ids of its two parts, with its interface or the words standing for none, and its stage. */
const links = () => page.$$eval('.path-result .path-chain', (chains) => chains.flatMap((chain) => {
  const items = [...chain.children];
  const id = (node) => node?.querySelector('.path-part .mono')?.textContent ?? '';
  return items.flatMap((el, i) => (el.classList.contains('path-link') ? [{
    from: id(items[i - 1]),
    to: id(items[i + 1]),
    joint: el.querySelector('.path-joint-id')?.textContent ?? null,
    label: el.querySelector('.path-internal')?.textContent ?? null,
    stage: el.querySelector('.path-stage')?.textContent ?? null,
  }] : []));
}));
const linkOf = (all, from, to) => all.find((l) => l.from === from && l.to === to);
const describe = (l) => (l ? `joint ${l.joint}, label "${l.label}", stage "${l.stage}"` : 'no such step on screen');

/** Follows a flow by its hash and waits until the walk names `reached`. */
async function flow(hash, reached) {
  await page.evaluate((h) => { location.hash = h; }, hash);
  await page.waitForFunction((id) => [...document.querySelectorAll('.path-result .path-chain .path-part .mono')].some((e) => e.textContent === id), reached, { timeout: 60000 });
  await page.waitForTimeout(400);
}

// The ornithopter's mechanical flow from the crank shaft: the lantern pinion through the peg wheel to the windlass drum.
const orn = await gearsOf('ornithopter');
await page.goto(`${base}/?check=${Date.now()}#/paths/flow/MANV-6011/mechanical/down`);
await page.waitForSelector('.path-ratio', { timeout: 60000 });
await page.waitForTimeout(600);
let all = await links();
const viaJoint = linkOf(all, 'MANV-6011', 'LINT-6015');
check(viaJoint?.joint === 'IF-150' && viaJoint.label === null, `ornithopter: the crank shaft turns the pinion through IF-150 (${describe(viaJoint)})`);
const mesh = linkOf(all, 'LINT-6015', 'FR-ORN-ROUE-001');
check(mesh?.joint === null && mesh?.label === CROSS_SITE_MESH, `ornithopter: the ES pinion to FR peg wheel step is a gear mesh between two sites with no interface declared (${describe(mesh)})`);
const want = speedUp(orn, 'LINT-6015', 'FR-ORN-ROUE-001');
check(mesh?.stage?.includes(`LINT-6015 ${orn.get('LINT-6015').teeth} teeth, m 12 mm`) && mesh?.stage?.includes(`FR-ORN-ROUE-001 ${orn.get('FR-ORN-ROUE-001').teeth} teeth, m 12.7 mm`),
  `ornithopter: the stage names both gears with their teeth and modules (${describe(mesh)})`);
check(figureOf(mesh?.stage) === want, `ornithopter: the stage's ratio reads ×${want}, output turns per input turn, as the card writes it (${describe(mesh)})`);
const cardFigure = figureOf(await page.locator('.path-ratio-figure').textContent());
check(cardFigure === want, `ornithopter: the ratio card reads ×${want} (×${cardFigure})`);
check(/output turns per input turn/i.test(await page.locator('.path-ratio .eyebrow').textContent()), 'ornithopter: the card states its convention, output turns per input turn');
await page.screenshot({ path: `${shots}ornithopter-crank-flow.png` });

// The wind turbine's gearbox: every mesh inside the German site keeps the words of a step inside one site's assembly.
await page.selectOption('.product select.profile-select', 'wind-turbine');
await flow('#/paths/flow/ES-3701/mechanical/down', 'D-37031');
await page.waitForSelector('.path-ratio', { timeout: 60000 });
all = await links();
const planetary = linkOf(all, 'D-37020', 'D-37023');
check(planetary?.joint === null && planetary?.label === SAME_SITE && /against fixed ring/.test(planetary?.stage ?? ''),
  `wind turbine: the planetary stage, DE planet carrier to DE sun against the fixed ring, is a step inside one site's assembly (${describe(planetary)})`);
const highSpeed = linkOf(all, 'D-37028', 'D-37031');
check(highSpeed?.joint === null && highSpeed?.label === SAME_SITE && figureOf(highSpeed?.stage) !== null,
  `wind turbine: the high-speed stage inside the DE gearbox keeps the words of one site's assembly and writes its ratio as ×figure (${describe(highSpeed)})`);
const inside = all.filter((l) => l.joint === null);
check(inside.length > 0 && inside.every((l) => l.label === SAME_SITE), `wind turbine: all ${inside.length} steps of the drive train without an interface are inside one site's assembly`);
await page.screenshot({ path: `${shots}wind-turbine-hub-flow.png` });

// The yaw drive: the DE pinion turns the FR slewing ring, two sites, no interface and no mesh recorded.
await flow('#/paths/flow/D-37049/mechanical/down', 'FR3711');
all = await links();
const yaw = linkOf(all, 'D-37048', 'FR3711');
check(yaw?.joint === null && yaw?.label === CROSS_SITE && yaw?.stage === null,
  `wind turbine: the DE yaw pinion to FR slewing ring step, two sites and no mesh recorded, declares no interface (${describe(yaw)})`);
const yawInside = linkOf(all, 'D-37050', 'D-37048');
check(yawInside?.joint === null && yawInside?.label === SAME_SITE, `wind turbine: the yaw drive to its pinion, both DE, is inside one site's assembly (${describe(yawInside)})`);
await page.screenshot({ path: `${shots}wind-turbine-yaw-flow.png` });

await browser.close();
console.log(failures.length ? `check-paths: ${failures.length} FAILED` : 'check-paths: all checks passed');
process.exit(failures.length ? 1 : 0);
