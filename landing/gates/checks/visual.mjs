// 4.1–4.5: contrast, target size, text size, motion length and curve, and reduced motion.
import fs from "node:fs/promises";
import path from "node:path";
import AxeBuilder from "@axe-core/playwright";
import { VIEWPORTS, beatCount, gotoSection, launch, scrollToBeat, touchTargets, visibleTextBlocks } from "../lib/browser.mjs";

const THEMES = [["light", false], ["dark", true]];

// 4.1 axe color-contrast on the section at every scene, light and dark (WCAG 1.4.3: 4.5:1, large text 3:1).
export async function contrast(ctx) {
  const violations = [];
  let incomplete = 0;
  for (const [theme, dark] of THEMES) {
    const b = await launch({ ...VIEWPORTS.phone, dark });
    try {
      await gotoSection(b.page, ctx.url, { theme });
      const n = await beatCount(b.page);
      for (let i = 0; i < n; i++) {
        await scrollToBeat(b.page, i, 0.5, 900);
        const res = await new AxeBuilder({ page: b.page }).include("#how").withRules(["color-contrast"]).analyze();
        for (const v of res.violations) for (const node of v.nodes) violations.push(`${theme} 장면 ${i + 1}: ${node.html.slice(0, 80)} — ${node.any[0]?.message ?? v.help}`);
        incomplete += res.incomplete.reduce((s, v) => s + v.nodes.length, 0);
      }
    } finally { await b.close(); }
  }
  const file = ctx.evidence("contrast.json");
  await fs.writeFile(file, JSON.stringify({ violations, incomplete }, null, 2));
  return { pass: violations.length === 0, value: violations.length ? `${violations.length}곳 대비 부족` : `위반 0 (라이트·다크 × 6장면)${incomplete ? `, 판정 불가 ${incomplete}곳` : ""}`, detail: violations.slice(0, 6).join("; "), evidence: [file] };
}

// 4.2 Every touchable thing in the active scene is at least 44 × 44 CSS px (WCAG 2.5.5 AAA).
export async function targets(ctx) {
  const small = [];
  let count = 0, min = Infinity;
  for (const vp of [VIEWPORTS.phone, VIEWPORTS.phoneS]) {
    const b = await launch(vp);
    try {
      await gotoSection(b.page, ctx.url);
      const n = await beatCount(b.page);
      for (let i = 0; i < n; i++) {
        await scrollToBeat(b.page, i, 0.5, 900);
        for (const t of await touchTargets(b.page)) { count++; const m = Math.min(t.width, t.height); min = Math.min(min, m); if (m < 44) small.push(`${vp.width}px 장면 ${i + 1}: ${t.tag}.${t.className.split(" ")[0]} ${t.label} ${Math.round(t.width)}×${Math.round(t.height)}`); }
      }
    } finally { await b.close(); }
  }
  return { pass: small.length === 0, value: `${count}개 중 44px 미만 ${small.length}개, 최소 ${Math.round(min)}px`, detail: small.slice(0, 8).join("; ") };
}

// 4.3 Text outside the phone ≥ 16 px, inside the phone ≥ 13 px (Learn UI Design: mobile body 16–18, secondary 13–14).
export async function fontSizes(ctx) {
  const small = [];
  let min = { out: Infinity, in: Infinity };
  const b = await launch(VIEWPORTS.phoneS);
  try {
    await gotoSection(b.page, ctx.url);
    const n = await beatCount(b.page);
    for (let i = 0; i < n; i++) {
      await scrollToBeat(b.page, i, 0.5, 900);
      for (const t of await visibleTextBlocks(b.page, ".ts")) {
        if (/^[*·\s]+$/.test(t.text) || t.aria) continue;
        if (t.inPhone) { min.in = Math.min(min.in, t.fontSize); if (t.fontSize < 13) small.push(`장면 ${i + 1} 폰 안 ${t.fontSize}px: ${t.text.slice(0, 20)}`); }
        else if (t.inStage) { min.out = Math.min(min.out, t.fontSize); if (t.fontSize < 16) small.push(`장면 ${i + 1} 폰 밖 ${t.fontSize}px: ${t.text.slice(0, 20)}`); }
      }
    }
  } finally { await b.close(); }
  return { pass: small.length === 0, value: `최소 폰 밖 ${min.out}px, 폰 안 ${min.in}px`, detail: small.slice(0, 8).join("; ") };
}

// 4.4 Each element's transition/animation runs 100–400 ms, enters with ease-out (never bare ease-in), and
// never animates `all`. Scroll-linked transforms are set inline per frame and carry no transition, so they
// fall outside this rule. Longer explanatory sequences are listed with a reason in gates/motion-exceptions.json.
export async function motion(ctx) {
  const exceptions = JSON.parse(await fs.readFile(path.join(ctx.gatesDir, "motion-exceptions.json"), "utf8").catch(() => "[]"));
  const b = await launch(VIEWPORTS.phone);
  try {
    await gotoSection(b.page, ctx.url);
    const found = await b.page.evaluate(() => {
      const ms = (s) => s.split(",").map((v) => (v.trim().endsWith("ms") ? parseFloat(v) : parseFloat(v) * 1000));
      const out = [];
      for (const el of document.querySelectorAll("#how *")) {
        const cs = getComputedStyle(el);
        const durations = ms(cs.transitionDuration).map((d, k) => ({ d, prop: cs.transitionProperty.split(",")[k]?.trim() ?? cs.transitionProperty.split(",")[0].trim(), ease: cs.transitionTimingFunction.split(/,(?![^(]*\))/)[k]?.trim() ?? cs.transitionTimingFunction }));
        const anims = ms(cs.animationDuration).map((d, k) => ({ d, prop: `animation:${cs.animationName.split(",")[k]?.trim()}`, ease: cs.animationTimingFunction.split(/,(?![^(]*\))/)[k]?.trim(), iter: cs.animationIterationCount.split(",")[k]?.trim() }));
        for (const t of [...durations, ...anims]) {
          if (!t.d || t.prop === "none" || t.prop === "animation:none") continue;
          const sel = `${el.tagName.toLowerCase()}${el.className && typeof el.className === "string" ? "." + el.className.trim().split(/\s+/).join(".") : ""}`;
          out.push({ sel, ...t });
        }
      }
      return out;
    });
    const seen = new Map();
    for (const f of found) seen.set(`${f.sel}|${f.prop}|${f.d}|${f.ease}`, f);
    // an exception's selector: ".a.b" matches any element whose class list carries ".a.b"; a bare tag ("span") only an
    // element with that tag and no class
    const excepted = (f) => exceptions.find((e) => (e.selector.startsWith(".") ? f.sel.includes(e.selector) : f.sel === e.selector));
    const problems = [];
    for (const f of seen.values()) {
      if (excepted(f)) continue;
      if (f.prop === "all") problems.push(`${f.sel}: transition: all`);
      if (f.d < 100 || f.d > 400) problems.push(`${f.sel} ${f.prop} ${f.d}ms`);
      if (f.ease === "ease-in" || f.ease === "cubic-bezier(0.42, 0, 1, 1)") problems.push(`${f.sel} ${f.prop}: ease-in`);
    }
    const file = ctx.evidence("motion.json");
    await fs.writeFile(file, JSON.stringify({ found: [...seen.values()], exceptions, problems }, null, 2));
    return { pass: problems.length === 0, value: `전환 ${seen.size}개 중 범위 밖 ${problems.length}개 (예외 ${exceptions.length}개)`, detail: problems.slice(0, 10).join("; "), evidence: [file] };
  } finally { await b.close(); }
}

// 4.5 With prefers-reduced-motion the phone and the words never tween: transforms have no transition and the
// phone's transform takes at most two values across the step change (a cut, not a turn).
export async function reducedMotion(ctx) {
  const b = await launch({ ...VIEWPORTS.phone, reduce: true });
  try {
    await gotoSection(b.page, ctx.url);
    const tweens = await b.page.evaluate(() => {
      const out = [];
      for (const el of document.querySelectorAll("#how *")) {
        const cs = getComputedStyle(el);
        const props = cs.transitionProperty.split(",").map((p) => p.trim());
        const durs = cs.transitionDuration.split(",").map((v) => (v.trim().endsWith("ms") ? parseFloat(v) : parseFloat(v) * 1000));
        props.forEach((p, k) => { if (/transform|translate|rotate|scale|top|left|width|height/.test(p) && durs[k] > 0) out.push(`${el.tagName.toLowerCase()}.${(el.className || "").toString().split(" ")[0]} ${p} ${durs[k]}ms`); });
        if (cs.animationName !== "none" && parseFloat(cs.animationDuration) > 0) out.push(`${el.tagName.toLowerCase()}.${(el.className || "").toString().split(" ")[0]} animation ${cs.animationName}`);
      }
      return [...new Set(out)];
    });
    const values = new Set();
    const bridge = await b.page.evaluate(() => { const el = document.querySelector("[data-bridge]"); const r = el.getBoundingClientRect(); return { top: scrollY + r.top, height: r.height }; });
    for (let t = -0.5; t <= 1.5; t += 0.05) { await b.page.evaluate((y) => window.scrollTo(0, y), bridge.top + bridge.height * t); await b.page.waitForTimeout(40); values.add(await b.page.evaluate(() => document.querySelector("[data-phone]").style.transform)); }
    const pass = tweens.length === 0 && values.size <= 2;
    return { pass, value: `위치·크기 전환 ${tweens.length}개, 단계 전환 중 폰 변환값 ${values.size}가지`, detail: [...tweens.slice(0, 6), ...(values.size > 2 ? [`transform 값: ${[...values].join(" | ")}`] : [])].join("; ") };
  } finally { await b.close(); }
}
