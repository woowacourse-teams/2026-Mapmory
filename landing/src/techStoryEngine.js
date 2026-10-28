import { geoDistance, geoGraticule10, geoInterpolate, geoOrthographic, geoPath } from "d3-geo";
import { feature } from "topojson-client";
import land110 from "world-atlas/land-110m.json";
import KOREA from "./data/tech-korea-map.json";
import SPRITE from "./data/storySprite.js";
import { classifyGlobeGesture } from "./globe-gesture.js";
import { TECH_MONTHS, TECH_PIN, TECH_TRIP, TECH_VISITED, techScenes } from "./techStory.js";

// Port of 티온's approved #tech sketch (v8, 2026-10-23), made scroll-driven on 10-28 (티온: visitors flick through
// without reading, and waiting for a replay feels slow). Mounted by TechStory.jsx into its own markup; returns destroy.
// Follows the page scroll and never steers it: one passive scroll listener and rAF, no wheel/touch/key handlers on the
// page, no preventDefault, scroll-snap or programmatic scroll. Every listener, timer and frame is returned on destroy.
//
// The scroll position is the playhead. Each scene's one change (photos turning over, a place's photos lifting, picked
// photos folding into a record, the map filling, the globe's trip) follows the focus line through that scene's room:
// a fast scroll plays it in fast-forward, a stop leaves a readable still. Taps stay as optional extras: a tap in a
// scene takes it over from the scroll until the scene is entered again.
const LAND = feature(land110, land110.objects.land);
const GRATICULE = geoGraticule10();
const SPRITE_URL = "/assets/story/tiles.webp";
const PIN = `<svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="${TECH_PIN}"/></svg>`;
const clamp = (v, a = 0, b = 1) => Math.min(b, Math.max(a, v));
// ease-in-out for anything scrubbed by the scroll: it settles into each stop instead of snapping
const smooth = (t) => { t = clamp(t); return t * t * (3 - 2 * t); };
const lerp = (a, b, t) => a + (b - a) * t;
const span = (p, [a, b]) => clamp((p - a) / (b - a));

export function mountTechStory(root, { share, onScene = () => {}, onInteract = () => {} }) {
  const $ = (selector) => root.querySelector(selector);
  const $$ = (selector) => [...root.querySelectorAll(selector)];
  const reduce = matchMedia("(prefers-reduced-motion: reduce)").matches;
  const narrow = matchMedia("(max-width: 760px)");
  const scenes = techScenes(share);
  const cleanups = [];
  const timers = new Set();
  const later = (fn, ms) => { const id = setTimeout(() => { timers.delete(id); fn(); }, ms); timers.add(id); return id; };
  const on = (el, type, fn, opts) => { el.addEventListener(type, fn, opts); cleanups.push(() => el.removeEventListener(type, fn, opts)); };
  let colors = null;
  const palette = () => (colors ??= Object.fromEntries(["sea", "land", "grid", "accent", "screen"].map((k) => [k, getComputedStyle(root).getPropertyValue(`--ts-${k}`).trim()])));
  const tried = (i) => onInteract(i + 1, scenes[i].interaction);
  // scenes a tap has taken over from the scroll, until they are entered again
  const manual = new Set();
  const takeOver = (i) => { manual.add(i); tried(i); };

  // sprite cells (16 columns of 104px), sized to each element by percentage
  const n = SPRITE.tiles.length;
  const rows = Math.ceil(n / SPRITE.cols);
  const placeOf = (k) => SPRITE.tiles[k][1];
  const cellStyle = (k) => `background-position:${((k % SPRITE.cols) / (SPRITE.cols - 1)) * 100}% ${(Math.floor(k / SPRITE.cols) / (rows - 1)) * 100}%;background-color:${SPRITE.colors[k]}`;
  // resolved against the page: a url() inside a custom property resolves against the stylesheet that uses it
  root.style.setProperty("--ts-atlas", `url("${new URL(SPRITE_URL, document.baseURI).href}")`);
  root.style.setProperty("--ts-bs", `${SPRITE.cols * 100}% ${rows * 100}%`);
  const shuffled = (seed) => {
    const a = [...Array(n).keys()];
    let s = seed;
    for (let i = n - 1; i > 0; i--) { s = (s * 9301 + 49297) % 233280; const j = Math.floor((s / 233280) * (i + 1)); [a[i], a[j]] = [a[j], a[i]]; }
    return a;
  };

  // ── scene 1: the camera roll moves up with the scroll, and the photos passing the middle turn over to where and
  // when they were taken; a hover (tap without hover, Enter on the roll) turns any photo by hand
  const roll = $("[data-roll]"), rollView = roll.parentElement;
  // 36 photos twice: a whole number of rows in 3, 4 or 6 columns, so the roll has no seam if it ever wraps
  const order0 = shuffled(7).slice(0, 36);
  const flip = (k) => `<div class="ts-flip"><div class="ts-flip-in"><div class="ts-ph" style="${cellStyle(k)}"></div><div class="ts-flip-back">${PIN}${placeOf(k)}<small>${TECH_MONTHS[placeOf(k)] ?? ""}</small></div></div></div>`;
  roll.innerHTML = [...order0, ...order0].map(flip).join("");
  const flips = [...roll.children];
  const byHand = new Set();
  let rollMids = [], rollHalf = 0, rollRow = 0;
  function measureRoll() {
    rollMids = flips.map((f) => f.offsetTop + f.offsetHeight / 2);
    rollHalf = roll.offsetHeight / 2;
    rollRow = flips[0]?.offsetHeight ?? 0;
  }
  function layoutRoll(q) {
    if (!rollRow) measureRoll();
    if (!rollRow) return;
    const view = rollView.clientHeight;
    // reduced motion: the roll stays put and the turned-over row walks down it instead
    const off = reduce ? 0 : q * rollHalf;
    roll.style.transform = `translateY(${(-off).toFixed(1)}px)`;
    const band = reduce ? lerp(rollRow * 0.6, view - rollRow * 0.6, q) : view * 0.45;
    flips.forEach((f, k) => { if (!byHand.has(f)) f.classList.toggle("is-open", Math.abs(rollMids[k] - off - band) < rollRow * 0.55); });
  }
  const turn = (tile) => { byHand.add(tile); tile.classList.toggle("is-open"); tried(0); };
  on(roll, "click", (e) => { const f = e.target.closest(".ts-flip"); if (f) turn(f); });
  // a hover counts only when the mouse itself moves onto a photo (CSS turns it over)
  on(roll, "pointermove", (e) => { if (e.pointerType === "mouse" && (e.movementX || e.movementY) && e.target.closest(".ts-flip")) tried(0); });
  const nearestMiddle = () => {
    const v = rollView.getBoundingClientRect(), cx = v.left + v.width / 2, cy = v.top + v.height * 0.45;
    let tile = null, best = Infinity;
    for (const f of roll.children) { const r = f.getBoundingClientRect(); const d = Math.hypot(r.left + r.width / 2 - cx, r.top + r.height / 2 - cy); if (d < best) { best = d; tile = f; } }
    return tile;
  };
  // keyboard: the roll is one tab stop (72 photos would be 72 stops); Enter or Space turns the photo nearest the middle
  on(rollView, "keydown", (e) => {
    if (e.key !== "Enter" && e.key !== " ") return;
    e.preventDefault();
    const tile = nearestMiddle();
    if (tile) turn(tile);
  });

  // ── scene 2: as the scroll goes on, the 제주 photos lift out of the grid one after another and a popup counts them;
  // a tap on any photo lifts every photo of that place instead
  const gather = $("[data-gather]");
  // 42 photos including every 제주 photo, so the scrubbed gather has a handful to lift
  const tilesOf = (key) => [...Array(n).keys()].filter((k) => SPRITE.tiles[k][0] === key);
  const jejuAll = tilesOf("jeju");
  const order1 = shuffled(19).filter((k) => !jejuAll.includes(k)).slice(0, 42 - jejuAll.length);
  gather.innerHTML = [...jejuAll, ...order1].map((k) => `<button type="button" class="ts-ph" data-k="${k}" aria-label="${placeOf(k)} 사진" style="${cellStyle(k)}"></button>`).join("");
  const gTiles = [...gather.children];
  const jejuTiles = gTiles.slice(0, jejuAll.length), otherTiles = gTiles.slice(jejuAll.length);
  let shown = [], shownCols = 0, shownCw = 0, gatherState = { place: null, count: 0, stagger: false };
  // the grid shows as many photos as fit (6 columns; on phones 3-4 columns of about 46px or more, for a finger) with
  // the 제주 photos spread through it; the rest are hidden, and the DOM order follows the grid for keyboard order
  function arrange(count, cols) {
    const slots = new Array(count).fill(null);
    const J = jejuTiles.length, rows = Math.ceil(count / cols);
    jejuTiles.forEach((t, j) => {
      // one per row band, stepping across columns, so they never line up in one column
      let i = Math.min(count - 1, Math.floor(((j + 0.5) * rows) / J) * cols + ((j * 5 + 2) % cols));
      while (slots[i]) i = (i + 1) % count;
      slots[i] = t;
    });
    let o = 0;
    for (let i = 0; i < count; i++) if (!slots[i]) slots[i] = otherTiles[o++];
    const rest = otherTiles.slice(o);
    [...slots, ...rest].forEach((t) => gather.appendChild(t));
    slots.forEach((t) => { t.hidden = false; });
    rest.forEach((t) => { t.hidden = true; });
    return slots;
  }
  // `count` photos of `place` (in grid order) are lifted; the others dim once the first has lifted. Returns how many
  // photos of the place the grid shows.
  // the grid's columns and cell size for the current box; the tiles are rearranged only when the count or the
  // columns change, so a scroll frame costs one pass over the shown tiles
  const GAP = 3;
  function measureGrid() {
    const w = gather.clientWidth, ht = gather.clientHeight;
    if (!w || !ht) return null;
    const cols = narrow.matches ? Math.max(3, Math.min(4, Math.floor((w + GAP) / (46 + GAP)))) : 6;
    const cw = (w - GAP * (cols - 1)) / cols;
    const total = Math.min(gTiles.length, cols * Math.max(1, Math.floor((ht + GAP) / (cw + GAP))));
    if (total !== shown.length || cols !== shownCols) { shown = arrange(total, cols); shownCols = cols; }
    if (cw !== shownCw) { shownCw = cw; gather.style.setProperty("--cw", `${cw}px`); }
    return { cols, cw };
  }
  const shownOf = (place) => shown.reduce((c, t) => c + (placeOf(+t.dataset.k) === place ? 1 : 0), 0);
  function layoutGather(place, count = Infinity, stagger = false) {
    gatherState = { place, count, stagger };
    const g = measureGrid();
    if (!g) return 0;
    const { cols, cw } = g, gap = GAP;
    let h = 0;
    shown.forEach((t, i) => {
      const ofPlace = !!place && placeOf(+t.dataset.k) === place;
      const hit = ofPlace && h < count;
      if (ofPlace) h++;
      t.style.transitionDelay = stagger && hit ? `${(h - 1) * 45}ms` : "0ms";
      t.style.transform = `translate(${(i % cols) * (cw + gap)}px,${Math.floor(i / cols) * (cw + gap)}px)${hit ? " translateY(-3px) scale(1.12)" : ""}`;
      t.classList.toggle("is-hit", hit);
      t.classList.toggle("is-dim", !!place && !ofPlace && count > 0);
    });
    return h;
  }
  const found = $("[data-found]"), foundText = $("[data-found-text]");
  const cntN = $("[data-count-n]"), cntT = $("[data-count-t]"), cntP = $("[data-count-place]");
  const setCounter = (e) => { cntN.textContent = Math.round(30000 * e).toLocaleString("ko-KR"); cntT.textContent = String(Math.round(30 * e)); };
  let foundTimer = 0;
  const setText = (el, text) => { if (el.textContent !== text) el.textContent = text; };
  function scrubGather(q) {
    if (!measureGrid()) return;
    const total = shownOf("제주");
    const lifted = Math.round(smooth((q - 0.12) / 0.55) * total);
    layoutGather("제주", lifted);
    setText(cntP, "제주");
    setText(foundText, `제주 사진 ${total}장을 모았어요`);
    found.classList.toggle("is-shown", total > 0 && lifted >= total);
    setCounter(smooth((q - 0.05) / 0.6));
  }
  function gatherTo(place) {
    cntP.textContent = place;
    const total = layoutGather(place, Infinity, true);
    found.classList.remove("is-shown");
    clearTimeout(foundTimer);
    foundText.textContent = `${place} 사진 ${total}장을 모았어요`;
    foundTimer = later(() => found.classList.add("is-shown"), reduce ? 0 : 200 + total * 45);
    setCounter(1);
  }
  on(gather, "click", (e) => {
    const t = e.target.closest(".ts-ph");
    if (!t) return;
    takeOver(1);
    gatherTo(placeOf(+t.dataset.k));
  });

  // ── scene 3: as the scroll goes on, three photos are picked and fold into one record card; a tap picks or unpicks a
  // photo and 기록하기 folds them by hand
  const pick = $("[data-pick]");
  const jeju = jejuAll.slice(0, 6), japan = tilesOf("japan").slice(0, 3);
  pick.insertAdjacentHTML("afterbegin", [...jeju, ...japan].map((k) => `<button type="button" class="ts-ph" data-place="${placeOf(k)}" aria-pressed="false" aria-label="${placeOf(k)} 사진 고르기" style="${cellStyle(k)}"></button>`).join(""));
  const pTiles = $$("[data-pick] .ts-ph");
  const save = $("[data-save]"), recText = $("[data-rec-text]");
  const PICKS = [[0, 0.1], [2, 0.22], [4, 0.34]];
  let fold = 0;
  const setPicked = (t, sel) => { t.classList.toggle("is-sel", sel); t.setAttribute("aria-pressed", String(sel)); };
  // f: how far the picked photos have folded into the stack (0 grid, 1 stack); the others fade as they fold
  function layoutPick(f) {
    fold = f;
    const w = pick.clientWidth, gap = 5, pw = (w - gap * 2) / 3;
    // the grid (and the folded stack) sit in the middle of the space above the button, not stuck to the top
    const top = Math.max(0, (pick.clientHeight - (pw * 3 + gap * 2)) / 2);
    pick.style.setProperty("--pw", `${pw}px`);
    const picked = pTiles.filter((t) => t.classList.contains("is-sel"));
    const count = picked.length, mid = (count - 1) / 2;
    let s = 0;
    pTiles.forEach((t, i) => {
      const gx = (i % 3) * (pw + gap), gy = top + Math.floor(i / 3) * (pw + gap);
      if (t.classList.contains("is-sel")) {
        const sx = w / 2 - pw / 2 + (s - mid) * 10, sy = top + pw * 0.45 + s * 4, rot = (s - mid) * 6;
        s++;
        t.style.transform = `translate(${lerp(gx, sx, f).toFixed(1)}px,${lerp(gy, sy, f).toFixed(1)}px) rotate(${(rot * f).toFixed(1)}deg)`;
        t.style.opacity = "";
      } else {
        t.style.transform = `translate(${gx}px,${gy}px)`;
        t.style.opacity = f > 0 ? String(1 - f) : "";
      }
      // folded: the stack's top photo (last in the DOM, painted on top) stays a button that unfolds it; the photos
      // under it are covered and the unpicked ones invisible, so they leave the tab order (axe target-size, 5.5)
      t.disabled = f >= 0.9 && t !== picked[count - 1];
    });
    pick.classList.toggle("is-folding", f > 0);
    pick.classList.toggle("is-folded", f >= 0.9);
    pick.style.setProperty("--rt", `${top + pw * 1.45 + 30}px`);
    // the card is named after the place most of the picked photos come from
    const tally = {};
    picked.forEach((t) => { tally[t.dataset.place] = (tally[t.dataset.place] ?? 0) + 1; });
    const place = Object.keys(tally).sort((a, b) => tally[b] - tally[a])[0] ?? "제주";
    recText.textContent = `${place} 여행 · ${count}장`;
    save.disabled = f >= 0.9 || count === 0;
    save.textContent = f >= 0.9 ? "기록했어요" : "기록하기";
  }
  function scrubPick(q) {
    PICKS.forEach(([i, at]) => setPicked(pTiles[i], q >= at));
    layoutPick(smooth((q - 0.52) / 0.3));
  }
  // by hand the fold is a CSS transition (`.is-manual`), by scroll the frames are the animation
  const byHandPick = () => { pick.classList.add("is-manual"); takeOver(2); };
  on(pick, "click", (e) => {
    const t = e.target.closest(".ts-ph");
    // a tap anywhere on the folded card unfolds it; otherwise only a photo counts, blank space is not a try
    if (fold >= 0.9) { byHandPick(); layoutPick(0); return; }
    if (!t) return;
    byHandPick();
    setPicked(t, !t.classList.contains("is-sel"));
    layoutPick(0);
  });
  on(save, "click", () => { byHandPick(); layoutPick(1); });

  // ── scene 4: as the scroll goes on, 경주시 -> 경상북도 -> 대한민국 fill; a tap clears the map, a tap again fills it
  const regions = $("[data-regions]");
  const [w, h] = [KOREA.w + 12, KOREA.h + 12];
  regions.innerHTML = `<svg viewBox="-6 -6 ${w} ${h}" aria-hidden="true"><defs><path id="ts-kr" d="${KOREA.kr}"/></defs>`
    + '<use class="ts-lv is-kr" href="#ts-kr"/>'
    + `<path class="ts-lv is-gb" d="${KOREA.gb}"/><path class="ts-lv is-gj" d="${KOREA.gj}"/>`
    + `<use class="ts-ln is-dist" href="#ts-kr"/><path class="ts-ln is-sd" d="${KOREA.sd}"/>`
    + `<text class="ts-tag is-t1" x="${KOREA.gjc[0] - 6}" y="${KOREA.gjc[1] + 3}" text-anchor="end">경주시</text>`
    + `<text class="ts-tag is-t2" x="${KOREA.gbc[0]}" y="${KOREA.gbc[1]}" text-anchor="middle">경상북도</text>`
    + `<text class="ts-tag is-t3" x="${KOREA.krc[0]}" y="${KOREA.krc[1]}" text-anchor="end">대한민국</text></svg>`;
  const chip = $("[data-region-chip]"), chipText = $("[data-region-chip-text]");
  const LEVELS = ["", "경주시", "경상북도", "대한민국"];
  let level = 0, chipTimers = [];
  // `staged`: by hand the three levels follow one another (the same beats as .is-staged in tech-story.css)
  function setLevel(lvl, staged = false) {
    level = lvl;
    regions.classList.toggle("is-staged", staged);
    for (let l = 1; l <= 3; l++) regions.classList.toggle(`is-l${l}`, lvl >= l);
    regions.classList.toggle("is-filled", lvl > 0);
    regions.setAttribute("aria-pressed", String(lvl > 0));
    chipTimers.forEach((id) => { clearTimeout(id); timers.delete(id); });
    chipTimers = [];
    chip.classList.toggle("is-shown", lvl > 0);
    if (!lvl) return;
    if (!staged || reduce) { chipText.textContent = LEVELS[lvl]; return; }
    for (let l = 1; l <= lvl; l++) chipTimers.push(later(() => { chipText.textContent = LEVELS[l]; }, (l - 1) * 350));
  }
  const scrubMap = (q) => setLevel(q < 0.12 ? 0 : q < 0.42 ? 1 : q < 0.72 ? 2 : 3);
  const toggleFill = () => { takeOver(3); setLevel(level > 0 ? 0 : 3, true); };
  on(regions, "click", toggleFill);
  on(regions, "keydown", (e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); toggleFill(); } });

  // ── globes (d3 orthographic on canvas): sea, graticule, land, route, dots, the flying tip. `zoom` above 1 looks
  // closer at a part of the globe through its round outline.
  const keys = new WeakMap();
  function drawGlobe(canvas, rotate, o) {
    const cw = canvas.clientWidth, ch = canvas.clientHeight;
    if (!cw || !ch) return;
    const c = palette(), zoom = o.zoom ?? 1;
    const key = `${cw}x${ch}|${rotate.map((v) => v.toFixed(2))}|${zoom.toFixed(3)}|${o.route?.length ?? 0}|${o.tip?.map((v) => v.toFixed(2)) ?? ""}|${c.sea}`;
    if (keys.get(canvas) === key) return;
    keys.set(canvas, key);
    const ctx = canvas.getContext("2d"), dpr = Math.min(devicePixelRatio || 1, 2);
    if (canvas.width !== Math.round(cw * dpr) || canvas.height !== Math.round(ch * dpr)) { canvas.width = Math.round(cw * dpr); canvas.height = Math.round(ch * dpr); }
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, cw, ch);
    const R = Math.min(cw, ch) * 0.47, cx = cw / 2, cy = ch / 2;
    ctx.save();
    if (zoom > 1) { ctx.beginPath(); ctx.arc(cx, cy, R, 0, Math.PI * 2); ctx.clip(); }
    const path = geoPath(geoOrthographic().translate([cx, cy]).scale(R * zoom).rotate(rotate).clipAngle(90), ctx);
    ctx.beginPath(); path({ type: "Sphere" }); ctx.fillStyle = c.sea; ctx.fill();
    ctx.beginPath(); path(GRATICULE); ctx.strokeStyle = c.grid; ctx.lineWidth = 0.5; ctx.stroke();
    ctx.beginPath(); path(LAND); ctx.fillStyle = c.land; ctx.fill();
    if (o.route?.length > 1) { ctx.beginPath(); path({ type: "LineString", coordinates: o.route }); ctx.strokeStyle = c.accent; ctx.lineWidth = Math.max(1.5, R * 0.012); ctx.lineCap = "round"; ctx.lineJoin = "round"; ctx.stroke(); }
    ctx.fillStyle = c.accent;
    path.pointRadius(Math.max(2.5, R * 0.02));
    o.points.forEach((pt) => { ctx.beginPath(); path({ type: "Point", coordinates: pt }); ctx.fill(); });
    if (o.tip) {
      path.pointRadius(Math.max(5, R * 0.045)); ctx.beginPath(); path({ type: "Point", coordinates: o.tip }); ctx.fillStyle = c.screen; ctx.fill();
      path.pointRadius(Math.max(3, R * 0.028)); ctx.beginPath(); path({ type: "Point", coordinates: o.tip }); ctx.fillStyle = c.accent; ctx.fill();
    }
    ctx.restore();
  }

  // ── scenes 5 and 6, one trip: the globe turns a little with the scroll inside the phone (a drag or the arrow keys
  // turn it more), comes out of the phone, flies through the recorded places in date order at the speed of the
  // scroll, and goes back into the phone as the video to share
  const globe = $("[data-globe]"), big = $("[data-trip-globe]"), hud = $("[data-trip]"), hudName = $("[data-trip-name]"), hudDate = $("[data-trip-date]");
  const shareBtn = $("[data-share-btn]"), shareBox = $("[data-share-box]");
  const stops = TECH_TRIP, ll = (s) => [s.lon, s.lat];
  const LEGS = stops.slice(1).map((s, i) => ({ arc: geoInterpolate(ll(stops[i]), ll(s)), d: (geoDistance(ll(stops[i]), ll(s)) * 180) / Math.PI }));
  // scroll room per leg: a hop at home is quick, a flight abroad takes longer
  const weights = LEGS.map((l) => 0.45 + 1.4 * Math.sqrt(l.d / 180));
  const wsum = weights.reduce((a, b) => a + b, 0);
  const starts = weights.reduce((acc, w) => [...acc, acc[acc.length - 1] + w / wsum], [0]);
  // the camera comes closer for a short hop, so it can be seen, and backs out for a flight
  const zoomOf = (d) => clamp(1 + 1.6 * (1 - d / 30), 1, 2.6);
  const legZoom = LEGS.map((l) => zoomOf(l.d));
  const stopZoom = stops.map((_, k) => (k === 0 ? legZoom[0] : k === LEGS.length ? legZoom[k - 1] : (legZoom[k - 1] + legZoom[k]) / 2));
  const facing = (s) => [-s.lon, -s.lat * 0.6];
  // the trip's room is the globe scene's (long) beat and the share scene's beat together. Its phases hang off where
  // the share beat begins (`pShare`, measured each frame, since the beat heights are CSS): the globe starts back a
  // little before the share screen appears and lands just after, so it always lands into the dark share screen
  // whatever the beat heights or the viewport. Without the share scene the room is the globe beat alone.
  const phases = (pShare) => share.enabled
    ? { out: [0.06, 0.2], trip: [0.2, pShare - 0.09], back: [pShare - 0.09, pShare + 0.03], land: pShare + 0.03 }
    : { out: [0.08, 0.26], trip: [0.26, 0.86], back: [0.86, 0.98], land: 0.98 };
  let dragRot = 0, dragX = null, dragStart = null, GS = 300, bigOn = false, hudOn = false, hudAt = -1;
  const drawInPhone = (rot, o = {}) => drawGlobe(globe, [rot, -22], { points: TECH_VISITED, ...o });
  // same rule as the interactive globe: a tap or a phone's vertical page pan is not a drag
  on(globe, "pointerdown", (e) => { dragStart = e; dragX = null; });
  on(globe, "pointermove", (e) => {
    if (dragStart && dragX === null) {
      const kind = classifyGlobeGesture(dragStart, e);
      if (kind === "pending") return;
      if (kind === "page_scroll") { dragStart = null; return; }
      dragX = dragStart.clientX;
      globe.setPointerCapture?.(e.pointerId);
      tried(4);
    }
    if (dragX === null) return;
    dragRot += (e.clientX - dragX) * 0.5;
    dragX = e.clientX;
    schedule();
  });
  ["pointerup", "pointercancel"].forEach((type) => on(globe, type, () => { dragX = null; dragStart = null; }));
  // keyboard: the arrow keys turn the globe a step at a time
  on(globe, "keydown", (e) => {
    const step = e.key === "ArrowRight" ? 15 : e.key === "ArrowLeft" ? -15 : 0;
    if (!step) return;
    e.preventDefault();
    dragRot += step;
    tried(4);
    schedule();
  });
  const showHud = (at, x, y) => {
    if (at !== hudAt) { hudAt = at; hudName.textContent = stops[at].name; hudDate.textContent = stops[at].date.slice(0, 8); }
    hud.style.transform = `translate(${x.toFixed(1)}px,${y.toFixed(1)}px) translateX(-50%)`;
    if (!hudOn) { hudOn = true; hud.classList.add("is-on"); }
  };
  const hideHud = () => { if (hudOn) { hudOn = false; hud.classList.remove("is-on"); } };
  const hideBig = () => { if (bigOn) { bigOn = false; big.classList.remove("is-on"); globe.style.opacity = ""; } };
  // the big globe: as wide as the stage allows, no wider than 440px, and on phones no taller than the room between the
  // header and the words under the phone
  function sizeBig(st, wr) {
    const cy = wr.top - st.top + wr.height / 2;
    let gs = Math.min(st.width * 0.92, 440);
    if (narrow.matches) { const cap = captionsEl.getBoundingClientRect(); gs = Math.min(gs, 2 * (cap.top - st.top - cy) - 76, 2 * (cy - 72)); } else gs = Math.min(gs, st.height * 0.56);
    gs = Math.max(140, Math.round(gs));
    if (gs !== GS) { GS = gs; big.style.width = big.style.height = `${GS}px`; }
    return { x: wr.left - st.left + wr.width / 2, y: cy, s: 1 };
  }
  // where the trip is, 0..1, from the focus line through the trip's room. The room's end is where the stage unpins
  // (the beats' bottom meets the stage's bottom), which the focus line itself never reaches.
  function tripProgress(focus) {
    const r4 = beats[4].getBoundingClientRect(), r5 = beats[5]?.getBoundingClientRect(), rl = r5 ?? r4;
    const end = rl.bottom - (stage.getBoundingClientRect().bottom - focus);
    const room = Math.max(1, end - r4.top);
    // pShare: where the share beat (scene 6) begins in the room; kept away from the ends so the trip keeps its room
    return { p: clamp((focus - r4.top) / room), pShare: r5 ? clamp((r5.top - r4.top) / room, 0.5, 0.95) : 1 };
  }
  // returns how the phone itself should look: `tilt` 1 = the step's angle, 0 = facing the visitor
  function tripFrame(focus) {
    const { p, pShare } = tripProgress(focus);
    const P = phases(pShare);
    const inPhase = p < P.out[0];
    const rotIn = -110 + smooth(p / P.out[0]) * 30 + dragRot;
    const look = { tilt: 1 - smooth(p / P.out[0]), scale: 1, alpha: 1 };
    shareBtn?.classList.toggle("is-pressed", p >= P.land + 0.06);
    if (reduce) {
      // reduced motion: the globe stays in the phone; the route draws in and the view steps from place to place
      hideBig();
      if (p < P.trip[0]) { drawInPhone(rotIn); hideHud(); return { tilt: 1, scale: 1, alpha: 1 }; }
      const u = span(p, P.trip), i = Math.min(LEGS.length - 1, starts.findIndex((s, k) => u >= s && u < (starts[k + 1] ?? 2)));
      const local = span(u, [starts[i], starts[i + 1]]), at = p >= P.trip[1] ? stops.length - 1 : local < 0.5 ? i : i + 1;
      drawGlobe(globe, facing(stops[at]), { points: TECH_VISITED, route: stops.slice(0, at + 1).map(ll), tip: ll(stops[at]) });
      if (p < P.back[0]) { const r = globe.getBoundingClientRect(), st = stage.getBoundingClientRect(); showHud(at, r.left - st.left + r.width / 2, r.bottom - st.top + 8); } else hideHud();
      return { tilt: 1, scale: 1, alpha: 1 };
    }
    if (inPhase) { hideBig(); hideHud(); drawInPhone(rotIn); return look; }
    const st = stage.getBoundingClientRect(), wr = wrap.getBoundingClientRect();
    const bigPos = sizeBig(st, wr);
    // where the globe sits inside the phone: the globe screen on the way out, the share screen on the way back
    const home = share.enabled && p >= P.back[0] && shareBox ? shareBox : globe;
    const hr = home.getBoundingClientRect();
    const small = { x: hr.left - st.left + hr.width / 2, y: hr.top - st.top + hr.height / 2, s: Math.min(hr.width, hr.height) / GS };
    let pos, rotate, zoom = 1, route = [], tip = null, alpha = 1;
    if (p < P.out[1]) {
      const e = smooth(span(p, P.out));
      pos = { x: lerp(small.x, bigPos.x, e), y: lerp(small.y, bigPos.y, e), s: lerp(small.s, 1, e) };
      look.scale = lerp(1, 0.9, e); look.alpha = lerp(1, 0.28, e);
      const to = facing(stops[0]);
      rotate = [lerp(-110 + 30 + dragRot, to[0], e), lerp(-22, to[1], e)];
      zoom = lerp(1, stopZoom[0], e);
      tip = e > 0.5 ? ll(stops[0]) : null;
      hideHud();
    } else if (p < P.trip[1]) {
      const u = span(p, P.trip);
      let i = starts.findIndex((s, k) => u >= s && u < (starts[k + 1] ?? 2));
      i = clamp(i, 0, LEGS.length - 1);
      const local = span(u, [starts[i], starts[i + 1]]), e = smooth(local);
      tip = LEGS[i].arc(e);
      pos = bigPos; look.scale = 0.9; look.alpha = 0.28;
      rotate = [-tip[0], -tip[1] * 0.6];
      zoom = local < 0.25 ? lerp(stopZoom[i], legZoom[i], local / 0.25) : local > 0.75 ? lerp(legZoom[i], stopZoom[i + 1], (local - 0.75) / 0.25) : legZoom[i];
      route = [...stops.slice(0, i + 1).map(ll), tip];
      showHud(local < 0.5 ? i : i + 1, bigPos.x, bigPos.y + GS / 2 + 14);
    } else {
      const e = smooth(span(p, P.back)), last = stops[stops.length - 1];
      pos = { x: lerp(bigPos.x, small.x, e), y: lerp(bigPos.y, small.y, e), s: lerp(1, small.s, e) };
      look.scale = lerp(0.9, 1, e); look.alpha = lerp(0.28, 1, e);
      const f = facing(last);
      rotate = [f[0] + 10 * e, f[1]];
      zoom = lerp(stopZoom[stops.length - 1], 1, e);
      route = stops.map(ll); tip = ll(last);
      alpha = 1 - clamp((p - P.land) / 0.04);
      hideHud();
    }
    if (!bigOn) { bigOn = true; big.classList.add("is-on"); globe.style.opacity = "0"; }
    big.style.opacity = alpha.toFixed(3);
    big.style.transform = `translate(${(pos.x - (GS * pos.s) / 2).toFixed(1)}px,${(pos.y - (GS * pos.s) / 2).toFixed(1)}px) scale(${pos.s.toFixed(4)})`;
    drawGlobe(big, rotate, { points: TECH_VISITED, route, tip, zoom });
    // the globe screen keeps the finished route for the way back when there is no share screen
    if (!share.enabled && p >= P.back[0]) drawGlobe(globe, facing(stops[stops.length - 1]), { points: TECH_VISITED, route: stops.map(ll), tip: ll(stops[stops.length - 1]) });
    return look;
  }

  // ── scene 6: the share video. Never plays by itself; a year button plays from that year, a tap pauses or plays.
  const shareVideo = $("[data-share-video]");
  const shareYears = $("[data-share-years]");
  let loadVideo = () => Promise.resolve(), videoBlob = "", gone = false;
  if (shareVideo) {
    // muted, so it may play on phones too (with playsinline). The first source this browser can play, resolved against
    // the page like the sprite (the <source> children stay for a page without this script).
    const source = share.media.sources.find((x) => shareVideo.canPlayType(x.type)) ?? share.media.sources.at(-1);
    const url = new URL(source.src, document.baseURI).href;
    if (share.media.poster) shareVideo.poster = new URL(share.media.poster, document.baseURI).href;
    // Fetched whole (about 300-400KB) the first time a visitor shows intent on the share screen (a pointer over it, a
    // touch, focus), not on reaching the scene, so scrolling past costs nothing; whole, so a year button can seek even
    // on a host that does not answer range requests. The plain URL if the fetch fails.
    let loading = null;
    loadVideo = () => (loading ??= fetch(url)
      .then((r) => (r.ok ? r.blob() : Promise.reject(new Error(String(r.status)))))
      .then((blob) => { if (gone) return; videoBlob = URL.createObjectURL(blob); shareVideo.src = videoBlob; })
      .catch(() => { if (!gone) shareVideo.src = url; }));
    for (const ev of ["pointerenter", "pointerdown", "focusin"]) on(shareBox, ev, () => loadVideo(), { passive: true });
    shareVideo.removeAttribute("src");
    shareVideo.querySelectorAll("source").forEach((el) => el.remove());
    const yearButtons = shareYears ? [...shareYears.children] : [];
    const syncYear = () => {
      const t = shareVideo.currentTime + 0.05;
      let k = 0;
      yearButtons.forEach((b, i) => { if (t >= +b.dataset.at) k = i; });
      yearButtons.forEach((b, i) => { b.classList.toggle("is-on", i === k); b.setAttribute("aria-pressed", String(i === k)); });
    };
    on(shareVideo, "timeupdate", syncYear);
    on(shareVideo, "seeked", syncYear);
    on(shareVideo, "play", () => shareBox.classList.remove("is-paused"));
    on(shareVideo, "pause", () => shareBox.classList.add("is-paused"));
    const play = () => loadVideo().then(() => shareVideo.play()).catch(() => {});
    const seek = (at) => loadVideo().then(() => {
      const go = () => { shareVideo.currentTime = at; syncYear(); };
      if (shareVideo.readyState >= 1) go(); else on(shareVideo, "loadedmetadata", go, { once: true });
      return shareVideo.play();
    }).catch(() => {});
    const toggle = () => { if (shareVideo.paused) play(); else shareVideo.pause(); };
    on(shareVideo, "click", toggle);
    on(shareVideo, "keydown", (e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); toggle(); } });
    if (shareYears) {
      on(shareYears, "click", (e) => {
        const b = e.target.closest("button");
        if (!b) return;
        tried(5);
        seek(+b.dataset.at);
      });
    }
    shareBox.classList.add("is-paused");
  }

  // ── scenes and scroll
  const screens = $$("[data-scene]");
  const beats = $$("[data-beat]");
  const captions = $$("[data-caption]");
  const bridges = $$("[data-bridge]");
  const stage = $("[data-tech-stage]"), wrap = $("[data-phone-wrap]"), phone = $("[data-phone]"), captionsEl = $(".ts-captions");
  let active = -1, frameRaf = 0, visible = false, shownCaption = null;
  // the words change in place with the scene
  function showCaption(el) {
    if (el === shownCaption) return;
    captions.forEach((c) => c.classList.toggle("is-on", c === el));
    shownCaption = el;
  }
  // entering a scene hands it back to the scroll
  const onEnter = [
    () => byHand.clear(),
    () => { manual.delete(1); clearTimeout(foundTimer); },
    () => { manual.delete(2); pick.classList.remove("is-manual"); },
    () => manual.delete(3),
    () => {},
    () => {},
  ];
  // i = -1 while the section is off screen: everything stops, and re-entry starts the scene again
  function setActive(i) {
    if (i === active) return;
    active = i;
    if (i >= 0) screens.forEach((s, k) => s.classList.toggle("is-on", k === i));
    if (i !== 5) shareVideo?.pause?.();
    if (i < 4) { hideBig(); hideHud(); }
    if (i < 0) return;
    onEnter[i]?.();
    onScene(i + 1);
  }
  function frame() {
    frameRaf = 0;
    const vh = innerHeight;
    // the focus line: the middle of the band the words sit in (the viewport middle on desktop; on phones the middle
    // of the room under the phone, or above it when the words come first)
    const focus = narrow.matches ? (() => { const r = captionsEl.getBoundingClientRect(); return r.top + r.height / 2; })() : vh / 2;
    // the scene whose room the focus line is in, and how far through it (q); between rooms the nearest scene
    let act = -1, q = 0, near = 0, nearD = Infinity, nearTop = 0;
    beats.forEach((b) => {
      const r = b.getBoundingClientRect(), i = +b.dataset.beat;
      if (focus >= r.top && focus < r.bottom) { act = i; q = (focus - r.top) / r.height; }
      const d = Math.abs(r.top + r.height / 2 - focus);
      if (d < nearD) { nearD = d; near = i; nearTop = r.top; }
    });
    if (act < 0) { act = near; q = focus < nearTop ? 0 : 1; }
    setActive(act);
    showCaption(captions[act]);
    if (!manual.has(act)) {
      if (act === 0) layoutRoll(q);
      else if (act === 1) scrubGather(q);
      else if (act === 2) scrubPick(q);
      else if (act === 3) scrubMap(q);
    }
    const look = act >= 4 ? tripFrame(focus) : { tilt: 1, scale: 1, alpha: 1 };
    const raw = bridges.length ? (() => { const r = bridges[0].getBoundingClientRect(); return clamp((focus - r.top) / r.height); })() : 0;
    const p = reduce ? (raw > 0.5 ? 1 : 0) : smooth(raw);
    // side: -1 = phone on the left (step 1), +1 = on the right (step 2); the phone faces the visitor for the trip
    const side = (-1 + 2 * p) * look.tilt;
    const scale = look.scale === 1 ? "" : ` scale(${look.scale.toFixed(4)})`;
    if (narrow.matches) {
      // Phones tell the steps apart by the angle the phone is seen from (티온, 10-27): it stays centred and turns a
      // little toward one side for step 1 and the other for step 2 (the same way round as on desktop), and the turn
      // between them follows the scroll through the room between the steps.
      wrap.style.transform = "translate(-50%,-50%)";
      phone.style.transform = `rotateY(${(-14 * side).toFixed(2)}deg) rotateZ(${(side * 1.2).toFixed(2)}deg)${scale}`;
    } else {
      const span = Math.min(captionsEl.clientWidth, 1120);
      wrap.style.transform = `translate(calc(-50% + ${0.26 * (-1 + 2 * p) * span}px),-50%)`;
      phone.style.transform = `rotateY(${(-18 * side).toFixed(2)}deg) rotateZ(${(side * 1.5).toFixed(2)}deg)${scale}`;
    }
    phone.style.opacity = look.alpha === 1 ? "" : look.alpha.toFixed(3);
  }
  const schedule = () => { if (visible && !frameRaf) frameRaf = requestAnimationFrame(frame); };
  on(window, "scroll", schedule, { passive: true });
  // measure and move only while the story is on screen
  const onScreen = new IntersectionObserver(([entry]) => {
    visible = entry.isIntersecting;
    if (visible) schedule();
    else { cancelAnimationFrame(frameRaf); frameRaf = 0; setActive(-1); onScene(0); }
  });
  onScreen.observe($(".ts-story"));
  const resize = new ResizeObserver(() => {
    measureRoll();
    layoutGather(gatherState.place, gatherState.count, gatherState.stagger);
    layoutPick(fold);
    keys.delete(globe); keys.delete(big);
    schedule();
  });
  resize.observe(root);
  // the header's theme toggle flips data-theme on <html>: redraw canvases with the new tokens
  const theme = new MutationObserver(() => { colors = null; schedule(); });
  theme.observe(document.documentElement, { attributes: true, attributeFilter: ["data-theme"] });

  root.classList.add("is-live");
  measureRoll();
  layoutRoll(0);
  layoutGather("제주", 0);
  layoutPick(0);
  setLevel(0);

  const destroy = () => {
    gone = true;
    cleanups.forEach((fn) => fn());
    timers.forEach((id) => clearTimeout(id));
    cancelAnimationFrame(frameRaf);
    shareVideo?.pause();
    if (videoBlob) URL.revokeObjectURL(videoBlob);
    resize.disconnect();
    onScreen.disconnect();
    theme.disconnect();
    root.classList.remove("is-live");
    roll.innerHTML = "";
    gather.innerHTML = "";
    pTiles.forEach((t) => t.remove());
    regions.innerHTML = "";
  };
  // re-reads the scroll position now (the scene-reach confirm asks for a fresh read before counting a scene)
  destroy.sample = () => { if (visible) frame(); };
  return destroy;
}
