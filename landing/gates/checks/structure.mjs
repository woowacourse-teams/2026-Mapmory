// 3.1–3.6: the section follows the page's scroll, never steers it; nothing moves sideways; the words are
// readable wherever a flick stops; every scene is reached; each scene fits one phone screen; a flick still shows
// each scene change.
import fs from "node:fs/promises";
import path from "node:path";
import { SCENE_COUNT } from "../config.mjs";
import { VIEWPORTS, activeScene, beatCount, flickTo, gotoSection, launch, overlaps, rectOf, scrollToBeat, sectionRange, within } from "../lib/browser.mjs";

const stripComments = (s) => s.replace(/\/\*[\s\S]*?\*\//g, "").replace(/(^|[^:\\])\/\/.*$/gm, "$1");

// 3.1 Static: the engine listens to scroll and never scrolls, snaps or swallows the user's gesture.
export async function noScrolljack(ctx) {
  const engine = stripComments(await fs.readFile(path.join(ctx.landingRoot, "src/techStoryEngine.js"), "utf8"));
  const css = await fs.readFile(path.join(ctx.landingRoot, "src/tech-story.css"), "utf8");
  const banned = [[/"wheel"/, "wheel 리스너"], [/"touchmove"/, "touchmove 리스너"], [/"touchstart"/, "touchstart 리스너"], [/scrollTo\(/, "scrollTo"], [/scrollBy\(/, "scrollBy"], [/scrollIntoView/, "scrollIntoView"], [/window, "keydown"/, "window keydown"]];
  const hits = banned.filter(([re]) => re.test(engine)).map(([, name]) => name);
  if (/scroll-snap/.test(css)) hits.push("scroll-snap");
  if (!/on\(window, "scroll", schedule, \{ passive: true \}\)/.test(engine)) hits.push("passive scroll 리스너 아님");
  return { pass: hits.length === 0, value: hits.length ? hits.join(", ") : "금지 호출 0, passive 스크롤 리스너", detail: hits.join(", ") };
}

// 3.2 No horizontal scroll at any width, at every scene.
export async function noHorizontalScroll(ctx) {
  const bad = [];
  for (const vp of [VIEWPORTS.reflow, VIEWPORTS.phoneS, VIEWPORTS.phone, VIEWPORTS.desktop]) {
    const b = await launch(vp);
    try {
      await gotoSection(b.page, ctx.url);
      const n = await beatCount(b.page);
      for (let i = 0; i < n; i++) {
        await scrollToBeat(b.page, i, 0.5, 300);
        const { sw, iw } = await b.page.evaluate(() => ({ sw: document.documentElement.scrollWidth, iw: innerWidth }));
        if (sw > iw) bad.push(`${vp.width}px 장면 ${i + 1}: scrollWidth ${sw} > ${iw}`);
      }
    } finally { await b.close(); }
  }
  return { pass: bad.length === 0, value: bad.length ? `${bad.length}곳 가로 넘침` : "320/360/390/1440 모두 scrollWidth = 뷰포트", detail: bad.join("; ") };
}

// 3.3 Flick (2000 px/s) to 40 stops inside the section and look 300 ms later: the title must be whole and solid.
export async function titleAlwaysReadable(ctx) {
  const results = [];
  const evidence = [];
  for (const vp of [VIEWPORTS.phone, VIEWPORTS.phoneS]) {
    const b = await launch(vp);
    try {
      await gotoSection(b.page, ctx.url);
      const { start, end } = await sectionRange(b.page);
      const inner = [start + 0.1 * (end - start), end - 0.1 * (end - start)];
      const stops = Array.from({ length: 40 }, (_, k) => inner[0] + ((inner[1] - inner[0]) * k) / 39);
      await b.page.evaluate((y) => window.scrollTo(0, y), start - 300);
      let readable = 0;
      for (const [k, stop] of stops.entries()) {
        await flickTo(b.page, stop, 2000);
        await b.page.waitForTimeout(300);
        const r = await b.page.evaluate(() => {
          const copy = document.querySelector(".ts-copy.is-on") ?? document.querySelector('.ts-copy[data-caption="0"]');
          const h = copy?.querySelector("h3"); if (!h) return null;
          let node = copy, opacity = 1; while (node && node !== document.body) { opacity *= parseFloat(getComputedStyle(node).opacity); node = node.parentElement; }
          const rect = h.getBoundingClientRect();
          return { opacity, top: rect.top, bottom: rect.bottom, text: h.textContent };
        });
        const ok = r && r.opacity >= 0.9 && r.top >= 0 && r.bottom <= vp.height;
        if (ok) readable++;
        else if (evidence.length < 3) { const f = ctx.evidence(`fling-miss-${vp.width}-${k}.png`); await b.page.screenshot({ path: f }); evidence.push(f); }
        results.push({ width: vp.width, stop: Math.round(stop), ok, opacity: r?.opacity, text: r?.text });
      }
    } finally { await b.close(); }
  }
  const file = ctx.evidence("fling-stops.json");
  await fs.writeFile(file, JSON.stringify(results, null, 2));
  const total = results.length, ok = results.filter((r) => r.ok).length;
  return { pass: ok === total, value: `${ok}/${total} 멈춤 지점에서 제목이 온전히 보임 (390·360px)`, detail: results.filter((r) => !r.ok).map((r) => `${r.width}px y=${r.stop} opacity ${r.opacity?.toFixed(2)}`).join("; "), evidence: [file, ...evidence] };
}

// 3.4 Scrolling at a reading pace (1000 px/s) activates every scene at least once.
export async function sceneReach(ctx) {
  const b = await launch(VIEWPORTS.phone);
  try {
    await gotoSection(b.page, ctx.url);
    const { start, end, vh } = await sectionRange(b.page);
    // from above the section head, so the focus line (lower half of a phone screen) enters the first beat on the way
    const from = Math.max(0, start - vh - 100);
    await b.page.evaluate((y) => window.scrollTo(0, y), from);
    await b.page.waitForTimeout(300);
    const seen = new Set();
    const step = 16;
    for (let y = from; y <= end + 200; y += step) { await b.page.evaluate((y) => window.scrollTo(0, y), y); await b.page.waitForTimeout(16); seen.add(await activeScene(b.page)); }
    const reached = [...seen].filter((i) => i >= 0).sort();
    return { pass: reached.length === SCENE_COUNT, value: `${reached.length}/${SCENE_COUNT} 장면 활성화 (${reached.map((i) => i + 1).join(",")})` };
  } finally { await b.close(); }
}

// 3.5 At each scene stop the phone, the label and the title are all on screen, under the header, not overlapping.
export async function sceneLayout(ctx) {
  const bad = [];
  const evidence = [];
  for (const vp of [VIEWPORTS.phone, VIEWPORTS.phoneS]) {
    const b = await launch(vp);
    try {
      await gotoSection(b.page, ctx.url);
      const n = await beatCount(b.page);
      for (let i = 0; i < n; i++) {
        await scrollToBeat(b.page, i, 0.5, 700);
        const phone = await rectOf(b.page, "[data-phone]"), label = await rectOf(b.page, ".ts-copy.is-on .ts-label"), title = await rectOf(b.page, ".ts-copy.is-on h3"), header = await rectOf(b.page, ".site-header");
        const issues = [];
        if (!within(phone, vp.width, vp.height)) issues.push("폰이 화면 밖");
        if (!within(label, vp.width, vp.height)) issues.push("라벨이 화면 밖");
        if (!within(title, vp.width, vp.height)) issues.push("제목이 화면 밖");
        if (header && phone && phone.top < header.bottom) issues.push("폰이 헤더에 가려짐");
        if (overlaps(phone, label) || overlaps(phone, title)) issues.push("폰과 글이 겹침");
        if (overlaps(label, title)) issues.push("라벨과 제목이 겹침");
        const f = ctx.evidence(`scene-${vp.width}-${i + 1}.png`);
        await b.page.screenshot({ path: f });
        evidence.push(f);
        if (issues.length) bad.push(`${vp.width}px 장면 ${i + 1}: ${issues.join(", ")}`);
      }
    } finally { await b.close(); }
  }
  return { pass: bad.length === 0, value: bad.length ? `${bad.length}곳 문제` : "390·360px 6장면 모두 폰·라벨·제목이 한 화면에, 겹침 없음", detail: bad.join("; "), evidence };
}

// 3.6 A flick through the section (2,000 px/s on a 390 phone, sampled every frame) still shows each scene's change:
// every scene is seen in at least two different states (the turned-over row of the roll, the number of lifted photos,
// the picks and the fold, the map's levels, the trip's stops, the globe landing; the share scene is tap-only and exempt).
export async function flickLegible(ctx) {
  const b = await launch(VIEWPORTS.phone);
  try {
    await gotoSection(b.page, ctx.url);
    const { start, end, vh } = await sectionRange(b.page);
    await b.page.evaluate((y) => window.scrollTo(0, y), Math.max(0, start - vh));
    await b.page.waitForTimeout(500);
    await b.page.evaluate(() => {
      window.__flick = [];
      const text = (sel) => document.querySelector(sel)?.textContent.trim() ?? "";
      const cls = (sel) => document.querySelector(sel)?.className ?? "";
      const sample = () => {
        const on = [...document.querySelectorAll("[data-caption]")].indexOf(document.querySelector("[data-caption].is-on"));
        const globe = () => { const cv = document.querySelector("[data-globe]"); if (!cv?.width) return ""; const d = cv.getContext("2d").getImageData(0, 0, cv.width, cv.height).data; let s = 0; for (let i = 0; i < d.length; i += 4 * 211) s += d[i] + 2 * d[i + 1]; return String(s); };
        const state = on === 0 ? `${text("[data-field-text]")}|${cls("[data-load]")}`
          : on === 1 ? String(document.querySelectorAll("[data-gather] .is-hit:not([hidden])").length)
          : on === 2 ? cls("[data-safe]")
          : on === 3 ? `${document.querySelectorAll("[data-pick] .is-sel").length}|${cls("[data-pick]")}|${text("[data-save]")}`
          : on === 4 ? String(document.querySelectorAll("[data-regions] .ts-pv.is-on").length)
          : on === 5 ? globe() : "";
        window.__flick.push([on, state]);
        window.__flickRaf = requestAnimationFrame(sample);
      };
      sample();
    });
    await flickTo(b.page, end, 2000);
    await b.page.waitForTimeout(300);
    const samples = await b.page.evaluate(() => { cancelAnimationFrame(window.__flickRaf); return window.__flick; });
    const n = await beatCount(b.page);
    const rows = [...Array(n).keys()].map((i) => {
      const mine = samples.filter((s) => s[0] === i);
      return { scene: i + 1, frames: mine.length, states: [...new Set(mine.map((s) => s[1]))] };
    });
    // the share scene (7) is tap-only by design: its video never plays by itself and scroll changes nothing there
    const tapOnly = new Set([7]);
    const bad = rows.filter((r) => r.states.length < 2 && !tapOnly.has(r.scene));
    const file = ctx.evidence("flick.json");
    await fs.writeFile(file, JSON.stringify({ speed: 2000, rows }, null, 2));
    return { pass: bad.length === 0 && n === SCENE_COUNT, value: `장면별 상태 ${rows.map((r) => r.states.length).join("/")}개 (프레임 ${rows.map((r) => r.frames).join("/")})`,
      detail: bad.map((r) => `장면 ${r.scene}: 상태 ${r.states.length}개 (${r.frames}프레임)`).join("; "), evidence: [file] };
  } finally { await b.close(); }
}
