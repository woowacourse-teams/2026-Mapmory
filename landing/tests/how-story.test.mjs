import assert from "node:assert/strict";
import { access, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { createRequire } from "node:module";
import { tmpdir } from "node:os";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";
import { build } from "esbuild";
import { PHOTO_FINDER_LIBRARY_PHOTOS, PHOTO_FINDER_PLACES } from "../src/photoFinderDemo.js";
import {
  HOW_STORY_COPY_SLOTS,
  HOW_STORY_FILLERS,
  HOW_STORY_FLAG_BOX,
  HOW_STORY_GRID,
  HOW_STORY_NODE_SIZE,
  HOW_STORY_LIBRARY_COUNT,
  HOW_STORY_METRICS,
  HOW_STORY_SCENE_COUNT,
  HOW_STORY_SCENES,
  HOW_STORY_SELECTION,
  HOW_STORY_SELECTIONS,
  HOW_STORY_TEXT,
  HOW_STORY_TILES,
  HOW_STORY_TITLE,
  HOW_STORY_TRACK,
  getHowStoryFrame,
  getHowStoryNotes,
  getHowStoryPlan,
  getHowStoryProgress,
  getHowStoryScene,
  getHowStorySceneDescription,
  getHowStoryTapRange,
  isHowStoryHold,
  isInsideRings,
  projectToStoryMap,
  resolveHowStoryMetrics,
  stillProgress,
} from "../src/howStory.js";
import { createPassiveViewTracker, createSceneReachTracker, latestEntry } from "../src/howStoryAnalytics.js";

const read = (path) => readFile(new URL(path, import.meta.url), "utf8");
const gyeongju = JSON.parse(await read("../src/data/gyeongju-boundary.json"));
const storyCss = await read("../src/how-story.css");
const componentSource = await read("../src/HowStory.jsx");
const hookSource = await read("../src/useHowStoryScroll.js");
const appSource = await read("../src/App.jsx");
const heroSource = await read("../src/PhotoFinderHero.jsx");

// A test-only override with every measured number filled in.
const SAMPLE_METRICS = Object.freeze({
  scanSeconds: 6.2,
  scanLibraryCount: 14213,
  scanSource: "테스트 기기 · 사진 14,213장 · 첫 검색 실측",
  taps: 6,
  tapsSource: "테스트 빌드 실측",
});

// The travel with the frame at its largest (the viewport room); a frame sized to its content only adds travel.
const travelAt = (width, height) => {
  const desktop = width > 900;
  const trackHeight = width >= HOW_STORY_TRACK.wheelTrackMinWidth
    ? Math.min(Math.max(HOW_STORY_TRACK.desktopMinPx, (HOW_STORY_TRACK.desktopSvh / 100) * height), HOW_STORY_TRACK.desktopMaxPx)
    : (HOW_STORY_TRACK.mobileSvh / 100) * height;
  const offset = desktop ? HOW_STORY_TRACK.frameOffset.desktop : width > 560 ? HOW_STORY_TRACK.frameOffset.tablet : HOW_STORY_TRACK.frameOffset.mobile;
  const maxFrame = desktop ? HOW_STORY_TRACK.desktopMaxFrame : width > 560 ? HOW_STORY_TRACK.tabletMaxFrame : Infinity;
  return trackHeight - Math.min(height - offset, maxFrame);
};
const holdEnd = (index) => (index === HOW_STORY_SCENE_COUNT - 1 ? 1 : HOW_STORY_SCENES[index].window[1] - 1e-6);
const holdBegin = ({ window: [start, end], holdStart }) => start + holdStart * (end - start);
const stable = ({ nodes, texts, fillerFlags }) => ({ nodes, texts, fillerFlags });
const sweep = (step = 0.0025) => Array.from({ length: Math.round(1 / step) + 1 }, (_, index) => Math.min(1, index * step));
const sceneStrings = HOW_STORY_SCENES.flatMap((scene) => [scene.headline, scene.support]);
const collectStrings = (value) => (typeof value === "string" ? [value] : Array.isArray(value) || (value && typeof value === "object") ? Object.values(value).flatMap(collectStrings) : []);
const descriptionsFor = (options) => HOW_STORY_SCENES.map((_, index) => getHowStorySceneDescription(index, options));

test("progress clamps, guards a zero travel and is linear in between", () => {
  const base = { trackHeight: 2000, frameHeight: 600, stickyTop: 80 };
  assert.equal(getHowStoryProgress({ ...base, trackTop: 500 }), 0);
  assert.equal(getHowStoryProgress({ ...base, trackTop: -5000 }), 1);
  assert.equal(getHowStoryProgress({ ...base, trackTop: 80 - 700 }), 0.5);
  assert.equal(getHowStoryProgress({ trackTop: 79, trackHeight: 600, frameHeight: 600, stickyTop: 80 }), 1);
});

test("scene windows are contiguous from 0 to 1 and every scene has a hold", () => {
  assert.equal(HOW_STORY_SCENES.length, HOW_STORY_SCENE_COUNT);
  assert.equal(HOW_STORY_SCENES[0].window[0], 0);
  assert.equal(HOW_STORY_SCENES.at(-1).window[1], 1);
  HOW_STORY_SCENES.forEach((scene, index) => {
    assert.ok(scene.window[0] < scene.window[1]);
    if (index > 0) assert.equal(scene.window[0], HOW_STORY_SCENES[index - 1].window[1]);
    assert.ok(scene.holdStart > 0 && scene.holdStart < 1);
    assert.equal(getHowStoryScene(stillProgress(index)).index, index);
    assert.ok(isHowStoryHold(stillProgress(index)), `still ${index} sits in its hold`);
  });
  assert.equal(getHowStoryScene(-1).index, 0);
  assert.equal(getHowStoryScene(2).index, HOW_STORY_SCENE_COUNT - 1);
});

test("frames are pure: sweeping down then up gives identical frames at equal p", () => {
  for (const selection of HOW_STORY_SELECTIONS) {
    const down = sweep().map((p) => getHowStoryFrame(p, { selection }));
    const up = sweep().reverse().map((p) => getHowStoryFrame(p, { selection })).reverse();
    assert.deepEqual(up, down);
  }
});

test("each hold is long enough for one wheel step and the CSS matches the track constants", () => {
  // Desktop and half-screen laptop windows (561-900px wide use the desktop track) need 120px for scenes 1-4;
  // the last scene's state stays on screen while the frame scrolls away, so it cannot be skipped.
  for (const [width, height, minimum, scenes] of [[1280, 720, 120, 5], [683, 657, 120, 4], [720, 680, 120, 4], [880, 700, 120, 4], [360, 640, 100, 5]]) {
    const travel = travelAt(width, height);
    HOW_STORY_SCENES.slice(0, scenes).forEach((scene, index) => {
      const hold = (scene.window[1] - holdBegin(scene)) * travel;
      assert.ok(hold >= minimum, `${width}x${height} scene ${index + 1} hold ${hold.toFixed(1)}px`);
    });
  }
  assert.equal(travelAt(1280, 720), 1840);
  assert.equal(travelAt(360, 640), 1504);
  assert.equal(travelAt(683, 657), 1747);
  assert.match(storyCss, /height: 320svh;/);
  assert.match(storyCss, /@media \(min-width: 561px\) \{[^}]*\.how-story-track \{ height: 340vh; height: clamp\(2300px, 340svh, 3000px\); \}/);
  for (const top of [80, 88]) assert.match(storyCss, new RegExp(`top: calc\\(${top}px \\+ var\\(--how-sticky-extra, 0px\\)\\);`));
  assert.match(storyCss, /top: 96px;/);
  assert.match(hookSource, /setProperty\("--how-sticky-extra"/);
  assert.match(storyCss, /max-height: calc\(100svh - 96px\);/);
  assert.match(storyCss, /max-height: min\(calc\(100svh - 104px\), 860px\);/);
  assert.match(storyCss, /height: calc\(100svh - 112px\);\s*max-height: 760px;/);
  assert.equal(HOW_STORY_TRACK.tabletMaxFrame, 860);
  assert.equal(HOW_STORY_TRACK.desktopMaxFrame, 760);
});

test("nothing moves during a hold, and motion starts as soon as the frame sticks", () => {
  for (const selection of HOW_STORY_SELECTIONS) {
    for (const metrics of [HOW_STORY_METRICS, SAMPLE_METRICS]) {
      HOW_STORY_SCENES.forEach((scene, index) => {
        const options = { selection, metrics };
        assert.deepEqual(stable(getHowStoryFrame(holdBegin(scene), options)), stable(getHowStoryFrame(holdEnd(index), options)), `${selection} scene ${index + 1}`);
      });
    }
  }
  assert.notDeepEqual(stable(getHowStoryFrame(50 / 1504)), stable(getHowStoryFrame(0)));
  // Every on-screen tap lands before its scene's hold.
  for (const selection of HOW_STORY_SELECTIONS) {
    for (const beat of getHowStoryPlan({ selection, metrics: SAMPLE_METRICS }).beats) {
      assert.ok(beat.t < HOW_STORY_SCENES[beat.scene].holdStart, `${selection} ${beat.id}`);
    }
  }
});

test("matching mirrors the app: inside the bundled 경주시 boundary means a match", () => {
  assert.equal(gyeongju.code, "47130");
  assert.equal(gyeongju.provinceCode, "KR-47");
  assert.ok(gyeongju.rings[0].length >= 100);
  for (const tile of HOW_STORY_TILES) {
    if (tile.kind === "no-location") {
      assert.equal(tile.lat, null);
      assert.equal(tile.lng, null);
      continue;
    }
    assert.equal(isInsideRings(tile.lng, tile.lat, gyeongju.rings), tile.kind === "match", tile.src);
  }
  assert.equal(HOW_STORY_TILES.filter(({ kind }) => kind === "match").length, 4);
  assert.deepEqual(HOW_STORY_TILES.filter(({ kind }) => kind === "match").map(({ slot }) => slot).sort(), [0, 1, 2, 3]);
  const hold = getHowStoryFrame(holdEnd(2));
  HOW_STORY_TILES.forEach((tile, index) => {
    if (tile.kind !== "match") return;
    const [x, y] = projectToStoryMap(tile.lng, tile.lat);
    const node = hold.nodes[`tile${index}`];
    assert.ok(Math.abs(node.tx + 28 - x) <= 0.5 && Math.abs(node.ty + 28 - y) <= 0.5, tile.src);
    assert.equal(hold.nodes[`tileMint${index}`].o, 1);
  });
});

test("every dot clears the drawn 경주시 boundary, so inside and outside read clearly", () => {
  // The match dot is 10px and bumps to x1.4; the boundary stroke is 2px. Story px, simplified ring as drawn.
  const clearance = 1.4 * (HOW_STORY_NODE_SIZE.dot / 2) + 2;
  assert.match(storyCss, /\.how-map-boundary \{[^}]*stroke-width: 2;/);
  const ring = gyeongju.rings[0].map(([lng, lat]) => projectToStoryMap(lng, lat));
  const segmentDistance = ([px, py], [ax, ay], [bx, by]) => {
    const dx = bx - ax;
    const dy = by - ay;
    const u = Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy || 1)));
    return Math.hypot(px - (ax + u * dx), py - (ay + u * dy));
  };
  const distance = (point) => Math.min(...ring.map((start, index) => segmentDistance(point, start, ring[(index + 1) % ring.length])));
  for (const tile of HOW_STORY_TILES) {
    if (tile.kind === "no-location") continue;
    const point = projectToStoryMap(tile.lng, tile.lat);
    assert.ok(distance(point) >= clearance, `${tile.src} sits ${distance(point).toFixed(1)}px from the boundary`);
  }
  // The four found photos stay apart, so each reads as its own dot.
  const matches = HOW_STORY_TILES.filter(({ kind }) => kind === "match").map((tile) => projectToStoryMap(tile.lng, tile.lat));
  for (const [i, a] of matches.entries()) for (const b of matches.slice(i + 1)) assert.ok(Math.hypot(a[0] - b[0], a[1] - b[1]) >= 18);
});

test("both selection variants end with the same three photos checked and one left on the phone", () => {
  assert.equal(HOW_STORY_SELECTION, "tap-to-check");
  for (const selection of HOW_STORY_SELECTIONS) {
    const frame = getHowStoryFrame(holdEnd(3), { selection });
    const badgeOpacity = (slot) => frame.nodes[`tileBadge${HOW_STORY_TILES.findIndex((tile) => tile.slot === slot)}`].o;
    assert.deepEqual([0, 1, 2, 3].map(badgeOpacity), [1, 1, 0, 1], selection);
    assert.equal(frame.texts.sheetCount, "3장 선택");
    assert.equal(frame.nodes.keepTag.o, 1);
  }
  assert.deepEqual([...HOW_STORY_COPY_SLOTS], [0, 1, 3]);
  // tap-to-check starts with nothing checked; prechecked starts with all four.
  const at = (selection, t4) => getHowStoryFrame(HOW_STORY_SCENES[3].window[0] + t4 * 0.32, { selection }).texts.sheetCount;
  assert.equal(at("tap-to-check", 0.25), "0장 선택");
  assert.equal(at("prechecked", 0.31), "4장 선택");
});

test("the server counter stays at 0 until the copies cross the line, then reads 3", () => {
  const frames = sweep(0.001).map((p) => getHowStoryFrame(p));
  const firstArrival = frames.findIndex((frame) => frame.counts.received > 0);
  assert.ok(firstArrival > 0);
  for (const frame of frames.slice(0, firstArrival)) assert.equal(frame.texts.server, "받은 사진 0장");
  for (const frame of frames.filter(({ p }) => p >= holdBegin(HOW_STORY_SCENES[3]))) assert.equal(frame.texts.server, "받은 사진 3장");
  assert.ok(frames[firstArrival].p >= HOW_STORY_SCENES[3].window[0]);
  for (let index = 0; index < HOW_STORY_SCENE_COUNT; index += 1) {
    const still = getHowStoryFrame(stillProgress(index));
    for (const key of ["copy0", "copy1", "copy2", "tapRing"]) assert.equal(still.nodes[key].o, 0, `still ${index + 1} ${key}`);
  }
});

test("copy stays short: headline <= 20 characters, support line <= 40", () => {
  for (const { headline, support } of HOW_STORY_SCENES) {
    assert.ok(headline.length <= 20, headline);
    assert.ok(support.length <= 40, support);
  }
  assert.equal(HOW_STORY_TITLE, "사진첩이 커도, 장소만 고르면 돼요.");
});

test("no claim the client code does not back, on either app version", () => {
  const banned = /위치·날짜|날짜로|자동으로 (날짜|제목)|\bAI\b|인공지능|인식|위치.{0,6}(지워|삭제|제거)|서버에서도.*삭제|올리는 중|업로드 중|\d+%|모두 선택|오사카|명소|맛집|사진첩.*보지 않|사진을 (다|전부) 보지 않|선택한 사진만 (읽|확인)|일부 (허용|접근)으로도 찾|위치(만)? (봐요|읽어요)|폰에만|위치 정보는 폰|지도로 이동|권한 없이|10장까지|원본 그대로|\/10장|1분|3만|초 만에|탭이면|번 탭|저장 중/;
  const faqLiterals = [...appSource.matchAll(/\{ question: "([^"]+)", answer: "([^"]+)" \}/g)].flatMap(([, question, answer]) => [question, answer]);
  assert.ok(faqLiterals.length >= 10);
  const trustLines = [...(appSource + heroSource).matchAll(/사진은 폰 안에서[^<"]*/g)].map(([line]) => line);
  assert.ok(trustLines.length >= 2);
  const strings = [
    HOW_STORY_TITLE,
    ...sceneStrings,
    ...collectStrings(HOW_STORY_TEXT),
    ...descriptionsFor({}),
    ...descriptionsFor({ metrics: SAMPLE_METRICS }),
    ...descriptionsFor({ selection: "prechecked" }),
    ...faqLiterals,
    ...trustLines,
    appSource.match(/<h2 id="privacy-title">([^<]+)<\/h2>/)[1],
  ];
  for (const value of strings) assert.doesNotMatch(value, banned, value);
});

test("required strings: the boundary, the save rule and the place", () => {
  const visible = [...sceneStrings, ...collectStrings(HOW_STORY_TEXT), getHowStoryFrame(0).texts.server].join("\n");
  for (const required of ["받은 사진 0장", "사진은 저장할 때만 이 선을 넘어요", "고른 사진만 올라가요", "경상북도 경주시"]) {
    assert.ok(visible.includes(required), required);
  }
});

test("with no measured numbers, no time, tap count or footnote appears anywhere", () => {
  assert.deepEqual({ ...HOW_STORY_METRICS }, { scanSeconds: null, scanLibraryCount: null, scanSource: null, taps: null, tapsSource: null });
  const resolved = resolveHowStoryMetrics();
  assert.deepEqual(resolved.errors, []);
  assert.equal(resolved.scan, null);
  assert.equal(resolved.taps, null);
  assert.equal(resolved.libraryCount, HOW_STORY_LIBRARY_COUNT);
  assert.deepEqual(getHowStoryNotes(), []);
  for (const p of sweep(0.01)) {
    const frame = getHowStoryFrame(p);
    assert.equal(frame.texts.timer, null);
    assert.equal(frame.texts.taps, null);
    assert.equal(frame.counts.taps, null);
  }
  assert.equal(getHowStoryFrame(1).texts.checked, "21,474 / 21,474장");
  for (const description of descriptionsFor({})) assert.doesNotMatch(description, /실측|\d초|탭 \d/);
  assert.match(componentSource, /plan\.scan && /);
  assert.match(componentSource, /plan\.taps && /);
});

test("measured numbers drive the timer, the library count, the tap counter and the notes", () => {
  const options = { metrics: SAMPLE_METRICS };
  const plan = getHowStoryPlan(options);
  assert.deepEqual(plan.errors, []);
  assert.equal(plan.libraryCount, 14213);
  assert.equal(getHowStoryFrame(0, options).texts.timer, "0.0초");
  assert.equal(getHowStoryFrame(0, options).texts.taps, "0번");
  const scanned = getHowStoryFrame(holdEnd(1), options);
  assert.equal(scanned.texts.timer, "6.2초");
  assert.equal(scanned.texts.checked, "14,213 / 14,213장");
  // The 4 found photos are made up, so a measured library size never shares their sentence.
  assert.equal(getHowStoryFrame(holdEnd(2), options).texts.pill, "경주시 사진 4장");
  assert.equal(getHowStoryFrame(holdEnd(2)).texts.pill, "21,474장 중 경주시 사진 4장");
  for (const p of sweep(0.01)) assert.ok(!getHowStoryFrame(p, options).texts.pill.includes("14,213"));
  assert.doesNotMatch(getHowStorySceneDescription(2, options), /14,213|21,474/);
  assert.match(getHowStorySceneDescription(2), /21,474장 중 경주시에서 찍은 사진 4장/);
  assert.equal(getHowStoryFrame(1, options).texts.taps, "6번");
  // The counter moves by one per visible tap, and never during a hold.
  let previous = 0;
  for (const p of sweep(0.0005)) {
    const { taps } = getHowStoryFrame(p, options).counts;
    assert.ok(taps === previous || taps === previous + 1);
    previous = taps;
  }
  assert.equal(previous, 6);
  assert.deepEqual(getHowStoryNotes(options), [SAMPLE_METRICS.scanSource, SAMPLE_METRICS.tapsSource]);
  assert.match(getHowStorySceneDescription(1, options), /14,213장.*6\.2초/);
  assert.match(getHowStorySceneDescription(4, options), /탭 6번/);
  // Only taps both app versions share are drawn: the place, the photos and save, plus the search field when
  // the measured count includes it. No 사진 찾기 tap (develop starts the scan on the pick).
  assert.deepEqual(plan.beats.map(({ id }) => id), ["search", "place", "select-0", "select-1", "select-2", "save"]);
  assert.deepEqual(getHowStoryPlan({ metrics: { ...SAMPLE_METRICS, taps: 5 } }).beats.map(({ id }) => id), ["place", "select-0", "select-1", "select-2", "save"]);
  assert.deepEqual(getHowStoryTapRange("tap-to-check"), { min: 5, max: 6 });
  assert.deepEqual(getHowStoryTapRange("prechecked"), { min: 3, max: 4 });
  assert.match(resolveHowStoryMetrics({ ...SAMPLE_METRICS, taps: 7 }).errors.join(), /cannot be shown/);
  assert.match(resolveHowStoryMetrics(SAMPLE_METRICS, "prechecked").errors.join(), /cannot be shown/);
  assert.match(resolveHowStoryMetrics({ ...SAMPLE_METRICS, scanLibraryCount: null }).errors.join(), /scanLibraryCount/);
  assert.equal(resolveHowStoryMetrics({ ...SAMPLE_METRICS, scanSource: " " }).scan, null);
});

test("the component and hook keep to the scroll rules", () => {
  assert.doesNotMatch(componentSource, /[가-힣]/);
  assert.match(componentSource, /<ol className="how-story-steps"/);
  assert.match(componentSource, /<div className="how-stage" aria-hidden="true">/);
  assert.match(componentSource, /<figure className="how-story-still" aria-hidden="true">/);
  assert.doesNotMatch(componentSource, /className="how-story-step"[^>]*aria-hidden/);
  assert.match(componentSource, /prefers-reduced-motion: reduce/);
  assert.match(componentSource, /innerHeight < MIN_VIEWPORT_HEIGHT/);
  assert.match(hookSource, /addEventListener\("scroll", schedule, \{ passive: true \}\)/);
  assert.match(hookSource, /rootMargin: ATTACH_MARGIN/);
  assert.match(hookSource, /classList\.add\("is-live"\)/);
  for (const source of [componentSource, hookSource, storyCss]) {
    assert.doesNotMatch(source, /addEventListener\("(wheel|touch|key)|preventDefault|scrollIntoView|scrollTo\(|setInterval|scroll-snap/);
  }
  assert.match(storyCss, /\[data-mode="static"\] \{ height: auto; \}/);
  assert.match(storyCss, /\[data-mode="static"\] \.how-story-frame \{ position: static; height: auto;/);
  assert.match(storyCss, /\.is-live [^{]*\{ will-change: transform, opacity; \}/);
  assert.doesNotMatch(storyCss.replace(/\.how-story-track\.is-live[^{]*\{ will-change[^}]*\}/g, ""), /will-change/);
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

test("story photos are a separate, credited set and never reuse the hero's", async () => {
  const heroPhotos = new Set([...PHOTO_FINDER_LIBRARY_PHOTOS, ...PHOTO_FINDER_PLACES.flatMap(({ photos }) => photos)]);
  const storyPhotos = new Set([...HOW_STORY_TILES.map(({ src }) => src), ...HOW_STORY_FILLERS.map(({ src }) => src)]);
  assert.equal(storyPhotos.size, HOW_STORY_TILES.length);
  for (const src of storyPhotos) {
    assert.ok(!heroPhotos.has(src), src);
    assert.doesNotMatch(src, /gangwon-02|gangwon-04|busan-04/);
    await access(new URL(`../public${src}`, import.meta.url));
  }
  assert.equal(HOW_STORY_FILLERS.length + HOW_STORY_TILES.length, 90);
});

test("the library fillers never contradict the result: no found photo repeats, the no-location photo never gets a pin", () => {
  const matchSources = new Set(HOW_STORY_TILES.filter(({ kind }) => kind === "match").map(({ src }) => src));
  const noLocationSource = HOW_STORY_TILES.find(({ kind }) => kind === "no-location").src;
  const locatedStorySources = new Set(HOW_STORY_TILES.filter(({ kind }) => kind === "elsewhere").map(({ src }) => src));
  for (const filler of HOW_STORY_FILLERS) {
    const where = filler.cell.join(":");
    assert.ok(!matchSources.has(filler.src), `${where} repeats a found photo`);
    if (filler.hasLocation) assert.notEqual(filler.src, noLocationSource, `${where} pins the no-location photo`);
    else assert.ok(!locatedStorySources.has(filler.src), `${where} shows a located photo as 위치 없음`);
  }
  assert.ok(HOW_STORY_FILLERS.some(({ hasLocation }) => !hasLocation));
  // No photo sits right beside a copy of itself.
  const byCell = new Map([...HOW_STORY_FILLERS, ...HOW_STORY_TILES].map(({ cell, src }) => [cell.join(":"), src]));
  for (const [key, src] of byCell) {
    const [row, column] = key.split(":").map(Number);
    for (const neighbour of [`${row + 1}:${column}`, `${row}:${column + 1}`]) assert.notEqual(byCell.get(neighbour), src, `${key} and ${neighbour}`);
  }
});

test("every flag the scan passes is fully shown by the scene 2 hold, and none show before the scan", () => {
  const holdFrame = getHowStoryFrame(holdEnd(1));
  const before = getHowStoryFrame(holdEnd(0));
  assert.equal(holdFrame.nodes.pins.o, 1);
  assert.equal(before.nodes.pins.o, 0);
  assert.equal(holdFrame.nodes.pinsStrip.ty, HOW_STORY_GRID.finalOffset);
  // The flag layer moves exactly with the library strip at every p.
  for (const p of sweep(0.005)) {
    const { nodes } = getHowStoryFrame(p);
    assert.equal(nodes.pinsStrip.ty, nodes.strip.ty, `p ${p}`);
  }
  // The filler flag layer is clipped at the scan line: every filler's flag ends above it at the hold.
  assert.match(storyCss, new RegExp(`\\.how-pins-clip \\{ width: 320px; height: ${HOW_STORY_GRID.scanLineY}px; overflow: hidden; \\}`));
  for (const filler of HOW_STORY_FILLERS) {
    const [, bottom] = HOW_STORY_FLAG_BOX[filler.hasLocation ? "pin" : "none"];
    const flagBottom = HOW_STORY_GRID.y0 + filler.cell[0] * HOW_STORY_GRID.pitch + bottom + HOW_STORY_GRID.finalOffset;
    assert.ok(flagBottom <= HOW_STORY_GRID.scanLineY, `${filler.cell.join(":")} flag ends at ${flagBottom}`);
  }
  HOW_STORY_TILES.forEach((_, index) => {
    assert.equal(holdFrame.nodes[`tileFlag${index}`].o, 1, `story flag ${index}`);
    assert.equal(before.nodes[`tileFlag${index}`].o, 0, `story flag ${index} before the scan`);
  });
});

test("scene 1 makes no claim about where the place search runs: the 내 폰 안 frame waits for the scan", () => {
  for (const p of sweep(0.002).filter((value) => value < HOW_STORY_SCENES[1].window[0])) {
    const frame = getHowStoryFrame(p);
    assert.equal(frame.nodes.zoneFrame.o, 0, `p ${p}`);
    assert.equal(frame.nodes.zoneChip.o, 0, `p ${p}`);
  }
  for (const index of [1, 2, 3, 4]) {
    const frame = getHowStoryFrame(stillProgress(index));
    assert.equal(frame.nodes.zoneFrame.o, 1);
    assert.equal(frame.nodes.zoneChip.o, 1);
  }
  // The 확인한 사진 counter is part of the zone: it arrives with the frame, never before it.
  for (const p of sweep(0.002)) {
    const { nodes } = getHowStoryFrame(p);
    assert.equal(nodes.checkedMeter.o, nodes.zoneFrame.o, `p ${p}`);
  }
  // The search card is gone before the frame fades in around the library.
  const scan = HOW_STORY_SCENES[1];
  const firstZone = sweep(0.0005).find((p) => p >= scan.window[0] && getHowStoryFrame(p).nodes.zoneFrame.o > 0);
  assert.ok(getHowStoryFrame(firstZone).nodes.searchCard.o <= 0.25);
  assert.match(componentSource, /className="how-zone-frame" data-node="zoneFrame"/);
  assert.match(componentSource, /className="how-zone-chip" data-node="zoneChip"/);
  assert.doesNotMatch(storyCss, /\.how-zone \{[^}]*dashed/);
});

test("a measured tap counter wraps to its own row on a narrow stage instead of cutting the numbers", () => {
  assert.match(storyCss, /\.how-stage \{[^}]*container-type: inline-size;/);
  assert.match(storyCss, /@container \(max-width: 340px\) \{\s*\.how-meter\.has-taps \{ height: auto; min-height: 22px; flex-wrap: wrap;[^}]*\}/);
  // The values that change on every frame are fixed-size boxes; the timer sits beside the count, not inside it.
  assert.match(storyCss, /\.how-meter-value\[data-text="checked"\] \{[^}]*contain: strict; \}/);
  assert.match(storyCss, /\.how-meter-timer \{[^}]*contain: strict; \}/);
});

// Renders the real stage with React on the server (analytics stubbed) and parses the markup into a tree.
async function loadStageRenderer() {
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
      contents: `export { HowStory, HowStoryStage } from "./src/HowStory.jsx";
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
  const root = { tag: "#root", attrs: {}, children: [], parent: null };
  let current = root;
  for (const [, closing, tag, rawAttrs, selfClosing] of html.matchAll(/<(\/?)([a-zA-Z][\w:-]*)([^>]*?)(\/?)>/g)) {
    if (closing) {
      assert.equal(current.tag, tag, "well-formed markup");
      current = current.parent;
      continue;
    }
    const attrs = Object.fromEntries([...rawAttrs.matchAll(/([\w:-]+)(?:="([^"]*)")?/g)].map(([, name, value = ""]) => [name, value]));
    const node = { tag, attrs, children: [], parent: current };
    current.children.push(node);
    if (!selfClosing && !VOID_TAGS.has(tag)) current = node;
  }
  return root;
}
const descendants = (node) => node.children.flatMap((child) => [child, ...descendants(child)]);
const ancestors = (node) => (node.parent ? [node.parent, ...ancestors(node.parent)] : []);
const hasClass = (node, name) => (node.attrs.class ?? "").split(/\s+/).includes(name);

test("phone-side counters sit inside the 내 폰 안 zone; above the line is only the server counter", async () => {
  const { HowStory, HowStoryStage, createElement, renderToStaticMarkup } = await loadStageRenderer();
  const checkStage = (stage, plan, label) => {
    const all = descendants(stage);
    const texts = (key) => all.filter((node) => node.attrs["data-text"] === key);
    // Server box, then the line, then the zone, top to bottom.
    assert.deepEqual(stage.children.map((child) => child.attrs.class), ["how-server", "how-line", "how-zone"], label);
    const [server, , zone] = stage.children;
    const targets = all.filter((node) => "data-strip-target" in node.attrs);
    assert.equal(targets.length, 1, label);
    // The element that holds the copies' target (the box above the line) holds the server count and nothing else.
    assert.ok(ancestors(targets[0]).includes(server), label);
    assert.deepEqual(descendants(server).filter((node) => node.attrs["data-text"]).map((node) => node.attrs["data-text"]), ["server"], label);
    const phoneSide = ["checked", ...(plan.scan ? ["timer"] : []), ...(plan.taps ? ["taps"] : [])];
    for (const key of phoneSide) {
      const [element, ...extra] = texts(key);
      assert.ok(element && extra.length === 0, `${label}: one ${key}`);
      assert.ok(ancestors(element).includes(zone), `${label}: ${key} is inside the 내 폰 안 zone`);
      assert.ok(!ancestors(element).includes(server), `${label}: ${key} is not in the server box`);
    }
    // A null metric has no UI at all.
    assert.equal(texts("timer").length, plan.scan ? 1 : 0, label);
    assert.equal(texts("taps").length, plan.taps ? 1 : 0, label);
    // The scan count fades in with the zone (the checkedMeter node), so it never shows outside the dashed frame.
    assert.equal(ancestors(texts("checked")[0]).find((node) => node.attrs["data-node"])?.attrs["data-node"], "checkedMeter", label);
  };

  // The page as shipped, rendered on the server: the static list, one stage per scene.
  const page = parseMarkup(renderToStaticMarkup(createElement(HowStory)));
  const stills = descendants(page).filter((node) => hasClass(node, "how-stage"));
  assert.equal(stills.length, HOW_STORY_SCENE_COUNT);
  stills.forEach((stage, index) => checkStage(stage, getHowStoryPlan(), `still ${index + 1}`));

  // Every measured number filled in (test-only override), in both selection variants.
  for (const selection of HOW_STORY_SELECTIONS) {
    const options = { selection, metrics: { ...SAMPLE_METRICS, taps: getHowStoryTapRange(selection).max } };
    const plan = getHowStoryPlan(options);
    assert.deepEqual(plan.errors, []);
    assert.ok(plan.scan && plan.taps);
    for (let index = 0; index < HOW_STORY_SCENE_COUNT; index += 1) {
      const frame = getHowStoryFrame(stillProgress(index), options);
      const markup = renderToStaticMarkup(createElement(HowStoryStage, { frame, plan }));
      const [stage] = parseMarkup(markup).children;
      checkStage(stage, plan, `${selection} still ${index + 1}`);
    }
  }
});

test("how_story_view always reaches analytics before the first how_story_scene", () => {
  // A shared fake clock, wired the way HowStory.jsx wires the two trackers.
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
  // The story becomes eligible for a view, and 200 ms later the reader settles in the scene 1 hold.
  view.update(true);
  advance(200);
  reach.update({ sceneIndex: 0, inHold: true, eligible: true });
  advance(700);
  assert.deepEqual(sent, [["view", 900], ["scene 1", 900]]);
  // The view's own timer was cancelled: it never sends twice.
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

test("the skip link target clears the sticky header and the story end is centred from tablet width", () => {
  assert.match(storyCss, /\.how-story-end \{ display: flex; scroll-margin-top: 100px; \}/);
  assert.match(storyCss, /@media \(min-width: 561px\) \{[\s\S]*?\.how-story-end \{ justify-content: center; \}\s*\}/);
});

test("a static list re-checks scroll mode when the window grows taller, never on a phone's URL-bar flap", () => {
  assert.match(componentSource, /const STATIC_RECHECK_GROWTH = 100;/);
  assert.match(componentSource, /if \(modeRef\.current === "static" && grew\) reevaluate\(\);/);
  assert.match(componentSource, /lowestHeight < MIN_VIEWPORT_HEIGHT && height >= MIN_VIEWPORT_HEIGHT/);
});
