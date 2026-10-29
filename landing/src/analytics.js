import { canCaptureGa, isScalar, measurementContext, resolveMeasurementId } from "./analytics-contract.js";

const environment = import.meta.env ?? {};
const measurementId = resolveMeasurementId(environment);
const landingVersion = environment.VITE_LANDING_VERSION?.trim() || "v4";
const posthogKey = environment.VITE_POSTHOG_KEY?.trim() || "";
const posthogHost = environment.VITE_POSTHOG_HOST?.trim() || "";
const capturePosthogLocally = environment.VITE_POSTHOG_CAPTURE_LOCAL === "true";
const isLocalBrowser = typeof window !== "undefined"
  && ["localhost", "127.0.0.1"].includes(window.location.hostname);

export const INTERNAL_TRAFFIC_STORAGE_KEY = "mapmory_internal_traffic_v1";

export function resolveTrafficType({ search = "", storage = null } = {}) {
  const internalMode = new URLSearchParams(search).get("internal");

  if (internalMode === "1") {
    try {
      storage?.setItem(INTERNAL_TRAFFIC_STORAGE_KEY, "1");
    } catch {
      // The current visit is still marked internal when storage is unavailable.
    }
    return "internal";
  }

  if (internalMode === "0") {
    try {
      storage?.removeItem(INTERNAL_TRAFFIC_STORAGE_KEY);
    } catch {
      // The explicit reset still applies to the current visit.
    }
    return "external";
  }

  try {
    return storage?.getItem(INTERNAL_TRAFFIC_STORAGE_KEY) === "1"
      ? "internal"
      : "external";
  } catch {
    return "external";
  }
}

function resolveBrowserTrafficType() {
  if (typeof window === "undefined") return "external";

  let storage = null;
  try {
    storage = window.localStorage;
  } catch {
    // Storage can be unavailable in privacy-restricted browser contexts.
  }

  return resolveTrafficType({
    search: window.location.search,
    storage,
  });
}

const trafficType = resolveBrowserTrafficType();

export const POSTHOG_CAPTURE_CONFIG = Object.freeze({
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

export const ANALYTICS_EVENTS = Object.freeze({
  EXPERIENCE_CTA_CLICK: "experience_cta_click",
  EXPERIENCE_VIEW: "experience_view",
  EXPERIENCE_START: "experience_start",
  MEMORY_OPEN: "memory_open",
  MEMORY_PHOTO_SWIPED: "memory_photo_swiped",
  MEMORY_SHEET_CLOSED: "memory_sheet_closed",
  EXPERIENCE_END: "experience_end",
  WAITLIST_CTA_CLICK: "waitlist_cta_click",
  WAITLIST_FORM_VIEW: "waitlist_form_view",
  WAITLIST_FORM_START: "waitlist_form_start",
  WAITLIST_SUBMIT_ATTEMPT: "waitlist_submit_attempt",
  WAITLIST_SUBMIT: "waitlist_submit",
  WAITLIST_SUBMIT_ERROR: "waitlist_submit_error",
  DOWNLOAD_CLICK: "download_click",
  HERO_DEMO_SELECT: "hero_demo_select",
  HOW_STORY_VIEW: "how_story_view",
  HOW_STORY_SCENE: "how_story_scene",
  TECH_STORY_VIEW: "tech_story_view",
  TECH_STORY_SCENE: "tech_story_scene",
  TECH_STORY_INTERACT: "tech_story_interact",
});

// The #tech section's touchable element per scene (1-6), one tech_story_interact per type per page.
export const TECH_STORY_INTERACTIONS = Object.freeze(["place_pick", "gather", "save", "map_toggle", "globe_drag", "year_pick"]);

const supportedEvents = new Set(Object.values(ANALYTICS_EVENTS));
const forbiddenParameterPattern = /(email|phone|name|address|message|free.?text)/i;
const supportedParameters = new Set([
  "experience_type",
  "interaction_type",
  "memory_id",
  "selection_source",
  "cta_placement",
  "store",
  "open_index",
  "time_since_start_seconds",
  "active_duration_seconds",
  "time_since_memory_open_seconds",
  "unique_memories_opened",
  "last_completed_step",
  "exit_reason",
  "attempt_number",
  "result",
  "error_type",
  "validation_field",
  "transport_type",
  "photo_index",
  "photo_count",
  "close_method",
  "max_photo_index",
  "photos_viewed",
  "demo_place",
  "scene_index",
  "story_mode",
]);

let gaInitialized = false;
let posthogInitialization = null;

function initializePostHog() {
  const shouldInitialize = posthogKey
    && posthogHost
    && (!isLocalBrowser || capturePosthogLocally)
    && typeof window !== "undefined";

  if (!shouldInitialize) return null;
  if (posthogInitialization) return posthogInitialization;

  posthogInitialization = import("posthog-js/dist/module.no-external")
    .then(({ default: posthog }) => {
      posthog.init(posthogKey, {
        ...POSTHOG_CAPTURE_CONFIG,
        api_host: posthogHost,
      });
      posthog.register({
        $geoip_disable: true,
        traffic_type: trafficType,
      });
      posthog.capture("$pageview", {
        ...measurementContext("landing"),
        landing_version: landingVersion,
        traffic_type: trafficType,
        $pathname: window.location.pathname,
        $geoip_disable: true,
      });
      return posthog;
    })
    .catch((error) => {
      if (capturePosthogLocally) {
        console.warn("PostHog analytics initialization failed.", error);
      }
      return null;
    });

  return posthogInitialization;
}

// PostHog (about 90 KB gzipped) waits until the page has loaded and the browser is idle, so it never competes with
// the first screen. A visitor who touches, scrolls or leaves the tab starts it at once (so quick bounces keep their
// $pageview), an event tracked earlier starts it through trackEvent, and a stuck load event waits at most 4 s.
function schedulePostHog() {
  if (typeof window.addEventListener !== "function" || typeof document === "undefined") {
    void initializePostHog();
    return;
  }
  const now = () => void initializePostHog();
  const engage = ["pointerdown", "keydown", "scroll", "visibilitychange"];
  for (const type of engage) window.addEventListener(type, now, { once: true, passive: true });
  const start = () => {
    if (typeof window.requestIdleCallback === "function") window.requestIdleCallback(now, { timeout: 2000 });
    else setTimeout(now, 1000);
  };
  if (document.readyState === "complete") start();
  else {
    window.addEventListener("load", start, { once: true });
    setTimeout(now, 4000);
  }
}

export function initializeAnalytics() {
  if (typeof window !== "undefined") schedulePostHog();

  if (!measurementId || gaInitialized || typeof window === "undefined"
    || !canCaptureGa(environment, window.location.hostname)) return;

  gaInitialized = true;
  window.dataLayer = window.dataLayer || [];
  window.gtag = function gtag() {
    window.dataLayer.push(arguments);
  };

  window.gtag("js", new Date());
  window.gtag("config", measurementId, {
    anonymize_ip: true,
    send_page_view: true,
    ...measurementContext("landing"),
    ...(environment.VITE_GA_DEBUG === "true" ? { debug_mode: true } : {}),
    landing_version: landingVersion,
    traffic_type: trafficType,
  });

  const script = document.createElement("script");
  script.async = true;
  script.src = `https://www.googletagmanager.com/gtag/js?id=${encodeURIComponent(measurementId)}`;
  document.head.appendChild(script);
}

export function buildEventParameters(parameters = {}) {
  const safeParameters = Object.fromEntries(
    Object.entries(parameters).filter(([key, value]) => (
      supportedParameters.has(key)
      && !forbiddenParameterPattern.test(key)
      && isScalar(value)
    )),
  );

  return {
    ...measurementContext("landing"),
    landing_version: landingVersion,
    traffic_type: trafficType,
    ...safeParameters,
  };
}

export function isSupportedEvent(name) {
  return supportedEvents.has(name);
}

export function trackEvent(name, parameters = {}) {
  if (!isSupportedEvent(name)) return false;
  if (name === ANALYTICS_EVENTS.DOWNLOAD_CLICK
    && !["app_store", "google_play"].includes(parameters.store)) return false;
  // The #how photo story sends only these two passive reach events, never experience_* or memory_open.
  if (name === ANALYTICS_EVENTS.HOW_STORY_VIEW
    && !(parameters.experience_type === "how_story"
      && ["scroll", "static"].includes(parameters.story_mode))) return false;
  if (name === ANALYTICS_EVENTS.HOW_STORY_SCENE
    && !(parameters.experience_type === "how_story"
      && Number.isInteger(parameters.scene_index)
      && parameters.scene_index >= 1
      && parameters.scene_index <= 6)) return false;
  // The #tech section: passive reach (view, scene 1-6) plus the first try of each scene's touchable element.
  if ((name === ANALYTICS_EVENTS.TECH_STORY_VIEW || name === ANALYTICS_EVENTS.TECH_STORY_SCENE || name === ANALYTICS_EVENTS.TECH_STORY_INTERACT)
    && parameters.experience_type !== "tech_story") return false;
  if ((name === ANALYTICS_EVENTS.TECH_STORY_SCENE || name === ANALYTICS_EVENTS.TECH_STORY_INTERACT)
    && !(Number.isInteger(parameters.scene_index) && parameters.scene_index >= 1 && parameters.scene_index <= 6)) return false;
  if (name === ANALYTICS_EVENTS.TECH_STORY_INTERACT && !TECH_STORY_INTERACTIONS.includes(parameters.interaction_type)) return false;
  const eventParameters = buildEventParameters(parameters);
  let tracked = false;

  if (measurementId && gaInitialized && typeof window !== "undefined" && typeof window.gtag === "function") {
    window.gtag("event", name, eventParameters);
    tracked = true;
  }

  const posthogClient = initializePostHog();
  if (posthogClient) {
    tracked = true;
    void posthogClient.then((posthog) => {
      posthog?.capture(name, {
        ...eventParameters,
        $geoip_disable: true,
      });
    });
  }

  return tracked;
}
