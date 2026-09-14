import { canCaptureGa, isScalar, measurementContext, resolveMeasurementId } from "./analytics-contract.js";

const environment = import.meta.env ?? {};
const measurementId = resolveMeasurementId(environment);
const landingVersion = environment.VITE_LANDING_VERSION?.trim() || "v3";
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
  KOREA_MEMORY_ADD: "korea_memory_add",
  EXPERIENCE_END: "experience_end",
  WAITLIST_CTA_CLICK: "waitlist_cta_click",
  WAITLIST_FORM_VIEW: "waitlist_form_view",
  WAITLIST_FORM_START: "waitlist_form_start",
  WAITLIST_SUBMIT_ATTEMPT: "waitlist_submit_attempt",
  WAITLIST_SUBMIT: "waitlist_submit",
  WAITLIST_SUBMIT_ERROR: "waitlist_submit_error",
  DOWNLOAD_CLICK: "download_click",
  DOWNLOAD_CTA_CLICK: "download_cta_click",
});

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
  "add_index",
  "time_since_start_ms",
  "active_duration_ms",
  "unique_memories_opened",
  "last_completed_step",
  "exit_reason",
  "attempt_number",
  "result",
  "error_type",
  "validation_field",
  "transport_type",
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

export function initializeAnalytics() {
  void initializePostHog();

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
