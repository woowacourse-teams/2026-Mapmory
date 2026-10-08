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

// Openly licensed thumbnails (CC0 / public domain / CC BY, EXIF stripped) that fill the hero photo library.
// Credits live in data/photo-credits.json.
const photo = (name) => `/assets/photos/${name}.webp`;
const series = (group, count) => Array.from({ length: count }, (_, index) => photo(`${group}-${String(index + 1).padStart(2, "0")}`));

export const PHOTO_FINDER_LIBRARY_PHOTOS = Object.freeze(series("library", 35));

// Only places the shipping app can search today: Korean provinces and whole countries.
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
    photos: series("japan", 5),
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
    photos: series("jeju", 5),
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
    photos: series("usa", 5),
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
