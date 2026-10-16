import assert from "node:assert/strict";
import { access, mkdir, mkdtemp, readFile, rm, stat, writeFile } from "node:fs/promises";
import path from "node:path";
import test from "node:test";
import { fileURLToPath, pathToFileURL } from "node:url";
import { build } from "esbuild";
import { PHOTO_FINDER_LIBRARY_COUNT, PHOTO_FINDER_PLACES } from "../src/photoFinderDemo.js";
import { BEAT_OF_UNIT, BEAT_SPAN, BEATS, END_CARD, REPLAY_LIBRARY_COUNT, REPLAY_PLACES, REPLAY_TOTAL, SECTION_VH } from "../src/travelReplay.js";

const landing = fileURLToPath(new URL("../", import.meta.url));
const read = (file) => readFile(new URL(file, import.meta.url), "utf8");
const appSource = await read("../src/App.jsx");
const mainSource = await read("../src/main.jsx");
const componentSource = await read("../src/TravelReplay.jsx");
const engineSource = await read("../src/travelReplayEngine.js");
const modelSource = await read("../src/travelReplay.js");
const css = await read("../src/travel-replay.css");
const sectionSources = [componentSource, engineSource, modelSource];
const stripComments = (source) => source.replace(/\/\*[\s\S]*?\*\//g, "").replace(/(^|[^:"'`])\/\/.*$/gm, "$1");

test("the replay's copy is the approved sketch A copy (replay A v4, 2026-10-15)", () => {
  assert.deepEqual(BEATS, [
    ["사용 방법", "수천 장 속에서 그곳 사진만 모여요", "사진에 저장된 위치로 찾아요"],
    ["사용 방법", "찾는 건 내 폰 안에서 해요", "찾는 동안 사진을 서버로 보내지 않아요"],
    ["01 제주", "제주 사진은 제주로", "제주도부터 대한민국까지 칠해져요"],
    ["02 일본 · 미국", "일본도, 미국도 같은 방법으로", "사진을 한 장씩 옮길 필요 없어요"],
    ["사용 방법", "다녀온 곳이 지구본에 쌓여요", "이제 아래에서 직접 돌려보세요"],
  ]);
  assert.deepEqual(END_CARD, { trust: "사진은 폰 안에서 찾고, 고른 사진만 올라가요.", demo: "예시 여행이에요. 숫자는 히어로 데모와 같아요." });
});

test("the copy never claims what the app does not do", () => {
  const visible = [...BEATS.flat(), ...Object.values(END_CARD), ...engineSource.match(/`내 폰 안[^`]*`|"내 폰 안[^"]*"/g)].join(" ");
  for (const banned of [/AI|인공지능|자동 인식|장면/, /오프라인/, /삭제|지워/, /초|분 만에|탭/, /GPS/]) assert.doesNotMatch(visible, banned);
});

test("the example trip is the hero demo's trip: same places, photos and counts", () => {
  assert.equal(REPLAY_LIBRARY_COUNT, PHOTO_FINDER_LIBRARY_COUNT);
  const hero = new Map(PHOTO_FINDER_PLACES.map((place) => [place.key, place]));
  for (const place of REPLAY_PLACES) {
    const match = hero.get(place.key);
    assert.ok(match, place.key);
    assert.equal(place.name, match.label);
    assert.equal(place.count, match.foundCount);
    assert.equal(place.country, place.island ? "410" : match.regionCode);
  }
  assert.equal(REPLAY_TOTAL, 169);
});

test("the scroll timeline: 740vh, six units, captions 1-5 in order", () => {
  assert.equal(SECTION_VH, 740);
  assert.equal(BEAT_SPAN, 6);
  assert.deepEqual(BEAT_OF_UNIT, [0, 1, 2, 3, 3, 4]);
  assert.match(css, /\.tr-track \{ position: relative; height: 740vh; height: 740svh; \}/);
});

test("section#how renders the replay with its store buttons, and main loads its CSS", () => {
  assert.match(appSource, /import \{ TravelReplay \} from "\.\/TravelReplay\.jsx";/);
  assert.match(appSource, /<section className="how-section" id="how" aria-labelledby="how-title">\s*<TravelReplay storeActions=\{REPLAY_STORE_ACTIONS\} \/>\s*<a className="how-experience-link" href="#experience"/);
  assert.match(appSource, /<StoreButton placement="replay" platform="ios" label="App Store" \/>\s*<StoreButton placement="replay" platform="android" label="Google Play" \/>/);
  assert.match(mainSource, /import "\.\/travel-replay\.css";/);
  assert.doesNotMatch(appSource + mainSource, /HowPhotoStory|how-photo-story/);
});

test("the replay follows the page scroll and never steers it", () => {
  for (const source of sectionSources.map(stripComments)) {
    assert.doesNotMatch(source, /addEventListener\("(wheel|touchstart|touchmove|keydown)"/);
    assert.doesNotMatch(source, /preventDefault|scrollTo|scrollBy|scrollIntoView|overflow = "hidden"/);
  }
  assert.doesNotMatch(css, /scroll-snap/);
  assert.match(engineSource, /window\.addEventListener\("scroll", onScroll, \{ passive: true \}\);/);
});

test("destroy returns every listener and observer, so a StrictMode double mount leaves one replay", () => {
  for (const [add, remove] of [
    ['window.addEventListener("scroll", onScroll', 'window.removeEventListener("scroll", onScroll'],
    ['window.addEventListener("resize", onResize', 'window.removeEventListener("resize", onResize'],
    ['reduceQuery.addEventListener("change", onResize', 'reduceQuery.removeEventListener("change", onResize'],
  ]) {
    assert.ok(engineSource.includes(add), add);
    assert.ok(engineSource.includes(remove), remove);
  }
  for (const call of ["cancelAnimationFrame(raf)", "io.disconnect()", "themeObserver.disconnect()", "cardLayer.replaceChildren()", "markerLayer.replaceChildren()"]) assert.ok(engineSource.includes(call), call);
});

test("geography loads lazily as its own chunks, and photos only once the section is near", () => {
  for (const mod of ['import("d3-geo")', 'import("topojson-client")', 'import("world-atlas/countries-110m.json")', 'import("./data/replay-countries-50m-lite.json")']) assert.ok(engineSource.includes(mod), mod);
  for (const source of sectionSources.concat(appSource)) assert.doesNotMatch(source, /^import [^;]*(d3-geo|topojson-client|world-atlas|replay-countries)/m);
  assert.match(engineSource, /el\.dataset\.src = replayPhoto\(name\)/);
  assert.match(engineSource, /\{ rootMargin: "100% 0px" \}/);
});

test("the simplified 50m map stays small", async () => {
  const file = new URL("../src/data/replay-countries-50m-lite.json", import.meta.url);
  assert.ok((await stat(file)).size < 400 * 1024);
  const topo = JSON.parse(await readFile(file, "utf8"));
  assert.equal(topo.type, "Topology");
  const ids = new Set(topo.objects.countries.geometries.map(({ id }) => id));
  for (const id of ["410", "392", "840"]) assert.ok(ids.has(id), id);
});

test("analytics: the replay keeps the how_story reach events and nothing else", () => {
  assert.match(componentSource, /const EXPERIENCE_TYPE = "how_story";/);
  assert.match(componentSource, /const eligible = state\.inView && document\.visibilityState !== "hidden";/);
  for (const source of sectionSources) assert.doesNotMatch(source, /EXPERIENCE_(VIEW|START|END)|MEMORY_OPEN|experience_(view|start|end)|memory_open|useExperienceAnalytics|how_play/);
  assert.doesNotMatch(engineSource, /trackEvent|analytics\.js/);
  assert.match(engineSource, /report\(\{ mode, inView: visible >= vh \/ 2, scene: beat \+ 1, atRest: r\.top <= 1 && r\.bottom >= vh - 1 \}\);/);
});

test("the end card's store links are unreachable until it is on screen", () => {
  assert.match(engineSource, /endcard\.inert = !on;/);
  assert.match(css, /\.tr-endcard \{[^}]*opacity: 0; pointer-events: none;/);
});

test("the CSS is scoped under .tr and has one dark token block", () => {
  const rules = css.replace(/\/\*[\s\S]*?\*\//g, "").match(/[^{}]+(?=\{)/g).map((s) => s.trim()).filter((s) => s && !s.startsWith("@"));
  for (const selector of rules.flatMap((rule) => rule.split(",").map((s) => s.trim()))) {
    assert.match(selector, /^(:root\[data-theme="dark"\] )?\.(tr|how-section)\b/, selector);
  }
  assert.equal(css.match(/:root\[data-theme="dark"\]/g).length, 1);
});

test("the photos the replay shows exist", async () => {
  for (const { key, cover } of REPLAY_PLACES) {
    await access(path.join(landing, `public/assets/photos/${cover}.webp`));
    for (let i = 1; i <= 5; i++) await access(path.join(landing, `public/assets/photos/${key}-0${i}.webp`));
  }
});

// Renders the real component with React on the server (analytics stubbed): the skeleton the engine builds into.
test("the server-rendered skeleton has the heading, the five steps and the replay's parts", async () => {
  const stub = {
    name: "analytics-stub",
    setup(api) {
      api.onResolve({ filter: /\/analytics\.js$/ }, () => ({ path: "analytics", namespace: "stub" }));
      api.onLoad({ filter: /.*/, namespace: "stub" }, () => ({ contents: "export const ANALYTICS_EVENTS = {}; export const trackEvent = () => false;", loader: "js" }));
    },
  };
  const result = await build({
    stdin: { contents: 'export { TravelReplay } from "./src/TravelReplay.jsx"; export { renderToStaticMarkup } from "react-dom/server"; export { createElement } from "react";', resolveDir: landing, loader: "js" },
    bundle: true, format: "esm", platform: "node", write: false, jsx: "automatic", logLevel: "silent", plugins: [stub],
    external: ["react", "react/*", "react-dom", "react-dom/*"],
  });
  const cache = path.join(landing, "node_modules/.cache");
  await mkdir(cache, { recursive: true });
  const dir = await mkdtemp(path.join(cache, "travel-replay-test-"));
  try {
    const file = path.join(dir, "bundle.mjs");
    await writeFile(file, result.outputFiles[0].text);
    const { TravelReplay, renderToStaticMarkup, createElement } = await import(pathToFileURL(file).href);
    const html = renderToStaticMarkup(createElement(TravelReplay, { storeActions: createElement("div", { className: "store-buttons" }) }));
    assert.match(html, /^<div class="tr"><h2 class="tr-title" id="how-title">사용 방법<\/h2><ol class="tr-steps">/);
    const items = [...html.matchAll(/<li data-tr-step=""><span class="tr-eyebrow">.*?<\/span><h3>([^<]+)<\/h3><p>([^<]+)<\/p><\/li>/g)].map(([, h, p]) => [h, p]);
    assert.deepEqual(items, BEATS.map(([, title, sub]) => [title, sub]));
    for (const hook of ["data-tr-track", "data-tr-stage", "data-tr-globe", "data-tr-markers", "data-tr-phone", "data-tr-mosaic", "data-tr-chip", "data-tr-cards", "data-tr-tally", "data-tr-endcard"]) assert.ok(html.includes(hook), hook);
    assert.equal(html.match(/data-tr-beat=""/g).length, 5);
    assert.match(html, /<div class="tr-caption" aria-hidden="true">/);
    assert.match(html, /<div class="store-buttons"><\/div><p class="tr-end-trust">/);
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});
