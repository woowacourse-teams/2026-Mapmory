import assert from "node:assert/strict";
import { access, readFile } from "node:fs/promises";
import test from "node:test";
import { PHOTO_FINDER_LIBRARY_PHOTOS, PHOTO_FINDER_PLACES } from "../src/photoFinderDemo.js";
import {
  HOW_STORY_COPY_SLOTS,
  HOW_STORY_FILLERS,
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
import { createPassiveViewTracker, createSceneReachTracker } from "../src/howStoryAnalytics.js";

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

const travelAt = (width, height) => {
  const desktop = width > 900;
  const trackHeight = desktop
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
  for (const [width, height, minimum] of [[1280, 720, 120], [360, 640, 100]]) {
    const travel = travelAt(width, height);
    HOW_STORY_SCENES.forEach((scene, index) => {
      const hold = (scene.window[1] - holdBegin(scene)) * travel;
      assert.ok(hold >= minimum, `${width}x${height} scene ${index + 1} hold ${hold.toFixed(1)}px`);
    });
  }
  assert.equal(travelAt(1280, 720), 1840);
  assert.equal(travelAt(360, 640), 1504);
  assert.match(storyCss, /height: 320svh;/);
  assert.match(storyCss, /height: clamp\(2300px, 340svh, 3000px\);/);
  for (const top of [80, 88, 96]) assert.match(storyCss, new RegExp(`top: ${top}px;`));
  for (const offset of [96, 104, 112]) assert.match(storyCss, new RegExp(`height: calc\\(100svh - ${offset}px\\);`));
  assert.match(storyCss, /max-height: 860px;/);
  assert.match(storyCss, /max-height: 760px;/);
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
  assert.equal(plan.beats.length, 6);
  assert.equal(getHowStoryFrame(0, options).texts.timer, "0.0초");
  assert.equal(getHowStoryFrame(0, options).texts.taps, "0번");
  const scanned = getHowStoryFrame(holdEnd(1), options);
  assert.equal(scanned.texts.timer, "6.2초");
  assert.equal(scanned.texts.checked, "14,213 / 14,213장");
  assert.equal(getHowStoryFrame(holdEnd(2), options).texts.pill, "14,213장 중 경주시 사진 4장");
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
  // A seventh tap adds the search-field tap; the prechecked choreography has room for 4-5 taps only.
  assert.equal(getHowStoryPlan({ metrics: { ...SAMPLE_METRICS, taps: 7 } }).beats[0].id, "search");
  assert.deepEqual(getHowStoryTapRange("tap-to-check"), { min: 6, max: 7 });
  assert.deepEqual(getHowStoryTapRange("prechecked"), { min: 4, max: 5 });
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
