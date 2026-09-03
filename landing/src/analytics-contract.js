// New measurements must not be mixed with the historical internal-CTA conversions.
export const ANALYTICS_SCHEMA_VERSION = "2";

export function resolveMeasurementId(config = {}) {
  return config.VITE_GA_MEASUREMENT_ID?.trim() || "";
}

export function canCaptureGa(config = {}, hostname = "") {
  if (!resolveMeasurementId(config)) return false;
  // Production-mode builds also run in local/LAN previews. Opt in explicitly for QA.
  return hostname === "map-mory.com" || hostname === "www.map-mory.com"
    || config.VITE_GA_CAPTURE_LOCAL === "true";
}

export function measurementContext(surface) {
  return { surface, analytics_schema_version: ANALYTICS_SCHEMA_VERSION };
}

export function isScalar(value) {
  return typeof value === "boolean"
    || (typeof value === "number" && Number.isFinite(value))
    || (typeof value === "string" && value.length > 0 && value.length <= 100);
}
