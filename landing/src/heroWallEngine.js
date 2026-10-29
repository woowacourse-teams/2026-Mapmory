// The first screen (sketch v11 "A"): a live wall of photos. The pointer is a lens that shows where each photo was
// taken; a place chip keeps only that place. Every tile is a different photo (no repeats): the wall grows its tiles
// until it fits the photos it has. A port of the sketch's hero IIFE with init/destroy; React owns the chips and text.
// It follows the pointer only (no wheel/touch/key handlers, never preventDefault) and draws only while on screen.
import { SPRITE, getSpriteImage } from "./photoStory.js";
import { clamp, drawSpriteTile, easeOut, fitCanvas, font, onThemeChange, readTokens, roundRect } from "./storyCanvas.js";

const TOKENS = ["canvas", "surface", "text", "accent", "ps-on-accent", "ps-dot", "ps-shadow"];
const rand = (n) => { const x = Math.sin(n * 91.7 + 17.3) * 43758.5453; return x - Math.floor(x); };

// mountHeroWall(hero section, canvas, { reducedMotion, onPick }) -> { setPick(key | null), destroy() }
// onPick(place) fires when a photo with a place is tapped, so a photo works like its chip.
export function mountHeroWall(hero, canvas, { reducedMotion = false, onPick } = {}) {
  const ctx = canvas.getContext("2d");
  const sprite = getSpriteImage();
  let W = 0, H = 0, DPR = 1, tiles = [], tok = {};
  const lens = { x: 0, y: 0, tx: 0, ty: 0, live: false, last: 0 };
  let pick = null, pickAt = 0, visible = true, raf = 0, destroyed = false;
  // where the words are on the canvas (with a margin): a chosen place's photos stay under the clearing there
  const copyEl = hero.querySelector(".wall-hero-copy");
  let words = { x0: 0, y0: 0, x1: 0, y1: 0 };
  const measureWords = () => {
    if (!copyEl) return;
    const c = copyEl.getBoundingClientRect(), r = canvas.getBoundingClientRect(), m = 14;
    words = { x0: c.left - r.left - m, y0: c.top - r.top - m, x1: c.right - r.left + m, y1: c.bottom - r.top + m };
  };

  // a chosen place's photos rise out of the wall into a tidy grid where nothing covers them: under the words, or on
  // both sides of them when the band below is too low (2026-10-29: left in place, most of them sat under the words)
  let slots = new Map();
  function fitGrid(n, r, gap, max) {
    const w = r.x1 - r.x0, h = r.y1 - r.y0;
    let best = { s: 0, c: 1 };
    for (let c = 1; c <= n; c++) {
      const rows = Math.ceil(n / c), s = Math.min((w - gap * (c - 1)) / c, (h - gap * (rows - 1)) / rows);
      if (s > best.s) best = { s, c };
    }
    return { s: Math.min(best.s, max), c: best.c };
  }
  function placeGrid(list, r, gap, max) {
    if (!list.length) return;
    const { s, c } = fitGrid(list.length, r, gap, max), rows = Math.ceil(list.length / c);
    const y0 = r.y0 + (r.y1 - r.y0 - (rows * s + (rows - 1) * gap)) / 2;
    list.forEach((t, i) => {
      const row = Math.floor(i / c), inRow = Math.min(c, list.length - row * c);
      const x0 = r.x0 + (r.x1 - r.x0 - (inRow * s + (inRow - 1) * gap)) / 2;
      slots.set(t, { x: x0 + (i - row * c) * (s + gap), y: y0 + row * (s + gap), s });
    });
  }
  function gather() {
    slots = new Map();
    if (!pick || !W) return;
    const kept = tiles.filter((t) => t.place === pick && t.x + t.s > 0 && t.x < W);
    const small = W < 520, gap = small ? 8 : 12, max = small ? 84 : 112, pad = 16;
    const below = { x0: pad, y0: words.y1 + 4, x1: W - pad, y1: H - (small ? 40 : 52) };
    if (fitGrid(kept.length, below, gap, max).s >= (small ? 52 : 72)) { placeGrid(kept, below, gap, max); return; }
    const top = 96, bottom = H - 52, half = Math.ceil(kept.length / 2);
    placeGrid(kept.slice(0, half), { x0: pad, y0: top, x1: words.x0, y1: bottom }, gap, max);
    placeGrid(kept.slice(half), { x0: words.x1, y0: top, x1: W - pad, y1: bottom }, gap, max);
  }

  const readTok = () => { tok = readTokens(hero, TOKENS); };
  function layout() {
    ({ W, H, DPR } = fitCanvas(canvas));
    const N = SPRITE.tiles.length, small = W < 520;
    let ts = small ? 46 : 80, cols, rows;
    const gap = small ? 5 : 8;
    for (;;) { const step = ts + gap; cols = Math.ceil(W / step) + 1; rows = Math.ceil(H / step) + 1; if (cols * rows <= N) break; ts += 2; }
    const step = ts + gap;
    tiles = [];
    for (let y = 0; y < rows; y++) for (let x = 0; x < cols; x++) {
      const k = tiles.length, d = SPRITE.tiles[k];
      tiles.push({ x: x * step - (y % 2) * step / 2, y: y * step - step / 3, s: ts, k, place: d.place, label: d.label, v: 0, ph: rand(k * 5.5) * 6.28 });
    }
    lens.x = lens.tx = W * 0.72; lens.y = lens.ty = H * 0.7;
  }

  function paint({ t, x, y, s, a, ring }) {
    ctx.globalAlpha = a;
    drawSpriteTile(ctx, sprite, SPRITE, t.k, x, y, s, 8);
    if (ring > 0) { ctx.globalAlpha = ring; ctx.strokeStyle = tok.accent; ctx.lineWidth = 2.5; roundRect(ctx, x - 2, y - 2, s + 4, s + 4, 10); ctx.stroke(); }
  }

  // one frame; returns true while something is still settling (reduced motion keeps drawing only until then)
  function draw(now) {
    ctx.setTransform(DPR, 0, 0, DPR, 0, 0);
    ctx.globalAlpha = 1;
    ctx.clearRect(0, 0, W, H);
    if (!reducedMotion && (!lens.live || now - lens.last > 2600)) { // idle: the lens drifts on its own so phones see it too
      lens.live = false;
      const t = now / 1000;
      lens.tx = W * (0.5 + 0.36 * Math.sin(t * 0.45)); lens.ty = H * (0.5 + 0.34 * Math.sin(t * 0.31 + 1.2));
    }
    const follow = reducedMotion ? 1 : 0.12;
    lens.x += (lens.tx - lens.x) * follow; lens.y += (lens.ty - lens.y) * follow;
    const LR = W < 520 ? 92 : 150, k = pick ? (reducedMotion ? 1 : easeOut(clamp((now - pickAt) / 700))) : 0;
    let hit = null, hitD = 1e9, settling = k < 1 && !!pick;
    const top = [];
    for (const t of tiles) {
      const cx = t.x + t.s / 2, cy = t.y + t.s / 2, d = Math.hypot(cx - lens.x, cy - lens.y);
      const inLens = clamp(1 - d / LR);
      const keep = pick ? (t.place === pick ? 1 : 0) : 1;
      const target = pick ? keep : inLens;
      t.v = reducedMotion ? target : t.v + (target - t.v) * 0.14;
      if (Math.abs(target - t.v) > 0.01) settling = true;
      // a calm wall: photos sit dim; the lens (or a chosen place) brings them up
      const dotK = pick && !keep ? k : 0;
      if (dotK > 0.98) { t.at = null; ctx.globalAlpha = 0.7; ctx.fillStyle = tok["ps-dot"]; ctx.beginPath(); ctx.arc(cx, cy, 2.2, 0, 7); ctx.fill(); continue; }
      const lift = 1 + t.v * (pick ? 0.12 : 0.16);
      const bob = reducedMotion ? 0 : Math.sin(now / 1400 + t.ph) * 1.2;
      let s = t.s * lift * (1 - dotK * 0.9), x = cx - s / 2, y = cy - s / 2 + bob;
      const slot = pick && keep ? slots.get(t) : null;
      if (pick && keep && !slot) { t.at = null; continue; } // off-screen photos of the place stay out
      if (slot) { s += (slot.s - s) * k; x += (slot.x - x) * k; y += (slot.y - y) * k; }
      const item = { t, x, y, s, a: 0.16 + t.v * 0.84 - dotK * 0.1, ring: pick && keep ? k : 0 };
      t.at = { x, y, s };
      if (pick ? keep : t.v > 0.3) top.push(item); else paint(item);
      if (!pick && d < hitD && inLens > 0.55) { hitD = d; hit = item; }
    }
    // a soft clearing behind the words, then the lit photos on top of it
    const g = ctx.createRadialGradient(W / 2, H * 0.47, 0, W / 2, H * 0.47, Math.max(W, H) * 0.42);
    g.addColorStop(0, tok.canvas); g.addColorStop(0.55, `${tok.canvas}e6`); g.addColorStop(1, `${tok.canvas}00`);
    ctx.globalAlpha = 1; ctx.fillStyle = g; ctx.fillRect(0, 0, W, H);
    top.forEach(paint);
    ctx.globalAlpha = 1;
    // the lens: one photo at a time tells where it was taken
    if (hit) {
      const { t, x, y, s } = hit;
      ctx.font = font(W < 520 ? 13 : 15);
      const label = `📍 ${t.label}`, tw = ctx.measureText(label).width + 22, th = W < 520 ? 28 : 32;
      const lx = Math.min(W - tw - 8, Math.max(8, x + s / 2 - tw / 2)), ly = y - th - 8;
      ctx.shadowColor = tok["ps-shadow"]; ctx.shadowBlur = 14; ctx.shadowOffsetY = 4;
      ctx.fillStyle = t.place ? tok.accent : tok.surface; roundRect(ctx, lx, ly, tw, th, th / 2); ctx.fill(); ctx.shadowColor = "transparent";
      ctx.fillStyle = t.place ? tok["ps-on-accent"] : tok.text; ctx.textBaseline = "middle"; ctx.fillText(label, lx + 11, ly + th / 2 + 1);
      ctx.strokeStyle = t.place ? tok.accent : tok.text; ctx.lineWidth = 2; roundRect(ctx, x - 2, y - 2, s + 4, s + 4, 10); ctx.stroke();
    }
    return settling;
  }

  const loop = (now) => {
    raf = 0;
    if (destroyed || !visible || !W) return;
    const settling = draw(now);
    if (!reducedMotion || settling) raf = requestAnimationFrame(loop);
  };
  const kick = () => { if (!raf && !destroyed && visible) raf = requestAnimationFrame(loop); };

  const io = new IntersectionObserver((entries) => { visible = entries[entries.length - 1].isIntersecting; kick(); });
  io.observe(hero);
  const ro = new ResizeObserver(() => { layout(); measureWords(); gather(); kick(); });
  ro.observe(canvas);
  // the words grow when a place is picked (the ask link appears), without the canvas changing size
  const wordsRo = new ResizeObserver(() => { measureWords(); gather(); kick(); });
  if (copyEl) wordsRo.observe(copyEl);
  // the photo under a point (topmost first), among the ones drawn in the last frame
  const photoAt = (e) => {
    const r = canvas.getBoundingClientRect(), px = e.clientX - r.left, py = e.clientY - r.top;
    for (let i = tiles.length - 1; i >= 0; i--) {
      const a = tiles[i].at;
      if (a && px >= a.x && px <= a.x + a.s && py >= a.y && py <= a.y + a.s) return tiles[i];
    }
    return null;
  };
  const onPointerMove = (e) => {
    const r = canvas.getBoundingClientRect();
    lens.tx = e.clientX - r.left; lens.ty = e.clientY - r.top; lens.live = true; lens.last = performance.now();
    if (e.pointerType === "mouse") canvas.style.cursor = photoAt(e)?.place ? "pointer" : "";
    kick();
  };
  hero.addEventListener("pointermove", onPointerMove);
  // a tap on a photo that has a place picks that place, like its chip (the words above let taps through)
  const onClick = (e) => {
    if (e.target !== canvas) return;
    const t = photoAt(e);
    if (t?.place && t.place !== pick) onPick?.(t.place);
  };
  hero.addEventListener("click", onClick);
  const onSprite = () => kick();
  sprite?.addEventListener("load", onSprite);
  const offTheme = onThemeChange(() => { readTok(); kick(); });
  document.fonts?.ready.then(() => kick());

  readTok();
  layout();
  kick();

  return {
    setPick(key) {
      if (key === pick) return;
      pick = key; pickAt = performance.now();
      gather();
      kick();
    },
    destroy() {
      destroyed = true;
      cancelAnimationFrame(raf);
      io.disconnect();
      ro.disconnect();
      wordsRo.disconnect();
      offTheme();
      hero.removeEventListener("pointermove", onPointerMove);
      hero.removeEventListener("click", onClick);
      sprite?.removeEventListener("load", onSprite);
    },
  };
}
