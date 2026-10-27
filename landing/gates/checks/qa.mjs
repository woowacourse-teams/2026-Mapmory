// 6.1–6.3, 6.5: the device/theme matrix, the keyboard, the touch scenarios, and the release flag.
import fs from "node:fs/promises";
import path from "node:path";
import { VIEWPORTS, beatCount, gotoSection, launch, overlaps, rectOf, scrollToBeat, within } from "../lib/browser.mjs";
import { runAllScenes } from "../lib/interact.mjs";

// 6.1 Every width × theme × motion setting, at every scene: nothing overflows sideways, the phone, label and
// title sit whole on screen without overlapping, the phone's screen stays inside the phone, and no title is clipped.
export async function matrix(ctx) {
  const problems = [];
  const cells = [];
  const evidence = [];
  for (const vp of [VIEWPORTS.reflow, VIEWPORTS.phoneS, VIEWPORTS.phone, VIEWPORTS.tablet, VIEWPORTS.desktop]) {
    for (const [theme, dark] of [["light", false], ["dark", true]]) {
      for (const reduce of [false, true]) {
        const b = await launch({ ...vp, dark, reduce });
        const tag = `${vp.width}-${theme}${reduce ? "-reduce" : ""}`;
        try {
          await gotoSection(b.page, ctx.url, { theme });
          const n = await beatCount(b.page);
          for (let i = 0; i < n; i++) {
            await scrollToBeat(b.page, i, 0.5, reduce ? 300 : 700);
            const m = await b.page.evaluate(() => {
              const r = (sel) => { const el = document.querySelector(sel); if (!el) return null; const q = el.getBoundingClientRect(); return { top: q.top, left: q.left, right: q.right, bottom: q.bottom, width: q.width, height: q.height }; };
              const h3 = document.querySelector(".ts-copy.is-on h3");
              return { sw: document.documentElement.scrollWidth, iw: innerWidth, phone: r("[data-phone]"), screen: r(".ts-screen.is-on"), label: r(".ts-copy.is-on .ts-label"), title: r(".ts-copy.is-on h3"), header: r(".site-header"),
                clipped: h3 ? h3.scrollWidth > h3.clientWidth + 1 : false };
            });
            const issues = [];
            if (m.sw > m.iw) issues.push(`가로 넘침 ${m.sw}>${m.iw}`);
            if (!within(m.phone, vp.width, vp.height)) issues.push("폰이 화면 밖");
            if (!within(m.label, vp.width, vp.height)) issues.push("라벨이 화면 밖");
            if (!within(m.title, vp.width, vp.height)) issues.push("제목이 화면 밖");
            if (m.header && m.phone && m.phone.top < m.header.bottom && overlaps(m.header, m.phone)) issues.push("폰이 헤더에 가려짐");
            if (overlaps(m.phone, m.label) || overlaps(m.phone, m.title)) issues.push("폰과 글이 겹침");
            if (overlaps(m.label, m.title)) issues.push("라벨과 제목이 겹침");
            if (m.screen && m.phone && (m.screen.left < m.phone.left - 1 || m.screen.right > m.phone.right + 1 || m.screen.top < m.phone.top - 1 || m.screen.bottom > m.phone.bottom + 1)) issues.push("화면이 폰 밖으로 나감");
            if (m.clipped) issues.push("제목 잘림");
            cells.push({ cell: tag, scene: i + 1, issues });
            if (issues.length) { problems.push(`${tag} 장면 ${i + 1}: ${issues.join(", ")}`); if (evidence.length < 12) { const f = ctx.evidence(`matrix-${tag}-${i + 1}.png`); await b.page.screenshot({ path: f }); evidence.push(f); } }
            else if (i === 1) { const f = ctx.evidence(`matrix-${tag}.png`); await b.page.screenshot({ path: f }); evidence.push(f); }
          }
        } finally { await b.close(); }
      }
    }
  }
  const file = ctx.evidence("matrix.json");
  await fs.writeFile(file, JSON.stringify(cells, null, 2));
  return { pass: problems.length === 0, value: `${cells.length}칸 중 문제 ${problems.length}칸 (5폭 × 2테마 × 동작 줄이기 2 × 6장면)`, detail: problems.slice(0, 10).join("; "), evidence: [file, ...evidence] };
}

// 6.2 Keyboard, with reduced motion so state changes are instant: at every scene, every touchable thing on the
// phone screen is reached by Tab from the section title, shows a focus ring, and reacts to Enter or Space (the
// globe to an arrow key).
export async function keyboard(ctx) {
  const b = await launch({ ...VIEWPORTS.desktop, reduce: true, touch: false });
  const rows = [];
  try {
    await gotoSection(b.page, ctx.url);
    const n = await beatCount(b.page);
    for (let i = 0; i < n; i++) {
      await scrollToBeat(b.page, i, 0.5, 600);
      const targets = await b.page.evaluate(() => {
        const sel = ".ts-screen.is-on button, .ts-screen.is-on [role=button], .ts-screen.is-on [tabindex], .ts-screen.is-on video, .ts-screen.is-on canvas, .ts-screen.is-on .ts-flip";
        return [...document.querySelectorAll(sel)].filter((el) => !el.hidden && !el.disabled && el.getClientRects().length).map((el, k) => { el.dataset.gateK = String(k); return { k, tag: el.tagName.toLowerCase(), label: (el.getAttribute("aria-label") || el.textContent || el.className).trim().slice(0, 24) }; });
      });
      await b.page.evaluate(() => { const h = document.getElementById("tech-title"); h.tabIndex = -1; h.focus(); });
      const reached = new Map();
      for (let t = 0; t < 120; t++) {
        await b.page.keyboard.press("Tab");
        const f = await b.page.evaluate(() => {
          const el = document.activeElement;
          if (!el || !el.closest("#how")) return { out: true };
          const style = () => { const cs = getComputedStyle(el); return `${cs.outlineStyle}|${cs.outlineWidth}|${cs.boxShadow}|${cs.borderColor}|${cs.backgroundColor}`; };
          const focused = style(); el.blur(); const blurred = style(); el.focus();
          return { k: el.dataset.gateK, ring: focused !== blurred };
        });
        if (f.out) break;
        if (f.k !== undefined && !reached.has(f.k)) reached.set(f.k, f);
      }
      for (const target of targets) {
        const r = reached.get(String(target.k));
        let works = false, how = "";
        if (r) {
          const snap = () => b.page.evaluate((k) => { const el = document.querySelector(`[data-gate-k="${k}"]`); const s = document.querySelector(".ts-screen.is-on"); const v = s.querySelector("video"); const c = s.querySelector("canvas"); return JSON.stringify({ cls: el.className, pressed: el.getAttribute("aria-pressed"), screen: s.className, text: s.innerText, paused: v?.paused, canvas: c ? c.toDataURL().length + c.toDataURL().slice(-80) : null }); }, target.k);
          await b.page.evaluate((k) => document.querySelector(`[data-gate-k="${k}"]`).focus(), target.k);
          const keys = target.tag === "canvas" ? ["ArrowRight", "ArrowLeft"] : ["Enter", "Space"];
          for (const key of keys) {
            const before = await snap();
            await b.page.keyboard.press(key);
            await b.page.waitForTimeout(350);
            if ((await snap()) !== before) { works = true; how = key; break; }
            if (await b.page.evaluate((k) => document.querySelector(`[data-gate-k="${k}"]`)?.disabled, target.k)) { works = true; how = `${key} (끝난 뒤 비활성)`; break; }
          }
        }
        rows.push({ scene: i + 1, target: `${target.tag} ${target.label}`, reached: Boolean(r), ring: Boolean(r?.ring), works, how });
      }
      if (!targets.length) rows.push({ scene: i + 1, target: "(조작 요소 없음)", reached: false, ring: false, works: false, how: "" });
    }
  } finally { await b.close(); }
  const bad = rows.filter((r) => !(r.reached && r.ring && r.works));
  const file = ctx.evidence("keyboard.json");
  await fs.writeFile(file, JSON.stringify(rows, null, 2));
  return { pass: bad.length === 0, value: `조작 요소 ${rows.length}개 중 Tab 도달 ${rows.filter((r) => r.reached).length}, 포커스 표시 ${rows.filter((r) => r.ring).length}, 키로 동작 ${rows.filter((r) => r.works).length}`,
    detail: bad.slice(0, 8).map((r) => `장면 ${r.scene} ${r.target}: ${[!r.reached && "Tab 도달 못 함", r.reached && !r.ring && "포커스 표시 없음", r.reached && !r.works && "Enter/Space 반응 없음"].filter(Boolean).join(", ")}`).join("; "), evidence: [file] };
}

// 6.3 The six touch scenarios on a 390 px phone (see lib/interact.mjs).
export async function touch(ctx) {
  const b = await launch(VIEWPORTS.phone);
  try {
    await gotoSection(b.page, ctx.url);
    const results = await runAllScenes(b.page, { touch: true });
    const file = ctx.evidence("touch.json");
    await fs.writeFile(file, JSON.stringify(results, null, 2));
    const ok = results.filter((r) => r.ok).length;
    return { pass: ok === results.length && results.length === 6, value: `${ok}/${results.length} (${results.map((r) => `${r.name} ${r.ok ? "O" : "X"}`).join(", ")})`, detail: results.filter((r) => !r.ok).map((r) => `${r.scene} ${r.name}: ${r.note}`).join("; "), evidence: [file] };
  } finally { await b.close(); }
}

// 6.5 The share scene ships only with the app feature (#436): TECH_SHARE.enabled must be false until a person records
// in gates/manual/6.5.json that the feature is in the store ({ "appShipped": true, "by": "…", "at": "YYYY-MM-DD" }).
export async function shareFlag(ctx) {
  const src = await fs.readFile(path.join(ctx.landingRoot, "src/techStory.js"), "utf8");
  const enabled = /TECH_SHARE = Object\.freeze\(\{[\s\S]*?enabled:\s*true/.test(src);
  const record = JSON.parse(await fs.readFile(path.join(ctx.gatesDir, "manual", "6.5.json"), "utf8").catch(() => "null"));
  const shipped = record?.appShipped === true && record.by && record.at;
  if (!enabled) return { pass: true, value: "TECH_SHARE.enabled=false (공유 장면 없이 내보냄)" };
  if (shipped) return { pass: true, value: `enabled=true, 앱 공유 기능 출시 확인 (${record.by} ${record.at})` };
  return { pass: false, value: "enabled=true인데 앱 공유 기능(#436) 출시 기록 없음", detail: "출시 빌드 전에 src/techStory.js TECH_SHARE.enabled를 false로 두거나, 앱이 스토어에 나간 뒤 gates/manual/6.5.json에 appShipped: true를 기록" };
}
