// The #how scroll story: 장소 고르기 -> 폰이 사진첩을 훑기 -> 그곳 사진만 남기 -> 골라서 저장 -> 지도.
// Pure: no React, no analytics, no DOM. Every frame is a function of scroll progress p (0..1), so
// scrolling back up replays the same frames in reverse. All visible text lives here.
import { createProjector } from "./koreaMap.js";

export const HOW_STORY_EXPERIENCE_TYPE = "how_story";
export const HOW_STORY_SCENE_COUNT = 5;

// h2 of #how. The owner may swap in the team's Wow sentence.
export const HOW_STORY_TITLE = "사진첩이 커도, 장소만 고르면 돼요.";
// Illustrative library size, the same number as the hero; the stage carries the 예시 badge.
export const HOW_STORY_LIBRARY_COUNT = 21474;
// Only numbers the team measured on the shipping app. Each starts as null, and null means its UI is absent.
export const HOW_STORY_METRICS = Object.freeze({
  scanSeconds: null, // measured seconds for a first photo search for a place over a whole library; null = never show a time
  scanLibraryCount: null, // the library size that measurement was taken on; required when scanSeconds is set
  scanSource: null, // e.g. "갤럭시 S23 · 사진 14,213장 · 첫 검색 실측"; shown as a small footnote when scanSeconds is set
  // Measured taps on the shipping app for exactly the taps this story shows: (the search field), the place
  // result, each photo, then save. The + that opens a record is not shown, so it is not counted. Fill it
  // only when the measured build needs those taps and no others (main also needs 이 장소 사진 찾기,
  // 선택한 사진 추가, a title and a start date, so a count measured there must stay null). null = no counter.
  taps: null,
  tapsSource: null,
});
// "tap-to-check": found photos start unchecked (develop). "prechecked": they start checked (main). One-constant switch.
export const HOW_STORY_SELECTION = "tap-to-check";
export const HOW_STORY_SELECTIONS = Object.freeze(["tap-to-check", "prechecked"]);

export const HOW_STORY_CANVAS = Object.freeze({ width: 320, height: 280 });
// Phones use the 320svh track; from 561px wide (where a mouse wheel is likely) the track uses the desktop
// clamp so every hold outlasts one 120px wheel notch, even in a half-screen laptop window.
export const HOW_STORY_TRACK = Object.freeze({
  wheelTrackMinWidth: 561,
  mobileSvh: 320,
  desktopSvh: 340,
  desktopMinPx: 2300,
  desktopMaxPx: 3000,
  frameOffset: Object.freeze({ mobile: 96, tablet: 104, desktop: 112 }),
  stickyTop: Object.freeze({ mobile: 80, tablet: 88, desktop: 96 }),
  tabletMaxFrame: 860,
  desktopMaxFrame: 760,
});

const photo = (name) => `/assets/photos/${name}.webp`;

export const HOW_STORY_TEXT = Object.freeze({
  skip: "사진 찾는 과정 건너뛰기",
  progressLabels: Object.freeze(["장소 고르기", "사진첩 훑기", "그곳 사진만", "골라서 저장", "지도"]),
  hudChecked: "확인한 사진",
  hudCheckedValue: "{n} / {total}장",
  hudTimer: "{s}초",
  hudServer: "Mapmory 서버",
  hudServerValue: "받은 사진 {n}장",
  hudTaps: "탭",
  hudTapsValue: "{n}번",
  lineLabel: "사진은 저장할 때만 이 선을 넘어요",
  zone: "내 폰 안",
  place: "경상북도 경주시",
  sample: "예시",
  searchPlaceholder: "장소 검색",
  searchQuery: "경주",
  noLocation: "위치 없음",
  mapCaption: "앱에 들어 있는 경주시 경계",
  foundPill: "{total}장 중 경주시 사진 {n}장",
  // With a measured library on screen, the illustrative result drops the library size, so a measured number
  // never shares a sentence with the made-up 4.
  foundPillMeasured: "경주시 사진 {n}장",
  sheetTitle: "경주시에서 찾은 사진",
  sheetSubtitle: "촬영 위치로 찾았어요",
  sheetCount: "{n}장 선택",
  save: "저장",
  keep: "폰에 그대로",
  levels: Object.freeze([
    Object.freeze({ level: "시·군·구", name: "경주시" }),
    Object.freeze({ level: "시·도", name: "경상북도" }),
    Object.freeze({ level: "나라", name: "대한민국" }),
  ]),
  scanNote: " 실측: {source}, 사진 {count}장을 확인하는 데 {s}초 걸렸어요.",
  tapsNote: " 실측: {source}, 장소 검색부터 저장까지 탭 {n}번.",
});

// Each scene: [start, end] window of p, the local t where its still hold starts, one headline and one
// support line (one sentence each, two rendered lines at most) and a screen-reader description.
export const HOW_STORY_SCENES = Object.freeze([
  Object.freeze({
    id: "place",
    window: Object.freeze([0, 0.16]),
    holdStart: 0.56,
    headline: "장소만 골라요",
    support: "다녀온 곳 이름을 검색해서 골라요.",
    description: "예시 화면이에요. 장소 검색에 다녀온 곳 이름 경주를 입력하고, 검색 결과에서 경상북도 경주시를 골라요.",
  }),
  Object.freeze({
    id: "scan",
    window: Object.freeze([0.16, 0.38]),
    holdStart: 0.68,
    headline: "폰이 사진첩을 전부 훑어요",
    support: "사진마다 촬영 위치를 확인해요. 장면은 보지 않아요.",
    description: "고른 장소의 사진을 찾을 때, 폰이 사진마다 저장된 촬영 위치를 확인해요. 사진 속 장면은 보지 않아요. 이 예시에서는 사진첩 {total}장을 처음부터 끝까지 훑어요.{scanNote}",
  }),
  Object.freeze({
    id: "match",
    window: Object.freeze([0.38, 0.54]),
    holdStart: 0.56,
    headline: "그곳에서 찍은 사진만 남아요",
    support: "찾는 동안 사진은 서버로 가지 않아요.",
    description: "폰이 앱에 들어 있는 경주시 경계와 촬영 위치를 비교해서, {scope}경주시에서 찍은 사진 4장만 남겨요. 이 예시에서 부산과 강원에서 찍은 사진, 위치 정보가 없는 사진은 빠졌어요. 찾는 동안 사진은 서버로 가지 않아요.",
  }),
  Object.freeze({
    id: "save",
    window: Object.freeze([0.54, 0.86]),
    holdStart: 0.76,
    headline: "골라서 저장하면 고른 사진만 올라가요",
    support: "나머지 사진은 폰에 그대로 있어요.",
    descriptionByVariant: Object.freeze({
      "tap-to-check": "찾은 사진 4장 가운데 올리고 싶은 3장을 탭해서 골라요. 그 뒤 기록을 저장하면 그 3장만 Mapmory 서버로 올라가고, 고르지 않은 1장과 나머지 사진첩은 폰에 그대로 있어요.",
      prechecked: "찾은 사진 4장이 체크된 채로 모여요. 올리고 싶지 않은 1장은 탭해서 빼요. 그 뒤 기록을 저장하면 체크한 3장만 Mapmory 서버로 올라가고, 뺀 1장과 나머지 사진첩은 폰에 그대로 있어요.",
    }),
  }),
  Object.freeze({
    id: "map",
    window: Object.freeze([0.86, 1]),
    holdStart: 0.52,
    headline: "지도가 칠해져요",
    support: "경주시·경상북도·대한민국이 함께 칠해져요.",
    description: "기록 하나로 앱 지도에서 경주시(시·군·구), 경상북도(시·도), 대한민국(나라)이 함께 칠해져요.{tapsNote}",
  }),
]);

// The photo library grid scanned in scene 2: 5 columns of 56px tiles, 60px apart.
export const HOW_STORY_GRID = Object.freeze({
  columns: 5,
  rows: 18,
  tile: 56,
  pitch: 60,
  x0: 12,
  y0: 4,
  // The strip scrolls up until rows 14-17 sit centred in the canvas.
  finalOffset: -822,
  scanLineY: 236,
});

// The 9 story photos. They sit in the last screen of the library; the rest of the grid is filler (never the
// hero's files). Coordinates were checked against the app's own 경주시 boundary (47130): matches fall inside
// it, everything else outside, and every dot clears the drawn boundary line in scene 3.
export const HOW_STORY_TILES = Object.freeze([
  { src: photo("gyeongbuk-02"), kind: "match", lat: 35.8347, lng: 129.219, cell: [14, 1], slot: 0 },
  { src: photo("busan-01"), kind: "elsewhere", lat: 35.185, lng: 129.205, cell: [14, 3] },
  { src: photo("gangwon-01"), kind: "elsewhere", lat: 38.119, lng: 128.465, cell: [15, 0] },
  { src: photo("gyeongbuk-01"), kind: "match", lat: 35.79, lng: 129.332, cell: [15, 2], slot: 1 },
  { src: photo("busan-03"), kind: "elsewhere", lat: 35.1587, lng: 129.1604, cell: [15, 4] },
  { src: photo("busan-02"), kind: "no-location", lat: null, lng: null, cell: [16, 1] },
  { src: photo("gyeongbuk-04"), kind: "match", lat: 35.78, lng: 129.09, cell: [16, 3], slot: 3 },
  { src: photo("gyeongbuk-03"), kind: "match", lat: 35.93, lng: 129.23, cell: [17, 0], slot: 2 },
  { src: photo("gangwon-03"), kind: "elsewhere", lat: 38.172, lng: 128.49, cell: [17, 2] },
].map((tile) => Object.freeze(tile)));

// Filler crops [x%, y%, zoom], as in the hero, so repeats read as other shots.
const FILLER_CROPS = [[50, 50, 1.15], [28, 34, 1.5], [72, 66, 1.6], [50, 22, 1.4], [22, 78, 1.7], [80, 40, 1.55], [40, 70, 1.45]];
// Fillers only reuse photos whose story role they can share: the found photos never repeat (a pinned 경주
// filler would be a 경주 photo the find missed), and the no-location photo only repeats as 위치 없음.
const LOCATED_FILLER_SOURCES = HOW_STORY_TILES.filter(({ kind }) => kind === "elsewhere").map(({ src }) => src);
const NO_LOCATION_FILLER_SOURCE = HOW_STORY_TILES.find(({ kind }) => kind === "no-location").src;
const storyByCell = new Map(HOW_STORY_TILES.map((tile) => [tile.cell.join(":"), tile.src]));
const fillerByCell = new Map();
// How strongly each photo already sits next to a cell: 2 for a side neighbour, 1 for a corner.
const neighbourWeights = (row, column) => {
  const weights = new Map();
  for (let dr = -1; dr <= 1; dr += 1) {
    for (let dc = -1; dc <= 1; dc += 1) {
      const key = `${row + dr}:${column + dc}`;
      const src = storyByCell.get(key) ?? fillerByCell.get(key);
      if ((dr || dc) && src) weights.set(src, (weights.get(src) ?? 0) + (dr && dc ? 1 : 2));
    }
  }
  return weights;
};
export const HOW_STORY_FILLERS = Object.freeze(Array.from({ length: HOW_STORY_GRID.rows * HOW_STORY_GRID.columns }, (_, index) => {
  const row = Math.floor(index / HOW_STORY_GRID.columns);
  const column = index % HOW_STORY_GRID.columns;
  if (storyByCell.has(`${row}:${column}`)) return null;
  const hasLocation = index % 8 !== 5;
  let src = NO_LOCATION_FILLER_SOURCE;
  if (hasLocation) {
    // Rotate through the located photos and take the first one not already next to this cell (or, when all
    // are, the one that touches it least).
    const weights = neighbourWeights(row, column);
    const start = (column + row * 2) % LOCATED_FILLER_SOURCES.length;
    const order = LOCATED_FILLER_SOURCES.map((_, step) => LOCATED_FILLER_SOURCES[(start + step) % LOCATED_FILLER_SOURCES.length]);
    src = order.reduce((best, candidate) => ((weights.get(candidate) ?? 0) < (weights.get(best) ?? 0) ? candidate : best));
  }
  fillerByCell.set(`${row}:${column}`, src);
  return Object.freeze({
    src,
    crop: FILLER_CROPS[(column * 2 + row) % FILLER_CROPS.length],
    hasLocation,
    cell: Object.freeze([row, column]),
  });
}).filter(Boolean));

// Scene 3 map: 경주시 and its neighbours, fitted to the canvas.
export const HOW_STORY_MAP_VIEW = Object.freeze({ minLng: 128.34, maxLng: 129.96, minLat: 35.05, maxLat: 36.2 });
const projectStoryMap = createProjector(HOW_STORY_MAP_VIEW, HOW_STORY_CANVAS);
export function projectToStoryMap(lng, lat) {
  return projectStoryMap(lng, lat);
}

// Logical sizes of the absolutely placed stage nodes; the stage renders them at these sizes.
export const HOW_STORY_NODE_SIZE = Object.freeze({
  tile: 56,
  dot: 10,
  slot: 64,
  tapRing: 40,
  searchCard: Object.freeze([288, 116]),
  sheet: Object.freeze([304, 196]),
  pill: Object.freeze([260, 32]),
  keepTag: Object.freeze([104, 24]),
  korea: Object.freeze([150, 190]),
  chip: Object.freeze([120, 44]),
  mapCaption: Object.freeze([200, 16]),
});

const SEARCH_CARD_CENTER = [160, 82];
const SEARCH_PILL_CENTER = [160, 56];
const RESULT_ROW_CENTER = [160, 106];
const SHEET_CENTER = [160, 140];
const SAVE_CENTER = [276, 70];
const SLOT_Y = 152;
const SLOT_XS = [52, 124, 196, 268];
const KEEP_TAG_CENTER = [196, 208];
const PILL_CENTER = [160, 196];
const MAP_CAPTION_CENTER = [178, 150];
const KOREA_CENTER = [91, 140];
const CHIP_XS = 250;
const CHIP_YS = [86, 140, 194];
export const HOW_STORY_DEFAULT_STRIP_TARGET = Object.freeze({ x: 260, y: -92 });

// Taps the choreography shows. Core taps always appear; optional ones are added in order only when a
// measured tap count asks for them, so the HUD counter can end exactly on HOW_STORY_METRICS.taps.
// Only taps both app versions share are drawn: the scan starts once the place is picked (develop starts it
// on the pick; main adds an 이 장소 사진 찾기 tap the story leaves out), and 저장 stands for saving the record.
const TAP_BEATS = Object.freeze({
  search: { scene: 0, t: 0.06, target: SEARCH_PILL_CENTER, optional: true },
  place: { scene: 0, t: 0.36, target: RESULT_ROW_CENTER },
  save: { scene: 3, t: 0.44, target: SAVE_CENTER },
});
const OPTIONAL_TAP_ORDER = ["search"];
const SELECTION_TAPS = Object.freeze({
  "tap-to-check": Object.freeze([{ slot: 0, t: 0.28 }, { slot: 1, t: 0.33 }, { slot: 3, t: 0.38 }]),
  prechecked: Object.freeze([{ slot: 2, t: 0.34 }]),
});
const PRECHECK_POP = [0.26, 0.3];

export const clampUnit = (value) => Math.min(1, Math.max(0, value));
export const seg = (t, a, b) => clampUnit((t - a) / (b - a));
export const lerp = (a, b, u) => a + (b - a) * u;
export const easeOutCubic = (u) => 1 - (1 - u) ** 3;
export const easeInOutCubic = (u) => (u < 0.5 ? 4 * u ** 3 : 1 - (-2 * u + 2) ** 3 / 2);
const bump = (t, a, b) => Math.sin(Math.PI * seg(t, a, b));

const numberFormat = new Intl.NumberFormat("ko-KR");
export function formatHowStoryNumber(value) {
  return numberFormat.format(value);
}
export function fillHowStoryText(template, values = {}) {
  return template.replace(/\{(\w+)\}/g, (match, key) => (key in values ? String(values[key]) : match));
}

// Ported from the removed hero relay (heroMemoryRelay.js): progress across the sticky travel.
export function getHowStoryProgress({ trackTop, trackHeight, frameHeight, stickyTop }) {
  const travel = Math.max(1, trackHeight - frameHeight);
  return clampUnit((stickyTop - trackTop) / travel);
}

export function getHowStoryScene(p) {
  const progress = clampUnit(p);
  const index = HOW_STORY_SCENES.findIndex(({ window: [, end] }, i) => progress < end || i === HOW_STORY_SCENE_COUNT - 1);
  const [start, end] = HOW_STORY_SCENES[index].window;
  return { index, t: seg(progress, start, end) };
}

export function isHowStoryHold(p) {
  const { index, t } = getHowStoryScene(p);
  return t >= HOW_STORY_SCENES[index].holdStart;
}

// The still for scene i: the end of its hold (the very end for the last scene).
export function stillProgress(index) {
  return index === HOW_STORY_SCENE_COUNT - 1 ? 1 : HOW_STORY_SCENES[index].window[1] - 0.001;
}

const sceneP = (scene, t) => {
  const [start, end] = HOW_STORY_SCENES[scene].window;
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

function coreTapIds(selection) {
  return ["place", ...SELECTION_TAPS[selection].map((_, index) => `select-${index}`), "save"];
}

export function getHowStoryTapRange(selection = HOW_STORY_SELECTION) {
  const core = coreTapIds(selection).length;
  return { min: core, max: core + OPTIONAL_TAP_ORDER.length };
}

// Every on-screen tap, in order, with its absolute p and logical target.
export function getHowStoryTapPlan(selection = HOW_STORY_SELECTION, tapCount = null) {
  const { min } = getHowStoryTapRange(selection);
  const extra = tapCount === null ? 0 : tapCount - min;
  const optional = new Set(OPTIONAL_TAP_ORDER.slice(0, Math.max(0, extra)));
  const beats = [];
  for (const [id, beat] of Object.entries(TAP_BEATS)) {
    if (beat.optional && !optional.has(id)) continue;
    beats.push({ id, scene: beat.scene, t: beat.t, target: beat.target });
  }
  SELECTION_TAPS[selection].forEach(({ slot, t }, index) => {
    beats.push({ id: `select-${index}`, scene: 3, t, target: [SLOT_XS[slot], SLOT_Y], slot });
  });
  return beats
    .map((beat) => ({ ...beat, p: sceneP(beat.scene, beat.t) }))
    .sort((a, b) => a.p - b.p);
}

const isFilled = (value) => value !== null && value !== undefined;
const nonEmptyString = (value) => typeof value === "string" && value.trim().length > 0;

// Turns the raw constants into what the stage may show. An incomplete or impossible metric is dropped
// (its UI stays absent) and reported in errors, which the tests require to be empty.
export function resolveHowStoryMetrics(metrics = HOW_STORY_METRICS, selection = HOW_STORY_SELECTION) {
  const errors = [];
  let scan = null;
  if (isFilled(metrics.scanSeconds)) {
    if (!(Number.isFinite(metrics.scanSeconds) && metrics.scanSeconds > 0)) errors.push("scanSeconds must be a positive number");
    else if (!(Number.isInteger(metrics.scanLibraryCount) && metrics.scanLibraryCount > 0)) errors.push("scanLibraryCount is required with scanSeconds");
    else if (!nonEmptyString(metrics.scanSource)) errors.push("scanSource is required with scanSeconds");
    else scan = { seconds: metrics.scanSeconds, libraryCount: metrics.scanLibraryCount, source: metrics.scanSource.trim() };
  }
  let taps = null;
  if (isFilled(metrics.taps)) {
    const { min, max } = getHowStoryTapRange(selection);
    if (!Number.isInteger(metrics.taps)) errors.push("taps must be an integer");
    else if (metrics.taps < min || metrics.taps > max) errors.push(`taps ${metrics.taps} cannot be shown: the ${selection} choreography shows ${min}-${max} taps`);
    else if (!nonEmptyString(metrics.tapsSource)) errors.push("tapsSource is required with taps");
    else taps = { count: metrics.taps, source: metrics.tapsSource.trim() };
  }
  // A measured time only makes sense next to the library it was measured on.
  const libraryCount = scan ? scan.libraryCount : HOW_STORY_LIBRARY_COUNT;
  return { scan, taps, libraryCount, errors };
}

const planCache = new Map();
export function getHowStoryPlan({ selection = HOW_STORY_SELECTION, metrics = HOW_STORY_METRICS } = {}) {
  if (!HOW_STORY_SELECTIONS.includes(selection)) throw new Error(`Unknown selection ${selection}`);
  const key = `${selection}|${JSON.stringify(metrics)}`;
  if (!planCache.has(key)) {
    const resolved = resolveHowStoryMetrics(metrics, selection);
    planCache.set(key, Object.freeze({
      selection,
      ...resolved,
      beats: getHowStoryTapPlan(selection, resolved.taps?.count ?? null),
    }));
  }
  return planCache.get(key);
}

export function getHowStorySceneDescription(index, options = {}) {
  const plan = getHowStoryPlan(options);
  const scene = HOW_STORY_SCENES[index];
  const template = scene.descriptionByVariant ? scene.descriptionByVariant[plan.selection] : scene.description;
  return fillHowStoryText(template, {
    total: formatHowStoryNumber(plan.libraryCount),
    // The 4 found photos are made up; next to a measured library they lose the "N장 중" part.
    scope: plan.scan ? "" : `${formatHowStoryNumber(plan.libraryCount)}장 중 `,
    scanNote: plan.scan ? fillHowStoryText(HOW_STORY_TEXT.scanNote, {
      source: plan.scan.source,
      count: formatHowStoryNumber(plan.scan.libraryCount),
      s: plan.scan.seconds,
    }) : "",
    tapsNote: plan.taps ? fillHowStoryText(HOW_STORY_TEXT.tapsNote, { source: plan.taps.source, n: plan.taps.count }) : "",
  });
}

// Footnote lines under the stage: the source of every measured number on screen.
export function getHowStoryNotes(options = {}) {
  const plan = getHowStoryPlan(options);
  return [plan.scan?.source, plan.taps?.source].filter(Boolean);
}

const round = (value, digits = 2) => Number(value.toFixed(digits));

// An absolutely placed node centred on (x, y): the translate is its top-left corner.
function placed(size, x, y, s = 1, o = 1) {
  const [w, h] = Array.isArray(size) ? size : [size, size];
  return { tx: round(x - w / 2), ty: round(y - h / 2), s: round(s, 4), o: round(clampUnit(o), 3) };
}
const offset = (dx, dy, s = 1, o = 1) => ({ tx: round(dx), ty: round(dy), s: round(s, 4), o: round(clampUnit(o), 3) });
const fade = (o) => ({ o: round(clampUnit(o), 3) });

function tileCenter([row, column], stripY) {
  const { x0, y0, pitch, tile } = HOW_STORY_GRID;
  return [x0 + column * pitch + tile / 2, y0 + row * pitch + tile / 2 + stripY];
}

// Filler flags sit in one layer that moves with the strip and is clipped at the scan line, so a flag shows
// as it passes the line without any per-flag write. The 9 story tiles move on their own, so their flags get
// the same reveal as an opacity: 0 while the flag is below the line, 1 once all of it is above.
export const HOW_STORY_FLAG_BOX = Object.freeze({ pin: Object.freeze([4, 22]), none: Object.freeze([0, 56]) });
function flagReveal([row], kind, scanned) {
  const [top, bottom] = HOW_STORY_FLAG_BOX[kind === "no-location" ? "none" : "pin"];
  const tileTop = HOW_STORY_GRID.y0 + row * HOW_STORY_GRID.pitch;
  const travel = -HOW_STORY_GRID.finalOffset;
  return seg(scanned, (tileTop + top - HOW_STORY_GRID.scanLineY) / travel, (tileTop + bottom - HOW_STORY_GRID.scanLineY) / travel);
}

const DOT_SCALE = HOW_STORY_NODE_SIZE.dot / HOW_STORY_NODE_SIZE.tile;
const SLOT_SCALE = HOW_STORY_NODE_SIZE.slot / HOW_STORY_NODE_SIZE.tile;
const matchesBySlot = HOW_STORY_TILES.map((tile, index) => ({ ...tile, index }))
  .filter(({ kind }) => kind === "match")
  .sort((a, b) => a.slot - b.slot);
// The sheet slots that end up selected in both variants; their copies rise to the server counter.
export const HOW_STORY_COPY_SLOTS = Object.freeze([0, 1, 3]);
const COPY_SLOTS = HOW_STORY_COPY_SLOTS;
const tileDot = (tile) => {
  if (tile.kind === "no-location") return null;
  const [x, y] = projectToStoryMap(tile.lng, tile.lat);
  return [Math.min(Math.max(x, 6), HOW_STORY_CANVAS.width - 6), Math.max(y, -6)];
};

export function getHowStoryFrame(p, { selection = HOW_STORY_SELECTION, stripTarget = HOW_STORY_DEFAULT_STRIP_TARGET, metrics = HOW_STORY_METRICS } = {}) {
  const plan = getHowStoryPlan({ selection, metrics });
  const progress = clampUnit(p);
  const { index: sceneIndex, t } = getHowStoryScene(progress);
  const [t1, t2, t3, t4, t5] = HOW_STORY_SCENES.map(({ window: [start, end] }) => seg(progress, start, end));
  const nodes = {};

  // Scene 1: search card over the dimmed library. The 내 폰 안 frame only appears once the photo scan starts,
  // so the stage never says where the place search runs (develop sends the typed query to a place search).
  const cardOut = seg(t2, 0.02, 0.1);
  const zoneIn = seg(t2, 0.08, 0.16);
  nodes.zoneFrame = fade(zoneIn);
  nodes.zoneChip = fade(zoneIn);
  nodes.veil = fade(0.55 * (1 - cardOut));
  nodes.searchCard = placed(HOW_STORY_NODE_SIZE.searchCard, SEARCH_CARD_CENTER[0], SEARCH_CARD_CENTER[1] - 12 * cardOut, 1, 1 - cardOut);
  const typedLength = t1 < 0.1 ? 0 : t1 < 0.18 ? 1 : 2;
  nodes.caret = fade(seg(t1, 0.03, 0.05) * (1 - seg(t1, 0.36, 0.38)));
  nodes.placeholder = fade(typedLength === 0 ? 1 : 0);
  const resultIn = seg(t1, 0.2, 0.3);
  nodes.resultRow = offset(0, 6 * (1 - easeOutCubic(resultIn)), 1, resultIn);
  nodes.resultMark = fade(seg(t1, 0.37, 0.41));
  const chipIn = easeOutCubic(seg(t1, 0.4, 0.5));
  nodes.placeChip = offset(0, 0, 0.9 + 0.1 * chipIn, chipIn);

  // Scene 2: the library scrolls past the scan line while the counter runs.
  const scanned = easeInOutCubic(seg(t2, 0.12, 0.62));
  const stripY = HOW_STORY_GRID.finalOffset * scanned;
  const scanActive = seg(t2, 0.1, 0.14);
  const flagsOut = 1 - seg(t3, 0.02, 0.12);
  nodes.strip = offset(0, stripY, 1, 1 - seg(t3, 0.04, 0.2));
  nodes.pins = fade(scanActive * flagsOut);
  nodes.pinsStrip = offset(0, stripY);
  nodes.scanLine = fade(scanActive * (1 - seg(t2, 0.62, 0.66)));

  // Scenes 2-5 for the 9 story photos: grid cell -> dot on the map -> sheet slot.
  const matchFlash = seg(t3, 0.4, 0.46);
  const elsewhereDim = 1 - 0.7 * seg(t3, 0.42, 0.5);
  const tiles = HOW_STORY_TILES.map((tile, index) => {
    const [cellX, cellY] = tileCenter(tile.cell, stripY);
    const flag = scanActive * flagReveal(tile.cell, tile.kind, scanned) * flagsOut;
    const dot = tileDot(tile);
    if (!dot) {
      // No stored location: skipped by the find in this example, so it never becomes a dot.
      return { node: placed(HOW_STORY_NODE_SIZE.tile, cellX, cellY, 1, 1 - seg(t3, 0.06, 0.16)), photo: 1, flag, grey: 0, mint: 0, badge: offset(0, 0, 0.6, 0) };
    }
    const toDot = easeInOutCubic(seg(t3, 0.06 + 0.015 * index, 0.26 + 0.015 * index));
    let x = lerp(cellX, dot[0], toDot);
    let y = lerp(cellY, dot[1], toDot);
    let scale = lerp(1, DOT_SCALE, toDot);
    let photoOpacity = 1 - seg(toDot, 0, 0.5);
    let grey = seg(toDot, 0.3, 0.7);
    let mint = 0;
    let opacity = 1;
    let badge = offset(0, 0, 0.6, 0);
    if (tile.kind === "elsewhere") {
      if (dot[1] < 0) opacity = 1 - seg(toDot, 0.6, 1);
      else opacity = elsewhereDim * (1 - seg(t4, 0.02, 0.1));
    } else {
      mint = matchFlash;
      grey *= 1 - matchFlash;
      scale *= 1 + 0.4 * bump(t3, 0.4, 0.5);
      // Scene 4: dot -> sheet slot, photo back in.
      const slotIndex = tile.slot;
      const toSlot = easeInOutCubic(seg(t4, 0.04 + 0.025 * slotIndex, 0.18 + 0.025 * slotIndex));
      if (toSlot > 0) {
        x = lerp(dot[0], SLOT_XS[slotIndex], toSlot);
        y = lerp(dot[1], SLOT_Y, toSlot);
        scale = lerp(DOT_SCALE, SLOT_SCALE, toSlot);
        photoOpacity = seg(toSlot, 0.3, 0.8);
        mint = 1 - seg(toSlot, 0, 0.5);
        grey = 0;
      }
      badge = selectionBadge(slotIndex, t4, plan.selection);
      opacity = 1 - seg(t5, 0, 0.15);
    }
    return { node: placed(HOW_STORY_NODE_SIZE.tile, x, y, scale, opacity), photo: photoOpacity, flag, grey, mint, badge };
  });
  tiles.forEach((tile, index) => {
    nodes[`tile${index}`] = tile.node;
    nodes[`tilePhoto${index}`] = fade(tile.photo);
    nodes[`tileFlag${index}`] = fade(tile.flag);
    nodes[`tileGrey${index}`] = fade(tile.grey);
    nodes[`tileMint${index}`] = fade(tile.mint);
    nodes[`tileBadge${index}`] = tile.badge;
  });

  // Scene 3: the bundled boundary, the match and the result pill.
  const mapOut = seg(t4, 0.02, 0.14);
  nodes.map = fade(seg(t3, 0.06, 0.24) * (1 - mapOut));
  nodes.mapFill = fade(seg(t3, 0.4, 0.5));
  nodes.mapCaption = placed(HOW_STORY_NODE_SIZE.mapCaption, MAP_CAPTION_CENTER[0], MAP_CAPTION_CENTER[1], 1, seg(t3, 0.14, 0.26) * (1 - mapOut));
  const pillIn = seg(t3, 0.46, 0.54);
  nodes.pill = placed(HOW_STORY_NODE_SIZE.pill, PILL_CENTER[0], PILL_CENTER[1] + 6 * (1 - easeOutCubic(pillIn)), 1, pillIn * (1 - seg(t4, 0.02, 0.1)));

  // Scene 4: the sheet, the selection, the save tap and the copies rising across the line.
  const sheetIn = seg(t4, 0.02, 0.16);
  const sheetOut = seg(t5, 0, 0.15);
  nodes.sheet = placed(HOW_STORY_NODE_SIZE.sheet, SHEET_CENTER[0], SHEET_CENTER[1] + 280 * (1 - easeOutCubic(sheetIn)), 1, seg(t4, 0.02, 0.1) * (1 - sheetOut));
  // The sheet's labels wait until the found photos have landed, so the photos never cross its text.
  nodes.sheetHead = fade(seg(t4, 0.2, 0.27));
  nodes.saveText = offset(0, 0, 1 - 0.06 * bump(t4, 0.43, 0.47));
  const copyTravel = COPY_SLOTS.map((_, k) => easeInOutCubic(seg(t4, 0.47 + 0.04 * k, 0.63 + 0.04 * k)));
  COPY_SLOTS.forEach((slotIndex, k) => {
    const u = copyTravel[k];
    const x = lerp(SLOT_XS[slotIndex], stripTarget.x, u);
    const y = lerp(SLOT_Y, stripTarget.y, u);
    nodes[`copy${k}`] = placed(HOW_STORY_NODE_SIZE.tile, x, y, lerp(SLOT_SCALE, 0.3, u), u > 0 ? 1 - seg(u, 0.85, 1) : 0);
  });
  const keepIn = seg(t4, 0.5, 0.58);
  nodes.keepTag = placed(HOW_STORY_NODE_SIZE.keepTag, KEEP_TAG_CENTER[0], KEEP_TAG_CENTER[1] + 4 * (1 - keepIn), 1, keepIn * (1 - sheetOut));

  // Scene 5: the Korea map fills 경상북도 and three levels light up.
  const koreaIn = seg(t5, 0.05, 0.2);
  nodes.korea = placed(HOW_STORY_NODE_SIZE.korea, KOREA_CENTER[0], KOREA_CENTER[1], 0.96 + 0.04 * easeOutCubic(koreaIn), koreaIn);
  nodes.koreaFill = fade(seg(t5, 0.16, 0.3));
  HOW_STORY_TEXT.levels.forEach((_, k) => {
    const chipIn = seg(t5, 0.26 + 0.08 * k, 0.36 + 0.08 * k);
    nodes[`level${k}`] = placed(HOW_STORY_NODE_SIZE.chip, CHIP_XS, CHIP_YS[k] + 6 * (1 - easeOutCubic(chipIn)), 1, chipIn);
  });

  // One tap ring serves every tap; the counter counts the taps already made.
  let ring = { o: 0, x: SEARCH_PILL_CENTER[0], y: SEARCH_PILL_CENTER[1], s: 0.8 };
  for (const beat of plan.beats) {
    const local = HOW_STORY_SCENES[beat.scene].window;
    const tb = seg(progress, local[0], local[1]);
    const o = 0.6 * Math.min(seg(tb, beat.t - 0.02, beat.t), 1 - seg(tb, beat.t, beat.t + 0.04));
    if (o > ring.o) ring = { o, x: beat.target[0], y: beat.target[1], s: 0.8 + 0.45 * seg(tb, beat.t - 0.02, beat.t + 0.04) };
  }
  nodes.tapRing = placed(HOW_STORY_NODE_SIZE.tapRing, ring.x, ring.y, ring.s, ring.o);
  const tapsMade = plan.beats.filter((beat) => progress >= beat.p).length;

  const received = copyTravel.filter((u) => u >= 1).length;
  const checked = Math.round(plan.libraryCount * scanned);
  const texts = {
    typed: HOW_STORY_TEXT.searchQuery.slice(0, typedLength),
    checked: fillHowStoryText(HOW_STORY_TEXT.hudCheckedValue, { n: formatHowStoryNumber(checked), total: formatHowStoryNumber(plan.libraryCount) }),
    server: fillHowStoryText(HOW_STORY_TEXT.hudServerValue, { n: received }),
    sheetCount: fillHowStoryText(HOW_STORY_TEXT.sheetCount, { n: selectedCount(t4, plan.selection) }),
    pill: plan.scan
      ? fillHowStoryText(HOW_STORY_TEXT.foundPillMeasured, { n: matchesBySlot.length })
      : fillHowStoryText(HOW_STORY_TEXT.foundPill, { total: formatHowStoryNumber(plan.libraryCount), n: matchesBySlot.length }),
    timer: plan.scan ? fillHowStoryText(HOW_STORY_TEXT.hudTimer, { s: (plan.scan.seconds * seg(t2, 0.12, 0.62)).toFixed(1) }) : null,
    taps: plan.taps ? fillHowStoryText(HOW_STORY_TEXT.hudTapsValue, { n: tapsMade }) : null,
  };

  return {
    p: progress,
    sceneIndex,
    t,
    segments: [t1, t2, t3, t4, t5].map((value) => round(value, 4)),
    nodes,
    texts,
    counts: { checked, received, taps: plan.taps ? tapsMade : null },
  };
}

// Inline style for one frame node; the scroll hook writes the same strings straight to the DOM.
export function getHowStoryNodeStyle(node) {
  const style = {};
  if (node.tx !== undefined) style.transform = `translate3d(${node.tx}px, ${node.ty}px, 0) scale(${node.s})`;
  if (node.o !== undefined) style.opacity = String(node.o);
  return style;
}

export function getHowStorySegmentTransform(value) {
  return `scaleX(${value})`;
}

function selectionBadge(slot, t4, selection) {
  if (selection === "prechecked") {
    const pop = seg(t4, PRECHECK_POP[0] + 0.01 * slot, PRECHECK_POP[0] + 0.01 * slot + 0.02);
    const tap = SELECTION_TAPS.prechecked.find((beat) => beat.slot === slot);
    const out = tap ? seg(t4, tap.t + 0.01, tap.t + 0.03) : 0;
    return offset(0, 0, lerp(0.6, 1, easeOutCubic(pop)) * (1 - 0.4 * out), pop * (1 - out));
  }
  const tap = SELECTION_TAPS["tap-to-check"].find((beat) => beat.slot === slot);
  const pop = tap ? seg(t4, tap.t + 0.005, tap.t + 0.025) : 0;
  return offset(0, 0, lerp(0.6, 1, easeOutCubic(pop)), pop);
}

function selectedCount(t4, selection) {
  if (selection === "prechecked") {
    if (t4 < PRECHECK_POP[0]) return 0;
    return t4 >= SELECTION_TAPS.prechecked[0].t + 0.01 ? 3 : 4;
  }
  return SELECTION_TAPS["tap-to-check"].filter((beat) => t4 >= beat.t + 0.005).length;
}
