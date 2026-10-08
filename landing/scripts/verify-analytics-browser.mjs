// Isolated production bundles, a fake GA ID and intercepted requests: no production traffic.
// node scripts/verify-analytics-browser.mjs <absolute path to playwright/index.mjs>
import assert from "node:assert/strict";
import { mkdtemp, readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { build } from "vite";

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
    const howPlayEvents = async (eventName) => (await events()).filter(([, name, props]) => name === eventName && props.experience_type === "how_play");
    await page.goto("https://map-mory.com/?internal=1");
    await page.locator(".site-header").waitFor();
    const menu = page.locator(".header-store-menu");
    const trigger = page.locator(".header-store-trigger");
    // React attaches the Escape and outside-click listeners only after the async toggle event.
    const openStoreMenu = async () => {
      const toggled = await menu.evaluateHandle((node) => ({ done: new Promise((resolve) => node.addEventListener("toggle", () => setTimeout(resolve), { once: true })) }));
      await trigger.click();
      await toggled.evaluate(({ done }) => done);
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
    // View before any tap proves the observer sits on a real box, not the display: contents wrapper.
    const howPlay = page.locator(".how-play-phone");
    await howPlay.evaluate((node) => node.scrollIntoView({ block: "center" }));
    await waitForEvent("experience_view", "how_play");
    assert.equal((await howPlayEvents("experience_start")).length, 0);
    for (const [index, [placeButton, pickAll, demoPlace]] of [["부산", true, "busan"], ["강원도 칠해 보기", false, "gangwon"], ["경주도 칠해 보기", true, "gyeongbuk"]].entries()) {
      await howPlay.getByRole("button", { name: placeButton, exact: true }).click();
      const starts = await howPlayEvents("experience_start");
      assert.equal(starts.length, 1);
      assert.equal(starts[0][2].interaction_type, "place_select");
      if (pickAll) await howPlay.getByRole("button", { name: "모두 선택", exact: true }).click();
      else await howPlay.locator(".how-play-photos button").first().click();
      await howPlay.getByRole("button", { name: pickAll ? "4장 저장하기" : "1장 저장하기", exact: true }).click();
      await waitForEvent("how_play_save", "how_play", index + 1);
      const { demo_place, save_index, selected_photos } = (await howPlayEvents("how_play_save")).at(-1)[2];
      assert.deepEqual({ demo_place, save_index, selected_photos }, { demo_place: demoPlace, save_index: index + 1, selected_photos: pickAll ? 4 : 1 });
    }
    // A replay after 처음부터 다시 must not re-send a place already saved on this page.
    await howPlay.getByRole("button", { name: "처음부터 다시", exact: true }).click();
    await howPlay.getByRole("button", { name: "부산", exact: true }).click();
    await howPlay.getByRole("button", { name: "모두 선택", exact: true }).click();
    await howPlay.getByRole("button", { name: "4장 저장하기", exact: true }).click();
    await howPlay.getByRole("button", { name: "강원도 칠해 보기", exact: true }).waitFor();
    assert.equal((await howPlayEvents("how_play_save")).length, 3);
    assert.equal((await howPlayEvents("experience_start")).length, 1);
    console.log("How play passed", { mobile });
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
    // Leaving the phone for the globe ends how_play after the 1.5 s grace.
    await waitForEvent("experience_end", "how_play");
    const howPlayEnds = await howPlayEvents("experience_end");
    assert.equal(howPlayEnds.length, 1);
    assert.equal(howPlayEnds[0][2].exit_reason, "section_exit");
    assert.equal(howPlayEnds[0][2].last_completed_step, "how_play_save");
    assert.equal(howPlayEnds[0][2].unique_memories_opened, 0);
    if (!mobile) await page.locator(".site-header nav").getByRole("link", { name: "사용 방법", exact: true }).click();
    assert.deepEqual((await howPlayEvents("experience_cta_click")).map(([, , props]) => props.cta_placement), mobile ? [] : ["header_nav"]);
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
    assert.equal(landingEvents.some(([, name, props]) => name === "memory_open" && props.experience_type === "how_play"), false);
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
    findings.push({ mobile, passed: true, covered: "header stores/Escape/outside dismissal, hero exclusion, how_play view before tap/start/save_index 1-3/reset replay not re-sent/section_exit end, globe-only view/start/open, how-section globe link, header how_play link (desktop), final store without experience context, no photo paths in landing events, recap download failure/retry, demo/store and photos/no-GPS" });
    await context.close();
  }
  console.log(JSON.stringify({ output, findings, productionAnalyticsRequests: 0 }, null, 2));
} finally { await browser.close(); }
