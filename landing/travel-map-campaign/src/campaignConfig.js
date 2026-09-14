export const CAMPAIGN_ID = "2026_travel_map";
export const MAPMORY_SITE_ORIGIN = "https://map-mory.com";
export const CAMPAIGN_PATH = "/recap/";
export const CAMPAIGN_URL = `${MAPMORY_SITE_ORIGIN}${CAMPAIGN_PATH}`;
export const MAPMORY_DOMAIN_LABEL = "map-mory.com";
export const GOOGLE_PLAY_PACKAGE_ID = "com.mapmory.android";
export const GOOGLE_PLAY_URL = `https://play.google.com/store/apps/details?id=${GOOGLE_PLAY_PACKAGE_ID}`;

const playInstallReferrer = new URLSearchParams({
  utm_source: "travel_map_campaign",
  utm_medium: "web_campaign",
  utm_campaign: CAMPAIGN_ID,
  utm_content: "demand_primary",
}).toString();

export const APP_ACQUISITION_URL = `${GOOGLE_PLAY_URL}&referrer=${encodeURIComponent(playInstallReferrer)}`;

// Internal navigation is measured by travel_map_landing_click, not acquisition UTMs.
export const CAMPAIGN_LANDING_URL = `${MAPMORY_SITE_ORIGIN}/`;
