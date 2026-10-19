// Isolated production bundles, a fake GA ID and intercepted requests: no production traffic.
// node scripts/verify-analytics-browser.mjs <absolute path to playwright/index.mjs>
// The #how stories (#roll, #rec) are read through the hooks listed in how-story-browser-checks.mjs.
import assert from "node:assert/strict";
import { mkdtemp, readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { build } from "vite";
import { checkScrollStory, checkStaticStory, dataLayerEvents, storyEvents, storyExperienceEvents } from "./how-story-browser-checks.mjs";

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
const browser = await chromium.launch({ channel: process.env.PLAYWRIGHT_CHROMIUM_CHANNEL || undefined, executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE || undefined, headless: true, args: ["--use-angle=swiftshader", "--enable-unsafe-swiftshader"] });
const mime = { ".html": "text/html", ".js": "application/javascript", ".css": "text/css", ".json": "application/json", ".svg": "image/svg+xml", ".jpg": "image/jpeg", ".png": "image/png", ".webp": "image/webp", ".woff2": "font/woff2" };
const serve = async (route) => {
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
};
const findings = [];
try {
  for (const mobile of [false, true]) {
    const context = await browser.newContext({ viewport: mobile ? { width: 390, height: 844 } : { width: 1440, height: 1000 }, isMobile: mobile, hasTouch: mobile, reducedMotion: "reduce", serviceWorkers: "block" });
    await context.route("**/*", serve);
    const page = await context.newPage();
    page.setDefaultTimeout(20000);
    page.setDefaultNavigationTimeout(20000);
    const errors = [];
    page.on("pageerror", (error) => errors.push(error.message));
    const events = () => dataLayerEvents(page);
    const waitForEvent = (eventName, experienceType, count = 1) => page.waitForFunction(([name, type, minimum]) => [...(window.dataLayer ?? [])]
      .filter((args) => args[0] === "event" && args[1] === name && args[2]?.experience_type === type).length >= minimum, [eventName, experienceType, count]);
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
    // Reduced motion gets the static version: one view (static) and scenes 1-6 once each after a rest in view.
    await checkStaticStory(page);
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
    assert.deepEqual((await storyEvents(page, "experience_cta_click")).map(([, , props]) => props.cta_placement), mobile ? [] : ["header_nav"]);
    if (mobile) await page.goBack();
    if (mobile) assert.ok((await events()).some(([, name, props]) => name === "experience_cta_click" && props.experience_type === "globe" && props.cta_placement === "how_section"));
    await page.locator("#download").getByRole("link", { name: "아이폰 App Store", exact: true }).click();
    for (const popup of context.pages()) if (popup !== page) await popup.close();
    await page.bringToFront();
    const storeClicks = (await events()).filter(([, name]) => name === "download_click");
    assert.equal(storeClicks.length, 3);
    assert.equal(storeClicks.at(-1)[2].cta_placement, "final");
    assert.ok(storeClicks.every(([, , props]) => props.experience_type === undefined));
    console.log("Final store passed", { mobile });
    const landingEvents = await events();
    assert.equal((await storyExperienceEvents(page)).length, 0);
    assert.equal((await storyEvents(page, "how_story_view")).length, 1);
    assert.deepEqual((await storyEvents(page, "how_story_scene")).map(([, , props]) => props.scene_index).sort(), [1, 2, 3, 4, 5, 6]);
    assert.equal(landingEvents.some(([, name]) => /^how_play/.test(name)), false);
    assert.equal(JSON.stringify(landingEvents).includes("/assets/"), false);
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
    findings.push({ mobile, passed: true, covered: "header stores/Escape/outside dismissal, hero exclusion, reduced-motion how_story static view, static scenes 1-6 once each after a half-in-view rest (items jumped over not counted, revisits not re-sent), no experience_* for how_story, globe-only view/start/open, how-section globe link, header how_story link (desktop), final store without experience context, no asset paths in landing events, recap download failure/retry, demo/store and photos/no-GPS" });
    await context.close();
  }
  // Scroll mode: a fling past beats 1-5 counts none of them; a reader who rests on each beat sends one view
  // and each scene once; reading it again never re-sends; landing straight on a beat sends the view before the scene.
  for (const mobile of [false, true]) {
    const context = await browser.newContext({ viewport: mobile ? { width: 390, height: 844 } : { width: 1440, height: 900 }, isMobile: mobile, hasTouch: mobile, reducedMotion: "no-preference", serviceWorkers: "block" });
    await context.route("**/*", serve);
    const page = await context.newPage();
    page.setDefaultTimeout(20000);
    const errors = [];
    page.on("pageerror", (error) => errors.push(error.message));
    await page.goto("https://map-mory.com/?internal=1");
    await checkScrollStory(page, { reopen: () => page.goto("https://map-mory.com/?internal=1") });
    // The hero's own store buttons send download_click from "hero"; its chips send hero_demo_select only when tapped.
    await page.evaluate(() => window.scrollTo({ top: 0, behavior: "instant" }));
    await page.locator(".wall-hero").getByRole("button", { name: /^일본/ }).click();
    const chip = (await dataLayerEvents(page)).filter(([, name]) => name === "hero_demo_select");
    assert.deepEqual(chip.map(([, , props]) => [props.experience_type, props.demo_place]), [["hero_demo", "japan"]]);
    await page.locator(".wall-hero").getByRole("link", { name: "iOS 앱 받기", exact: true }).click();
    for (const popup of context.pages()) if (popup !== page) await popup.close();
    await page.bringToFront();
    const heroClick = (await dataLayerEvents(page)).filter(([, name]) => name === "download_click").at(-1);
    assert.deepEqual([heroClick[2].cta_placement, heroClick[2].store, heroClick[2].experience_type], ["hero", "app_store", undefined]);
    if (mobile) {
      // The how-section link is still the mobile entry to the globe.
      await page.locator(".how-experience-link").click();
      await page.waitForFunction(() => (window.dataLayer ?? []).some((args) => args[0] === "event" && args[1] === "experience_view" && args[2]?.experience_type === "globe"));
      assert.ok((await dataLayerEvents(page)).some(([, name, props]) => name === "experience_cta_click" && props?.experience_type === "globe" && props?.cta_placement === "how_section"));
    }
    assert.equal(errors.length, 0, errors.join("\n"));
    findings.push({ mobile, passed: true, covered: "scroll mode: a fling past beats 1-5 counts none and 6 at rest sends view (scroll) then scene 6; resting on each beat sends 5-1 once each, a second read re-sends nothing; no experience_* for how_story; landing on 3 sends the view before the scene; hero chip tap sends hero_demo_select (japan) once, the auto-pick none; hero App Store sends download_click cta_placement=hero" + (mobile ? "; how-section link to the globe" : "") });
    console.log("How story (scroll) passed", { mobile });
    await context.close();
  }
  console.log(JSON.stringify({ output, findings, productionAnalyticsRequests: 0 }, null, 2));
} finally { await browser.close(); }
