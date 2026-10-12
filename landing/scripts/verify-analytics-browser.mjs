// Isolated production bundles, a fake GA ID and intercepted requests: no production traffic.
// node scripts/verify-analytics-browser.mjs <absolute path to playwright/index.mjs>
import assert from "node:assert/strict";
import { mkdtemp, readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { build } from "vite";
import { getHowStoryProgress, getHowStoryScene, isHowStoryHold } from "../src/howStory.js";

const root = fileURLToPath(new URL("../", import.meta.url));
const output = process.argv[3] ? path.resolve(process.argv[3]) : await mkdtemp(path.join(root, "../../landing-analytics-qa-"));
const { chromium } = await import(pathToFileURL(path.resolve(process.argv[2])).href);
process.env.VITE_GA_MEASUREMENT_ID = "G-TEST123";
process.env.VITE_POSTHOG_KEY = "";
process.env.VITE_POSTHOG_HOST = "";
for (const surface of process.argv[3] ? [] : ["landing", "recap"]) {
  const appRoot = surface === "landing" ? root : path.join(root, "travel-map-campaign");
  await build({ root: appRoot, configFile: path.join(appRoot, "vite.config.mjs"),
    base: surface === "recap" ? "/recap/" : "/", logLevel: "warn",
    build: { outDir: path.join(output, surface) },
  });
}
const browser = await chromium.launch({ channel: process.env.PLAYWRIGHT_CHROMIUM_CHANNEL || undefined, headless: true, args: ["--use-angle=swiftshader", "--enable-unsafe-swiftshader"] });
const mime = { ".html": "text/html", ".js": "application/javascript", ".css": "text/css", ".json": "application/json", ".svg": "image/svg+xml", ".jpg": "image/jpeg", ".png": "image/png", ".woff2": "font/woff2" };
const findings = [];
try {
  for (const mobile of [false, true]) {
    const context = await browser.newContext({ viewport: mobile ? { width: 390, height: 844 } : { width: 1440, height: 1000 }, isMobile: mobile, hasTouch: mobile, reducedMotion: "reduce", serviceWorkers: "block" });
    await context.route("**/*", async (route) => {
      const url = new URL(route.request().url());
      if (url.hostname === "www.googletagmanager.com") return route.fulfill({ contentType: "application/javascript", body: "/* GA network disabled for QA */" });
      if (url.hostname !== "map-mory.com") return route.abort();
      const recap = url.pathname.startsWith("/recap/");
      const relative = decodeURIComponent(recap ? url.pathname.slice(7) : url.pathname.slice(1)) || "index.html";
      const appDir = path.join(output, recap ? "recap" : "landing");
      const file = path.resolve(appDir, relative);
      if (!file.startsWith(appDir + path.sep)) return route.abort();
      try { return route.fulfill({ contentType: mime[path.extname(file)] ?? "application/octet-stream", body: await readFile(file) }); }
      catch { return route.fulfill({ status: 404, body: "Missing fixture asset" }); }
    });
    const page = await context.newPage();
    page.setDefaultTimeout(20000);
    page.setDefaultNavigationTimeout(20000);
    const errors = [];
    page.on("pageerror", (error) => errors.push(error.message));
    const events = () => page.evaluate(() => (window.dataLayer ?? []).map((args) => [...args]).filter(([command]) => command === "event"));
    const waitForEvent = (eventName, experienceType, count = 1) => page.waitForFunction(([name, type, minimum]) => [...(window.dataLayer ?? [])]
      .filter((args) => args[0] === "event" && args[1] === name && args[2]?.experience_type === type).length >= minimum, [eventName, experienceType, count]);
    const storyEvents = async (eventName) => (await events()).filter(([, name, props]) => name === eventName && props.experience_type === "how_story");
    const storyExperienceEvents = async () => (await events()).filter(([, name, props]) => ["experience_view", "experience_start", "experience_end", "memory_open"].includes(name) && props.experience_type === "how_story");
    await page.goto("https://map-mory.com/?internal=1");
    await page.locator(".site-header").waitFor();
    const menu = page.locator(".header-store-menu");
    const trigger = page.locator(".header-store-trigger");
    // React attaches the Escape and outside-click listeners only after the async toggle event.
    // The flag is polled with waitForFunction so a menu that never opens fails on the default timeout instead of hanging.
    const openStoreMenu = async () => {
      await menu.evaluate((node) => {
        delete node.dataset.qaToggled;
        node.addEventListener("toggle", () => setTimeout(() => { node.dataset.qaToggled = "1"; }), { once: true });
      });
      await trigger.click();
      await page.waitForFunction(() => document.querySelector(".header-store-menu").dataset.qaToggled === "1");
      await menu.locator('[role="group"]').waitFor({ state: "visible" });
    };
    await openStoreMenu();
    await page.keyboard.press("Escape");
    await page.waitForFunction(() => !document.querySelector(".header-store-menu").open);
    assert.equal(await trigger.evaluate((element) => element === document.activeElement), true);
    await openStoreMenu();
    await page.locator(".site-header .brand").click();
    await page.waitForFunction(() => !document.querySelector(".header-store-menu").open);
    assert.equal((await events()).filter(([, name]) => ["experience_start", "memory_open"].includes(name)).length, 0);
    for (const [label, store] of [["App Store", "app_store"], ["Google Play", "google_play"]]) {
      await page.locator(".header-store-trigger").click();
      await page.locator(".header-store-popover").getByRole("link", { name: label, exact: true }).click();
      for (const popup of context.pages()) if (popup !== page) await popup.close();
      await page.bringToFront();
      const event = (await events()).filter(([, name]) => name === "download_click").at(-1);
      assert.equal(event[2].store, store);
      assert.equal(event[2].cta_placement, "header");
      assert.equal(event[2].traffic_type, "internal");
    }
    assert.equal((await events()).filter(([, name]) => name === "download_click").length, 2);
    console.log("Header stores passed", { mobile });
    // Reduced motion gets the static list: one passive view, never a scene reach, never experience_*.
    const story = page.locator(".how-story-track");
    assert.equal(await story.getAttribute("data-mode"), "static");
    await story.evaluate((node) => window.scrollTo({ top: window.scrollY + node.getBoundingClientRect().top - 100, behavior: "instant" }));
    await waitForEvent("how_story_view", "how_story");
    const staticViews = await storyEvents("how_story_view");
    assert.equal(staticViews.length, 1);
    assert.equal(staticViews[0][2].story_mode, "static");
    await page.waitForTimeout(1200);
    assert.equal((await storyEvents("how_story_scene")).length, 0);
    assert.equal((await storyExperienceEvents()).length, 0);
    console.log("How story (static) passed", { mobile });
    // Mobile hides the header nav, so it enters the globe from the how-it-works link instead.
    if (mobile) await page.locator(".how-experience-link").click();
    else await page.getByRole("link", { name: "지구본 체험", exact: true }).click();
    await waitForEvent("experience_view", "globe");
    await page.getByLabel("기억이 있는 나라 바로 선택").getByRole("button", { name: "일본", exact: true }).click();
    await page.waitForFunction(() => [...(window.dataLayer ?? [])].some((args) => args[1] === "memory_open"));
    const globe = (await events()).filter(([, , props]) => props.experience_type === "globe").map(([, name]) => name);
    console.log("Globe events", { mobile, globe });
    assert.equal(globe.filter((name) => name === "experience_view").length, 1);
    assert.equal(globe.filter((name) => name === "experience_start").length, 1);
    assert.ok(globe.indexOf("experience_view") < globe.indexOf("experience_start"));
    assert.ok(globe.indexOf("experience_start") < globe.indexOf("memory_open"));
    if (!mobile) await page.locator(".site-header nav").getByRole("link", { name: "사용 방법", exact: true }).click();
    assert.deepEqual((await storyEvents("experience_cta_click")).map(([, , props]) => props.cta_placement), mobile ? [] : ["header_nav"]);
    if (mobile) await page.goBack();
    if (mobile) assert.ok((await events()).some(([, name, props]) => name === "experience_cta_click" && props.cta_placement === "how_section"));
    await page.locator("#download").getByRole("link", { name: "App Store", exact: true }).click();
    for (const popup of context.pages()) if (popup !== page) await popup.close();
    await page.bringToFront();
    const storeClicks = (await events()).filter(([, name]) => name === "download_click");
    assert.equal(storeClicks.length, 3);
    assert.equal(storeClicks.at(-1)[2].cta_placement, "final");
    assert.ok(storeClicks.every(([, , props]) => props.experience_type === undefined));
    console.log("Final store passed", { mobile });
    const landingEvents = await events();
    assert.equal(landingEvents.some(([, name, props]) => name === "memory_open" && props.experience_type === "how_story"), false);
    assert.equal((await storyEvents("how_story_view")).length, 1);
    assert.equal(JSON.stringify(landingEvents).includes("/assets/photos"), false);
    await page.goto("https://map-mory.com/recap/?internal=1");
    await page.getByRole("button", { name: "사진 없이 샘플 결과 먼저 보기" }).click();
    await page.getByRole("button", { name: "내 여행 영상 보기" }).click();
    // Inject a download failure after real video rendering, not a fabricated UI error.
    await page.evaluate(() => {
      const originalClick = HTMLAnchorElement.prototype.click;
      HTMLAnchorElement.prototype.click = function () {
        if (this.download) throw new Error("QA download failure: retry is available");
        return originalClick.call(this);
      };
    });
    await page.getByRole("button", { name: "영상 저장", exact: true }).click();
    await page.getByText("QA download failure: retry is available", { exact: true }).waitFor({ timeout: 60000 });
    assert.equal(await page.getByRole("button", { name: "영상 저장", exact: true }).isEnabled(), true);
    assert.equal((await events()).filter(([, name]) => name === "travel_map_video_saved").length, 0);
    const failedExport = (await events()).filter(([, name]) => name === "travel_map_export_failed");
    assert.equal(failedExport.length, 1);
    assert.equal(failedExport[0][2].format, "video");
    assert.equal(failedExport[0][2].error_type, "export_failed");
    await page.getByRole("button", { name: "Mapmory 앱 알아보기" }).click();
    await page.getByRole("link", { name: "Google Play에서 바로 다운로드" }).click();
    const recapEvents = await events();
    for (const eventName of ["travel_map_demo_start", "travel_map_processing_complete", "travel_map_recap_view", "travel_map_app_bridge_click", "travel_map_demand_view", "download_click"]) {
      const matches = recapEvents.filter(([, name]) => name === eventName);
      assert.equal(matches.length, 1, eventName);
      assert.equal(matches[0][2].journey_source, "demo");
      assert.equal(matches[0][2].surface, "recap");
    }
    await page.goto("https://map-mory.com/recap/?internal=1");
    await page.locator('input[type="file"]').setInputFiles({ name: "no-gps.png", mimeType: "image/png", buffer: Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLbtAAAAABJRU5ErkJggg==", "base64") });
    await page.waitForFunction(() => [...(window.dataLayer ?? [])].some((args) => args[1] === "travel_map_photo_analysis_empty"));
    assert.equal((await events()).find(([, name]) => name === "travel_map_photo_analysis_empty")[2].journey_source, "photos");
    assert.equal(errors.length, 0, errors.join("\n"));
    findings.push({ mobile, passed: true, covered: "header stores/Escape/outside dismissal, hero exclusion, reduced-motion how_story static view without scene reach or experience_*, globe-only view/start/open, how-section globe link, header how_story link (desktop), final store without experience context, no photo paths in landing events, recap download failure/retry, demo/store and photos/no-GPS" });
    await context.close();
  }
  // Scroll mode: a reader who stops in each scene's hold sends one view and scenes 1-5 once, in order.
  for (const mobile of [false, true]) {
    const context = await browser.newContext({ viewport: mobile ? { width: 390, height: 844 } : { width: 1440, height: 900 }, isMobile: mobile, hasTouch: mobile, reducedMotion: "no-preference", serviceWorkers: "block" });
    await context.route("**/*", async (route) => {
      const url = new URL(route.request().url());
      if (url.hostname === "www.googletagmanager.com") return route.fulfill({ contentType: "application/javascript", body: "/* GA network disabled for QA */" });
      if (url.hostname !== "map-mory.com") return route.abort();
      const appDir = path.join(output, "landing");
      const file = path.resolve(appDir, decodeURIComponent(url.pathname.slice(1)) || "index.html");
      if (!file.startsWith(appDir + path.sep)) return route.abort();
      try { return route.fulfill({ contentType: mime[path.extname(file)] ?? "application/octet-stream", body: await readFile(file) }); }
      catch { return route.fulfill({ status: 404, body: "Missing fixture asset" }); }
    });
    const page = await context.newPage();
    page.setDefaultTimeout(20000);
    const errors = [];
    page.on("pageerror", (error) => errors.push(error.message));
    const storyEvents = (eventName) => page.evaluate((name) => (window.dataLayer ?? []).map((args) => [...args])
      .filter(([command, event, props]) => command === "event" && event === name && props?.experience_type === "how_story"), eventName);
    await page.goto("https://map-mory.com/?internal=1");
    await page.locator(".how-story-track").waitFor();
    assert.equal(await page.locator(".how-story-track").getAttribute("data-mode"), "scroll");
    await page.mouse.move(720, 450);
    const settle = () => page.evaluate(() => new Promise((resolve) => {
      let last = -1;
      const check = () => (window.scrollY === last ? resolve(window.scrollY) : (last = window.scrollY, requestAnimationFrame(() => requestAnimationFrame(check))));
      check();
    }));
    const progress = () => page.evaluate(() => {
      const track = document.querySelector(".how-story-track");
      const frame = document.querySelector(".how-story-frame");
      const rect = track.getBoundingClientRect();
      return { trackTop: rect.top, trackHeight: rect.height, frameHeight: frame.offsetHeight, stickyTop: parseFloat(getComputedStyle(frame).top) || 0, end: rect.bottom };
    });
    // One 120px step at a time, like a wheel notch or a short swipe; stops 900 ms once in each scene's hold.
    const pass = async (direction) => {
      const paused = new Set();
      for (let step = 0; step < 200; step += 1) {
        if (mobile) await page.evaluate((dy) => window.scrollBy(0, dy), 120 * direction);
        else await page.mouse.wheel(0, 120 * direction);
        const y = await settle();
        await page.waitForTimeout(60);
        const measure = await progress();
        const p = getHowStoryProgress(measure);
        const { index } = getHowStoryScene(p);
        const inView = measure.trackTop < 900 && measure.end > 0;
        if (inView && isHowStoryHold(p) && !paused.has(index)) {
          paused.add(index);
          await page.waitForTimeout(900);
        }
        if (direction > 0 && measure.end < 0) break;
        if (direction < 0 && y === 0) break;
      }
      return [...paused].sort();
    };
    assert.deepEqual(await pass(1), [0, 1, 2, 3, 4]);
    const sceneIndexes = async () => (await storyEvents("how_story_scene")).map(([, , props]) => props.scene_index);
    assert.deepEqual(await sceneIndexes(), [1, 2, 3, 4, 5]);
    const views = await storyEvents("how_story_view");
    assert.equal(views.length, 1);
    assert.equal(views[0][2].story_mode, "scroll");
    // Reading it again, up and back down, never re-sends.
    await pass(-1);
    await pass(1);
    assert.deepEqual(await sceneIndexes(), [1, 2, 3, 4, 5]);
    assert.equal((await storyEvents("how_story_view")).length, 1);
    const experienceEvents = await page.evaluate(() => (window.dataLayer ?? []).map((args) => [...args])
      .filter(([command, name, props]) => command === "event" && /^(experience_(view|start|end)|memory_open)$/.test(name) && props?.experience_type === "how_story"));
    assert.equal(experienceEvents.length, 0);
    if (mobile) {
      // The how-section link is still the mobile entry to the globe.
      await page.locator(".how-experience-link").click();
      await page.waitForFunction(() => (window.dataLayer ?? []).some((args) => args[0] === "event" && args[1] === "experience_view" && args[2]?.experience_type === "globe"));
      assert.ok((await page.evaluate(() => (window.dataLayer ?? []).map((args) => [...args]))).some(([, name, props]) => name === "experience_cta_click" && props?.cta_placement === "how_section"));
    }
    // A reload that lands straight in a hold (restored scroll) starts the 700 ms scene timer and the 1 s view
    // timer together; the view must still reach the dataLayer before the scene.
    await page.goto("https://map-mory.com/?internal=1");
    await page.locator(".how-story-track").waitFor();
    const holdY = (target) => page.evaluate((p) => {
      const track = document.querySelector(".how-story-track");
      const frame = document.querySelector(".how-story-frame");
      const rect = track.getBoundingClientRect();
      const stickyTop = parseFloat(getComputedStyle(frame).top) || 0;
      return Math.round(window.scrollY + rect.top - stickyTop + p * (rect.height - frame.offsetHeight));
    }, target);
    await page.evaluate((y) => window.scrollTo({ top: y, behavior: "instant" }), await holdY(0.345));
    await settle();
    await page.reload();
    await page.locator(".how-story-track").waitFor();
    await settle();
    if (!isHowStoryHold(getHowStoryProgress(await progress()))) {
      // Restoration did not land in the hold: jump there in one step, which is the same race.
      await page.evaluate((y) => window.scrollTo({ top: y, behavior: "instant" }), await holdY(0.345));
      await settle();
    }
    const landed = getHowStoryProgress(await progress());
    assert.ok(isHowStoryHold(landed) && getHowStoryScene(landed).index === 1, `landed at p=${landed}`);
    await page.waitForFunction(() => (window.dataLayer ?? []).some((args) => args[0] === "event" && args[1] === "how_story_scene"));
    await page.waitForTimeout(600);
    const order = await page.evaluate(() => (window.dataLayer ?? []).map((args) => [...args])
      .filter(([command, name, props]) => command === "event" && /^how_story_(view|scene)$/.test(name) && props?.experience_type === "how_story")
      .map(([, name, props]) => (name === "how_story_scene" ? `scene:${props.scene_index}` : `view:${props.story_mode}`)));
    assert.deepEqual(order, ["view:scroll", "scene:2"]);
    assert.equal(errors.length, 0, errors.join("\n"));
    findings.push({ mobile, passed: true, covered: "scroll mode: one how_story_view (scroll), how_story_scene 1-5 in order once each with a 900 ms stop per hold, no re-send after scrolling up and down again, no experience_* for how_story, reload into the scene-2 hold sends the view before the scene" + (mobile ? ", how-section link to the globe" : "") });
    console.log("How story (scroll) passed", { mobile });
    await context.close();
  }
  console.log(JSON.stringify({ output, findings, productionAnalyticsRequests: 0 }, null, 2));
} finally { await browser.close(); }
