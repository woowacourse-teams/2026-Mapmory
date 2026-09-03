import assert from "node:assert/strict";
import test from "node:test";
import { createMapProjection, drawJourneyMap, getCachedImage, loadJourneyImages } from "../src/mapRenderer.js";
import { renderJourneyVideo, renderShareImage } from "../src/videoRenderer.js";

const point = { lat: 37.5, lng: 127, name: "서울", tripId: 0, date: new Date("2026-01-01T00:00:00Z") };
const journey = { photoCount: 1, points: [point] };

function globals(t, values) {
  for (const [key, value] of Object.entries(values)) {
    const original = Object.getOwnPropertyDescriptor(globalThis, key);
    Object.defineProperty(globalThis, key, { configurable: true, writable: true, value });
    t.after(() => original ? Object.defineProperty(globalThis, key, original) : delete globalThis[key]);
  }
}

function context() {
  return new Proxy({}, { get: (target, key) => key in target ? target[key] : () => {} });
}

test("an empty journey draws finite world geometry before photos are selected", () => {
  let coordinatesDrawn = 0;
  const ctx = context();
  for (const method of ["moveTo", "lineTo", "arc"]) {
    ctx[method] = (...coordinates) => {
      assert.ok(coordinates.every(Number.isFinite), `${method} received non-finite coordinates`);
      coordinatesDrawn += 1;
    };
  }
  const projection = drawJourneyMap(ctx, 390, 400, [], 0);
  assert.ok(Number.isFinite(projection.scale()) && projection.scale() > 0);
  assert.ok(projection([0, 0]).every(Number.isFinite));
  assert.ok(coordinatesDrawn > 0);
});

test("journey points still determine the non-empty map extent", () => {
  const points = [point, { ...point, lng: 128, lat: 38 }];
  const world = createMapProjection(390, 400, []);
  const journeyProjection = createMapProjection(390, 400, points);
  assert.ok(journeyProjection.scale() > world.scale());
  for (const entry of points) {
    const [x, y] = journeyProjection([entry.lng, entry.lat]);
    assert.ok(Number.isFinite(x) && Number.isFinite(y));
    assert.ok(x >= 43.99 && x <= 346.01 && y >= 43.99 && y <= 356.01);
  }
});

test("in-flight image loads keep old/new map redraws and concurrent export waiters", async (t) => {
  const images = [];
  class FakeImage extends EventTarget {
    complete = false;
    naturalWidth = 0;
    naturalHeight = 0;
    constructor() { super(); images.push(this); }
  }
  globals(t, { Image: FakeImage });
  const points = [{ ...point, image: "fixture-concurrent-image" }];
  const first = t.mock.fn();
  const returning = t.mock.fn();
  drawJourneyMap(context(), 320, 400, points, 1, first);
  const exportOne = loadJourneyImages(points);
  const exportTwo = loadJourneyImages(points);
  drawJourneyMap(context(), 320, 400, points, 1, returning);
  drawJourneyMap(context(), 320, 400, points, 1, returning);
  assert.equal(images.length, 1);
  const [image] = images;
  image.complete = true;
  image.naturalWidth = image.naturalHeight = 100;
  image.dispatchEvent(new Event("load"));
  await Promise.all([exportOne, exportTwo]);
  assert.equal(first.mock.callCount(), 1);
  assert.equal(returning.mock.callCount(), 1);
  assert.equal(getCachedImage(points[0].image), image);
  image.dispatchEvent(new Event("load"));
  assert.equal(returning.mock.callCount(), 1);
});

test("failed images settle waiters without being returned as drawable images", async (t) => {
  let image;
  class FakeImage extends EventTarget {
    complete = false;
    naturalWidth = 0;
    constructor() { super(); image = this; }
  }
  globals(t, { Image: FakeImage });
  const points = [{ ...point, image: "fixture-error-image" }];
  const waiting = loadJourneyImages(points);
  image.complete = true;
  image.dispatchEvent(new Event("error"));
  await waiting;
  await loadJourneyImages(points);
  assert.equal(getCachedImage(points[0].image), null);
  drawJourneyMap(context(), 320, 400, points, 1);
});

function recordingEnvironment(t, failure = "") {
  const stopTrack = t.mock.fn();
  class FakeCanvas {
    getContext() { return context(); }
    captureStream() { return { getTracks: () => [{ stop: stopTrack }] }; }
    toBlob(callback) { callback(failure === "image" ? null : new Blob(["image"], { type: "image/png" })); }
  }
  class FakeRecorder {
    state = "inactive";
    static isTypeSupported() { return true; }
    constructor() { if (failure === "constructor") throw new Error("constructor failed"); }
    start() {
      if (failure === "start") throw new Error("start failed");
      this.state = "recording";
      if (failure === "event") queueMicrotask(() => this.onerror?.());
    }
    stop() {
      this.state = "inactive";
      if (failure === "stop") throw new Error("stop failed");
      this.ondataavailable?.({ data: new Blob(["video"]) });
      this.onstop?.();
    }
  }
  globals(t, {
    window: { MediaRecorder: FakeRecorder },
    MediaRecorder: FakeRecorder,
    HTMLCanvasElement: FakeCanvas,
    document: { createElement: () => new FakeCanvas() },
    requestAnimationFrame: (callback) => setTimeout(() => callback(performance.now() + 100_000), 0),
    cancelAnimationFrame: clearTimeout,
  });
  return stopTrack;
}

test("successful recording returns a blob and stops capture tracks", async (t) => {
  const stopTrack = recordingEnvironment(t);
  const result = await renderJourneyVideo(journey);
  assert.ok(result.size > 0);
  assert.match(result.type, /^video\//);
  assert.equal(stopTrack.mock.callCount(), 1);
});

for (const failure of ["constructor", "start", "event", "stop", "draw"]) {
  test(`recording ${failure} failure rejects and releases capture tracks`, async (t) => {
    const stopTrack = recordingEnvironment(t, failure);
    const progress = () => { if (failure === "draw") throw new Error("draw failed"); };
    await assert.rejects(renderJourneyVideo(journey, undefined, progress));
    assert.equal(stopTrack.mock.callCount(), 1);
  });
}

test("PNG export rejects missing blobs and preserves successful exports", async (t) => {
  recordingEnvironment(t, "image");
  await assert.rejects(renderShareImage(journey), /이미지를 만들지 못했어요/);
});

test("PNG export returns a valid blob on success", async (t) => {
  recordingEnvironment(t);
  const blob = await renderShareImage(journey);
  assert.equal(blob.type, "image/png");
  assert.ok(blob.size > 0);
});
