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
  // Scroll decides the scene, so a keyboard visitor scrolls to a scene and then tabs into the phone: the walk starts
  // from the scene's own screen (focused without scrolling), the hidden screens' controls are visibility: hidden and
  // not tabbable, and a Tab that scrolled the page and switched the scene would count as leaving the screen.
  const b = await launch({ ...VIEWPORTS.desktop, reduce: true, touch: false });
  const rows = [];
  try {
    await gotoSection(b.page, ctx.url);
    const n = await beatCount(b.page);
    for (let i = 0; i < n; i++) {
      await scrollToBeat(b.page, i, 0.5, 900);
      const targets = await b.page.evaluate(() => {
        // the photo roll is one composite widget (its own tab stop), not 72 stops; the scene's other controls are real buttons
        const sel = ".ts-screen.is-on button, .ts-screen.is-on [role=button], .ts-screen.is-on [tabindex], .ts-screen.is-on video, .ts-screen.is-on canvas";
        document.querySelectorAll("[data-gate-k]").forEach((el) => { delete el.dataset.gateK; }); // the earlier scenes' marks
        return [...document.querySelectorAll(sel)].filter((el) => !el.hidden && !el.disabled && el.getClientRects().length).map((el, k) => { el.dataset.gateK = String(k); return { k, tag: el.tagName.toLowerCase(), label: (el.getAttribute("aria-label") || el.textContent || el.className).trim().slice(0, 24) }; });
      });
      const startAtScreen = () => b.page.evaluate(() => { const s = document.querySelector(".ts-screen.is-on"); s.tabIndex = -1; s.focus({ preventScroll: true }); });
      await startAtScreen();
      const reached = new Map();
      let sceneChanged = false;
      for (let t = 0; t < 120; t++) {
        await b.page.keyboard.press("Tab");
        const f = await b.page.evaluate((i) => {
          const el = document.activeElement;
          const on = [...document.querySelectorAll("[data-scene]")].findIndex((s) => s.classList.contains("is-on"));
          // leaving the section (the next link scrolls the page) is fine; a Tab inside the stage that moved the scene is not
          if (on !== i) return { out: true, sceneChanged: Boolean(el?.closest(".ts-stage")) };
          if (!el || !el.closest(".ts-screen.is-on")) return { out: true };
          const style = () => { const cs = getComputedStyle(el); return `${cs.outlineStyle}|${cs.outlineWidth}|${cs.boxShadow}|${cs.borderColor}|${cs.backgroundColor}`; };
          const focused = style(); el.blur(); const blurred = style(); el.focus({ preventScroll: true });
          return { k: el.dataset.gateK, ring: focused !== blurred };
        }, i);
        if (f.sceneChanged) sceneChanged = true;
        if (f.out) break;
        if (f.k !== undefined && !reached.has(f.k)) reached.set(f.k, f);
        if (reached.size === targets.length) break;
      }
      // the walk may have left the section (and scrolled the page): come back to the scene for the key tests
      await scrollToBeat(b.page, i, 0.5, 600);
      for (const target of targets) {
        const r = reached.get(String(target.k));
        let works = false, how = "";
        if (r) {
          // anything the key changed in the screen counts: a class anywhere in it, its text, the video playing, the globe's pixels
          const snap = () => b.page.evaluate((k) => { const el = document.querySelector(`[data-gate-k="${k}"]`); const s = document.querySelector(".ts-screen.is-on"); const v = s.querySelector("video"); const c = s.querySelector("canvas"); return JSON.stringify({ cls: [...s.querySelectorAll("[class]")].map((e) => e.className).join("|"), pressed: el.getAttribute("aria-pressed"), text: s.innerText, paused: v?.paused, time: v && Math.round(v.currentTime), canvas: c ? c.toDataURL().length + c.toDataURL().slice(-80) : null }); }, target.k);
          // a button whose click fires on Enter/Space works even when the screen looks the same (the place was already gathered)
          const focused = await b.page.evaluate((k) => { const el = document.querySelector(`[data-gate-k="${k}"]`); window.__gateClicks = 0; el.addEventListener("click", () => { window.__gateClicks++; }); el.focus({ preventScroll: true }); return document.activeElement === el; }, target.k);
          if (!focused) { rows.push({ scene: i + 1, target: `${target.tag} ${target.label}`, reached: true, ring: Boolean(r.ring), works: false, how: "포커스 안 됨", sceneChanged }); continue; }
          const keys = target.tag === "canvas" ? ["ArrowRight", "ArrowLeft"] : ["Enter", "Space"];
          for (const key of keys) {
            const before = await snap();
            await b.page.keyboard.press(key);
            // a seek in the share video can take a moment on a slow machine: wait up to 1.5 s for any change
            for (let w = 0; w < 10 && !works; w++) {
              await b.page.waitForTimeout(150);
              if ((await snap()) !== before || (target.tag === "button" && await b.page.evaluate(() => window.__gateClicks > 0))) { works = true; how = key; }
              else if (await b.page.evaluate((k) => document.querySelector(`[data-gate-k="${k}"]`)?.disabled, target.k)) { works = true; how = `${key} (끝난 뒤 비활성)`; }
            }
            if (works) break;
          }
        }
        rows.push({ scene: i + 1, target: `${target.tag} ${target.label}`, reached: Boolean(r), ring: Boolean(r?.ring), works, how, sceneChanged });
      }
      if (!targets.length) rows.push({ scene: i + 1, target: "(조작 요소 없음)", reached: false, ring: false, works: false, how: "", sceneChanged });
    }
  } finally { await b.close(); }
  const bad = rows.filter((r) => !(r.reached && r.ring && r.works) || r.sceneChanged);
  const file = ctx.evidence("keyboard.json");
  await fs.writeFile(file, JSON.stringify(rows, null, 2));
  return { pass: bad.length === 0, value: `조작 요소 ${rows.length}개 중 Tab 도달 ${rows.filter((r) => r.reached).length}, 포커스 표시 ${rows.filter((r) => r.ring).length}, 키로 동작 ${rows.filter((r) => r.works).length}${rows.some((r) => r.sceneChanged) ? ", Tab이 장면을 바꿈" : ""}`,
    detail: bad.slice(0, 8).map((r) => `장면 ${r.scene} ${r.target}: ${[r.sceneChanged && "Tab 중 장면이 바뀜", !r.reached && "Tab 도달 못 함", r.reached && !r.ring && "포커스 표시 없음", r.reached && !r.works && "Enter/Space 반응 없음"].filter(Boolean).join(", ")}`).join("; "), evidence: [file] };
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
