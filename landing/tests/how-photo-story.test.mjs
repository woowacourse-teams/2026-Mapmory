import assert from "node:assert/strict";
import { access, mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import path from "node:path";
import test from "node:test";
import { fileURLToPath, pathToFileURL } from "node:url";
import { build } from "esbuild";
import {
  COAST_MS,
  COMPACT_VH,
  COPY,
  IMAGE_GROUPS,
  INNER,
  PHOTOS,
  PIN_VH,
  STATIC_FIT,
  STATIC_MAX_VH,
  STEPS,
  TAG,
  TITLE,
  TM,
  beatEnd,
  beatStart,
  chainIsFast,
  copyFade,
  copyFor,
  driftAt,
  fitWorld,
  halfInView,
  imageGroupOf,
  imageUrl,
  innerStepOnEnter,
  isStaticMode,
  latchViewportHeight,
  placeOf,
  restPosition,
  restStep,
  sceneAtRest,
  sceneOf,
  stageTooSmall,
  stateAt,
  willPass,
} from "../src/howPhotoStory.js";

const landing = fileURLToPath(new URL("../", import.meta.url));
const read = (file) => readFile(new URL(file, import.meta.url), "utf8");
const appSource = await read("../src/App.jsx");
const componentSource = await read("../src/HowPhotoStory.jsx");
const engineSource = await read("../src/howPhotoStoryEngine.js");
const modelSource = await read("../src/howPhotoStory.js");
const storyCss = await read("../src/how-photo-story.css");
const landingCss = (await read("../src/styles.css")) + (await read("../src/photo-finder-hero.css"));
const mainSource = await read("../src/main.jsx");
const lqip = JSON.parse(await read("../src/data/how-photo-story-lqip.json"));
const sectionSources = [componentSource, engineSource, modelSource];
const stripComments = (source) => source.replace(/\/\*[\s\S]*?\*\//g, "").replace(/(^|[^:"'`])\/\/.*$/gm, "$1");
const allStrings = [...TITLE, ...COPY.flat(), TAG];

test("the section's copy is exactly the approved sketch A copy", () => {
  assert.deepEqual(TITLE, ["사용 방법", "사진이 수천 장이어도", "장소만 고르면 돼요"]);
  assert.deepEqual(COPY, [
    ["01", "사진마다 찍은 곳이", "담겨 있어요", "카메라 위치 저장이 켜져 있으면요"],
    ["02", "수천 장 속에서", "그곳 사진만 모여요", "사진에 저장된 위치로 찾아요"],
    ["03", "찾는 건", "내 폰 안에서 해요", "찾는 동안 사진을 서버로 보내지 않아요"],
    ["04", "저장할 때", "고른 사진만 올라가요", "나머지 사진은 그대로 폰에 있어요"],
    ["05", "다녀온 곳이", "지도에 칠해져요", "경주시, 경상북도, 대한민국까지요"],
  ]);
  assert.equal(TAG, "경주");
});

test("no claim the app does not back, on either app version: no time or tap claims, no AI, no deleting, no offline", () => {
  const banned = /\bAI\b|인공지능|인식|위치.{0,6}(지워|삭제|제거)|삭제|지워|지울|원본|10장|오프라인|자동|스캔|분석|순식간|금방|빠르게|바로|몇 초|\d+\s*초|\d+\s*분|\d+\s*장|탭|모두 선택|업로드 중|올리는 중|저장 중|\d+%|폰에만|폰 안에만|권한 없이|명소|맛집|오사카|아무것도/;
  for (const value of allStrings) assert.doesNotMatch(value, banned, value);
  // The only digits on the section are the beat numbers.
  for (const value of [...TITLE, ...COPY.flatMap(([, l1, l2, sub]) => [l1, l2, sub])]) assert.doesNotMatch(value, /\d/, value);
  assert.deepEqual(COPY.map(([eyebrow]) => eyebrow), ["01", "02", "03", "04", "05"]);
  // Every visible word comes from the model: the component and the engine hold no Korean text of their own.
  for (const [file, source] of [["HowPhotoStory.jsx", componentSource], ["howPhotoStoryEngine.js", engineSource]]) {
    assert.doesNotMatch(stripComments(source), /[가-힣]/, file);
  }
});

test("the film's thresholds keep the approved spans and constants", () => {
  const flat = STEPS.flat();
  for (let i = 1; i < flat.length; i += 1) assert.ok(flat[i] > flat[i - 1], `step ${i} increases`);
  assert.ok(flat.every((t) => t > -1 && t < PIN_VH));
  assert.deepEqual(STEPS, [[-0.2, 0.2], [0.96], [1.9], [2.64], [3.56]]);
  // Spans: title .20 · 01 .76 · 02 .94 · 03 .74 · 04 .92 · 05 .90 (the frame pins at s = 0, unpins at PIN_VH).
  const spans = [STEPS[0][1] - 0, STEPS[1][0] - STEPS[0][1], STEPS[2][0] - STEPS[1][0], STEPS[3][0] - STEPS[2][0], STEPS[4][0] - STEPS[3][0], PIN_VH - STEPS[4][0]];
  assert.deepEqual(spans.map((v) => +v.toFixed(2)), [0.2, 0.76, 0.94, 0.74, 0.92, 0.9]);
  assert.equal(PIN_VH, 4.46);
  assert.equal(COAST_MS, 480);
  assert.deepEqual(INNER, { 2: "sub", 4: "copy" });
  assert.deepEqual(TM.slow, { sub: 300, jump: [100, 0, 40, 80], jumpDur: 560, rimAfter: 360, heapIn: 380, heapD: 180, heapDur: 600, heapO: 360, drop: 520, slotD: 160,
    objO: 380, lockIn: 260, lkd: 520, gld: 620, settleD: 40, settleStep: 30, settleDur: 620, chk: [100, 170, 240], copy: 260, copyStep: 70, copyDur: 640, p01: 910 });
  assert.deepEqual(TM.fast, { sub: 140, jump: [0, 0, 30, 60], jumpDur: 480, rimAfter: 280, heapIn: 160, heapD: 120, heapDur: 500, heapO: 300, drop: 400, slotD: 0,
    objO: 220, lockIn: 100, lkd: 280, gld: 340, settleD: 0, settleStep: 20, settleDur: 520, chk: [40, 80, 120], copy: 60, copyStep: 50, copyDur: 520, p01: 760 });
  // Every fast chain is shorter than its slow one, so a fling always plays the tighter version.
  for (const key of Object.keys(TM.slow)) {
    const [fast, slow] = [TM.fast[key], TM.slow[key]].map((v) => (Array.isArray(v) ? Math.max(...v) : v));
    assert.ok(fast <= slow, key);
  }
  assert.equal(STATIC_MAX_VH, 500);
  assert.equal(COMPACT_VH, 700);
  assert.equal(STATIC_FIT, 0.94);
});

test("stateAt maps a scroll position to its beat and step at every boundary", () => {
  const cases = [[-5, 0, 0], [-0.2001, 0, 0], [-0.2, 1, 0], [0, 1, 0], [0.1999, 1, 0], [0.2, 1, 1], [0.9599, 1, 1], [0.96, 2, 0], [1.8999, 2, 0], [1.9, 3, 0],
    [2.6399, 3, 0], [2.64, 4, 0], [3.5599, 4, 0], [3.56, 5, 0], [PIN_VH, 5, 0], [9, 5, 0]];
  for (const [s, b, k] of cases) assert.deepEqual(stateAt(s), { b, k }, `s=${s}`);
  // A beat's span runs from its start to the next beat's threshold (05 ends just after the unpin).
  assert.equal(beatStart(0, 0), -9);
  assert.equal(beatStart(1, 1), 0.2);
  assert.equal(beatStart(3, 0), 1.9);
  assert.equal(beatEnd(4), 3.56);
  assert.equal(beatEnd(5), PIN_VH + 0.3);
});

test("a fling past a beat plays its short chain; a scroll that comes to rest in it plays the full one", () => {
  const vh = 664;
  const pxPerMs = (vhPerSecond) => vhPerSecond * vh / 1000;
  // Slow reading never counts as a fling.
  assert.equal(chainIsFast({ s: 2.0, v: pxPerMs(0.25), vh, b: 3, k: 0, first: false }), false);
  // Fast, and the coast carries it past 03 (stop >= 2.64).
  assert.equal(chainIsFast({ s: 2.0, v: pxPerMs(3), vh, b: 3, k: 0, first: false }), true);
  // Fast, but it will come to rest inside 03.
  assert.equal(chainIsFast({ s: 1.95, v: pxPerMs(0.8), vh, b: 3, k: 0, first: false }), false);
  // Fast upward out of 03 (stop < 1.9).
  assert.equal(chainIsFast({ s: 1.95, v: -pxPerMs(1), vh, b: 3, k: 0, first: false }), true);
  // The first paint is never fast.
  assert.equal(chainIsFast({ s: 2.0, v: pxPerMs(3), vh, b: 3, k: 0, first: true }), false);
  // The inner step's early timer: a firm fling carries the reader past 02, a stop does not.
  assert.equal(willPass({ s: 1.0, v: pxPerMs(2), vh, b: 2 }), true);
  assert.equal(willPass({ s: 1.0, v: 0, vh, b: 2 }), false);
  // 02/04 entered going forward start at step 0 (the timer plays the step); coming back or on first paint, the rest state.
  assert.equal(innerStepOnEnter({ b: 2, prevB: 1 }), 0);
  assert.equal(innerStepOnEnter({ b: 2, prevB: 3 }), 1);
  assert.equal(innerStepOnEnter({ b: 4, prevB: -1 }), 1);
});

test("modes: reduced motion, a short viewport or a stage too small get the static version", () => {
  assert.equal(isStaticMode({ reducedMotion: false, vh: 664, smallWidthLatched: false }), false);
  assert.equal(isStaticMode({ reducedMotion: true, vh: 900, smallWidthLatched: false }), true);
  assert.equal(isStaticMode({ reducedMotion: false, vh: 390, smallWidthLatched: false }), true); // a landscape phone
  assert.equal(isStaticMode({ reducedMotion: false, vh: 499, smallWidthLatched: false }), true);
  assert.equal(isStaticMode({ reducedMotion: false, vh: 500, smallWidthLatched: false }), false);
  assert.equal(isStaticMode({ reducedMotion: false, vh: 844, smallWidthLatched: true }), true); // very large text
  assert.equal(stageTooSmall({ portraitPhone: true, stageHeight: 239, vh: 600 }), true);
  assert.equal(stageTooSmall({ portraitPhone: true, stageHeight: 300, vh: 844 }), true); // under 0.36 x 844
  assert.equal(stageTooSmall({ portraitPhone: true, stageHeight: 320, vh: 844 }), false);
  assert.equal(stageTooSmall({ portraitPhone: false, stageHeight: 100, vh: 844 }), false);
  assert.deepEqual(fitWorld(390, 560), { tall: true, worldHeight: 760, scale: 0.65 });
  assert.equal(fitWorld(590, 620).tall, false);
});

test("the viewport height is latched per width on touch devices, so the address bar never re-lays the film", () => {
  const base = { touch: true, width: 390, lastWidth: 390, vh: 664 };
  assert.equal(latchViewportHeight({ ...base, measured: 750 }), 664); // toolbar hides: +13%, kept
  assert.equal(latchViewportHeight({ ...base, measured: 600 }), 664);
  assert.equal(latchViewportHeight({ ...base, measured: 390 }), 390); // a big change re-measures
  assert.equal(latchViewportHeight({ ...base, width: 844, measured: 390 }), 390); // rotation
  assert.equal(latchViewportHeight({ ...base, touch: false, measured: 700 }), 700); // desktop follows the window
  assert.equal(latchViewportHeight({ ...base, vh: 0, measured: 700 }), 700);
});

test("copy, fade and drift are pure functions of the scroll position", () => {
  assert.equal(copyFor({ b: 0, k: 0, titleOnScreen: false }), -1);
  assert.equal(copyFor({ b: 0, k: 0, titleOnScreen: true }), 0);
  assert.equal(copyFor({ b: 1, k: 0, titleOnScreen: true }), 0);
  assert.equal(copyFor({ b: 1, k: 1, titleOnScreen: true }), 1);
  assert.equal(copyFor({ b: 4, k: 0, titleOnScreen: false }), 4);
  assert.equal(copyFade(PIN_VH - 0.04), 1);
  assert.ok(copyFade(PIN_VH) > 0 && copyFade(PIN_VH) < 1);
  assert.equal(copyFade(PIN_VH + 0.06), 0);
  assert.equal(driftAt(0.5, 0), null);
  assert.equal(driftAt(STEPS[2][0], 3), 10);
  assert.equal(driftAt(STEPS[3][0], 3), -10);
  assert.equal(driftAt(PIN_VH + 0.6, 5), -10);
  assert.equal(placeOf(1, 0), 0);
  assert.equal(placeOf(3, 0), 3);
  assert.equal(restPosition(0), 0);
  assert.equal(restPosition(1), 0.5);
  assert.ok(Math.abs(restPosition(5) - 3.86) < 1e-9);
});

test("analytics: a scene is a beat 1-5 resting at its point on a pinned frame; a static item rests half in view", () => {
  assert.equal(sceneOf(0, 0), 0);
  assert.equal(sceneOf(1, 0), 0); // the 01 photo rides in under the title: still the title
  assert.deepEqual([1, 2, 3, 4, 5].map(restStep), [1, 1, 0, 1, 0]);
  assert.equal(sceneAtRest({ b: 1, k: 1, pinned: true }), true);
  assert.equal(sceneAtRest({ b: 1, k: 0, pinned: true }), false);
  assert.equal(sceneAtRest({ b: 2, k: 0, pinned: true }), false); // 02's inner step has not started
  assert.equal(sceneAtRest({ b: 2, k: 1, pinned: true }), true);
  assert.equal(sceneAtRest({ b: 3, k: 0, pinned: true }), true);
  assert.equal(sceneAtRest({ b: 4, k: 0, pinned: true }), false);
  assert.equal(sceneAtRest({ b: 4, k: 1, pinned: true }), true);
  assert.equal(sceneAtRest({ b: 5, k: 0, pinned: true }), true);
  assert.equal(sceneAtRest({ b: 5, k: 0, pinned: false }), false); // unpinned, the map leaving
  assert.equal(sceneAtRest({ b: 0, k: 0, pinned: true }), false);
  assert.equal(halfInView(0, 400, 844), true);
  assert.equal(halfInView(644, 1044, 844), true); // 200 of 400 on screen
  assert.equal(halfInView(645, 1045, 844), false);
  assert.equal(halfInView(-3000, 500, 844), true); // taller than the screen: half the screen is enough
  assert.equal(halfInView(-3000, 400, 844), false);
  assert.equal(halfInView(900, 1200, 844), false);
});

test("every image the section requests exists under public/, with a tiny preview for each", async () => {
  const names = IMAGE_GROUPS.flat();
  assert.deepEqual(names, ["photos/gyeongbuk-02", "photos/gyeongbuk-01", "photos/gyeongbuk-03", "photos/gyeongbuk-04", "heap", "gallery"]);
  assert.deepEqual(PHOTOS.map((name) => `photos/${name}`).sort(), names.filter((n) => n.startsWith("photos/")).sort());
  for (const name of names) {
    const url = imageUrl(name);
    assert.match(url, /^\/assets\/(photos|how)\/[a-z0-9-]+\.webp$/);
    await access(path.join(landing, "public", url));
    assert.equal(imageGroupOf(url), IMAGE_GROUPS.findIndex((group) => group.includes(name)));
    assert.match(lqip[name.replace("photos/", "")], /^data:image\/webp;base64,/);
  }
  assert.equal(imageUrl("heap"), "/assets/how/heap.webp");
  assert.match(storyCss, /url\("\/assets\/how\/gallery\.webp"\)/);
  await access(path.join(landing, "public/assets/how/gallery.webp"));
});

test("App renders the story inside section#how, labelled by its h2, with main's globe link after it", () => {
  assert.match(appSource, /<section className="how-section" id="how" aria-labelledby="how-title">\s*<HowPhotoStory \/>\s*<a className="how-experience-link" href="#experience" onClick=\{\(\) => globeAnalytics\.trackEntryClick\("how_section"\)\}>[\s\S]*?<\/a>\s*<\/section>/);
  assert.match(appSource, /import \{ HowPhotoStory \} from "\.\/HowPhotoStory\.jsx";/);
  assert.match(mainSource, /import "\.\/how-photo-story\.css";/);
  assert.doesNotMatch(mainSource, /how-play/);
  assert.match(componentSource, /<h2 id="how-title"><span>\{TITLE\[1\]\}<\/span> <span>\{TITLE\[2\]\}<\/span><\/h2>/);
  // The pinned film's visible copy and the stage are aria-hidden; screen readers get one clean list.
  assert.match(componentSource, /<div className="hw-copy" aria-hidden="true" \/>/);
  assert.match(componentSource, /<div className="hw-stage" aria-hidden="true" \/>/);
  assert.match(componentSource, /<ol className="hw-sr">/);
  // QA and analytics hooks
  assert.match(componentSource, /className="hw-track" data-how-track=""/);
  assert.match(componentSource, /className="hw-frame" data-how-frame=""/);
  assert.match(engineSource, /root\.dataset\.howMode = isStatic \? "static" : "scroll";/);
  assert.match(engineSource, /track\.dataset\.howBeat = String\(b\);\s*track\.dataset\.howStep = String\(k\);/);
  assert.match(engineSource, /item\.dataset\.howScene = String\(i \+ 1\);/);
});

test("the section never steers the scroll: no wheel or touch handlers, no preventDefault, no snap or lock", () => {
  for (const source of [...sectionSources, storyCss]) {
    assert.doesNotMatch(source, /addEventListener\("(wheel|touch\w*|key\w*|pointer\w*|mousewheel)"|preventDefault|scroll-snap|overscroll-behavior|touch-action|setInterval|scrollBy\(/);
    assert.doesNotMatch(source, /document\.body\.style\.overflow|overflow:\s*hidden\s*;?\s*\}?\s*$/m);
  }
  assert.doesNotMatch(storyCss, /(html|body)\s*\{/);
  // The only programmatic scroll is the mode-switch restore, and every call of it is instant.
  const code = stripComments(engineSource);
  const scrolls = [...code.matchAll(/(scrollTo|scrollIntoView)\(([^;]*)\);?/g)];
  assert.equal(scrolls.length, 3);
  for (const [call] of scrolls) assert.match(call, /behavior: "instant"/, call);
  const restore = code.slice(code.indexOf("function restore("), code.indexOf("function setMode("));
  assert.equal([...restore.matchAll(/scrollTo\(|scrollIntoView\(/g)].length, 3);
  assert.doesNotMatch(stripComments(componentSource), /scrollTo|scrollIntoView/);
  // The listeners the engine does add are passive scroll and resize, and destroy() removes them.
  assert.match(engineSource, /window\.addEventListener\("scroll", req, \{ passive: true \}\);/);
  assert.match(engineSource, /window\.removeEventListener\("scroll", req\);\s*window\.removeEventListener\("resize", refresh\);/);
});

test("destroy() undoes everything the engine made, so StrictMode's remount leaves one film", () => {
  const destroy = engineSource.slice(engineSource.indexOf("return function destroy()"));
  for (const part of [/cleanups\.forEach\(\(cleanup\) => cleanup\(\)\)/, /cancelAnimationFrame\(ticking\)/, /cancelAnimationFrame\(pendRaf\)/, /clearTimeout\(subTimer\)/,
    /created\.forEach\(\(node\) => node\.remove\(\)\)/, /root\.classList\.remove\("is-static", "hw-compact", "ld"\)/, /delete root\.dataset\.howMode/]) {
    assert.match(destroy, part);
  }
  // Every observer is registered through observe(), which queues its disconnect.
  assert.equal([...engineSource.matchAll(/new window\[Observer\]/g)].length, 1);
  assert.doesNotMatch(engineSource, /new (ResizeObserver|IntersectionObserver)\(/);
  assert.match(engineSource, /cleanups\.push\(\(\) => observer\.disconnect\(\)\)/);
  // Late callbacks (fonts, timers, the map chunk, image loads) check that the film still exists.
  for (const guard of [/fonts\.ready\.then\(\(\) => \{ if \(destroyed\) return;/, /if \(destroyed \|\| curB !== b \|\| curK !== 0\) return;/, /loadMap\(\)\.then\(\(data\) => \{\s*if \(destroyed\) return;/, /function release\(\) \{\s*if \(destroyed\) return;/]) {
    assert.match(engineSource, guard);
  }
  assert.doesNotMatch(engineSource, /document\.getElementById/);
  assert.match(componentSource, /const destroy = mountHowPhotoStory\(root, /);
  assert.match(componentSource, /destroy\(\);\s*view\.stop\(\);\s*reach\.stop\(\);/);
});

test("the projected map is fetched lazily with the pictures, never in the first chunk", () => {
  assert.match(engineSource, /import\("\.\/data\/how-photo-map\.json"\)/);
  for (const source of sectionSources.concat(appSource)) assert.doesNotMatch(source, /^import [^;]*how-photo-map\.json/m);
  assert.match(engineSource, /const startLoad = \(\) => \{ requestMap\(\); if \(!released\) release\(\); \};/);
  assert.match(engineSource, /rootMargin: "200% 0px"/);
  // The tiny previews ship with the component (about 3 KB).
  assert.match(componentSource, /import lqip from "\.\/data\/how-photo-story-lqip\.json";/);
});

test("the section's CSS is scoped under .hw and collides with nothing on the landing", () => {
  const rules = [...storyCss.replace(/\/\*[\s\S]*?\*\//g, "").matchAll(/([^{}@]+)\{[^{}]*\}/g)].map(([, sel]) => sel.trim()).filter((sel) => sel && !/^(from|to|\d+%)/.test(sel));
  const allowed = /^(\.hw(?![\w-])|:root\[data-theme="dark"\] \.hw(?![\w-])|\.how-section(?![\w-])|\.how-experience-link(?![\w-]))/;
  for (const rule of rules) for (const sel of rule.split(",")) assert.match(sel.trim(), allowed, sel);
  for (const name of [...storyCss.matchAll(/@keyframes ([\w-]+)/g)].map(([, n]) => n)) assert.match(name, /^hw-/);
  for (const name of [...storyCss.matchAll(/(--[\w-]+)\s*:/g)].map(([, n]) => n)) assert.match(name, /^--hw-/);
  // No class the section uses is styled anywhere else on the landing.
  const classes = new Set(rules.flatMap((rule) => [...rule.matchAll(/\.([a-z][\w-]*)/g)].map(([, c]) => c)).filter((c) => !["how-section", "how-experience-link"].includes(c)));
  assert.ok(classes.size > 50);
  for (const cls of classes) assert.doesNotMatch(landingCss, new RegExp(`\\.${cls}(?![\\w-])`), cls);
  // section#how is no longer squeezed into the trust section's column.
  assert.doesNotMatch(landingCss, /\.how-section/);
  assert.match(storyCss, /\.how-section\{position:relative;padding:0 0 72px;scroll-margin-top:0\}/);
  assert.match(storyCss, /\.hw\{\s*--hw-canvas:var\(--canvas\);--hw-ink:var\(--text\)/);
  // Dark theme: its own tokens, the landing's AA sub text, every map layer restyled.
  assert.match(storyCss, /:root\[data-theme="dark"\] \.hw\{--hw-ink-2:var\(--muted\)\}/);
  for (const layer of ["m-base", "m-kr", "m-gb", "m-gj"]) assert.match(storyCss, new RegExp(`:root\\[data-theme="dark"\\] \\.hw \\.${layer} \\.big\\{fill:`));
});

test("analytics: one passive view and one reach per scene, the view flushed first, never experience_* or memory_open", () => {
  assert.match(componentSource, /onReach: \(index\) => \{\s*view\.flush\(\);\s*trackEvent\(ANALYTICS_EVENTS\.HOW_STORY_SCENE, \{ experience_type: EXPERIENCE_TYPE, scene_index: index \}\);/);
  assert.match(componentSource, /trackEvent\(ANALYTICS_EVENTS\.HOW_STORY_VIEW, \{ experience_type: EXPERIENCE_TYPE, story_mode: state\.mode \}\)/);
  assert.match(componentSource, /const EXPERIENCE_TYPE = "how_story";/);
  assert.match(componentSource, /const eligible = state\.inView && document\.visibilityState !== "hidden";/);
  assert.match(componentSource, /document\.addEventListener\("visibilitychange", sync\);/);
  for (const source of sectionSources) assert.doesNotMatch(source, /EXPERIENCE_(VIEW|START|END)|MEMORY_OPEN|experience_(view|start|end)|memory_open|useExperienceAnalytics|how_play/);
  assert.doesNotMatch(engineSource, /trackEvent|analytics\.js/);
});

// Renders the real component with React on the server (analytics stubbed): the skeleton the engine builds into.
test("the server-rendered skeleton has the heading, the film's parts and the five-beat list", async () => {
  const stub = {
    name: "analytics-stub",
    setup(api) {
      api.onResolve({ filter: /\/analytics\.js$/ }, () => ({ path: "analytics", namespace: "stub" }));
      api.onLoad({ filter: /.*/, namespace: "stub" }, () => ({ contents: "export const ANALYTICS_EVENTS = {}; export const trackEvent = () => false;", loader: "js" }));
    },
  };
  const result = await build({
    stdin: { contents: 'export { HowPhotoStory } from "./src/HowPhotoStory.jsx"; export { renderToStaticMarkup } from "react-dom/server"; export { createElement } from "react";', resolveDir: landing, loader: "js" },
    bundle: true, format: "esm", platform: "node", write: false, jsx: "automatic", logLevel: "silent", plugins: [stub],
    external: ["react", "react/*", "react-dom", "react-dom/*"],
  });
  // Inside node_modules/.cache so the bundle resolves the landing's own react.
  const cache = path.join(landing, "node_modules/.cache");
  await mkdir(cache, { recursive: true });
  const dir = await mkdtemp(path.join(cache, "how-photo-story-test-"));
  try {
    const file = path.join(dir, "bundle.mjs");
    await writeFile(file, result.outputFiles[0].text);
    const { HowPhotoStory, renderToStaticMarkup, createElement } = await import(pathToFileURL(file).href);
    const html = renderToStaticMarkup(createElement(HowPhotoStory));
    assert.match(html, /^<div class="hw"><div class="hw-head"><p class="hw-eyebrow">사용 방법<\/p><h2 id="how-title"><span>사진이 수천 장이어도<\/span> <span>장소만 고르면 돼요<\/span><\/h2><\/div>/);
    assert.match(html, /<div class="hw-track" data-how-track=""><div class="hw-frame" data-how-frame=""><div class="hw-grid"><div class="hw-copy" aria-hidden="true"><\/div><div class="hw-stage" aria-hidden="true"><\/div><\/div><\/div><ol class="hw-sr">/);
    const items = [...html.matchAll(/<li><h3>([^<]+)<\/h3><p>([^<]+)<\/p><\/li>/g)].map(([, h, p]) => [h, p]);
    assert.deepEqual(items, COPY.map(([, l1, l2, sub]) => [`${l1} ${l2}`, sub]));
    assert.match(html, /<\/ol><\/div><div class="hw-static"><\/div><\/div>$/);
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});
