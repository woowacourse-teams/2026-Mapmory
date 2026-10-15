// The #how travel replay engine: a port of the approved sketch (replay A v4, 2026-10-15).
// React renders the skeleton once (TravelReplay.jsx); this module builds the photos, tags and album markers inside it,
// draws the globe and the phone's photo library on canvases, and follows the page scroll. It never steers the scroll.
//
// Scroll runs b0 = 0..6. Unit 0 is the find: thousands of tiles in the phone, a location sweep dims the rest, and the
// matching photos lift out into three stacks. Everything after runs on b = b0 - 1. Beat 0 holds the three stacks in the
// phone. Beat 1+i is place i: u 0-.35 photos fly to the place and fan out, .3-.55 the region fills, .45-.62 the fan folds
// into a round album marker, .55-1 the camera travels on. Beat 4 pulls back to show every marker, then the end card.
import { PHOTO_FINDER_LIBRARY_PHOTOS } from "./photoFinderDemo.js";
import { BEAT_OF_UNIT, BEAT_SPAN, REPLAY_LIBRARY_COUNT, REPLAY_PHOTOS_PER_PLACE as PER, REPLAY_PLACES as PLACES, REPLAY_TOTAL, replayPhoto } from "./travelReplay.js";

const clamp = (v, a = 0, b = 1) => Math.max(a, Math.min(b, v));
const ease = (t) => 1 - Math.pow(1 - t, 3);
const easeIO = (t) => (t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2);
const back = (t) => { const c = 1.7; return 1 + (c + 1) * Math.pow(t - 1, 3) + c * Math.pow(t - 1, 2); };
const lerp = (a, b, t) => a + (b - a) * t;
const seg = (u, a, b) => clamp((u - a) / (b - a));
const rand = (i) => { const x = Math.sin(i * 12.9898) * 43758.5453; return x - Math.floor(x); };
const q = (v) => Math.round(v * 400);
const fmt = (v) => v.toLocaleString("ko-KR");
const TOKEN_NAMES = ["--tr-ocean", "--tr-ocean-deep", "--tr-land", "--tr-land-edge", "--tr-grat", "--tr-rim", "--tr-pin", "--tr-fill", "--tr-fill-hi", "--tr-light", "--tr-shade", "--surface"];
const TILE_COLORS = ["#7c9a8a", "#a7b8a0", "#c9b79c", "#8aa1b5", "#d8cbb5", "#6f7f74", "#b39b86", "#9fb0bf", "#e2d7c4", "#55665c", "#c4a98c", "#879a7c", "#b8c4b0", "#94867a"];
// Below this viewport height (landscape phones) or with reduced motion the replay shows its finished frame instead.
export const STATIC_MAX_HEIGHT = 500;

// place-local time u, and the place's sub-beats
const local = (pi, b) => b - (1 + pi);
const flyT = (pi, b) => seg(local(pi, b), 0, 0.35);
const fillIsland = (pi, b) => ease(seg(local(pi, b), 0.28, 0.4));
const fillCountry = (pi, b) => ease(seg(local(pi, b), PLACES[pi].island ? 0.4 : 0.28, 0.55));
const foldT = (pi, b) => seg(local(pi, b), 0.45, 0.62);
const markerT = (pi, b) => seg(local(pi, b), 0.52, 0.66);

// Lazy geography: d3-geo, topojson and both maps arrive as their own chunks once the section comes near.
let geoModules = null;
function loadGeo() {
  geoModules ??= Promise.all([
    import("d3-geo"),
    import("topojson-client"),
    import("world-atlas/countries-110m.json"),
    import("./data/replay-countries-50m-lite.json"),
  ]).then(([d3, topojson, coarse, fine]) => ({ d3, topojson, coarse: coarse.default ?? coarse, fine: fine.default ?? fine }));
  return geoModules;
}

function prepare({ d3, topojson }, topo, crop) {
  let all = topojson.feature(topo, topo.objects.countries).features;
  if (crop) {
    // only Asia-Pacific and North America are ever seen up close
    const near = (f) => { const [[x0], [x1]] = d3.geoBounds(f); return x1 >= 90 || x0 <= -40 || x0 > x1; };
    const keep = new Set(all.filter(near).map((f) => f.id));
    topo = { ...topo, objects: { countries: { ...topo.objects.countries, geometries: topo.objects.countries.geometries.filter((g) => keep.has(g.id)) } } };
    all = all.filter((f) => keep.has(f.id));
  }
  const byId = new Map(all.map((f) => [String(f.id).padStart(3, "0"), f]));
  const out = {
    land: topojson.merge(topo, topo.objects.countries.geometries),
    borders: topojson.mesh(topo, topo.objects.countries, (a, b) => a !== b),
    feats: {},
  };
  for (const pl of PLACES) {
    const country = byId.get(pl.country);
    let island = null;
    if (pl.island && country) {
      const polys = country.geometry.type === "MultiPolygon" ? country.geometry.coordinates : [country.geometry.coordinates];
      const hit = polys.find((c) => d3.geoContains({ type: "Polygon", coordinates: c }, pl.lonlat));
      if (hit) island = { type: "Polygon", coordinates: hit };
    }
    out.feats[pl.key] = { country, island };
  }
  return out;
}

// QA hooks (scripts/how-story-browser-checks.mjs reads the replay only through these, not its styling):
//   root [data-mode]           "scroll" | "static" (= how_story_view.story_mode)
//   [data-tr-track][data-beat] the caption on screen, 0-4 (scene_index - 1)
//   [data-tr-step]             the five steps, shown as a list in static mode
// Mounts the replay inside root and returns destroy(); destroy.sample() re-reads the scroll and reports state now.
// onState({ mode: "scroll" | "static", inView, scene: 1-5 | 0, atRest }) runs whenever any of those change.
export function mountTravelReplay(root, { onState } = {}) {
  const $ = (sel) => root.querySelector(sel);
  const section = $("[data-tr-track]");
  const stage = $("[data-tr-stage]");
  const canvas = $("[data-tr-globe]");
  const ctx = canvas.getContext("2d");
  const phone = $("[data-tr-phone]");
  const mosaic = $("[data-tr-mosaic]");
  const mctx = mosaic.getContext("2d");
  const chipText = $("[data-tr-chip]");
  const beats = [...root.querySelectorAll("[data-tr-beat]")];
  const tally = $("[data-tr-tally]");
  const tDots = [...tally.querySelectorAll("[data-tr-dot]")];
  const tPlaces = $("[data-tr-places]");
  const tPhotos = $("[data-tr-photos]");
  const endcard = $("[data-tr-endcard]");
  const steps = [...root.querySelectorAll("[data-tr-step]")];
  const cardLayer = $("[data-tr-cards]");
  const markerLayer = $("[data-tr-markers]");

  const reduceQuery = window.matchMedia("(prefers-reduced-motion: reduce)");
  let mode = "scroll";
  let destroyed = false;
  let tokens = {};
  const readTokens = () => { const s = getComputedStyle(root); for (const n of TOKEN_NAMES) tokens[n] = s.getPropertyValue(n).trim(); };

  // DOM: 5 photos + a tag per place, one album marker per place. Images get their src once the section is near.
  const cards = [], tags = [], markers = [], lazyImgs = [];
  const img = (name) => { const el = document.createElement("img"); el.alt = ""; el.decoding = "async"; el.dataset.src = replayPhoto(name); lazyImgs.push(el); return el; };
  PLACES.forEach((pl, pi) => {
    for (let i = 0; i < PER; i++) {
      const d = document.createElement("div");
      d.className = "tr-photo";
      d.append(img(`${pl.key}-0${i + 1}`));
      cardLayer.append(d);
      cards.push({ el: d, pi, i, rot: (i - 2) * 6 + (pi - 1) * 2 });
    }
    const t = document.createElement("span");
    t.className = "tr-tag";
    t.innerHTML = `${pl.name} <em>${pl.count}장</em>`;
    cardLayer.append(t);
    tags.push(t);
    const m = document.createElement("div");
    m.className = "tr-marker";
    const ph = document.createElement("div");
    ph.className = "tr-marker-photo";
    ph.append(img(pl.cover));
    m.append(ph);
    m.insertAdjacentHTML("beforeend", `<span class="tr-marker-count">${pl.count}</span><span class="tr-marker-name">${pl.name}</span>`);
    markerLayer.append(m);
    markers.push(m);
  });

  // the phone's library: the hero library's photos as the thousands, each place's photos as the matches
  const tileImgs = [], libraryImgs = [];
  let assetsRequested = false;
  function requestAssets() {
    if (assetsRequested || destroyed) return;
    assetsRequested = true;
    for (const el of lazyImgs) el.src = el.dataset.src;
    for (const pl of PLACES) for (let i = 0; i < PER; i++) tileImgs.push(loadImage(replayPhoto(`${pl.key}-0${i + 1}`)));
    for (const src of PHOTO_FINDER_LIBRARY_PHOTOS) libraryImgs.push(loadImage(src));
    loadGeo().then((mods) => {
      if (destroyed) return;
      geo = mods;
      projection = mods.d3.geoOrthographic().clipAngle(90).precision(0.35);
      path = mods.d3.geoPath(projection, ctx);
      grat = mods.d3.geoGraticule().step([20, 20])();
      lo = prepare(mods, mods.coarse);
      hi = prepare(mods, mods.fine, true);
      lastKey = "";
      prerenderHolds();
      req();
    }).catch(() => { /* the globe stays empty; the photos, captions and end card still tell the story */ });
  }
  function loadImage(src) {
    const im = new Image();
    im.decoding = "async";
    im.onload = () => { if (lib && !lib.ready) req(); else if (current <= 0) req(); };
    im.src = src;
    return im;
  }
  const loaded = (im) => im.complete && im.naturalWidth > 0;

  let W = 0, H = 0, DPR = 1, geo = null, lo = null, hi = null, projection = null, path = null, grat = null;
  const sphere = { type: "Sphere" };
  const narrow = () => W < 640;

  // layout sizes are read once per resize, never inside a frame (reads after writes force layout)
  const dims = { cw: 0, ch: 0, tags: [], marker: 0, secH: 0, vh: 0 };
  let tiles = null, lib = null, lastKey = "", baseKey = "";
  const baseCv = document.createElement("canvas");
  const holdCache = new Map();
  const idleIds = [];
  function size() {
    const r = stage.getBoundingClientRect();
    W = r.width; H = r.height;
    // 1.5 everywhere: the globe stays crisp and the three cached holds stay small on large, dense screens
    DPR = Math.min(1.5, window.devicePixelRatio || 1);
    canvas.width = Math.round(W * DPR); canvas.height = Math.round(H * DPR);
    dims.cw = cards[0].el.offsetWidth; dims.ch = cards[0].el.offsetHeight;
    dims.tags = tags.map((t) => t.offsetWidth);
    dims.marker = markers[0].offsetWidth;
    dims.secH = section.offsetHeight;
    dims.vh = window.innerHeight;
    tiles = null; lastKey = ""; baseKey = "";
    prerenderHolds();
  }

  // camera keys: centre lon/lat, radius as share of min(W,H), vertical centre
  function keys() {
    const n = narrow();
    return [
      { c: [150, 26], k: n ? 0.44 : 0.34, y: n ? 0.55 : 0.6 },
      { c: [126.6, 33.6], k: n ? 2.6 : 2.4, y: n ? 0.55 : 0.58 },
      { c: [137.5, 36.5], k: n ? 1.35 : 1.3, y: n ? 0.55 : 0.58 },
      { c: [-104, 39], k: n ? 0.95 : 0.92, y: n ? 0.55 : 0.58 },
      { c: n ? [178, 34] : [-172, 30], k: n ? 0.45 : 0.41, y: n ? 0.5 : 0.54 },
    ];
  }
  function camera(b) {
    const K = keys();
    const i = clamp(Math.floor(b - 0.55), 0, 3), t = easeIO(seg(b - i, 0.55, 1));
    const a = K[i], z = K[Math.min(4, i + 1)];
    const c = geo.d3.geoInterpolate(a.c, z.c)(t);
    const hop = geo.d3.geoDistance(a.c, z.c);
    const dip = Math.sin(t * Math.PI) * clamp(hop / 1.2) * 0.5; // pull back mid-flight so long hops read as travel
    // log-space zoom so 2.4 -> 1.3 does not lurch
    const k = Math.exp(lerp(Math.log(a.k), Math.log(z.k), t)) * (1 - dip);
    return { c, k, y: lerp(a.y, z.y, t), moving: t > 0 && t < 1 };
  }
  function visibility(ll) {
    const r = projection.rotate();
    const d = geo.d3.geoDistance(ll, [-r[0], -r[1]]);
    return clamp((Math.PI / 2 - 0.08 - d) / 0.15);
  }
  const project = (ll) => (projection ? projection(ll) : null);

  // the base globe (sea, grid, land, borders) changes only with the camera, so it is drawn to its own canvas
  function renderBase(cv, cx, cy, R, map, light) {
    if (cv.width !== canvas.width || cv.height !== canvas.height) { cv.width = canvas.width; cv.height = canvas.height; }
    const bctx = cv.getContext("2d"), bpath = geo.d3.geoPath(projection, bctx);
    bctx.setTransform(DPR, 0, 0, DPR, 0, 0);
    bctx.clearRect(0, 0, W, H);
    const g = bctx.createRadialGradient(cx, cy, R * 0.92, cx, cy, R * 1.2);
    g.addColorStop(0, tokens["--tr-rim"]); g.addColorStop(1, "rgba(0,0,0,0)");
    bctx.fillStyle = g; bctx.beginPath(); bctx.arc(cx, cy, R * 1.2, 0, Math.PI * 2); bctx.fill();
    const og = bctx.createRadialGradient(cx - R * 0.35, cy - R * 0.4, R * 0.1, cx, cy, R);
    og.addColorStop(0, tokens["--tr-ocean"]); og.addColorStop(1, tokens["--tr-ocean-deep"]);
    bctx.beginPath(); bpath(sphere); bctx.fillStyle = og; bctx.fill();
    bctx.save(); bctx.beginPath(); bpath(sphere); bctx.clip();
    bctx.beginPath(); bpath(grat); bctx.lineWidth = 1; bctx.strokeStyle = tokens["--tr-grat"]; bctx.stroke();
    bctx.beginPath(); bpath(map.land); bctx.fillStyle = tokens["--tr-land"]; bctx.fill();
    if (!light) { bctx.beginPath(); bpath(map.borders); bctx.lineWidth = 0.7; bctx.strokeStyle = tokens["--tr-land-edge"]; bctx.stroke(); }
    bctx.restore();
  }
  // the three close-up holds use the fine map; draw them ahead in idle time so arriving never stalls
  function prerenderHolds() {
    holdCache.clear();
    while (idleIds.length) cancelIdle(idleIds.pop());
    if (!hi || !W || mode !== "scroll") return;
    const K = keys();
    [1, 2, 3].forEach((i, n) => idleIds.push(requestIdle(() => {
      if (destroyed) return;
      const k = K[i], R = Math.min(W, H) * k.k, cx = W / 2, cy = H * k.y;
      if (R <= Math.min(W, H) * 0.62) return;
      const keep = [projection.rotate(), projection.scale(), projection.translate(), projection.precision()];
      projection.rotate([-k.c[0], -k.c[1]]).scale(R).translate([cx, cy]).precision(0.35);
      const cv = document.createElement("canvas");
      renderBase(cv, cx, cy, R, hi);
      holdCache.set([q(k.c[0]), q(k.c[1]), q(k.k), q(k.y)].join() + "h", cv);
      projection.rotate(keep[0]).scale(keep[1]).translate(keep[2]).precision(keep[3]);
    }, 1500 + n * 300)));
  }

  function draw(b) {
    if (!lo) return;
    const cam = camera(b);
    const R = Math.min(W, H) * cam.k;
    const cx = W / 2, cy = H * cam.y;
    projection.rotate([-cam.c[0], -cam.c[1]]).scale(R).translate([cx, cy]).precision(cam.moving ? 0.9 : 0.35);
    // skip the redraw while the camera holds and nothing on the globe changes
    const key = [q(cam.c[0]), q(cam.c[1]), q(cam.k), q(cam.y), ...PLACES.flatMap((_, pi) => [q(fillCountry(pi, b)), q(fillIsland(pi, b)), q(markerT(pi, b)), q(seg(local(pi, b), 0.28, 0.7))])].join();
    if (key === lastKey) return;
    lastKey = key;
    // fine coastlines only when the camera is still and close; the coarse map is plenty in motion
    const map = !cam.moving && R > Math.min(W, H) * 0.62 ? hi : lo;
    const camKey = key.split(",", 4).join() + (map === hi ? "h" : "l");
    let base = holdCache.get(camKey);
    if (!base) { if (camKey !== baseKey) { baseKey = camKey; renderBase(baseCv, cx, cy, R, map, cam.moving); } base = baseCv; }
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    ctx.drawImage(base, 0, 0);
    ctx.setTransform(DPR, 0, 0, DPR, 0, 0);
    ctx.save(); ctx.beginPath(); path(sphere); ctx.clip();
    // visited regions
    PLACES.forEach((pl, pi) => {
      const fc = fillCountry(pi, b), fi = pl.island ? fillIsland(pi, b) : 0;
      const set = map.feats[pl.key];
      if (fc > 0 && set.country) { ctx.globalAlpha = fc; ctx.beginPath(); path(set.country); ctx.fillStyle = tokens["--tr-fill"]; ctx.fill(); }
      if (fi > 0 && set.island) { ctx.globalAlpha = fi; ctx.beginPath(); path(set.island); ctx.fillStyle = tokens["--tr-fill-hi"]; ctx.fill(); }
      ctx.globalAlpha = 1;
    });
    // ripple when a place fills
    PLACES.forEach((pl, pi) => {
      const r = seg(local(pi, b), 0.28, 0.7);
      if (r <= 0 || r >= 1) return;
      const p = projection(pl.lonlat); if (!p) return;
      ctx.beginPath(); ctx.arc(p[0], p[1], 8 + ease(r) * Math.min(W, H) * 0.12, 0, Math.PI * 2);
      ctx.globalAlpha = (1 - r) * 0.7 * visibility(pl.lonlat); ctx.lineWidth = 2; ctx.strokeStyle = tokens["--tr-pin"]; ctx.stroke();
      ctx.globalAlpha = 1;
    });
    // light and shade so the sphere reads as round
    const lg = ctx.createRadialGradient(cx - R * 0.45, cy - R * 0.5, 0, cx - R * 0.2, cy - R * 0.2, R * 1.25);
    lg.addColorStop(0, tokens["--tr-light"]); lg.addColorStop(0.55, "rgba(255,255,255,0)"); lg.addColorStop(1, tokens["--tr-shade"]);
    ctx.fillStyle = lg; ctx.fillRect(cx - R, cy - R, R * 2, R * 2);
    ctx.restore();
    // callout stems from pin to marker, and pins
    PLACES.forEach((pl, pi) => {
      const v = visibility(pl.lonlat), f = fillCountry(pi, b);
      const p = projection(pl.lonlat);
      if (!p || v <= 0 || f <= 0) return;
      const m = markerPos(pi, p), mt = ease(markerT(pi, b));
      ctx.globalAlpha = v * mt;
      ctx.beginPath(); ctx.moveTo(p[0], p[1]); ctx.lineTo(lerp(p[0], m[0], mt), lerp(p[1], m[1], mt));
      ctx.lineWidth = 1.5; ctx.strokeStyle = tokens["--tr-pin"]; ctx.stroke();
      ctx.globalAlpha = v * f;
      ctx.beginPath(); ctx.arc(p[0], p[1], 4.5, 0, Math.PI * 2); ctx.fillStyle = tokens["--tr-pin"]; ctx.fill();
      ctx.lineWidth = 2; ctx.strokeStyle = tokens["--surface"]; ctx.stroke();
      ctx.globalAlpha = 1;
    });
  }

  // marker sits off the pin along its lean, so Jeju and Japan never collide
  function markerPos(pi, p) {
    const n = narrow(), d = n ? 40 : 52;
    const [lx, ly] = (n && PLACES[pi].leanN) || PLACES[pi].lean, len = Math.hypot(lx, ly);
    return [p[0] + lx / len * d, p[1] + ly / len * d];
  }
  function stackHome(pi) {
    const n = narrow();
    const span = Math.min(W * (n ? 0.86 : 0.62), 620);
    return [W / 2 + (pi - 1) * span / (n ? 2.9 : 2.6), H * (n ? 0.7 : 0.78)];
  }

  function layoutTiles(w, h) {
    const n = narrow(), ts = n ? 13 : 17, gap = n ? 3 : 4, pad = n ? 16 : 22;
    const cols = Math.floor((w - pad * 2 + gap) / (ts + gap)), rows = Math.floor((h - pad * 2 + gap) / (ts + gap));
    const ox = (w - (cols * (ts + gap) - gap)) / 2, oy = (h - (rows * (ts + gap) - gap)) / 2;
    // one matching tile per card, spread across the grid
    const hits = cards.map((_, k) => Math.floor(rand(k + 1) * rows) * cols + Math.floor((k * 7 + 3) % cols));
    return { w, h, cols, rows, ts, gap, ox, oy, hits };
  }
  function drawSquare(target, im, x, y, ts) {
    const side = Math.min(im.naturalWidth, im.naturalHeight);
    target.drawImage(im, (im.naturalWidth - side) / 2, (im.naturalHeight - side) / 2, side, side, x, y, ts, ts);
  }
  // the library is drawn once to two offscreen canvases (bright, and dimmed once the sweep has read it)
  function buildLibrary() {
    const { w, h, cols, rows, ts, gap, ox, oy, hits } = tiles;
    const hitSet = new Set(hits);
    const ready = libraryImgs.length > 0 && libraryImgs.every(loaded);
    const make = (alpha) => {
      const c = document.createElement("canvas");
      c.width = Math.round(w * DPR); c.height = Math.round(h * DPR);
      const x2 = c.getContext("2d"); x2.setTransform(DPR, 0, 0, DPR, 0, 0); x2.globalAlpha = alpha;
      for (let r = 0; r < rows; r++) for (let col = 0; col < cols; col++) {
        const i = r * cols + col; if (hitSet.has(i)) continue;
        const x = ox + col * (ts + gap), y = oy + r * (ts + gap);
        if (ready) drawSquare(x2, libraryImgs[Math.floor(rand(i * 7.7) * libraryImgs.length)], x, y, ts);
        else { x2.fillStyle = TILE_COLORS[Math.floor(rand(i) * TILE_COLORS.length)]; x2.fillRect(x, y, ts, ts); }
      }
      return c;
    };
    lib = { full: make(0.9), dim: make(0.1), ready, w, h, dpr: DPR };
  }
  function drawMosaic(b0) {
    const { w, h, cols, rows, ts, gap, ox, oy, hits } = tiles;
    if (!lib || lib.w !== w || lib.h !== h || lib.dpr !== DPR || (!lib.ready && libraryImgs.length && libraryImgs.every(loaded))) buildLibrary();
    if (mosaic.width !== Math.round(w * DPR) || mosaic.height !== Math.round(h * DPR)) { mosaic.width = Math.round(w * DPR); mosaic.height = Math.round(h * DPR); }
    mctx.setTransform(1, 0, 0, 1, 0, 0);
    mctx.clearRect(0, 0, mosaic.width, mosaic.height);
    const sweep = seg(b0, 0.1, 0.36); // location sweep, left to right
    const sxPx = Math.round((ox + sweep * (cols * (ts + gap))) * DPR);
    mctx.globalAlpha = seg(b0, 0, 0.1);
    if (sxPx > 0) mctx.drawImage(lib.dim, 0, 0, sxPx, mosaic.height, 0, 0, sxPx, mosaic.height);
    if (sxPx < mosaic.width) mctx.drawImage(lib.full, sxPx, 0, mosaic.width - sxPx, mosaic.height, sxPx, 0, mosaic.width - sxPx, mosaic.height);
    mctx.setTransform(DPR, 0, 0, DPR, 0, 0);
    for (let k = 0; k < hits.length; k++) {
      const i = hits[k], c = i % cols, r = Math.floor(i / cols);
      const x = ox + c * (ts + gap), y = oy + r * (ts + gap);
      const passed = seg(sweep * (cols + 6) - c, 0, 6);
      // a matching photo: ringed in mint once the sweep has read its place; the card takes over from .4
      const a = seg(b0, 0, 0.1) * (1 - seg(b0, 0.4, 0.46));
      if (passed > 0.3) { mctx.globalAlpha = a; mctx.fillStyle = tokens["--tr-fill"]; mctx.fillRect(x - 2.5, y - 2.5, ts + 5, ts + 5); }
      mctx.globalAlpha = a;
      const im = tileImgs[k];
      if (im && loaded(im)) drawSquare(mctx, im, x, y, ts);
      else { mctx.fillStyle = TILE_COLORS[i % TILE_COLORS.length]; mctx.fillRect(x, y, ts, ts); }
    }
    // the sweep line itself
    if (sweep > 0 && sweep < 1) {
      const sx = ox + sweep * (cols * (ts + gap));
      const g = mctx.createLinearGradient(sx - 40, 0, sx, 0);
      g.addColorStop(0, "rgba(23,201,133,0)"); g.addColorStop(1, "rgba(23,201,133,.35)");
      mctx.globalAlpha = 1; mctx.fillStyle = g; mctx.fillRect(sx - 40, oy - 4, 40, rows * (ts + gap) + 4);
      mctx.fillStyle = tokens["--tr-fill"]; mctx.fillRect(sx - 1, oy - 4, 2, rows * (ts + gap) + 4);
    }
    mctx.globalAlpha = 1;
  }
  function tileCenter(k, left, top) {
    const { cols, ts, gap, ox, oy, hits } = tiles, i = hits[k];
    return [left + ox + (i % cols) * (ts + gap) + ts / 2, top + oy + Math.floor(i / cols) * (ts + gap) + ts / 2];
  }

  let phoneBox = [0, 0], lastChip = "", lastSafe = null;
  function place(b, b0) {
    const n = narrow();
    // the phone wraps the three stacks in beat 0, then lets them go
    {
      const a = stackHome(0), z = stackHome(2), cw = dims.cw, ch = dims.ch;
      const padX = n ? 16 : 46, padY = n ? 30 : 40;
      const left = a[0] - cw / 2 - padX, right = z[0] + cw / 2 + padX;
      const top = a[1] - ch / 2 - padY - 10, bottom = a[1] + ch / 2 + (n ? 64 : 88);
      // during the find the phone is taller, holding the whole library; it settles around the stacks
      const pw = right - left, ph = bottom - top, extraMax = H * (n ? 0.22 : 0.26);
      const extra = extraMax * (1 - easeIO(seg(b0, 0.8, 1)));
      phone.style.width = `${pw}px`; phone.style.height = `${ph + extra}px`;
      if (!tiles || tiles.w !== pw || tiles.h !== ph + extraMax) {
        tiles = layoutTiles(pw, ph + extraMax);
        mosaic.style.width = `${pw}px`; mosaic.style.height = `${ph + extraMax}px`;
      }
      const out = seg(b, 0.72, 1.02);
      phone.style.opacity = ease(seg(b0, 0, 0.1)) * (1 - out);
      phone.style.transform = `translate(${left}px, ${top - extra}px) scale(${lerp(1, 1.06, out)})`;
      mosaic.style.opacity = 1 - seg(b0, 0.78, 0.98);
      if (b0 < 1.05) drawMosaic(b0);
      // counter: the whole library shrinks to the photos that have a place
      const safe = b0 >= 1.05 && b0 < 1.75;
      const chip = b0 < 0.12 ? `내 폰 안 · 사진 ${fmt(REPLAY_LIBRARY_COUNT)}장`
        : safe ? "내 폰 안 · 서버로 보낸 사진 0장"
          : `내 폰 안 · ${fmt(REPLAY_LIBRARY_COUNT)}장 중 ${fmt(Math.round(lerp(REPLAY_LIBRARY_COUNT, REPLAY_TOTAL, ease(seg(b0, 0.12, 0.5)))))}장`;
      if (chip !== lastChip) { chipText.textContent = chip; lastChip = chip; }
      if (safe !== lastSafe) { phone.classList.toggle("is-safe", safe); lastSafe = safe; }
      phoneBox = [left, top - extraMax];
    }
    canvas.style.opacity = b0 < 1 ? lerp(0.12, 0.35, ease(seg(b0, 0.7, 1))) : lerp(0.35, 1, ease(seg(b, 0.6, 1.1)));
    cards.forEach((c) => {
      const pl = PLACES[c.pi];
      const t = ease(seg(flyT(c.pi, b) - c.i * 0.05, 0, 0.8));
      const fold = easeIO(foldT(c.pi, b));
      const [hx, hy] = stackHome(c.pi);
      let sx = hx + (c.i - 2) * (n ? 5 : 8), sy = hy - c.i * 3;
      const k = c.pi * PER + c.i;
      // find: each card starts as its matching tile and lifts into its place's stack
      const lift = easeIO(seg(b0, 0.42 + c.pi * 0.06 + c.i * 0.02, 0.78 + c.pi * 0.04 + c.i * 0.02));
      const appear = seg(b0, 0.38, 0.44);
      let s0 = 1;
      if (lift < 1) {
        const [tx, ty] = tileCenter(k, phoneBox[0], phoneBox[1]);
        sx = lerp(tx, sx, lift); sy = lerp(ty, sy, lift) - Math.sin(lift * Math.PI) * (n ? 24 : 40);
        s0 = lerp(tiles.ts / dims.cw * 1.4, 1, lift);
      }
      const p = project(pl.lonlat) || [W / 2, H / 2];
      const m = markerPos(c.pi, p);
      // fan above the pin: an arc of five, large enough to read the photos
      const R = n ? 78 : 150;
      const ang = (-90 + (c.i - 2) * (n ? 30 : 24)) * Math.PI / 180;
      const fx = p[0] + Math.cos(ang) * R * (n ? 1.15 : 1.25), fy = p[1] - (n ? 30 : 40) + Math.sin(ang) * R * 0.7;
      let x = lerp(sx, fx, t), y = lerp(sy, fy, t) - Math.sin(t * Math.PI) * (n ? 50 : 90);
      x = lerp(x, m[0], fold); y = lerp(y, m[1], fold);
      const s = lerp(lerp(1, n ? 0.95 : 1.05, t), 0.2, fold) * s0;
      const rot = lerp(lerp(c.rot * lift, (c.i - 2) * 7, t), 0, fold);
      c.el.style.opacity = appear * (1 - seg(fold, 0.7, 1));
      c.el.style.transform = `translate(${x - dims.cw / 2}px, ${y - dims.ch / 2}px) rotate(${rot}deg) scale(${s})`;
      c.el.style.zIndex = t > 0 && fold < 1 ? 20 + c.i : 5 - c.pi;
    });
    tags.forEach((tg, pi) => {
      const [hx, hy] = stackHome(pi);
      const appear = ease(seg(b0, 0.8 + pi * 0.04, 0.95 + pi * 0.04));
      tg.style.opacity = appear * (1 - seg(flyT(pi, b), 0, 0.25));
      tg.style.transform = `translate(${hx - dims.tags[pi] / 2}px, ${hy + (n ? 44 : 64)}px)`;
    });
    markers.forEach((mk, pi) => {
      const p = project(PLACES[pi].lonlat);
      const mt = markerT(pi, b);
      if (!p || mt <= 0) { mk.style.opacity = 0; return; }
      const m = markerPos(pi, p);
      const s = back(mt) * (b > 4 ? lerp(1, 0.9, seg(b, 4, 4.6)) : 1);
      mk.style.opacity = clamp(mt * 3) * visibility(PLACES[pi].lonlat);
      mk.style.transform = `translate(${m[0] - dims.marker / 2}px, ${m[1] - dims.marker / 2}px) scale(${s})`;
    });
  }

  let lastTally = "";
  function setTally(b) {
    let photos = 0, places = 0;
    const on = PLACES.map((pl, pi) => {
      const t = seg(local(pi, b), 0.25, 0.6);
      photos += Math.round(pl.count * ease(t));
      if (t > 0.05) places += 1;
      return t > 0.05;
    });
    const off = b < 0.85 || b > 4.3;
    const key = `${photos}|${places}|${on.join()}|${off}`;
    if (key === lastTally) return;
    lastTally = key;
    on.forEach((v, pi) => tDots[pi].classList.toggle("is-on", v));
    tPlaces.textContent = places;
    tPhotos.textContent = photos;
    tally.classList.toggle("is-off", off);
  }

  let current = -1;
  function setBeat(i) {
    if (i === current) return;
    current = i;
    section.dataset.beat = String(i);
    beats.forEach((el, k) => el.classList.toggle("is-active", k === i));
  }
  let endOn = null;
  function setEnd(on) {
    if (on === endOn) return;
    endOn = on;
    endcard.classList.toggle("is-on", on);
    endcard.inert = !on; // its store links are reachable only while it is on screen
  }

  // state reported to the analytics wiring
  let reported = "";
  function report(next) {
    const key = `${next.mode}|${next.inView}|${next.scene}|${next.atRest}`;
    if (key === reported) return;
    reported = key;
    onState?.(next);
  }

  function progress(r) {
    return clamp(-r.top / Math.max(1, dims.secH - H)) * BEAT_SPAN;
  }
  let raf = 0;
  function frame() {
    raf = 0;
    if (destroyed || !W) return;
    if (mode === "static") { staticFrame(); return; }
    const r = section.getBoundingClientRect();
    const b0 = Math.min(BEAT_SPAN - 0.001, progress(r)), b = Math.max(0, b0 - 1);
    draw(b); place(b, b0); setTally(b);
    const beat = BEAT_OF_UNIT[Math.min(BEAT_OF_UNIT.length - 1, Math.floor(b0 + 0.1))];
    setBeat(beat);
    setEnd(b > 4.35);
    const vh = dims.vh;
    const visible = Math.min(r.bottom, vh) - Math.max(r.top, 0);
    report({ mode, inView: visible >= vh / 2, scene: beat + 1, atRest: r.top <= 1 && r.bottom >= vh - 1 });
  }
  // static: the finished frame, with the five steps as a plain list above it
  function staticFrame() {
    // read every rect before writing any style, so a scroll never forces a layout
    const vh = window.innerHeight;
    const rr = root.getBoundingClientRect();
    const visible = Math.min(rr.bottom, vh) - Math.max(rr.top, 0);
    // of the steps at least half in view, the one nearest the middle of the screen
    let scene = 0, best = Infinity;
    steps.forEach((el, k) => {
      const s = el.getBoundingClientRect();
      const seen = Math.min(s.bottom, vh) - Math.max(s.top, 0);
      const off = Math.abs((s.top + s.bottom) / 2 - vh / 2);
      if (s.height > 0 && seen >= s.height / 2 && off < best) { best = off; scene = k + 1; }
    });
    const b0 = BEAT_SPAN - 0.001, b = b0 - 1;
    draw(b); place(b, b0); setTally(b); setBeat(BEAT_OF_UNIT.at(-1)); setEnd(true);
    report({ mode, inView: visible >= Math.min(vh, rr.height) / 2, scene, atRest: scene > 0 });
  }
  const req = () => { if (!raf && !destroyed) raf = requestAnimationFrame(frame); };

  function decideMode() {
    const next = reduceQuery.matches || window.innerHeight < STATIC_MAX_HEIGHT ? "static" : "scroll";
    if (next === mode && root.dataset.mode) return false;
    mode = next;
    root.dataset.mode = mode;
    root.classList.toggle("is-static", mode === "static");
    return true;
  }

  let near = false;
  const io = new IntersectionObserver(([e]) => {
    near = e.isIntersecting;
    if (near) { requestAssets(); req(); } else report({ mode, inView: false, scene: 0, atRest: false });
  }, { rootMargin: "100% 0px" });
  io.observe(root);
  const onScroll = () => { if (near) req(); };
  let lastW = 0, lastH = 0;
  const onResize = () => {
    const changed = decideMode();
    const r = stage.getBoundingClientRect();
    if (!changed && r.width === lastW && r.height === lastH) {
      // only the window height moved (a phone's address bar): the 100svh stage kept its size, so keep every canvas
      if (window.innerHeight !== dims.vh) { dims.vh = window.innerHeight; req(); }
      return;
    }
    lastW = r.width; lastH = r.height;
    size(); req();
  };
  const retheme = () => { readTokens(); lastKey = ""; baseKey = ""; lib = null; prerenderHolds(); req(); };
  const themeObserver = new MutationObserver(retheme);
  window.addEventListener("scroll", onScroll, { passive: true });
  window.addEventListener("resize", onResize);
  reduceQuery.addEventListener("change", onResize);
  themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ["data-theme"] });
  document.fonts?.ready.then(() => { if (!destroyed) { size(); req(); } });

  decideMode();
  readTokens();
  setEnd(false);
  size();
  lastW = W; lastH = H;
  req();

  const destroy = () => {
    destroyed = true;
    cancelAnimationFrame(raf);
    while (idleIds.length) cancelIdle(idleIds.pop());
    io.disconnect();
    themeObserver.disconnect();
    window.removeEventListener("scroll", onScroll);
    window.removeEventListener("resize", onResize);
    reduceQuery.removeEventListener("change", onResize);
    cardLayer.replaceChildren();
    markerLayer.replaceChildren();
    delete root.dataset.mode;
    root.classList.remove("is-static");
  };
  // re-read where the reader is now (the analytics confirm calls this on a fresh frame)
  destroy.sample = () => { if (!destroyed && W) { cancelAnimationFrame(raf); frame(); } };
  return destroy;
}

function requestIdle(fn, timeout) {
  return typeof window.requestIdleCallback === "function"
    ? { idle: window.requestIdleCallback(fn, { timeout }) }
    : { timer: setTimeout(fn, timeout) }; // Safari has no requestIdleCallback: keep the holds spread out
}
function cancelIdle(id) {
  if (id.idle !== undefined) window.cancelIdleCallback(id.idle);
  else clearTimeout(id.timer);
}
