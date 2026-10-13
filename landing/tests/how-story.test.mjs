import assert from "node:assert/strict";
import { mkdtemp, readFile, readdir, rm, stat, writeFile } from "node:fs/promises";
import { createRequire } from "node:module";
import { tmpdir } from "node:os";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";
import { build } from "esbuild";
import {
  APP_SCREEN_TEXT,
  HOW_STORY_BEATS,
  HOW_STORY_EYEBROW,
  HOW_STORY_LIBRARY_COUNT,
  HOW_STORY_MATCHES,
  HOW_STORY_METRICS,
  HOW_STORY_SCENE_COUNT,
  HOW_STORY_TEST_LAYOUT,
  HOW_STORY_TEXT,
  HOW_STORY_TIMING,
  HOW_STORY_TITLE,
  HOW_STORY_TITLE_LINES,
  HOW_STORY_TRACK,
  HOW_STORY_TRAVEL_UNITS,
  HOW_STORY_WINDOWS,
  HOW_STORY_BAND_COUNT_MS,
  appLoadingPercent,
  bandCountAt,
  formatHowStoryBandValue,
  getHowStoryBand,
  getHowStoryFrame,
  getHowStoryNodeStyle,
  getHowStoryPlan,
  getHowStoryProgress,
  getHowStoryScene,
  getHowStorySceneDescription,
  getHowStoryTapPlan,
  getHowStoryTapRange,
  isHowStoryHold,
  isInsideRings,
  linearProgressPlacement,
  loadingStep,
  mapPercent,
  resolveHowStoryMetrics,
  seg,
  stillProgress,
  waveEdge,
} from "../src/howStory.js";
import { createPassiveViewTracker, createSceneReachTracker, latestEntry } from "../src/howStoryAnalytics.js";
import { buildHowMapData, decodeSteps } from "../src/how/mapData.js";
import {
  APP_MAP_VIEW,
  HOW_STORY_APP_LINE_HEIGHT,
  HOW_STORY_APP_PROGRESS,
  HOW_STORY_GEOMETRY,
  HOW_STORY_MAP,
  HOW_STORY_PICKER,
  HOW_STORY_PRINTS,
  PICKER_SCROLL_DP,
  HOW_STORY_BREAKPOINTS,
  createHowStoryLayout,
  fitHowStoryPhone,
  getHowStoryMediaMode,
  getHowStoryMode,
  mapCameraBox,
  pickerCardRectsDp,
} from "../src/howStoryLayout.js";

const read = (file) => readFile(new URL(file, import.meta.url), "utf8");
const gyeongju = JSON.parse(await read("../src/data/gyeongju-boundary.json"));
const gyeongbuk = JSON.parse(await read("../src/data/gyeongbuk-districts.json"));
const howProvinces = JSON.parse(await read("../src/data/how-korea-provinces.json"));
const worldGlobe = JSON.parse(await read("../src/data/world-globe.json"));
const mosaic = JSON.parse(await read("../src/data/how-mosaic.json"));
const storyCss = await read("../src/how-story.css");
const screensCss = await read("../src/how-app-screens.css");
const componentSource = await read("../src/HowStory.jsx");
const hookSource = await read("../src/useHowStoryScroll.js");
const appSource = await read("../src/App.jsx");
const heroSource = await read("../src/PhotoFinderHero.jsx");
const analyticsSource = await read("../src/analytics.js");
const howDir = new URL("../src/how/", import.meta.url);
const howSources = Object.fromEntries(await Promise.all((await readdir(howDir)).map(async (file) => [file, await readFile(new URL(file, howDir), "utf8")])));

// A test-only override with every measured number filled in.
const SAMPLE_METRICS = Object.freeze({
  scanSeconds: 6.2,
  scanLibraryCount: 14213,
  scanSource: "테스트 기기 · 처음 찾을 때 · 2026년 11월 내부 측정",
  taps: 5,
  tapsSource: "테스트 빌드 · 2026년 11월 내부 측정",
});
const LAYOUTS = Object.entries(HOW_STORY_TEST_LAYOUT);
// Hold ranges from spec section 4 (p).
const SPEC_HOLDS = [[0.10465, 0.17442], [0.2907, 0.40698], [0.48512, 0.59302], [0.75116, 0.7907], [0.95814, 1]];

const holdBegin = (index) => {
  const [start, end] = HOW_STORY_WINDOWS[index];
  return start + HOW_STORY_BEATS[index].holdStart * (end - start);
};
const holdEnd = (index) => (index === HOW_STORY_SCENE_COUNT - 1 ? 1 : HOW_STORY_WINDOWS[index][1] - 1e-6);
const beatP = (index, t) => HOW_STORY_WINDOWS[index][0] + t * (HOW_STORY_WINDOWS[index][1] - HOW_STORY_WINDOWS[index][0]);
const sweep = (step = 0.0025) => Array.from({ length: Math.round(1 / step) + 1 }, (_, index) => Math.min(1, index * step));
const still = ({ nodes, texts, attrs, state }) => ({ nodes, texts, attrs, state });
const collectStrings = (value) => (typeof value === "string" ? [value] : Array.isArray(value) || (value && typeof value === "object") ? Object.values(value).flatMap(collectStrings) : []);
const descriptions = HOW_STORY_BEATS.map((_, index) => getHowStorySceneDescription(index));
const copyColumn = HOW_STORY_BEATS.flatMap((beat) => [beat.eyebrow, ...beat.headline, beat.sub]);
const chars = (value) => [...value].length;

test("progress clamps, guards a zero travel and is linear in between", () => {
  const base = { trackHeight: 2000, frameHeight: 600, stickyTop: 80 };
  assert.equal(getHowStoryProgress({ ...base, trackTop: 500 }), 0);
  assert.equal(getHowStoryProgress({ ...base, trackTop: -5000 }), 1);
  assert.equal(getHowStoryProgress({ ...base, trackTop: 80 - 700 }), 0.5);
  assert.equal(getHowStoryProgress({ trackTop: 79, trackHeight: 600, frameHeight: 600, stickyTop: 80 }), 1);
});

test("beat windows are cumulative units over 4.3, contiguous from 0 to 1, with the spec's holds", () => {
  assert.equal(HOW_STORY_SCENE_COUNT, 5);
  assert.equal(HOW_STORY_BEATS.length, HOW_STORY_SCENE_COUNT);
  assert.equal(HOW_STORY_BEATS.reduce((sum, { units }) => sum + units, 0), HOW_STORY_TRAVEL_UNITS);
  let units = 0;
  HOW_STORY_WINDOWS.forEach(([start, end], index) => {
    assert.ok(Math.abs(start - units / HOW_STORY_TRAVEL_UNITS) < 1e-9);
    units += HOW_STORY_BEATS[index].units;
    assert.ok(Math.abs(end - units / HOW_STORY_TRAVEL_UNITS) < 1e-9);
    if (index > 0) assert.equal(start, HOW_STORY_WINDOWS[index - 1][1]);
    assert.ok(Math.abs(holdBegin(index) - SPEC_HOLDS[index][0]) < 1e-5, `hold ${index + 1} start`);
    assert.ok(Math.abs(HOW_STORY_WINDOWS[index][1] - SPEC_HOLDS[index][1]) < 1e-5, `hold ${index + 1} end`);
    assert.equal(getHowStoryScene(stillProgress(index)).index, index);
    assert.ok(isHowStoryHold(stillProgress(index)), `still ${index + 1} sits in its hold`);
  });
  assert.equal(HOW_STORY_WINDOWS[0][0], 0);
  assert.equal(HOW_STORY_WINDOWS.at(-1)[1], 1);
  assert.equal(getHowStoryScene(-1).index, 0);
  assert.equal(getHowStoryScene(2).index, HOW_STORY_SCENE_COUNT - 1);
});

test("frames are pure: sweeping down then up gives identical frames at equal p, state included", () => {
  for (const [name, layout] of LAYOUTS) {
    const down = sweep().map((p) => getHowStoryFrame(p, layout));
    const up = sweep().reverse().map((p) => getHowStoryFrame(p, layout)).reverse();
    assert.deepEqual(up, down, name);
  }
});

test("every hold outlasts one wheel step, and the CSS track and frame match the constants", () => {
  const T = HOW_STORY_TRACK;
  const travelAt = (width, height) => (width >= T.wheelTrackMinWidth
    ? Math.min(Math.max(T.desktopMinPx, (T.desktopTravelSvh / 100) * height), T.desktopMaxPx)
    : (T.mobileTravelSvh / 100) * height);
  const holds = HOW_STORY_BEATS.map((_, index) => HOW_STORY_WINDOWS[index][1] - holdBegin(index));
  for (const width of [561, 901, 1280, 1440, 1920]) {
    for (const height of [560, 600, 800, 900, 1080]) {
      holds.forEach((hold, index) => assert.ok(hold * travelAt(width, height) >= 120, `${width}x${height} beat ${index + 1}: ${hold * travelAt(width, height)}px`));
    }
  }
  for (const [width, height] of [[360, 640], [360, 780], [390, 844]]) {
    holds.forEach((hold, index) => assert.ok(hold * travelAt(width, height) >= 100, `${width}x${height} beat ${index + 1}`));
  }
  const travel = `clamp(${T.desktopMinPx}px, ${T.desktopTravelSvh}svh, ${T.desktopMaxPx}px)`;
  assert.ok(storyCss.includes(`height: calc(${T.mobileTravelSvh + 100}svh - ${T.frameOffset.mobile}px);`));
  assert.ok(storyCss.includes(`top: ${T.stickyTop.mobile}px;\n  height: calc(100vh - ${T.frameOffset.mobile}px);\n  height: calc(100svh - ${T.frameOffset.mobile}px);`));
  assert.ok(storyCss.includes(`height: calc(${travel} + min(100svh - ${T.frameOffset.tablet}px, ${T.tabletMaxFrame}px));`));
  assert.ok(storyCss.includes(`top: ${T.stickyTop.tablet}px;`));
  assert.ok(storyCss.includes(`height: min(100svh - ${T.frameOffset.tablet}px, ${T.tabletMaxFrame}px);`));
  assert.ok(storyCss.includes(`height: calc(${travel} + min(100svh - ${T.frameOffset.desktop}px, ${T.desktopMaxFrame}px));`));
  assert.ok(storyCss.includes(`top: ${T.stickyTop.desktop}px;`));
  assert.ok(storyCss.includes(`height: min(100svh - ${T.frameOffset.desktop}px, ${T.desktopMaxFrame}px);`));
  assert.match(storyCss, /@media \(min-width: 561px\)/);
  assert.match(storyCss, /@media \(min-width: 901px\)/);
});

test("nothing moves during a hold, and beat 01 starts moving as soon as the frame sticks", () => {
  for (const [name, layout] of LAYOUTS) {
    HOW_STORY_BEATS.forEach((_, index) => {
      const begin = holdBegin(index);
      const reference = still(getHowStoryFrame(begin, layout));
      for (let step = 0; step <= 40; step += 1) {
        const p = begin + ((holdEnd(index) - begin) * step) / 40;
        assert.deepEqual(still(getHowStoryFrame(p, layout)), reference, `${name} beat ${index + 1} at ${p}`);
      }
    });
    const first = getHowStoryFrame(0, layout).state;
    const early = getHowStoryFrame(beatP(0, 0.1), layout).state;
    assert.notDeepEqual(early, first, "beat 01 changes state by t 0.10");
  }
});

test("copy rules: two-line headlines of at most 11 characters, one sub line ending in 요", () => {
  for (const beat of HOW_STORY_BEATS) {
    assert.equal(beat.headline.length, 2, beat.id);
    for (const line of beat.headline) assert.ok(chars(line) <= 11, line);
    assert.ok(chars(beat.sub) <= 30, beat.sub);
    assert.match(beat.sub, /요$/);
  }
  assert.deepEqual(HOW_STORY_BEATS.map(({ eyebrow }) => eyebrow), ["01", "02", "03", "04", "05"]);
  assert.equal(HOW_STORY_TITLE_LINES.length, 2);
  for (const line of HOW_STORY_TITLE_LINES) assert.ok(chars(line) <= 11, line);
  assert.equal(HOW_STORY_TITLE, "사진이 수천 장이어도 장소만 고르면 돼요");
  assert.equal(HOW_STORY_EYEBROW, "사용 방법");
});

test("no claim the client code does not back, on either app version, anywhere on the copy surface", () => {
  const banned = /위치·날짜|날짜로|자동으로 (날짜|제목)|\bAI\b|인공지능|인식|위치.{0,6}(지워|삭제|제거)|서버에서도.*삭제|올리는 중|업로드 중|\d+%|모두 선택|오사카|명소|맛집|사진첩.*보지 않|사진을 (다|전부) 보지 않|선택한 사진만 (읽|확인)|일부 (허용|접근)으로도 찾|위치(만)? (봐요|읽어요)|폰에만|위치 정보는 폰|지도로 이동|권한 없이|10장까지|원본 그대로|\/10장|1분|3만|초 만에|탭이면|번 탭|저장 중|스캔|분석|순식간|금방|빠르게|바로|몇 초|\d+\s*초|\d+\s*분|\d+\s*탭|탭\s*\d|아무것도|폰 안에만/;
  const faqLiterals = [...appSource.matchAll(/\{ question: "([^"]+)", answer: "([^"]+)" \}/g)].flatMap(([, question, answer]) => [question, answer]);
  assert.ok(faqLiterals.length >= 10);
  const trustLines = [...(appSource + heroSource).matchAll(/사진은 폰 안에서[^<"]*/g)].map(([line]) => line);
  assert.ok(trustLines.length >= 2);
  const strings = [
    HOW_STORY_TITLE,
    HOW_STORY_EYEBROW,
    ...copyColumn,
    ...descriptions,
    ...collectStrings(HOW_STORY_TEXT),
    ...faqLiterals,
    ...trustLines,
    appSource.match(/<h2 id="privacy-title">([^<]+)<\/h2>/)[1],
  ];
  for (const value of strings) assert.doesNotMatch(value, banned, value);
});

test("no illustrative number in the copy column, and the required lines are there", () => {
  for (const value of [...HOW_STORY_TITLE_LINES, ...HOW_STORY_BEATS.flatMap((beat) => [...beat.headline, beat.sub])]) assert.doesNotMatch(value, /\d/, value);
  // Screen readers hear the example's numbers only inside the example's frame (the visual pill is aria-hidden).
  for (const value of descriptions) {
    if (/\d/.test(value)) assert.match(value, /이 예시|예시 화면/, value);
  }
  // How the photos are chosen differs between the app versions (develop taps from none, main starts every found
  // photo checked), so no description says how.
  for (const value of descriptions) assert.doesNotMatch(value, /탭해서|탭하면|눌러서|체크/, value);
  const surface = [...copyColumn, ...collectStrings(HOW_STORY_TEXT)].join("\n");
  for (const required of ["고른 사진만 올라가요", "폰 안에서", "예시 화면"]) assert.ok(surface.includes(required), required);
  assert.equal(HOW_STORY_BEATS[1].sub, "사진 확인은 폰 안에서 해요");
  // The heading in App.jsx renders the eyebrow and the two lines.
  assert.match(appSource, /<p className="eyebrow how-eyebrow">\{HOW_STORY_EYEBROW\}<\/p>/);
  assert.match(appSource, /<h2 id="how-title">\{HOW_STORY_TITLE_LINES\.map\(/);
});

test("in-phone strings are app labels with no misleading claim, and only the app's own % strings", () => {
  const strings = collectStrings(APP_SCREEN_TEXT);
  for (const value of strings) {
    assert.doesNotMatch(value, /\bAI\b|인공지능|인식|자동으로 (날짜|제목)|위치.{0,6}(지워|삭제|제거)|저장 중|업로드 중|올리는 중/, value);
    assert.doesNotMatch(value, /1분|3만|초 만에|탭이면|번 탭|10장까지|원본 그대로|\/10장/, value);
  }
  assert.deepEqual(strings.filter((value) => value.includes("%")).sort(), ["{p}%", "{pct}% 채움"].sort());
  // Release gate (no test reads the client; see landing/AGENTS.md): the comment sits next to the screens.
  assert.match(howSources["AppScreens.jsx"], /Release gate: these screens are develop's record flow/);
  assert.equal(APP_SCREEN_TEXT.picker.max, 100);
});

test("the counter takes the app's 1% steps, front-loaded, and the hold reads the whole library", () => {
  const ks = [];
  for (let index = 0; index <= 200000; index += 1) ks.push(loadingStep(index / 200000).k);
  assert.equal(new Set(ks).size, 101);
  ks.forEach((k, index) => { if (index) assert.ok(k >= ks[index - 1]); });
  for (let k = 0; k <= 100; k += 1) assert.ok(ks.includes(k));
  // Every count drawn with a percent is a pair the app draws: develop's percent is processed * 100 / total, floored.
  for (let index = 0; index <= 2000; index += 1) {
    const { k, processed } = loadingStep(index / 2000);
    assert.equal(Math.floor((processed * 100) / HOW_STORY_LIBRARY_COUNT), k, `k=${k} processed=${processed}`);
    assert.equal(appLoadingPercent(processed), k);
    assert.ok(processed < HOW_STORY_LIBRARY_COUNT || k === 100);
  }
  assert.equal(loadingStep(0.12).k, 0);
  assert.ok(loadingStep(0.3).k >= 85);
  assert.equal(loadingStep(0.48).k, 100);
  assert.equal(loadingStep(0.9).k, 100);
  for (const [name, layout] of LAYOUTS) {
    // The hold reads the whole library on the overlay; the in-phone counter left with the screen.
    const hold = getHowStoryFrame(beatP(1, 0.75), layout);
    assert.equal(hold.texts.overlayValue, "21,474장", name);
    assert.equal(hold.texts.loadingCount, undefined, name);
    assert.equal(hold.nodes.loadingFill, undefined, name);
    // While the screen shows, its counter, percent and bar run on the same step; once it has gone, nothing is
    // written to it. The count starts before the screen goes.
    let counted = 0;
    for (let index = 0; index <= 2000; index += 1) {
      const t = index / 2000;
      const frame = getHowStoryFrame(beatP(1, t), layout);
      const { k, processed } = loadingStep(t);
      if (frame.nodes.screenFade.o > 0) {
        assert.equal(frame.texts.loadingCount, `${processed} / 21474장`, `${name} t=${t}`);
        assert.equal(frame.texts.loadingPct, `${k}%`, `${name} t=${t}`);
        assert.ok(frame.nodes.loadingFill && frame.nodes.loadingTrack, `${name} t=${t}`);
        if (k > 0) counted += 1;
      } else if (t > 0.2) {
        for (const key of ["loadingCount", "loadingPct"]) assert.equal(frame.texts[key], undefined, `${name} t=${t}`);
        for (const key of ["loadingFill", "loadingTrack"]) assert.equal(frame.nodes[key], undefined, `${name} t=${t}`);
      }
      if (frame.texts.overlayValue !== undefined) assert.equal(frame.texts.overlayValue, `${processed.toLocaleString("en-US")}장`, `${name} t=${t}`);
    }
    assert.ok(counted > 0, name);
  }
  // At 100% develop stops showing the count (it prepares the previews instead), so the phone never draws
  // "21474 / 21474장": the in-phone counter is written only while the screen shows, well before the count ends.
  for (const [name, layout] of LAYOUTS) {
    for (const p of sweep(0.0005)) {
      const { texts } = getHowStoryFrame(p, layout);
      if (texts.loadingCount !== undefined) assert.notEqual(texts.loadingCount, "21474 / 21474장", `${name} p=${p}`);
    }
  }
});

test("the pin wave crosses the visible band with the counter, and every found photo pops while the library shows", () => {
  for (const [name, layout] of LAYOUTS) {
    const H = layout.window.h;
    const pops = layout.cells.map(({ popAt }) => popAt);
    assert.deepEqual([...pops].sort((a, b) => a - b), pops, `${name}: pins pop in cell order`);
    for (const { stageY, size } of layout.cells) {
      // Every found photo sits inside the visible stage.
      assert.ok(stageY >= 0 && stageY + size <= layout.stageHeight, name);
    }
    let previousPins = 0;
    for (let index = 0; index <= 4000; index += 1) {
      const t = index / 4000;
      const frame = getHowStoryFrame(beatP(1, t), layout);
      const { k } = loadingStep(t);
      const edge = waveEdge(k, layout);
      if (k > 0) assert.ok(Math.abs(edge - (layout.window.vTop + (k / 100) * (layout.window.vBottom - layout.window.vTop))) < 1e-9);
      assert.ok(Math.abs(frame.nodes.pinsReveal.ty - -H * (1 - edge)) < 0.01, `${name} t ${t}`);
      assert.ok(Math.abs(frame.nodes.pinsInner.ty - H * (1 - edge)) < 0.01, `${name} t ${t}`);
      if (t < 0.12) assert.equal(frame.state.pins, 0, `${name}: no pin before the count starts`);
      if (frame.state.pins > previousPins) assert.ok(frame.nodes.mosaicWindow.o >= 0.9, `${name}: pop at t ${t} with the library at ${frame.nodes.mosaicWindow.o}`);
      previousPins = frame.state.pins;
    }
    assert.equal(getHowStoryFrame(holdBegin(1), layout).state.pins, 4, name);
  }
});

test("nothing suggests 'inside the phone' during the place search", () => {
  for (const [name, layout] of LAYOUTS) {
    for (let index = 0; index <= 400; index += 1) {
      const frame = getHowStoryFrame(beatP(0, index / 400) - (index === 400 ? 1e-9 : 0), layout);
      assert.equal(frame.nodes.ground.o, 0, name);
      assert.equal(frame.nodes.mosaicWindow.o, 0, name);
      assert.equal(frame.nodes.overlay.o, 0, name);
    }
  }
});

test("the number lands as a new number at beat 03, over a dimmed (not removed) library", () => {
  for (const [name, layout] of LAYOUTS) {
    assert.equal(getHowStoryFrame(beatP(2, 0.13), layout).state.found, 0, name);
    const landed = getHowStoryFrame(beatP(2, 0.14), layout);
    assert.equal(landed.state.found, 1, name);
    const hold = getHowStoryFrame(holdBegin(2), layout);
    // The library stays at 10-20% brightness: a ground-coloured cover at 80-90% over it.
    assert.ok(hold.nodes.mosaicDim.o > 0.8 && hold.nodes.mosaicDim.o < 0.9, name);
    assert.equal(hold.nodes.pinsReveal.o, 0, name);
    // The four tiles sit in the 2x2 cluster at full opacity.
    layout.cluster.forEach((rect, index) => {
      const node = hold.nodes[`match${index}`];
      assert.equal(node.o, 1, name);
      assert.ok(Math.abs(node.tx - rect.x) < 0.01 && Math.abs(node.ty - rect.y) < 0.01, `${name} tile ${index}`);
      assert.ok(Math.abs(node.s * layout.nodeBase - rect.size) < 0.01, `${name} tile ${index} size`);
    });
  }
});

test("the found photos are inside the app's 경주시 boundary; three are picked and D is not", () => {
  assert.equal(HOW_STORY_MATCHES.length, 4);
  for (const { lat, lng, id } of HOW_STORY_MATCHES) assert.ok(isInsideRings(lng, lat, gyeongju.rings), id);
  assert.deepEqual(HOW_STORY_MATCHES.filter(({ picked }) => picked).map(({ id }) => id), ["A", "B", "C"]);
  assert.equal(HOW_STORY_MATCHES.find(({ id }) => id === "D").picked, false);
  for (const { src } of HOW_STORY_MATCHES) assert.match(src, /^\/assets\/photos\/gyeongbuk-0[1-4]\.webp$/);
});

test("map numbers follow the app's formula and the drill map has develop's 22 districts", () => {
  assert.equal(mapPercent(1, 22), 5);
  assert.equal(mapPercent(1, 17), 6);
  assert.equal(mapPercent(1, 195), 1);
  assert.equal(gyeongbuk.districts.length, 22);
  assert.ok(gyeongbuk.districts.some(({ code }) => code === "47130"));
});

const near = (a, b, label, epsilon = 0.01) => assert.ok(Math.abs(a - b) < epsilon, `${label}: ${a} vs ${b}`);
const rectOf = (node, layout) => ({ x: node.tx, y: node.ty, size: node.s * layout.nodeBase });
const nearRect = (node, rect, layout, label) => {
  const placed = rectOf(node, layout);
  near(placed.x, rect.x, `${label} x`);
  near(placed.y, rect.y, `${label} y`);
  near(placed.size, rect.size, `${label} size`);
};
const beatSweep = (index, steps = 200) => Array.from({ length: steps + 1 }, (_, step) => beatP(index, step / steps) - (step === steps && index < 4 ? 1e-9 : 0));

test("the picker's cards sit where develop's list puts them, from the app's line height, scrolled by 48dp", () => {
  // The JS geometry and the CSS read the same tokens.
  assert.match(screensCss, new RegExp(`--app-line-height: ${HOW_STORY_APP_LINE_HEIGHT};`));
  assert.match(screensCss, new RegExp(`--app-map-status-dp: ${HOW_STORY_MAP.statusDp};`));
  assert.equal(PICKER_SCROLL_DP, 48);
  assert.match(screensCss, new RegExp(`\\.how-picker-list \\{[^}]*transform: translateY\\(calc\\(-${PICKER_SCROLL_DP} \\* var\\(--dp\\)\\)\\);`));
  assert.match(screensCss, new RegExp(`grid-template-columns: repeat\\(2, calc\\(${HOW_STORY_PICKER.card} \\* var\\(--dp\\)\\)\\); gap: calc\\(${HOW_STORY_PICKER.gap} \\* var\\(--dp\\)\\);`));
  const rects = pickerCardRectsDp();
  // Two 153dp columns from the 20dp start padding, 10dp apart; row 2 is 10dp below row 1.
  assert.equal(HOW_STORY_PICKER.card, (360 - HOW_STORY_PICKER.padStart - HOW_STORY_PICKER.padEnd - HOW_STORY_PICKER.gap) / 2);
  assert.deepEqual(rects.map(({ x }) => x), [20, 183, 20, 183]);
  near(rects[2].y - rects[0].y, 163, "row gap");
  // Both rows sit in the top 87% of the screen, under the 89dp top bar.
  assert.ok(rects[0].y > 89 && (rects[3].y + rects[3].size) / 780 <= 0.87, `${rects[3].y}`);
  for (const [name, layout] of LAYOUTS) {
    rects.forEach((rect, index) => {
      const card = layout.cards[index];
      near(card.x, layout.screen.x + rect.x * layout.dp, `${name} card ${index} x`);
      near(card.y, layout.screen.y + rect.y * layout.dp, `${name} card ${index} y`);
      near(card.size, rect.size * layout.dp, `${name} card ${index} size`);
    });
    HOW_STORY_PRINTS.slots.forEach(({ dx, dy, r }, index) => {
      const print = layout.prints[index];
      near(print.cx, layout.screen.x + (HOW_STORY_PRINTS.centre[0] + dx) * layout.dp, `${name} print ${index} x`);
      near(print.cy, layout.screen.y + (HOW_STORY_PRINTS.centre[1] + dy) * layout.dp, `${name} print ${index} y`);
      near(print.size, HOW_STORY_PICKER.card * HOW_STORY_PRINTS.size * layout.dp, `${name} print ${index} size`);
      assert.equal(print.r, r);
    });
    // The flying photos are drawn at their largest size and only scaled down.
    assert.ok(layout.nodeBase >= Math.max(layout.cards[0].size, ...layout.cluster.map(({ size }) => size)), name);
  }
  // The print stack (each print's turned outer box, white border included: 12 card-dp at half a card) stays
  // between the header's title row and the date row with its 이 날짜 사진 선택 button, left of the 3장 선택 count,
  // so it never covers what the hold reads (round-1 V2: a copy touched the date button at 360x640).
  const k = HOW_STORY_PICKER;
  const titleBottom = k.listTop + k.padTop - PICKER_SCROLL_DP + 12 * HOW_STORY_APP_LINE_HEIGHT + 14 + 25 * HOW_STORY_APP_LINE_HEIGHT;
  const dateTop = rects[0].y - k.firstRowGap - k.dateGap - k.smallButton;
  const outer = k.card * HOW_STORY_PRINTS.size + 2 * 12 * HOW_STORY_PRINTS.size;
  for (const { dx, dy, r } of HOW_STORY_PRINTS.slots) {
    const turn = (Math.abs(r) * Math.PI) / 180;
    const half = (outer * (Math.cos(turn) + Math.sin(turn))) / 2;
    const [cx, cy] = [HOW_STORY_PRINTS.centre[0] + dx, HOW_STORY_PRINTS.centre[1] + dy];
    assert.ok(cy - half > titleBottom && cy + half < dateTop, `print at ${cy}: ${cy - half}..${cy + half} between ${titleBottom} and ${dateTop}`);
    assert.ok(cx - half > 0 && cx + half < 360 - k.padEnd - 50, `print at ${cx}`);
  }
});

test("scrubbed moves are calm: each big move takes at least 100px of scroll from 10% to 90%, with no jump", () => {
  // Travel in px: phones scroll 430svh (390x844: one unit = 844px); wheel devices clamp(3200px, 430svh, 3800px), so
  // 1280x800 has 800px a unit and the shortest wheel track (any window up to 744px tall) 3200 / 4.3 = 744px.
  const sizes = [["mobile", 844], ["desktop", 800], ["desktop", HOW_STORY_TRACK.desktopMinPx / HOW_STORY_TRAVEL_UNITS]];
  const view = APP_MAP_VIEW;
  // The progress bars and the pin wave's reveal follow the counter (front-loaded on purpose, spec beat 02, and the
  // wave's first step only uncovers the window above the stage), so they are not moves.
  const counterDriven = new Set(["loadingFill", "loadingTrack", "overlayBarFill", "pinsReveal", "pinsInner"]);
  for (const [name, unit] of sizes) {
    const layout = HOW_STORY_TEST_LAYOUT[name];
    const label = `${name} (${Math.round(unit)}px a unit)`;
    const travel = unit * HOW_STORY_TRAVEL_UNITS;
    const frames = [];
    for (let px = 0; px <= Math.ceil(travel); px += 1) frames.push([px, getHowStoryFrame(Math.min(1, px / travel), layout)]);
    const within = (beat) => frames.filter(([px]) => px >= HOW_STORY_WINDOWS[beat][0] * travel && px < HOW_STORY_WINDOWS[beat][1] * travel);
    const lift = layout.cells[3].lift;
    const cluster = layout.cluster[3];
    const moves = [
      ["inward camera", 1, (f) => (f.nodes.phoneCamera.s - 1) / (layout.kIn - 1)],
      ["outward camera", 3, (f) => 1 - (f.nodes.phoneCamera.s - 1) / (layout.kIn - 1)],
      ["tile flight", 2, (f) => (f.nodes.match3.tx - lift.x) / (cluster.x - lift.x)],
      ["map camera", 4, (f) => Math.log(Number(f.attrs.mapSvg.viewBox.split(" ")[2]) / view.drillBox[2]) / Math.log(view.koreaBox[2] / view.drillBox[2])],
      // Saving: each copy lifts off its card into the print stack (print C travels about 330px at 390x844).
      ...[0, 1, 2].map((index) => {
        const card = layout.cards[index];
        const from = card.y + card.size / 2;
        return [`print ${"ABC"[index]} lift`, 3, (f) => (f.nodes[`copy${index}`].ty + layout.nodeBase / 2 - from) / (layout.prints[index].cy - from)];
      }),
    ];
    for (const [move, beat, measure] of moves) {
      const samples = within(beat).map(([px, frame]) => [px, measure(frame)]);
      const at = (value) => samples.find(([, f]) => f >= value)[0];
      const span = at(0.9) - at(0.1);
      assert.ok(span >= 100, `${label} ${move}: 10-90% in ${span}px`);
      // A 7px step (a fine trackpad tick) never moves it more than 7%.
      for (let index = 7; index < samples.length; index += 1) {
        const step = Math.abs(samples[index][1] - samples[index - 7][1]);
        assert.ok(step <= 0.07, `${label} ${move}: ${(step * 100).toFixed(1)}% in 7px at ${samples[index][0]}px`);
      }
    }
    // Any other scrubbed translation of 60px or more is held to the same rule, so a new big move cannot slip in.
    const series = new Map();
    for (const [px, frame] of frames) {
      for (const [key, node] of Object.entries(frame.nodes)) {
        if (counterDriven.has(key)) continue;
        for (const prop of ["tx", "ty"]) {
          if (node[prop] === undefined) continue;
          const id = `${key}.${prop}`;
          if (!series.has(id)) series.set(id, []);
          series.get(id).push([px, node[prop]]);
        }
      }
    }
    for (const [id, values] of series) {
      // Runs: stretches of change between rests of 3px or more.
      let index = 0;
      while (index < values.length - 1) {
        if (values[index + 1][1] === values[index][1]) {
          index += 1;
          continue;
        }
        let next = index + 1;
        let rest = 0;
        while (next < values.length - 1 && rest < 3) {
          rest = values[next + 1][1] === values[next][1] ? rest + 1 : 0;
          next += 1;
        }
        const run = values.slice(index, next - rest + 1);
        const [first, last] = [run[0][1], run.at(-1)[1]];
        if (Math.abs(last - first) >= 60) {
          const at = (value) => run.find(([, v]) => (v - first) / (last - first) >= value)[0];
          assert.ok(at(0.9) - at(0.1) >= 100, `${label} ${id}: ${Math.abs(last - first).toFixed(0)}px of travel in ${at(0.9) - at(0.1)}px of scroll from ${run[0][0]}px`);
        }
        index = next;
      }
    }
  }
  // Whole screens and the print stack are not scrubbed: the push to the loading screen, the picker and map tabs and the
  // stack rising away play in time on a state (how-story.css), like the copy swap.
  for (const [name, layout] of LAYOUTS) {
    for (const p of sweep(0.01)) {
      const { nodes } = getHowStoryFrame(p, layout);
      for (const key of ["screenImport", "screenLoading", "screenPicker", "screenMap"]) assert.equal(nodes[key], undefined, `${name} ${key} at ${p}`);
    }
  }
  const css = storyCss.replace(/\/\*[\s\S]*?\*\//g, "");
  assert.match(css, /\.how-stage :is\(\.how-app-import, \.how-app-loading\) \{ transition: transform 480ms var\(--ease-in-out\), opacity 480ms var\(--ease-in-out\); \}/);
  assert.match(css, /\.how-stage:not\(\[data-scene="1"\]\) \.how-app-import \{ opacity: 0; transform: translateX\(-30%\); \}/);
  assert.match(css, /\.how-stage:not\(\[data-scene="1"\]\) \.how-app-loading \{ transform: none; \}/);
});

test("a found photo leaves the library with its ring and pin, from exactly its lifted cell, in one frame", () => {
  for (const [name, layout] of LAYOUTS) {
    layout.cells.forEach((cell, index) => {
      const { start: first, stagger, length } = HOW_STORY_TIMING.cluster;
      const start = first + stagger * index;
      const before = getHowStoryFrame(beatP(2, start - 0.001), layout).nodes;
      const after = getHowStoryFrame(beatP(2, start + 1e-6), layout).nodes;
      assert.deepEqual([before[`matchCell${index}`].o, before[`matchMark${index}`].o, before[`match${index}`].o], [1, 1, 0], `${name} ${index} before`);
      assert.deepEqual([after[`matchCell${index}`].o, after[`matchMark${index}`].o, after[`match${index}`].o, after[`matchRing${index}`].o], [0, 0, 1, 1], `${name} ${index} after`);
      nearRect(after[`match${index}`], cell.lift, layout, `${name} ${index} starts on its lifted cell`);
      // The ring fades mid flight and is gone well before the tile settles.
      assert.equal(getHowStoryFrame(beatP(2, start + length * 0.7), layout).nodes[`matchRing${index}`].o, 0, `${name} ${index}`);
    });
    // Its ring and pin are drawn in the node at the cell's own sizes when the flight starts.
    near(layout.matchK * layout.cells[0].lift.size, layout.nodeBase, `${name} match-k`, 0.05);
  }
  assert.match(storyCss, /\.how-match-mark \{[^}]*box-shadow: 0 0 0 calc\(3px \* var\(--match-k, 4\)\) var\(--how-match\), 0 0 0 calc\(7px \* var\(--match-k, 4\)\)/);
  assert.match(hookSource, /set\("--match-k", String\(layout\.matchK\)\);/);
});

test("beat 04: the found photos become the picker's cards and never leave; only the three picked get a copy", () => {
  for (const [name, layout] of LAYOUTS) {
    for (const p of beatSweep(3)) {
      const frame = getHowStoryFrame(p, layout);
      // Exactly three copies (A, B, C); card D has none.
      assert.deepEqual(Object.keys(frame.nodes).filter((key) => /^copy\d+$/.test(key)), ["copy0", "copy1", "copy2"], name);
      // The originals stay on screen through the whole beat.
      for (let index = 0; index < 4; index += 1) assert.equal(frame.nodes[`match${index}`].o, 1, `${name} card ${index} at ${p}`);
    }
    // While the camera is still pulling out, each tile that has landed sits on its card where the camera shows it.
    const tile0Lands = HOW_STORY_TIMING.cards.start + HOW_STORY_TIMING.cards.length;
    assert.ok(tile0Lands < HOW_STORY_TIMING.outward[1], `${tile0Lands}`);
    const moving = getHowStoryFrame(beatP(3, (tile0Lands + HOW_STORY_TIMING.outward[1]) / 2), layout);
    const camera = moving.nodes.phoneCamera;
    assert.ok(camera.s > 1.001, `${name} camera still moving: ${camera.s}`);
    const centre = { x: layout.phone.x + layout.phone.w / 2, y: layout.phone.y + layout.phone.h / 2 };
    const card0 = layout.cards[0];
    nearRect(moving.nodes.match0, { x: centre.x + (card0.x - centre.x) * camera.s + camera.tx, y: centre.y + (card0.y - centre.y) * camera.s + camera.ty, size: card0.size * camera.s }, layout, `${name} tile 0 on its moving card`);
    // Once the chrome comes on (t .315) the tiles are the cards: on the card rects, with the border and preview button on.
    const landed = getHowStoryFrame(beatP(3, HOW_STORY_TIMING.chrome), layout);
    layout.cards.forEach((rect, index) => {
      nearRect(landed.nodes[`match${index}`], rect, layout, `${name} card ${index}`);
      assert.equal(landed.nodes[`matchChrome${index}`].o, 1, name);
      assert.equal(landed.nodes[`matchShadow${index}`].o, 0, name);
    });
    // Before the camera comes out, no card chrome and no copy shows.
    const start = getHowStoryFrame(beatP(3, 0.2), layout);
    for (let index = 0; index < 3; index += 1) assert.equal(start.nodes[`copy${index}`].o, 0, name);
    for (let index = 0; index < 4; index += 1) assert.equal(start.nodes[`matchChrome${index}`].o, 0, name);
    // Taps: A, B, C in turn once the cards are on, then 기록하기 (the spinner); then the copies lift, and the hold shows
    // them as the print stack.
    const sel = (t) => getHowStoryFrame(beatP(3, t), layout).state;
    assert.deepEqual([0.36, 0.38, 0.43, 0.48, 0.5, 0.52].map((t) => [sel(t).sel, sel(t).save]), [[0, 0], [1, 0], [2, 0], [3, 0], [3, 0], [3, 1]], name);
    const taps = getHowStoryTapPlan().filter(({ scene }) => scene === 3);
    assert.ok(taps[0].t > HOW_STORY_TIMING.chrome && taps.at(-1).t < HOW_STORY_TIMING.lift[0], taps.map(({ t }) => t).join());
    // The copies start exactly on their cards (turned 0) and end as the print stack, fully opaque, in the hold.
    const saved = getHowStoryFrame(beatP(3, HOW_STORY_TIMING.lift[0]), layout);
    const hold = getHowStoryFrame(holdBegin(3), layout);
    for (let index = 0; index < 3; index += 1) {
      const card = layout.cards[index];
      const at = saved.nodes[`copy${index}`];
      near(at.tx + layout.nodeBase / 2, card.x + card.size / 2, `${name} copy ${index} starts on its card x`);
      near(at.ty + layout.nodeBase / 2, card.y + card.size / 2, `${name} copy ${index} starts on its card y`);
      near(at.s * layout.nodeBase, card.size, `${name} copy ${index} starts at the card's size`);
      assert.equal(at.r, 0, name);
      const print = layout.prints[index];
      const copy = hold.nodes[`copy${index}`];
      near(copy.tx + layout.nodeBase / 2, print.cx, `${name} print ${index} x`);
      near(copy.ty + layout.nodeBase / 2, print.cy, `${name} print ${index} y`);
      near(copy.s * layout.nodeBase, print.size, `${name} print ${index} size`, 0.05);
      assert.equal(copy.r, print.r, name);
      assert.equal(copy.o, 1, name);
    }
  }
  // The rings drawn are the picks and the save in the tap plan: one per picked photo, none on D.
  const picks = getHowStoryTapPlan().filter(({ id }) => id.startsWith("pick-"));
  assert.equal(picks.length, HOW_STORY_MATCHES.filter(({ picked }) => picked).length);
});

test("beat 05: the prints leave and the picker goes before develop's drill map comes; 경주시 fills, then the camera pulls back to Korea and the world", () => {
  const T = HOW_STORY_TIMING;
  // The constants are the data's: develop's drill bounds (울릉도 and 독도 included) and the bounds of develop's
  // provinces (GeneratedKoreaMapData, also with 울릉도 and 독도).
  assert.deepEqual(HOW_STORY_MAP.drillBounds, gyeongbuk.bounds);
  assert.deepEqual(HOW_STORY_MAP.koreaBounds, howProvinces.bounds);
  assert.equal(howProvinces.provinces.length, 17);
  assert.ok(worldGlobe.countries.some(({ code }) => code === "KR"));
  const view = APP_MAP_VIEW;
  // Both framings keep the map area's aspect, and the drill one is a zoom into the Korea one.
  near(view.drillBox[2] / view.drillBox[3], view.area.width / view.area.height, "drill aspect", 1e-6);
  assert.deepEqual(view.koreaBox, [0, 0, view.area.width, view.area.height]);
  assert.ok(view.drillBox[2] < view.koreaBox[2]);
  // The camera: a zoom about one fixed point, the width moving geometrically.
  const half = mapCameraBox(view.drillBox, view.koreaBox, 0.5);
  near(half[2], Math.sqrt(view.drillBox[2] * view.koreaBox[2]), "geometric width", 1e-6);
  const [fx, fy] = [0, 1].map((axis) => (view.drillBox[axis] * view.koreaBox[2] - view.koreaBox[axis] * view.drillBox[2]) / (view.koreaBox[2] - view.drillBox[2]));
  for (const u of [0.25, 0.5, 0.75]) {
    const box = mapCameraBox(view.drillBox, view.koreaBox, u);
    near((fx - box[0]) / box[2], (fx - view.drillBox[0]) / view.drillBox[2], `fixed x at ${u}`, 1e-9);
    near((fy - box[1]) / box[3], (fy - view.drillBox[1]) / view.drillBox[3], `fixed y at ${u}`, 1e-9);
  }
  const toBox = (frame) => frame.attrs.mapSvg.viewBox.split(" ").map(Number);
  for (const [name, layout] of LAYOUTS) {
    let previousWidth = 0;
    for (const p of beatSweep(4)) {
      const frame = getHowStoryFrame(p, layout);
      const { t } = getHowStoryScene(p);
      const box = toBox(frame);
      if (t < T.mapCamera[0]) box.forEach((value, index) => near(value, view.drillBox[index], `${name} drill box`));
      if (t >= T.mapCamera[1]) box.forEach((value, index) => near(value, view.koreaBox[index], `${name} korea box`));
      assert.ok(box[2] >= previousWidth - 0.01, `${name} the camera only pulls back`);
      previousWidth = box[2];
      // The tabs: on one threshold the picker goes and the map comes (in time, the map after the picker's fade); 경주시
      // fills only once it is in.
      assert.equal(frame.state.picker, t < T.mapIn ? 1 : 0, `${name} t ${t}`);
      assert.equal(frame.state.map, t < T.mapIn ? "none" : t < T.fill ? "drill0" : t < T.world ? "drill1" : "world", `${name} t ${t}`);
      // Saving and the map never share a frame: the map tab comes only once the picker tab and its photos have gone
      // (round-1 H3; after a save both app versions open the record list, not the map). And no scroll position rests on
      // an empty phone: exactly one of the two tabs is on at every p (round-3 V3).
      assert.notEqual(frame.state.picker === 1, frame.state.map !== "none", `${name} one tab at t ${t}`);
      for (let index = 0; index < 4; index += 1) assert.equal(frame.nodes[`match${index}`].o, frame.state.picker, `${name} photo ${index} at t ${t}`);
      // The frame holds the prints in the stack; the stack itself rises away in time (below).
      for (let index = 0; index < 3; index += 1) {
        const copy = frame.nodes[`copy${index}`];
        near(copy.ty + layout.nodeBase / 2, layout.prints[index].cy, `${name} print ${index} at t ${t}`);
        assert.equal(copy.o, 1, name);
      }
    }
    // Drill, then Korea: district lines and the drill card hand over to the province outlines and the Korea card.
    const before = getHowStoryFrame(beatP(4, T.mapCamera[0] - 0.01), layout).nodes;
    const after = getHowStoryFrame(beatP(4, T.mapCamera[1] + 0.01), layout).nodes;
    assert.deepEqual(["mapDistrictLines", "mapLabels", "mapFillDistrict", "mapCardDrill"].map((key) => [before[key].o, after[key].o]), [[1, 0], [1, 0], [1, 0], [1, 0]], name);
    assert.deepEqual(["mapProvinces", "mapFillProvince", "mapCardKorea"].map((key) => [before[key].o, after[key].o]), [[0, 1], [0, 1], [0, 1]], name);
  }
  // The moments are apart in scroll even on the shortest wheel track (one unit is 3200px / 4.3 = 744px there): the
  // stack rises away as the beat opens, then the picker goes and the map comes (one threshold), then 경주시 fills.
  const beatPx = HOW_STORY_BEATS[4].units * (HOW_STORY_TRACK.desktopMinPx / HOW_STORY_TRAVEL_UNITS);
  assert.ok(T.mapIn * beatPx >= 60 && (T.fill - T.mapIn) * beatPx >= 100, `${beatPx}`);
  assert.equal(T.pickerOut, undefined, "one threshold for both tabs");
  assert.ok(T.fill < T.mapCamera[0] && T.mapCamera[1] < T.world, "fill, camera, world in turn");
  // In time: the print stack rises off the stage as beat 05 opens, fading over the rise's second half, and is hidden
  // only once both have ended, whatever the map tab does meanwhile (round-3 FE1: hidden on the map's threshold, a
  // quick scroll made the prints vanish mid-flight).
  const css = storyCss.replace(/\/\*[\s\S]*?\*\//g, "");
  const stackRule = css.match(/\.how-stage\[data-scene="5"\] \.how-copy-stack \{ opacity: 0; transform: translateY\(calc\(-1 \* var\(--stack-rise, 100vh\)\)\); visibility: hidden; transition: transform (\d+)ms var\(--ease-in-out\), opacity (\d+)ms var\(--ease-out\) (\d+)ms, visibility 0s linear (\d+)ms; \}/);
  assert.ok(stackRule, "the stack's beat-05 rule");
  const [riseMs, fadeMs, fadeDelay, hideDelay] = stackRule.slice(1).map(Number);
  const stackGone = Math.max(riseMs, fadeDelay + fadeMs);
  assert.ok(riseMs >= 600 && hideDelay >= stackGone, `${riseMs} ${fadeDelay}+${fadeMs} ${hideDelay}`);
  assert.doesNotMatch(css, /data-map[^{}]*\.how-copy-stack/, "no map state hides the stack");
  assert.match(hookSource, /set\("--stack-rise", `\$\{layout\.stackRise\}px`\);/);
  // The rise takes the lowest print (border, turn and shadow: under one print size below its centre) past the top.
  for (const [name, layout] of LAYOUTS) for (const { cy, size } of layout.prints) assert.ok(layout.stackRise >= cy + size, name);
  // The map never shares a frame with the prints either: its content sits on a gate the beat's start sets running,
  // shut until the stack has gone, then opening; read slowly, the gate is long open by the map's threshold.
  const [, gateMs] = css.match(/\.how-stage\[data-scene="5"\] \.how-map-gate \{ animation: how-map-gate (\d+)ms linear backwards; \}/);
  const [, shutPct] = css.match(/@keyframes how-map-gate \{\s*0%, ([\d.]+)% \{ opacity: 0;[^}]*\}\s*100% \{ opacity: 1; \}\s*\}/);
  const shutMs = (Number(gateMs) * Number(shutPct)) / 100;
  assert.ok(shutMs >= stackGone - 0.5 && Number(gateMs) - shutMs >= 300, `${gateMs} ${shutPct}`);
  // The gate restarts on every visit: the skipped subtree is inside it (a skipped subtree keeps its old style).
  assert.match(css, /\.how-stage:not\(\[data-scene="5"\]\) \.how-map-gate \{ content-visibility: hidden; \}/);
  assert.doesNotMatch(css, /\.how-app-map \{ content-visibility/);
  // A jump into the beat (a reload, or a flick across two taps) shows it settled: the hook marks both elements
  // data-skip, as it does a tap ring, and they play nothing.
  assert.match(componentSource, /className="how-copy-stack" data-enter="map"/);
  assert.match(howSources["AppScreens.jsx"], /className="how-map-gate" data-enter=\{n\("map"\)\}/);
  assert.ok(HOW_STORY_BEATS.some(({ id }) => id === "map"));
  assert.match(hookSource, /querySelectorAll\("\[data-enter\]"\)/);
  assert.match(css, /\.how-stage\[data-scene="5"\] \.how-copy-stack\[data-skip="1"\] \{ transition: none; \}/);
  assert.match(css, /\.how-stage\[data-scene="5"\] \.how-map-gate\[data-skip="1"\] \{ animation: none; \}/);
  // The picker and its photos fade (200ms) and the map waits that long before it fades in; scrolled back across the
  // threshold, the picker waits out the map's fade. Neither way shows both, at any pace.
  const [, pickerMs] = css.match(/\.how-stage\[data-scene="5"\] :is\(\.how-app-picker, \.how-match\) \{ transition: opacity (\d+)ms/);
  const [, mapMs, mapDelay] = css.match(/\.how-stage\[data-scene="5"\]:not\(\[data-map="none"\]\) \.how-app-map \{ opacity: 1; transition: opacity (\d+)ms var\(--ease-out\) (\d+)ms; \}/);
  assert.ok(Number(mapDelay) >= Number(pickerMs) && Number(mapMs) >= 300, `${pickerMs} ${mapMs} ${mapDelay}`);
  const [, mapOutMs] = css.match(/\.how-stage\[data-scene="5"\] \.how-app-map \{ transition: opacity (\d+)ms/);
  const [, backDelay] = css.match(/\.how-stage\[data-scene="5"\]\[data-picker="1"\] :is\(\.how-app-picker, \.how-match\) \{ transition: opacity \d+ms var\(--ease-out\) (\d+)ms; \}/);
  assert.ok(Number(backDelay) >= Number(mapOutMs), `${backDelay} ${mapOutMs}`);
});

test("with no measured numbers, no band, no time and no tap count appear anywhere", () => {
  assert.deepEqual(Object.values(HOW_STORY_METRICS), [null, null, null, null, null]);
  assert.deepEqual(resolveHowStoryMetrics().errors, []);
  assert.equal(getHowStoryBand(), null);
  for (const [, layout] of LAYOUTS) {
    for (const p of sweep(0.005)) {
      for (const value of Object.values(getHowStoryFrame(p, layout).texts)) assert.doesNotMatch(value, /초|번/, value);
    }
  }
  for (const value of descriptions) assert.doesNotMatch(value, /실측|측정/);
});

test("measured numbers reach only the band; the stage keeps the example library", () => {
  const band = getHowStoryBand({ metrics: SAMPLE_METRICS });
  assert.deepEqual(band.eyebrow, "직접 재 봤어요");
  assert.deepEqual(band.stats.map(({ id, label, value, footnote }) => ({ id, label, value, footnote })), [
    { id: "scan", label: "사진 14,213장을 확인하는 데 걸린 시간", value: "6.2초", footnote: "테스트 기기 · 처음 찾을 때 · 2026년 11월 내부 측정 기준" },
    { id: "taps", label: "장소 검색부터 저장까지 누른 횟수", value: "5번", footnote: "테스트 빌드 · 2026년 11월 내부 측정 기준" },
  ]);
  // The count-up runs 0 -> value in the value's own steps (0.1 s, whole taps), never past it, and ends on it.
  for (const { count, value } of band.stats) {
    const values = Array.from({ length: 101 }, (_, index) => bandCountAt(count, index / 100));
    assert.equal(values[0], 0);
    assert.equal(formatHowStoryBandValue(count, values.at(-1)), value);
    values.forEach((n, index) => {
      assert.ok(n <= count.to && (index === 0 || n >= values[index - 1]), `${count.slot} ${n}`);
      assert.ok(Math.abs(n * 10 ** count.decimals - Math.round(n * 10 ** count.decimals)) < 1e-9, `${n} is one step of the value`);
    });
  }
  assert.equal(formatHowStoryBandValue(band.stats[0].count, 4.3), "4.3초");
  assert.equal(formatHowStoryBandValue(band.stats[1].count, 3), "3번");
  assert.equal(HOW_STORY_BAND_COUNT_MS, 900);
  assert.equal(getHowStoryBand({ metrics: { ...SAMPLE_METRICS, scanSeconds: 7 } }).stats[0].value, "7초");
  for (const p of sweep(0.01)) {
    const { texts } = getHowStoryFrame(p, HOW_STORY_TEST_LAYOUT.mobile, { metrics: SAMPLE_METRICS });
    for (const value of Object.values(texts)) assert.doesNotMatch(value, /14,?213/, value);
  }
  assert.equal(getHowStoryFrame(beatP(1, 0.75), HOW_STORY_TEST_LAYOUT.mobile, { metrics: SAMPLE_METRICS }).texts.overlayValue, "21,474장");
  assert.deepEqual(getHowStoryPlan({ metrics: SAMPLE_METRICS }).beats.map(({ id }) => id), ["row", "pick-0", "pick-1", "pick-2", "save"]);
  assert.deepEqual(getHowStoryTapPlan(6).map(({ id }) => id), ["field", "row", "pick-0", "pick-1", "pick-2", "save"]);
  assert.deepEqual(getHowStoryTapRange(), { min: 5, max: 6 });
  assert.equal(getHowStoryFrame(beatP(0, 0.05), HOW_STORY_TEST_LAYOUT.mobile, { metrics: { ...SAMPLE_METRICS, taps: 6 } }).state.tapField, 1);
  assert.equal(getHowStoryFrame(beatP(0, 0.05), HOW_STORY_TEST_LAYOUT.mobile, { metrics: SAMPLE_METRICS }).state.tapField, 0);
  const errorsFor = (overrides) => resolveHowStoryMetrics({ ...SAMPLE_METRICS, ...overrides }).errors;
  assert.deepEqual(errorsFor({ taps: 7 }), ["taps 7 cannot be shown: the story draws 5–6 taps"]);
  assert.deepEqual(errorsFor({ scanSource: "테스트 기기 · 2026년 11월 내부 측정" }), ["scanSource must say 처음 찾을 때 or 다시 찾을 때"]);
  assert.deepEqual(errorsFor({ scanSource: "테스트 기기 · 다시 찾을 때 · 내부 측정" }), ["scanSource must carry a month"]);
  assert.deepEqual(errorsFor({ tapsSource: "테스트 빌드 · 내부 측정" }), ["tapsSource must carry a month"]);
  assert.equal(getHowStoryBand({ metrics: { ...SAMPLE_METRICS, taps: 7 } }).stats.length, 1);
});

test("the library mosaic is open-licence crops only, with no photo beside itself", async () => {
  assert.equal(mosaic.cols, 16);
  assert.equal(mosaic.rows, 20);
  assert.equal(mosaic.sources.length, 320);
  assert.ok(mosaic.noLocation.length >= 30 && mosaic.noLocation.length <= 40);
  for (const source of mosaic.sources) assert.doesNotMatch(source, /gyeongbuk-/);
  // The sprite repeats, so neighbours wrap around its edges.
  const at = (col, row) => mosaic.sources[((row + mosaic.rows) % mosaic.rows) * mosaic.cols + ((col + mosaic.cols) % mosaic.cols)];
  for (let row = 0; row < mosaic.rows; row += 1) {
    for (let col = 0; col < mosaic.cols; col += 1) {
      for (const [dc, dr] of [[-1, -1], [0, -1], [1, -1], [-1, 0], [1, 0], [-1, 1], [0, 1], [1, 1]]) {
        assert.notEqual(at(col + dc, row + dr), at(col, row), `cell ${col},${row}`);
      }
    }
  }
  const sprite = await stat(new URL("../public/assets/how/library-mosaic.webp", import.meta.url));
  assert.ok(sprite.size <= 100 * 1024, `${sprite.size} bytes`);
  // The white pins are a raster of the same block (an SVG pattern of every pin was a long GPU raster on first show).
  const pins = await stat(new URL("../public/assets/how/library-pins.webp", import.meta.url));
  assert.ok(pins.size <= 30 * 1024, `${pins.size} bytes`);
  assert.doesNotMatch(howSources["LibraryMosaic.jsx"], /<pattern|<use |dangerouslySetInnerHTML/);
  assert.match(storyCss, /\.has-library \.how-pins-panel \{ background-image: url\("\/assets\/how\/library-pins\.webp"\); \}/);
  for (const [file, kb] of [["gyeongbuk-districts.json", 30], ["how-korea-provinces.json", 30], ["world-globe.json", 25]]) {
    const { size } = await stat(new URL(`../src/data/${file}`, import.meta.url));
    assert.ok(size <= kb * 1024, `${file} ${size}`);
  }
});

test("the mosaic panels break the 16-cell repeat, and no photo meets itself across a panel seam", () => {
  const { cols, rows, across, down, offsets } = mosaic.panels;
  assert.equal(offsets.length, across * down);
  const width = across * cols;
  const height = down * rows;
  const source = (col, row) => {
    const [ox, oy] = offsets[Math.floor(row / rows) * across + Math.floor(col / cols)];
    return mosaic.sources[((oy + (row % rows)) % mosaic.rows) * mosaic.cols + ((ox + (col % cols)) % mosaic.cols)];
  };
  for (let row = 0; row < height; row += 1) {
    for (let col = 0; col < width; col += 1) {
      for (const [dc, dr] of [[-1, -1], [0, -1], [1, -1], [-1, 0], [1, 0], [-1, 1], [0, 1], [1, 1]]) {
        const c = col + dc;
        const r = row + dr;
        if (c >= 0 && r >= 0 && c < width && r < height) assert.notEqual(source(c, r), source(col, row), `cell ${col},${row}`);
      }
    }
  }
  // The block's own period (16 across, 20 down) and the panel size no longer line the photos up.
  for (const [dx, dy] of [[mosaic.cols, 0], [0, mosaic.rows], [cols, 0], [0, rows]]) {
    let same = 0;
    let total = 0;
    for (let row = 0; row + dy < height; row += 1) {
      for (let col = 0; col + dx < width; col += 1) {
        total += 1;
        if (source(col, row) === source(col + dx, row + dy)) same += 1;
      }
    }
    assert.ok(same / total < 0.1, `shift ${dx},${dy}: ${same}/${total}`);
  }
  // The panels cover the largest library window; the pins follow the same panels.
  for (const [name, layout] of LAYOUTS) {
    assert.ok(Math.ceil(layout.window.w / layout.tile) <= width && Math.ceil(layout.window.h / layout.tile) <= height, name);
  }
  assert.match(howSources["LibraryMosaic.jsx"], /mosaic\.panels/);
});

test("the loading bar follows Material3 1.4: indicator, a 4dp gap, the track, and the stop dot", () => {
  // The CSS tokens mirror the layout's numbers.
  assert.match(screensCss, new RegExp(`--app-progress-gap: ${HOW_STORY_APP_PROGRESS.gapDp};`));
  assert.match(screensCss, new RegExp(`--app-progress-stop: ${HOW_STORY_APP_PROGRESS.stopDp};`));
  for (const [name, layout] of LAYOUTS) {
    const bar = layout.loadingBar;
    const gap = HOW_STORY_APP_PROGRESS.gapDp * layout.dp;
    assert.ok(Math.abs(bar.h - 8 * layout.dp) < 0.01, name);
    assert.deepEqual(linearProgressPlacement(0, bar, gap), { fill: -bar.w, track: 0 }, name);
    assert.equal(linearProgressPlacement(100, bar, gap).fill, 0, name);
    assert.ok(linearProgressPlacement(100, bar, gap).track >= bar.w - 0.01, `${name}: the track has gone at 100%`);
    let previous = null;
    for (let k = 1; k <= 100; k += 1) {
      const { fill, track } = linearProgressPlacement(k, bar, gap);
      if (previous) assert.ok(fill >= previous.fill && track >= previous.track, `${name} k=${k}`);
      // Between the indicator's round end and the track's, the visible gap is the M3 gap.
      if (k >= 5 && k <= 90) assert.ok(Math.abs(track - (fill + bar.w) - gap) < 0.02, `${name} k=${k}`);
      previous = { fill, track };
    }
  }
});

test("the desktop phone keeps its shadow room, lifted photos clear the overlay, and the cluster lands below it", () => {
  const { desktopMargin, overlayLabel, overlayValue, overlayBarSpace, clusterGap, matchLift, matchHalo, matchClear } = HOW_STORY_GEOMETRY;
  for (const stageHeight of [640, 680, 720, 800]) {
    const layout = createHowStoryLayout({ mode: "desktop", stageWidth: 600, stageHeight, viewportWidth: 1280 });
    assert.equal(layout.phone.y, desktopMargin.top, `${stageHeight}`);
    assert.ok(stageHeight - layout.phone.y - layout.phone.h >= desktopMargin.bottom, `${stageHeight}`);
  }
  const shapes = [["mobile", 390, 593, 390], ["mobile", 360, 400, 360], ["mobile", 360, 460, 360], ["tablet", 600, 700, 768], ["desktop", 600, 680, 1280]];
  for (const [mode, stageWidth, stageHeight, viewportWidth] of shapes) {
    const layout = createHowStoryLayout({ mode, stageWidth, stageHeight, viewportWidth });
    const top = Math.min(...layout.cluster.map(({ y }) => y));
    assert.ok(top >= layout.overlayTop + overlayLabel[mode] + overlayValue[mode] + clusterGap - 0.01, `${mode} ${stageWidth}x${stageHeight}`);
    const overlayBottom = layout.overlayTop + overlayLabel[mode] + overlayValue[mode] + overlayBarSpace;
    for (const cell of layout.cells) {
      assert.ok(Math.abs(cell.lift.size - cell.size * matchLift) < 0.01, mode);
      assert.ok(cell.lift.y - matchHalo >= overlayBottom + matchClear - 0.01, `${mode} ${stageWidth}x${stageHeight}: ${cell.lift.y}`);
    }
  }
});

test("the component, hook and styles keep to the scroll rules", () => {
  for (const [file, source] of [["HowStory.jsx", componentSource], ...Object.entries(howSources)]) assert.doesNotMatch(source, /[가-힣]/, file);
  assert.match(componentSource, /prefers-reduced-motion: reduce/);
  assert.match(componentSource, /innerHeight < MIN_VIEWPORT_HEIGHT/);
  assert.match(hookSource, /addEventListener\("scroll", schedule, \{ passive: true \}\)/);
  assert.match(hookSource, /rootMargin: ATTACH_MARGIN/);
  // The large-text check reads layout heights: a copy line animating in from 20px below is not overflow (a reload
  // straight into the story measures during that swap).
  assert.doesNotMatch(hookSource, /copy\.scrollHeight/);
  assert.match(hookSource, /copyBeats\.map\(\(beat\) => beat\.offsetHeight\)/);
  assert.match(hookSource, /classList\.add\("is-live"/);
  for (const source of [componentSource, hookSource, storyCss, screensCss, ...Object.values(howSources)]) {
    assert.doesNotMatch(source, /addEventListener\("(wheel|touch|key)|preventDefault|scrollIntoView|scrollTo\(|setInterval|scroll-snap/);
  }
  assert.match(storyCss, /\.how-story-track\[data-mode="static"\] \{ height: auto; \}/);
  assert.match(storyCss, /\.how-story-track\.is-live [^{]*\{ will-change: transform, opacity; \}/);
  for (const css of [storyCss.replace(/\.how-story-track\.is-live[^{]*\{ will-change[^}]*\}/g, ""), screensCss]) assert.doesNotMatch(css, /will-change/);
  // The fixed-width counter boxes never relayout on a text write: the text sits in a strictly contained box that
  // is neither a flex nor a grid item (those cannot be relayout boundaries).
  assert.match(screensCss, /\.how-loading-count \{[^}]*contain: strict;/);
  assert.match(screensCss, /:is\(\.how-loading-count, \.how-loading-percent\) > span \{ display: block; height: 100%; contain: strict; \}/);
  // The overlay's live number is sized by the final value but runs wider on LINE Seed's wider digits, so it is
  // contained for size and layout (the relayout boundary) and never for paint, which clipped 장 (round-3 V1).
  assert.match(storyCss, /\.how-overlay-live \{ position: absolute; inset: 0; contain: size layout style;/);
  assert.doesNotMatch(storyCss.match(/\.how-overlay-live \{[^}]*\}/)[0], /strict|paint|content|overflow/);
  // Per-frame paint and layout traps found in traces: a rounded overflow clip inside the sliding import screen,
  // the map's SVG labels relaying out under the camera's scale, and the spinner promoting what paints after it.
  assert.doesNotMatch(screensCss, /\.how-import-list \{[^}]*overflow/);
  assert.match(storyCss, /\.how-stage:not\(\[data-scene="5"\]\) \.how-map-gate \{ content-visibility: hidden; \}/);
  assert.match(screensCss, /\.how-loading-spin \{ position: relative; z-index: 1;/);
});

test("layers come from CSS per scene, never from the hook's writes (round-1 P3)", () => {
  // A 3D translate in an inline write would promote every moved node, small ones included.
  for (const node of [{ tx: 1, ty: 2, s: 1 }, { tx: 1, ty: 2, s: 0.5 }, { tx: 1, ty: 2, s: 0.5, r: -8 }]) {
    assert.doesNotMatch(getHowStoryNodeStyle(node).transform, /3d|translateZ/);
  }
  assert.equal(getHowStoryNodeStyle({ tx: 1, ty: 2, s: 1 }).transform, "translate(1px, 2px)");
  assert.equal(getHowStoryNodeStyle({ tx: 1, ty: 2, s: 0.5, r: -8 }).transform, "translate(1px, 2px) rotate(-8deg) scale(0.5)");
  // Every will-change is gated by the stage's state on the live stage.
  const rules = [...storyCss.replace(/\/\*[\s\S]*?\*\//g, "").matchAll(/([^{}]+)\{[^}]*will-change/g)].map(([, selector]) => selector.trim());
  assert.ok(rules.length >= 8, rules.join(" | "));
  for (const selector of rules) assert.match(selector, /^\.how-story-track\.is-live \.how-stage(:is\()?\[data-(scene|inside|save)="\d"\]/, selector);
  // The bezel's fade is the camera's own opacity; the library's dimming is a flat cover, not the sprite's opacity
  // (which split its panels into several large layers).
  assert.doesNotMatch(componentSource, /how-phone-fade|phoneFade/);
  for (const [name, layout] of LAYOUTS) {
    for (const p of [0, beatP(1, 0.3), holdBegin(2), beatP(3, 0.2), 1]) {
      const frame = getHowStoryFrame(p, layout);
      assert.equal(frame.nodes.phoneFade, undefined, name);
      assert.ok(frame.nodes.phoneCamera.o >= 0 && frame.nodes.phoneCamera.o <= 1, name);
      assert.equal(frame.nodes.mosaicSprite, undefined, name);
    }
    assert.equal(getHowStoryFrame(0, layout).nodes.phoneCamera.o, 1, name);
    assert.equal(getHowStoryFrame(holdBegin(2), layout).nodes.phoneCamera.o, 0, name);
    assert.equal(getHowStoryFrame(1, layout).nodes.phoneCamera.o, 1, name);
  }
  assert.match(howSources["LibraryMosaic.jsx"], /className="how-mosaic-dim" data-node="mosaicDim"/);
  assert.match(storyCss, /\.how-mosaic-dim \{ background: var\(--how-ground\); opacity: 0; \}/);
  assert.match(storyCss, /\.how-mosaic-window \{[^}]*background: var\(--how-ground\);/);
  // data-inside 0 drops the camera's, the library's and the overlay's layers: only while all of them are at rest.
  for (const [name, layout] of LAYOUTS) {
    const insides = new Set();
    for (let p = 0; p <= 1; p += 0.002) {
      const frame = getHowStoryFrame(p, layout);
      insides.add(frame.state.inside);
      if (frame.state.inside === 1) continue;
      const { phoneCamera, screenFade, bottomFade, mosaicWindow, ground, overlay } = frame.nodes;
      assert.deepEqual([phoneCamera.tx, phoneCamera.ty, phoneCamera.s, phoneCamera.o], [0, 0, 1, 1], `${name} p ${p}`);
      assert.deepEqual([screenFade.o, bottomFade.o, mosaicWindow.o, ground.o, overlay.o], [1, 1, 0, 0, 0], `${name} p ${p}`);
    }
    assert.deepEqual([...insides].sort(), [0, 1], name);
  }
  // data-picker 0 hides the picker's photos (once their 200ms fade is over) and drops their layers: only once all
  // are at 0. The prints' stack is hidden once the map tab is in (beat 05 test).
  for (const [name, layout] of LAYOUTS) {
    let seen = false;
    for (let t = 0; t <= 1; t += 0.01) {
      const frame = getHowStoryFrame(beatP(4, t), layout);
      if (frame.state.picker === 1) continue;
      seen = true;
      for (let i = 0; i < 4; i += 1) assert.equal(frame.nodes[`match${i}`].o, 0, `${name} t ${t}`);
    }
    assert.ok(seen, name);
    assert.equal(getHowStoryFrame(beatP(3, 0.9), layout).state.picker, 1, name);
  }
  assert.match(storyCss, /\.how-stage\[data-picker="0"\] \.how-match \{ visibility: hidden; transition: opacity 200ms var\(--ease-out\), visibility 0s linear 200ms; \}/);
  // The IME's last step is a delayed switch: a filled animation would keep it composited for good.
  assert.doesNotMatch(`${storyCss}\n${screensCss}`, /forwards/);
});

test("every endless animation in the story runs only on the live stage, in its own scene", () => {
  // A spinner left running after the reader moves on keeps restyling the page forever (round-1 F2).
  const endless = [...`${storyCss}\n${screensCss}`.replace(/\/\*[\s\S]*?\*\//g, "").matchAll(/([^{}]+)\{[^}]*animation:[^;}]*infinite/g)].map(([, selector]) => selector.trim());
  assert.ok(endless.length >= 2, endless.join(" | "));
  for (const selector of endless) assert.match(selector, /^\.how-story-track\.is-live \.how-stage\[data-scene="\d"\]/, selector);
  assert.match(screensCss, /\[data-save="1"\] \.how-app-spinner \{ display: block; \}/);
});

test("a jump shows the end state per tap, without a stage-wide switch that would replay finished rings", () => {
  // Toggling a stage-wide "instant" flag for one frame re-applied every ring's animation, so rings and pulses that
  // had already played ran again after each flick (round-1 S1/F1). Skips are now per tap and sticky.
  for (const source of [hookSource, storyCss, screensCss, componentSource]) assert.doesNotMatch(source, /instant/i);
  assert.match(hookSource, /const jump = lastP < 0 \|\| countTapsCrossed\(lastP, p, plan\.beats\) >= 2;/);
  // Each tap, and each moment that plays as a beat opens (data-enter), is skipped when a jump turns it on, and
  // un-skipped once the reader is back above it.
  assert.match(hookSource, /plan\.beats\.map\(\(beat\) => \(\{ element: tapElements\.get\(beat\.id\), p: beat\.p \}\)\),\s*\.\.\.enterElements,/);
  assert.match(hookSource, /if \(p < moment\.p\) write\(moment\.element, "data:skip", "0"\);\s*else if \(jump && !\(lastP >= moment\.p\)\) write\(moment\.element, "data:skip", "1"\);/);
  assert.match(screensCss, /\.how-tap\[data-tap\]\[data-skip="1"\],/);
  assert.match(screensCss, /\.how-match-chrome:has\(> \.how-tap\[data-skip="1"\]\) \.how-picker-preview \{ animation: none; \}/);
});

test("the pill, glow and static figures keep their round-1 visual fixes", () => {
  // V7: the 예시 화면 pill is solid in both themes, and white 11-12px bold text on it is at least 4.5:1.
  const luminance = (hex) => {
    const channels = hex.match(/[0-9a-f]{2}/gi).map((part) => parseInt(part, 16) / 255);
    const [r, g, b] = channels.map((c) => (c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4));
    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
  };
  const pills = [...storyCss.matchAll(/--how-pill-bg: ([^;]+);/g)].map(([, value]) => value.trim());
  assert.equal(pills.length, 2, pills.join(", "));
  for (const value of pills) {
    assert.match(value, /^#[0-9a-f]{6}$/i, value);
    assert.ok(1.05 / (luminance(value) + 0.05) >= 4.5, value);
  }
  // V8: the glow arrives with the phone, never before the frame has entered.
  assert.match(storyCss, /\.how-story-track:not\(\.is-entered\) \.how-glow \{ opacity: 0; transition: none; \}/);
  // V9: on desktop the pill sits in the stage box's top-right corner, not beside the phone over the zoomed screen.
  assert.match(storyCss, /\.how-frame-pill \{ grid-area: 1 \/ 2; top: 16px; right: 16px; \}/);
  // V10: static figures are sized by the window height too, and short landscape windows set copy and figure side by side.
  assert.match(storyCss, /\.how-static-figure \{ --phone-w: min\(260px, max\(120px, calc\(\(var\(--how-static-room\) - 18px\) \* 360 \/ 780 \+ 18px\)\)\);/);
  assert.match(storyCss, /--phone-w: min\(300px, max\(120px, calc\(\(var\(--how-static-room\) - 18px\) \* 360 \/ 780 \+ 18px\)\)\);/);
  assert.match(storyCss, /@supports \(height: 100svh\) \{\s*\.how-static \{ --how-static-room: calc\(100svh - 120px\); \}/);
  assert.match(storyCss, /@media \(min-width: 640px\) and \(max-width: 900px\) and \(max-height: 560px\) \{[^@]*\.how-static-beat \{ display: grid; grid-template-columns: minmax\(0, 1fr\) auto;/);
});

test("the skip link target clears the sticky header and the story end is centred from tablet width", () => {
  assert.match(storyCss, /\.how-story-end \{ display: flex; scroll-margin-top: 100px; \}/);
  assert.match(storyCss, /@media \(min-width: 561px\) \{[\s\S]*?\.how-story-end \{ justify-content: center; \}\s*\}/);
});

test("a static list re-checks scroll mode when the window grows taller, never on a phone's URL-bar flap", () => {
  assert.match(componentSource, /const STATIC_RECHECK_GROWTH = 100;/);
  assert.match(componentSource, /const SMALL_VIEWPORT_RECHECK_GROWTH = 16;/);
  assert.match(componentSource, /lowestHeight < MIN_VIEWPORT_HEIGHT && height >= MIN_VIEWPORT_HEIGHT/);
  // The small viewport height (svh) is what the frame is sized in; a collapsing URL bar does not change it. A fit
  // failure records it, so a desktop window shrunk below the fit and restored re-checks (round-1 F6).
  assert.match(componentSource, /height:100vh;height:100svh/);
  assert.match(componentSource, /small - checkedSmallHeightRef\.current >= SMALL_VIEWPORT_RECHECK_GROWTH/);
  assert.match(componentSource, /const handleFitFail = useCallback\(\(\) => \{\s*checkedSmallHeightRef\.current = smallViewportHeight\(\);/);
});

test("desktop windows down to about 630px tall keep the story, with the phone never taller than its stage", () => {
  const { bezel, phoneMin, desktopPhone } = HOW_STORY_GEOMETRY;
  // Stage height is the window's inner height - 120px on desktop (100svh - 120px, at most 760px).
  for (const innerHeight of [632, 650, 657, 680, 700, 720, 760, 800, 880]) {
    const stageHeight = Math.min(innerHeight - 120, 760);
    const width = fitHowStoryPhone({ mode: "desktop", stageWidth: 600, stageHeight, viewportWidth: 1280 });
    assert.notEqual(width, null, `${innerHeight}`);
    assert.ok(width >= phoneMin.desktop && width <= desktopPhone[1], `${innerHeight}: ${width}`);
    const phoneHeight = ((width - bezel * 2) * 780) / 360 + bezel * 2;
    assert.ok(stageHeight - phoneHeight >= 56 - 1, `${innerHeight}: ${phoneHeight} in ${stageHeight}`);
  }
  assert.equal(fitHowStoryPhone({ mode: "desktop", stageWidth: 600, stageHeight: 500, viewportWidth: 1280 }), null);
  // The preferred 260px phone stays while 28px margins allow it.
  assert.equal(fitHowStoryPhone({ mode: "desktop", stageWidth: 600, stageHeight: 600, viewportWidth: 1280 }), 260);
});

test("on every stage that fits, the four found photos pop inside the visible band and stay on the stage", () => {
  // Round-2 F1: with the 220px desktop floor, short laptop and tablet windows keep the story, and found photo D sat
  // below the band the pin wave crosses, so it never popped (1366x657: three photos ringed under a 4장 story).
  // Stages as the CSS builds them: desktop 600 x min(innerHeight - 120, 760); tablet min(width, 560) x
  // (min(innerHeight - 104, 860) - the copy slot - 20); phones the full width x (innerHeight - 80 - the copy - 20).
  // Each mode keeps the story from these window heights (below them the phone is under its 220px floor).
  const fitsFrom = { desktop: 632, tablet: 654, mobile: 630 };
  const stages = [];
  for (let height = 600; height <= 1000; height += 2) stages.push({ height, mode: "desktop", stageWidth: 600, stageHeight: Math.min(height - 120, 760), viewportWidth: 1280 });
  for (let height = 560; height <= 1366; height += 2) for (const width of [600, 768, 820, 900]) stages.push({ height, mode: "tablet", stageWidth: Math.min(width, 560), stageHeight: Math.min(height - 104, 860) - 147 - 20, viewportWidth: width });
  for (let height = 560; height <= 932; height += 2) for (const width of [360, 375, 390, 414, 430]) {
    const copy = 16 + 20 + 6 + 2.6 * Math.min(28, Math.max(26, 0.072 * width)) + 8 + 24;
    stages.push({ height, mode: "mobile", stageWidth: width, stageHeight: height - 80 - copy - 20, viewportWidth: width });
  }
  const { matchHalo } = HOW_STORY_GEOMETRY;
  for (const { height, ...stage } of stages) {
    const layout = createHowStoryLayout(stage);
    const label = `${stage.mode} ${stage.viewportWidth}x${height} (stage ${stage.stageWidth}x${stage.stageHeight})`;
    if (height >= fitsFrom[stage.mode]) assert.notEqual(layout, null, label);
    if (!layout) continue;
    for (const [index, cell] of layout.cells.entries()) {
      // The wave's edge ends at the band's bottom, so each pin must pop at or above it.
      assert.ok(cell.popAt <= layout.window.vBottom, `${label}: cell ${index} pops at ${cell.popAt}, band ends at ${layout.window.vBottom}`);
      // The lifted photo, its ring and its halo stay on the stage.
      assert.ok(cell.lift.y - matchHalo >= 0 && cell.lift.y + cell.lift.size + matchHalo <= layout.stageHeight, `${label}: cell ${index} lifted at ${cell.lift.y}`);
    }
    assert.equal(getHowStoryFrame(holdBegin(1), layout).state.pins, 4, label);
  }
});

test("the JS picks its layout mode with the same media queries as the CSS", () => {
  const css = storyCss.replace(/\/\*[\s\S]*?\*\//g, "");
  for (const width of Object.values(HOW_STORY_BREAKPOINTS)) assert.match(css, new RegExp(`@media \\(min-width: ${width}px\\)`));
  assert.match(hookSource, /getHowStoryMediaMode\(\(query\) => window\.matchMedia\(query\)\)/);
  // A 905px window with a 15px classic scrollbar: clientWidth is 890, but the CSS is in desktop columns.
  const media = (viewport) => (query) => ({ matches: viewport >= Number(/min-width: (\d+)px/.exec(query)[1]) });
  assert.equal(getHowStoryMediaMode(media(905)), "desktop");
  assert.equal(getHowStoryMode(890), "tablet");
  assert.equal(getHowStoryMediaMode(media(900)), "tablet");
  assert.equal(getHowStoryMediaMode(media(561)), "tablet");
  assert.equal(getHowStoryMediaMode(media(560)), "mobile");
});

test("the scene reach tracker needs 700 ms in a hold and fires each scene once", () => {
  const timers = new Map();
  let now = 0;
  let nextId = 1;
  const schedule = (callback, delay) => {
    const id = nextId++;
    timers.set(id, { callback, at: now + delay });
    return id;
  };
  const cancel = (id) => timers.delete(id);
  const advance = (ms) => {
    now += ms;
    for (const [id, timer] of [...timers]) {
      if (timer.at <= now) {
        timers.delete(id);
        timer.callback();
      }
    }
  };
  const reached = [];
  const tracker = createSceneReachTracker({ onReach: (index) => reached.push(index), schedule, cancel });
  tracker.update({ sceneIndex: 0, inHold: true, eligible: true });
  advance(699);
  assert.deepEqual(reached, []);
  tracker.update({ sceneIndex: 0, inHold: true, eligible: true });
  advance(1);
  assert.deepEqual(reached, [0]);
  tracker.update({ sceneIndex: 1, inHold: false, eligible: true });
  tracker.update({ sceneIndex: 0, inHold: true, eligible: true });
  advance(2000);
  assert.deepEqual(reached, [0]);
  // A fling past scene 2, then a hidden tab during scene 3, never counts.
  tracker.update({ sceneIndex: 1, inHold: true, eligible: true });
  advance(300);
  tracker.update({ sceneIndex: 2, inHold: false, eligible: true });
  advance(1000);
  tracker.update({ sceneIndex: 2, inHold: true, eligible: true });
  advance(300);
  tracker.update({ sceneIndex: 2, inHold: true, eligible: false });
  advance(1000);
  assert.deepEqual(reached, [0]);
  tracker.update({ sceneIndex: 2, inHold: true, eligible: true });
  advance(700);
  assert.deepEqual(reached, [0, 2]);
  tracker.update({ sceneIndex: 3, inHold: true, eligible: true });
  tracker.stop();
  advance(1000);
  assert.deepEqual(reached, [0, 2]);
});

test("the passive view tracker fires once after 1 s of continuous eligibility", () => {
  const pending = [];
  const schedule = (callback, delay) => {
    pending.push({ callback, delay, active: true });
    return pending.length - 1;
  };
  const cancel = (id) => { pending[id].active = false; };
  const run = () => pending.filter((timer) => timer.active).forEach((timer) => { timer.active = false; timer.callback(); });
  let views = 0;
  const tracker = createPassiveViewTracker({ onView: () => { views += 1; }, schedule, cancel });
  tracker.update(true);
  tracker.update(false);
  run();
  assert.equal(views, 0);
  tracker.update(true);
  tracker.update(true);
  assert.equal(pending.filter((timer) => timer.active).length, 1);
  assert.equal(pending.at(-1).delay, 1000);
  run();
  assert.equal(views, 1);
  assert.equal(tracker.fired, true);
  tracker.update(true);
  run();
  assert.equal(views, 1);
});

test("how_story_view always reaches analytics before the first how_story_scene", () => {
  const timers = new Map();
  let now = 0;
  let nextId = 1;
  const schedule = (callback, delay) => {
    const id = nextId++;
    timers.set(id, { callback, at: now + delay });
    return id;
  };
  const cancel = (id) => timers.delete(id);
  const advance = (ms) => {
    const end = now + ms;
    for (;;) {
      const due = [...timers].filter(([, timer]) => timer.at <= end).sort((a, b) => a[1].at - b[1].at)[0];
      if (!due) break;
      timers.delete(due[0]);
      now = due[1].at;
      due[1].callback();
    }
    now = end;
  };
  const sent = [];
  const view = createPassiveViewTracker({ onView: () => sent.push(["view", now]), schedule, cancel });
  const reach = createSceneReachTracker({ onReach: (index) => { view.flush(); sent.push([`scene ${index + 1}`, now]); }, schedule, cancel });
  view.update(true);
  advance(200);
  reach.update({ sceneIndex: 0, inHold: true, eligible: true });
  advance(700);
  assert.deepEqual(sent, [["view", 900], ["scene 1", 900]]);
  advance(2000);
  view.update(true);
  view.flush();
  advance(2000);
  assert.deepEqual(sent.map(([name]) => name), ["view", "scene 1"]);
  assert.equal(view.fired, true);
  assert.match(componentSource, /onReach: \(index\) => \{\s*viewTrackerRef\.current\?\.flush\(\);\s*trackEvent\(ANALYTICS_EVENTS\.HOW_STORY_SCENE/);
});

test("observers act on the newest of a batch of queued intersection entries", () => {
  const visible = { isIntersecting: true, intersectionRect: { height: 600 }, rootBounds: { height: 844 } };
  const hidden = { isIntersecting: false, intersectionRect: { height: 0 }, rootBounds: { height: 844 } };
  assert.equal(latestEntry([visible, hidden]), hidden);
  assert.equal(latestEntry([hidden, visible]), visible);
  assert.equal(latestEntry([visible]), visible);
  for (const source of [componentSource, hookSource]) {
    assert.match(source, /latestEntry\(entries\)/);
    assert.doesNotMatch(source, /new IntersectionObserver\(\(\[entry\]\)/);
  }
});

test("analytics keep scene_index to 1..5", () => {
  assert.match(analyticsSource, /parameters\.scene_index >= 1\s*&& parameters\.scene_index <= 5/);
  assert.equal(HOW_STORY_SCENE_COUNT, 5);
});

// Renders the real components with React on the server (analytics stubbed) and parses the markup into a tree.
async function loadRenderer() {
  const stub = {
    name: "analytics-stub",
    setup(buildApi) {
      buildApi.onResolve({ filter: /\/analytics\.js$/ }, () => ({ path: "analytics", namespace: "stub" }));
      buildApi.onLoad({ filter: /^analytics$/, namespace: "stub" }, () => ({
        contents: "export const ANALYTICS_EVENTS = new Proxy({}, { get: (_, key) => String(key) }); export const trackEvent = () => false;",
      }));
    },
  };
  const result = await build({
    stdin: {
      contents: `export { HowStory, HowStoryScrollFrame, HowStoryStage } from "./src/HowStory.jsx";
        export { MapScreen } from "./src/how/AppScreens.jsx";
        export { HowStoryBand } from "./src/how/HowStoryBand.jsx";
        export { createElement } from "react";
        export { renderToStaticMarkup } from "react-dom/server";`,
      resolveDir: fileURLToPath(new URL("../", import.meta.url)),
      sourcefile: "how-story-ssr.js",
    },
    bundle: true, write: false, format: "cjs", platform: "node", jsx: "automatic", logLevel: "silent",
    define: { "process.env.NODE_ENV": JSON.stringify("production"), "import.meta.env.DEV": "false" }, plugins: [stub],
  });
  const dir = await mkdtemp(path.join(tmpdir(), "how-story-ssr-"));
  try {
    const file = path.join(dir, "how-story-ssr.cjs");
    await writeFile(file, result.outputFiles[0].text);
    return createRequire(import.meta.url)(file);
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
}
const VOID_TAGS = new Set(["area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "source", "track", "wbr"]);
function parseMarkup(html) {
  const root = { tag: "#root", attrs: {}, children: [], text: "", parent: null };
  let current = root;
  let last = 0;
  for (const match of html.matchAll(/<(\/?)([a-zA-Z][\w:-]*)([^>]*?)(\/?)>/g)) {
    current.text += html.slice(last, match.index);
    last = match.index + match[0].length;
    const [, closing, tag, rawAttrs, selfClosing] = match;
    if (closing) {
      assert.equal(current.tag, tag, "well-formed markup");
      current = current.parent;
      continue;
    }
    const attrs = Object.fromEntries([...rawAttrs.matchAll(/([\w:-]+)(?:="([^"]*)")?/g)].map(([, name, value = ""]) => [name, value]));
    const node = { tag, attrs, children: [], text: "", parent: current };
    current.children.push(node);
    if (!selfClosing && !VOID_TAGS.has(tag)) current = node;
  }
  return root;
}
const descendants = (node) => node.children.flatMap((child) => [child, ...descendants(child)]);
const hasClass = (node, name) => (node.attrs.class ?? "").split(/\s+/).includes(name);
const textOf = (node) => node.text + node.children.map(textOf).join("");

test("the frame carries every node the frame writes, one 예시 화면 pill, and the static list one pill per figure", async () => {
  const { HowStory, HowStoryScrollFrame, createElement, renderToStaticMarkup } = await loadRenderer();
  const props = {
    frameRef: { current: null },
    copyRef: { current: null },
    stageRef: { current: null },
    scene: { index: 0, previous: null, direction: "down" },
    descriptions: HOW_STORY_BEATS.map((_, index) => getHowStorySceneDescription(index)),
  };
  const frameNode = parseMarkup(renderToStaticMarkup(createElement(HowStoryScrollFrame, props))).children[0];
  assert.ok(hasClass(frameNode, "how-story-frame"));
  const stage = descendants(frameNode).find((node) => hasClass(node, "how-stage"));
  assert.equal(stage.attrs["aria-hidden"], "true");
  // The hook finds nodes on the whole frame (the pill sits beside the stage) and texts inside the stage.
  const all = descendants(frameNode);
  const nodes = new Set(all.map((node) => node.attrs["data-node"]).filter(Boolean));
  const texts = new Set(descendants(stage).map((node) => node.attrs["data-text"]).filter(Boolean));
  const attrs = new Set(descendants(stage).map((node) => node.attrs["data-attr"]).filter(Boolean));
  const taps = new Set(descendants(stage).map((node) => node.attrs["data-tap"]).filter(Boolean));
  for (const [name, layout] of LAYOUTS) {
    for (const p of [0, 0.2, 0.5, 0.62, 0.7, 0.77, 0.82, 0.9, 1]) {
      const frame = getHowStoryFrame(p, layout);
      for (const key of Object.keys(frame.nodes)) assert.ok(nodes.has(key), `${name}: missing [data-node="${key}"]`);
      for (const key of Object.keys(frame.texts)) assert.ok(texts.has(key), `${name}: missing [data-text="${key}"]`);
      for (const key of Object.keys(frame.attrs)) assert.ok(attrs.has(key), `${name}: missing [data-attr="${key}"]`);
      assert.equal(frame.nodes.pill.o, 1);
    }
  }
  // Every tap the story can draw has its ring: the field (when counted), the row, the three picks and 기록하기.
  for (const { id } of getHowStoryTapPlan(6)) assert.ok(taps.has(id), `missing [data-tap="${id}"]`);
  // The stage's map carries the three summary cards with the app's own numbers.
  const stageText = textOf(stage);
  for (const value of ["0 / 22", "0% 채움", "1 / 22", "5% 채움", "1 / 17", "6% 채움", "1 / 195", "1% 채움"]) {
    const [n, rest] = value.split(" / ");
    assert.ok(rest === undefined ? stageText.includes(value) : stageText.includes(`${n}`) && stageText.includes(` / ${rest}`), value);
  }
  // In scroll mode the picker's grid holds empty places: the stage tiles are its cards.
  const pickerCards = descendants(stage).filter((node) => hasClass(node, "how-picker-card"));
  assert.equal(pickerCards.length, 4);
  for (const card of pickerCards) assert.equal(card.children.length, 0);
  const pills = all.filter((node) => hasClass(node, "how-pill"));
  assert.equal(pills.length, 1);
  assert.equal(textOf(pills[0]), HOW_STORY_TEXT.pill);
  assert.equal(pills[0].attrs["data-node"], "pill");
  assert.equal(pills[0].attrs["aria-hidden"], "true");
  assert.ok(!descendants(stage).includes(pills[0]), "the pill sits on the frame, outside the stage");
  // No swap delay on the first copy; the copy column has one article per beat.
  assert.equal(descendants(frameNode).find((node) => hasClass(node, "how-copy")).attrs["data-swap"], undefined);
  assert.equal(all.filter((node) => hasClass(node, "how-match")).length, 4);
  // No React render outside the static list: the server renders the static list (no window).
  const page = parseMarkup(renderToStaticMarkup(createElement(HowStory)));
  const track = descendants(page).find((node) => hasClass(node, "how-story-track"));
  assert.equal(track.attrs["data-mode"], "static");
  const beats = descendants(track).filter((node) => hasClass(node, "how-static-beat"));
  assert.equal(beats.length, 5);
  for (const beat of beats) {
    const figures = descendants(beat).filter((node) => node.tag === "figure");
    assert.equal(figures.length, 1);
    assert.equal(figures[0].attrs["aria-hidden"], "true");
    assert.equal(descendants(figures[0]).filter((node) => hasClass(node, "how-pill")).length, 1);
    assert.equal(descendants(beat).filter((node) => node.tag === "h3").length, 1);
    // End states only: nothing the scroll hook drives.
    assert.equal(descendants(figures[0]).filter((node) => node.attrs["data-attr"]).length, 0);
  }
  // 04: A, B and C selected (3장 선택), 기록하기 enabled, no spinner (not saving), no copies; four photo cards.
  const [, loading, , picker, map] = beats.map((beat) => descendants(beat).find((node) => node.tag === "figure"));
  // 02: a moment mid-run, a count with the app's own percent for it (develop never draws the count at 100%).
  const loadingText = textOf(loading);
  const [, processed] = loadingText.match(/(\d+) \/ 21474장/) ?? [];
  assert.ok(processed && Number(processed) < HOW_STORY_LIBRARY_COUNT, loadingText);
  assert.ok(loadingText.includes(`${appLoadingPercent(Number(processed))}%`), loadingText);
  assert.equal(picker.attrs["data-sel"], "3");
  assert.equal(picker.attrs["data-save"], undefined);
  assert.equal(descendants(picker).filter((node) => hasClass(node, "how-picker-card") && node.children.some((child) => child.tag === "img")).length, 4);
  assert.equal(descendants(picker).filter((node) => hasClass(node, "how-copy-node")).length, 0);
  // 05: the Korea map alone, 경상북도 filled: 나의 대한민국 지도, 1 / 17, 6% 채움.
  const mapScreen = descendants(map).find((node) => hasClass(node, "how-app-map"));
  assert.equal(mapScreen.attrs["data-variant"], "korea");
  const mapText = textOf(map);
  for (const value of ["나의 대한민국 지도", " / 17", "6% 채움"]) assert.ok(mapText.includes(value), value);
  for (const value of ["나의 세계 지도", " / 22", "5% 채움", "1% 채움"]) assert.ok(!mapText.includes(value), value);
  const skip = descendants(page).find((node) => hasClass(node, "how-story-skip"));
  assert.equal(skip.attrs.href, "#how-end");
  // With every metric null there is no band at all: no heading, no gap, no placeholder.
  assert.equal(descendants(page).filter((node) => (node.attrs.class ?? "").includes("how-band")).length, 0);
});

test("the map draws develop's own data: the drill land is the district rings, the provinces are develop's, thinned by at most 0.4dp", async () => {
  const data = buildHowMapData({ gyeongbuk, provinces: howProvinces, globe: worldGlobe });
  // The cards' totals are the data's.
  assert.match(howSources["AppScreens.jsx"], new RegExp(`export const DRILL_TOTAL = ${gyeongbuk.districts.length};`));
  assert.match(howSources["AppScreens.jsx"], new RegExp(`export const KOREA_TOTAL = ${howProvinces.provinces.length};`));
  assert.equal(data.districts.length, 22);
  assert.equal(data.districtLand, data.districts.map(({ d }) => d).join(""));
  assert.equal(data.visitedDistrict, data.districts.find(({ code }) => code === "47130").d);
  assert.equal(data.provinces.length, 17);
  assert.equal(data.visitedProvince, data.provinces.find(({ code }) => code === gyeongbuk.provinceCode).d);
  // develop's 강원 stops at 37.03 degrees, so no strip of it runs down 경상북도's coast (the landing's older
  // province file reached 36.61).
  const [originX, originY] = howProvinces.origin;
  const latitudes = (code) => howProvinces.provinces.find((province) => province.code === code).rings.flatMap((ring) => decodeSteps(ring).map(([, y]) => (y + originY) / 1000));
  assert.ok(Math.min(...latitudes("KR-42")) > 37, `${Math.min(...latitudes("KR-42"))}`);
  // The thinning (in degrees) plus the 0.001-degree rounding stays inside 0.4dp at the Korea view.
  const [, top] = APP_MAP_VIEW.project(127, howProvinces.bounds.maxLat);
  const [, bottom] = APP_MAP_VIEW.project(127, howProvinces.bounds.minLat);
  const dpPerDegree = (bottom - top) / (howProvinces.bounds.maxLat - howProvinces.bounds.minLat);
  assert.ok((howProvinces.tolerance + 0.0005) * dpPerDegree <= 0.4, `${(howProvinces.tolerance + 0.0005) * dpPerDegree}dp`);
  assert.equal(howProvinces.maxErrorDp, 0.4);
  // decodeSteps undoes the step encoding.
  assert.deepEqual(decodeSteps([10, 20, 1, -2, -3, 4]), [[10, 20], [11, 18], [8, 22]]);
  void originX;

  // Rendered with the data, the story's map has every group; without it (before the lazy load lands) the groups
  // the hook drives are still there, empty.
  const { MapScreen, createElement, renderToStaticMarkup } = await loadRenderer();
  const withData = parseMarkup(renderToStaticMarkup(createElement(MapScreen, { variant: "story", data })));
  const group = (root, name) => descendants(root).find((node) => hasClass(node, name));
  assert.equal(group(withData, "how-map-lines").children.length, 22);
  assert.equal(group(withData, "how-map-provinces").children.length, 17);
  assert.equal(group(withData, "how-map-labels").children.length, 22);
  assert.equal(group(withData, "how-map-fill-province").children[0].attrs.d, data.visitedProvince);
  const empty = parseMarkup(renderToStaticMarkup(createElement(MapScreen, { variant: "story" })));
  for (const key of ["mapDistrictLines", "mapProvinces", "mapFillDistrict", "mapFillProvince", "mapLabels"]) {
    const node = descendants(empty).find((child) => child.attrs["data-node"] === key);
    assert.ok(node, key);
    assert.equal(node.children.length, 0, key);
  }
});

test("the map data is not in the entry chunk: it loads once, when the track comes near or a static map mounts", () => {
  for (const file of ["AppScreens.jsx", "mapData.js"]) assert.doesNotMatch(howSources[file], /^import [^;]*\.json";$/m, file);
  assert.doesNotMatch(howSources["AppScreens.jsx"], /data\/(gyeongbuk-districts|how-korea-provinces|korea-provinces|world-globe)\.json/);
  for (const file of ["gyeongbuk-districts", "how-korea-provinces", "world-globe"]) {
    assert.match(howSources["mapData.js"], new RegExp(`import\\("\\.\\./data/${file}\\.json"\\)`));
  }
  assert.match(howSources["mapData.js"], /createCachedAsyncLoader\(/);
  assert.match(hookSource, /track\.classList\.add\("is-live", "has-library"\);\s*\/\/[^\n]*\n\s*loadHowMapData\(\)\.catch\(\(\) => \{\}\);/);
  assert.match(howSources["AppScreens.jsx"], /const loaded = useHowMapData\(!story && !given\);/);
});

test("beat 05's drill is a state develop's map allows: zoomed in on the mainland and panned within its clamp", () => {
  const { viewport, projection, zoom, pan } = APP_MAP_VIEW.drillCamera;
  // KoreaMapArtwork: MinZoom 1, MaxZoom 6.
  assert.ok(zoom >= 1 && zoom <= 6, `${zoom}`);
  // clampKoreaMapPan, written out again from develop's source (PanSlackFraction 0.1).
  const clampAxis = (value, leading, trailing, size) => {
    const slack = size * 0.1;
    if (trailing - leading <= size) return Math.min(Math.max(value, -slack), slack);
    return Math.min(Math.max(value, size - trailing - slack), -leading + slack);
  };
  const [cx, cy] = [viewport.width / 2, viewport.height / 2];
  const clamped = [
    clampAxis(pan[0], cx + (projection.left - cx) * zoom, cx + (projection.left + projection.width - cx) * zoom, viewport.width),
    clampAxis(pan[1], cy + (projection.top - cy) * zoom, cy + (projection.top + projection.height - cy) * zoom, viewport.height),
  ];
  pan.forEach((value, axis) => near(value, clamped[axis], `pan ${axis} is inside the clamp`, 1e-9));
  // At the drill view every mainland district's label point is inside the map area, and 경주시's sits in its upper
  // 62%, clear of a small phone's cropped bottom; 울릉군 is off to the east.
  const scale = APP_MAP_VIEW.area.width / APP_MAP_VIEW.drillBox[2];
  const atDrill = (lng, lat) => {
    const [x, y] = APP_MAP_VIEW.project(lng, lat);
    return [(x - APP_MAP_VIEW.drillBox[0]) * scale, (y - APP_MAP_VIEW.drillBox[1]) * scale];
  };
  for (const { code, name, labelPoint } of gyeongbuk.districts) {
    const [x, y] = atDrill(...labelPoint);
    if (code === "47940") assert.ok(x > APP_MAP_VIEW.area.width, name);
    else assert.ok(x > 16 && x < APP_MAP_VIEW.area.width - 16 && y > APP_MAP_VIEW.backRow && y < APP_MAP_VIEW.area.height, `${name} ${x} ${y}`);
  }
  const [, gyeongjuY] = atDrill(...gyeongbuk.districts.find(({ code }) => code === "47130").labelPoint);
  assert.ok(gyeongjuY / APP_MAP_VIEW.area.height <= 0.62, `${gyeongjuY}`);
  // About twice develop's first framing.
  const firstFrame = (APP_MAP_VIEW.area.width / APP_MAP_VIEW.drillBox[2]) / zoom;
  assert.ok(scale / firstFrame >= 2, `${scale / firstFrame}`);
});

test("the world view is develop's globe turned to 대한민국 and pinched in, so the filled country reads", () => {
  assert.equal(worldGlobe.longitude, -127);
  assert.equal(worldGlobe.latitude, HOW_STORY_MAP.globeTiltDeg);
  assert.equal(worldGlobe.zoom, HOW_STORY_MAP.globeZoom);
  // WorldGlobe: MaxTilt 1.3 rad, zoom 0.75-8.
  assert.ok((HOW_STORY_MAP.globeTiltDeg * Math.PI) / 180 <= 1.3);
  assert.ok(HOW_STORY_MAP.globeZoom >= 0.75 && HOW_STORY_MAP.globeZoom <= 8);
  const { cx, cy, r } = APP_MAP_VIEW.globe;
  near(r, HOW_STORY_MAP.globeRadius * Math.min(APP_MAP_VIEW.area.width, APP_MAP_VIEW.area.height) * HOW_STORY_MAP.globeZoom, "radius");
  // 대한민국 sits near the middle of the area, at least 18dp tall (11dp on develop's first frame, at the top edge).
  const korea = worldGlobe.countries.find(({ code }) => code === "KR").rings.flatMap((ring) => decodeSteps(ring)).map(([x, y]) => [cx + (x / worldGlobe.radius) * r, cy - (y / worldGlobe.radius) * r]);
  const ys = korea.map(([, y]) => y);
  const xs = korea.map(([x]) => x);
  assert.ok(Math.max(...ys) - Math.min(...ys) >= 18, `${Math.max(...ys) - Math.min(...ys)}dp`);
  assert.ok(Math.abs((Math.max(...xs) + Math.min(...xs)) / 2 - cx) < 30 && Math.abs((Math.max(...ys) + Math.min(...ys)) / 2 - cy) < 30);
});

test("screen readers get all five beats in scroll mode, with what each stage shows", async () => {
  const { HowStoryScrollFrame, createElement, renderToStaticMarkup } = await loadRenderer();
  const props = {
    frameRef: { current: null },
    copyRef: { current: null },
    stageRef: { current: null },
    scene: { index: 2, previous: 1, direction: "down" },
    descriptions,
  };
  const frame = parseMarkup(renderToStaticMarkup(createElement(HowStoryScrollFrame, props))).children[0];
  // Only one beat's copy shows at a time, so the visual column is hidden from them and a list carries every beat.
  assert.equal(descendants(frame).find((node) => hasClass(node, "how-copy")).attrs["aria-hidden"], "true");
  const list = descendants(frame).find((node) => node.tag === "ol" && hasClass(node, "sr-only"));
  const items = list.children.filter((node) => node.tag === "li");
  assert.equal(items.length, HOW_STORY_SCENE_COUNT);
  items.forEach((item, index) => {
    const beat = HOW_STORY_BEATS[index];
    assert.equal(textOf(item.children.find((node) => node.tag === "h3")), beat.headline.join(" "));
    const lines = item.children.filter((node) => node.tag === "p").map(textOf);
    assert.deepEqual(lines, [beat.sub, descriptions[index]]);
  });
  assert.ok(!descendants(frame.children.find((node) => hasClass(node, "how-stage")) ?? { children: [] }).includes(list));
});

test("the band shows measured numbers only, final values for screen readers, and no sample in a production build", async () => {
  const { HowStoryBand, createElement, renderToStaticMarkup } = await loadRenderer();
  // The component renders the band only when it resolves.
  assert.match(componentSource, /\{band && <HowStoryBand /);
  const band = getHowStoryBand({ metrics: SAMPLE_METRICS });
  const root = parseMarkup(renderToStaticMarkup(createElement(HowStoryBand, { band, animate: false }))).children[0];
  assert.ok(hasClass(root, "how-band"));
  assert.equal(textOf(descendants(root).find((node) => hasClass(node, "how-band-eyebrow"))), "직접 재 봤어요");
  const stats = descendants(root).filter((node) => hasClass(node, "how-band-stat"));
  assert.equal(stats.length, 2);
  stats.forEach((stat, index) => {
    const expected = band.stats[index];
    const value = descendants(stat).find((node) => hasClass(node, "how-band-value"));
    assert.equal(textOf(descendants(stat).find((node) => node.tag === "dt")), expected.label);
    // The live and sizing copies are hidden from screen readers; they read the final value once.
    assert.equal(textOf(value.children.find((node) => hasClass(node, "sr-only"))), expected.value);
    for (const name of ["how-band-live", "how-band-sizer"]) assert.equal(value.children.find((node) => hasClass(node, name)).attrs["aria-hidden"], "true");
    assert.equal(textOf(descendants(stat).find((node) => hasClass(node, "how-band-footnote"))), expected.footnote);
  });
  // The count-up is one-shot on an observer, in requestAnimationFrame steps; the static list shows final values.
  assert.match(howSources["HowStoryBand.jsx"], /threshold: 0\.5/);
  assert.match(howSources["HowStoryBand.jsx"], /requestAnimationFrame/);
  assert.match(howSources["HowStoryBand.jsx"], /prefers-reduced-motion: reduce/);
  assert.match(componentSource, /animate=\{mode === "scroll"\}/);
  // ?how-sample is a development aid only: a production bundle carries none of the sample's strings.
  assert.match(componentSource, /import\.meta\.env\.DEV && /);
  const production = await build({
    entryPoints: [fileURLToPath(new URL("../src/HowStory.jsx", import.meta.url))],
    bundle: true, write: false, format: "esm", platform: "browser", jsx: "automatic", minify: true, charset: "utf8", logLevel: "silent",
    external: ["react", "react/jsx-runtime", "react-dom", "posthog-js"],
    define: { "process.env.NODE_ENV": JSON.stringify("production"), "import.meta.env.DEV": "false" },
  });
  const bundled = production.outputFiles.map(({ text }) => text).join("\n");
  // Hangul stays readable in the bundle (charset utf8), so the band's own strings are there and the sample's are not.
  assert.ok(bundled.includes(HOW_STORY_TEXT.band.eyebrow));
  for (const sample of ["테스트 기기", "테스트 빌드", "14213", "how-sample"]) assert.ok(!bundled.includes(sample), sample);
});
