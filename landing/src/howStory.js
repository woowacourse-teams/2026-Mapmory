// The #how app tour ("토스" rebuild): one pinned phone shows develop's record flow while the page scrolls.
// 01 장소 검색 -> 02 사진첩 확인(the camera pushes into the phone) -> 03 경주 사진 4장 -> 04 고르기 -> 05 지도.
// Pure: no React, no analytics, no DOM. Every frame is a function of scroll progress p (0..1) and the measured
// layout, so scrolling back up replays the same frames. All visible text lives here.
import { APP_MAP_VIEW, HOW_STORY_APP_PROGRESS, HOW_STORY_TEST_LAYOUT, PICKER_SCROLL_DP, mapCameraBox } from "./howStoryLayout.js";

export { HOW_STORY_TEST_LAYOUT, PICKER_SCROLL_DP };

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

const photo = (name) => `/assets/photos/${name}.webp`;

// The four 경주 photos, in picker order (A, B on row 1; C, D on row 2). Coordinates were checked against the
// app's 경주시 boundary (47130). Where each sits in the library window is HOW_STORY_MATCH_CELLS (howStoryLayout.js);
// all four share the picker's date header (APP_SCREEN_TEXT.picker.date).
export const HOW_STORY_MATCHES = Object.freeze([
  { id: "A", src: photo("gyeongbuk-02"), lat: 35.8347, lng: 129.219, picked: true },
  { id: "B", src: photo("gyeongbuk-01"), lat: 35.79, lng: 129.332, picked: true },
  { id: "C", src: photo("gyeongbuk-03"), lat: 35.93, lng: 129.23, picked: true },
  { id: "D", src: photo("gyeongbuk-04"), lat: 35.78, lng: 129.09, picked: false },
].map((match) => Object.freeze(match)));

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
    // The app versions differ in how the choice is made (develop: tap to pick from none; main: every found photo
    // starts checked, untick the rest), so the sentence says only that three are chosen, in the example's frame.
    description: "이 예시에서는 찾은 사진 4장 가운데 3장을 골라 기록에 담아요. 기록을 저장하면 고른 3장만 Mapmory 서버로 올라가고, 고르지 않은 1장과 나머지 사진첩은 폰에 그대로 있어요.",
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
// Scrubbed cameras and flights (ease-in-out-sine): no steep middle, so a single wheel notch or flick shows the
// in-between frames instead of a jump. (EASE_IN_OUT's middle covers 80% of a move in a fifth of its window.)
export const GLIDE = cubicBezier(0.37, 0, 0.63, 1);
// Only for the beat 02 counter and pin wave (ease-out-cubic).
export const COUNT = cubicBezier(0.33, 1, 0.68, 1);

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
  // Beat 04: once the cards have landed (t .35), three picks and 기록하기, so the copies can lift over a wide window.
  Object.freeze({ id: "pick-0", scene: 3, t: 0.37 }),
  Object.freeze({ id: "pick-1", scene: 3, t: 0.42 }),
  Object.freeze({ id: "pick-2", scene: 3, t: 0.47 }),
  Object.freeze({ id: "save", scene: 3, t: 0.51 }),
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

// A jump shows the end state without tap rings: the story was entered by a reload (the hook's first frame), or the
// reader moved more than px within the last ms (a spun wheel, a key press), or moved faster than speed px/s across
// the updates inside that window (a flick, from its second frame). One wheel notch (100-120px) is not a jump, instant
// (one update, no speed to measure) or smooth (about 1000px/s at most), even where it crosses all four of beat 04's
// taps (about 35px apart, so a notch crosses two or three): its taps play, one after another
// (HOW_STORY_TAP_STAGGER_MS apart, how-app-screens.css), in the order crossed (round-3 FE2: counting two taps crossed
// in one update as a jump skipped nearly every ring for a wheel).
export const HOW_STORY_JUMP = Object.freeze({ px: 180, ms: 120, speed: 2000 });
export const HOW_STORY_TAP_STAGGER_MS = 100;

// Feed it (time in ms, scroll position in px) on every update; it answers whether this update is part of a jump: the
// distance from the position at the window's start (the newest sample at or before it, else the oldest kept), or the
// speed from the first update inside the window to this one.
export function createHowStoryJumpTracker(limits = HOW_STORY_JUMP) {
  let samples = [];
  return (time, position) => {
    samples.push([time, position]);
    let base = 0;
    for (let index = samples.length - 1; index >= 0; index -= 1) {
      if (samples[index][0] <= time - limits.ms) {
        base = index;
        break;
      }
    }
    samples = samples.slice(base);
    if (Math.abs(position - samples[0][1]) > limits.px) return true;
    const inside = samples.find(([at]) => at > time - limits.ms);
    const elapsed = time - inside[0];
    return elapsed >= 8 && (Math.abs(position - inside[1]) / elapsed) * 1000 > limits.speed;
  };
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

// The measured stats band after the pin: null (nothing renders) unless at least one stat resolves. Each stat's
// count says how its value counts up (to, in steps of one decimal place when the value has one).
export function getHowStoryBand(options = {}) {
  const { scan, taps } = getHowStoryPlan(options);
  const { band } = HOW_STORY_TEXT;
  const stats = [];
  if (scan) {
    const count = Object.freeze({ to: scan.seconds, decimals: Number.isInteger(scan.seconds) ? 0 : 1, template: band.scanValue, slot: "s" });
    stats.push(Object.freeze({
      id: "scan",
      label: fillHowStoryText(band.scanLabel, { count: formatHowStoryNumber(scan.libraryCount) }),
      value: formatHowStoryBandValue(count, count.to),
      footnote: fillHowStoryText(band.footnote, { source: scan.source }),
      count,
    }));
  }
  if (taps) {
    const count = Object.freeze({ to: taps.count, decimals: 0, template: band.tapsValue, slot: "n" });
    stats.push(Object.freeze({
      id: "taps",
      label: band.tapsLabel,
      value: formatHowStoryBandValue(count, count.to),
      footnote: fillHowStoryText(band.footnote, { source: taps.source }),
      count,
    }));
  }
  return stats.length ? Object.freeze({ eyebrow: band.eyebrow, stats: Object.freeze(stats) }) : null;
}

// One band value at n (the count-up writes these; n = count.to is the final value).
export function formatHowStoryBandValue(count, n) {
  return fillHowStoryText(count.template, { [count.slot]: n.toFixed(count.decimals) });
}

// The band's count-up: 0 -> to over HOW_STORY_BAND_COUNT_MS with EASE_OUT, floored to the value's decimal place.
export const HOW_STORY_BAND_COUNT_MS = 900;
export function bandCountAt(count, u) {
  if (u >= 1) return count.to;
  const step = 10 ** count.decimals;
  return Math.floor(EASE_OUT(clampUnit(u)) * count.to * step + 1e-9) / step;
}

export function getHowStorySceneDescription(index) {
  return fillHowStoryText(HOW_STORY_BEATS[index].description, { total: formatHowStoryNumber(HOW_STORY_LIBRARY_COUNT) });
}

// The app's percent for a count (PhotoLoadingProgress.percentage on develop: processed * 100 / total, floored).
export const appLoadingPercent = (processed, total = HOW_STORY_LIBRARY_COUNT) => Math.min(100, Math.max(0, Math.floor((processed * 100) / total)));
// The smallest count the app shows with k%, so every count and percent drawn together is a pair the app draws.
export const appLoadingCount = (k, total = HOW_STORY_LIBRARY_COUNT) => Math.ceil((total * k) / 100);

// Beat 02's counter: the app's real 1% steps (101 values), front-loaded with COUNT.
export function loadingStep(t) {
  const k = Math.floor(100 * COUNT(seg(t, 0.12, 0.48)) + 1e-9);
  return { k, processed: appLoadingCount(k) };
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

// develop's determinate LinearProgressIndicator (Material3 1.4, PhotoLoadingBar): a round-capped indicator, a
// gap, then the track from the gap to the end, with a stop dot at the track's end. Both parts are full-width
// elements placed by translateX (never by width or scaleX, which would squash the round ends): fill is where the
// indicator's right end sits relative to the bar's right edge, track where the track's left end sits. px.
export function linearProgressPlacement(k, bar, gap) {
  const { w, h } = bar;
  if (k <= 0) return { fill: -w, track: 0 };
  const cap = h / 2;
  const fraction = k / 100;
  const fillEnd = Math.min(w, Math.max(fraction * w, cap) + cap);
  const trackStart = fraction + Math.min(fraction, (gap + h) / w);
  const track = trackStart >= 1 ? w : Math.min(w - cap, Math.max(trackStart * w, cap)) - cap;
  return { fill: round(fillEnd - w), track: round(track) };
}

// Beat-local times (t) the frame and the CSS share. Scrubbed moves ride GLIDE over windows wide enough that each
// takes at least 100px of scroll from 10% to 90% on every wheel device (one unit is at least 744px there) and at
// 390x844 (a test sweeps them). Screen changes inside the phone are app navigation, so they play in time on a
// threshold (how-story.css), like the copy swap: a whole screen moved by scroll crossed the phone inside one wheel
// notch (round-2 V1).
export const HOW_STORY_TIMING = Object.freeze({
  // Beat 02: the camera pushes into the phone (the push to the loading screen plays on the scene change); the dark
  // ground comes up under it over its own window, from the beat's start, so it still leads the library into the
  // screen but takes over 100px of scroll (round-3 V2: riding the camera's first third, the whole stage went from the
  // light canvas to near black inside one wheel notch). Outward it fades over the whole camera move.
  inward: Object.freeze([0.06, 0.36]),
  ground: Object.freeze([0, 0.28]),
  // Beat 03: the found photos fly from the library to the cluster, one after another.
  cluster: Object.freeze({ start: 0.04, stagger: 0.02, length: 0.3 }),
  // Beat 04: the camera comes back out while the photos fly on to the picker's cards; the card chrome comes on once
  // all four have landed; then the taps (DRAWN_TAPS) and the three copies lift into the print stack.
  outward: Object.freeze([0, 0.28]),
  cards: Object.freeze({ start: 0, stagger: 0.015, length: 0.26 }),
  chrome: 0.315,
  lift: Object.freeze([0.525, 0.795]),
  // Beat 05 (thresholds; the moves play in time): the print stack rises away on the scene change; the picker tab goes
  // and the map tab comes on one threshold, the map waiting out the picker's fade in time (how-story.css), so no
  // scroll position rests on an empty phone (round-3 V3: a scroll band between the two left it blank); 경주시 fills,
  // the map camera pulls back (scrubbed), and the world tab shows.
  mapIn: 0.09,
  fill: 0.26,
  mapCamera: Object.freeze([0.38, 0.66]),
  world: 0.72,
});

// One frame of the story. nodes are written as transform/opacity, texts as text, state as data-* on the stage.
// A layer nobody can see gets no writes: the loading screen's counter, percent and bar stop once the screen has
// gone clear, and the overlay's number and bar start only when the overlay shows (the hook keeps the last
// value; the frame emits the right one again as soon as the layer is visible).
export function getHowStoryFrame(p, layout = HOW_STORY_TEST_LAYOUT.mobile, options = {}) {
  const { inward = HOW_STORY_INWARD } = options;
  const plan = getHowStoryPlan(options);
  const progress = clampUnit(p);
  const { index: sceneIndex, t } = getHowStoryScene(progress);
  const [t1, t2, t3, t4, t5] = HOW_STORY_WINDOWS.map(([start, end]) => seg(progress, start, end));
  const L = layout;
  const nodes = {};
  const texts = {};

  // Camera: in over beat 02 t .06-.36, out over beat 04 t 0-.28 (e = 1 means inside the phone). Scrubbed moves
  // ride GLIDE over about a third of a viewport each: the reader's scroll is the speed, so the curve stays gentle.
  const T = HOW_STORY_TIMING;
  const outward = progress >= HOW_STORY_WINDOWS[3][0];
  const inE = GLIDE(seg(t2, ...T.inward));
  const outE = GLIDE(seg(t4, ...T.outward));
  const e = outward ? 1 - outE : inE;
  const zoom = inward === "zoom";
  const z = zoom ? lerp(1, L.kIn, e) : 1;
  // The clay bezel's fade rides on the camera itself (one layer, not a wrapper of its own).
  const phoneO = outward ? seg(outE, 0.3, 0.9) : 1 - seg(inE, 0.25, 0.7);
  nodes.phoneCamera = zoom ? move(L.d.x * e, L.d.y * e, z, phoneO) : move(0, 0, 1, phoneO);
  // A stage rect inside the phone, where the camera shows it now (the camera scales about the phone's centre).
  const phoneCentre = { x: L.phone.x + L.phone.w / 2, y: L.phone.y + L.phone.h / 2 };
  const throughCamera = (rect) => (zoom
    ? { x: phoneCentre.x + (rect.x - phoneCentre.x) * z + L.d.x * e, y: phoneCentre.y + (rect.y - phoneCentre.y) * z + L.d.y * e, size: rect.size * z }
    : rect);
  // Inward, in order: the dark ground and the library come up under the phone, the screen (content and background)
  // goes clear so the library shows in the screen's place, then the clay bezel fades. Outward runs the other way. Each
  // is one opacity on its own layer (the bezel's is the camera's). The ground is the largest change on the stage, so
  // it is scrubbed as calmly as the moves (T.ground inward, the whole camera move outward).
  const windowO = outward ? 1 - seg(outE, 0, 0.5) : seg(inE, 0.05, 0.3);
  nodes.mosaicWindow = zoom
    ? move((e - 1) * L.d.x, (e - 1) * L.d.y, z / L.kIn, windowO)
    : move(0, 0, 1, windowO);
  const ground = outward ? 1 - outE : GLIDE(seg(t2, ...T.ground));
  nodes.ground = fade(ground);
  // The bottom fade blends the cropped phone into the canvas; it hides while the ground is up.
  nodes.bottomFade = fade(1 - ground);
  const screenO = outward ? seg(outE, 0.3, 0.75) : 1 - seg(inE, 0.1, 0.35);
  nodes.screenFade = fade(screenO);

  // Screens inside the phone are switched by state and play in time (how-story.css): beat 02 opens with the push from
  // 사진 불러오기 to the loading screen (data-scene); in beat 05 the picker tab goes (data-picker 0) and the map tab
  // comes (data-map past "none") on one threshold, T.mapIn, as two moments in time, never a crossfade: the map waits
  // until the picker has faded, so saving never reads as "and then the app opens the map", and the empty screen
  // between them is a pause in time that no scroll position can rest on.
  const landing = progress >= HOW_STORY_WINDOWS[4][0];
  const pickerGone = landing && t5 >= T.mapIn;

  // Counter and pin wave share one number, so they never disagree.
  const { k, processed } = loadingStep(t2);
  if (!outward && screenO > 0) {
    texts.loadingCount = fillHowStoryText(APP_SCREEN_TEXT.loading.count, { processed, total: HOW_STORY_LIBRARY_COUNT });
    texts.loadingPct = fillHowStoryText(APP_SCREEN_TEXT.loading.percent, { p: k });
    const bar = linearProgressPlacement(k, L.loadingBar, HOW_STORY_APP_PROGRESS.gapDp * L.dp);
    nodes.loadingFill = move(bar.fill, 0);
    nodes.loadingTrack = move(bar.track, 0);
  }
  const edge = waveEdge(k, L);
  const reveal = L.window.h * (1 - edge);
  nodes.pinsInner = move(0, reveal);

  // Once the wave has crossed the library, the white pins step back and the library settles a little, so the
  // four green matches and the number carry the hold. Beat 03 then dims the other photos (set aside, not
  // removed) and the pins and overlay bar go.
  const settle = EASE_OUT(seg(t2, 0.4, 0.48));
  const dim = EASE_OUT(seg(t3, 0, 0.2));
  // The library's brightness, drawn as a cover of the window's own ground colour over the photos (cover at 1 - b
  // over the sprite is the same blend as the sprite at b over the ground): a flat layer, where an opacity on the
  // sprite split its panels into several large layers.
  const brightness = lerp(lerp(1, 0.65, EASE_OUT(seg(t2, 0.44, 0.5))), 0.16, dim);
  nodes.mosaicDim = fade(1 - brightness);
  // The reveal carries the pins' opacity too (one layer fewer).
  nodes.pinsReveal = move(0, -reveal, 1, lerp(1, 0.55, settle) * (1 - dim));
  nodes.overlayBar = fade(1 - dim);
  const overlayIn = EASE_OUT(seg(t2, 0.2, 0.3));
  const overlayO = outward ? 1 - seg(outE, 0, 0.3) : overlayIn;
  nodes.overlay = outward ? move(0, 0, 1, overlayO) : move(0, 12 * (1 - overlayIn), 1, overlayIn);
  if (overlayO > 0) {
    texts.overlayValue = fillHowStoryText(HOW_STORY_TEXT.overlayValue, { n: formatHowStoryNumber(processed) });
    nodes.overlayBarFill = move(L.overlayBar * (k / 100 - 1), 0);
  }

  // The four 경주 photos. Beat 03: in the library (lifted once their pin pops) until their flight starts; then the
  // stage tile takes over at exactly the lifted rect, carrying the cell's green ring and pin (they fade out mid
  // flight), and glides (staggered) to a 2x2 cluster. Beat 04: they glide on to the picker's cards, landing where
  // the camera shows each card at that moment, and become them (the card border and preview button fade in once
  // all four have landed), so the photos found are the photos offered. Beat 05: they go with the picker.
  const pins = L.cells.filter(({ popAt }) => k > 0 && edge >= popAt).length;
  // The card chrome is a UI state: it comes on once all four have landed (a 200ms fade in how-story.css).
  const chrome = t4 >= T.chrome ? 1 : 0;
  L.cells.forEach((cell, i) => {
    const start3 = T.cluster.start + T.cluster.stagger * i;
    const started = progress >= HOW_STORY_WINDOWS[2][0] && t3 >= start3;
    const flight3 = seg(t3, start3, start3 + T.cluster.length);
    const start4 = T.cards.start + T.cards.stagger * i;
    const flight4 = seg(t4, start4, start4 + T.cards.length);
    const [from, to, u] = outward ? [L.cluster[i], throughCamera(L.cards[i]), GLIDE(flight4)] : [cell.lift, L.cluster[i], GLIDE(flight3)];
    const size = lerp(from.size, to.size, u);
    // The cell and its ring hand over to the tile in the same frame.
    nodes[`matchCell${i}`] = fade(started ? 0 : 1);
    nodes[`matchMark${i}`] = fade(started ? 0 : 1);
    // In beat 05 they go with the picker tab: a step here, a fade in time in how-story.css.
    nodes[`match${i}`] = move(lerp(from.x, to.x, u), lerp(from.y, to.y, u), size / L.nodeBase, started && !pickerGone ? 1 : 0);
    nodes[`matchRing${i}`] = fade(outward ? 0 : 1 - seg(flight3, 0.3, 0.7));
    nodes[`matchShadow${i}`] = fade(outward ? 1 - flight4 : flight3);
    nodes[`matchChrome${i}`] = fade(outward ? chrome : 0);
  });

  // Saving: copies of the three picked photos peel off their cards and lift together into a fanned stack of prints
  // over the header (the upload; the originals stay selected in the grid, card D has no copy). Beat 05 opens with
  // the stack rising off the top in time (how-story.css): the upload ends there, before the map, which never
  // receives them. The frame holds the prints in the stack.
  const picked = HOW_STORY_MATCHES.filter((match) => match.picked).length;
  const lift = GLIDE(seg(t4, ...T.lift));
  for (let i = 0; i < picked; i += 1) {
    const card = L.cards[i];
    const print = L.prints[i];
    const u = landing ? 1 : lift;
    const rect = {
      cx: lerp(card.x + card.size / 2, print.cx, u),
      cy: lerp(card.y + card.size / 2, print.cy, u),
      size: lerp(card.size, print.size, u),
      r: lerp(0, print.r, u),
    };
    const o = outward ? seg(u, 0, 0.35) : 0;
    nodes[`copy${i}`] = { ...move(rect.cx - L.nodeBase / 2, rect.cy - L.nodeBase / 2, rect.size / L.nodeBase, o), r: round(rect.r, 2) + 0 };
  }
  // The header rows under the stack (the two buttons, the note, the filter row) clear as the prints lift into their
  // place, so the hold is the stack alone between the title and the date row (round-3 V4). In beat 05 it goes with
  // the stack, in time (how-story.css).
  nodes.pickerClear = fade(outward && !landing ? lift : 0);

  // Beat 05's map, its own moment after the picker has gone: develop's 경상북도 drill map (zoomed in on the
  // mainland), 경주시 fills, then the map camera pulls back to the Korea map by interpolating the SVG viewBox (crisp
  // at every scale): district lines and labels go, province outlines come, 경주시's fill hands over to 경상북도's,
  // and the summary card crossfades to the Korea variant.
  const mapE = GLIDE(seg(t5, ...T.mapCamera));
  const view = APP_MAP_VIEW;
  const box = mapCameraBox(view.drillBox, view.koreaBox, mapE);
  const attrs = { mapSvg: { viewBox: box.map((value) => round(value, 2)).join(" ") } };
  // The district labels are HTML (SVG text would lay out again on every viewBox write), so their layer follows
  // the camera with a transform from the drill framing.
  const areaScale = view.area.width / box[2];
  nodes.mapLabels = move(
    (view.drillBox[0] - box[0]) * areaScale * L.dp,
    (view.drillBox[1] - box[1]) * areaScale * L.dp,
    view.drillBox[2] / box[2],
    1 - seg(mapE, 0, 0.5),
  );
  nodes.mapDistrictLines = fade(1 - seg(mapE, 0, 0.5));
  nodes.mapProvinces = fade(seg(mapE, 0.3, 1));
  nodes.mapFillDistrict = fade(1 - seg(mapE, 0.3, 0.8));
  nodes.mapFillProvince = fade(seg(mapE, 0.3, 0.8));
  // The two cards differ in height, so the drill card leaves a little ahead of the Korea card's arrival.
  nodes.mapCardDrill = fade(1 - seg(mapE, 0.4, 0.58));
  nodes.mapCardKorea = fade(seg(mapE, 0.48, 0.7));

  nodes.pill = fade(1);

  const taps = plan.taps?.count ?? null;
  const pickTaps = plan.beats.filter(({ id }) => id.startsWith("pick-"));
  const saveTap = plan.beats.find(({ id }) => id === "save");
  const state = {
    scene: sceneIndex + 1,
    tapField: taps === getHowStoryTapRange().max && t1 >= 0.04 ? 1 : 0,
    typed: t1 >= 0.08 ? 1 : 0,
    results: t1 >= 0.26 ? 1 : 0,
    row: t1 >= 0.42 ? 1 : 0,
    pins,
    found: t3 >= 0.14 ? 1 : 0,
    sel: pickTaps.filter((tap) => progress >= tap.p).length,
    save: progress >= saveTap.p ? 1 : 0,
    // The map tab: none until it comes (beat 05, T.mapIn), then 경상북도's drill view, 경주시 filled (T.fill), and the
    // world (T.world).
    map: !pickerGone ? "none" : t5 < T.fill ? "drill0" : t5 < T.world ? "drill1" : "world",
    // Inside the phone: from beat 02 until the camera is back out in beat 04 (T.outward[1]). Outside it the camera,
    // the screen fade and the bottom fade are at rest and the library, its ground and the overlay are at opacity 0,
    // so their layers drop (how-story.css).
    inside: progress >= HOW_STORY_WINDOWS[1][0] && (!outward || t4 < T.outward[1]) ? 1 : 0,
    // The picker tab, with its four photos: it goes at beat 05 T.mapIn, as the map tab comes (a fade in time; their
    // layers drop with it).
    picker: pickerGone ? 0 : 1,
  };

  return { p: progress, sceneIndex, t, e: round(e, 4), nodes, texts, attrs, state };
}

// Inline style for one frame node; the scroll hook writes the same strings straight to the DOM.
export function getHowStoryNodeStyle(node) {
  const style = {};
  if (node.tx !== undefined) {
    const turn = node.r ? ` rotate(${node.r}deg)` : "";
    // 2D on purpose: a 3D translate would give every moved node a layer of its own. The layers are chosen in CSS,
    // per scene (how-story.css).
    style.transform = node.s === 1 && !turn ? `translate(${node.tx}px, ${node.ty}px)` : `translate(${node.tx}px, ${node.ty}px)${turn} scale(${node.s})`;
  }
  if (node.o !== undefined) style.opacity = String(node.o);
  return style;
}
