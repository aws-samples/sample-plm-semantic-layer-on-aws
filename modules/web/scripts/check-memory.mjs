// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0
// Run: VITE_FIXTURES=1 npx vite --port 5179 --strictPort   then   BASE_URL=http://localhost:5179 node scripts/check-memory.mjs

// Headless memory check of the viewer. Loads the default product and samples, every 2 s until the scene settles, the
// JavaScript heap, the resident memory of the renderer and GPU processes, the decoding workers alive and the STEP files
// requested. Then switches product ROUNDS times over PRODUCTS and takes the ornithopter's spring return and back
// OPTION_SWITCHES times; after each step it reads the heap, the geometries the renderer holds and the instances drawn.
// The same product must come back with the same geometries and instances and a heap within HEAP_MARGIN_MB of its
// first visit, with no more decoding workers alive than the shared pool's four. Last, with the decoding worker's
// script broken so that every worker dies, the default product must still settle, naming the files that did not load.
// The heap is read after a full collection the page runs itself (gc(), exposed by --js-flags): a collection forced
// over the DevTools protocol (HeapProfiler.collectGarbage) crashes Chromium now and then while the decoding workers
// live. Prints one line per sample and step; exits 1 when a margin is broken. GPU=1 runs Chromium on the machine's GPU
// instead of SwiftShader. The scene is read through window.viewerProbe (development builds only; a production build
// reports the heap, the processes and the workers). PLAYWRIGHT may point at a playwright install's index.mjs.
import { execFileSync } from 'node:child_process';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const pw = await import(process.env.PLAYWRIGHT ?? 'playwright');
const { chromium } = pw.chromium ? pw : pw.default;
const base = process.env.BASE_URL ?? 'http://localhost:5179';
const PRODUCTS = (process.env.PRODUCTS ?? 'ornithopter,wind-turbine,difference-engine,rover').split(',');
const ROUNDS = Number(process.env.ROUNDS ?? 3);
const OPTION_SWITCHES = Number(process.env.OPTION_SWITCHES ?? 6);
const HEAP_MARGIN_MB = Number(process.env.HEAP_MARGIN_MB ?? 25);
const gpu = process.env.GPU === '1';

const failures = [];
const check = (ok, what) => {
  if (!ok) failures.push(what);
  console.log(`${ok ? 'ok  ' : 'FAIL'} ${what}`);
};
const mb = (bytes) => Math.round(bytes / 1048576);

const profile = mkdtempSync(join(tmpdir(), 'check-memory-'));
const context = await chromium.launchPersistentContext(profile, {
  viewport: { width: 1280, height: 800 },
  args: ['--enable-precise-memory-info', '--js-flags=--expose-gc', ...(gpu ? ['--use-angle=metal', '--enable-gpu', '--ignore-gpu-blocklist'] : ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'])],
});
const page = context.pages()[0] ?? await context.newPage();
page.on('pageerror', (e) => console.log(`page error: ${e.message}`));
page.on('crash', () => check(false, 'the page does not crash'));
// The Workers the page has started and not terminated.
await page.addInitScript(() => {
  const live = new Set();
  Object.assign(window, { liveWorkers: live });
  const Native = window.Worker;
  window.Worker = class extends Native {
    constructor(...args) {
      super(...args);
      live.add(this);
    }
    terminate() {
      live.delete(this);
      super.terminate();
    }
  };
});
let steps = 0;
page.on('request', (r) => { if (/\.stp(\?|$)/.test(r.url())) steps++; });
const cdp = await context.newCDPSession(page);
await cdp.send('Performance.enable');

/** Resident memory in MB of the browser's processes by type (renderer, gpu-process, ...), from ps. */
function processes() {
  const rows = execFileSync('ps', ['-A', '-o', 'pid=,ppid=,rss=,command='], { encoding: 'utf8' }).split('\n').map((l) => l.trim().match(/^(\d+)\s+(\d+)\s+(\d+)\s+(.*)$/)).filter(Boolean)
    .map(([, pid, ppid, rss, command]) => ({ pid, ppid, rss: Number(rss) * 1024, command }));
  const root = rows.find((r) => r.command.includes(profile) && !r.command.includes('--type='));
  const tree = new Set(root ? [root.pid] : []);
  for (let grew = true; grew;) {
    grew = false;
    for (const r of rows) if (!tree.has(r.pid) && tree.has(r.ppid)) { tree.add(r.pid); grew = true; }
  }
  const out = {};
  for (const r of rows.filter((x) => tree.has(x.pid))) {
    const type = /--type=([a-z-]+)/.exec(r.command)?.[1] ?? 'browser';
    const key = type === 'utility' ? 'utility' : type;
    out[key] = (out[key] ?? 0) + mb(r.rss);
  }
  return out;
}

const hasProbe = () => page.evaluate(() => 'viewerProbe' in window);

/** The heap, the processes, the workers alive, and for a development build the scene's figures. */
async function sample() {
  await page.evaluate(() => window.gc());
  const { metrics } = await cdp.send('Performance.getMetrics');
  const heap = mb(metrics.find((m) => m.name === 'JSHeapUsedSize').value);
  const workers = await page.evaluate(() => window.liveWorkers.size);
  const scene = (await hasProbe()) ? await page.evaluate(() => {
    const parts = window.viewerProbe.parts();
    return { ...window.viewerProbe.memory(), parts: Object.keys(parts).length, instances: Object.values(parts).reduce((n, p) => n + p.count, 0) };
  }) : null;
  return { heap, workers, ...processes(), scene, steps };
}

const line = (label, s) => console.log(`${label.padEnd(34)} heap ${String(s.heap).padStart(4)} MB  renderer ${String(s.renderer ?? '-').padStart(5)} MB  gpu ${String(s['gpu-process'] ?? '-').padStart(5)} MB  workers ${s.workers}  STEP requests ${s.steps}${s.scene ? `  geometries ${s.scene.geometries}  parts ${s.scene.parts}  instances ${s.scene.instances}` : ''}`);

const loading = () => page.evaluate(() => Boolean(document.querySelector('.viewer-status:not(.is-error)')));
/** Waits until no STEP file is loading and the parts drawn stop changing. */
async function settle(timeout = 240000) {
  const t0 = Date.now();
  let last = '';
  let still = 0;
  while (Date.now() - t0 < timeout) {
    await page.waitForTimeout(1000);
    const drawn = await page.evaluate(() => document.querySelector('.viewer-host')?.dataset.parts ?? '');
    still = drawn && drawn === last && !(await loading()) ? still + 1 : 0;
    last = drawn;
    if (still >= 3) return Date.now() - t0;
  }
  return null;
}

// The default product, sampled while it loads.
await page.goto(`${base}/?memory=${Date.now()}#/check`);
const t0 = Date.now();
let settledAt = null;
while (Date.now() - t0 < 150000) {
  await page.waitForTimeout(2000);
  const s = await sample();
  line(`default product, ${Math.round((Date.now() - t0) / 1000)} s`, s);
  const drawn = await page.evaluate(() => document.querySelector('.viewer-host')?.dataset.parts ?? '');
  if (drawn && !(await loading())) { settledAt = Date.now() - t0; break; }
}
check(settledAt !== null, `the default product settles (${settledAt === null ? 'not within 150 s' : `${Math.round(settledAt / 1000)} s`})`);
const loadedSteps = steps;
const first = await sample();
line('default product, settled', first);
const POOL = 4;
check(first.workers <= POOL, `at most the shared pool's ${POOL} decoding workers alive once the default product has settled (${first.workers})`);

// Product switches.
const visits = new Map();
for (let round = 1; round <= ROUNDS; round++) {
  for (const product of PRODUCTS) {
    const before = steps;
    await page.selectOption('.product select', product);
    const took = await settle();
    const s = await sample();
    line(`round ${round} ${product}${took === null ? ' (not settled)' : ''}`, s);
    if (!visits.has(product)) visits.set(product, { ...s, requested: s.steps - before });
    else {
      const v = visits.get(product);
      check(s.heap - v.heap <= HEAP_MARGIN_MB, `round ${round} ${product}: heap ${s.heap} MB against ${v.heap} MB on the first visit, within ${HEAP_MARGIN_MB} MB`);
      if (s.scene && v.scene) {
        check(s.scene.geometries === v.scene.geometries && s.scene.instances === v.scene.instances,
          `round ${round} ${product}: ${s.scene.geometries} geometries and ${s.scene.instances} instances, as on the first visit`);
      }
    }
    check(s.workers <= POOL, `round ${round} ${product}: at most the shared pool's ${POOL} decoding workers alive (${s.workers})`);
  }
}
const requested = [...visits].map(([p, v]) => `${p} ${v.requested}`).join(', ');
console.log(`STEP files requested on the first visit of each product: ${requested}; default load ${loadedSteps}`);

// The ornithopter's spring return taken and put back.
if (OPTION_SWITCHES > 0) {
  await page.selectOption('.product select', 'ornithopter');
  await settle();
  const atBase = await sample();
  line('ornithopter at its defaults', atBase);
  for (let i = 1; i <= OPTION_SWITCHES; i++) {
    const select = page.locator('.variant-group', { hasText: 'Wing return' }).locator('select');
    await select.selectOption(i % 2 ? 'spring-return' : 'stirrup-return');
    await settle();
  }
  if (OPTION_SWITCHES % 2) {
    await page.locator('.variant-group', { hasText: 'Wing return' }).locator('select').selectOption('stirrup-return');
    await settle();
  }
  const back = await sample();
  line(`ornithopter after ${OPTION_SWITCHES} option switches`, back);
  check(back.heap - atBase.heap <= HEAP_MARGIN_MB, `ornithopter: heap ${back.heap} MB after the option switches against ${atBase.heap} MB, within ${HEAP_MARGIN_MB} MB`);
  if (back.scene && atBase.scene) {
    check(back.scene.geometries === atBase.scene.geometries && back.scene.instances === atBase.scene.instances,
      `ornithopter: ${back.scene.geometries} geometries and ${back.scene.instances} instances after the option switches, as before`);
  }
}

// Workers that die fail their files: the load settles and the viewer names them.
const broken = await context.newPage();
await broken.route(/stepWorker/, (route) => route.fulfill({ contentType: 'text/javascript', body: 'throw new Error("decoder unavailable");' }));
await broken.goto(`${base}/?memory=${Date.now()}#/check`);
const named = await broken.waitForFunction(() => {
  const failed = document.querySelector('.viewer-status.is-error');
  return failed && !document.querySelector('.viewer-status:not(.is-error)') ? failed.children.length : 0;
}, null, { timeout: 60000 }).then((h) => h.jsonValue()).catch(() => 0);
check(named > 0, `with every decoding worker dead, the load settles and names the ${named} files that did not load`);

await context.close();
rmSync(profile, { recursive: true, force: true });
console.log(failures.length ? `check-memory: ${failures.length} FAILED` : 'check-memory: all checks passed');
process.exit(failures.length ? 1 : 0);
