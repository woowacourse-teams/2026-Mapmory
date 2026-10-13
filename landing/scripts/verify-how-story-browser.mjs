// Runtime checks of the #how photo story that `npm test` cannot see, in the dev server (React StrictMode on, so every
// effect mounts, unmounts and mounts again). Starts its own Vite dev server on a free port and stops it at the end.
// node scripts/verify-how-story-browser.mjs <absolute path to playwright/index.mjs>
//
// What it guards (each would pass the source-level tests if it regressed):
//   - StrictMode leaves exactly one film: one stage world, six copy beats, one probe, and one each of the page
//     listeners (window scroll and resize, the reduced-motion query, document visibilitychange);
//   - mounting and destroying the engine again and again returns listeners, observers, timers, frame requests and
//     DOM nodes to where they started, and restores the skeleton;
//   - a scene is "at rest" only at its point (02 and 04 only once their inner step has started), and the stage shows
//     the pose of the beat on screen;
//   - the map's four layers are built when the lazy chunk arrives, in the film and in every static figure;
//   - the static version: five figures hidden from screen readers, each at its beat's end pose, and the item in view
//     reported at rest;
//   - the reader keeps their place when the viewport changes: film <-> static (a phone rotated) and film <-> film
//     (a tablet rotated);
//   - failures degrade quietly: a map chunk that never arrives leaves 05 its blurred preview and no stray ground
//     shadow; a picture that fails keeps its blurred preview without a broken-image icon.
// The section is read through its QA hooks (listed in src/howPhotoStoryEngine.js), not its styling.
import assert from "node:assert/strict";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { createServer } from "vite";

const root = fileURLToPath(new URL("../", import.meta.url));
if (!process.argv[2]) throw new Error("usage: node scripts/verify-how-story-browser.mjs <absolute path to playwright/index.mjs>");
const { chromium } = await import(pathToFileURL(path.resolve(process.argv[2])).href);

// The step each beat's point lands on (01: copy and 경주 tag; 02: the 경주 photos out of the heap; 04: the copies up).
const FINAL_STEP = [null, 1, 1, 0, 1, 0];
const TIMED_STEP = new Set([2, 4]);
// The pose each static figure is built at (the end state of its beat).
const STATIC_POSES = ["1.1", "2.1", "3.0", "4.1", "5.0"];
const PAGE_LISTENERS = ["MediaQueryList:change", "document:visibilitychange", "window:resize", "window:scroll"];

// Init script: records the page listeners, observers, timers and frame requests made from the section's modules (by
// their call stack), so a leak or a listener left behind by StrictMode's first mount shows up.
function instrument() {
  const mine = () => /howPhotoStoryEngine|HowPhotoStory\.jsx|howStoryAnalytics/.test(new Error().stack || "");
  const live = new Map();
  let seq = 0;
  const observers = [];
  const timers = new Map();
  const rafs = new Set();
  const describe = (target) => (target === window ? "window" : target === document ? "document" : target?.constructor?.name ?? "?");
  const add = EventTarget.prototype.addEventListener;
  const remove = EventTarget.prototype.removeEventListener;
  const captureOf = (options) => (typeof options === "boolean" ? options : Boolean(options?.capture));
  EventTarget.prototype.addEventListener = function (type, fn, options) {
    // listeners on elements the engine made (pictures, { once: true }) go with their nodes; the page's are what leak
    if (mine() && !(this instanceof Node && this !== document)) {
      const capture = captureOf(options);
      if (![...live.values()].some((e) => e.target === this && e.type === type && e.fn === fn && e.capture === capture)) {
        live.set(++seq, { target: this, type, fn, capture });
      }
    }
    return add.call(this, type, fn, options);
  };
  EventTarget.prototype.removeEventListener = function (type, fn, options) {
    const capture = captureOf(options);
    for (const [key, e] of live) if (e.target === this && e.type === type && e.fn === fn && e.capture === capture) live.delete(key);
    return remove.call(this, type, fn, options);
  };
  for (const name of ["ResizeObserver", "IntersectionObserver"]) {
    const Original = window[name];
    if (!Original) continue;
    window[name] = class extends Original {
      constructor(callback, init) {
        super(callback, init);
        if (mine()) { this.record = { name, connected: true }; observers.push(this.record); }
      }
      disconnect() { if (this.record) this.record.connected = false; return super.disconnect(); }
    };
  }
  const setT = window.setTimeout;
  const clearT = window.clearTimeout;
  const raf = window.requestAnimationFrame;
  const caf = window.cancelAnimationFrame;
  window.setTimeout = function (fn, ms, ...args) {
    const tracked = mine();
    const id = setT.call(window, function (...a) { timers.delete(id); return typeof fn === "function" ? fn.apply(this, a) : undefined; }, ms, ...args);
    if (tracked) timers.set(id, ms);
    return id;
  };
  window.clearTimeout = function (id) { timers.delete(id); return clearT.call(window, id); };
  window.requestAnimationFrame = function (fn) {
    const tracked = mine();
    const id = raf.call(window, (t) => { rafs.delete(id); return fn(t); });
    if (tracked) rafs.add(id);
    return id;
  };
  window.cancelAnimationFrame = function (id) { rafs.delete(id); return caf.call(window, id); };
  window.__howRuntime = () => ({
    listeners: [...live.values()].map((e) => `${describe(e.target)}:${e.type}`).sort(),
    observers: observers.filter((o) => o.connected).map((o) => o.name).sort(),
    timers: timers.size,
    rafs: rafs.size,
  });
}

const geometry = () => {
  const root = document.querySelector("#how .hw");
  const track = root.querySelector("[data-how-track]");
  const vh = parseFloat(root.style.getPropertyValue("--hw-vh")) || innerHeight;
  return { base: scrollY + track.getBoundingClientRect().top, vh };
};
const state = (page) => page.evaluate(() => {
  const root = document.querySelector("#how .hw");
  const track = root.querySelector("[data-how-track]");
  const vh = parseFloat(root.style.getPropertyValue("--hw-vh")) || innerHeight;
  let item = 0;
  if (root.dataset.howMode === "static") {
    let best = Infinity;
    root.querySelectorAll("[data-how-scene]").forEach((el) => {
      const r = el.getBoundingClientRect();
      const d = Math.abs((r.top + r.bottom) / 2 - innerHeight / 2);
      if (r.bottom > 0 && r.top < innerHeight && d < best) { best = d; item = Number(el.dataset.howScene); }
    });
  }
  return {
    mode: root.dataset.howMode,
    beat: Number(track.dataset.howBeat ?? -1),
    step: Number(track.dataset.howStep ?? -1),
    rest: Number(root.dataset.howRest ?? -1),
    pose: root.querySelector(".hw-stage > .world")?.dataset.howPose ?? null,
    s: -track.getBoundingClientRect().top / vh,
    item,
  };
});
const frames = (page, n = 2) => page.evaluate((count) => new Promise((resolve) => {
  const next = (left) => (left ? requestAnimationFrame(() => next(left - 1)) : resolve());
  next(count);
}), n);
const scrollToS = async (page, s) => {
  await page.evaluate((target) => {
    const root = document.querySelector("#how .hw");
    const track = root.querySelector("[data-how-track]");
    const vh = parseFloat(root.style.getPropertyValue("--hw-vh")) || innerHeight;
    window.scrollTo({ top: scrollY + track.getBoundingClientRect().top + target * vh, behavior: "instant" });
  }, s);
  await frames(page, 3);
};

const server = await createServer({ root, configFile: path.join(root, "vite.config.mjs"), logLevel: "warn", server: { host: "127.0.0.1", port: 5990, strictPort: false } });
await server.listen();
const url = server.resolvedUrls.local[0];
const browser = await chromium.launch({ headless: true, args: ["--use-angle=swiftshader", "--enable-unsafe-swiftshader"] });
const passed = [];
const open = async (contextOptions, { routes = [] } = {}) => {
  const context = await browser.newContext({ serviceWorkers: "block", ...contextOptions });
  // nothing leaves the machine (analytics providers, CDN globe textures)
  await context.route((u) => !u.href.startsWith(url) && !/fonts\.(googleapis|gstatic)\.com/.test(u.hostname), (route) => route.abort());
  for (const [pattern, handler] of routes) await context.route(pattern, handler);
  await context.addInitScript(instrument);
  const page = await context.newPage();
  page.setDefaultTimeout(20000);
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await page.goto(url, { waitUntil: "domcontentloaded" });
  await page.waitForSelector("#how .hw[data-how-mode]");
  await page.evaluate(() => document.fonts.ready);
  await frames(page, 4);
  return { context, page, errors };
};
const desktop = { viewport: { width: 1280, height: 800 } };
const phone = { viewport: { width: 390, height: 664 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true };
const tablet = { viewport: { width: 768, height: 1024 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true };

try {
  // 1. StrictMode leaves one film, and one of each page listener; reading the film, a scene is at rest only at its
  //    point and the stage shows the beat's pose; the map's layers arrive.
  for (const [label, options] of [["desktop", desktop], ["phone", phone]]) {
    const { context, page, errors } = await open(options);
    const counts = await page.evaluate(() => ({
      roots: document.querySelectorAll(".hw").length,
      worlds: document.querySelectorAll(".hw .hw-stage > .world").length,
      beats: document.querySelectorAll(".hw .hw-copy > .hw-beat").length,
      probes: document.querySelectorAll(".hw [data-how-probe]").length,
      drifts: document.querySelectorAll(".hw .hw-stage .drift").length,
      staticItems: document.querySelectorAll(".hw [data-how-scene]").length,
      mode: document.querySelector(".hw").dataset.howMode,
    }));
    assert.deepEqual(counts, { roots: 1, worlds: 1, beats: 6, probes: 1, drifts: 1, staticItems: 0, mode: "scroll" }, label);
    assert.deepEqual((await page.evaluate(() => window.__howRuntime())).listeners, PAGE_LISTENERS, `${label}: StrictMode's first mount left a listener`);

    // Read the film slowly (a 0.06 VH step at a time), stopping at each beat's point, and record every change of the
    // beat, step and scene at rest.
    await page.evaluate(() => {
      const root = document.querySelector("#how .hw");
      const track = root.querySelector("[data-how-track]");
      window.__howLog = [];
      const snap = () => window.__howLog.push({ beat: Number(track.dataset.howBeat), step: Number(track.dataset.howStep), rest: Number(root.dataset.howRest ?? 0) });
      new MutationObserver(snap).observe(track, { attributes: true, attributeFilter: ["data-how-beat", "data-how-step"] });
      new MutationObserver(snap).observe(root, { attributes: true, attributeFilter: ["data-how-rest"] });
    });
    await scrollToS(page, -0.6);
    const poses = new Map();
    for (let s = -0.6; s <= 4.7; s += 0.06) {
      await scrollToS(page, s);
      const st = await state(page);
      if (st.beat >= 1 && !poses.has(st.beat) && s >= 0 && s <= 4.4) {
        // 02 and 04 reach their point on a timer; the others with the scroll
        if (TIMED_STEP.has(st.beat) && st.step !== FINAL_STEP[st.beat]) {
          await page.waitForFunction(([b, k]) => {
            const track = document.querySelector("#how [data-how-track]");
            return Number(track.dataset.howBeat) !== b || Number(track.dataset.howStep) === k;
          }, [st.beat, FINAL_STEP[st.beat]]);
        }
        await frames(page, 3);
        const at = await state(page);
        if (at.beat === st.beat && at.step === FINAL_STEP[at.beat]) poses.set(at.beat, at);
      }
    }
    const log = await page.evaluate(() => window.__howLog);
    for (const entry of log) {
      assert.ok(entry.rest === 0 || (entry.rest === entry.beat && entry.step === FINAL_STEP[entry.beat]), `${label}: a scene at rest before its point ${JSON.stringify(entry)}`);
    }
    assert.deepEqual([...new Set(log.map((e) => e.rest).filter(Boolean))].sort(), [1, 2, 3, 4, 5], `${label}: every scene came to rest`);
    for (const [beat, at] of poses) {
      assert.equal(at.pose, `${beat}.${FINAL_STEP[beat]}`, `${label}: the stage shows beat ${beat}'s pose`);
      assert.equal(at.rest, beat, `${label}: beat ${beat} at rest at its point`);
    }
    assert.equal(poses.size, 5, `${label}: every beat's point was seen`);
    await page.waitForFunction(() => document.querySelectorAll(".hw .hw-stage .map.has-map .ml").length === 4);
    const runtime = await page.evaluate(() => window.__howRuntime());
    assert.deepEqual(runtime.listeners, PAGE_LISTENERS, label);
    assert.deepEqual(runtime.observers, ["ResizeObserver"], `${label}: the stage's ResizeObserver only (the loader's IntersectionObserver is done)`);
    assert.deepEqual(errors, [], label);
    passed.push(`${label}: StrictMode leaves one film and one of each page listener; scenes rest only at their points; the stage shows each beat's pose; the map's 4 layers arrive`);
    await context.close();
  }

  // 2. Mount and destroy the engine 70 times on a skeleton in view: everything it made goes away.
  {
    const { context, page, errors } = await open(desktop);
    const cdp = await context.newCDPSession(page);
    const gc = async () => { for (let i = 0; i < 3; i += 1) { await cdp.send("HeapProfiler.collectGarbage"); await page.waitForTimeout(100); } };
    await page.evaluate(async () => {
      const { mountHowPhotoStory } = await import("/src/howPhotoStoryEngine.js");
      const lqip = (await import("/src/data/how-photo-story-lqip.json")).default;
      const host = document.createElement("div");
      host.innerHTML = '<div class="hw"><div class="hw-head"><p class="hw-eyebrow">x</p><h2>x</h2></div><div class="hw-track" data-how-track=""><div class="hw-frame" data-how-frame=""><div class="hw-grid"><div class="hw-copy" aria-hidden="true"></div><div class="hw-stage" aria-hidden="true"></div></div></div><ol class="hw-sr"></ol></div><div class="hw-static"></div></div>';
      document.querySelector("#how").after(host);
      window.__howHost = { host, pristine: host.innerHTML };
      window.__howCycles = async (n) => {
        for (let i = 0; i < n; i += 1) {
          const destroy = mountHowPhotoStory(host.firstElementChild, { lqip, onState: () => {} });
          host.scrollIntoView({ behavior: "instant" });
          window.scrollBy(0, 400);
          await new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)));
          destroy();
        }
      };
    });
    // Warm up first: scrolling the skeleton into view also brings the globe section below into view, which builds
    // its own nodes once. Then two rounds of 30: a leak grows with every round, the page's own one-off work does not.
    const measure = async (cycles) => {
      await page.evaluate((n) => window.__howCycles(n), cycles);
      await page.waitForTimeout(1200);
      await gc();
      return { runtime: await page.evaluate(() => window.__howRuntime()), dom: await cdp.send("Memory.getDOMCounters") };
    };
    const warm = await measure(10);
    const first = await measure(30);
    const second = await measure(30);
    for (const round of [first, second]) assert.deepEqual(round.runtime, warm.runtime, "mount/destroy cycles leave listeners, observers, timers or frame requests behind");
    const nodes = [warm, first, second].map((r) => r.dom.nodes);
    const listeners = [warm, first, second].map((r) => r.dom.jsEventListeners);
    assert.ok(second.dom.nodes - first.dom.nodes <= 3 && first.dom.nodes - warm.dom.nodes <= 15, `DOM nodes grow with remounts: ${nodes.join(" -> ")}`);
    assert.ok(second.dom.jsEventListeners - first.dom.jsEventListeners <= 1, `JS listeners grow with remounts: ${listeners.join(" -> ")}`);
    // (the inline styles it set are removed; an emptied style attribute may stay)
    assert.equal(await page.evaluate(() => window.__howHost.host.innerHTML.replaceAll(' style=""', "") === window.__howHost.pristine), true, "destroy() restores the skeleton");
    assert.deepEqual(errors, []);
    passed.push(`70 engine remounts in view: listeners, observers, timers, frame requests unchanged; DOM nodes ${nodes.join(" -> ")}, JS listeners ${listeners.join(" -> ")}; skeleton restored`);
    await context.close();
  }

  // 3. The static version (reduced motion): five figures hidden from screen readers, each at its beat's end pose, each
  //    with its map; the item in view is reported at rest.
  {
    const { context, page, errors } = await open({ ...desktop, reducedMotion: "reduce" });
    const items = await page.evaluate(() => [...document.querySelectorAll(".hw [data-how-scene]")].map((item) => ({
      scene: item.dataset.howScene,
      hidden: item.querySelector(".st-fig")?.getAttribute("aria-hidden"),
      pose: item.querySelector(".st-fig .world")?.dataset.howPose,
      h3: Boolean(item.querySelector("h3")),
    })));
    assert.deepEqual(items.map((i) => i.scene), ["1", "2", "3", "4", "5"]);
    assert.ok(items.every((i) => i.hidden === "true" && i.h3), JSON.stringify(items));
    assert.deepEqual(items.map((i) => i.pose), STATIC_POSES);
    for (const scene of [3, 5]) {
      await page.evaluate((n) => document.querySelector(`.hw [data-how-scene="${n}"]`).scrollIntoView({ block: "center", behavior: "instant" }), scene);
      await frames(page, 3);
      assert.equal((await state(page)).rest, scene, `static item ${scene} at rest`);
    }
    await page.waitForFunction(() => [...document.querySelectorAll(".hw [data-how-scene] .map")].every((m) => m.classList.contains("has-map") && m.querySelectorAll(".ml").length === 4));
    assert.deepEqual(errors, []);
    passed.push("static: 5 figures aria-hidden at poses " + STATIC_POSES.join(" ") + ", each with its map; the item in view at rest");
    await context.close();
  }

  // 4. The reader keeps their place when the viewport changes: a phone rotated (film -> static -> film) and a tablet
  //    rotated (the film on both sides), also while the frame scrolls away after 05.
  const tabletLandscape = { ...tablet, viewport: { width: 1024, height: 768 } };
  for (const [label, options, other, s, beat] of [
    ["phone", phone, { width: 750, height: 342 }, 2.27, 3],
    ["tablet", tablet, { width: 1024, height: 768 }, 3.1, 4],
    ["tablet leaving 05", tabletLandscape, { width: 768, height: 1024 }, 4.9, 5],
  ]) {
    const { context, page, errors } = await open(options);
    await scrollToS(page, s);
    await page.waitForTimeout(400);
    assert.equal((await state(page)).beat, beat, label);
    await page.setViewportSize(other);
    await page.waitForTimeout(500);
    const turned = await state(page);
    if (label === "phone") assert.deepEqual([turned.mode, turned.item], ["static", beat], `${label}: rotated to the static version at item ${beat}`);
    else assert.deepEqual([turned.mode, turned.beat], ["scroll", beat], `${label}: rotated, still on beat ${beat} (s=${turned.s.toFixed(2)})`);
    await page.setViewportSize(options.viewport);
    await page.waitForTimeout(500);
    const back = await state(page);
    assert.deepEqual([back.mode, back.beat], ["scroll", beat], `${label}: rotated back to beat ${beat} (s=${back.s.toFixed(2)})`);
    assert.deepEqual(errors, [], label);
    passed.push(`${label} rotated at beat ${beat}: ${label === "phone" ? `static item ${turned.item}` : `beat ${turned.beat}`}, back on beat ${back.beat}`);
    await context.close();
  }

  // 5. Failures degrade quietly. The map chunk never arrives: 05 shows its blurred preview and no stray ground shadow.
  //    Every picture fails: each keeps its blurred preview and paints no broken-image icon.
  {
    const { context, page } = await open(desktop, { routes: [[/how-photo-map\.json/, (route) => route.abort("failed")]] });
    await scrollToS(page, 4.0);
    await page.waitForTimeout(600);
    const map = await page.evaluate(() => {
      const m = document.querySelector(".hw .hw-stage .map");
      const inner = m.querySelector(".in");
      return {
        layers: m.querySelectorAll(".ml").length,
        hasMap: m.classList.contains("has-map"),
        shadow: getComputedStyle(inner, "::before").display,
        preview: getComputedStyle(inner, "::after").backgroundImage.startsWith('url("data:image/webp'),
      };
    });
    assert.deepEqual(map, { layers: 0, hasMap: false, shadow: "none", preview: true }, "05 without its map chunk shows the blurred preview, no shadow");
    await context.close();
  }
  {
    const failed = /\/assets\/(how\/(heap|gallery)|photos\/gyeongbuk-0\d)\.webp/;
    const { context, page, errors } = await open(phone, { routes: [[failed, (route) => route.abort("failed")]] });
    await scrollToS(page, 1.4);
    await page.waitForTimeout(1200);
    const pictures = await page.evaluate(() => [...document.querySelectorAll(".hw img")].map((im) => ({ src: im.getAttribute("src"), broken: im.hasAttribute("src") && im.complete && im.naturalWidth === 0, preview: /data:image/.test(im.style.backgroundImage) })));
    assert.ok(pictures.length >= 8);
    assert.deepEqual(pictures.filter((p) => p.broken), [], "a failed picture keeps a broken src");
    assert.ok(pictures.every((p) => p.preview), "every picture keeps its preview");
    assert.deepEqual(errors, []);
    passed.push("failures: without its map chunk 05 shows the blurred map preview and no ground shadow; failed pictures keep their previews with no broken src");
    await context.close();
  }
  console.log(JSON.stringify({ url, passed }, null, 2));
} finally {
  await browser.close();
  await server.close();
}
