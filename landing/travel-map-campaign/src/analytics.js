import { canCaptureGa, isScalar, measurementContext, resolveMeasurementId } from "../../src/analytics-contract.js";
export { resolveMeasurementId } from "../../src/analytics-contract.js";

const environment = import.meta.env ?? {};
const measurementId = resolveMeasurementId(environment);
const campaignVersion = environment.VITE_CAMPAIGN_VERSION?.trim() || "travel-map-v1";
const posthogKey = environment.VITE_POSTHOG_KEY?.trim() || "";
const posthogHost = environment.VITE_POSTHOG_HOST?.trim() || "";
const capturePosthogLocally = environment.VITE_POSTHOG_CAPTURE_LOCAL === "true";
const isLocalBrowser = typeof window !== "undefined"
  && ["localhost", "127.0.0.1"].includes(window.location.hostname);
const forbiddenParameterPattern = /(email|phone|name|address|message|free.?text)/i;
const supportedParameters = new Set([
  "journey_source", "selected_photos", "valid_gps_photos", "duration_seconds",
  "metadata_missing_photos", "read_failed_photos", "unsupported_photos",
  "picker_source",
  "cta_placement", "destination", "store", "format", "result", "error_type",
]);
const supportedEvents = new Set([
  "travel_map_photo_select", "travel_map_demo_start", "travel_map_photo_analysis_empty",
  "travel_map_processing_complete", "travel_map_processing_failed", "travel_map_replay_complete",
  "travel_map_recap_view", "travel_map_video_save_start", "travel_map_video_saved",
  "travel_map_image_save_start", "travel_map_image_saved", "travel_map_export_failed",
  "travel_map_share_click", "travel_map_share_result", "travel_map_app_bridge_click",
  "travel_map_demand_view", "travel_map_landing_click", "download_click",
]);

export const INTERNAL_TRAFFIC_STORAGE_KEY = "mapmory_internal_traffic_v1";

export function resolveTrafficType({ search = "", storage = null } = {}) {
  const internalMode = new URLSearchParams(search).get("internal");

  if (internalMode === "1") {
    try { storage?.setItem(INTERNAL_TRAFFIC_STORAGE_KEY, "1"); } catch { /* Current visit still counts as internal. */ }
    return "internal";
  }

  if (internalMode === "0") {
    try { storage?.removeItem(INTERNAL_TRAFFIC_STORAGE_KEY); } catch { /* Explicit reset still applies. */ }
    return "external";
  }

  try { return storage?.getItem(INTERNAL_TRAFFIC_STORAGE_KEY) === "1" ? "internal" : "external"; }
  catch { return "external"; }
}

function resolveBrowserTrafficType() {
  if (typeof window === "undefined") return "external";
  let storage = null;
  try { storage = window.localStorage; } catch { /* Storage may be unavailable. */ }
  return resolveTrafficType({ search: window.location.search, storage });
}

const trafficType = resolveBrowserTrafficType();
let gaInitialized = false;
let posthogInitialization = null;

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
        ...measurementContext("recap"),
        campaign_version: campaignVersion,
        traffic_type: trafficType,
        $geoip_disable: true,
      });
      posthog.capture("$pageview", { $pathname: window.location.pathname });
      return posthog;
    })
    .catch((error) => {
      if (capturePosthogLocally) {
        console.warn("PostHog campaign analytics initialization failed.", error);
      }
      return null;
    });

  return posthogInitialization;
}

export function sanitizeEventProperties(properties = {}) {
  return Object.fromEntries(Object.entries(properties).filter(([key, value]) => (
    supportedParameters.has(key) && !forbiddenParameterPattern.test(key) && isScalar(value)
    && (key !== "journey_source" || ["demo", "photos"].includes(value))
    && (key !== "picker_source" || ["file_system_access", "legacy_input"].includes(value))
  )));
}

export function initializeCampaignAnalytics() {
  void initializePostHog();

  if (!measurementId || gaInitialized || typeof window === "undefined"
    || !canCaptureGa(environment, window.location.hostname)) return false;

  gaInitialized = true;
  window.dataLayer = window.dataLayer || [];
  window.gtag = window.gtag || function gtag() { window.dataLayer.push(arguments); };
  window.gtag("js", new Date());
  window.gtag("config", measurementId, {
    anonymize_ip: true,
    send_page_view: true,
    ...measurementContext("recap"),
    ...(environment.VITE_GA_DEBUG === "true" ? { debug_mode: true } : {}),
    campaign_version: campaignVersion,
    traffic_type: trafficType,
  });

  const script = document.createElement("script");
  script.async = true;
  script.src = `https://www.googletagmanager.com/gtag/js?id=${encodeURIComponent(measurementId)}`;
  document.head.appendChild(script);
  return true;
}

export function trackCampaignEvent(eventName, properties = {}) {
  if (!supportedEvents.has(eventName)) return false;
  if (eventName === "download_click" && !["google_play", "app_store"].includes(properties.store)) return false;
  const eventProperties = {
    ...measurementContext("recap"),
    campaign_version: campaignVersion,
    traffic_type: trafficType,
    ...sanitizeEventProperties(properties),
  };

  let tracked = false;
  if (measurementId && gaInitialized && typeof window !== "undefined" && typeof window.gtag === "function") {
    window.gtag("event", eventName, eventProperties);
    tracked = true;
  }
  const posthogClient = initializePostHog();
  if (posthogClient) {
    tracked = true;
    void posthogClient.then((posthog) => {
      posthog?.capture(eventName, { ...eventProperties, $geoip_disable: true });
    });
  }
  return tracked;
}
