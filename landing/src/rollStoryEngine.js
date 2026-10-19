// #roll (sketch v11): the phone answers "would it work on my roll?". Scroll scrubs three beats inside the phone.
// 0-.3 the wall's photos pour into the camera roll; .3-.66 the roll comes apart into dots that settle where each
// photo was taken (a timer shows the sort finishing), the globe turns once; .66-1 a food photo goes to 제주 like any
// other, a screenshot with no location steps aside. Nothing sweeps across the roll: each photo already carries its place.
// A port of the sketch's IIFE with init/destroy. It follows the page scroll (progress comes from PhotoStory) and the
// pointer sways the phone; it never steers the scroll. A lazy chunk: loaded only when #how comes near.
import { FOOD_URL, SPRITE, getSpriteImage } from "./photoStory.js";
import { clamp, drawSpriteTile, easeIO, fitCanvas, font, lerp, onThemeChange, readTokens, roundRect } from "./storyCanvas.js";

const LIBRARY = 21474, SORT_SECONDS = 21, COLS = 5, VIS_ROWS = 9, RAD = Math.PI / 180;
const PLACE = {
  "제주": [[126.55, 33.38]], "서울": [[126.98, 37.56]], "부산": [[129.07, 35.18]], "강릉": [[128.9, 37.75]], "경주": [[129.22, 35.84]], "전주": [[127.15, 35.82]],
  "여수": [[127.66, 34.76]], "속초": [[128.59, 38.2]], "일본": [[139.7, 35.7], [135.7, 35.0], [138.7, 35.4]], "대만": [[121.5, 25.0], [121.6, 24.0]],
  "베트남": [[108.3, 15.9], [105.8, 21.0]], "태국": [[100.5, 13.75], [98.98, 18.8]], "프랑스": [[2.35, 48.85], [5.4, 43.3]], "스페인": [[2.17, 41.39], [-5.98, 37.39]], "미국": [[-112.1, 36.1], [-122.4, 37.8], [-74, 40.7]],
};
const WEIGHT = { "서울": 9, "부산": 1.4, "강릉": 1, "경주": 0.8, "전주": 0.7, "여수": 0.6, "속초": 0.9, "제주": 1.6, "일본": 2.4, "대만": 1.2, "베트남": 1.2, "태국": 1, "프랑스": 1.4, "스페인": 1.1, "미국": 1.6 };
const SPREAD = { "서울": 1.1, "일본": 2.6, "미국": 5, "프랑스": 2.4, "스페인": 2.2, "베트남": 2.2, "태국": 2.2, "대만": 1 };
const rnd = (i) => { const x = Math.sin(i * 12.9898 + 78.233) * 43758.5453; return x - Math.floor(x); };

// the camera roll's order: the first screenful shows every place once, then the rest
const ORDER = (() => {
  const vis = COLS * VIS_ROWS, idx = SPRITE.tiles.map((_, k) => k).sort((a, b) => rnd(a * 2.7) - rnd(b * 2.7));
  const seen = new Set(), first = [];
  for (const k of idx) if (!seen.has(SPRITE.tiles[k].label)) { seen.add(SPRITE.tiles[k].label); first.push(k); }
  const head = [...first, ...idx.filter((k) => !first.includes(k))].slice(0, vis).sort((a, b) => rnd(a * 9.1) - rnd(b * 9.1));
  return head.concat(idx.filter((k) => !head.includes(k)));
})();

// screenshots: chat, memo, map capture, receipt — simple shapes so they read as "not a photo" at any size
function drawJunk(ctx, kind, x, y, s) {
  const bar = (bx, by, bw, bh, c) => { ctx.fillStyle = c; ctx.fillRect(x + bx * s, y + by * s, bw * s, bh * s); };
  if (kind === 0) { bar(0, 0, 1, 1, "#b2c7da"); bar(0.1, 0.15, 0.5, 0.14, "#ffffff"); bar(0.4, 0.4, 0.5, 0.14, "#fee500"); bar(0.1, 0.65, 0.4, 0.14, "#ffffff"); }
  else if (kind === 1) { bar(0, 0, 1, 1, "#fff6c9"); for (let q = 0; q < 5; q++) bar(0.12, 0.15 + q * 0.15, q % 2 ? 0.55 : 0.75, 0.06, "#c9b56a"); }
  else if (kind === 2) { bar(0, 0, 1, 1, "#e8efe3"); bar(0, 0.55, 1, 0.08, "#ffffff"); bar(0.45, 0, 0.07, 1, "#ffffff"); bar(0.62, 0.25, 0.14, 0.14, "#e5484d"); bar(0, 0.85, 1, 0.15, "#ffffff"); }
  else { bar(0, 0, 1, 1, "#f2f2f2"); bar(0.15, 0.12, 0.7, 0.08, "#9aa79e"); for (let q = 0; q < 4; q++) bar(0.15, 0.3 + q * 0.12, 0.7, 0.04, "#c9d1cc"); bar(0.15, 0.8, 0.4, 0.08, "#142019"); }
}

// mountRollStory({ track, canvas, copy }, { mode: "scroll" | "static" }) -> { setProgress(p), setMode(mode), destroy() }
export function mountRollStory({ track, canvas, copy }, { mode: initialMode = "scroll" } = {}) {
  const ctx = canvas.getContext("2d");
  const sprite = getSpriteImage();
  const food = new Image();
  food.decoding = "async";
  food.src = FOOD_URL;
  let LAND = [];
  let mode = initialMode, progress = mode === "static" ? 1 : 0;
  let W = 0, H = 0, DPR = 1, phone = false, ph = null, tok = {}, dots = [];
  let destroyed = false, raf = 0, onScreen = false;
  const readTok = () => { tok = readTokens(track, ["ps-shadow"]); };

  const tiles = ORDER.map((k, slot) => ({ k, slot, label: SPRITE.tiles[k].label, d: rnd(slot * 1.7) * 0.55, depth: 0.4 + rnd(slot * 2.9) * 0.9, from: [0, 0], fs: 0, junk: null }));
  const visible = tiles.slice(0, COLS * VIS_ROWS);
  tiles.forEach((t, i) => { if (i % 6 === 4) t.junk = (i / 6 | 0) % 4; }); // every sixth tile is a screenshot, not a trip photo

  function makeDots(n) {
    const labels = Object.keys(WEIGHT), total = labels.reduce((s, l) => s + WEIGHT[l], 0), out = [];
    labels.forEach((l) => {
      const m = Math.round(n * WEIGHT[l] / total), src = visible.filter((t) => t.label === l && t.junk == null), anchors = PLACE[l];
      const spread = SPREAD[l] ?? 0.55;
      for (let j = 0; j < m; j++) {
        const i = out.length, a = anchors[j % anchors.length], r = Math.sqrt(rnd(i * 3.3)) * spread, th = rnd(i * 5.1) * 6.283;
        out.push({ label: l, ll: [a[0] + Math.cos(th) * r * 1.2, a[1] + Math.sin(th) * r], src: src.length ? src[j % src.length] : visible[Math.floor(rnd(i * 7.7) * visible.length)], d: rnd(i * 1.9) * 0.5 });
      }
    });
    return out;
  }

  function layout() {
    ({ W, H, DPR } = fitCanvas(canvas));
    if (!W || !H) return;
    phone = W <= 760;
    const fit = mode === "static";
    let h = fit ? Math.min(H * 0.92, 600) : phone ? Math.min(H * 0.6, 520) : Math.min(H * 0.84, 680), w = h * 0.485;
    if (w > W * (phone ? 0.66 : 0.9)) { w = W * (phone ? 0.66 : 0.9); h = w / 0.485; }
    const cx = fit || phone ? W / 2 : W * 0.66, top = fit ? (H - h) / 2 : phone ? H - h - 28 : (H - h) / 2;
    const bez = w * 0.035, sx = cx - w / 2 + bez, sy = top + bez, sw = w - bez * 2, sh = h - bez * 2;
    const pad = sw * 0.045, gap = Math.max(1.5, sw * 0.008), cell = (sw - pad * 2 - gap * (COLS - 1)) / COLS;
    ph = { cx, top, w, h, bez, sx, sy, sw, sh, r: w * 0.14, pad, gap, cell, status: sh * 0.06, header: sh * 0.1 };
    ph.gR = sw * 0.42; ph.gx = cx; ph.gy = sy + ph.status + ph.header + (sh - ph.status - ph.header) * 0.5;
    // the flying photos start anywhere on the stage except behind the words
    const cr = copy.getBoundingClientRect(), st = canvas.getBoundingClientRect();
    const box = [cr.left - st.left - 30, cr.top - st.top - 30, cr.right - st.left + 30, cr.bottom - st.top + 30];
    tiles.forEach((t, i) => {
      let x, y, k = 0;
      do { x = rnd(i * 5.3 + k * 1.9) * W; y = rnd(i * 8.7 + k * 2.3) * H; k++; } while (k < 20 && x > box[0] - 60 && x < box[2] && y > box[1] - 60 && y < box[3]);
      t.from = [x, y]; t.fs = (phone ? 34 : 62) * (0.75 + rnd(i * 4.1) * 0.5);
    });
    dots = makeDots(phone ? 800 : 1300);
  }

  const gridTop = () => ph.sy + ph.status + ph.header + ph.pad * 0.4;
  const slotXY = (slot) => [ph.sx + ph.pad + (slot % COLS) * (ph.cell + ph.gap), gridTop() + Math.floor(slot / COLS) * (ph.cell + ph.gap)];
  let lam0 = 127.5;
  const phi0 = 30;
  function proj(ll) {
    const l = (ll[0] - lam0) * RAD, f = ll[1] * RAD, f0 = phi0 * RAD;
    const c = Math.sin(f0) * Math.sin(f) + Math.cos(f0) * Math.cos(f) * Math.cos(l);
    return [ph.gx + ph.gR * Math.cos(f) * Math.sin(l), ph.gy - ph.gR * (Math.cos(f0) * Math.sin(f) - Math.sin(f0) * Math.cos(f) * Math.cos(l)), c];
  }
  // the little globe (gradient + ~2,400 land dots) is drawn once per rotation into an offscreen canvas
  let baseCanvas = null, baseKey = "";
  function globeBase() {
    const key = `${lam0.toFixed(1)}|${LAND.length}|${ph.gR}|${DPR}`;
    if (baseCanvas && key === baseKey) return baseCanvas;
    baseKey = key;
    const size = ph.gR * 2 + 4;
    baseCanvas ??= document.createElement("canvas");
    baseCanvas.width = Math.ceil(size * DPR); baseCanvas.height = Math.ceil(size * DPR);
    const b = baseCanvas.getContext("2d");
    b.setTransform(DPR, 0, 0, DPR, 0, 0);
    b.translate(ph.gR + 2 - ph.gx, ph.gR + 2 - ph.gy); // phone coordinates
    const g = b.createRadialGradient(ph.gx - ph.gR * 0.35, ph.gy - ph.gR * 0.4, ph.gR * 0.1, ph.gx, ph.gy, ph.gR);
    g.addColorStop(0, "#f2f7f3"); g.addColorStop(1, "#dde8e1"); b.fillStyle = g; b.beginPath(); b.arc(ph.gx, ph.gy, ph.gR, 0, 6.283); b.fill();
    b.fillStyle = "#b9c9be";
    for (const ll of LAND) { const [x, y, c] = proj(ll); if (c <= 0) continue; b.globalAlpha = 0.25 + c * 0.55; b.fillRect(x - 0.9, y - 0.9, 1.8, 1.8); }
    return baseCanvas;
  }
  function drawTile(t, x, y, s, a, r = 3) {
    if (s <= 0.5) return;
    ctx.globalAlpha = a;
    if (t.junk != null) { ctx.save(); roundRect(ctx, x, y, s, s, r); ctx.clip(); drawJunk(ctx, t.junk, x, y, s); ctx.restore(); }
    else drawSpriteTile(ctx, sprite, SPRITE, t.k, x, y, s, r);
    ctx.globalAlpha = 1;
  }
  const pill = (txt, x, y, fs, bg, fg, a = 1) => {
    ctx.font = font(fs); const tw = ctx.measureText(txt).width + fs * 2.2, th = fs * 2.2;
    ctx.globalAlpha = a; ctx.fillStyle = bg; roundRect(ctx, x - tw / 2, y - th / 2, tw, th, th / 2); ctx.fill();
    ctx.fillStyle = fg; ctx.textAlign = "center"; ctx.textBaseline = "middle"; ctx.fillText(txt, x, y + 1); ctx.textAlign = "left"; ctx.globalAlpha = 1;
    return th;
  };

  const mouse = { x: 0, y: 0, tx: 0, ty: 0, px: -999, py: -999 };
  const onPointerMove = (e) => {
    if (mode === "static") return;
    const r = canvas.getBoundingClientRect();
    mouse.tx = (e.clientX / W - 0.5) * 2; mouse.ty = (e.clientY / H - 0.5) * 2; mouse.px = e.clientX - r.left; mouse.py = e.clientY - r.top;
  };
  const onPointerLeave = () => { mouse.tx = mouse.ty = 0; mouse.px = mouse.py = -999; };

  function draw(now) {
    const p = progress, still = mode === "static";
    const pA = clamp(p / 0.3), pB = clamp((p - 0.3) / 0.22), pS = clamp((p - 0.52) / 0.14), pF = clamp((p - 0.7) / 0.26);
    mouse.x += (mouse.tx - mouse.x) * 0.08; mouse.y += (mouse.ty - mouse.y) * 0.08;
    lam0 = 127.5 + 360 * (pS > 0 ? 1 - easeIO(pS) : 0); // one turn around the world, ending on Korea
    ctx.setTransform(DPR, 0, 0, DPR, 0, 0); ctx.globalAlpha = 1; ctx.clearRect(0, 0, W, H);
    const sway = still ? [0, 0] : [mouse.x * 8, mouse.y * 6 + Math.sin(now / 1600) * 3];
    ctx.save(); ctx.translate(sway[0], sway[1]);
    ctx.shadowColor = tok["ps-shadow"]; ctx.shadowBlur = 50; ctx.shadowOffsetY = 26;
    ctx.fillStyle = "#121619"; roundRect(ctx, ph.cx - ph.w / 2, ph.top, ph.w, ph.h, ph.r); ctx.fill(); ctx.shadowColor = "transparent";
    ctx.strokeStyle = "#3b4349"; ctx.lineWidth = 1.5; roundRect(ctx, ph.cx - ph.w / 2 + 0.75, ph.top + 0.75, ph.w - 1.5, ph.h - 1.5, ph.r); ctx.stroke();
    ctx.fillStyle = "#f7f9f7"; roundRect(ctx, ph.sx, ph.sy, ph.sw, ph.sh, ph.r - ph.bez); ctx.fill();
    ctx.save(); roundRect(ctx, ph.sx, ph.sy, ph.sw, ph.sh, ph.r - ph.bez); ctx.clip();
    const fs = Math.max(9, ph.sw * 0.045), F = (k) => font(fs * k);
    // status bar: time, island, signal, battery
    ctx.fillStyle = "#142019"; ctx.font = F(1); ctx.textBaseline = "middle";
    const d = new Date(); ctx.fillText(`${d.getHours()}:${String(d.getMinutes()).padStart(2, "0")}`, ph.sx + ph.sw * 0.1, ph.sy + ph.status * 0.62);
    ctx.fillStyle = "#121619"; roundRect(ctx, ph.cx - ph.sw * 0.16, ph.sy + ph.status * 0.22, ph.sw * 0.32, ph.status * 0.78, ph.status * 0.4); ctx.fill();
    const by = ph.sy + ph.status * 0.62, bx = ph.sx + ph.sw * 0.9;
    ctx.fillStyle = "#142019"; for (let i = 0; i < 4; i++) ctx.fillRect(bx - ph.sw * 0.2 + i * fs * 0.32, by + fs * 0.3 - fs * 0.18 * (i + 1), fs * 0.2, fs * 0.18 * (i + 1));
    ctx.strokeStyle = "#142019"; ctx.lineWidth = 1; roundRect(ctx, bx - fs * 1.4, by - fs * 0.32, fs * 1.25, fs * 0.64, 2); ctx.stroke(); ctx.fillRect(bx - fs * 1.32, by - fs * 0.24, fs * 0.9, fs * 0.48);
    // header: the roll and a 사진 | 지도 switch that flips to 지도 as the roll comes apart
    const hy = ph.sy + ph.status + ph.header * 0.5, mapOn = pB > 0.35;
    ctx.font = F(1.55); ctx.fillText("사진첩", ph.sx + ph.pad, hy);
    const segW = ph.sw * 0.34, segH = ph.header * 0.5, segX = ph.sx + ph.sw - ph.pad - segW, segY = hy - segH / 2;
    ctx.fillStyle = "#e9efea"; roundRect(ctx, segX, segY, segW, segH, segH / 2); ctx.fill();
    ctx.fillStyle = "#ffffff"; roundRect(ctx, lerp(segX + 2, segX + segW / 2, easeIO(clamp((pB - 0.2) / 0.3))), segY + 2, segW / 2 - 2, segH - 4, (segH - 4) / 2); ctx.fill();
    ctx.font = F(0.85); ctx.textAlign = "center"; ctx.fillStyle = mapOn ? "#9aa79e" : "#142019"; ctx.fillText("사진", segX + segW / 4, hy + 1);
    ctx.fillStyle = mapOn ? "#142019" : "#9aa79e"; ctx.fillText("지도", segX + segW * 0.75, hy + 1); ctx.textAlign = "left";
    // under the title: the count while photos arrive, then the sort timer, then done
    ctx.font = F(0.85); ctx.fillStyle = "#66756b";
    const count = LIBRARY.toLocaleString("ko-KR");
    const line = pB <= 0 ? `${count}장` : pB < 0.9 ? "위치로 나누는 중…" : `${count}장 · ${SORT_SECONDS}초 만에 나눴어요`;
    if (pB >= 0.9) ctx.fillStyle = "#077a4f";
    ctx.fillText(line, ph.sx + ph.pad, ph.sy + ph.status + ph.header * 0.92);
    // the globe fades in as the roll comes apart
    const gA = clamp(pB * 1.6);
    if (gA > 0) {
      ctx.globalAlpha = gA;
      ctx.drawImage(globeBase(), ph.gx - ph.gR - 2, ph.gy - ph.gR - 2, ph.gR * 2 + 4, ph.gR * 2 + 4);
      ctx.globalAlpha = 1;
    }
    for (const t of visible) {
      const ft = easeIO(clamp((pA - t.d) / 0.45)); if (ft < 1) continue;
      const out = easeIO(clamp((pB - t.d * 0.6) / 0.55)); if (out >= 1) continue;
      const [x, y] = slotXY(t.slot), s = ph.cell * (1 - out);
      drawTile(t, x + (ph.cell - s) / 2, y + (ph.cell - s) / 2, s, 1 - out * 0.3);
    }
    if (pB > 0.6) for (const [l, lls] of Object.entries(PLACE)) for (const a of lls) {
      const [x, y, c] = proj(a); if (c <= 0.15) continue;
      const lit = pF > 0 && l === "제주", rad = ph.gR * (lit ? 0.2 : 0.12), g = ctx.createRadialGradient(x, y, 0, x, y, rad);
      g.addColorStop(0, lit ? "rgba(23,201,133,.45)" : "rgba(23,201,133,.18)"); g.addColorStop(1, "rgba(23,201,133,0)");
      ctx.globalAlpha = clamp((pB - 0.6) * 2.5) * c; ctx.fillStyle = g; ctx.beginPath(); ctx.arc(x, y, rad, 0, 6.283); ctx.fill();
    }
    ctx.globalAlpha = 1;
    const near = (x, y) => Math.max(0, 1 - Math.hypot(x - mouse.px + sway[0], y - mouse.py + sway[1]) / 46);
    for (const dt of dots) {
      const go = easeIO(clamp((pB - dt.d * 0.55) / 0.5)); if (go <= 0) continue;
      const [sx0, sy0] = slotXY(dt.src.slot), [tx, ty, c] = proj(dt.ll);
      const x = lerp(sx0 + ph.cell / 2, tx, go), y = lerp(sy0 + ph.cell / 2, ty, go);
      const a = go < 1 ? 1 : (c > 0 ? 0.35 + c * 0.65 : 0); if (a <= 0) continue;
      const sparkle = near(x, y);
      ctx.globalAlpha = Math.min(1, a + sparkle * 0.6); ctx.fillStyle = sparkle > 0.1 ? "#17c985" : "#2f8f68";
      const s = 2.8 + sparkle * 2.6; ctx.fillRect(x - s / 2, y - s / 2, s, s);
    }
    ctx.globalAlpha = 1;
    // once sorted, the clusters say where they are
    if (pB > 0.9) for (const [l, a, dx, dy] of [["한국", "서울", -2.6, -1.4], ["일본", "일본", 2.6, -0.4], ["베트남", "베트남", -2.8, 1.2]]) {
      const [x, y, c] = proj(PLACE[a][0]); if (c <= 0.3) continue;
      pill(l, x + dx * fs, y + dy * fs, fs * 0.78, "#ffffff", "#142019", clamp((pB - 0.9) * 10) * c);
    }
    // food and a screenshot: the food photo carries a place, so it goes home to 제주; the screenshot has none
    if (pF > 0) {
      const sz = (ph.sw - ph.pad * 2 - ph.gap * 8) / 3, top0 = ph.sy + ph.status + ph.header + ph.sh * 0.02, [jx, jy] = proj(PLACE["제주"][0]);
      [0, 1].forEach((i) => {
        const t = easeIO(clamp((pF - 0.2 - i * 0.15) / 0.5)), x0 = ph.sx + ph.pad + i * (sz + ph.gap * 4), y0 = top0;
        const x = lerp(x0, jx - 3, t), y = lerp(y0, jy - 3, t) - Math.sin(t * Math.PI) * 30, s = lerp(sz, 6, t), a = clamp(pF * 5) * (t > 0.92 ? (1 - t) / 0.08 : 1);
        if (a <= 0) return;
        ctx.globalAlpha = a; ctx.save(); roundRect(ctx, x, y, s, s, 4); ctx.clip();
        if (food.complete && food.naturalWidth) ctx.drawImage(food, i * 160, 0, 160, 160, x, y, s, s);
        ctx.restore();
        if (t < 0.4) { ctx.globalAlpha = a * (1 - t / 0.4); pill("📍 제주", x + s / 2, y + s + fs * 1.1, fs * 0.9, "#142019", "#f0fff8"); }
        ctx.globalAlpha = 1;
      });
      // the screenshot: a chat capture with no place; it slides aside into "위치 없음"
      const st = easeIO(clamp((pF - 0.5) / 0.35)), sx = lerp(ph.sx + ph.pad + 2 * (sz + ph.gap * 4), ph.sx + ph.sw - ph.pad - sz * 0.6, st), syy = lerp(top0, ph.sy + ph.sh * 0.78, st), ss = lerp(sz, sz * 0.6, st);
      ctx.globalAlpha = clamp(pF * 5) * (1 - st * 0.45); ctx.fillStyle = "#ffffff"; roundRect(ctx, sx, syy, ss, ss, 4); ctx.fill(); ctx.strokeStyle = "#dbe5de"; ctx.lineWidth = 1; roundRect(ctx, sx, syy, ss, ss, 4); ctx.stroke();
      ctx.fillStyle = "#b2c7da"; roundRect(ctx, sx, syy, ss, ss * 0.2, 4); ctx.fill(); ctx.fillStyle = "#142019"; ctx.font = F(0.62); ctx.textBaseline = "middle"; ctx.fillText("대화방", sx + ss * 0.08, syy + ss * 0.1);
      ctx.fillStyle = "#e9efea"; roundRect(ctx, sx + ss * 0.08, syy + ss * 0.3, ss * 0.55, ss * 0.15, 4); ctx.fill(); ctx.fillStyle = "#fee500"; roundRect(ctx, sx + ss * 0.37, syy + ss * 0.52, ss * 0.55, ss * 0.15, 4); ctx.fill();
      ctx.fillStyle = "#e9efea"; roundRect(ctx, sx + ss * 0.08, syy + ss * 0.74, ss * 0.45, ss * 0.15, 4); ctx.fill();
      pill(st > 0.9 ? "직접 골라 넣기" : "위치 없음", Math.min(sx + ss / 2, ph.sx + ph.sw - ph.pad - fs * 4.4), syy + ss + fs * 1.1, fs * 0.86, "#66756b", "#ffffff", clamp((pF - 0.5) * 6));
      ctx.globalAlpha = 1;
    }
    // the promise, in one line, once the map is up
    const lockA = clamp((pB - 0.4) * 3);
    if (lockA > 0) {
      const txt = "찍은 곳만 읽어요 · 내 폰 안에서"; ctx.font = F(0.8); const tw = ctx.measureText(txt).width + 34, th = fs * 2.1;
      const lx = ph.cx - tw / 2, ly = ph.sy + ph.sh - th - ph.sh * 0.035; ctx.globalAlpha = lockA; ctx.fillStyle = "#142019"; roundRect(ctx, lx, ly, tw, th, th / 2); ctx.fill();
      ctx.fillStyle = "#17c985"; roundRect(ctx, lx + 12, ly + th * 0.42, fs * 0.8, fs * 0.62, 2); ctx.fill(); ctx.strokeStyle = "#17c985"; ctx.lineWidth = 1.4; ctx.beginPath(); ctx.arc(lx + 12 + fs * 0.4, ly + th * 0.42, fs * 0.28, Math.PI, 0); ctx.stroke();
      ctx.fillStyle = "#f0fff8"; ctx.textBaseline = "middle"; ctx.fillText(txt, lx + 26, ly + th / 2 + 1); ctx.globalAlpha = 1;
    }
    ctx.restore(); ctx.restore();
    for (const t of tiles) {
      const ft = easeIO(clamp((pA - t.d) / 0.45)); if (ft >= 1 || pA <= 0) continue;
      const [sx, sy] = slotXY(t.slot), x0 = t.from[0] + mouse.x * 24 * t.depth, y0 = t.from[1] + mouse.y * 16 * t.depth;
      const s = lerp(t.fs, ph.cell, ft), x = lerp(x0, sx + sway[0], ft), y = lerp(y0, Math.min(sy, ph.sy + ph.sh - ph.cell * 0.6) + sway[1], ft) - Math.sin(ft * Math.PI) * 40;
      drawTile(t, x, y, s, lerp(0.55, 1, ft) * (ft > 0.92 && sy > ph.sy + ph.sh ? 1 - (ft - 0.92) / 0.08 : 1), lerp(8, 3, ft));
    }
  }

  // scroll mode: draw every frame while the track is on screen (the phone sways); static: draw once per change
  const loop = (now) => {
    raf = 0;
    if (destroyed || !ph) return;
    draw(now);
    if (mode === "scroll" && onScreen) raf = requestAnimationFrame(loop);
  };
  const kick = () => { if (!raf && !destroyed && (onScreen || mode === "static")) raf = requestAnimationFrame(loop); };
  const io = new IntersectionObserver((entries) => { onScreen = entries[entries.length - 1].isIntersecting; kick(); });
  io.observe(track);
  const ro = new ResizeObserver(() => { layout(); kick(); });
  ro.observe(canvas);
  track.addEventListener("pointermove", onPointerMove);
  track.addEventListener("pointerleave", onPointerLeave);
  const onAsset = () => kick();
  sprite?.addEventListener("load", onAsset);
  food.addEventListener("load", onAsset);
  const offTheme = onThemeChange(() => { readTok(); kick(); });
  // the land dots of the little globe: their own lazy chunk
  import("./data/story-globe-dots.json").then(({ default: dotsData }) => { if (!destroyed) { LAND = dotsData; kick(); } }).catch(() => {});

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
      track.removeEventListener("pointermove", onPointerMove);
      track.removeEventListener("pointerleave", onPointerLeave);
      sprite?.removeEventListener("load", onAsset);
      food.removeEventListener("load", onAsset);
    },
  };
}
