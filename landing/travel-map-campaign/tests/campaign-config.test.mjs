import assert from "node:assert/strict";
import test from "node:test";
import { APP_ACQUISITION_URL, CAMPAIGN_LANDING_URL, GOOGLE_PLAY_PACKAGE_ID, MAPMORY_DOMAIN_LABEL, MAPMORY_SITE_ORIGIN } from "../src/campaignConfig.js";
import { initializeCampaignAnalytics, POSTHOG_CAPTURE_CONFIG, resolveMeasurementId, resolveTrafficType, sanitizeEventProperties, trackCampaignEvent } from "../src/analytics.js";

test("uses the official Google Play listing for app acquisition", () => {
  const destination = new URL(APP_ACQUISITION_URL);
  assert.equal(MAPMORY_SITE_ORIGIN, "https://map-mory.com");
  assert.equal(MAPMORY_DOMAIN_LABEL, "map-mory.com");
  assert.equal(GOOGLE_PLAY_PACKAGE_ID, "com.mapmory.android");
  assert.equal(destination.hostname, "play.google.com");
  assert.equal(destination.searchParams.get("id"), GOOGLE_PLAY_PACKAGE_ID);
  const referrer = new URLSearchParams(destination.searchParams.get("referrer"));
  assert.equal(referrer.get("utm_source"), "travel_map_campaign");
  assert.equal(referrer.get("utm_medium"), "web_campaign");
  assert.equal(referrer.get("utm_campaign"), "2026_travel_map");
  assert.equal(referrer.get("utm_content"), "demand_primary");
});

test("GA requires an explicit measurement ID even for production-mode previews", () => {
  assert.equal(resolveMeasurementId(), "");
  assert.equal(resolveMeasurementId({ PROD: true }), "");
  assert.equal(resolveMeasurementId({ PROD: true, VITE_GA_MEASUREMENT_ID: "  " }), "");
  assert.equal(resolveMeasurementId({ VITE_GA_MEASUREMENT_ID: " G-TEST123 " }), "G-TEST123");
});

test("an unconfigured campaign neither initializes GA nor forwards to a foreign gtag", (t) => {
  const original = Object.getOwnPropertyDescriptor(globalThis, "window");
  const gtag = t.mock.fn();
  Object.defineProperty(globalThis, "window", { configurable: true, value: { gtag } });
  t.after(() => original ? Object.defineProperty(globalThis, "window", original) : delete globalThis.window);
  assert.equal(initializeCampaignAnalytics(), false);
  assert.equal(trackCampaignEvent("travel_map_demo_start"), false);
  assert.equal(gtag.mock.callCount(), 0);
});

test("internal landing navigation preserves acquisition attribution", () => {
  const destination = new URL(CAMPAIGN_LANDING_URL);
  assert.equal(destination.origin, MAPMORY_SITE_ORIGIN);
  assert.equal(destination.pathname, "/");
  assert.equal(destination.search, "");
});

test("marks internal campaign traffic without collecting personal fields", () => {
  const values = new Map();
  const storage = {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: (key) => values.delete(key),
  };
  assert.equal(resolveTrafficType({ search: "?internal=1", storage }), "internal");
  assert.equal(resolveTrafficType({ storage }), "internal");
  assert.equal(resolveTrafficType({ search: "?internal=0", storage }), "external");
  assert.deepEqual(sanitizeEventProperties({ journey_source: "photos", picker_source: "file_system_access", selected_photos: 5, latitude: 37.5, filename: "private.jpg", email: "private@example.com" }), { journey_source: "photos", picker_source: "file_system_access", selected_photos: 5 });
  assert.deepEqual(sanitizeEventProperties({ journey_source: "photos", picker_source: "unknown" }), { journey_source: "photos" });
});

test("keeps Recap PostHog anonymous and explicit", () => {
  assert.deepEqual(POSTHOG_CAPTURE_CONFIG, {
    defaults: "2026-05-30",
    autocapture: false,
    capture_pageview: false,
    capture_pageleave: false,
    disable_session_recording: true,
    disable_surveys: true,
    person_profiles: "never",
    persistence: "memory",
    advanced_disable_feature_flags: true,
    advanced_disable_feature_flags_on_first_load: true,
  });
});
