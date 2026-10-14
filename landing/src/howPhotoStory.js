// The #how section (사용 방법): sketch A ("밝은 오브젝트"), approved on 2026-10-13.
// Pure, DOM-free model: the copy, the film's scroll thresholds and chain timings, and the small decisions the engine
// (howPhotoStoryEngine.js) makes on every frame. Every number here was tuned and verified on the sketch; keep them.

// The film's first screen is the section title (the real h2#how-title stays in the DOM for the section's label).
export const TITLE = Object.freeze(["사용 방법", "사진이 수천 장이어도", "장소만 고르면 돼요"]);
// [eyebrow, headline line 1, headline line 2, sub]. True on both main and develop of the app.
export const COPY = Object.freeze([
  Object.freeze(["01", "사진마다 찍은 곳이", "담겨 있어요", "카메라 위치 저장이 켜져 있으면요"]),
  Object.freeze(["02", "수천 장 속에서", "그곳 사진만 모여요", "사진에 저장된 위치로 찾아요"]),
  Object.freeze(["03", "찾는 건", "내 폰 안에서 해요", "찾는 동안 사진을 서버로 보내지 않아요"]),
  Object.freeze(["04", "저장할 때", "고른 사진만 올라가요", "나머지 사진은 그대로 폰에 있어요"]),
  Object.freeze(["05", "다녀온 곳이", "지도에 칠해져요", "경주시, 경상북도, 대한민국까지요"]),
]);
// The one place label on the stage (the 01 photo's tag, the 02 call, the 05 map tag).
export const TAG = "경주";

// Scroll thresholds, in viewport heights from the moment the frame pins (s = 0). The section title is the film's
// first screen (pinned, so it never slides under the header); the 01 photo rides in under it as the frame arrives,
// then 01's copy and the 경주 tag take over. Every other beat is one threshold that plays a timed chain (reversed
// on the way back). Spans: title .20 · 01 .76 · 02 .94 · 03 .74 · 04 .92 · 05 .90 (the payoff beats get the slack).
export const STEPS = Object.freeze([
  Object.freeze([-0.2, 0.2]), // 01 photo (under the title) · copy + the tag comes out of it
  Object.freeze([0.96]), //      02 the heap · (+sub) only the 경주 photos jump out, the heap leaves
  Object.freeze([1.9]), //       03 the photos settle into the phone · the lock closes inside the glass
  Object.freeze([2.64]), //      04 three are checked · copies of those three go up into the cloud
  Object.freeze([3.56]), //      05 map · tag · 경주시 · 경상북도 · 대한민국
]);
export const PIN_VH = 4.46;
// A beat the thumb is about to fly past plays its short chain; the beat where the scroll will come to rest plays the
// full one. Where it will rest: the remaining travel of a decaying fling is about velocity / 0.0021 per ms (iOS).
export const COAST_MS = 480;
// A scroll faster than this (viewport heights per second) is a fling.
export const FLING_VH_PER_S = 0.3;
// chain timings (ms): slow = a reader at rest; fast = a beat crossed mid-fling (its point lands within ~350 ms)
export const TM = Object.freeze({
  slow: Object.freeze({ sub: 300, jump: [100, 0, 40, 80], jumpDur: 560, rimAfter: 360, heapIn: 380, heapD: 180, heapDur: 600, heapO: 360, drop: 520, slotD: 160,
    objO: 380, lockIn: 260, lkd: 520, gld: 620, settleD: 40, settleStep: 30, settleDur: 620, chk: [100, 170, 240], copy: 260, copyStep: 70, copyDur: 640, p01: 910 }),
  fast: Object.freeze({ sub: 140, jump: [0, 0, 30, 60], jumpDur: 480, rimAfter: 280, heapIn: 160, heapD: 120, heapDur: 500, heapO: 300, drop: 400, slotD: 0,
    objO: 220, lockIn: 100, lkd: 280, gld: 340, settleD: 0, settleStep: 20, settleDur: 520, chk: [40, 80, 120], copy: 60, copyStep: 50, copyDur: 520, p01: 760 }),
});
// 02 and 04 have an inner step on a timer (the 경주 photos jump out of the heap; the copies go up).
export const INNER = Object.freeze({ 2: "sub", 4: "copy" });

// Modes: a viewport under 500px tall (a landscape phone) gets the static version; every portrait phone gets the film.
export const STATIC_MAX_VH = 500;
// Short phones (latched viewport under 700px) get the tighter copy spacing.
export const COMPACT_VH = 700;
// The static version's figures: the virtual world (600 x 700) fills 94% of its box.
export const STATIC_FIT = 0.94;

// The 경주 photos (the 01 photo first), and the image groups in the order the film needs them: requested only when the
// section is within two viewports, one group after another, so a slow network gets the first one first.
export const PHOTOS = Object.freeze(["gyeongbuk-02", "gyeongbuk-01", "gyeongbuk-03", "gyeongbuk-04"]);
export const IMAGE_GROUPS = Object.freeze([
  Object.freeze(["photos/gyeongbuk-02"]),
  Object.freeze(["photos/gyeongbuk-01", "photos/gyeongbuk-03", "photos/gyeongbuk-04"]),
  Object.freeze(["heap"]),
  Object.freeze(["gallery"]),
]);
// The section's own textures live in /assets/how/; the 경주 photos are the credited library files.
export const imageUrl = (name) => (name.startsWith("photos/") ? `/assets/${name}.webp` : `/assets/how/${name}.webp`);
export const imageGroupOf = (src) => IMAGE_GROUPS.findIndex((group) => group.some((name) => src.includes(`${name}.webp`)));

// The beat (0 = the title, 1-5) and its step for a scroll position s (viewport heights since the pin).
export function stateAt(s) {
  let b = 0;
  let k = 0;
  STEPS.forEach((steps, i) => {
    if (s >= steps[0]) {
      b = i + 1;
      k = 0;
      steps.forEach((t, j) => { if (s >= t) k = j; });
    }
  });
  return { b, k };
}

// Where a scroll going on now (v in px per ms) will come to rest, in viewport heights.
export const coastStop = (s, v, vh) => s + v * COAST_MS / vh;
export const isFling = (v, vh) => Math.abs(v) * 1000 / vh > FLING_VH_PER_S;
// The span of a beat's state: from where it starts to where the next beat takes over.
export const beatStart = (b, k) => (b === 0 ? -9 : b === 1 && k === 1 ? STEPS[0][1] : STEPS[b - 1][0]);
export const beatEnd = (b) => (b < 5 ? STEPS[b][0] : PIN_VH + 0.3);

// A beat just entered plays its short chain when the scroll that brought it is a fling that will carry on past it
// (or back out of it). The first paint is never fast.
export function chainIsFast({ s, v, vh, b, k, first }) {
  if (first) return false;
  const stop = coastStop(s, v, vh);
  return isFling(v, vh) && (stop >= beatEnd(b) || stop < beatStart(b, k));
}

// When an inner step's early timer fires: will the scroll going on now carry the reader past beat b?
export function willPass({ s, v, vh, b }) {
  const stop = coastStop(s, v, vh);
  return isFling(v, vh) && (stop >= beatEnd(b) || stop < STEPS[b - 1][0]);
}

// The inner step of 02/04 when the film arrives on that beat: the rest state coming back (or on the first paint),
// the start state going forward (the timer then plays it).
export function innerStepOnEnter({ b, prevB }) {
  return prevB > b || prevB === -1 ? 1 : 0;
}

// 100svh in px, latched per width on touch devices: an address bar that hides or a webview that resizes itself by a
// few percent changes nothing; a rotation or a big change re-measures. Without touch it always follows the window.
export function latchViewportHeight({ touch, width, lastWidth, vh, measured }) {
  if (!touch || width !== lastWidth || !vh || Math.abs(measured - vh) > 0.25 * vh) return measured;
  return vh;
}

// Reduced motion, a viewport too short for the film (a landscape phone), or a stage too small for its picture
// (very large text, latched for this width) get the static version.
export function isStaticMode({ reducedMotion, vh, smallWidthLatched }) {
  return Boolean(reducedMotion) || vh < STATIC_MAX_VH || Boolean(smallWidthLatched);
}

// Text so large that the picture would shrink to a speck (portrait phone layout only).
export function stageTooSmall({ portraitPhone, stageHeight, vh }) {
  return Boolean(portraitPhone) && stageHeight < Math.max(240, 0.36 * vh);
}

// The stage's virtual world: 600 wide, 700 tall (760 on tall phone stages), scaled to fit.
export function fitWorld(width, height) {
  const tall = height / width > 1.3;
  const worldHeight = tall ? 760 : 700;
  return { tall, worldHeight, scale: Math.min(width / 600, height / worldHeight) };
}

// The copy for a state: the title (0) until 01's copy takes over; -1 = nothing yet (the title is still below the fold).
export function copyFor({ b, k, titleOnScreen }) {
  if (b === 0 || (b === 1 && k === 0)) return titleOnScreen ? 0 : -1;
  return b;
}

// As the frame unpins after 05, the copy fades before it can slide under the header (the map leaves alone).
export function copyFade(s) {
  return s > PIN_VH - 0.04 ? Math.max(0, 1 - (s - (PIN_VH - 0.04)) / 0.09) : 1;
}

// Gentle float with scroll inside the active beat (pure function of s), in px; null on the title.
export function driftAt(s, b) {
  if (b <= 0) return null;
  const start = STEPS[b - 1][0];
  const end = b < 5 ? STEPS[b][0] : PIN_VH + 0.6;
  const f = Math.max(0, Math.min(1, (s - start) / (end - start)));
  return 10 - 20 * f;
}

// Where the reader is in the film (0 = the title, 1-5), for a switch between the film and the static version.
export const placeOf = (b, k) => (b === 1 && k === 0 ? 0 : b);
// The scroll position (in viewport heights since the pin) that shows a place at rest.
export const restPosition = (beat) => (beat === 0 ? 0 : STEPS[beat - 1][STEPS[beat - 1].length - 1] + 0.3);

// Analytics. A scene is a beat 1-5 (the title and the 01 photo under it are not); it is at rest when the frame is
// pinned on screen and the beat shows its point: 01 with its copy and tag, 02 and 04 once their inner step has started.
export const sceneOf = (b, k) => placeOf(b, k);
export const restStep = (b) => (INNER[b] ? 1 : STEPS[b - 1].length - 1);
export function sceneAtRest({ b, k, pinned }) {
  return b >= 1 && b <= 5 && Boolean(pinned) && k === restStep(b);
}
// At least half of a box is on screen (half of the screen for a box taller than it).
export function halfInView(top, bottom, viewportHeight) {
  const visible = Math.max(0, Math.min(bottom, viewportHeight) - Math.max(top, 0));
  return bottom > top && visible > 0 && visible >= 0.5 * Math.min(bottom - top, viewportHeight);
}
