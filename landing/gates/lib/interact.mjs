// What a visitor does on each scene, with a finger (contexts with touch) or a mouse. Shared by the touch,
// analytics and console checks. Each scenario scrolls to its scene, acts, and reports what it saw; none throws
// for a missing element, so one broken scene shows as one failed line.
import { scrollToBeat } from "./browser.mjs";

const centerOf = (page, sel, i = 0) => page.evaluate(([sel, i]) => {
  const el = document.querySelectorAll(sel)[i];
  if (!el) return null;
  const r = el.getBoundingClientRect();
  return [r.x + r.width / 2, r.y + r.height / 2];
}, [sel, i]);

const tapper = (page, touch) => async (sel, i = 0) => {
  const c = await centerOf(page, sel, i);
  if (!c) return false;
  if (touch) await page.touchscreen.tap(c[0], c[1]); else await page.mouse.click(c[0], c[1]);
  return true;
};

const row = (scene, name, ok, note) => ({ scene, name, ok: Boolean(ok), note });

// 1 Tap the place field: the place changes (제주 → 일본 → …) and that place's photos lift under it.
export async function placePick(page, { touch = true } = {}) {
  const tap = tapper(page, touch);
  await scrollToBeat(page, 0, 0.5, 300);
  await page.waitForTimeout(400);
  const read = () => page.evaluate(() => ({ place: document.querySelector("[data-field-text]")?.textContent ?? "", lifted: document.querySelectorAll("[data-gather] .ts-ph.is-hit:not([hidden])").length }));
  const first = await read();
  if (!(await tap("[data-field]"))) return row(1, "장소", false, "장소 칸 없음");
  await page.waitForTimeout(1600);
  const second = await read();
  return row(1, "장소", second.place && second.place !== first.place && second.lifted > 0, `"${first.place}" → 누른 뒤 "${second.place}" (${second.lifted}장 떠오름)`);
}

// 2 Tap any photo: every photo of that place lifts and the popup names it (the scroll lifts 제주's by itself).
export async function gather(page, { touch = true } = {}) {
  const tap = tapper(page, touch);
  await scrollToBeat(page, 1, 0.5, 300);
  await page.waitForTimeout(600);
  const read = () => page.evaluate(() => ({ text: document.querySelector("[data-found-text]")?.textContent ?? "", shown: Boolean(document.querySelector("[data-found]")?.classList.contains("is-shown")), lifted: document.querySelectorAll("[data-gather] .ts-ph.is-hit:not([hidden])").length }));
  const first = await read();
  const other = await page.evaluate(() => [...document.querySelectorAll("[data-gather] .ts-ph:not([hidden])")].findIndex((t) => !t.classList.contains("is-hit")));
  if (other < 0) return row(2, "모으기", false, "다른 곳 사진이 없음");
  await tap("[data-gather] .ts-ph:not([hidden])", other);
  await page.waitForTimeout(1600);
  const second = await read();
  return row(2, "모으기", first.text && second.text && second.text !== first.text && second.shown && second.lifted > 0, `처음 "${first.text}" → 누른 뒤 "${second.text}" (${second.lifted}장 떠오름)`);
}

// 4 Pick one more photo (scene 3, the safety line, has nothing to tap) and press 기록하기: the picked photos fold into one record card.
export async function save(page, { touch = true } = {}) {
  const tap = tapper(page, touch);
  await scrollToBeat(page, 3, 0.5, 300);
  await page.waitForTimeout(500);
  if (await page.evaluate(() => document.querySelector("[data-pick]")?.classList.contains("is-folded"))) { await tap("[data-pick]"); await page.waitForTimeout(500); }
  const pressed0 = await page.evaluate(() => document.querySelectorAll("[data-pick] .ts-ph")[1]?.getAttribute("aria-pressed"));
  await tap("[data-pick] .ts-ph", 1);
  await page.waitForTimeout(300);
  const pressed1 = await page.evaluate(() => document.querySelectorAll("[data-pick] .ts-ph")[1]?.getAttribute("aria-pressed"));
  await tap("[data-save]");
  await page.waitForTimeout(1300);
  const s = await page.evaluate(() => ({ folded: document.querySelector("[data-pick]")?.classList.contains("is-folded"), button: document.querySelector("[data-save]")?.textContent.trim(), card: document.querySelector("[data-rec-text]")?.textContent.trim() }));
  return row(4, "기록", pressed0 !== pressed1 && s.folded && s.button === "기록했어요" && /\d+장/.test(s.card ?? ""), `고르기 ${pressed0}→${pressed1}, 접힘 ${s.folded}, 버튼 "${s.button}", 카드 "${s.card}"`);
}

// 5 Tap the map: it clears; tap again: every visited 시·도 fills.
export async function mapToggle(page, { touch = true } = {}) {
  const tap = tapper(page, touch);
  await scrollToBeat(page, 4, 0.5, 300);
  await page.waitForTimeout(500);
  const state = () => page.evaluate(() => { const r = document.querySelector("[data-regions]"); return { on: r?.querySelectorAll(".ts-pv.is-on").length ?? 0, all: r?.querySelectorAll(".ts-pv").length ?? 0, pressed: r?.getAttribute("aria-pressed") }; });
  const a = await state();
  await tap("[data-regions]");
  await page.waitForTimeout(500);
  const b = await state();
  await tap("[data-regions]");
  await page.waitForTimeout(500);
  const c = await state();
  return row(5, "지도", a.on > 0 && b.on === 0 && c.on === c.all && c.pressed === "true", `칠한 시·도 ${a.on} → 지움 ${b.on} → 다시 칠함 ${c.on}/${c.all}`);
}

// 6 At the start of the globe scene (the globe still in the phone) a sideways drag turns the globe: its pixels change
// with the drag, stay put while the finger (or mouse) rests, and the page has not moved. On a phone an up-and-down
// drag is left to the page: the browser cancels the pointer and scrolls.
export async function globeDrag(page, { touch = true } = {}) {
  await scrollToBeat(page, 5, 0.02, 300);
  await page.waitForTimeout(500);
  const c = await centerOf(page, "[data-globe]");
  if (!c) return row(6, "지구본", false, "지구본 없음");
  const [gx, gy] = c;
  const sample = () => page.evaluate(() => { const cv = document.querySelector("[data-globe]"); const d = cv.getContext("2d").getImageData(0, 0, cv.width, cv.height).data; const out = []; for (let i = 0; i < d.length; i += 4 * 97) out.push(d[i], d[i + 1], d[i + 2]); return out; });
  const diff = (a, b) => { let s = 0; for (let i = 0; i < a.length; i++) s += Math.abs(a[i] - b[i]); return s / a.length; };
  await page.evaluate(() => { window.__gateCancel = 0; document.querySelector("[data-globe]").addEventListener("pointercancel", () => { window.__gateCancel++; }); });
  const before = await sample();
  await page.waitForTimeout(300);
  const idle = diff(before, await sample());
  const scroll0 = await page.evaluate(() => scrollY);
  const heldStill = async () => { await page.waitForTimeout(250); const s1 = await sample(); await page.waitForTimeout(300); return diff(s1, await sample()); };
  if (touch) {
    const cdp = await page.context().newCDPSession(page);
    await cdp.send("Input.dispatchTouchEvent", { type: "touchStart", touchPoints: [{ x: gx - 60, y: gy }] });
    for (let k = 1; k <= 10; k++) { await cdp.send("Input.dispatchTouchEvent", { type: "touchMove", touchPoints: [{ x: gx - 60 + k * 12, y: gy + k * 0.5 }] }); await page.waitForTimeout(16); }
    const held = await heldStill();
    const turned = diff(before, await sample());
    await cdp.send("Input.dispatchTouchEvent", { type: "touchEnd", touchPoints: [] });
    const scroll1 = await page.evaluate(() => scrollY);
    await page.waitForTimeout(200);
    await page.evaluate(() => { window.__gateCancel = 0; });
    await cdp.send("Input.dispatchTouchEvent", { type: "touchStart", touchPoints: [{ x: gx, y: gy + 60 }] });
    for (let k = 1; k <= 12; k++) { await cdp.send("Input.dispatchTouchEvent", { type: "touchMove", touchPoints: [{ x: gx, y: gy + 60 - k * 12 }] }); await page.waitForTimeout(16); }
    await cdp.send("Input.dispatchTouchEvent", { type: "touchEnd", touchPoints: [] });
    await page.waitForTimeout(500);
    const scroll2 = await page.evaluate(() => scrollY);
    const cancelled = await page.evaluate(() => window.__gateCancel);
    await cdp.detach().catch(() => {});
    const ok = idle < 0.2 && held < 0.2 && turned > 0.5 && Math.abs(scroll1 - scroll0) < 2 && scroll2 - scroll1 > 40 && cancelled > 0;
    return row(6, "지구본", ok, `놓아두면 가만히 ${idle < 0.2} (변화 ${idle.toFixed(2)}); 옆으로 끌어 잡고 있기: 끌린 만큼 돌아감 ${turned > 0.5}, 잡고 있는 동안 가만히 ${held < 0.2} (변화 ${held.toFixed(2)}), 페이지 ${(scroll1 - scroll0).toFixed(0)}px 이동; 위아래 끌기: 페이지 ${(scroll2 - scroll1).toFixed(0)}px 이동, 브라우저가 스크롤로 가져감 ${cancelled > 0}`);
  }
  await page.mouse.move(gx - 60, gy);
  await page.mouse.down();
  for (let k = 1; k <= 10; k++) { await page.mouse.move(gx - 60 + k * 12, gy, { steps: 1 }); await page.waitForTimeout(16); }
  const held = await heldStill();
  const turned = diff(before, await sample());
  await page.mouse.up();
  const scroll1 = await page.evaluate(() => scrollY);
  return row(6, "지구본", idle < 0.2 && held < 0.2 && turned > 0.5 && Math.abs(scroll1 - scroll0) < 2, `놓아두면 가만히 ${idle < 0.2} (변화 ${idle.toFixed(2)}); 마우스로 끌기: 돌아감 ${turned > 0.5}, 잡고 있는 동안 가만히 ${held < 0.2} (변화 ${held.toFixed(2)}), 페이지 ${(scroll1 - scroll0).toFixed(0)}px 이동`);
}

// 6 The share video does not play by itself (10-28); a year button plays from that year; a tap on the video pauses it.
export async function yearPick(page, { touch = true } = {}) {
  const tap = tapper(page, touch);
  await scrollToBeat(page, 6, 0.5, 300);
  await page.waitForTimeout(700);
  const read = () => page.evaluate(() => {
    const v = document.querySelector("[data-share-video]");
    if (!v) return null;
    const buttons = [...document.querySelectorAll("[data-share-years] button")];
    return { src: (v.currentSrc || "").split("/").pop().slice(0, 30), paused: v.paused, t: +v.currentTime.toFixed(2), ready: v.readyState, error: v.error?.code ?? 0,
      canPlay: Boolean(v.canPlayType("video/webm") || v.canPlayType("video/mp4")), on: buttons.find((b) => b.getAttribute("aria-pressed") === "true")?.textContent ?? "", at: buttons.map((b) => +b.dataset.at), boxPaused: document.querySelector("[data-share-box]")?.classList.contains("is-paused") };
  });
  const v0 = await read();
  if (!v0) return row(7, "영상", true, "공유 장면 꺼짐 (TECH_SHARE.enabled=false)");
  await page.waitForTimeout(1000);
  const v0b = await read();
  const still = v0b.paused && v0b.t === v0.t;
  await tap("[data-share-years] button", 2);
  await page.waitForTimeout(1200);
  const v1 = await read();
  await tap("[data-share-video]");
  await page.waitForTimeout(400);
  const v2 = await read();
  const seeked = v1.t >= (v1.at[2] ?? 0) - 0.3;
  const ok = still && v1.on === "2025" && seeked && !v1.paused && v2.paused && v2.boxPaused;
  return row(7, "영상", ok, `스스로 재생 안 함 ${still}${v0.canPlay ? "" : " (이 브라우저는 코덱 없음)"}, 2025 누름 → 선택 "${v1.on}", 위치 ${v1.t}s (기대 ≥ ${v1.at[2]}s), 재생 ${!v1.paused}, 영상 누름 → 멈춤 ${v2.paused}`);
}

export const SCENARIOS = [placePick, gather, save, mapToggle, globeDrag, yearPick];

export async function runAllScenes(page, opts = {}) {
  const out = [];
  for (const fn of SCENARIOS) {
    try { out.push(await fn(page, opts)); }
    catch (e) { out.push(row(SCENARIOS.indexOf(fn) + 1, fn.name, false, `오류: ${e.message}`)); }
  }
  return out;
}
