// Small helpers shared by the hero wall, the roll and the record story canvases.
export const clamp = (v, a = 0, b = 1) => Math.min(b, Math.max(a, v));
export const lerp = (a, b, t) => a + (b - a) * t;
export const easeOut = (t) => 1 - Math.pow(1 - t, 3);
export const easeIO = (t) => (t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2);
export const FONT = '"LINE Seed Sans KR", "LINE Seed Sans KR Ext", system-ui, sans-serif';
export const font = (px, weight = 700) => `${weight} ${px}px ${FONT}`;
export const prefersReducedMotion = () => typeof window !== "undefined" && window.matchMedia("(prefers-reduced-motion: reduce)").matches;

export function roundRect(ctx, x, y, w, h, r) {
  ctx.beginPath();
  if (ctx.roundRect) ctx.roundRect(x, y, w, h, Math.max(0, Math.min(r, w / 2, h / 2)));
  else ctx.rect(x, y, w, h);
}

// The landing's CSS custom properties read from `el` (the section scopes its own --ps-* tokens).
export function readTokens(el, names) {
  const style = getComputedStyle(el);
  const out = {};
  for (const name of names) out[name] = style.getPropertyValue(`--${name}`).trim();
  return out;
}

// Calls fn whenever the header's theme toggle changes data-theme on <html>; returns a disconnect function.
export function onThemeChange(fn) {
  const observer = new MutationObserver(fn);
  observer.observe(document.documentElement, { attributes: true, attributeFilter: ["data-theme"] });
  return () => observer.disconnect();
}

// Draws cell k of the sprite into (x, y, s, s), clipped to a rounded square; a flat colour stands in until it loads.
export function drawSpriteTile(ctx, sprite, meta, k, x, y, s, r = 4) {
  if (s <= 0.5) return;
  ctx.save();
  roundRect(ctx, x, y, s, s, r);
  ctx.clip();
  if (sprite?.complete && sprite.naturalWidth) {
    ctx.drawImage(sprite, (k % meta.cols) * meta.cell, Math.floor(k / meta.cols) * meta.cell, meta.cell, meta.cell, x, y, s, s);
  } else {
    ctx.fillStyle = meta.colors[k] || "#dbe5de";
    ctx.fill();
  }
  ctx.restore();
}

// Sizes a canvas to its CSS box at a capped device pixel ratio; returns { W, H, DPR }.
export function fitCanvas(canvas, maxDpr = 2) {
  const r = canvas.getBoundingClientRect();
  const DPR = Math.min(maxDpr, window.devicePixelRatio || 1);
  canvas.width = Math.max(1, Math.round(r.width * DPR));
  canvas.height = Math.max(1, Math.round(r.height * DPR));
  return { W: r.width, H: r.height, DPR };
}
