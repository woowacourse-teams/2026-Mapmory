// #rec (sketch v11): how a trip is kept. Scroll scrubs three beats. 0-.34 inside the phone, three of 제주's photos
// are ticked and saved and only those three rise out; .34-.58 a globe takes over and 제주 fills, the three become a
// marker; .58-1 the globe turns west and every other trip fills in turn. Dragging the globe turns it by hand
// (pointer events only, horizontal; touch-action: pan-y keeps the page scrolling). A port of the sketch's IIFE with
// init/destroy. d3-geo, topojson and the 110m countries load with this lazy chunk, only when #how comes near.
import { geoContains, geoGraticule, geoOrthographic, geoPath, geoRotation } from "d3-geo";
import { feature, merge } from "topojson-client";
import { SPRITE, getSpriteImage } from "./photoStory.js";
import { clamp, drawSpriteTile, easeIO, fitCanvas, font, lerp, onThemeChange, readTokens, roundRect } from "./storyCanvas.js";

// trips in the order the globe meets them as it turns west from Korea
const TRIPS = [
  { id: "410", name: "제주도", label: "제주", ll: [126.55, 33.38] }, { id: "392", name: "일본", label: "일본", ll: [138.5, 36.2] },
  { id: "158", name: "대만", label: "대만", ll: [121, 23.7] }, { id: "704", name: "베트남", label: "베트남", ll: [107.5, 15] },
  { id: "764", name: "태국", label: "태국", ll: [100.9, 15.6] }, { id: "250", name: "프랑스", label: "프랑스", ll: [2.4, 46.6] },
  { id: "724", name: "스페인", label: "스페인", ll: [-3.7, 40.3] }, { id: "840", name: "미국", label: "미국", ll: [-100, 39] },
].map((t) => ({ ...t, k: SPRITE.tiles.findIndex((x) => x.label === t.label), at: Math.max(0.03, (127.5 - t.ll[0]) / (127.5 + 100)) }));
const JEJU = SPRITE.tiles.map((t, k) => (t.label === "제주" ? k : -1)).filter((k) => k >= 0);
// Korea is kept by province: a 제주 trip fills 제주 (clockwise ring, as d3 wants)
const JEJU_SHAPE = { type: "Feature", geometry: { type: "Polygon", coordinates: [[[126.16, 33.32], [126.25, 33.45], [126.5, 33.53], [126.75, 33.56], [126.95, 33.45], [126.85, 33.31], [126.55, 33.22], [126.3, 33.2], [126.16, 33.32]]] } };
const TOKENS = ["text", "canvas", "ps-shadow", "ps-ocean-hi", "ps-ocean-lo", "ps-land", "ps-land-line", "ps-graticule"];

// mountRecStory({ track, canvas }, { mode: "scroll" | "static" }) -> { setProgress(p), setMode(mode), destroy() }
export function mountRecStory({ track, canvas }, { mode: initialMode = "scroll" } = {}) {
  const ctx = canvas.getContext("2d");
  const sprite = getSpriteImage();
  let mode = initialMode, progress = mode === "static" ? 1 : 0;
  let W = 0, H = 0, DPR = 1, phone = false, G = null, feats = null, land = null, grat = null, tok = {};
  let drag = 0, dragV = 0, dragging = null, destroyed = false, raf = 0, onScreen = false;
  const readTok = () => { tok = readTokens(track, TOKENS); };

  import("world-atlas/countries-110m.json").then(({ default: world }) => {
    if (destroyed) return;
    feats = new Map(feature(world, world.objects.countries).features.map((f) => [String(f.id).padStart(3, "0"), f]));
    // a country with far-off parts (France's Guiana, Alaska) fills only the part the trip was in
    for (const t of TRIPS) {
      const f = feats.get(t.id); if (!f || f.geometry.type !== "MultiPolygon") continue;
      const hit = f.geometry.coordinates.find((c) => geoContains({ type: "Polygon", coordinates: c }, t.ll));
      if (hit) feats.set(t.id, { type: "Feature", geometry: { type: "Polygon", coordinates: hit } });
    }
    feats.set("410", JEJU_SHAPE);
    land = merge(world, world.objects.countries.geometries); grat = geoGraticule().step([20, 20])();
    kick();
  }).catch(() => {});

  function layout() {
    ({ W, H, DPR } = fitCanvas(canvas));
    phone = W <= 760;
    G = mode === "static"
      ? { x: W / 2, y: H * 0.44, r: Math.min(W * 0.4, H * 0.32) }
      : phone ? { x: W / 2, y: H * 0.64, r: Math.min(W * 0.42, H * 0.3) } : { x: W * 0.66, y: H * 0.52, r: Math.min(H * 0.38, W * 0.25) };
  }
  const tile = (k, x, y, s, a = 1, r = 4) => { ctx.globalAlpha = a; drawSpriteTile(ctx, sprite, SPRITE, k, x, y, s, r); ctx.globalAlpha = 1; };

  // drag to turn the globe (horizontal only), with a little glide after letting go
  const onDown = (e) => { dragging = { x: e.clientX, d: drag }; dragV = 0; canvas.setPointerCapture?.(e.pointerId); kick(); };
  const onMove = (e) => { if (!dragging) return; const nd = dragging.d + (e.clientX - dragging.x) * 0.35; dragV = nd - drag; drag = nd; kick(); };
  const onUp = () => { dragging = null; kick(); };

  function draw() {
    const p = progress;
    const pP = clamp(p / 0.34), pK = clamp((p - 0.34) / 0.24), pT = clamp((p - 0.58) / 0.4);
    if (!dragging) { drag += dragV; dragV *= 0.92; if (Math.abs(dragV) < 0.01) dragV = 0; }
    ctx.setTransform(DPR, 0, 0, DPR, 0, 0); ctx.globalAlpha = 1; ctx.clearRect(0, 0, W, H);
    const fs = phone ? 12 : 14;
    // the globe (fades in after the phone has gone, never under it)
    const gA = clamp((pP - 0.92) / 0.08);
    const zt = easeIO(clamp(pT / 0.3)), zoom = lerp(7, 1, zt); // opens close on Korea, then pulls out to the globe
    const lam = 127.5 - easeIO(pT) * 227.5 + drag, phi = lerp(35.6, lerp(28, 32, easeIO(pT)), zt);
    if (gA > 0 && feats) {
      const proj = geoOrthographic().scale(G.r * zoom).translate([G.x, G.y]).rotate([-lam, -phi]).clipAngle(90), path = geoPath(proj, ctx);
      ctx.globalAlpha = gA; ctx.shadowColor = tok["ps-shadow"]; ctx.shadowBlur = 40; ctx.shadowOffsetY = 18;
      const og = ctx.createRadialGradient(G.x - G.r * 0.35, G.y - G.r * 0.4, G.r * 0.1, G.x, G.y, G.r); og.addColorStop(0, tok["ps-ocean-hi"]); og.addColorStop(1, tok["ps-ocean-lo"]);
      ctx.beginPath(); ctx.arc(G.x, G.y, G.r, 0, 6.283); ctx.fillStyle = og; ctx.fill(); ctx.shadowColor = "transparent";
      ctx.save(); ctx.beginPath(); ctx.arc(G.x, G.y, G.r, 0, 6.283); ctx.clip(); // close up, the map stays inside the circle
      ctx.beginPath(); path(grat); ctx.strokeStyle = tok["ps-graticule"]; ctx.lineWidth = 1; ctx.stroke();
      ctx.beginPath(); path(land); ctx.fillStyle = tok["ps-land"]; ctx.fill();
      // each trip fills its country: Korea in beat 2, the rest as the globe turns to meet them
      const fills = TRIPS.map((t, i) => (i === 0 ? easeIO(pK) : easeIO(clamp((pT - t.at * 0.92) / 0.08))));
      TRIPS.forEach((t, i) => { if (fills[i] <= 0) return; const f = feats.get(t.id); if (!f) return; ctx.globalAlpha = gA * fills[i]; ctx.beginPath(); path(f); ctx.fillStyle = "#17c985"; ctx.fill(); });
      ctx.globalAlpha = gA; ctx.beginPath(); path(land); ctx.strokeStyle = tok["ps-land-line"]; ctx.lineWidth = 0.7; ctx.stroke();
      if (zt < 1) {
        const [jx, jy] = proj([126.55, 33.0]); ctx.globalAlpha = gA * (1 - zt) * clamp(pK * 3); ctx.fillStyle = tok.text; ctx.font = font(fs);
        ctx.textAlign = "center"; ctx.textBaseline = "top"; ctx.fillText("제주도", jx, jy + 6); ctx.textAlign = "left";
      }
      ctx.restore();
      // markers: a round photo on each filled trip that faces us
      let places = 0, recs = 0;
      const rotate = geoRotation([-lam, -phi]);
      TRIPS.forEach((t, i) => {
        if (fills[i] < 0.5) return;
        places++; recs += i === 0 ? 3 : 1;
        const pt = proj(t.ll), rot = rotate(t.ll);
        const facing = Math.cos(rot[1] * Math.PI / 180) * Math.cos(rot[0] * Math.PI / 180); if (!pt || facing <= 0) return;
        const r = (phone ? 14 : 18) * clamp((fills[i] - 0.5) * 2), x = pt[0], y = pt[1] - r * 2.6; // lifted on a stem so the filled country stays visible
        ctx.globalAlpha = gA * clamp(facing * 3); ctx.beginPath(); ctx.moveTo(pt[0], pt[1]); ctx.lineTo(x, y + r); ctx.strokeStyle = "#0a9d67"; ctx.lineWidth = 1.5; ctx.stroke();
        ctx.save(); ctx.beginPath(); ctx.arc(x, y, r, 0, 6.283); ctx.closePath(); ctx.fillStyle = "#ffffff"; ctx.fill(); ctx.clip(); tile(i === 0 ? JEJU[0] : t.k, x - r + 2, y - r + 2, r * 2 - 4, 1, r); ctx.restore();
        ctx.globalAlpha = gA * clamp(facing * 3); ctx.beginPath(); ctx.arc(x, y, r, 0, 6.283); ctx.strokeStyle = "#17c985"; ctx.lineWidth = 2.5; ctx.stroke();
        if (i === 0) {
          ctx.fillStyle = "#17c985"; ctx.beginPath(); ctx.arc(x + r * 0.8, y - r * 0.8, fs * 0.75, 0, 6.283); ctx.fill();
          ctx.fillStyle = "#062b1d"; ctx.font = font(fs * 0.85); ctx.textAlign = "center"; ctx.textBaseline = "middle"; ctx.fillText("3", x + r * 0.8, y - r * 0.8 + 1); ctx.textAlign = "left";
        }
        ctx.globalAlpha = 1;
      });
      // the running tally under the globe
      if (pK > 0.3) {
        const txt = `다녀온 곳 ${places}곳 · 사진 ${recs}장`; ctx.font = font(fs + 1); const tw = ctx.measureText(txt).width + 28, th = fs * 2.4;
        ctx.globalAlpha = clamp((pK - 0.3) * 3); ctx.fillStyle = tok.text; roundRect(ctx, G.x - tw / 2, G.y + G.r + 18, tw, th, th / 2); ctx.fill();
        ctx.fillStyle = tok.canvas; ctx.textAlign = "center"; ctx.textBaseline = "middle"; ctx.fillText(txt, G.x, G.y + G.r + 18 + th / 2 + 1); ctx.textAlign = "left";
        const names = TRIPS.filter((_, i) => fills[i] >= 0.5).map((t) => t.name).join(" · ");
        ctx.font = font(fs - 1); ctx.fillStyle = tok.text; ctx.globalAlpha = clamp((pK - 0.3) * 3) * 0.75; ctx.textAlign = "center"; ctx.fillText(names, G.x, G.y + G.r + 18 + th + fs * 1.2); ctx.textAlign = "left"; ctx.globalAlpha = 1;
      }
    }
    // the phone: 제주's photos, three get ticked, saved; only those three rise toward the globe
    const phA = 1 - clamp((pP - 0.86) / 0.1);
    {
      let h = phone ? Math.min(H * 0.58, 500) : Math.min(H * 0.8, 640), w = h * 0.485; if (phone && w > W * 0.66) { w = W * 0.66; h = w / 0.485; }
      const cx = phone ? W / 2 : W * 0.66, top = phone ? H - h - 28 : (H - h) / 2, bez = w * 0.035, sx = cx - w / 2 + bez, sy = top + bez, sw = w - bez * 2, sh = h - bez * 2, rad = w * 0.14;
      ctx.globalAlpha = phA; ctx.shadowColor = tok["ps-shadow"]; ctx.shadowBlur = 50; ctx.shadowOffsetY = 26; ctx.fillStyle = "#121619"; roundRect(ctx, cx - w / 2, top, w, h, rad); ctx.fill(); ctx.shadowColor = "transparent";
      ctx.fillStyle = "#f7f9f7"; roundRect(ctx, sx, sy, sw, sh, rad - bez); ctx.fill();
      ctx.save(); roundRect(ctx, sx, sy, sw, sh, rad - bez); ctx.clip();
      const f2 = Math.max(9, sw * 0.045), pad = sw * 0.05;
      ctx.fillStyle = "#121619"; roundRect(ctx, cx - sw * 0.16, sy + sh * 0.013, sw * 0.32, sh * 0.047, sh * 0.02); ctx.fill();
      const now2 = new Date(), sby = sy + sh * 0.036; ctx.fillStyle = "#142019"; ctx.font = font(f2); ctx.textBaseline = "middle";
      ctx.fillText(`${now2.getHours()}:${String(now2.getMinutes()).padStart(2, "0")}`, sx + sw * 0.1, sby);
      for (let q = 0; q < 4; q++) ctx.fillRect(sx + sw * 0.7 + q * f2 * 0.32, sby + f2 * 0.3 - f2 * 0.18 * (q + 1), f2 * 0.2, f2 * 0.18 * (q + 1));
      ctx.strokeStyle = "#142019"; ctx.lineWidth = 1; roundRect(ctx, sx + sw * 0.8, sby - f2 * 0.32, f2 * 1.25, f2 * 0.64, 2); ctx.stroke(); ctx.fillRect(sx + sw * 0.8 + f2 * 0.08, sby - f2 * 0.24, f2 * 0.9, f2 * 0.48);
      ctx.fillStyle = "#142019"; ctx.font = font(f2 * 1.5); ctx.textBaseline = "middle"; ctx.fillText("제주", sx + pad, sy + sh * 0.11);
      ctx.font = font(f2 * 0.9); ctx.fillStyle = "#66756b"; ctx.fillText("31장 · 마음에 드는 사진을 골라요", sx + pad, sy + sh * 0.155);
      const cols = 3, gap = 4, cell = (sw - pad * 2 - gap * (cols - 1)) / cols, gy = sy + sh * 0.19;
      const picks = [0, 4, 6], ticked = picks.map((_, j) => clamp((pP - 0.08 - j * 0.12) / 0.08)), saved = clamp((pP - 0.52) / 0.06), rise = easeIO(clamp((pP - 0.68) / 0.24)), riseA = 1 - clamp((pK - 0.15) / 0.3);
      for (let i = 0; i < 9; i++) {
        const k = JEJU[i % JEJU.length], x = sx + pad + (i % cols) * (cell + gap), y = gy + Math.floor(i / cols) * (cell + gap), j = picks.indexOf(i);
        if (j >= 0 && rise > 0) continue;
        tile(k, x, y, cell, phA * (saved > 0 && j < 0 ? 0.45 : 1));
        if (i === 8) {
          ctx.globalAlpha = phA * 0.55; ctx.fillStyle = "#142019"; roundRect(ctx, x, y, cell, cell, 4); ctx.fill();
          ctx.globalAlpha = phA; ctx.fillStyle = "#ffffff"; ctx.font = font(f2 * 1.3); ctx.textAlign = "center"; ctx.fillText("+22", x + cell / 2, y + cell / 2); ctx.textAlign = "left";
        }
        if (j >= 0 && ticked[j] > 0) {
          const r = cell * 0.17; ctx.globalAlpha = phA * ticked[j]; ctx.strokeStyle = "#17c985"; ctx.lineWidth = 3; roundRect(ctx, x + 1.5, y + 1.5, cell - 3, cell - 3, 4); ctx.stroke();
          ctx.fillStyle = "#ffffff"; ctx.beginPath(); ctx.arc(x + cell - r * 1.4, y + r * 1.4, r + 2, 0, 6.283); ctx.fill();
          ctx.fillStyle = "#17c985"; ctx.beginPath(); ctx.arc(x + cell - r * 1.4, y + r * 1.4, r, 0, 6.283); ctx.fill();
          ctx.strokeStyle = "#062b1d"; ctx.lineWidth = 2; ctx.beginPath(); ctx.moveTo(x + cell - r * 1.9, y + r * 1.4); ctx.lineTo(x + cell - r * 1.5, y + r * 1.8); ctx.lineTo(x + cell - r * 0.9, y + r * 1.0); ctx.stroke(); ctx.globalAlpha = phA;
        }
      }
      // the save button, then the note that only the picked ones go up
      const bw = sw - pad * 2, bh = f2 * 3, bx = sx + pad, byy = sy + sh - bh - sh * 0.05;
      ctx.globalAlpha = phA; ctx.fillStyle = saved > 0 ? "#0a9d67" : "#17c985"; roundRect(ctx, bx, byy, bw, bh, bh / 2); ctx.fill();
      ctx.fillStyle = "#062b1d"; ctx.font = font(f2 * 1.1); ctx.textAlign = "center";
      ctx.fillText(saved > 0 ? "저장했어요 · 고른 3장만 올라가요" : `${ticked.filter((t) => t >= 1).length}장 골랐어요 · 저장하기`, cx, byy + bh / 2 + 1); ctx.textAlign = "left";
      if (saved > 0) { ctx.globalAlpha = phA * saved; ctx.fillStyle = "#66756b"; ctx.font = font(f2 * 0.85); ctx.textAlign = "center"; ctx.fillText("나머지 28장은 폰에 그대로예요", cx, byy - f2 * 1.1); ctx.textAlign = "left"; }
      ctx.restore(); ctx.globalAlpha = 1;
      // the three picked photos lift out toward where Korea will fill
      if (rise > 0 && riseA > 0) picks.forEach((i, j) => {
        const k = JEJU[i % JEJU.length], x0 = sx + pad + (i % cols) * (cell + gap), y0 = gy + Math.floor(i / cols) * (cell + gap);
        const tx = G.x - cell * 0.6 + j * cell * 0.22, ty = G.y - G.r * 0.5 - cell * 0.4, x = lerp(x0, tx, rise), y = lerp(y0, ty, rise) - Math.sin(rise * Math.PI) * 50, s = lerp(cell, cell * 0.7, rise);
        ctx.globalAlpha = riseA; ctx.save(); ctx.translate(x + s / 2, y + s / 2); ctx.rotate((j - 1) * 0.12 * rise); ctx.shadowColor = tok["ps-shadow"]; ctx.shadowBlur = 14; ctx.fillStyle = "#ffffff"; roundRect(ctx, -s / 2 - 3, -s / 2 - 3, s + 6, s + 6, 5); ctx.fill(); ctx.shadowColor = "transparent"; ctx.restore();
        ctx.save(); ctx.translate(x + s / 2, y + s / 2); ctx.rotate((j - 1) * 0.12 * rise); tile(k, -s / 2, -s / 2, s, riseA, 4); ctx.restore(); ctx.globalAlpha = 1;
      });
    }
  }

  // nothing here moves by itself: draw once per change (scroll progress, a drag and its glide, size, theme, assets)
  const loop = () => {
    raf = 0;
    if (destroyed || !G || !W) return;
    draw();
    if (!dragging && dragV) raf = requestAnimationFrame(loop); // the glide after letting go
  };
  const kick = () => { if (!raf && !destroyed && (onScreen || mode === "static")) raf = requestAnimationFrame(loop); };
  const io = new IntersectionObserver((entries) => { onScreen = entries[entries.length - 1].isIntersecting; kick(); });
  io.observe(track);
  const ro = new ResizeObserver(() => { layout(); kick(); });
  ro.observe(canvas);
  canvas.addEventListener("pointerdown", onDown);
  canvas.addEventListener("pointermove", onMove);
  canvas.addEventListener("pointerup", onUp);
  canvas.addEventListener("pointercancel", onUp);
  const onAsset = () => kick();
  sprite?.addEventListener("load", onAsset);
  const offTheme = onThemeChange(() => { readTok(); kick(); });

  readTok();
  layout();
  kick();

  return {
    setProgress(p) { if (mode === "scroll" && p !== progress) { progress = p; kick(); } },
    setMode(next) {
      if (next === mode) return;
      mode = next;
      progress = mode === "static" ? 1 : progress;
      layout();
      kick();
    },
    destroy() {
      destroyed = true;
      cancelAnimationFrame(raf);
      io.disconnect();
      ro.disconnect();
      offTheme();
      canvas.removeEventListener("pointerdown", onDown);
      canvas.removeEventListener("pointermove", onMove);
      canvas.removeEventListener("pointerup", onUp);
      canvas.removeEventListener("pointercancel", onUp);
      sprite?.removeEventListener("load", onAsset);
    },
  };
}
