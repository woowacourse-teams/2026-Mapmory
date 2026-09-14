import assert from "node:assert/strict";
import { File } from "node:buffer";
import { readFile } from "node:fs/promises";
import test from "node:test";
import {
  analyzePhotoFiles,
  clusterNearbyPhotoPoints,
  createPlaybackJourney,
  demoJourney,
  getAutoDuration,
  getJourneyStats,
  getReplayDuration,
  normalizeGpsCoordinate,
  selectJourneyHighlights,
} from "../src/journeyData.js";

const fixturePath = new URL("../public/assets/team-jeju-coast.jpg", import.meta.url);

function point({
  id,
  minute = 0,
  lat = 37.5665,
  lng = 126.978,
  tripId = 0,
  name = "서울",
}) {
  return {
    id,
    image: `/${id}.jpg`,
    date: new Date(Date.UTC(2026, 0, 1, 0, minute)),
    lat,
    lng,
    tripId,
    name,
  };
}

test("accepts an image extension even when the browser leaves MIME blank", async () => {
  const bytes = await readFile(fixturePath);
  const file = new File([bytes], "team-jeju-coast.jpg", { type: "" });
  const result = await analyzePhotoFiles([file]);

  assert.equal(result.photoCount, 1);
  assert.equal(result.supportedPhotoCount, 1);
  assert.deepEqual(result.formats, ["JPG"]);
  assert.equal(result.validPhotoCount, 0);
  assert.equal(result.metadataReadCount + result.metadataMissingCount + result.parseFailedCount, 1);
  assert.equal(result.parseFailedCount, 0);
  assert.equal(result.gpsReadFailedCount, 0);

  result.objectUrls.forEach((url) => URL.revokeObjectURL(url));
});

test("normalizes EXIF and XMP GPS coordinate formats", () => {
  assert.equal(normalizeGpsCoordinate([33, 29, 58.56], "N", "latitude"), 33.4996);
  assert.equal(normalizeGpsCoordinate("126,31.872E", "", "longitude"), 126.5312);
  assert.equal(normalizeGpsCoordinate("37.5665", "N", "latitude"), 37.5665);
  assert.equal(normalizeGpsCoordinate(122.4194, "W", "longitude"), -122.4194);
  assert.equal(Number.isNaN(normalizeGpsCoordinate("not-a-coordinate", "", "latitude")), true);
});

test("keeps the sample and share video short", () => {
  assert.equal(getReplayDuration(demoJourney), 3.5);
  assert.equal(getAutoDuration(demoJourney), 5);
});

test("selects at most five chronological highlights and keeps both endpoints", () => {
  const points = Array.from({ length: 10 }, (_, index) => ({
    id: `point-${index}`,
    date: new Date(`2026-01-${String(index + 1).padStart(2, "0")}T10:00:00+09:00`),
  }));
  const highlights = selectJourneyHighlights(points);

  assert.equal(highlights.length, 5);
  assert.equal(highlights[0].id, "point-0");
  assert.equal(highlights.at(-1).id, "point-9");
  assert.deepEqual(highlights.map((point) => point.id), ["point-0", "point-2", "point-5", "point-7", "point-9"]);
});

test("keeps zero-to-five point journeys intact and safely handles custom limits", () => {
  for (let count = 0; count <= 5; count += 1) {
    const points = Array.from({ length: count }, (_, index) => ({ id: index, date: new Date(2026, 0, index + 1) }));
    assert.deepEqual(selectJourneyHighlights(points).map((point) => point.id), points.map((point) => point.id));
  }

  const thirtyPoints = Array.from({ length: 30 }, (_, index) => ({ id: index, date: new Date(2026, 0, index + 1) }));
  assert.equal(selectJourneyHighlights(thirtyPoints).length, 5);
  assert.deepEqual(selectJourneyHighlights(thirtyPoints, 1).map((point) => point.id), [0]);
  assert.equal(selectJourneyHighlights(thirtyPoints, Number.NaN).length, 5);
});

test("keeps complete journey statistics when playback uses highlights", () => {
  const points = Array.from({ length: 8 }, (_, index) => ({
    id: `point-${index}`,
    name: `도시 ${index}`,
    tripId: index < 4 ? 0 : 1,
    date: new Date(`2026-02-${String(index + 1).padStart(2, "0")}T10:00:00+09:00`),
  }));
  const playbackJourney = createPlaybackJourney({ photoCount: 30, points });

  assert.equal(playbackJourney.points.length, 5);
  assert.deepEqual(getJourneyStats(playbackJourney), { trips: 2, cities: 8, photos: 30 });
});

test("clusters a same-place photo burst and keeps the earliest representative", () => {
  const photos = Array.from({ length: 5 }, (_, index) => point({
    id: `burst-${index}`,
    minute: index * 10,
  }));

  const clustered = clusterNearbyPhotoPoints(photos);

  assert.deepEqual(clustered.map(({ id }) => id), ["burst-0"]);
  assert.strictEqual(clustered[0], photos[0]);
});

test("requires both the distance and time thresholds for a nearby cluster", () => {
  const latitudeDegreeInMeters = 111_195;
  const origin = point({ id: "origin" });
  const withinBoth = point({
    id: "within-both",
    minute: 60,
    lat: origin.lat + (49 / latitudeDegreeInMeters),
  });
  const outsideDistance = point({
    id: "outside-distance",
    minute: 61,
    lat: origin.lat + (51 / latitudeDegreeInMeters),
  });
  const outsideTime = point({
    id: "outside-time",
    minute: 122,
    lat: outsideDistance.lat,
  });

  const clustered = clusterNearbyPhotoPoints([outsideTime, withinBoth, origin, outsideDistance]);

  assert.deepEqual(clustered.map(({ id }) => id), ["origin", "outside-distance", "outside-time"]);
});

test("never clusters matching coordinates across trip boundaries", () => {
  const photos = [
    point({ id: "trip-zero", minute: 0, tripId: 0 }),
    point({ id: "trip-one", minute: 1, tripId: 1 }),
  ];

  assert.deepEqual(clusterNearbyPhotoPoints(photos).map(({ id }) => id), ["trip-zero", "trip-one"]);
});

test("does not cluster points without finite GPS coordinates", () => {
  const photos = [
    { ...point({ id: "missing-first", minute: 0 }), lat: undefined, lng: undefined },
    { ...point({ id: "missing-second", minute: 1 }), lat: undefined, lng: undefined },
    { ...point({ id: "invalid-third", minute: 2 }), lat: "37.5665", lng: "126.978" },
  ];

  assert.deepEqual(
    clusterNearbyPhotoPoints(photos).map(({ id }) => id),
    ["missing-first", "missing-second", "invalid-third"],
  );
});

test("uses the cluster anchor to prevent transitive geographic drift", () => {
  const latitudeDegreeInMeters = 111_195;
  const photos = [
    point({ id: "zero-meters", minute: 0 }),
    point({ id: "forty-meters", minute: 10, lat: 37.5665 + (40 / latitudeDegreeInMeters) }),
    point({ id: "eighty-meters", minute: 20, lat: 37.5665 + (80 / latitudeDegreeInMeters) }),
  ];

  assert.deepEqual(clusterNearbyPhotoPoints(photos).map(({ id }) => id), ["zero-meters", "eighty-meters"]);
});

test("clusters before selecting highlights while preserving every statistical point", () => {
  const latitudeDegreeInMeters = 111_195;
  const allPoints = Array.from({ length: 6 }, (_, clusterIndex) => {
    const lat = 37.5665 + ((clusterIndex * 120) / latitudeDegreeInMeters);
    return [
      point({ id: `cluster-${clusterIndex}-first`, minute: clusterIndex * 10, lat, name: `장소 ${clusterIndex}` }),
      point({ id: `cluster-${clusterIndex}-second`, minute: clusterIndex * 10 + 1, lat, name: `장소 ${clusterIndex}` }),
    ];
  }).flat();

  const playbackJourney = createPlaybackJourney({ photoCount: 12, points: allPoints });

  assert.equal(playbackJourney.allPoints.length, 12);
  assert.equal(playbackJourney.points.length, 5);
  assert.equal(playbackJourney.points[0].id, "cluster-0-first");
  assert.equal(playbackJourney.points.at(-1).id, "cluster-5-first");
  assert.equal(playbackJourney.points.some(({ id }) => id.endsWith("-second")), false);
  assert.deepEqual(getJourneyStats(playbackJourney), { trips: 1, cities: 6, photos: 12 });
});

test("uses clustered playback points for replay and share duration", () => {
  const photos = [
    point({ id: "same-place-first", minute: 0 }),
    point({ id: "same-place-second", minute: 1 }),
    point({ id: "same-place-third", minute: 2 }),
  ];
  const journey = { photoCount: photos.length, points: photos };
  const playbackJourney = createPlaybackJourney(journey);

  assert.equal(playbackJourney.points.length, 1);
  assert.strictEqual(createPlaybackJourney(journey), playbackJourney);
  assert.strictEqual(createPlaybackJourney(playbackJourney), playbackJourney);
  assert.equal(getReplayDuration(journey), 0.6);
  assert.equal(getReplayDuration(playbackJourney), getReplayDuration(journey));
  assert.equal(getAutoDuration(journey), 2.1);
  assert.equal(getAutoDuration(playbackJourney), getAutoDuration(journey));
});

test("reports an unsupported file separately from a GPS-free image", async () => {
  const file = new File(["not an image"], "notes.txt", { type: "text/plain" });
  const result = await analyzePhotoFiles([file]);

  assert.equal(result.photoCount, 1);
  assert.equal(result.supportedPhotoCount, 0);
  assert.equal(result.unsupportedCount, 1);
  assert.equal(result.validPhotoCount, 0);
  assert.deepEqual(result.formats, ["TXT"]);
});
