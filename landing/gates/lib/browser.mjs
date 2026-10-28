// Playwright helpers shared by the gate checks. The section's scroll engine picks the active scene by a
// focus line (the middle of the words band: under the phone on phones, the viewport middle on desktop), so
// the helpers scroll by that line, never by element top.
import { chromium } from "playwright";

export const VIEWPORTS = Object.freeze({
  phoneS: { width: 360, height: 740, mobile: true },
  phone: { width: 390, height: 844, mobile: true },
  reflow: { width: 320, height: 568, mobile: true },
  tablet: { width: 768, height: 1024, mobile: true },
  desktop: { width: 1440, height: 900, mobile: false },
});

export async function launch({ width = 390, height = 844, mobile = true, dark = false, reduce = false, touch } = {}) {
  const browser = await chromium.launch({ executablePath: process.env.GATES_CHROME || undefined });
  const context = await browser.newContext({ viewport: { width, height }, deviceScaleFactor: mobile ? 2 : 1, isMobile: mobile, hasTouch: touch ?? mobile,
    colorScheme: dark ? "dark" : "light", reducedMotion: reduce ? "reduce" : "no-preference", locale: "ko-KR" });
  const page = await context.newPage();
  const errors = [];
  const failed = [];
  page.on("console", (m) => { if (m.type() === "error") errors.push(m.text()); });
  page.on("pageerror", (e) => errors.push(e.message));
  page.on("response", (res) => { if (res.status() >= 400) failed.push(`${res.status()} ${res.url()}`); });
  return { browser, context, page, errors, failed, close: () => browser.close() };
}

export async function gotoSection(page, url, { theme } = {}) {
  await page.goto(url, { waitUntil: "load" });
  if (theme) await page.evaluate((t) => document.documentElement.setAttribute("data-theme", t), theme);
  await page.locator("#how").waitFor();
  await page.evaluate(() => document.getElementById("how").scrollIntoView());
  await page.waitForSelector(".ts.is-live", { timeout: 20000 }).catch(() => {});
  await page.waitForTimeout(700);
}

export const isNarrow = (page) => page.evaluate(() => matchMedia("(max-width: 760px)").matches);

/** Scrolls so the focus line sits at fraction t of beat i, and lets the engine settle. While the stage is still
 *  sliding in (not yet pinned) the beats and the focus line move together, so the first beat's middle is "at" the
 *  line at every scroll of the approach; the pinned position is taken as that point, the first where the words
 *  band is fully on screen. */
export async function scrollToBeat(page, i, t = 0.5, settle = 450) {
  for (let k = 0; k < 3; k++) {
    await page.evaluate(([i, t]) => {
      const r = document.querySelector(`[data-beat="${i}"]`).getBoundingClientRect();
      const narrow = matchMedia("(max-width: 760px)").matches;
      let f = innerHeight / 2;
      if (narrow) { const c = document.querySelector(".ts-captions").getBoundingClientRect(); f = c.top + c.height / 2; }
      window.scrollBy(0, r.top + r.height * t - f);
      const top = document.querySelector(".ts-stage").getBoundingClientRect().top;
      if (top > 0) window.scrollBy(0, top);
    }, [i, t]);
    await page.waitForTimeout(120);
  }
  await page.waitForTimeout(settle);
}

export const isTripBeat = (page, i) => page.evaluate((i) => Boolean(document.querySelector(`[data-beat="${i}"]`)?.classList.contains("is-trip")), i);
export const activeScene = (page) => page.evaluate(() => [...document.querySelectorAll(".ts-screen")].indexOf(document.querySelector(".ts-screen.is-on")));
export const beatCount = (page) => page.evaluate(() => document.querySelectorAll("[data-beat]").length);

/** The section's scroll range in document pixels: top of the first beat to bottom of the last. */
export const sectionRange = (page) => page.evaluate(() => {
  const beats = [...document.querySelectorAll("[data-beat]")];
  const first = beats[0].getBoundingClientRect(), last = beats.at(-1).getBoundingClientRect();
  return { start: scrollY + first.top, end: scrollY + last.bottom, vh: innerHeight };
});

/** Moves the page like a flick: `speed` px/s in 16 ms steps, then stops at `target`. */
export async function flickTo(page, target, speed = 2000) {
  const from = await page.evaluate(() => scrollY);
  const step = (speed / 1000) * 16 * Math.sign(target - from);
  let y = from;
  while ((step > 0 && y + step < target) || (step < 0 && y + step > target)) { y += step; await page.evaluate((y) => window.scrollTo(0, y), y); await page.waitForTimeout(16); }
  await page.evaluate((y) => window.scrollTo(0, y), target);
}

/** Visible text blocks inside `rootSel` (elements that own text, on screen, not transparent), with where they sit. */
export const visibleTextBlocks = (page, rootSel = ".ts") => page.evaluate((rootSel) => {
  const root = document.querySelector(rootSel);
  const out = [];
  const seen = new Set();
  for (const el of root.querySelectorAll("*")) {
    let own = [...el.childNodes].filter((n) => n.nodeType === 3 && n.textContent.trim()).map((n) => n.textContent.trim()).join(" ");
    // a word spelled out in aria-hidden spans (the hand-written label) is one block, read from its visually hidden
    // text (or an aria-label); the visually hidden span itself is not a block on screen
    if (el.classList.contains("sr-only")) continue;
    if (!own && el.children.length && [...el.children].every((c) => c.getAttribute("aria-hidden") === "true" || c.classList.contains("sr-only"))) own = el.querySelector(":scope > .sr-only")?.textContent.trim() || el.getAttribute("aria-label") || "";
    if (!own) continue;
    let node = el, opacity = 1, hidden = false;
    while (node && node !== document.body) {
      const cs = getComputedStyle(node);
      if (cs.display === "none" || cs.visibility === "hidden") { hidden = true; break; }
      opacity *= parseFloat(cs.opacity);
      node = node.parentElement;
    }
    if (hidden || opacity < 0.5) continue;
    const r = el.getBoundingClientRect();
    if (r.width === 0 || r.height === 0 || r.bottom <= 0 || r.top >= innerHeight) continue;
    const cs = getComputedStyle(el);
    const key = `${el.tagName}:${own}:${Math.round(r.top)}`;
    if (seen.has(key)) continue;
    seen.add(key);
    out.push({ text: own, tag: el.tagName.toLowerCase(), className: typeof el.className === "string" ? el.className : "", fontSize: parseFloat(cs.fontSize),
      fontWeight: cs.fontWeight, rect: { top: r.top, left: r.left, width: r.width, height: r.height }, inPhone: Boolean(el.closest("[data-phone]")),
      inStage: Boolean(el.closest(".ts-stage")), aria: el.getAttribute("aria-hidden") === "true" });
  }
  return out;
}, rootSel);

/** Interactive elements in the active scene with their painted size (the clickable box, not the glyph). */
export const touchTargets = (page) => page.evaluate(() => {
  const sels = "button, a[href], [role=button], [tabindex='0'], input, video, canvas[data-tl-globe], [data-regions], .ts-ph, .ts-save, .ts-years button";
  const out = [];
  for (const el of document.querySelectorAll(`#how ${sels}`)) {
    if (el.closest(".ts-screen") && !el.closest(".ts-screen.is-on")) continue;
    let node = el, opacity = 1, hidden = false;
    while (node && node !== document.body) { const cs = getComputedStyle(node); if (cs.display === "none" || cs.visibility === "hidden") { hidden = true; break; } opacity *= parseFloat(cs.opacity); node = node.parentElement; }
    if (hidden || opacity < 0.5) continue;
    const r = el.getBoundingClientRect();
    if (r.width === 0 || r.height === 0 || r.bottom <= 0 || r.top >= innerHeight) continue;
    if (getComputedStyle(el).pointerEvents === "none") continue;
    out.push({ tag: el.tagName.toLowerCase(), className: typeof el.className === "string" ? el.className : "", label: (el.getAttribute("aria-label") || el.textContent || "").trim().slice(0, 40), width: r.width, height: r.height, top: r.top, left: r.left });
  }
  return out;
});

export const rectOf = (page, sel) => page.evaluate((sel) => { const el = document.querySelector(sel); if (!el) return null; const r = el.getBoundingClientRect(); return { top: r.top, left: r.left, right: r.right, bottom: r.bottom, width: r.width, height: r.height }; }, sel);
export const overlaps = (a, b) => a && b && a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom;
export const within = (r, w, h) => r && r.top >= 0 && r.left >= 0 && r.bottom <= h && r.right <= w;
