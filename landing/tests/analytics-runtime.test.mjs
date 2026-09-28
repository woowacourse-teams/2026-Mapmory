import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import vm from "node:vm";
import test from "node:test";
import { build } from "esbuild";
import { canCaptureGa, resolveMeasurementId } from "../src/analytics-contract.js";
import { classifyGlobeGesture } from "../src/globe-gesture.js";

async function loadAnalytics(surface, { env = {}, hostname = "map-mory.com", search = "?internal=1", mockPostHog = false } = {}) {
  const entry = surface === "landing" ? "../src/analytics.js" : "../travel-map-campaign/src/analytics.js";
  const posthogPlugin = {
    name: "mock-posthog",
    setup(buildApi) {
      buildApi.onResolve({ filter: /^posthog-js\/dist\/module\.no-external$/ }, () => ({ path: "posthog", namespace: "mock" }));
      buildApi.onLoad({ filter: /.*/, namespace: "mock" }, () => ({
        contents: `export default {
          init: (...args) => window.__posthogCalls.push(["init", ...args]),
          register: (...args) => window.__posthogCalls.push(["register", ...args]),
          capture: (...args) => window.__posthogCalls.push(["capture", ...args]),
        };`,
      }));
    },
  };
  const result = await build({
    entryPoints: [fileURLToPath(new URL(entry, import.meta.url))], bundle: true,
    write: false, format: "iife", globalName: "analytics",
    ...(mockPostHog ? { plugins: [posthogPlugin] } : { external: ["posthog-js/*"] }),
    define: { "import.meta.env": JSON.stringify({ VITE_GA_MEASUREMENT_ID: "G-TEST123", ...env }) },
  });
  const scripts = [];
  const storage = new Map();
  const context = vm.createContext({
    URLSearchParams, console,
    window: { __posthogCalls: [], location: { hostname, search, pathname: surface === "landing" ? "/" : "/recap/" }, localStorage: {
      getItem: (key) => storage.get(key), setItem: (key, value) => storage.set(key, value), removeItem: (key) => storage.delete(key),
    } },
    document: { createElement: () => ({}), head: { appendChild: (script) => scripts.push(script) } },
  });
  vm.runInContext(result.outputFiles[0].text, context);
  return { api: context.analytics, window: context.window, scripts,
    calls: () => JSON.parse(JSON.stringify((context.window.dataLayer ?? []).map((args) => [...args]))) };
}

test("Recap initializes its own PostHog client and sends scoped explicit events", async () => {
  const harness = await loadAnalytics("recap", {
    mockPostHog: true,
    env: {
      VITE_POSTHOG_KEY: "phc_test",
      VITE_POSTHOG_HOST: "https://us.i.posthog.com",
    },
  });
  harness.api.initializeCampaignAnalytics();
  harness.api.trackCampaignEvent("travel_map_photo_select", {
    journey_source: "photos", selected_photos: 3, filename: "private.jpg", latitude: 37.5,
  });
  await new Promise((resolve) => setImmediate(resolve));

  const calls = JSON.parse(JSON.stringify(harness.window.__posthogCalls));
  assert.equal(calls.filter(([name]) => name === "init").length, 1);
  assert.equal(calls.filter(([name, event]) => name === "capture" && event === "$pageview").length, 1);
  const event = calls.find(([name, eventName]) => name === "capture" && eventName === "travel_map_photo_select");
  assert.ok(event);
  assert.equal(event[2].surface, "recap");
  assert.equal(event[2].analytics_schema_version, "2");
  assert.equal(event[2].journey_source, "photos");
  assert.equal(event[2].selected_photos, 3);
  assert.equal(event[2].filename, undefined);
  assert.equal(event[2].latitude, undefined);
});

test("GA is explicit, and production builds do not opt local/LAN previews in", () => {
  assert.equal(resolveMeasurementId({ PROD: true }), "");
  const config = { VITE_GA_MEASUREMENT_ID: "G-TEST123" };
  for (const host of ["127.0.0.1", "localhost", "10.91.135.44", "preview.example.com"]) {
    assert.equal(canCaptureGa(config, host), false);
  }
  assert.equal(canCaptureGa(config, "map-mory.com"), true);
  assert.equal(canCaptureGa({ ...config, VITE_GA_CAPTURE_LOCAL: "true" }, "127.0.0.1"), true);
});

for (const surface of ["landing", "recap"]) {
  test(`${surface}: one page view config, scoped events, no campaign override or PII`, async () => {
    const harness = await loadAnalytics(surface);
    const init = harness.api.initializeAnalytics ?? harness.api.initializeCampaignAnalytics;
    const track = harness.api.trackEvent ?? harness.api.trackCampaignEvent;
    assert.equal(track("download_click", { store: "google_play" }), false);
    init(); init();
    assert.equal(harness.scripts.length, 1);
    const configs = harness.calls().filter(([command]) => command === "config");
    assert.equal(configs.length, 1);
    assert.equal(configs[0][2].surface, surface);
    assert.equal(configs[0][2].analytics_schema_version, "2");
    assert.equal(configs[0][2].traffic_type, "internal");
    assert.equal(configs[0][2].send_page_view, true);
    assert.equal(configs[0][2].campaign_name, undefined);
    assert.equal(track("unknown_event"), false);
    assert.equal(track("download_click", { cta_placement: "korea_memory" }), false);
    for (const store of ["app_store", "google_play"]) {
      assert.equal(track("download_click", { store, cta_placement: "header", email: "private@example.com", latitude: 37.5, arbitrary: "private", format: { raw: "data" } }), true);
    }
    const events = harness.calls().filter(([command]) => command === "event");
    assert.equal(events.length, 2);
    for (const [, , params] of events) {
      assert.equal(params.surface, surface);
      assert.equal(params.analytics_schema_version, "2");
      for (const key of ["email", "latitude", "arbitrary", "format", "campaign_name"]) assert.equal(params[key], undefined);
    }
  });
  test(`${surface}: blocked previews never forward to another tracker`, async () => {
    const harness = await loadAnalytics(surface, { hostname: "127.0.0.1" });
    let foreignCalls = 0;
    harness.window.gtag = () => { foreignCalls += 1; };
    (harness.api.initializeAnalytics ?? harness.api.initializeCampaignAnalytics)();
    (harness.api.trackEvent ?? harness.api.trackCampaignEvent)("download_click", { store: "google_play" });
    assert.equal(harness.scripts.length, 0);
    assert.equal(foreignCalls, 0);
  });
}

test("an internal download-section CTA never claims a store conversion", async () => {
  const harness = await loadAnalytics("landing");
  harness.api.initializeAnalytics();
  harness.api.trackEvent("download_cta_click", { cta_placement: "korea_memory" });
  const events = harness.calls().filter(([command]) => command === "event");
  assert.deepEqual(events.map(([, event]) => event), ["download_cta_click"]);
});

test("recap outcome measurements separate demo, own photos, cancellation and failure", async () => {
  const harness = await loadAnalytics("recap");
  harness.api.initializeCampaignAnalytics();
  for (const journey_source of ["demo", "photos"]) {
    for (const result of ["shared", "download_started", "cancelled", "failed"]) {
      harness.api.trackCampaignEvent("travel_map_share_result", { journey_source, result });
    }
  }
  const events = harness.calls().filter(([command]) => command === "event");
  assert.equal(events.length, 8);
  assert.deepEqual([...new Set(events.map(([, , params]) => params.journey_source))], ["demo", "photos"]);
});

test("touch scrolling and taps are not counted as globe drags", () => {
  const start = { pointerId: 1, pointerType: "touch", clientX: 10, clientY: 10 };
  assert.equal(classifyGlobeGesture(start, { pointerId: 1, clientX: 13, clientY: 12 }), "pending");
  assert.equal(classifyGlobeGesture(start, { pointerId: 1, clientX: 13, clientY: 80 }), "page_scroll");
  assert.equal(classifyGlobeGesture(start, { pointerId: 1, clientX: 50, clientY: 12 }), "globe_drag");
  assert.equal(classifyGlobeGesture({ ...start, pointerType: "mouse" }, { pointerId: 1, clientX: 10, clientY: 30 }), "globe_drag");
  assert.equal(classifyGlobeGesture(start, { pointerId: 2, clientX: 50, clientY: 12 }), "pending");
});
