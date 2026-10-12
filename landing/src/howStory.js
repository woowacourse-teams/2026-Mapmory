// The #how app tour ("토스" rebuild): one pinned phone shows develop's record flow while the page scrolls.
// 01 장소 검색 -> 02 사진첩 확인(the camera pushes into the phone) -> 03 경주 사진 4장 -> 04 고르기 -> 05 지도.
// Pure: no React, no analytics, no DOM. Every frame is a function of scroll progress p (0..1) and the measured
// layout, so scrolling back up replays the same frames. All visible text lives here.
import { HOW_STORY_TEST_LAYOUT } from "./howStoryLayout.js";

export { HOW_STORY_TEST_LAYOUT };

export const HOW_STORY_EXPERIENCE_TYPE = "how_story";
export const HOW_STORY_SCENE_COUNT = 5;

// Section h2: quantity first, then the ask. Two lines, each at most 11 characters.
export const HOW_STORY_TITLE_LINES = Object.freeze(["사진이 수천 장이어도", "장소만 고르면 돼요"]);
export const HOW_STORY_TITLE = HOW_STORY_TITLE_LINES.join(" ");
export const HOW_STORY_EYEBROW = "사용 방법";

// Illustrative library size (the hero's number). It lives only on the stage, under the 예시 화면 pill.
export const HOW_STORY_LIBRARY_COUNT = 21474;

export const HOW_STORY_METRICS = Object.freeze({
  // Seconds for one photo find for a place over a whole library, on the shipping build. null = no time anywhere.
  scanSeconds: null,
  // Library size of that run (integer > 0). Required with scanSeconds.
  scanLibraryCount: null,
  // "{기기} · {처음 찾을 때|다시 찾을 때} · 20NN년 M월 내부 측정". Android caches photo metadata in Room
  // (PhotoMetadataSync), so first and repeat finds differ; the source must say which. Required with scanSeconds.
  scanSource: null,
  // Taps on the shipping build for exactly the taps this story draws, in develop's flow:
  // [search field, if counted], the 행정구역 result row (it starts the find), each photo picked (3), 기록하기.
  // So 5, or 6 with the field tap. The tap that opens the record flow and any map taps are not drawn or counted.
  // A count measured on main (이 장소 사진 찾기, 선택한 사진 추가, title, date) must stay null.
  taps: null,
  // "{기기·빌드} · 20NN년 M월 내부 측정". Required with taps.
  tapsSource: null,
});

// Travel (track height minus frame height) in units: one unit is one viewport on phones and travel / 4.3 on
// wheel devices.
export const HOW_STORY_TRAVEL_UNITS = 4.3;
export const HOW_STORY_TRACK = Object.freeze({
  wheelTrackMinWidth: 561,
  mobileTravelSvh: 430,
  desktopTravelSvh: 430,
  desktopMinPx: 3200,
  desktopMaxPx: 3800,
  stickyTop: Object.freeze({ mobile: 80, tablet: 88, desktop: 96 }),
  // Frame height = min(100svh - offset, max).
  frameOffset: Object.freeze({ mobile: 80, tablet: 104, desktop: 120 }),
  desktopMaxFrame: 760,
  tabletMaxFrame: 860,
});

// "zoom" pushes the camera into the phone; "fade" (the perf fallback) crossfades the library in at full size.
export const HOW_STORY_INWARD = "zoom";
// The picker list is shown scrolled by this much so both card rows sit high on the screen.
export const PICKER_SCROLL_DP = 48;

const photo = (name) => `/assets/photos/${name}.webp`;

// The four 경주 photos, in picker order (A, B on row 1; C, D on row 2). Coordinates were checked against the
// app's 경주시 boundary (47130). The cell is where each sits in the library window (see howStoryLayout.js).
export const HOW_STORY_MATCHES = Object.freeze([
  { id: "A", src: photo("gyeongbuk-02"), lat: 35.8347, lng: 129.219, cell: [0.22, 0.46], capturedAt: "2025-10-04", picked: true },
  { id: "B", src: photo("gyeongbuk-01"), lat: 35.79, lng: 129.332, cell: [0.7, 0.52], capturedAt: "2025-10-04", picked: true },
  { id: "C", src: photo("gyeongbuk-03"), lat: 35.93, lng: 129.23, cell: [0.38, 0.63], capturedAt: "2025-10-04", picked: true },
  { id: "D", src: photo("gyeongbuk-04"), lat: 35.78, lng: 129.09, cell: [0.8, 0.7], capturedAt: "2025-10-04", picked: false },
].map((match) => Object.freeze({ ...match, cell: Object.freeze(match.cell) })));

export const HOW_STORY_TEXT = Object.freeze({
  skip: "사진 찾는 과정 건너뛰기",
  pill: "예시 화면",
  overlayChecked: "확인한 사진",
  overlayValue: "{n}장",
  overlayScope: "{total}장 중 경주 사진",
  band: Object.freeze({
    eyebrow: "직접 재 봤어요",
    scanLabel: "사진 {count}장을 확인하는 데 걸린 시간",
    scanValue: "{s}초",
    tapsLabel: "장소 검색부터 저장까지 누른 횟수",
    tapsValue: "{n}번",
    footnote: "{source} 기준",
  }),
});

// Every in-phone string. These are develop's UI strings (NewTripRecordFlowScreen.kt, TripMapScreen.kt,
// TripRecordComponents.kt on origin/develop); the place, the IME steps and the date are the example's.
export const APP_SCREEN_TEXT = Object.freeze({
  back: "←",
  import: Object.freeze({
    title: "사진 불러오기",
    action: "완료",
    eyebrow: "NEW ALBUM",
    heading: "어디 사진을 불러올까요?",
    section: "장소",
    badge: "필수",
    helper: "지역과 장소를 한 글자부터 검색해요.",
    placeholder: "장소 또는 행정구역 검색",
    ime: Object.freeze(["ㄱ", "겨", "경", "경ㅈ", "경주"]),
    resultsLabel: "행정구역 검색 결과",
    scope: "국내",
    place: "경상북도 경주시",
    chevron: "›",
    check: "✓",
    button: "사진 불러오기",
  }),
  loading: Object.freeze({
    title: "사진 불러오기",
    eyebrow: "PHOTO LIBRARY",
    heading: "사진을 정리하고 있어요",
    // Kotlin string interpolation of an Int: no thousands separator.
    count: "{processed} / {total}장",
    percent: "{p}%",
    message: "{place} 사진을 불러오는 중입니다.",
    place: "경주시",
  }),
  picker: Object.freeze({
    title: "사진 고르기",
    action: "기록하기",
    eyebrow: "PHOTO LIBRARY",
    heading: "{place} 사진 고르기",
    place: "경주시",
    count: "{n}장 선택",
    selectAll: "추천 사진 모두 선택",
    fromGallery: "사진첩에서 선택",
    note: Object.freeze(["날짜별 선택은 현재 불러온 사진에 적용돼요.", "추천 사진은 최대 {max}장까지 선택할 수 있어요."]),
    // TripRecordPhotoRules.MaxPhotosPerRecord on develop.
    max: 100,
    filter: "이미 기록에 포함된 사진 숨기기",
    date: "2025. 10. 04.",
    dateCount: "{n}장",
    selectDate: "이 날짜 사진 선택",
  }),
  map: Object.freeze({
    logo: Object.freeze(["Map", "mory"]),
    scopeKorea: "⌖ 대한민국",
    scopeWorld: "◎ 전세계",
    titleKorea: "나의 대한민국 지도",
    titleWorld: "나의 세계 지도",
    back: "← {province} 전체에서 나가기",
    province: "경상북도",
    total: " / {total}",
    percent: "{pct}% 채움",
    tabs: Object.freeze(["지도", "일지", "통계"]),
  }),
});

// Five beats, about one viewport each. units: share of the 4.3-unit travel. holdStart: local t where the still
// hold starts. Headlines are two 해요체 lines (<= 11 characters each); subs are one line (<= 30, ending in 요).
export const HOW_STORY_BEATS = Object.freeze([
  {
    id: "place",
    units: 0.75,
    holdStart: 0.6,
    eyebrow: "01",
    headline: ["다녀온 곳을", "검색해요"],
    sub: "시·군·구나 나라 단위로 골라요",
    description: "예시 화면이에요. 사진 불러오기 화면의 장소 검색에 경주를 입력하고, 행정구역 검색 결과에서 경상북도 경주시를 골라요.",
  },
  {
    id: "scan",
    units: 1,
    holdStart: 0.5,
    eyebrow: "02",
    headline: ["사진에 저장된", "위치로 찾아요"],
    sub: "사진 확인은 폰 안에서 해요",
    description: "고른 장소의 사진을 찾을 때, 폰이 사진마다 저장된 촬영 위치를 앱에 들어 있는 경주시 경계와 비교해요. 이 예시에서는 사진첩 {total}장을 처음부터 끝까지 확인해요. 사진 확인은 폰 안에서 해요.",
  },
  {
    id: "match",
    units: 0.8,
    holdStart: 0.42,
    eyebrow: "03",
    headline: ["경주에서 찍은", "사진만 찾았어요"],
    sub: "나머지 사진은 그대로 있어요",
    description: "이 예시에서는 사진첩 {total}장 가운데 경주에서 찍은 사진 4장을 찾았어요. 나머지 사진은 사진첩에 그대로 있어요.",
  },
  {
    id: "pick",
    units: 0.85,
    holdStart: 0.8,
    eyebrow: "04",
    headline: ["마음에 드는 사진만", "기록에 담아요"],
    sub: "저장할 때 고른 사진만 올라가요",
    description: "찾은 사진 4장 가운데 기록에 담고 싶은 3장을 탭해서 골라요. 기록을 저장하면 고른 3장만 Mapmory 서버로 올라가고, 고르지 않은 1장과 나머지 사진첩은 폰에 그대로 있어요.",
  },
  {
    id: "map",
    units: 0.9,
    holdStart: 0.8,
    eyebrow: "05",
    headline: ["다녀온 곳이", "지도에 칠해져요"],
    sub: "경주시, 경상북도, 대한민국까지요",
    description: "기록 하나로 앱 지도에서 경주시(시·군·구), 경상북도(시·도), 대한민국(나라)이 함께 칠해져요.",
  },
].map((beat) => Object.freeze({ ...beat, headline: Object.freeze(beat.headline) })));

// p windows: cumulative units / 4.3, contiguous from 0 to 1.
export const HOW_STORY_WINDOWS = Object.freeze(HOW_STORY_BEATS.map((beat, index) => {
  const before = HOW_STORY_BEATS.slice(0, index).reduce((sum, { units }) => sum + units, 0);
  const end = index === HOW_STORY_BEATS.length - 1 ? 1 : (before + beat.units) / HOW_STORY_TRAVEL_UNITS;
  return Object.freeze([before / HOW_STORY_TRAVEL_UNITS, end]);
}));

export const clampUnit = (value) => Math.min(1, Math.max(0, value));
export const seg = (t, a, b) => clampUnit((t - a) / (b - a));
export const lerp = (a, b, u) => a + (b - a) * u;

// CSS cubic-bezier as a function of x, solved with Newton steps and a bisection fallback, so scrubbed motion
// and CSS transitions share one curve.
export function cubicBezier(x1, y1, x2, y2) {
  const cx = 3 * x1;
  const bx = 3 * (x2 - x1) - cx;
  const ax = 1 - cx - bx;
  const cy = 3 * y1;
  const by = 3 * (y2 - y1) - cy;
  const ay = 1 - cy - by;
  const sampleX = (s) => ((ax * s + bx) * s + cx) * s;
  const sampleY = (s) => ((ay * s + by) * s + cy) * s;
  const slopeX = (s) => (3 * ax * s + 2 * bx) * s + cx;
  return (u) => {
    if (u <= 0) return 0;
    if (u >= 1) return 1;
    let s = u;
    for (let step = 0; step < 8; step += 1) {
      const error = sampleX(s) - u;
      if (Math.abs(error) < 1e-7) return sampleY(s);
      const slope = slopeX(s);
      if (Math.abs(slope) < 1e-6) break;
      s -= error / slope;
      if (s < 0 || s > 1) break;
    }
    let low = 0;
    let high = 1;
    s = u;
    for (let step = 0; step < 50; step += 1) {
      const x = sampleX(s);
      if (Math.abs(x - u) < 1e-7) break;
      if (x < u) low = s;
      else high = s;
      s = (low + high) / 2;
    }
    return sampleY(s);
  };
}
export const EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);
export const EASE_IN_OUT = cubicBezier(0.77, 0, 0.175, 1);
// Only for the beat 02 counter and pin wave (ease-out-cubic).
export const COUNT = cubicBezier(0.33, 1, 0.68, 1);
// Landings: a damped spring that overshoots a little and settles.
export const spring = (u) => (u <= 0 ? 0 : u >= 1 ? 1 : 1 - Math.exp(-8 * u) * Math.cos(7 * u));

const numberFormat = new Intl.NumberFormat("ko-KR");
export function formatHowStoryNumber(value) {
  return numberFormat.format(value);
}
export function fillHowStoryText(template, values = {}) {
  return template.replace(/\{(\w+)\}/g, (match, key) => (key in values ? String(values[key]) : match));
}
// The app's own map percentage: (visited / total * 100).roundToInt() (TripMapScreen.kt).
export const mapPercent = (visited, total) => Math.round((visited / total) * 100);

// Progress across the sticky travel.
export function getHowStoryProgress({ trackTop, trackHeight, frameHeight, stickyTop }) {
  const travel = Math.max(1, trackHeight - frameHeight);
  return clampUnit((stickyTop - trackTop) / travel);
}

export function getHowStoryScene(p) {
  const progress = clampUnit(p);
  const index = HOW_STORY_WINDOWS.findIndex(([, end], i) => progress < end || i === HOW_STORY_SCENE_COUNT - 1);
  const [start, end] = HOW_STORY_WINDOWS[index];
  return { index, t: seg(progress, start, end) };
}

export function isHowStoryHold(p) {
  const { index, t } = getHowStoryScene(p);
  return t >= HOW_STORY_BEATS[index].holdStart;
}

// The still for beat i: the end of its hold (the very end for the last beat).
export function stillProgress(index) {
  return index === HOW_STORY_SCENE_COUNT - 1 ? 1 : HOW_STORY_WINDOWS[index][1] - 0.001;
}

const beatP = (scene, t) => {
  const [start, end] = HOW_STORY_WINDOWS[scene];
  return start + t * (end - start);
};

// Bounding box, then ray casting with an on-edge check: the same test as the app's PhotoRecommendationRegion.contains.
export function isInsideRings(lng, lat, rings) {
  return rings.some((ring) => {
    if (ring.length < 3) return false;
    const lngs = ring.map(([x]) => x);
    const lats = ring.map(([, y]) => y);
    if (lng < Math.min(...lngs) || lng > Math.max(...lngs) || lat < Math.min(...lats) || lat > Math.max(...lats)) return false;
    let inside = false;
    let [previousLng, previousLat] = ring.at(-1);
    for (const [currentLng, currentLat] of ring) {
      const cross = (lat - previousLat) * (currentLng - previousLng) - (lng - previousLng) * (currentLat - previousLat);
      if (Math.abs(cross) <= 1e-9
        && lng >= Math.min(previousLng, currentLng) && lng <= Math.max(previousLng, currentLng)
        && lat >= Math.min(previousLat, currentLat) && lat <= Math.max(previousLat, currentLat)) return true;
      if ((currentLat > lat) !== (previousLat > lat)) {
        const crossingLng = ((previousLng - currentLng) * (lat - currentLat)) / (previousLat - currentLat) + currentLng;
        if (lng < crossingLng) inside = !inside;
      }
      previousLng = currentLng;
      previousLat = currentLat;
    }
    return inside;
  });
}

// Taps the story draws, equal to the taps in develop's flow: the 행정구역 row (it starts the find), the three
// photos picked and 기록하기. The search-field tap is drawn only when the measured count includes it.
const DRAWN_TAPS = Object.freeze([
  Object.freeze({ id: "field", scene: 0, t: 0.04, optional: true }),
  Object.freeze({ id: "row", scene: 0, t: 0.42 }),
  Object.freeze({ id: "pick-0", scene: 3, t: 0.36 }),
  Object.freeze({ id: "pick-1", scene: 3, t: 0.44 }),
  Object.freeze({ id: "pick-2", scene: 3, t: 0.52 }),
  Object.freeze({ id: "save", scene: 3, t: 0.62 }),
]);

export function getHowStoryTapRange() {
  const core = DRAWN_TAPS.filter(({ optional }) => !optional).length;
  return { min: core, max: DRAWN_TAPS.length };
}

export function getHowStoryTapPlan(tapCount = null) {
  const withField = tapCount === getHowStoryTapRange().max;
  return DRAWN_TAPS
    .filter(({ optional }) => !optional || withField)
    .map(({ id, scene, t }) => ({ id, scene, t, p: beatP(scene, t) }));
}

// How many drawn taps lie between two progress values (the hook skips rings and pulses for a fast flick).
export function countTapsCrossed(fromP, toP, beats = getHowStoryTapPlan()) {
  const low = Math.min(fromP, toP);
  const high = Math.max(fromP, toP);
  return beats.filter(({ p }) => p > low && p <= high).length;
}

const isFilled = (value) => value !== null && value !== undefined;
const nonEmptyString = (value) => typeof value === "string" && value.trim().length > 0;
const MONTH = /20\d\d년 \d{1,2}월/;

// Turns the raw constants into what the page may show. An incomplete or impossible metric is dropped (its UI
// stays absent) and reported in errors, which the tests require to be empty for the committed constants.
export function resolveHowStoryMetrics(metrics = HOW_STORY_METRICS) {
  const errors = [];
  let scan = null;
  if (isFilled(metrics.scanSeconds)) {
    if (!(Number.isFinite(metrics.scanSeconds) && metrics.scanSeconds > 0)) errors.push("scanSeconds must be a positive number");
    else if (!(Number.isInteger(metrics.scanLibraryCount) && metrics.scanLibraryCount > 0)) errors.push("scanLibraryCount is required with scanSeconds");
    else if (!nonEmptyString(metrics.scanSource)) errors.push("scanSource is required with scanSeconds");
    else if (!/처음 찾을 때|다시 찾을 때/.test(metrics.scanSource)) errors.push("scanSource must say 처음 찾을 때 or 다시 찾을 때");
    else if (!MONTH.test(metrics.scanSource)) errors.push("scanSource must carry a month");
    else scan = { seconds: metrics.scanSeconds, libraryCount: metrics.scanLibraryCount, source: metrics.scanSource.trim() };
  }
  let taps = null;
  if (isFilled(metrics.taps)) {
    const { min, max } = getHowStoryTapRange();
    if (!Number.isInteger(metrics.taps)) errors.push("taps must be an integer");
    else if (metrics.taps < min || metrics.taps > max) errors.push(`taps ${metrics.taps} cannot be shown: the story draws ${min}–${max} taps`);
    else if (!nonEmptyString(metrics.tapsSource)) errors.push("tapsSource is required with taps");
    else if (!MONTH.test(metrics.tapsSource)) errors.push("tapsSource must carry a month");
    else taps = { count: metrics.taps, source: metrics.tapsSource.trim() };
  }
  return { scan, taps, errors };
}

const planCache = new Map();
export function getHowStoryPlan({ metrics = HOW_STORY_METRICS } = {}) {
  const key = JSON.stringify(metrics);
  if (!planCache.has(key)) {
    const resolved = resolveHowStoryMetrics(metrics);
    planCache.set(key, Object.freeze({ ...resolved, beats: getHowStoryTapPlan(resolved.taps?.count ?? null) }));
  }
  return planCache.get(key);
}

// The measured stats band after the pin: null (nothing renders) unless at least one stat resolves.
export function getHowStoryBand(options = {}) {
  const { scan, taps } = getHowStoryPlan(options);
  const { band } = HOW_STORY_TEXT;
  const stats = [];
  if (scan) {
    stats.push({
      id: "scan",
      label: fillHowStoryText(band.scanLabel, { count: formatHowStoryNumber(scan.libraryCount) }),
      value: fillHowStoryText(band.scanValue, { s: Number.isInteger(scan.seconds) ? scan.seconds : scan.seconds.toFixed(1) }),
      footnote: fillHowStoryText(band.footnote, { source: scan.source }),
    });
  }
  if (taps) {
    stats.push({
      id: "taps",
      label: band.tapsLabel,
      value: fillHowStoryText(band.tapsValue, { n: taps.count }),
      footnote: fillHowStoryText(band.footnote, { source: taps.source }),
    });
  }
  return stats.length ? { eyebrow: band.eyebrow, stats } : null;
}

export function getHowStorySceneDescription(index) {
  return fillHowStoryText(HOW_STORY_BEATS[index].description, { total: formatHowStoryNumber(HOW_STORY_LIBRARY_COUNT) });
}

// Beat 02's counter: the app's real 1% steps (101 values), front-loaded with COUNT.
export function loadingStep(t) {
  const k = Math.floor(100 * COUNT(seg(t, 0.12, 0.48)) + 1e-9);
  return { k, processed: Math.floor((HOW_STORY_LIBRARY_COUNT * k) / 100) };
}

// The pin wave's edge, as a fraction of the library window: it crosses exactly the visible band as k runs.
export function waveEdge(k, layout) {
  if (k <= 0) return 0;
  const { vTop, vBottom } = layout.window;
  return vTop + (k / 100) * (vBottom - vTop);
}

const round = (value, digits = 2) => Number(value.toFixed(digits));
const fade = (o) => ({ o: round(clampUnit(o), 3) });
const move = (tx, ty, s = 1, o) => {
  const node = { tx: round(tx), ty: round(ty), s: round(s, 4) };
  if (o !== undefined) node.o = round(clampUnit(o), 3);
  return node;
};

// One frame of the story. nodes are written as transform/opacity, texts as text, state as data-* on the stage.
export function getHowStoryFrame(p, layout = HOW_STORY_TEST_LAYOUT.mobile, options = {}) {
  const { inward = HOW_STORY_INWARD } = options;
  const plan = getHowStoryPlan(options);
  const progress = clampUnit(p);
  const { index: sceneIndex, t } = getHowStoryScene(progress);
  const [t1, t2, t3, t4, t5] = HOW_STORY_WINDOWS.map(([start, end]) => seg(progress, start, end));
  const L = layout;
  const nodes = {};

  // Camera: in over beat 02 t .06-.24, out over beat 04 t 0-.28 (e = 1 means inside the phone).
  const outward = progress >= HOW_STORY_WINDOWS[3][0];
  const inE = EASE_IN_OUT(seg(t2, 0.06, 0.24));
  const outE = EASE_IN_OUT(seg(t4, 0, 0.28));
  const e = outward ? 1 - outE : inE;
  const zoom = inward === "zoom";
  const z = zoom ? lerp(1, L.kIn, e) : 1;
  nodes.phoneCamera = zoom ? move(L.d.x * e, L.d.y * e, z) : move(0, 0, 1);
  nodes.phoneFade = fade(outward ? seg(outE, 0.3, 0.9) : 1 - seg(inE, 0.25, 0.7));
  nodes.mosaicWindow = zoom
    ? move((e - 1) * L.d.x, (e - 1) * L.d.y, z / L.kIn, outward ? 1 - seg(outE, 0, 0.5) : seg(inE, 0.15, 0.6))
    : move(0, 0, 1, outward ? 1 - seg(outE, 0, 0.5) : seg(inE, 0.15, 0.6));
  const ground = outward ? 1 - seg(outE, 0.1, 0.6) : seg(inE, 0.3, 0.8);
  nodes.ground = fade(ground);
  // The bottom fade blends the cropped phone into the canvas; it hides while the ground is up.
  nodes.bottomFade = fade(1 - ground);

  // Screens inside the phone (screen px). Beat 02 pushes 사진 불러오기 out and the loading screen in.
  const push = EASE_IN_OUT(seg(t2, 0, 0.08));
  const mapIn = EASE_OUT(seg(t5, 0, 0.12));
  nodes.screenImport = move(-0.3 * L.screen.w * push, 0, 1, outward ? 0 : 1 - push);
  nodes.screenLoading = move(L.screen.w * (1 - push), 0, 1, outward ? 0 : 1);
  nodes.screenPicker = fade(outward ? 1 - mapIn : 0);
  nodes.screenMap = fade(mapIn);

  // Counter and pin wave share one number, so they never disagree.
  const { k, processed } = loadingStep(t2);
  nodes.loadingBar = { sx: round(k / 100, 2) };
  nodes.overlayBarFill = { sx: round(k / 100, 2) };
  const edge = waveEdge(k, L);
  const reveal = L.window.h * (1 - edge);
  nodes.pinsReveal = move(0, -reveal);
  nodes.pinsInner = move(0, reveal);

  // Beat 03: the other photos dim (set aside, not removed); the pins and the overlay bar go.
  const dim = EASE_OUT(seg(t3, 0, 0.2));
  nodes.mosaicSprite = fade(1 - 0.84 * dim);
  nodes.pinsLayer = fade(1 - dim);
  nodes.overlayBar = fade(1 - dim);
  const overlayIn = EASE_OUT(seg(t2, 0.16, 0.24));
  nodes.overlay = outward ? move(0, 0, 1, 1 - seg(outE, 0, 0.3)) : move(0, 12 * (1 - overlayIn), 1, overlayIn);

  // The four 경주 photos: in the library until their flight starts, then the stage tiles take over at the same
  // rect and fly (spring, staggered) to a 2x2 cluster. The beat 04 picker (static for now) brings its own cards.
  const pins = L.cells.filter(({ popAt }) => k > 0 && edge >= popAt).length;
  const leave = outward ? 1 - seg(t4, 0, 0.1) : 1;
  L.cells.forEach((cell, i) => {
    const start = 0.06 + 0.04 * i;
    const started = progress >= HOW_STORY_WINDOWS[2][0] && t3 >= start;
    const flight = seg(t3, start, start + 0.18);
    const u = spring(flight);
    const target = L.cluster[i];
    const size = lerp(cell.size, target.size, u);
    nodes[`matchCell${i}`] = fade(started ? 0 : 1);
    nodes[`matchMark${i}`] = fade(1 - seg(t3, start, start + 0.05));
    nodes[`match${i}`] = move(lerp(cell.x, target.x, u), lerp(cell.stageY, target.y, u), size / L.nodeBase, started ? leave : 0);
    nodes[`matchShadow${i}`] = fade(flight);
  });

  nodes.pill = fade(1);

  const taps = plan.taps?.count ?? null;
  const state = {
    scene: sceneIndex + 1,
    tapField: taps === getHowStoryTapRange().max && t1 >= 0.04 ? 1 : 0,
    typed: t1 >= 0.08 ? 1 : 0,
    results: t1 >= 0.26 ? 1 : 0,
    row: t1 >= 0.42 ? 1 : 0,
    pins,
    found: t3 >= 0.14 ? 1 : 0,
    // Beats 04-05 are static end states in this build step: three photos picked, 경주시 filled.
    sel: outward ? 3 : 0,
    save: 0,
    map: "drill1",
  };

  const texts = {
    loadingCount: fillHowStoryText(APP_SCREEN_TEXT.loading.count, { processed, total: HOW_STORY_LIBRARY_COUNT }),
    loadingPct: fillHowStoryText(APP_SCREEN_TEXT.loading.percent, { p: k }),
    overlayValue: fillHowStoryText(HOW_STORY_TEXT.overlayValue, { n: formatHowStoryNumber(processed) }),
  };

  return { p: progress, sceneIndex, t, e: round(e, 4), nodes, texts, attrs: {}, state };
}

// Inline style for one frame node; the scroll hook writes the same strings straight to the DOM.
export function getHowStoryNodeStyle(node) {
  const style = {};
  if (node.tx !== undefined) style.transform = `translate3d(${node.tx}px, ${node.ty}px, 0) scale(${node.s})`;
  else if (node.sx !== undefined) style.transform = `scaleX(${node.sx})`;
  if (node.o !== undefined) style.opacity = String(node.o);
  return style;
}
