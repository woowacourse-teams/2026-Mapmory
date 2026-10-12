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
  HOW_STORY_TITLE,
  HOW_STORY_TITLE_LINES,
  HOW_STORY_TRACK,
  HOW_STORY_TRAVEL_UNITS,
  HOW_STORY_WINDOWS,
  getHowStoryBand,
  getHowStoryFrame,
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
import { HOW_STORY_APP_PROGRESS, HOW_STORY_GEOMETRY, createHowStoryLayout } from "../src/howStoryLayout.js";

const read = (file) => readFile(new URL(file, import.meta.url), "utf8");
const gyeongju = JSON.parse(await read("../src/data/gyeongju-boundary.json"));
const gyeongbuk = JSON.parse(await read("../src/data/gyeongbuk-districts.json"));
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
  for (const t of [0, 0.1, 0.2, 0.3, 0.4, 0.48]) {
    const { k, processed } = loadingStep(t);
    assert.equal(processed, Math.floor((HOW_STORY_LIBRARY_COUNT * k) / 100));
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
    assert.ok(hold.nodes.mosaicSprite.o > 0.1 && hold.nodes.mosaicSprite.o < 0.2, name);
    assert.equal(hold.nodes.pinsLayer.o, 0, name);
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
  assert.deepEqual(band, {
    eyebrow: "직접 재 봤어요",
    stats: [
      { id: "scan", label: "사진 14,213장을 확인하는 데 걸린 시간", value: "6.2초", footnote: "테스트 기기 · 처음 찾을 때 · 2026년 11월 내부 측정 기준" },
      { id: "taps", label: "장소 검색부터 저장까지 누른 횟수", value: "5번", footnote: "테스트 빌드 · 2026년 11월 내부 측정 기준" },
    ],
  });
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
  for (const file of ["gyeongbuk-districts.json", "east-asia.json"]) {
    const { size } = await stat(new URL(`../src/data/${file}`, import.meta.url));
    assert.ok(size <= (file === "east-asia.json" ? 25 : 30) * 1024, `${file} ${size}`);
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
  assert.match(storyCss, /\.how-overlay-live \{ position: absolute; inset: 0; contain: strict;/);
  // Per-frame paint and layout traps found in traces: a rounded overflow clip inside the sliding import screen,
  // the map's SVG labels relaying out under the camera's scale, and the spinner promoting what paints after it.
  assert.doesNotMatch(screensCss, /\.how-import-list \{[^}]*overflow/);
  assert.match(storyCss, /\.how-stage:not\(\[data-scene="5"\]\) \.how-app-map \{ content-visibility: hidden; \}/);
  assert.match(screensCss, /\.how-loading-spin \{ position: relative; z-index: 1;/);
  assert.match(storyCss, /:is\([^)]*\.how-mosaic-sprite, \.how-pins-layer\) \{ will-change: opacity; \}/);
});

test("the skip link target clears the sticky header and the story end is centred from tablet width", () => {
  assert.match(storyCss, /\.how-story-end \{ display: flex; scroll-margin-top: 100px; \}/);
  assert.match(storyCss, /@media \(min-width: 561px\) \{[\s\S]*?\.how-story-end \{ justify-content: center; \}\s*\}/);
});

test("a static list re-checks scroll mode when the window grows taller, never on a phone's URL-bar flap", () => {
  assert.match(componentSource, /const STATIC_RECHECK_GROWTH = 100;/);
  assert.match(componentSource, /if \(modeRef\.current === "static" && grew\) reevaluate\(\);/);
  assert.match(componentSource, /lowestHeight < MIN_VIEWPORT_HEIGHT && height >= MIN_VIEWPORT_HEIGHT/);
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
        export { createElement } from "react";
        export { renderToStaticMarkup } from "react-dom/server";`,
      resolveDir: fileURLToPath(new URL("../", import.meta.url)),
      sourcefile: "how-story-ssr.js",
    },
    bundle: true, write: false, format: "cjs", platform: "node", jsx: "automatic", logLevel: "silent",
    define: { "process.env.NODE_ENV": JSON.stringify("production") }, plugins: [stub],
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
  for (const [name, layout] of LAYOUTS) {
    for (const p of [0, 0.2, 0.5, 0.7, 1]) {
      const frame = getHowStoryFrame(p, layout);
      for (const key of Object.keys(frame.nodes)) assert.ok(nodes.has(key), `${name}: missing [data-node="${key}"]`);
      for (const key of Object.keys(frame.texts)) assert.ok(texts.has(key), `${name}: missing [data-text="${key}"]`);
      assert.equal(frame.nodes.pill.o, 1);
    }
  }
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
  }
  const skip = descendants(page).find((node) => hasClass(node, "how-story-skip"));
  assert.equal(skip.attrs.href, "#how-end");
});
