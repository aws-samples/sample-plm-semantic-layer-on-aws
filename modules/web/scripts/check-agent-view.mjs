// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

// Headless check of the agent driving the viewer, from a running dev server in fixture mode:
//   VITE_FIXTURES=1 npx vite --port 5173   then   node scripts/check-agent-view.mjs
// It measures what a viewer sees: the Ask panel's tools row shows the input a turn processed with its cache read and
// write, cached share and price; the sites whose colour the 3D view paints (each PLM has its own
// hue) narrow to the sites of the isolated parts when the agent isolates them, and all come back
// with the normal view; at 1280 px with the Ask panel open the agent's note is at least 240 px wide, clear of the
// detail view, and a long caption keeps two lines with the full caption as its title; ids written without their hyphens
// or with one too many isolate the part they mean, and an id no part has is left out; a click on a
// drawn part shows the selection chip, and the next question carries that part (the fixture agent
// answers "what is this part" from forwardedProps.selection only), as does a click on a row of the
// bill-of-materials tree, which the viewer then shows selected; the agent's open_subtree for
// the wind turbine gearbox switches the product and opens the subtree of D-37073; the evidence Turtle reader reads a
// 100,000-character literal and tokenizes every fixture interface's evidence as the one-regex tokenizer does; interface
// ids list in numeric order, and the wind turbine's grid keeps eight whole three-digit tiles a row at 1280 px.
// SHOTS names a directory for the screenshots of the note and the grid.
// PLAYWRIGHT may point at a playwright package directory when it is not installed here.
const pw = await import(process.env.PLAYWRIGHT ?? 'playwright');
const { chromium } = pw.chromium ? pw : pw.default;
const base = process.env.BASE_URL ?? 'http://localhost:5173';

const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
page.on('pageerror', (e) => console.error('pageerror:', e.message));

let failures = 0;
const check = (ok, label, extra = '') => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${label}${extra ? `  (${extra})` : ''}`);
  if (!ok) failures++;
};
const settled = async () => {
  await page.waitForFunction(() => !document.querySelector('.viewer-status:not(.is-error)') && !document.querySelector('.loading'), null, { timeout: 60000 });
  // The camera tweens for about half a second after a command.
  await page.waitForTimeout(1500);
};

/** Hue ranges, in degrees, of the four sites' paint under the scene's lights. */
const SITE_HUE = { FR: [240, 262], DE: [30, 48], UK: [200, 222], ES: [165, 185] };

/** Per site, the pixels of the 3D view painted in its hue; the painted pixel nearest the centre. */
const drawn = async () => {
  const png = (await page.locator('.viewer-host').screenshot()).toString('base64');
  return page.evaluate(async ([data, hues]) => {
    const img = new Image();
    img.src = `data:image/png;base64,${data}`;
    await img.decode();
    const c = document.createElement('canvas');
    c.width = img.width;
    c.height = img.height;
    const g = c.getContext('2d');
    g.drawImage(img, 0, 0);
    const px = g.getImageData(0, 0, c.width, c.height).data;
    const sites = Object.fromEntries(Object.keys(hues).map((k) => [k, 0]));
    let best = null;
    for (let y = 0; y < c.height; y += 2) {
      for (let x = 0; x < c.width; x += 2) {
        const i = (y * c.width + x) * 4;
        const [r, g, b] = [px[i], px[i + 1], px[i + 2]];
        const hi = Math.max(r, g, b);
        const lo = Math.min(r, g, b);
        if (hi - lo < 40) continue;
        const d = Math.hypot(x - c.width / 2, y - c.height / 2);
        if (!best || d < best.d) best = { x, y, d };
        const h = hi === r ? ((g - b) / (hi - lo)) * 60 : hi === g ? (2 + (b - r) / (hi - lo)) * 60 : (4 + (r - g) / (hi - lo)) * 60;
        const hue = (h + 360) % 360;
        for (const [k, [a, z]] of Object.entries(hues)) if (hue >= a && hue <= z) sites[k]++;
      }
    }
    return { sites, shown: Object.keys(sites).filter((k) => sites[k] > 150).sort().join(','), at: best };
  }, [png, SITE_HUE]);
};
const ask = async (q) => {
  const turns = await page.locator('.ask-turn').count();
  await page.fill('.ask-input', q);
  await page.press('.ask-input', 'Enter');
  await page.waitForFunction((k) => document.querySelectorAll('.ask-turn.is-done').length > k, turns, { timeout: 30000 });
  return page.locator('.ask-turn').last();
};

await page.goto(`${base}/#/check`);
await page.waitForSelector('.viewer-canvas');
await settled();
const whole = await drawn();
check(whole.shown === 'DE,ES,FR,UK', 'the ornithopter is drawn in the four sites\' colours', JSON.stringify(whole.sites));

await page.click('.topbar button:has-text("Ask")');
await page.waitForSelector('.ask');
const impact = await ask('What would a change to plug FR-ORN-EMPL-R-001-J01 impact?');
check(await impact.locator('.ask-view:has-text("Parts isolated")').count() === 1, 'impact_of_change is followed by an isolate_parts block');
// The tools row shows the input the turn processed: uncached plus read from and written to the prompt cache, the cached
// share, and the estimated price; the fixture turn reports cache fields as the agent's prompt cache does.
const cost = (await impact.locator('.ask-cost').textContent()) ?? '';
const m = cost.replace(/,(?=\d{3})/g, '').match(/(\d+) input tokens \((\d+) uncached, (\d+) cache read, (\d+) cache write; (\d+)% cached\) \/ (\d+) output tokens, about \$([\d.]+) at list price/);
const [total, uncached, read, write, share] = m ? m.slice(1, 6).map(Number) : [];
check(Boolean(m) && total === uncached + read + write && read > 0 && write > 0 && share === Math.round((read / total) * 100) && Number(m[7]) > 0,
  'the tools row shows the input processed with its cache read, cache write, cached share and price', cost);
await settled();
const note = (await page.locator('.view-note').textContent()) ?? '';
check(/isolated/.test(note), 'the viewer names the isolated view', note);

// At 1280 px with the Ask panel open the note keeps a readable width; a long caption keeps two lines, its full text the title.
await page.setViewportSize({ width: 1280, height: 800 });
await page.waitForTimeout(300);
const noteBox = () => page.evaluate(() => {
  const n = document.querySelector('.view-note');
  const text = n.querySelector('.view-note-text');
  const lines = Math.round(text.clientHeight / parseFloat(getComputedStyle(text).lineHeight));
  const a = n.getBoundingClientRect();
  const l = document.querySelector('.loupe')?.getBoundingClientRect();
  const clear = !l || a.right <= l.left || a.left >= l.right || a.bottom <= l.top - 24 || a.top >= l.bottom;
  return { width: Math.round(a.width), lines, clipped: text.scrollHeight > text.clientHeight, title: text.title, text: text.textContent, loupe: !!l, clear };
});
const narrow = await noteBox();
check(narrow.width >= 240 && narrow.lines <= 2, 'with the Ask panel open at 1280 px the note is at least 240 px wide and at most two lines', JSON.stringify(narrow));
check(narrow.loupe && narrow.clear, 'the note sits clear of the detail view of the interface the agent selected', JSON.stringify({ loupe: narrow.loupe, clear: narrow.clear }));
check(narrow.title === narrow.text.replace(/\s+/g, ' ').trim(), 'the note\'s title is its full caption', narrow.title);
await page.evaluate(() => {
  document.querySelector('.view-note-text').firstChild.textContent = 'Cabin seats, bins, service units, galleys and lavatories of the forward cabin and the economy cabin, with their placards and the monuments between them '.repeat(2);
});
const long = await noteBox();
check(long.lines === 2 && long.clipped && long.clear, 'a long caption is cut to two lines', JSON.stringify({ width: long.width, lines: long.lines, clipped: long.clipped, clear: long.clear }));
if (process.env.SHOTS) await page.screenshot({ path: `${process.env.SHOTS}/agent-note-1280-ask-open.png` });
await page.setViewportSize({ width: 1440, height: 900 });
const isolated = await drawn();
// WURZ-R-61080 (DE) is isolated, FR-ORN-EMPL-R-001 (FR) is its faded context: the UK and ES parts are hidden.
check(isolated.sites.DE > 150 && isolated.sites.UK < 100 && isolated.sites.ES < 100, 'isolating draws the DE part and hides the UK and ES parts', JSON.stringify(isolated.sites));

await page.click('.view-note-clear');
await settled();
const normal = await drawn();
check((await page.locator('.view-note').count()) === 0 && normal.shown === 'DE,ES,FR,UK', 'the normal view draws the whole product again', JSON.stringify(normal.sites));

// An id written without its hyphens, or with one too many, still names the part it means when one loaded part has that
// form: WURZR61080 and wurz-r-6108-0 are WURZ-R-61080 (DE); an id no part has is left out.
await ask('Isolate WURZR61080 wurz-r-6108-0 NOPE-0000 as written');
await settled();
const isolatedNote = (await page.locator('.view-note').textContent()) ?? '';
const shownParts = Object.entries(await page.evaluate(() => window.viewerProbe.parts())).filter(([, p]) => p.shown).map(([id]) => id);
check(shownParts.length === 1 && shownParts[0] === 'WURZ-R-61080', 'mis-hyphenated ids isolate the part they mean, and only it', shownParts.join(','));
check(/\b1 part isolated/i.test(isolatedNote), 'the two spellings are one part and the unknown id is left out', isolatedNote);
await page.click('.view-note-clear');
await settled();

const box = await page.locator('.viewer-host').boundingBox();
await page.mouse.click(box.x + normal.at.x, box.y + normal.at.y);
await page.waitForSelector('.view-selected', { timeout: 5000 }).catch(() => null);
const chipId = (await page.locator('.view-selected .mono').textContent().catch(() => null)) ?? '';
check(chipId.length > 0, 'a click on a part selects it', chipId);

const what = await ask('What is this part?');
const prose = (await what.locator('.ask-prose').allTextContents()).join(' ');
check(chipId.length > 0 && prose.includes(chipId), 'the next question carries the selected part', prose.slice(0, 160));
check(await what.locator('.ask-view:has-text("Parts outlined")').count() === 1, 'the answer outlines the part it names');

// A click on a row of the bill-of-materials tree selects the part as a click in the viewer does.
await page.evaluate(() => { location.hash = '#/bom'; });
const row = page.locator('.bom-row:has(.bom-id:text-is("FR-ORN-LAT-L-001"))');
await row.waitFor({ timeout: 20000 });
await row.locator('.bom-id').click();
check((await row.getAttribute('aria-selected')) === 'true', 'a click on a bill-of-materials row selects its part');
const fromTree = await ask('What is this part?');
const treeProse = (await fromTree.locator('.ask-prose').allTextContents()).join(' ');
check(treeProse.includes('FR-ORN-LAT-L-001'), 'the next question carries the part selected in the tree', treeProse.slice(0, 160));
await page.evaluate(() => { location.hash = '#/check'; });
await page.waitForSelector('.view-selected', { timeout: 20000 }).catch(() => null);
const carried = (await page.locator('.view-selected .mono').textContent().catch(() => null)) ?? '';
check(carried === 'FR-ORN-LAT-L-001', 'the viewer shows the part selected in the tree', carried);

await ask('Show me the gearbox of the wind turbine');
await page.waitForFunction(() => location.hash.includes('root=D-37073'), null, { timeout: 10000 }).catch(() => null);
const hash = await page.evaluate(() => location.hash);
check(hash.includes('root=D-37073'), 'open_subtree opens the subtree of D-37073', hash);
check((await page.locator('.subtree-bar:has-text("D-37073")').count()) === 1, 'the breadcrumb names the gearbox');
const product = await page.locator('.product select').inputValue();
check(product === 'wind-turbine', 'the product switched to the wind turbine', product);
check((await page.locator('.view-selected').count()) === 0, 'the part selection belongs to the product it was made in');
await settled();
const gearbox = await drawn();
check(gearbox.sites.DE > 150, 'the gearbox subtree is drawn in the DE colour', JSON.stringify(gearbox.sites));
if (process.env.SHOT) await page.screenshot({ path: process.env.SHOT });

// Interface ids list in reading order: the service's whole-catalogue answer as the fixtures mirror it, and the wind
// turbine's grid at 1280 px, eight tiles a row, each three-digit label whole.
const catalogue = await page.evaluate(async () => {
  const { respond } = await import('/src/dev/fixtures.ts');
  return (await respond('/query/interfaces', 'programme-cleared')).interfaces.map((i) => i.id);
});
const numbers = catalogue.map((id) => Number(id.replace(/\D+/g, '')));
check(numbers.every((n, i) => i === 0 || numbers[i - 1] <= n) && catalogue[catalogue.lastIndexOf('IF-99') + 1] === 'IF-100',
  'the whole catalogue lists IF-99 before IF-100', catalogue.slice(catalogue.lastIndexOf('IF-99') - 1, catalogue.lastIndexOf('IF-99') + 3).join(' '));
await page.setViewportSize({ width: 1280, height: 800 });
await page.goto(`${base}/#/check`);
await page.selectOption('.product select', 'wind-turbine');
await page.waitForFunction(() => document.querySelector('.cells .cell')?.textContent === '100', null, { timeout: 30000 }).catch(() => null);
const grid = await page.evaluate(() => {
  const cells = [...document.querySelectorAll('.cells .cell')];
  const top = (c) => Math.round(c.getBoundingClientRect().top);
  return { labels: cells.map((c) => c.textContent), perRow: cells.filter((c) => top(c) === top(cells[0])).length, cut: cells.filter((c) => c.scrollWidth > c.clientWidth).length };
});
check(grid.labels.length > 0 && grid.labels.every((l, i) => i === 0 || Number(grid.labels[i - 1]) < Number(l)), 'the wind turbine\'s grid runs in numeric order',
  `${grid.labels.slice(0, 3).join(' ')} ... ${grid.labels.slice(-2).join(' ')}`);
check(grid.perRow === 8 && grid.cut === 0, 'the grid keeps eight tiles a row at 1280 px, every three-digit label whole', JSON.stringify({ perRow: grid.perRow, cut: grid.cut }));
if (process.env.SHOTS) await page.screenshot({ path: `${process.env.SHOTS}/interface-grid-wind-turbine-1280.png` });

// The evidence Turtle reader: a 100,000-character multi-line literal reads without throwing, and on every fixture
// interface's evidence (each arm, the merged graph, the shapes) its tokens are those of the one-regex tokenizer.
const reader = await page.evaluate(async () => {
  const { parseTriples, tokens } = await import('/src/check/evidence/turtle.ts');
  const { respond } = await import('/src/dev/fixtures.ts');
  const { interfacesOf } = await import('/src/dev/fixture-products.ts');
  const { products } = await import('/src/dev/fixture-seed.ts');
  const ONE_REGEX = /(#[^\n]*)|(<[^>]*>)|("""[\s\S]*?"""|"(?:[^"\\\n]|\\.)*")(?:\^\^(?:<[^>]*>|[\w-]*:[\w-]*)|@[\w-]+)?|([\w-]*:[\w%./-]*[\w%/-])|([+-]?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)|\b(true|false)\b|\b(a)\b|([;,.[\]])/g;
  const line = '  ?part atelier:label ?name ; atelier:gearModule ?q . FILTER (?name != \\"x\\")\\n';
  const long = ['<s> <p> "', line.repeat(Math.ceil(100000 / line.length)), '" .'].join('');
  let longOk;
  try {
    const t = parseTriples(long);
    longOk = t.length === 1 && t[0].literal && t[0].o.length > 100000;
  } catch (e) {
    longOk = String(e);
  }
  const texts = [];
  for (const { key: product } of products) for (const itf of interfacesOf(product)) {
    const ev = await respond(`/query/interfaces/${encodeURIComponent(itf.id)}/evidence?product=${product}`, 'programme-cleared');
    texts.push(...ev.arms.map((a) => a.triples ?? ''), ev.merged.turtle, ...(ev.shapes ?? []).map((sh) => sh.turtle));
  }
  // A token's eight groups as one string; a group that did not take part is empty, which no group that did can be.
  const key = (x) => Array.from({ length: 8 }, (_, i) => x[i] ?? '').join('\u001f');
  const differ = texts.filter((t) => [...t.matchAll(ONE_REGEX)].map((m) => key(m.slice(1))).join('|') !== [...tokens(t)].map(key).join('|'));
  return { longOk, texts: texts.length, chars: texts.reduce((n, t) => n + t.length, 0), differ: differ.length };
});
check(reader.longOk === true, 'a 100,000-character multi-line literal reads as one triple', String(reader.longOk));
check(reader.texts > 0 && reader.differ === 0, 'the evidence Turtle of every fixture interface tokenizes as before',
  `${reader.texts} texts, ${reader.chars} characters, ${reader.differ} differ`);

// The Agent path lists every tool call in order with its own duration and counts them all: a turn that calls find_parts
// twice, then isolate_parts, shows three calls.
const named = await ask('Show the parts named wing and spar');
const hops = await named.locator('.ask-hop').evaluateAll((els) => els.map((e) => [e.querySelector('.ask-hop-name')?.textContent, e.querySelector('.ask-hop-ms')?.textContent]));
const counted = (await named.locator('.ask-path-head .quiet').textContent()) ?? '';
check(JSON.stringify(hops.map(([n]) => n)) === JSON.stringify(['find_parts', 'find_parts', 'isolate_parts']) && hops.every(([, ms]) => / ms$/.test(ms ?? ''))
  && counted === '3 tool calls', 'the Agent path lists each call of the same tool, in order, with its duration, and counts them all', `${counted}: ${JSON.stringify(hops)}`);

await browser.close();
console.log(failures ? `\n${failures} check(s) failed` : '\nall checks passed');
process.exit(failures ? 1 : 0);
