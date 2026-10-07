// Timeline for the hero demo: library scan -> place search -> matching photos fly to the map.
export const PHOTO_FINDER_LIBRARY_COUNT = 21474;
export const PHOTO_FINDER_MATCH_COUNT = 5;

const FLIGHT_MS = 760;
const FLIGHT_STAGGER_MS = 70;
const SETTLE_MS = 120;

// One source of truth for every beat: the fill follows the first landings, the pin and result arrive last.
export const PHOTO_FINDER_TIMELINE = Object.freeze({
  scanEndMs: 1500,
  typeStepMs: 260,
  matchHoldMs: 700,
  flightMs: FLIGHT_MS,
  flightStaggerMs: FLIGHT_STAGGER_MS,
  flyMs: FLIGHT_MS + (PHOTO_FINDER_MATCH_COUNT - 1) * FLIGHT_STAGGER_MS + SETTLE_MS,
});

// Chip replays keep the same beats in the same order, only quicker.
export const PHOTO_FINDER_REPLAY_TIMELINE = Object.freeze({
  ...PHOTO_FINDER_TIMELINE,
  scanEndMs: 900,
  typeStepMs: 200,
  matchHoldMs: 450,
});

// Small team-owned thumbnails (EXIF stripped) that fill the hero photo library.
export const PHOTO_FINDER_LIBRARY_PHOTOS = Object.freeze([
  "/assets/team-thumbs/team-hapjeong-huiok.webp",
  "/assets/team-thumbs/team-jeju-coast.webp",
  "/assets/team-thumbs/team-shanghai-bund.webp",
  "/assets/team-thumbs/team-tokyo-street.webp",
  "/assets/team-thumbs/team-usa-antelope-canyon.webp",
  "/assets/team-thumbs/team-usa-bryce-canyon.webp",
  "/assets/team-thumbs/team-usa-las-vegas-day.webp",
  "/assets/team-thumbs/team-usa-las-vegas-fountain.webp",
  "/assets/team-thumbs/team-usa-las-vegas-venetian.webp",
  "/assets/team-thumbs/team-yeosu-mochi.webp",
]);

// Only places the shipping app can search today: Korean provinces and whole countries.
// crops are [x%, y%, zoom] so one walk reads as several different shots.
export const PHOTO_FINDER_PLACES = Object.freeze([
  {
    key: "japan",
    query: "일본",
    label: "일본",
    scope: "world",
    regionCode: "392",
    target: [138.6, 36.4],
    view: { minLng: 120, maxLng: 150, minLat: 26, maxLat: 46 },
    foundCount: 86,
    photos: ["/assets/team-thumbs/team-tokyo-street.webp"],
    // One Tokyo walk, five subjects: wide street (the only crop with the signal), pole and wires, shop signs, rooftop, storefront.
    crops: [[50, 40, 1], [100, 18, 2.4], [82, 99, 2.86], [47, 0, 2.67], [15, 100, 2.18]],
  },
  {
    key: "jeju",
    query: "제주",
    label: "제주",
    scope: "korea",
    regionCode: "KR-49",
    target: [126.55, 33.38],
    view: { minLng: 124.6, maxLng: 129.6, minLat: 33.0, maxLat: 35.1 },
    foundCount: 31,
    photos: ["/assets/team-thumbs/team-jeju-coast.webp"],
    crops: [[50, 20, 1.3], [30, 55, 1.8], [75, 85, 1.7], [50, 50, 1], [15, 35, 2]],
  },
  {
    key: "usa",
    query: "미국",
    label: "미국",
    scope: "world",
    regionCode: "840",
    target: [-112.5, 37.4],
    view: { minLng: -128, maxLng: -64, minLat: 22, maxLat: 52 },
    foundCount: 52,
    photos: [
      "/assets/team-thumbs/team-usa-bryce-canyon.webp",
      "/assets/team-thumbs/team-usa-antelope-canyon.webp",
      "/assets/team-thumbs/team-usa-las-vegas-day.webp",
      "/assets/team-thumbs/team-usa-las-vegas-fountain.webp",
      "/assets/team-thumbs/team-usa-las-vegas-venetian.webp",
    ],
    crops: [[50, 50, 1], [50, 50, 1], [50, 50, 1], [50, 50, 1], [50, 50, 1]],
  },
]);

export function getPhotoFinderDuration(query, timeline = PHOTO_FINDER_TIMELINE) {
  const { scanEndMs, typeStepMs, matchHoldMs, flyMs } = timeline;
  return scanEndMs + query.length * typeStepMs + matchHoldMs + flyMs;
}

export function getPhotoFinderState(elapsedMs, query, timeline = PHOTO_FINDER_TIMELINE) {
  const { scanEndMs, typeStepMs, matchHoldMs, flightMs, flyMs } = timeline;
  const typeEndMs = scanEndMs + query.length * typeStepMs;
  const matchEndMs = typeEndMs + matchHoldMs;
  const flyEndMs = matchEndMs + flyMs;
  const elapsed = Math.max(0, elapsedMs);
  const scanned = Math.min(1, elapsed / scanEndMs);

  let phase = "scan";
  if (elapsed >= flyEndMs) phase = "done";
  else if (elapsed >= matchEndMs) phase = "fly";
  else if (elapsed >= typeEndMs) phase = "match";
  else if (elapsed >= scanEndMs) phase = "type";

  const typedLength = phase === "scan"
    ? 0
    : Math.min(query.length, Math.floor((elapsed - scanEndMs) / typeStepMs) + 1);

  return {
    phase,
    typedQuery: query.slice(0, typedLength),
    scannedCount: Math.round(PHOTO_FINDER_LIBRARY_COUNT * (1 - (1 - scanned) ** 3)),
    // The fill starts as the first photo is about to land.
    isMapFilled: phase === "done" || (phase === "fly" && elapsed >= matchEndMs + flightMs * 0.85),
  };
}
