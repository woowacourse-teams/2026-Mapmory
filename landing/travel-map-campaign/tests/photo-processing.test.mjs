import assert from "node:assert/strict";
import test from "node:test";
import exifr from "exifr";
import { analyzePhotoFiles } from "../src/journeyData.js";
import { PHOTO_SELECTION_LIMITS as limits, parsePhotoBatches, validatePhotoSelection } from "../src/photoProcessing.js";

test("selection count and byte boundaries accept exact limits without truncation", () => {
  assert.doesNotThrow(() => validatePhotoSelection(Array.from({ length: limits.count }, () => ({ size: 0 }))));
  assert.doesNotThrow(() => validatePhotoSelection(Array.from({ length: 10 }, () => ({ size: limits.fileBytes }))));
  assert.throws(() => validatePhotoSelection([{ size: limits.fileBytes + 1 }]), /50MB/);
  assert.throws(() => validatePhotoSelection([...Array.from({ length: 10 }, () => ({ size: limits.fileBytes })), { size: 1 }]), /500MB/);
});

test("oversized selections fail before inspecting metadata or allocating object URLs", async () => {
  const untouched = { size: 0, get type() { assert.fail("must not inspect file type before validation"); } };
  await assert.rejects(analyzePhotoFiles(Array(limits.count + 1).fill(untouched)), /200장/);
  await assert.rejects(analyzePhotoFiles([{ size: limits.fileBytes + 1 }]), /50MB/);
  await assert.rejects(analyzePhotoFiles(Array(11).fill({ size: limits.fileBytes })), /500MB/);
});

test("batches bound concurrency, preserve order and original indexes", async () => {
  let active = 0;
  let peak = 0;
  const items = Array.from({ length: 10 }, (_, index) => index);
  const parsed = await parsePhotoBatches(items, async (item, index) => {
    active += 1;
    peak = Math.max(peak, active);
    await new Promise((resolve) => setTimeout(resolve, 5 - index % 3));
    active -= 1;
    assert.equal(item, index);
    return item * 2;
  });
  assert.equal(peak, limits.concurrency);
  assert.equal(active, 0);
  assert.deepEqual(parsed, items.map((item) => item * 2));
});

test("a failed batch settles its siblings and does not start subsequent files", async () => {
  const started = [];
  const finished = [];
  await assert.rejects(parsePhotoBatches([0, 1, 2, 3, 4], async (item) => {
    started.push(item);
    if (item === 0) throw new Error("parse failed");
    await new Promise((resolve) => setTimeout(resolve, 5));
    finished.push(item);
    return item;
  }), /parse failed/);
  assert.deepEqual(started, [0, 1, 2]);
  assert.deepEqual(finished.sort(), [1, 2]);
});

test("real analysis retains GPS, file indexes, statistics and all accepted photos", async (t) => {
  let active = 0;
  let peak = 0;
  t.mock.method(exifr, "parse", async () => {
    peak = Math.max(peak, ++active);
    await new Promise((resolve) => setTimeout(resolve, 3));
    active -= 1;
    return { DateTimeOriginal: new Date("2026-01-01T00:00:00Z"), latitude: 37.5, longitude: 127 };
  });
  t.mock.method(exifr, "gps", async () => ({ latitude: 37.5, longitude: 127 }));
  const files = Array.from({ length: 8 }, (_, index) => new File(["fixture"], `${index}.jpg`, { type: "image/jpeg", lastModified: index + 1 }));
  const result = await analyzePhotoFiles(files);
  t.after(() => result.objectUrls.forEach((url) => URL.revokeObjectURL(url)));
  assert.equal(peak, limits.concurrency);
  assert.equal(result.photoCount, 8);
  assert.equal(result.validPhotoCount, 8);
  assert.equal(result.metadataReadCount, 8);
  assert.equal(result.objectUrls.length, 8);
  assert.deepEqual(result.points.map((point) => point.file), files);
  assert.equal(result.points[7].id, "7.jpg-8-7");
});
