import { geoCentroid, geoGraticule10, geoOrthographic, geoPath } from "d3-geo";
import { feature } from "topojson-client";
import countries110 from "world-atlas/countries-110m.json";
import land110 from "world-atlas/land-110m.json";
import KOREA from "./data/tech-korea-map.json";
import SPRITE from "./data/storySprite.js";
import { classifyGlobeGesture } from "./globe-gesture.js";
import { TECH_COUNTRIES, TECH_PROVINCES, techScenes } from "./techStory.js";

// Port of 티온's approved #tech sketch (v8, 2026-10-23), made scroll-driven on 10-28 (티온: visitors flick through
// without reading, and waiting for a replay feels slow). Mounted by TechStory.jsx into its own markup; returns destroy.
// Follows the page scroll and never steers it: one passive scroll listener and rAF, no wheel/touch/key handlers on the
// page, no preventDefault, scroll-snap or programmatic scroll. Every listener, timer and frame is returned on destroy.
//
// The scroll position is the playhead. Each scene's one change (a place typed into the record field, its photos
// lifting, picked photos folding into a record, the map filling, the globe filling as it turns; 2026-10-29) follows
// the focus line through that scene's room:
// a fast scroll plays it in fast-forward, a stop leaves a readable still. Taps stay as optional extras: a tap in a
// scene takes it over from the scroll until the scene is entered again.
const LAND = feature(land110, land110.objects.land);
// the visited countries in fill order, each with the longitude it faces the camera from
const COUNTRIES = feature(countries110, countries110.objects.countries).features;
const FILLS = TECH_COUNTRIES.map((name) => COUNTRIES.find((f) => f.properties.name === name)).filter(Boolean);
const GRATICULE = geoGraticule10();
const SPRITE_URL = "/assets/story/tiles.webp";
const clamp = (v, a = 0, b = 1) => Math.min(b, Math.max(a, v));
// ease-in-out for anything scrubbed by the scroll: it settles into each stop instead of snapping
const smooth = (t) => { t = clamp(t); return t * t * (3 - 2 * t); };
const lerp = (a, b, t) => a + (b - a) * t;

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
  // each scene's position by its key (-1 when it is not in the story, like the share scene while it is off)
  const at = Object.fromEntries(["type", "gather", "safe", "save", "map", "globe", "share"].map((key) => [key, scenes.findIndex((s) => s.key === key)]));
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

  // ── scenes 1 and 2, one screen like the app's record form: as the scroll goes on a place is typed into the field
  // (scene 1), then that place's photos lift out of the grid one after another and a popup says how many (scene 2).
  // A tap on the field switches the place; a tap on any photo lifts every photo of its place.
  const gather = $("[data-gather]");
  // 72 photos including every 제주 photo: the scrubbed gather has a handful to lift, and a desktop phone fills its screen
  const tilesOf = (key) => [...Array(n).keys()].filter((k) => SPRITE.tiles[k][0] === key);
  const jejuAll = tilesOf("jeju");
  const order1 = shuffled(19).filter((k) => !jejuAll.includes(k)).slice(0, 72 - jejuAll.length);
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
  const field = $("[data-field]"), fieldText = $("[data-field-text]");
  const loadScreen = $("[data-load-screen]"), loadBtn = $("[data-load]"), safe = $("[data-safe]");
  // the places a tap on the field goes through (the sprite's place labels)
  const FIELD_PLACES = ["제주", "일본", "미국"];
  let foundTimer = 0, fieldPlace = FIELD_PLACES[0];
  const setText = (el, text) => { if (el.textContent !== text) el.textContent = text; };
  const setField = (text) => {
    setText(fieldText, text);
    field.classList.toggle("is-empty", !text);
  };
  // the form's state: what the field says, whether 사진 불러오기 is lit or pressed, whether the album shows, and
  // whether the other photos have gone and the safety line is up
  const setForm = (text, { ready = false, pressed = false, loaded = false, safeOn = false } = {}) => {
    setField(text);
    loadBtn.classList.toggle("is-ready", ready);
    loadBtn.classList.toggle("is-pressed", pressed);
    loadScreen.classList.toggle("is-loaded", loaded);
    safe.classList.toggle("is-shown", safeOn);
    gTiles.forEach((t) => t.classList.toggle("is-gone", safeOn && !t.classList.contains("is-hit")));
    if (safeOn) found.classList.remove("is-shown");
  };
  // scene 1: the place goes into the field a syllable at a time and 사진 불러오기 lights up, then is pressed. The typing
  // starts halfway through the room: on phones the first half passes before the stage pins
  function scrubType(q) {
    const chars = [...fieldPlace], typed = Math.round(smooth((q - 0.5) / 0.3) * chars.length);
    setForm(chars.slice(0, typed).join(""), { ready: typed === chars.length, pressed: q > 0.9 });
    layoutGather(null, 0);
    found.classList.remove("is-shown");
  }
  // ── scene 2 with motion (티온, 10-29 13:23Z): after 사진 불러오기 the album pours out of the phone across the whole
  // stage, the place's photos light up among them, and only those fly back into the phone's grid; the rest fade. The
  // scroll is the playhead, as everywhere. The place's photos are the very ones the grid in the phone shows, so the
  // landing hands over to the lifted grid without a jump. Reduced motion keeps the plain lift in the phone.
  const stage = $("[data-tech-stage]");
  const burst = document.createElement("div");
  burst.className = "ts-burst";
  burst.setAttribute("aria-hidden", "true");
  stage.appendChild(burst);
  let burstKey = "", bTiles = [];
  const rand = (seed) => { let x = seed; return () => { x = (x * 9301 + 49297) % 233280; return x / 233280; }; };
  // the tiles and where each one lands outside the phone: a jittered grid over the stage that leaves the header and the
  // scene's words clear; the place's photos are spread evenly through it
  function layoutBurst(place) {
    const sr = stage.getBoundingClientRect();
    const key = `${place}|${sr.width}|${sr.height}|${shown.length}|${shownCols}`;
    if (key === burstKey) return sr;
    burstKey = key;
    const size = narrow.matches ? 50 : 84, gap = narrow.matches ? 8 : 14, step = size + gap;
    const top = narrow.matches ? 76 : 92, words = captions[at.gather]?.querySelector("h3")?.parentElement.getBoundingClientRect();
    const keepOff = words && { l: words.left - sr.left - 12, r: words.right - sr.left + 12, t: words.top - sr.top - 12, b: words.bottom - sr.top + 12 };
    const cols = Math.max(1, Math.floor((sr.width - 8) / step)), rows = Math.max(1, Math.floor((sr.height - top - 8) / step));
    const ox = (sr.width - cols * step + gap) / 2, r = rand(7);
    let cells = [];
    for (let y = 0; y < rows; y++) for (let x = 0; x < cols; x++) {
      const cx = ox + x * step + size / 2 + (r() - 0.5) * gap * 1.6, cy = top + y * step + size / 2 + (r() - 0.5) * gap * 1.6;
      if (keepOff && cx + size / 2 > keepOff.l && cx - size / 2 < keepOff.r && cy + size / 2 > keepOff.t && cy - size / 2 < keepOff.b) continue;
      cells.push({ x: cx, y: cy, rot: (r() - 0.5) * 16, d: r() });
    }
    cells = cells.slice(0, 96);
    const mine = shown.map((t, i) => ({ k: +t.dataset.k, i })).filter((o) => placeOf(o.k) === place);
    const others = shuffled(31).filter((k) => placeOf(k) !== place);
    const every = mine.length ? cells.length / mine.length : Infinity;
    const placeAt = new Map(mine.map((o, j) => [Math.min(cells.length - 1, Math.floor((j + 0.5) * every)), o]));
    let o = 0;
    burst.innerHTML = "";
    bTiles = cells.map((c, idx) => {
      const m = placeAt.get(idx), k = m ? m.k : others[o++ % others.length];
      const el = document.createElement("div");
      el.className = "ts-ph";
      el.style.cssText = cellStyle(k);
      burst.appendChild(el);
      return { el, ...c, slot: m ? m.i : -1 };
    });
    burst.style.setProperty("--bs", `${size}px`);
    burst.dataset.size = size;
    return sr;
  }
  const hideBurst = () => burst.classList.remove("is-on");
  function scrubBurst(q) {
    const g = measureGrid();
    if (!g) return;
    const sr = layoutBurst(fieldPlace), size = +burst.dataset.size;
    // where the photos come from and go to: the album box inside the (turned) phone, as the page sees it
    const gr = gather.getBoundingClientRect(), fx = gr.width / (gather.clientWidth || 1), fy = gr.height / (gather.clientHeight || 1);
    const ox = gr.left - sr.left + gr.width / 2, oy = gr.top - sr.top + gr.height / 3;
    const out = (q - 0.04) / 0.36, lit = smooth((q - 0.42) / 0.12), back = (q - 0.56) / 0.3;
    // the album fades back in under the photos as they land, then the flying copies go
    const landed = q >= 0.88, album = q >= 0.78;
    setForm(fieldPlace, { ready: true, loaded: album });
    layoutGather(fieldPlace, album ? Infinity : 0);
    const total = shownOf(fieldPlace);
    setText(foundText, `${fieldPlace} 사진 ${total}장을 찾았어요`);
    found.classList.toggle("is-shown", landed && total > 0);
    burst.classList.toggle("is-on", out > 0 && !landed);
    if (out <= 0 || landed) return;
    const { cols, cw } = g;
    bTiles.forEach((t) => {
      const a = smooth(out * 1.5 - t.d * 0.5);
      let x = lerp(ox, t.x, a), y = lerp(oy, t.y, a), sc = lerp(0.2, 1, a), rot = t.rot * a, op = clamp(a * 4);
      if (t.slot >= 0) {
        const b = smooth(back * 1.4 - t.d * 0.4);
        const fxp = gr.left - sr.left + ((t.slot % cols) * (cw + GAP) + cw / 2) * fx;
        const fyp = gr.top - sr.top + (Math.floor(t.slot / cols) * (cw + GAP) + cw / 2) * fy - 3;
        x = lerp(x, fxp, b); y = lerp(y, fyp, b); sc = lerp(sc * (1 + 0.12 * lit), (cw * 1.12 * fx) / size, b); rot = lerp(rot, 0, b);
        t.el.classList.toggle("is-hit", lit > 0.5);
      } else {
        const b = smooth(back * 1.6 - t.d * 0.6);
        op *= lerp(1, 0.28, lit) * (1 - b);
        sc *= lerp(1, 0.86, b);
      }
      t.el.style.opacity = op.toFixed(3);
      t.el.style.transform = `translate(${(x - size / 2).toFixed(1)}px,${(y - size / 2).toFixed(1)}px) rotate(${rot.toFixed(1)}deg) scale(${sc.toFixed(3)})`;
    });
  }
  // scene 2: the album shows and the place's photos lift one after another
  function scrubGather(q) {
    if (!reduce) { scrubBurst(q); return; }
    if (!measureGrid()) return;
    setForm(fieldPlace, { ready: true, loaded: true });
    const total = shownOf(fieldPlace);
    const lifted = Math.round(smooth((q - 0.1) / 0.6) * total);
    layoutGather(fieldPlace, lifted);
    setText(foundText, `${fieldPlace} 사진 ${total}장을 찾았어요`);
    found.classList.toggle("is-shown", total > 0 && lifted >= total);
  }
  // scene 3: the found photos stay, the rest of the album fades, and the line that nothing left the phone rises
  function scrubSafe(q) {
    if (!measureGrid()) return;
    layoutGather(fieldPlace, Infinity);
    setForm(fieldPlace, { ready: true, loaded: true, safeOn: q > 0.15 });
  }
  function gatherTo(place) {
    fieldPlace = place;
    setForm(place, { ready: true, loaded: true });
    const total = layoutGather(place, Infinity, true);
    found.classList.remove("is-shown");
    clearTimeout(foundTimer);
    foundText.textContent = `${place} 사진 ${total}장을 찾았어요`;
    foundTimer = later(() => found.classList.add("is-shown"), reduce ? 0 : 200 + total * 45);
  }
  // by hand the scenes of the screen stay with the tap until they are entered again
  const byHandGather = (i) => { [at.type, at.gather, at.safe].forEach((k) => manual.add(k)); tried(i); };
  on(gather, "click", (e) => {
    const t = e.target.closest(".ts-ph");
    if (!t) return;
    byHandGather(at.gather);
    gatherTo(placeOf(+t.dataset.k));
  });
  on(field, "click", () => {
    byHandGather(at.type);
    gatherTo(FIELD_PLACES[(FIELD_PLACES.indexOf(fieldPlace) + 1) % FIELD_PLACES.length]);
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
  const byHandPick = () => { pick.classList.add("is-manual"); takeOver(at.save); };
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

  // ── scene 4: as the scroll goes on, the 시·도 the records visited fill one by one in date order; a tap clears the
  // map, a tap again fills it
  const regions = $("[data-regions]");
  const [w, h] = [KOREA.w + 12, KOREA.h + 12];
  regions.innerHTML = `<svg viewBox="-6 -6 ${w} ${h}" aria-hidden="true"><defs><path id="ts-kr" d="${KOREA.kr}"/></defs>`
    + '<use class="ts-kr" href="#ts-kr"/>'
    + TECH_PROVINCES.map((name) => `<path class="ts-pv" d="${KOREA.pv[name]}"/>`).join("")
    + `<use class="ts-ln is-dist" href="#ts-kr"/><path class="ts-ln is-sd" d="${KOREA.sd}"/></svg>`;
  const provinces = [...regions.querySelectorAll(".ts-pv")];
  let filled = 0;
  function setFilled(count) {
    filled = count;
    provinces.forEach((el, i) => el.classList.toggle("is-on", i < count));
    regions.setAttribute("aria-pressed", String(count > 0));
  }
  const scrubMap = (q) => setFilled(Math.round(smooth((q - 0.1) / 0.7) * provinces.length));
  const toggleFill = () => { takeOver(at.map); setFilled(filled > 0 ? 0 : provinces.length); };
  on(regions, "click", toggleFill);
  on(regions, "keydown", (e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); toggleFill(); } });

  // ── scene 5 (d3 orthographic on canvas): sea, graticule, land and the visited countries filled. As the scroll goes on
  // the globe turns from one visited country to the next in date order and each fills as it comes round; a drag or
  // the arrow keys turn it more.
  const globe = $("[data-globe]");
  let globeKey = "";
  function drawGlobe(rotate, fills) {
    const cw = globe.clientWidth, ch = globe.clientHeight;
    if (!cw || !ch) return;
    const c = palette();
    const key = `${cw}x${ch}|${rotate.map((v) => v.toFixed(2))}|${fills}|${c.sea}`;
    if (globeKey === key) return;
    globeKey = key;
    const ctx = globe.getContext("2d"), dpr = Math.min(devicePixelRatio || 1, 2);
    if (globe.width !== Math.round(cw * dpr) || globe.height !== Math.round(ch * dpr)) { globe.width = Math.round(cw * dpr); globe.height = Math.round(ch * dpr); }
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, cw, ch);
    const R = Math.min(cw, ch) * 0.47;
    const path = geoPath(geoOrthographic().translate([cw / 2, ch / 2]).scale(R).rotate(rotate).clipAngle(90), ctx);
    ctx.beginPath(); path({ type: "Sphere" }); ctx.fillStyle = c.sea; ctx.fill();
    ctx.beginPath(); path(GRATICULE); ctx.strokeStyle = c.grid; ctx.lineWidth = 0.5; ctx.stroke();
    ctx.beginPath(); path(LAND); ctx.fillStyle = c.land; ctx.fill();
    ctx.beginPath(); FILLS.slice(0, fills).forEach((f) => path(f)); ctx.fillStyle = c.accent; ctx.fill();
  }
  // where each visited country faces the camera from; the turn between two takes the short way round
  const FACES = FILLS.map((f) => geoCentroid(f));
  const turnTo = (a, b, t) => a + ((((b - a) % 360) + 540) % 360 - 180) * t;
  let dragRot = 0, dragX = null, dragStart = null;
  function scrubGlobe(q) {
    if (!FACES.length) return;
    const f = clamp((q - 0.05) / 0.85) * (FACES.length - 1), i = Math.min(FACES.length - 2, Math.floor(f));
    const t = reduce ? Math.round(f - i) : smooth(f - i), [a, b] = [FACES[i], FACES[i + 1] ?? FACES[i]];
    const lon = turnTo(a[0], b[0], t), lat = a[1] + (b[1] - a[1]) * t;
    // a country fills as the turn arrives at it
    drawGlobe([-lon + dragRot, -lat * 0.6], Math.min(FILLS.length, i + 1 + (f - i > 0.6 ? 1 : 0)));
  }
  // same rule as the interactive globe: a tap or a phone's vertical page pan is not a drag
  on(globe, "pointerdown", (e) => { dragStart = e; dragX = null; });
  on(globe, "pointermove", (e) => {
    if (dragStart && dragX === null) {
      const kind = classifyGlobeGesture(dragStart, e);
      if (kind === "pending") return;
      if (kind === "page_scroll") { dragStart = null; return; }
      dragX = dragStart.clientX;
      globe.setPointerCapture?.(e.pointerId);
      tried(at.globe);
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
    tried(at.globe);
    schedule();
  });

  // ── scene 6 (TECH_SHARE.enabled, off since 10-29): the share video. Never plays by itself; a year button plays from that year, a tap pauses or plays.
  const shareVideo = $("[data-share-video]");
  const shareYears = $("[data-share-years]"), shareBox = $("[data-share-box]");
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
        tried(at.share);
        seek(+b.dataset.at);
      });
    }
    shareBox.classList.add("is-paused");
  }

  // ── scenes and scroll
  const screens = $$("[data-scene]");
  // the first three scenes share the app's form screen; every later scene has its own
  const screenOf = (i) => Math.max(0, i - at.safe);
  const beats = $$("[data-beat]");
  const captions = $$("[data-caption]");
  const bridges = $$("[data-bridge]");
  const wrap = $("[data-phone-wrap]"), phone = $("[data-phone]"), captionsEl = $(".ts-captions");
  let active = -1, frameRaf = 0, visible = false, shownCaption = null;
  // the words change in place with the scene
  function showCaption(el) {
    if (el === shownCaption) return;
    captions.forEach((c) => c.classList.toggle("is-on", c === el));
    shownCaption = el;
  }
  // entering a scene hands it back to the scroll
  const backToScroll = () => { [at.type, at.gather, at.safe].forEach((k) => manual.delete(k)); clearTimeout(foundTimer); };
  const onEnter = {
    [at.type]: () => { backToScroll(); fieldPlace = FIELD_PLACES[0]; },
    [at.gather]: backToScroll,
    [at.safe]: backToScroll,
    [at.save]: () => { manual.delete(at.save); pick.classList.remove("is-manual"); },
    [at.map]: () => manual.delete(at.map),
  };
  // i = -1 while the section is off screen: everything stops, and re-entry starts the scene again
  function setActive(i) {
    if (i === active) return;
    active = i;
    if (i >= 0) screens.forEach((s, k) => s.classList.toggle("is-on", k === screenOf(i)));
    if (i !== at.share) shareVideo?.pause?.();
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
    if (act !== at.gather || manual.has(act)) hideBurst();
    if (!manual.has(act)) {
      if (act === at.type) scrubType(q);
      else if (act === at.gather) scrubGather(q);
      else if (act === at.safe) scrubSafe(q);
      else if (act === at.save) scrubPick(q);
      else if (act === at.map) scrubMap(q);
    }
    if (act === at.globe) scrubGlobe(q);
    const raw = bridges.length ? (() => { const r = bridges[0].getBoundingClientRect(); return clamp((focus - r.top) / r.height); })() : 0;
    const p = reduce ? (raw > 0.5 ? 1 : 0) : smooth(raw);
    // side: -1 = phone on the left (step 1), +1 = on the right (step 2)
    const side = -1 + 2 * p;
    if (narrow.matches) {
      // Phones tell the steps apart by the angle the phone is seen from (티온, 10-27): it stays centred and turns a
      // little toward one side for step 1 and the other for step 2 (the same way round as on desktop), and the turn
      // between them follows the scroll through the room between the steps.
      wrap.style.transform = "translate(-50%,-50%)";
      phone.style.transform = `rotateY(${(-14 * side).toFixed(2)}deg) rotateZ(${(side * 1.2).toFixed(2)}deg)`;
    } else {
      // 0.22 of a 1000px row (was 0.26 of 1120): the phone and its words sit closer, no empty third between them
      const span = Math.min(captionsEl.clientWidth, 1000);
      wrap.style.transform = `translate(calc(-50% + ${0.22 * (-1 + 2 * p) * span}px),-50%)`;
      phone.style.transform = `rotateY(${(-18 * side).toFixed(2)}deg) rotateZ(${(side * 1.5).toFixed(2)}deg)`;
    }
  }
  const schedule = () => { if (visible && !frameRaf) frameRaf = requestAnimationFrame(frame); };
  on(window, "scroll", schedule, { passive: true });
  // measure and move only while the story is on screen
  const onScreen = new IntersectionObserver(([entry]) => {
    visible = entry.isIntersecting;
    if (visible) schedule();
    else { cancelAnimationFrame(frameRaf); frameRaf = 0; hideBurst(); setActive(-1); onScene(0); }
  });
  onScreen.observe($(".ts-story"));
  const resize = new ResizeObserver(() => {
    layoutGather(gatherState.place, gatherState.count, gatherState.stagger);
    burstKey = "";
    layoutPick(fold);
    globeKey = "";
    schedule();
  });
  resize.observe(root);
  // the header's theme toggle flips data-theme on <html>: redraw canvases with the new tokens
  const theme = new MutationObserver(() => { colors = null; schedule(); });
  theme.observe(document.documentElement, { attributes: true, attributeFilter: ["data-theme"] });

  root.classList.add("is-live");
  setForm("");
  layoutGather(null, 0);
  layoutPick(0);
  setFilled(0);

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
    gather.innerHTML = "";
    burst.remove();
    pTiles.forEach((t) => t.remove());
    regions.innerHTML = "";
  };
  // re-reads the scroll position now (the scene-reach confirm asks for a fresh read before counting a scene)
  destroy.sample = () => { if (visible) frame(); };
  return destroy;
}
