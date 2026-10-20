import assert from "node:assert/strict";
import { mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import path from "node:path";
import test from "node:test";
import { fileURLToPath, pathToFileURL } from "node:url";
import { build } from "esbuild";
import {
  FAQ_ITEMS, FREE_LINE, HERO_AUTO_PICK, HERO_AUTO_PICK_MS, HERO_COPY, HERO_LIBRARY, HERO_PLACES, REC_BEATS, ROLL_BEATS,
  SCENE_AT, SPRITE, STATIC_MAX_HEIGHT, STORY_SCENES, STORY_TRACKS, TRUST_LINE, beatOf, heroPickedCopy, sceneOf,
} from "../src/photoStory.js";

// 2026-10-18 (sketch v11): hero photo wall, then #how = #roll + #rec (six beats), trust + FAQ, download.
const landing = fileURLToPath(new URL("../", import.meta.url));
const read = (file) => readFile(new URL(file, import.meta.url), "utf8");
const app = await read("../src/App.jsx");
const main = await read("../src/main.jsx");
const css = await read("../src/photo-story.css");
const model = await read("../src/photoStory.js");
const hero = await read("../src/PhotoWallHero.jsx");
const story = await read("../src/PhotoStory.jsx");
const controller = await read("../src/photoStoryController.js");
const heroEngine = await read("../src/heroWallEngine.js");
const roll = await read("../src/rollStoryEngine.js");
const rec = await read("../src/recStoryEngine.js");
const sources = [hero, story, controller, heroEngine, roll, rec, await read("../src/storyCanvas.js")];
const stripComments = (source) => source.replace(/\/\*[\s\S]*?\*\//g, "").replace(/(^|[^:"'`])\/\/.*$/gm, "$1");

test("the hero is the sketch's example roll: 제주 / 일본 / 미국, 4.5 s auto-pick of 제주, its own words", () => {
  assert.equal(HERO_LIBRARY, 21474);
  assert.deepEqual(HERO_PLACES.map(({ key, name, count }) => [key, name, count]), [["jeju", "제주", 31], ["japan", "일본", 86], ["usa", "미국", 52]]);
  assert.equal(HERO_AUTO_PICK, "jeju");
  assert.equal(HERO_AUTO_PICK_MS, 4500);
  assert.deepEqual({ ...HERO_COPY }, {
    kicker: "예시 사진첩이에요 · 장소를 눌러 보세요",
    title: "사진 21,474장",
    sub: "장소를 누르면 그곳 사진만",
    diff: "기록 안 해도, 찍은 사진으로 여행 지도",
    ask: "어떻게 찾았는지 보기 ↓",
    made: "Mapmory 팀이 만들었어요",
  });
  assert.deepEqual(HERO_PLACES.map((p) => heroPickedCopy(p)), [
    { kicker: "제주 사진 31장을 불러왔어요", sub: "제주는 31장" },
    { kicker: "일본 사진 86장을 불러왔어요", sub: "일본은 86장" },
    { kicker: "미국 사진 52장을 불러왔어요", sub: "미국은 52장" },
  ]);
  // only cells of the chosen place stay lit: each place has photos in the sprite
  for (const { key } of HERO_PLACES) assert.ok(SPRITE.tiles.some((t) => t.place === key), key);
  assert.match(hero, /href="#roll"/);
});

test("chip taps keep the hero_demo_select contract; the auto-pick is never measured", () => {
  assert.match(hero, /if \(next && source === "chip"\) trackEvent\(ANALYTICS_EVENTS\.HERO_DEMO_SELECT, \{ experience_type: "hero_demo", demo_place: next \}\);/);
  assert.match(hero, /choose\(HERO_AUTO_PICK, "autoplay"\)/);
  assert.match(app, /<StoreButton placement="hero" platform="ios" label="iOS 앱 받기" \/>/);
  assert.match(app, /<StoreButton placement="hero" platform="android" label="Android 앱 받기" \/>/);
  assert.match(app, /<StoreButton placement="final" platform="ios"/);
  assert.match(app, /<StoreButton placement="final" platform="android"/);
  assert.doesNotMatch(app, /placement="replay"|PhotoFinderHero|TravelReplay|<LaunchWaitlistForm/);
});

test("#how is #roll then #rec: six beats with the sketch's words and the footnote on 3만 장", () => {
  assert.deepEqual(STORY_TRACKS.map(({ key, vh, starts }) => [key, vh, starts]), [["roll", 330, [0, 0.3, 0.66]], ["rec", 320, [0, 0.34, 0.58]]]);
  assert.equal(STORY_SCENES, 6);
  assert.deepEqual([...ROLL_BEATS, ...REC_BEATS].map(({ title }) => title.join(" ")), [
    "내 사진첩에서도 될까요?", "사진에 담긴 위치로, 3만 장도 30초 안에", "음식 사진도 되나요?",
    "그래서 어떻게 기록되나요?", "다녀온 곳이 지도에 칠해져요", "여행이 쌓일수록 지구본이 채워져요",
  ]);
  assert.equal(ROLL_BEATS[1].note, "* 팀 테스트 폰에서 사진 3만 장을 잰 시간이에요. 기기에 따라 달라질 수 있어요.");
  assert.ok(ROLL_BEATS[1].footnote);
  // phones show a hand-written kicker + the title only (2026-10-20, 티온: "간단하고 애자일하게")
  assert.deepEqual([...ROLL_BEATS, ...REC_BEATS].map(({ kicker }) => kicker), [
    "끝없이 넘기는 사진첩", "위치만 읽어요 · 내 폰 안에서", "흑돼지 사진도 제주로", "고른 사진만 올라가요", "국내는 시·도, 해외는 나라로", "지구본을 돌려 보세요",
  ]);
  assert.equal(ROLL_BEATS[1].noteShort, "* 팀 테스트 폰에서 잰 시간이에요.");
  assert.match(css, /@media \(max-width: 760px\) \{[^}]*\}[\s\S]*?\.ps-beat \.ps-long \{ display: none; \}/);
  // each QA scroll point sits inside its scene
  SCENE_AT.forEach(([trackIndex, p], i) => assert.equal(sceneOf(trackIndex, beatOf(STORY_TRACKS[trackIndex], p)), i + 1));
  assert.equal(beatOf(STORY_TRACKS[0], 0.299), 0);
  assert.equal(beatOf(STORY_TRACKS[0], 0.3), 1);
  assert.equal(beatOf(STORY_TRACKS[1], 1), 2);
});

test("trust + FAQ and download use the sketch's words exactly", () => {
  assert.equal(TRUST_LINE, "사진을 찾는 일은 폰 안에서만. 앱에는 직접 고른 사진만 올라가고, 나만 볼 수 있어요.");
  assert.equal(FREE_LINE, "무료로 받을 수 있어요");
  assert.deepEqual(FAQ_ITEMS.map(({ question }) => question), [
    "사진첩 사진을 전부 가져가나요?", "사진 접근을 전부 허용해야 하나요?", "위치 정보가 없는 사진은요?", "남긴 기록은 지울 수 있나요?",
    "가입해야 하나요?", "동영상도 찾아 주나요?", "iCloud에 올라간 사진도 되나요?", "무료인가요?",
  ]);
  assert.match(app, /\{FAQ_ITEMS\.map\(/);
  assert.match(app, /<p className="download-free">\{FREE_LINE\}<\/p>/);
  assert.match(app, /\{TRUST_LINE\}<\/p>/);
});

test("the copy only claims what the app does", () => {
  const copy = [model, roll, rec].join("\n");
  // no AI or scene recognition, no offline promise, no promise that deleting removes stored files
  for (const banned of [/AI|인공지능|자동 인식|장면 인식|사진 속 내용을 (읽|분석)/, /오프라인/, /서버에서도 (사진이 )?(완전히 )?삭제|파일까지 지워/, /동영상도 찾아요/]) {
    assert.doesNotMatch(copy, banned);
  }
  assert.match(model, /사진 속 내용이 아니라 찍은 곳으로 찾아요/);
  assert.match(model, /일부만 허용하면 앱이 전체 허용을 안내해요\. 직접 골라서 올리는 건 일부만 허용해도 돼요\./);
  assert.match(model, /나만 볼 수 있어요/);
});

test("the stories follow the page scroll and never steer it", () => {
  for (const source of sources.map(stripComments)) {
    assert.doesNotMatch(source, /addEventListener\("(wheel|touchstart|touchmove|touchend|keydown|keyup)"/);
    assert.doesNotMatch(source, /preventDefault|scrollTo|scrollBy|scrollIntoView|overflow = "hidden"/);
  }
  assert.doesNotMatch(css, /scroll-snap/);
  assert.match(controller, /window\.addEventListener\("scroll", onScroll, \{ passive: true \}\);/);
  // the globe in #rec turns by pointer drag only, and vertical touch scrolling stays with the page
  assert.match(css, /\.ps-rec \.ps-canvas \{ cursor: grab; touch-action: pan-y; \}/);
  assert.match(css, /\.wall-hero \{[^}]*touch-action: pan-y;/);
});

test("reduced motion and short viewports get the static version", () => {
  assert.equal(STATIC_MAX_HEIGHT, 500);
  assert.match(controller, /reduceQuery\.matches \|\| window\.innerHeight < STATIC_MAX_HEIGHT \? "static" : "scroll"/);
  assert.match(css, /\.ps\.is-static \.ps-beat \{ position: static; opacity: 1;/);
  assert.match(hero, /reducedMotion: prefersReducedMotion\(\)/);
  assert.match(css, /@media \(prefers-reduced-motion: reduce\)/);
});

test("destroy returns every listener, observer and frame, so a StrictMode double mount leaves one story", () => {
  for (const [add, remove] of [
    ['window.addEventListener("scroll", onScroll', 'window.removeEventListener("scroll", onScroll'],
    ['window.addEventListener("resize", onResize', 'window.removeEventListener("resize", onResize'],
    ['reduceQuery.addEventListener("change", onResize', 'reduceQuery.removeEventListener("change", onResize'],
  ]) {
    assert.ok(controller.includes(add), add);
    assert.ok(controller.includes(remove), remove);
  }
  assert.match(controller, /t\.engine\?\.destroy\(\)/);
  assert.match(controller, /if \(destroyed\) return;\s*const \[roll, rec\] = tracks;/);
  for (const source of [heroEngine, roll, rec]) {
    for (const call of ["cancelAnimationFrame(raf)", "io.disconnect()", "ro.disconnect()", "offTheme()"]) assert.ok(source.includes(call), call);
    for (const [, target, type, fn] of source.matchAll(/(\w+)\.addEventListener\("(\w+)", (\w+)/g)) {
      assert.ok(source.includes(`${target}.removeEventListener("${type}", ${fn}`) || source.includes(`${target}?.removeEventListener("${type}", ${fn}`), `${target} ${type}`);
    }
  }
  assert.match(hero, /return \(\) => \{ engine\.destroy\(\); engineRef\.current = null; \};/);
  assert.match(hero, /return \(\) => \{ window\.clearTimeout\(timer\); hero\.removeEventListener\("pointerdown", onPointerDown\); \};/);
});

test("geography and the engines are lazy chunks; the stories draw only while on screen", () => {
  assert.match(controller, /import\("\.\/rollStoryEngine\.js"\), import\("\.\/recStoryEngine\.js"\)/);
  assert.match(roll, /import\("\.\/data\/story-globe-dots\.json"\)/);
  assert.match(rec, /import\("world-atlas\/countries-110m\.json"\)/);
  assert.match(rec, /^import \{[^}]+\} from "d3-geo";/m); // inside the lazy rec chunk
  for (const source of [app, hero, story, controller, model]) assert.doesNotMatch(source, /^import [^;]*(d3-geo|topojson-client|world-atlas)/m);
  for (const source of [heroEngine, roll, rec]) assert.match(source, /new IntersectionObserver/);
  assert.match(main, /import "\.\/photo-story\.css";/);
  assert.doesNotMatch(main, /travel-replay|photo-finder-hero/);
});

test("analytics: the stories keep the how_story reach events and nothing else", () => {
  assert.match(story, /const EXPERIENCE_TYPE = "how_story";/);
  assert.match(story, /const eligible = state\.inView && document\.visibilityState !== "hidden";/);
  for (const source of [story, controller, roll, rec, heroEngine]) assert.doesNotMatch(source, /EXPERIENCE_(VIEW|START|END)|MEMORY_OPEN|experience_(view|start|end)|memory_open|useExperienceAnalytics|how_play/);
  for (const source of [controller, roll, rec, heroEngine]) assert.doesNotMatch(source, /trackEvent|analytics\.js/);
  assert.match(controller, /if \(pinned\) \{ scene = sceneOf\(i, beat\); atRest = true; \}/);
  assert.match(app, /<section className="how-section" id="how" aria-labelledby="how-title">\s*<PhotoStory \/>\s*<a className="how-experience-link" href="#experience"/);
});

test("the CSS is scoped to the hero and the stories, with one dark token block", () => {
  const rules = css.replace(/\/\*[\s\S]*?\*\//g, "").match(/[^{}]+(?=\{)/g).map((s) => s.trim()).filter((s) => s && !s.startsWith("@") && !/^(from|to|\d+%)$/.test(s));
  for (const selector of rules.flatMap((rule) => rule.split(",").map((s) => s.trim()))) {
    assert.match(selector, /^(:root\[data-theme="dark"\] )?\.(wall-hero|ps|how-section)\b/, selector);
  }
  assert.equal(css.match(/:root\[data-theme="dark"\]/g).length, 2); // one block, two selectors (.wall-hero, .ps)
  assert.match(css, /\.ps-roll \{ height: 330vh; height: 330svh;/);
  assert.match(css, /\.ps-rec \{ height: 320vh; height: 320svh; \}/);
  assert.match(css, /\.wall-hero-chips button \{ min-height: 44px;/);
});

// Renders the real components with React on the server (analytics stubbed): the skeleton the engines draw into.
test("the server-rendered hero and stories have real headings, the chips and the six beats", async () => {
  const stub = {
    name: "analytics-stub",
    setup(api) {
      api.onResolve({ filter: /\/analytics\.js$/ }, () => ({ path: "analytics", namespace: "stub" }));
      api.onLoad({ filter: /.*/, namespace: "stub" }, () => ({ contents: "export const ANALYTICS_EVENTS = {}; export const trackEvent = () => false;", loader: "js" }));
    },
  };
  const result = await build({
    stdin: { contents: 'export { PhotoStory } from "./src/PhotoStory.jsx"; export { PhotoWallHero } from "./src/PhotoWallHero.jsx"; export { renderToStaticMarkup } from "react-dom/server"; export { createElement } from "react";', resolveDir: landing, loader: "js" },
    bundle: true, format: "esm", platform: "node", write: false, jsx: "automatic", logLevel: "silent", plugins: [stub],
    external: ["react", "react/*", "react-dom", "react-dom/*"],
  });
  const cache = path.join(landing, "node_modules/.cache");
  await mkdir(cache, { recursive: true });
  const dir = await mkdtemp(path.join(cache, "photo-story-test-"));
  try {
    const file = path.join(dir, "bundle.mjs");
    await writeFile(file, result.outputFiles[0].text);
    const { PhotoStory, PhotoWallHero, renderToStaticMarkup, createElement } = await import(pathToFileURL(file).href);
    const html = renderToStaticMarkup(createElement(PhotoStory));
    assert.match(html, /^<div class="ps"><h2 class="sr-only" id="how-title">사용 방법<\/h2>/);
    assert.match(html, /<section class="ps-track ps-roll" id="roll" data-story-track="roll" aria-label="어떻게 찾았냐면">/);
    assert.match(html, /<section class="ps-track ps-rec" id="rec" data-story-track="rec" aria-label="어떻게 기록되나요">/);
    const headings = [...html.matchAll(/<h3>([^<]+)<br\/><em>([^<]+)<\/em>/g)].map(([, a, b]) => `${a} ${b}`);
    assert.deepEqual(headings, [...ROLL_BEATS, ...REC_BEATS].map(({ title }) => title.join(" ")));
    assert.equal(html.match(/data-story-step=""/g).length, 6);
    const heroHtml = renderToStaticMarkup(createElement(PhotoWallHero, { storeActions: createElement("div", { className: "store-buttons" }) }));
    assert.match(heroHtml, /<h1 id="hero-title">사진 21,474장<span>장소를 누르면 그곳 사진만<\/span><\/h1>/);
    assert.equal(heroHtml.match(/aria-pressed="false"/g).length, 3);
    assert.match(heroHtml, /<div class="wall-hero-foot"><div class="store-buttons"><\/div><span>Mapmory 팀이 만들었어요<\/span><\/div>/);
    assert.doesNotMatch(heroHtml, /wall-hero-ask/); // the "how did it find them" link appears only after a pick
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});
