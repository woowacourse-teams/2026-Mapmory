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
    await page.goto("https://map-mory.com/?internal=1");
    await page.locator(".site-header").waitFor();
    const menu = page.locator(".header-store-menu");
    const trigger = page.locator(".header-store-trigger");
    await trigger.click();
    await menu.locator('[role="group"]').waitFor({ state: "visible" });
    await page.keyboard.press("Escape");
    await page.waitForFunction(() => !document.querySelector(".header-store-menu").open);
    assert.equal(await trigger.evaluate((element) => element === document.activeElement), true);
    await trigger.click();
    await menu.locator('[role="group"]').waitFor({ state: "visible" });
    await page.locator(".site-header .brand").click();
    await page.waitForFunction(() => !document.querySelector(".header-store-menu").open);
    assert.equal((await events()).filter(([, name]) => ["experience_start", "memory_open", "korea_memory_add"].includes(name)).length, 0);
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
    if (mobile) await page.locator(".hero-mobile-experience-cue").click();
    else await page.getByRole("link", { name: "지구본 체험", exact: true }).click();
    await page.waitForFunction(() => [...(window.dataLayer ?? [])].some((args) => args[1] === "experience_view"));
    await page.getByRole("button", { name: "일본", exact: true }).click();
    await page.waitForFunction(() => [...(window.dataLayer ?? [])].some((args) => args[1] === "memory_open"));
    const globe = (await events()).filter(([, , props]) => props.experience_type === "globe").map(([, name]) => name);
    console.log("Globe events", { mobile, globe });
    assert.ok(globe.indexOf("experience_view") < globe.indexOf("experience_start"));
    assert.ok(globe.indexOf("experience_start") < globe.indexOf("memory_open"));
    if (mobile) await page.goBack();
    if (mobile) await page.locator("#korea-detail").scrollIntoViewIfNeeded();
    else await page.getByRole("link", { name: "대한민국 지도", exact: true }).click();
    await page.getByRole("button", { name: "서울특별시 사진을 지도에 추가하기", exact: true }).click();
    await page.getByRole("button", { name: "서울의 기억 보기", exact: false }).click();
    await page.getByRole("link", { name: "내 기억 지도도 만들기" }).click();
    assert.equal((await events()).filter(([, name]) => name === "download_click").length, 2);
    assert.equal((await events()).filter(([, name]) => name === "download_cta_click").length, 1);
    console.log("Korea internal CTA passed", { mobile });
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
    findings.push({ mobile, passed: true, covered: "header stores/Escape/outside dismissal, hero exclusion, globe view/start/open, Korea add/internal CTA, recap download failure/retry, demo/store and photos/no-GPS" });
    await context.close();
  }
  console.log(JSON.stringify({ output, findings, productionAnalyticsRequests: 0 }, null, 2));
} finally { await browser.close(); }
