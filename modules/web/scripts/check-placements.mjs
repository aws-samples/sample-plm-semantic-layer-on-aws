// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0
// Run: VITE_FIXTURES=1 npx vite --port 5197 --strictPort   then   BASE_URL=http://localhost:5197 node scripts/check-placements.mjs

// Headless check of the placements in fixture mode. Every drawn part shows as many instances as
// data/products/<product>.json gives it occurrences (the lines' placements multiplied down from the
// site kits); the rover's wheel stands at three stations on both sides and the wind turbine's blade
// parts sit 120 degrees apart about the rotor axis; a click on the projection of one instance selects
// the part and names the occurrence; isolating the wheel hides every other part's instances; the
// legend counts part numbers and occurrences. Then it orbits the difference engine at 1280 x 800 for
// about 4 s and prints the frames per second. Screenshots go to SHOTS (default ./screenshots/placements).
// The scene is read through window.viewerProbe, which the scene exposes in development only.
// PLAYWRIGHT may point at a playwright install's index.mjs when it is not installed here; GPU=1
// launches Chromium on the machine's GPU instead of SwiftShader for the frame-rate measurement.
import { mkdir, readFile } from 'node:fs/promises';

const pw = await import(process.env.PLAYWRIGHT ?? 'playwright');
const { chromium } = pw.chromium ? pw : pw.default;
const base = process.env.BASE_URL ?? 'http://localhost:5197';
const shots = process.env.SHOTS ?? new URL('../screenshots/placements/', import.meta.url).pathname;
await mkdir(shots, { recursive: true });

const WHEEL = 'FR3801';
const BLADE_ROOT = 'FR3771';
const TIP_CAP = 'FR3717';

/** Occurrences of every geometric part of a product counted down the lines from `starts`, the site kits by default. */
async function expected(product) {
  const d = JSON.parse(await readFile(new URL(`../../../data/products/${product}.json`, import.meta.url), 'utf8'));
  const kids = new Map();
  for (const l of d.extended.bomLines) kids.set(l.parent, [...(kids.get(l.parent) ?? []), l]);
  const geometric = new Set(d.parts.filter((p) => (p.extended?.type ?? 'PART') === 'PART').map((p) => p.id));
  const countFrom = (starts) => {
    const counts = new Map();
    const walk = (id, k) => {
      for (const l of kids.get(id) ?? []) {
        const n = k * (l.placements?.length ?? 1);
        if (geometric.has(l.child)) counts.set(l.child, (counts.get(l.child) ?? 0) + n);
        walk(l.child, n);
      }
    };
    starts.forEach((s) => walk(s, 1));
    return counts;
  };
  return { ids: [...geometric], counts: countFrom(d.extended.assemblies.filter((x) => x.kind === 'SITE_KIT').map((x) => x.id)), countFrom };
}

const gpu = process.env.GPU === '1';
const browser = await chromium.launch({ args: gpu ? ['--use-angle=metal', '--enable-gpu', '--ignore-gpu-blocklist'] : ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
const page = await browser.newPage({ viewport: { width: 1280, height: 800 } });
page.on('pageerror', (e) => console.error('pageerror:', e.message));

const failures = [];
const check = (ok, what) => {
  if (!ok) failures.push(what);
  console.log(`${ok ? 'ok  ' : 'FAIL'} ${what}`);
};
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

/** Opens a product and waits until every STEP file is drawn at its placements. */
async function open(product) {
  const want = await expected(product);
  await page.selectOption('.product select', product);
  await page.waitForFunction((ids) => {
    const drawn = document.querySelector('.viewer-host')?.dataset.parts?.split(' ') ?? [];
    return drawn.length > 1 && drawn.every((id) => ids.includes(id)) && !document.querySelector('.viewer-status:not(.is-error)') && document.querySelector('.legend-item.is-total');
  }, want.ids, { timeout: 240000 });
  // The camera tweens for about half a second after a load.
  await page.waitForTimeout(1200);
  const parts = await probe.parts();
  const wrong = Object.entries(parts).filter(([id, p]) => p.count !== (want.counts.get(id) ?? 1));
  const total = Object.values(parts).reduce((n, p) => n + p.count, 0);
  check(wrong.length === 0, `${product}: ${Object.keys(parts).length} parts drawn as ${total} instances, each as many as the file gives it occurrences${wrong.length ? `; wrong: ${wrong.slice(0, 5).map(([id, p]) => `${id} ${p.count}/${want.counts.get(id)}`).join(', ')}` : ''}`);
  const legend = await page.$eval('.legend-item.is-total', (el) => el.textContent);
  const m = /(\d+) parts?, (\d+) occurrences?/.exec(legend ?? '');
  check(m !== null && Number(m[2]) >= total, `${product}: the legend reads "${legend}"`);
  return { parts, want };
}

await page.goto(`${base}/#/check`);
await page.waitForSelector('.product select');

// The rover: one wheel part, six instances, three stations along x on each side of the centreline.
const rover = await open('rover');
check(rover.parts[WHEEL]?.count === 6, `rover: the wheel ${WHEEL} draws ${rover.parts[WHEEL]?.count} instances, 6 expected`);
const wheels = await probe.centres(WHEEL);
const stations = new Set(wheels.map(([x]) => Math.round(x)));
const paired = [...stations].every((x) => wheels.filter((w) => Math.round(w[0]) === x).map((w) => Math.sign(w[1])).sort().join() === '-1,1');
check(stations.size === 3 && paired, `rover: wheels at x = ${[...stations].sort((a, b) => a - b).join(', ')} mm, one on each side at each station`);
await page.screenshot({ path: `${shots}rover-six-wheels.png` });

// Picking: a click on the third wheel's projection selects the wheel and names the occurrence.
let picked = null;
for (const k of [3, 2, 4, 5, 6, 1]) {
  const at = await probe.screenPoint(WHEEL, k);
  if (!at) continue;
  await page.mouse.click(at.x, at.y);
  picked = k;
  break;
}
await page.waitForSelector('.view-selected', { timeout: 5000 }).catch(() => null);
const chip = await page.$eval('.view-selected', (el) => el.textContent).catch(() => '');
check(picked !== null && chip.includes(WHEEL) && chip.includes(`occurrence ${picked} of 6`), `rover: a click on wheel instance ${picked} selects it: "${chip}"`);

// Isolation hides every other part's instances and keeps the six wheels.
await probe.isolate([WHEEL]);
await page.waitForTimeout(1200);
const isolated = await probe.parts();
const others = Object.entries(isolated).filter(([id, p]) => id !== WHEEL && p.shown);
check(isolated[WHEEL]?.shown && others.length === 0, `rover: isolating ${WHEEL} shows its 6 instances and hides the ${Object.keys(isolated).length - 1} other parts`);
const again = await probe.screenPoint(WHEEL, 5);
check(again !== null, 'rover: the fifth wheel is pickable in the isolated view');
await page.screenshot({ path: `${shots}rover-wheels-isolated.png` });
await probe.clear();
await page.click('.view-selected-clear').catch(() => null);

// The wind turbine: three blades, each blade part three times, 120 degrees apart about the rotor axis.
const turbine = await open('wind-turbine');
check(turbine.parts[TIP_CAP]?.count === 3, `wind turbine: the blade tip cap ${TIP_CAP} draws ${turbine.parts[TIP_CAP]?.count} instances, 3 expected`);
const blade = [...turbine.want.countFrom([BLADE_ROOT])].filter(([id]) => turbine.parts[id]);
check(blade.length > 0 && blade.every(([id, n]) => turbine.parts[id].count === 3 * n), `wind turbine: the ${blade.length} parts of one blade ${BLADE_ROOT} are each drawn three times as often as the blade holds them`);
const caps = await probe.centres(TIP_CAP);
const hub = caps.reduce((c, p) => [c[0] + p[0] / 3, c[1] + p[1] / 3, c[2] + p[2] / 3], [0, 0, 0]);
const angles = caps.map((p) => (Math.atan2(p[2] - hub[2], p[1] - hub[1]) * 180) / Math.PI).sort((a, b) => a - b);
const gaps = [angles[1] - angles[0], angles[2] - angles[1], 360 + angles[0] - angles[2]];
check(gaps.every((g) => Math.abs(g - 120) < 0.5), `wind turbine: tip caps ${gaps.map((g) => g.toFixed(2)).join(', ')} degrees apart about the rotor axis`);
await page.screenshot({ path: `${shots}wind-turbine-three-blades.png` });

// The difference engine: thousands of occurrences, orbited for about 4 s.
const engine = await open('difference-engine');
const occurrences = Object.values(engine.parts).reduce((n, p) => n + p.count, 0);
await page.screenshot({ path: `${shots}difference-engine.png` });
const renderer = await page.evaluate(() => {
  const gl = document.querySelector('.viewer-canvas').getContext('webgl2');
  const info = gl?.getExtension('WEBGL_debug_renderer_info');
  return info ? gl.getParameter(info.UNMASKED_RENDERER_WEBGL) : 'unknown';
});
const box = await page.$eval('.viewer-host', (el) => { const r = el.getBoundingClientRect(); return { x: r.left, y: r.top, w: r.width, h: r.height }; });
const cx = box.x + box.w / 2;
const cy = box.y + box.h / 2;
await page.evaluate(() => {
  window.rafFrames = 0;
  const tick = () => { window.rafFrames++; window.rafId = requestAnimationFrame(tick); };
  window.rafId = requestAnimationFrame(tick);
});
const before = await probe.frames();
await page.mouse.move(cx, cy);
await page.mouse.down();
const t0 = Date.now();
let i = 0;
while (Date.now() - t0 < 4000) {
  i++;
  await page.mouse.move(cx + 300 * Math.sin(i / 20), cy + 60 * Math.sin(i / 47));
}
await page.mouse.up();
const seconds = (Date.now() - t0) / 1000;
const rendered = (await probe.frames()) - before;
const raf = await page.evaluate(() => { cancelAnimationFrame(window.rafId); return window.rafFrames; });
console.log(`difference engine: ${Object.keys(engine.parts).length} parts, ${occurrences} occurrences, 1280 x 800, renderer ${renderer}`);
console.log(`difference engine: ${(rendered / seconds).toFixed(1)} rendered frames per second while orbiting (${rendered} frames, ${raf} animation frames, ${i} pointer moves in ${seconds.toFixed(1)} s)`);
check(rendered > 0, 'difference engine: the view renders while orbiting');

await browser.close();
console.log(failures.length ? `check-placements: ${failures.length} FAILED` : 'check-placements: all checks passed');
process.exit(failures.length ? 1 : 0);
