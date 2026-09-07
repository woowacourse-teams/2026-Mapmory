import test from "node:test";
import assert from "node:assert/strict";
import {
  ANALYTICS_EVENTS,
  INTERNAL_TRAFFIC_STORAGE_KEY,
  POSTHOG_CAPTURE_CONFIG,
  buildEventParameters,
  isSupportedEvent,
  resolveTrafficType,
} from "../src/analytics.js";

function createMemoryStorage(initialValue = null) {
  const values = new Map();
  if (initialValue !== null) {
    values.set(INTERNAL_TRAFFIC_STORAGE_KEY, initialValue);
  }

  return {
    getItem(key) {
      return values.get(key) ?? null;
    },
    setItem(key, value) {
      values.set(key, value);
    },
    removeItem(key) {
      values.delete(key);
    },
  };
}

test("declares the agreed landing funnel events", () => {
  assert.deepEqual(
    new Set(Object.values(ANALYTICS_EVENTS)),
    new Set([
      "experience_cta_click",
      "experience_view",
      "experience_start",
      "memory_open",
      "memory_photo_swiped",
      "memory_sheet_closed",
      "korea_memory_add",
      "experience_end",
      "waitlist_cta_click",
      "waitlist_form_view",
      "waitlist_form_start",
      "waitlist_submit_attempt",
      "waitlist_submit",
      "waitlist_submit_error",
      "download_click",
      "download_cta_click",
    ]),
  );
});

test("adds the landing version and removes direct personal information", () => {
  assert.deepEqual(
    buildEventParameters({
      cta_placement: "hero",
      email: "person@example.com",
      phone_number: "010-0000-0000",
      arbitrary_payload: "must-not-pass",
      unused: undefined,
    }),
    {
      surface: "landing",
      analytics_schema_version: "2",
      landing_version: "v4",
      traffic_type: "external",
      cta_placement: "hero",
    },
  );
});

test("rejects event names outside the agreed taxonomy", () => {
  assert.equal(isSupportedEvent("waitlist_submit"), true);
  assert.equal(isSupportedEvent("button_click"), false);
});

test("keeps seconds-based experience duration and distinct-memory parameters", () => {
  assert.deepEqual(
    buildEventParameters({
      experience_type: "globe",
      active_duration_seconds: 23.7,
      time_since_memory_open_seconds: 4.2,
      unique_memories_opened: 3,
      last_completed_step: "memory_open",
    }),
    {
      surface: "landing",
      analytics_schema_version: "2",
      landing_version: "v4",
      traffic_type: "external",
      experience_type: "globe",
      active_duration_seconds: 23.7,
      time_since_memory_open_seconds: 4.2,
      unique_memories_opened: 3,
      last_completed_step: "memory_open",
    },
  );
});

test("allows only the approved photo-sheet diagnostic properties", () => {
  assert.deepEqual(
    buildEventParameters({
      experience_type: "globe",
      memory_id: "usa-west",
      photo_index: 2,
      photo_count: 5,
      close_method: "browser_back",
      max_photo_index: 4,
      photos_viewed: 3,
      time_since_memory_open_seconds: 6.8,
      caption: "must-not-pass",
      coordinates: "must-not-pass",
    }),
    {
      surface: "landing",
      analytics_schema_version: "2",
      landing_version: "v4",
      traffic_type: "external",
      experience_type: "globe",
      memory_id: "usa-west",
      photo_index: 2,
      photo_count: 5,
      close_method: "browser_back",
      max_photo_index: 4,
      photos_viewed: 3,
      time_since_memory_open_seconds: 6.8,
    },
  );
});

test("preserves both public store destinations for download attribution", () => {
  for (const store of ["app_store", "google_play"]) {
    assert.deepEqual(buildEventParameters({ cta_placement: "header", store }), {
      surface: "landing",
      analytics_schema_version: "2",
      landing_version: "v4",
      traffic_type: "external",
      cta_placement: "header",
      store,
    });
  }
});

test("persists and clears the analytics-only internal traffic marker", () => {
  const storage = createMemoryStorage();

  assert.equal(resolveTrafficType({ search: "?internal=1", storage }), "internal");
  assert.equal(resolveTrafficType({ storage }), "internal");
  assert.equal(resolveTrafficType({ search: "?internal=0", storage }), "external");
  assert.equal(resolveTrafficType({ storage }), "external");
});

test("ignores unsupported internal query values", () => {
  const storage = createMemoryStorage("1");

  assert.equal(resolveTrafficType({ search: "?internal=true", storage }), "internal");
  assert.equal(resolveTrafficType({ search: "?internal=false", storage }), "internal");
});

test("keeps PostHog limited to anonymous explicit product events", () => {
  assert.deepEqual(
    POSTHOG_CAPTURE_CONFIG,
    {
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
    },
  );
});
