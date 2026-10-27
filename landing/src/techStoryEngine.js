import { geoGraticule10, geoInterpolate, geoOrthographic, geoPath } from "d3-geo";
import { feature } from "topojson-client";
import land110 from "world-atlas/land-110m.json";
import KOREA from "./data/tech-korea-map.json";
import SPRITE from "./data/storySprite.js";
import { classifyGlobeGesture } from "./globe-gesture.js";
import { TECH_MONTHS, TECH_VISITED, techScenes } from "./techStory.js";

// Port of 티온's approved #tech sketch (v8, 2026-10-23). Mounted by TechStory.jsx into its own markup; returns destroy.
// Follows the page scroll and never steers it: one passive scroll listener and rAF, no wheel/touch/key handlers on the
// page, no preventDefault, scroll-snap or programmatic scroll. Every listener, timer and frame is returned on destroy.
const LAND = feature(land110, land110.objects.land);
const GRATICULE = geoGraticule10();
const SPRITE_URL = "/assets/story/tiles.webp";
const PIN = '<svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M12 2a7 7 0 0 0-7 7c0 5 7 13 7 13s7-8 7-13a7 7 0 0 0-7-7Zm0 9.5A2.5 2.5 0 1 1 12 6.5a2.5 2.5 0 0 1 0 5Z"/></svg>';
const ease = (t) => (t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2);

export function mountTechStory(root, { share, onScene = () => {}, onInteract = () => {} }) {
  const $ = (selector) => root.querySelector(selector);
  const $$ = (selector) => [...root.querySelectorAll(selector)];
  const reduce = matchMedia("(prefers-reduced-motion: reduce)").matches;
  const narrow = matchMedia("(max-width: 760px)");
  const canHover = matchMedia("(hover: hover)").matches;
  const scenes = techScenes(share);
  const cleanups = [];
  const timers = new Set();
  const later = (fn, ms) => { const id = setTimeout(() => { timers.delete(id); fn(); }, ms); timers.add(id); return id; };
  const on = (el, type, fn, opts) => { el.addEventListener(type, fn, opts); cleanups.push(() => el.removeEventListener(type, fn, opts)); };
  const token = (name) => getComputedStyle(root).getPropertyValue(name).trim();
  const done = new Set();
  const tried = (i) => { done.add(i); onInteract(i + 1, scenes[i].interaction); };

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

  // without hover, a scene's hint can say "press" instead of "point"
  if (!canHover) $$("[data-scene]").forEach((screen, i) => { const hint = screen.querySelector(".ts-hint"); if (hint && scenes[i]?.hintTouch) hint.textContent = scenes[i].hintTouch; });

  // ── scene 1: a rolling camera roll; hover (tap without hover) flips a photo to where and when it was taken
  const roll = $("[data-roll]"), rollView = roll.parentElement;
  const hintFlip = $("[data-hint-flip]");
  // 36 photos twice: a whole number of rows in 3, 4 or 6 columns, so the loop has no seam
  const order0 = shuffled(7).slice(0, 36);
  const flip = (k) => `<div class="ts-flip"><div class="ts-flip-in"><div class="ts-ph" style="${cellStyle(k)}"></div><div class="ts-flip-back">${PIN}${placeOf(k)}<small>${TECH_MONTHS[placeOf(k)] ?? ""}</small></div></div></div>`;
  roll.innerHTML = [...order0, ...order0].map(flip).join("");
  let flipDemo = [], holdTimer = 0;
  const stopFlipDemo = () => { flipDemo.forEach((id) => { clearTimeout(id); timers.delete(id); }); flipDemo = []; };
  const flipTried = () => { stopFlipDemo(); hintFlip.classList.remove("is-try"); tried(0); };
  on(roll, "click", (e) => { const f = e.target.closest(".ts-flip"); if (!f) return; f.classList.toggle("is-open"); flipTried(); });
  // a hover counts only when the mouse itself moves onto a photo (the roll also slides photos under a resting cursor)
  on(roll, "pointermove", (e) => { if (e.pointerType === "mouse" && (e.movementX || e.movementY) && e.target.closest(".ts-flip")) flipTried(); });
  // a finger stops the roll for a moment, so the photo it opened stays under it
  const hold = (ms) => { rollView.classList.add("is-held"); clearTimeout(holdTimer); timers.delete(holdTimer); holdTimer = later(() => rollView.classList.remove("is-held"), ms); };
  on(rollView, "pointerdown", (e) => { if (e.pointerType !== "mouse") hold(3000); });
  // once per visit until tried: the photo nearest the middle turns over by itself, so the back is seen once
  function demoFlip() {
    if (done.has(0)) return;
    flipDemo.push(later(() => {
      if (active !== 0) return;
      const v = rollView.getBoundingClientRect(), cx = v.left + v.width / 2, cy = v.top + v.height * 0.4;
      let tile = null, best = Infinity;
      for (const f of roll.children) { const r = f.getBoundingClientRect(); const d = Math.hypot(r.left + r.width / 2 - cx, r.top + r.height / 2 - cy); if (d < best) { best = d; tile = f; } }
      if (!tile) return;
      hold(1900);
      tile.classList.add("is-open");
      flipDemo.push(later(() => { tile.classList.remove("is-open"); hintFlip.classList.add("is-try"); }, 1700));
    }, reduce ? 300 : 900));
  }

  // ── scene 2: tap any photo; every photo of that place lifts in place and a popup names the place
  const gather = $("[data-gather]");
  // 42 photos including every 제주 photo, so the first gather (제주, automatic) has a handful to lift
  const jejuAll = [...Array(n).keys()].filter((k) => SPRITE.tiles[k][0] === "jeju");
  const order1 = shuffled(19).filter((k) => !jejuAll.includes(k)).slice(0, 42 - jejuAll.length);
  gather.innerHTML = [...jejuAll, ...order1].map((k) => `<button type="button" class="ts-ph" data-k="${k}" aria-label="${placeOf(k)} 사진" style="${cellStyle(k)}"></button>`).join("");
  const gTiles = [...gather.children];
  const jejuTiles = gTiles.slice(0, jejuAll.length), otherTiles = gTiles.slice(jejuAll.length);
  let gathered = null, shown = [], shownCols = 0;
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
  function layoutGather(place) {
    gathered = place;
    const w = gather.clientWidth, ht = gather.clientHeight, gap = 3;
    if (!w || !ht) return 0;
    const cols = narrow.matches ? Math.max(3, Math.min(4, Math.floor((w + gap) / (46 + gap)))) : 6;
    const cw = (w - gap * (cols - 1)) / cols;
    const count = Math.min(gTiles.length, cols * Math.max(1, Math.floor((ht + gap) / (cw + gap))));
    if (count !== shown.length || cols !== shownCols) { shown = arrange(count, cols); shownCols = cols; }
    gather.style.setProperty("--cw", `${cw}px`);
    let h = 0;
    shown.forEach((t, i) => {
      const hit = !!place && placeOf(+t.dataset.k) === place;
      t.style.transitionDelay = hit ? `${h++ * 45}ms` : "0ms";
      t.style.transform = `translate(${(i % cols) * (cw + gap)}px,${Math.floor(i / cols) * (cw + gap)}px)${hit ? " translateY(-3px) scale(1.12)" : ""}`;
      t.classList.toggle("is-hit", hit);
      t.classList.toggle("is-dim", !!place && !hit);
    });
    return h;
  }
  const found = $("[data-found]"), foundText = $("[data-found-text]");
  const cntN = $("[data-count-n]"), cntT = $("[data-count-t]"), cntP = $("[data-count-place]");
  let countRaf = 0;
  function runCounter() {
    cancelAnimationFrame(countRaf);
    if (reduce) { cntN.textContent = "30,000"; cntT.textContent = "30"; return; }
    const t0 = performance.now();
    const step = (now) => {
      const p = Math.min(1, (now - t0) / 1800), e = 1 - Math.pow(1 - p, 3);
      cntN.textContent = Math.round(30000 * e).toLocaleString("ko-KR");
      cntT.textContent = Math.round(30 * e);
      if (p < 1) countRaf = requestAnimationFrame(step);
    };
    countRaf = requestAnimationFrame(step);
  }
  let foundTimer = 0;
  function gatherTo(place) {
    cntP.textContent = place;
    const h = layoutGather(place);
    found.classList.remove("is-shown");
    clearTimeout(foundTimer);
    foundText.textContent = `${place} 사진 ${h}장을 모았어요`;
    foundTimer = later(() => found.classList.add("is-shown"), reduce ? 0 : 250 + h * 45);
    runCounter();
  }
  on(gather, "click", (e) => {
    const t = e.target.closest(".ts-ph");
    if (!t) return;
    gather.classList.remove("is-try");
    tried(1);
    gatherTo(placeOf(+t.dataset.k));
  });

  // ── scene 3: pick photos, then 기록하기 folds them into one record card
  const pick = $("[data-pick]");
  const jeju = [...Array(n).keys()].filter((k) => SPRITE.tiles[k][0] === "jeju").slice(0, 6);
  const japan = [...Array(n).keys()].filter((k) => SPRITE.tiles[k][0] === "japan").slice(0, 3);
  pick.insertAdjacentHTML("afterbegin", [...jeju, ...japan].map((k, i) => {
    const sel = i % 2 === 0 && i < 6;
    return `<button type="button" class="ts-ph${sel ? " is-sel" : ""}" data-place="${placeOf(k)}" aria-pressed="${sel}" aria-label="${placeOf(k)} 사진 고르기" style="${cellStyle(k)}"></button>`;
  }).join(""));
  const pTiles = $$("[data-pick] .ts-ph");
  const save = $("[data-save]"), recText = $("[data-rec-text]");
  function layoutPick() {
    const w = pick.clientWidth, gap = 5, pw = (w - gap * 2) / 3;
    // the grid (and the folded stack) sit in the middle of the space above the button, not stuck to the top
    const top = Math.max(0, (pick.clientHeight - (pw * 3 + gap * 2)) / 2);
    pick.style.setProperty("--pw", `${pw}px`);
    const folded = pick.classList.contains("is-folded");
    let s = 0;
    pTiles.forEach((t, i) => {
      if (folded && t.classList.contains("is-sel")) {
        t.style.transform = `translate(${w / 2 - pw / 2 + (s - 1.5) * 10}px,${top + pw * 0.45 + s * 4}px) rotate(${(s - 1.5) * 6}deg)`;
        s++;
      } else {
        t.style.transform = `translate(${(i % 3) * (pw + gap)}px,${top + Math.floor(i / 3) * (pw + gap)}px)`;
      }
    });
    const picked = pTiles.filter((t) => t.classList.contains("is-sel"));
    const count = picked.length;
    pick.style.setProperty("--rt", `${top + pw * 1.45 + 30}px`);
    // the card is named after the place most of the picked photos come from
    const tally = {};
    picked.forEach((t) => { tally[t.dataset.place] = (tally[t.dataset.place] ?? 0) + 1; });
    const place = Object.keys(tally).sort((a, b) => tally[b] - tally[a])[0] ?? "제주";
    recText.textContent = `${place} 여행 · ${count}장`;
    save.disabled = folded || count === 0;
    save.textContent = folded ? "기록했어요" : "기록하기";
  }
  const fold = (folded) => { pick.classList.toggle("is-folded", folded); layoutPick(); };
  let foldDemo = [];
  const stopFoldDemo = () => { foldDemo.forEach((id) => { clearTimeout(id); timers.delete(id); }); foldDemo = []; };
  on(pick, "click", (e) => {
    stopFoldDemo();
    if (pick.classList.contains("is-folded")) { fold(false); return; }
    const t = e.target.closest(".ts-ph");
    if (!t) return;
    t.classList.toggle("is-sel");
    t.setAttribute("aria-pressed", t.classList.contains("is-sel"));
    layoutPick();
  });
  on(save, "click", () => { stopFoldDemo(); save.classList.remove("is-try"); tried(2); fold(true); });

  // ── scene 4: tap the map to fill 경주시 -> 경상북도 -> 대한민국, tap again to clear it
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
  let chipTimers = [];
  // the same beats as the fill in tech-story.css (150ms, 850ms, 1550ms)
  const LEVELS = [["경주시", 150], ["경상북도", 850], ["대한민국", 1550]];
  function setFill(filled) {
    regions.classList.toggle("is-filled", filled);
    regions.setAttribute("aria-pressed", String(filled));
    chipTimers.forEach((id) => { clearTimeout(id); timers.delete(id); });
    chipTimers = [];
    if (!filled) { chip.classList.remove("is-shown"); return; }
    LEVELS.forEach(([name, ms]) => chipTimers.push(later(() => { chipText.textContent = name; chip.classList.add("is-shown"); }, reduce ? 0 : ms)));
  }
  const toggleFill = () => { regions.classList.remove("is-try"); tried(3); setFill(!regions.classList.contains("is-filled")); };
  on(regions, "click", toggleFill);
  on(regions, "keydown", (e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); toggleFill(); } });

  // ── globes (d3 orthographic on canvas): sea, graticule, land, route, dots
  function drawGlobe(canvas, rotate, o) {
    const ctx = canvas.getContext("2d"), dpr = Math.min(devicePixelRatio || 1, 2), cw = canvas.clientWidth, ch = canvas.clientHeight;
    if (!cw || !ch) return;
    if (canvas.width !== Math.round(cw * dpr) || canvas.height !== Math.round(ch * dpr)) { canvas.width = Math.round(cw * dpr); canvas.height = Math.round(ch * dpr); }
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, cw, ch);
    const path = geoPath(geoOrthographic().translate([cw / 2, ch / 2]).scale(Math.min(cw, ch) * 0.46).rotate(rotate).clipAngle(90), ctx);
    ctx.beginPath(); path({ type: "Sphere" }); ctx.fillStyle = o.sea; ctx.fill();
    ctx.beginPath(); path(GRATICULE); ctx.strokeStyle = o.grid; ctx.lineWidth = 0.5; ctx.stroke();
    ctx.beginPath(); path(LAND); ctx.fillStyle = o.land; ctx.fill();
    if (o.route?.length > 1) { ctx.beginPath(); path({ type: "LineString", coordinates: o.route }); ctx.strokeStyle = o.dot; ctx.lineWidth = 2; ctx.stroke(); }
    ctx.fillStyle = o.dot;
    path.pointRadius(o.r || 4);
    o.points.forEach((pt) => { ctx.beginPath(); path({ type: "Point", coordinates: pt }); ctx.fill(); });
  }

  // ── scene 5: drag the globe to spin it (horizontal only; touch-action: pan-y keeps vertical page scroll)
  const globe = $("[data-globe]");
  let rot = -110, globeRaf = 0, dragX = null, dragStart = null;
  const drawGlobe5 = () => drawGlobe(globe, [rot, -22], { sea: token("--ts-sea"), land: token("--ts-land"), grid: token("--ts-grid"), dot: token("--ts-accent"), points: TECH_VISITED });
  function startGlobe() {
    if (globeRaf) return;
    const tick = () => { if (dragX === null) rot += 0.3; drawGlobe5(); globeRaf = requestAnimationFrame(tick); };
    if (reduce) drawGlobe5(); else tick();
  }
  const stopGlobe = () => { cancelAnimationFrame(globeRaf); globeRaf = 0; };
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
    rot += (e.clientX - dragX) * 0.5;
    dragX = e.clientX;
    if (!globeRaf) drawGlobe5();
  });
  ["pointerup", "pointercancel"].forEach((type) => on(globe, type, () => { dragX = null; dragStart = null; }));

  // ── scene 6: the share video's look; the drawn preview flies country to country, a year replays its leg
  const timeline = share.enabled && !share.media ? mountTimeline() : null;
  const shareVideo = $("[data-share-video]");
  const shareYears = $("[data-share-years]");
  let loadVideo = () => Promise.resolve(), videoBlob = "";
  if (shareVideo) {
    const box = $("[data-share-box]");
    // muted, so it may play by itself on phones too (with playsinline). The first source this browser can play,
    // resolved against the page like the sprite (the <source> children stay for a page without this script).
    const source = share.media.sources.find((x) => shareVideo.canPlayType(x.type)) ?? share.media.sources.at(-1);
    const url = new URL(source.src, document.baseURI).href;
    if (share.media.poster) shareVideo.poster = new URL(share.media.poster, document.baseURI).href;
    // Fetched whole (about 300-400KB) from the globe scene on, so a year button can seek even on a host that does not
    // answer range requests; the plain URL if the fetch fails.
    let loading = null;
    loadVideo = () => (loading ??= fetch(url)
      .then((r) => (r.ok ? r.blob() : Promise.reject(new Error(String(r.status)))))
      .then((blob) => { videoBlob = URL.createObjectURL(blob); shareVideo.src = videoBlob; })
      .catch(() => { shareVideo.src = url; }));
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
    on(shareVideo, "play", () => box.classList.remove("is-paused"));
    on(shareVideo, "pause", () => box.classList.add("is-paused"));
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
        shareYears.classList.remove("is-try");
        tried(5);
        seek(+b.dataset.at);
      });
    }
    box.classList.add("is-paused");
  }
  function mountTimeline() {
    const stops = share.stops;
    const canvas = $("[data-tl-globe]"), label = $("[data-tl-label]"), years = $("[data-tl-years]"), shareBtn = $("[data-share-btn]");
    const yearButtons = [...years.children];
    const ll = (s) => [s.lon, s.lat];
    let raf = 0, cur = ll(stops[0]), reached = 0, autoplay = false, next = 0;
    const clearNext = () => { clearTimeout(next); timers.delete(next); next = 0; };
    const showLabel = (i) => { label.innerHTML = `<b>${stops[i].name}</b><span>${stops[i].date}</span>`; };
    const draw = (route) => drawGlobe(canvas, [-cur[0], -cur[1] * 0.6], {
      sea: token("--ts-tl-sea"), land: token("--ts-tl-land"), grid: token("--ts-tl-grid"), dot: token("--ts-accent"), r: 5, route,
      points: stops.slice(0, reached + 1).map(ll),
    });
    // finished legs and the growing leg are both great-circle arcs, so the line drawn in flight is the line that stays
    const routeTo = (i, tip) => [...stops.slice(0, i + 1).map(ll), ...(tip ? [tip] : [])];
    function flyTo(i, then) {
      cancelAnimationFrame(raf);
      const from = i > 0 ? ll(stops[i - 1]) : [stops[0].lon + 70, stops[0].lat - 10];
      const arc = geoInterpolate(from, ll(stops[i])), t0 = performance.now(), dur = reduce ? 0 : 1500;
      reached = i - 1;
      if (i > 0) showLabel(i - 1);
      yearButtons.forEach((b, k) => b.classList.toggle("is-on", k === i));
      const step = (now) => {
        const p = dur ? Math.min(1, (now - t0) / dur) : 1, e = ease(p);
        cur = arc(e);
        if (e > 0.8) showLabel(i);
        draw(i > 0 ? routeTo(i - 1, cur) : []);
        if (p < 1) { raf = requestAnimationFrame(step); return; }
        raf = 0;
        reached = i;
        draw(routeTo(i));
        then?.();
      };
      raf = requestAnimationFrame(step);
    }
    function playFrom(i) {
      if (!autoplay) return;
      if (i >= stops.length) { shareBtn.classList.add("is-pressed"); years.classList.add("is-try"); return; }
      flyTo(i, () => { next = later(() => playFrom(i + 1), reduce ? 0 : 500); });
    }
    on(years, "click", (e) => {
      const b = e.target.closest("button");
      if (!b) return;
      autoplay = false;
      clearNext();
      years.classList.remove("is-try");
      shareBtn.classList.remove("is-pressed");
      tried(5);
      flyTo(+b.dataset.i);
    });
    return {
      start() {
        autoplay = true; reached = 0; cur = ll(stops[0]);
        shareBtn.classList.remove("is-pressed"); years.classList.remove("is-try");
        yearButtons.forEach((b, k) => b.classList.toggle("is-on", k === 0));
        showLabel(0); draw([]);
        clearNext();
        next = later(() => playFrom(1), reduce ? 0 : 500);
      },
      stop() { autoplay = false; clearNext(); cancelAnimationFrame(raf); raf = 0; },
      redraw() { draw(routeTo(reached)); },
    };
  }

  // ── scenes and scroll
  const screens = $$("[data-scene]");
  const beats = $$("[data-beat]");
  const copies = beats.map((b) => b.querySelector(".ts-copy"));
  const bridges = $$("[data-bridge]");
  const wrap = $("[data-phone-wrap]"), phone = $("[data-phone]"), beatsEl = $(".ts-beats");
  let active = -1, frameRaf = 0, visible = false;
  const onEnter = [
    () => { hintFlip.classList.add("is-try"); demoFlip(); },
    () => { gather.classList.add("is-try"); found.classList.remove("is-shown"); layoutGather(null); later(() => active === 1 && gatherTo("제주"), reduce ? 0 : 450); },
    () => {
      stopFoldDemo();
      fold(false);
      foldDemo.push(later(() => {
        if (active !== 2) return;
        fold(true);
        foldDemo.push(later(() => { if (active === 2) { fold(false); save.classList.add("is-try"); } }, 1800));
      }, reduce ? 0 : 1000));
    },
    () => { regions.classList.add("is-try"); setFill(false); requestAnimationFrame(() => requestAnimationFrame(() => setFill(true))); },
    () => { startGlobe(); loadVideo(); },
    () => {
      timeline?.start();
      if (!shareVideo) return;
      shareYears?.classList.add("is-try");
      // from the start each visit; with reduced motion the poster stays until a year or the video is pressed
      loadVideo().then(() => {
        if (active !== 5) return;
        if (shareVideo.readyState >= 1) shareVideo.currentTime = 0;
        if (!reduce) shareVideo.play().catch(() => {});
      });
    },
  ];
  // i = -1 while the section is off screen: everything that moves stops, and re-entry starts the scene again
  function setActive(i) {
    if (i === active) return;
    active = i;
    if (i >= 0) screens.forEach((s, k) => s.classList.toggle("is-on", k === i));
    if (i !== 4) stopGlobe();
    if (i !== 5) { timeline?.stop(); shareVideo?.pause?.(); }
    if (i !== 2) stopFoldDemo();
    if (i !== 0) { stopFlipDemo(); rollView.classList.remove("is-held"); }
    if (i < 0) { cancelAnimationFrame(countRaf); return; }
    onEnter[i]?.();
    onScene(i + 1);
  }
  function frame() {
    frameRaf = 0;
    const vh = innerHeight;
    const pb = narrow.matches ? wrap.getBoundingClientRect().bottom : 0;
    const focus = narrow.matches ? pb + (vh - pb) / 2 : vh / 2;
    let best = 0, bestD = Infinity;
    beats.forEach((b) => { const r = b.getBoundingClientRect(); const d = Math.abs(r.top + r.height / 2 - focus); if (d < bestD) { bestD = d; best = +b.dataset.beat; } });
    setActive(best);
    const raw = bridges.length ? (() => { const r = bridges[0].getBoundingClientRect(); return Math.min(1, Math.max(0, (focus - r.top) / r.height)); })() : 0;
    const p = reduce ? (raw > 0.5 ? 1 : 0) : ease(raw);
    // side: -1 = phone on the left (step 1), +1 = on the right (step 2)
    const side = -1 + 2 * p, dip = Math.sin(Math.PI * p);
    copies.forEach((c) => {
      if (!narrow.matches) { c.style.opacity = ""; return; }
      const r = c.getBoundingClientRect();
      c.style.opacity = String(Math.min(1, Math.max(0, Math.min((r.top - pb) / 24, (vh - 8 - r.bottom) / 24))));
    });
    if (narrow.matches) {
      // flat at rest so the screen reads straight on; it turns a little only while it passes between steps
      wrap.style.transform = `translate(-50%,-50%) scale(${1 - 0.1 * dip})`;
      phone.style.transform = dip > 0.001 ? `rotateY(${8 * Math.sin(2 * Math.PI * p)}deg)` : "";
    } else {
      const span = Math.min(beatsEl.clientWidth, 1120);
      wrap.style.transform = `translate(calc(-50% + ${0.26 * side * span}px),-50%)`;
      phone.style.transform = `rotateY(${-18 * side}deg) rotateZ(${side * 1.5}deg)`;
    }
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
  const resize = new ResizeObserver(() => { layoutGather(gathered); layoutPick(); if (active === 4 && !globeRaf) drawGlobe5(); if (active === 5) timeline?.redraw(); schedule(); });
  resize.observe(root);
  // the header's theme toggle flips data-theme on <html>: redraw canvases with the new tokens
  const theme = new MutationObserver(() => { if (active === 4) drawGlobe5(); if (active === 5) timeline?.redraw(); });
  theme.observe(document.documentElement, { attributes: true, attributeFilter: ["data-theme"] });

  root.classList.add("is-live");
  layoutGather(null);
  layoutPick();

  return () => {
    cleanups.forEach((fn) => fn());
    timers.forEach((id) => clearTimeout(id));
    cancelAnimationFrame(frameRaf);
    cancelAnimationFrame(countRaf);
    stopGlobe();
    timeline?.stop();
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
}
