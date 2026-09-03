import exifr from "exifr";
import { getJourneyMotionDuration } from "./journeyProgress.js";
import { parsePhotoBatches, validatePhotoSelection } from "./photoProcessing.js";
import { publicAssetUrl } from "./publicAssets.js";

const HOURS = 60 * 60 * 1000;
export const SHARE_INTRO_SECONDS = 0.5;
export const SHARE_OUTRO_SECONDS = 1;
export const NEARBY_PHOTO_CLUSTER_RADIUS_METERS = 50;
export const NEARBY_PHOTO_CLUSTER_MAX_GAP_MINUTES = 60;
const IMAGE_EXTENSION_PATTERN = /\.(?:jpe?g|heic|heif|png|tiff?|avif|webp)$/i;
let exifReaderPromise;
const playbackJourneyCache = new WeakMap();

function getExifReader() {
  exifReaderPromise ??= import("exifreader").then((module) => module.default);
  return exifReaderPromise;
}

function firstDefined(...values) {
  return values.find((value) => value !== undefined && value !== null && value !== "");
}

export function normalizeGpsCoordinate(value, ref, axis) {
  const rawValue = value && typeof value === "object" && !Array.isArray(value) && "value" in value
    ? value.value
    : value;
  const rawRef = String(ref ?? "").trim().toUpperCase();
  const inlineRef = typeof rawValue === "string" ? rawValue.trim().match(/[NSEW]$/i)?.[0]?.toUpperCase() : "";
  const direction = inlineRef || rawRef;
  let coordinate;

  if (typeof rawValue === "number") {
    coordinate = rawValue;
  } else if (Array.isArray(rawValue)) {
    const [degrees, minutes = 0, seconds = 0] = rawValue.map(Number);
    if ([degrees, minutes, seconds].every(Number.isFinite)) {
      coordinate = Math.abs(degrees) + minutes / 60 + seconds / 3600;
      if (degrees < 0) coordinate *= -1;
    }
  } else if (typeof rawValue === "string") {
    const cleaned = rawValue.trim().replace(/[NSEW]$/i, "").trim();
    const parts = cleaned.match(/-?\d+(?:\.\d+)?/g)?.map(Number) ?? [];
    if (parts.length === 1) {
      coordinate = parts[0];
    } else if (parts.length >= 2) {
      coordinate = Math.abs(parts[0]) + parts[1] / 60 + (parts[2] ?? 0) / 3600;
      if (parts[0] < 0) coordinate *= -1;
    }
  }

  if (!Number.isFinite(coordinate)) return Number.NaN;
  if ((direction === "S" || direction === "W") && coordinate > 0) coordinate *= -1;
  const limit = axis === "latitude" ? 90 : 180;
  return Math.abs(coordinate) <= limit ? coordinate : Number.NaN;
}

const knownPlaces = [
  { name: "서울", lat: 37.5665, lng: 126.978 },
  { name: "여수", lat: 34.7604, lng: 127.6622 },
  { name: "제주", lat: 33.4996, lng: 126.5312 },
  { name: "상하이", lat: 31.2304, lng: 121.4737 },
  { name: "도쿄", lat: 35.6762, lng: 139.6503 },
  { name: "오사카", lat: 34.6937, lng: 135.5023 },
  { name: "삿포로", lat: 43.0618, lng: 141.3545 },
];

export const demoJourney = {
  source: "demo",
  photoCount: 184,
  supportedPhotoCount: 184,
  validPhotoCount: 184,
  missingGpsCount: 0,
  metadataReadCount: 184,
  metadataMissingCount: 0,
  parseFailedCount: 0,
  gpsReadFailedCount: 0,
  readFailedCount: 0,
  unsupportedCount: 0,
  formats: ["JPG"],
  points: [
    { id: "seoul", name: "서울", lat: 37.549, lng: 126.914, date: new Date("2026-01-03T11:10:00+09:00"), image: publicAssetUrl("team-hapjeong-huiok.jpg"), tripId: 0 },
    { id: "yeosu", name: "여수", lat: 34.7604, lng: 127.6622, date: new Date("2026-02-14T15:30:00+09:00"), image: publicAssetUrl("team-yeosu-mochi.jpg"), tripId: 1 },
    { id: "jeju", name: "제주", lat: 33.4996, lng: 126.5312, date: new Date("2026-03-20T17:20:00+09:00"), image: publicAssetUrl("team-jeju-coast.jpg"), tripId: 2 },
    { id: "shanghai", name: "상하이", lat: 31.2304, lng: 121.4737, date: new Date("2026-04-10T19:10:00+08:00"), image: publicAssetUrl("team-shanghai-bund.jpg"), tripId: 3 },
    { id: "tokyo", name: "도쿄", lat: 35.6762, lng: 139.6503, date: new Date("2026-08-24T16:40:00+09:00"), image: publicAssetUrl("team-tokyo-street.jpeg"), tripId: 4 },
  ],
};

export function distanceKm(a, b) {
  const toRadians = (value) => (value * Math.PI) / 180;
  const earthRadiusKm = 6371;
  const deltaLat = toRadians(b.lat - a.lat);
  const deltaLng = toRadians(b.lng - a.lng);
  const lat1 = toRadians(a.lat);
  const lat2 = toRadians(b.lat);
  const halfLat = Math.sin(deltaLat / 2);
  const halfLng = Math.sin(deltaLng / 2);
  const value = halfLat * halfLat + Math.cos(lat1) * Math.cos(lat2) * halfLng * halfLng;
  return 2 * earthRadiusKm * Math.asin(Math.sqrt(value));
}

function hasFiniteGpsCoordinates(point) {
  return Number.isFinite(point?.lat) && Number.isFinite(point?.lng);
}

function nearestPlace(point) {
  let nearest = null;
  let nearestDistance = Number.POSITIVE_INFINITY;

  for (const place of knownPlaces) {
    const candidateDistance = distanceKm(point, place);
    if (candidateDistance < nearestDistance) {
      nearest = place;
      nearestDistance = candidateDistance;
    }
  }

  if (nearestDistance <= 90) return nearest.name;
  return `${point.lat.toFixed(2)}, ${point.lng.toFixed(2)}`;
}

export function getJourneyStats(journey) {
  const statisticalPoints = journey.allPoints ?? journey.points;
  const trips = new Set(statisticalPoints.map((point) => point.tripId)).size;
  const cities = new Set(statisticalPoints.map((point) => point.name)).size;
  return { trips, cities, photos: journey.photoCount };
}

export function selectJourneyHighlights(points, maxCount = 5) {
  const chronologicalPoints = [...points].sort((a, b) => a.date - b.date);
  const numericMaxCount = Number(maxCount);
  const limit = Number.isFinite(numericMaxCount) ? Math.max(1, Math.floor(numericMaxCount)) : 5;
  if (chronologicalPoints.length <= limit) return chronologicalPoints;
  if (limit === 1) return [chronologicalPoints[0]];

  return Array.from({ length: limit }, (_, index) => {
    const pointIndex = Math.round(index * (chronologicalPoints.length - 1) / (limit - 1));
    return chronologicalPoints[pointIndex];
  });
}

export function clusterNearbyPhotoPoints(
  points,
  {
    radiusMeters = NEARBY_PHOTO_CLUSTER_RADIUS_METERS,
    maxGapMinutes = NEARBY_PHOTO_CLUSTER_MAX_GAP_MINUTES,
  } = {},
) {
  const chronologicalPoints = [...points].sort((a, b) => a.date - b.date);
  if (chronologicalPoints.length <= 1) return chronologicalPoints;

  const maximumDistanceKm = radiusMeters / 1000;
  const maximumGapMilliseconds = maxGapMinutes * 60 * 1000;
  const representatives = [];
  let anchor = chronologicalPoints[0];
  let previous = anchor;

  representatives.push(anchor);

  for (const point of chronologicalPoints.slice(1)) {
    const timeGap = point.date - previous.date;
    const belongsToCurrentCluster = point.tripId === anchor.tripId
      && hasFiniteGpsCoordinates(anchor)
      && hasFiniteGpsCoordinates(point)
      && Number.isFinite(timeGap)
      && timeGap >= 0
      && timeGap <= maximumGapMilliseconds
      && distanceKm(anchor, point) <= maximumDistanceKm;

    if (!belongsToCurrentCluster) {
      anchor = point;
      representatives.push(anchor);
    }

    previous = point;
  }

  return representatives;
}

export function createPlaybackJourney(journey) {
  const cachedJourney = playbackJourneyCache.get(journey);
  if (cachedJourney) return cachedJourney;

  const allPoints = journey.allPoints ?? journey.points;
  const clusteredPoints = clusterNearbyPhotoPoints(allPoints);
  const playbackJourney = {
    ...journey,
    allPoints,
    points: selectJourneyHighlights(clusteredPoints),
  };
  playbackJourneyCache.set(journey, playbackJourney);
  playbackJourneyCache.set(playbackJourney, playbackJourney);
  return playbackJourney;
}

export function getAutoDuration(journey) {
  const pointCount = createPlaybackJourney(journey).points.length;
  return getJourneyMotionDuration(pointCount) + SHARE_INTRO_SECONDS + SHARE_OUTRO_SECONDS;
}

export function getReplayDuration(journey) {
  const pointCount = createPlaybackJourney(journey).points.length;
  return getJourneyMotionDuration(pointCount);
}

export function formatShortDate(date) {
  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(date).replaceAll(". ", ".").replace(/\.$/, "");
}

export async function analyzePhotoFiles(fileList) {
  const selectedFiles = Array.from(fileList);
  validatePhotoSelection(selectedFiles);
  const files = selectedFiles.filter((file) => file.type.startsWith("image/") || IMAGE_EXTENSION_PATTERN.test(file.name));
  const unsupportedFiles = selectedFiles.filter((file) => !files.includes(file));
  const objectUrls = [];
  const parsed = await parsePhotoBatches(files, async (file, index) => {
    let metadata = null;
    let metadataError = null;
    let gpsMetadata = null;
    let gpsError = null;
    let fallbackMetadata = null;
    let fallbackError = null;
    let arrayBufferPromise = null;
    const getArrayBuffer = () => {
      arrayBufferPromise ??= file.arrayBuffer();
      return arrayBufferPromise;
    };
    const getByteInput = () => {
      return getArrayBuffer().then((buffer) => new Uint8Array(buffer));
    };
    const metadataOptions = {
      gps: true,
      tiff: true,
      xmp: true,
      exif: true,
      ifd0: true,
      interop: false,
      makerNote: false,
      userComment: false,
    };
    try {
      metadata = await exifr.parse(file, metadataOptions);
    } catch {
      try {
        metadata = await exifr.parse(await getByteInput(), metadataOptions);
      } catch (error) {
        metadataError = error;
        metadata = null;
      }
    }

    try {
      gpsMetadata = await exifr.gps(file);
    } catch {
      try {
        gpsMetadata = await exifr.gps(await getByteInput());
      } catch (error) {
        gpsError = error;
      }
    }

    const primaryLat = firstDefined(gpsMetadata?.latitude, metadata?.latitude, metadata?.GPSLatitude, metadata?.LocationLatitude);
    const primaryLng = firstDefined(gpsMetadata?.longitude, metadata?.longitude, metadata?.GPSLongitude, metadata?.LocationLongitude);
    if (primaryLat === undefined || primaryLng === undefined) {
      try {
        const ExifReader = await getExifReader();
        fallbackMetadata = await ExifReader.load(file, {
          expanded: true,
          computed: true,
          includeOffsets: true,
          length: "auto",
        });
      } catch {
        try {
          const ExifReader = await getExifReader();
          fallbackMetadata = await ExifReader.load(await getArrayBuffer(), { expanded: true, computed: true });
        } catch (error) {
          fallbackError = error;
        }
      }
    }

    const fallbackHasMetadata = Boolean(fallbackMetadata?.exif || fallbackMetadata?.xmp || fallbackMetadata?.gps);
    const dateValue = metadata?.DateTimeOriginal
      ?? metadata?.CreateDate
      ?? metadata?.ModifyDate
      ?? fallbackMetadata?.exif?.DateTimeOriginal?.description
      ?? fallbackMetadata?.xmp?.DateTimeOriginal?.description
      ?? file.lastModified;
    const date = dateValue instanceof Date ? dateValue : new Date(dateValue || Date.now());
    const lat = normalizeGpsCoordinate(
      firstDefined(primaryLat, fallbackMetadata?.gps?.Latitude),
      firstDefined(metadata?.GPSLatitudeRef, metadata?.LatitudeRef),
      "latitude",
    );
    const lng = normalizeGpsCoordinate(
      firstDefined(primaryLng, fallbackMetadata?.gps?.Longitude),
      firstDefined(metadata?.GPSLongitudeRef, metadata?.LongitudeRef),
      "longitude",
    );
    const hasGps = Number.isFinite(lat) && Number.isFinite(lng) && Math.abs(lat) <= 90 && Math.abs(lng) <= 180;
    const extension = file.name.includes(".") ? file.name.split(".").pop().toUpperCase() : "알 수 없음";

    const image = URL.createObjectURL(file);
    objectUrls.push(image);
    return {
      id: `${file.name}-${file.lastModified}-${index}`,
      file,
      image,
      date: Number.isNaN(date.getTime()) ? new Date(file.lastModified || Date.now()) : date,
      lat,
      lng,
      hasGps,
      extension,
      metadataStatus: metadata || fallbackHasMetadata ? "read" : metadataError && fallbackError ? "failed" : "missing",
      gpsStatus: hasGps ? "found" : gpsError && fallbackError ? "failed" : "missing",
    };
  }).catch((error) => {
    objectUrls.forEach((url) => URL.revokeObjectURL(url));
    throw error;
  });

  const validPoints = parsed
    .filter((photo) => photo.hasGps)
    .sort((a, b) => a.date - b.date)
    .map((photo) => ({ ...photo, name: nearestPlace(photo) }));

  let tripId = 0;
  const points = validPoints.map((point, index) => {
    if (index > 0) {
      const previous = validPoints[index - 1];
      const gapHours = (point.date - previous.date) / HOURS;
      if (gapHours > 72) tripId += 1;
    }
    return { ...point, tripId };
  });

  return {
    source: "photos",
    photoCount: selectedFiles.length,
    supportedPhotoCount: files.length,
    validPhotoCount: points.length,
    missingGpsCount: parsed.filter((photo) => photo.gpsStatus === "missing").length,
    metadataReadCount: parsed.filter((photo) => photo.metadataStatus === "read").length,
    metadataMissingCount: parsed.filter((photo) => photo.metadataStatus === "missing").length,
    parseFailedCount: parsed.filter((photo) => photo.metadataStatus === "failed").length,
    gpsReadFailedCount: parsed.filter((photo) => photo.gpsStatus === "failed").length,
    readFailedCount: parsed.filter((photo) => photo.metadataStatus === "failed" || photo.gpsStatus === "failed").length,
    unsupportedCount: unsupportedFiles.length,
    formats: [...new Set([...parsed.map((photo) => photo.extension), ...unsupportedFiles.map((file) => file.name.includes(".") ? file.name.split(".").pop().toUpperCase() : "알 수 없음")])],
    points,
    objectUrls: parsed.map((photo) => photo.image),
  };
}
