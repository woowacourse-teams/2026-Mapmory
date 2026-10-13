import assert from "node:assert/strict";
import { access, readFile, stat } from "node:fs/promises";
import test from "node:test";
import { buildHowPhotoMap } from "../scripts/generate-how-photo-map.mjs";

const url = (path) => new URL(path, import.meta.url);
const readJson = async (path) => JSON.parse(await readFile(url(path), "utf8"));
const KB = 1024;

// The #how photo story's generated files (scripts/generate-how-photo-map.mjs, scripts/build-how-photo-story-assets.py)
// and their budgets: the map loads lazily near the section, the placeholders ship in the page's JS.
const FILES = {
  heap: { path: "../public/assets/how/heap.webp", maxBytes: 220 * KB, size: [1350, 810] },
  gallery: { path: "../public/assets/how/gallery.webp", maxBytes: 80 * KB, size: [360, 720] },
  map: { path: "../src/data/how-photo-map.json", maxBytes: 50 * KB },
  lqip: { path: "../src/data/how-photo-story-lqip.json", maxBytes: 4 * KB },
  sources: { path: "../src/data/how-photo-story-sources.json" },
};
const STORY_PHOTOS = [1, 2, 3, 4].map((n) => `gyeongbuk-0${n}`);

// Canvas size of a WebP file (simple lossy, lossless or extended format).
function webpInfo(buffer) {
  assert.equal(buffer.toString("ascii", 0, 4), "RIFF");
  assert.equal(buffer.toString("ascii", 8, 12), "WEBP");
  assert.equal(buffer.readUInt32LE(4) + 8, buffer.length, "RIFF size matches the file");
  const chunk = buffer.toString("ascii", 12, 16);
  if (chunk === "VP8X") return { width: 1 + buffer.readUIntLE(24, 3), height: 1 + buffer.readUIntLE(27, 3) };
  if (chunk === "VP8 ") return { width: buffer.readUInt16LE(26) & 0x3fff, height: buffer.readUInt16LE(28) & 0x3fff };
  if (chunk === "VP8L") {
    const bits = buffer.readUInt32LE(21);
    return { width: (bits & 0x3fff) + 1, height: ((bits >>> 14) & 0x3fff) + 1 };
  }
  throw new Error(`unknown WebP chunk ${chunk}`);
}

test("the generated #how files exist and stay inside their size budgets", async () => {
  for (const [name, { path, maxBytes }] of Object.entries(FILES)) {
    const { size } = await stat(url(path));
    assert.ok(size > 0, `${name} is empty`);
    if (maxBytes) assert.ok(size <= maxBytes, `${name} is ${size} bytes, over ${maxBytes}`);
  }
});

test("heap and gallery are WebP textures at the sizes the stage draws them", async () => {
  for (const name of ["heap", "gallery"]) {
    const { width, height } = webpInfo(await readFile(url(FILES[name].path)));
    assert.deepEqual([width, height], FILES[name].size, name);
  }
});

test("the map has exactly the fields the stage draws, as SVG paths in its 600-wide box", async () => {
  const map = await readJson(FILES.map.path);
  assert.deepEqual(Object.keys(map), ["w", "h", "landBig", "landSmall", "gbBig", "gbSmall", "gj", "outIdx", "tip", "gjc"]);
  assert.equal(map.w, 600);
  assert.ok(map.h > 500 && map.h < 600, `h ${map.h}`);
  const rings = (path) => path.match(/M[^M]*/g) ?? [];
  for (const key of ["landBig", "landSmall", "gbBig", "gbSmall", "gj"]) {
    assert.match(map[key], /^[MLaZ0-9. -]+$/, key);
    assert.ok(rings(map[key]).length > 0, key);
    for (const ring of rings(map[key])) assert.ok(ring.endsWith("Z"), `${key} ring is closed`);
  }
  // Every polygon point (the 독도 circles aside) lies inside the box.
  for (const key of ["landBig", "gbBig", "gj"]) {
    const values = map[key].match(/-?[\d.]+/g).map(Number);
    for (let i = 0; i < values.length; i += 2) {
      assert.ok(values[i] >= 0 && values[i] <= map.w && values[i + 1] >= 0 && values[i + 1] <= map.h, `${key} point ${values[i]},${values[i + 1]}`);
    }
  }
  // 경상북도 is drawn from the same rings as the land under it.
  const land = new Set([...rings(map.landBig), ...rings(map.landSmall)]);
  for (const ring of [...rings(map.gbBig), ...rings(map.gbSmall)]) assert.ok(land.has(ring), "a 경상북도 ring that is not land");
  // The coast outline is a list of landBig ring indices.
  const big = rings(map.landBig);
  assert.equal(new Set(map.outIdx).size, map.outIdx.length);
  for (const index of map.outIdx) assert.ok(Number.isInteger(index) && index >= 0 && index < big.length, `outIdx ${index}`);
  // The tag points into 경주시 from just inside its northern edge, above its centre.
  const gj = map.gj.match(/-?[\d.]+/g).map(Number);
  const xs = gj.filter((_, i) => i % 2 === 0);
  const ys = gj.filter((_, i) => i % 2 === 1);
  for (const [x, y] of [map.tip, map.gjc]) {
    assert.ok(x >= Math.min(...xs) && x <= Math.max(...xs) && y >= Math.min(...ys) && y <= Math.max(...ys), `${x},${y} is inside 경주시's box`);
  }
  assert.equal(map.tip[0], map.gjc[0]);
  assert.ok(map.tip[1] < map.gjc[1]);
});

test("the map file is what its generator makes from the client's map data", async () => {
  assert.deepEqual(buildHowPhotoMap(), await readJson(FILES.map.path));
});

test("the placeholders are tiny WebP data URIs for the 경주 photos, the heap, the gallery and 05's map", async () => {
  const lqip = await readJson(FILES.lqip.path);
  assert.deepEqual(Object.keys(lqip), [...STORY_PHOTOS, "heap", "gallery", "map", "map-dark"]);
  for (const [name, uri] of Object.entries(lqip)) {
    const match = uri.match(/^data:image\/webp;base64,([A-Za-z0-9+/]+=*)$/);
    assert.ok(match, `${name} is a WebP data URI`);
    const { width, height } = webpInfo(Buffer.from(match[1], "base64"));
    assert.ok(width <= 48 && height <= 48, `${name} is ${width}x${height}`);
  }
});

test("the textures' source photos are credited library photos, never the story's own 경주 photos", async () => {
  const credits = new Map((await readJson("../src/data/photo-credits.json")).map((credit) => [credit.file, credit]));
  const sources = await readJson(FILES.sources.path);
  for (const name of ["heap", "gallery"]) {
    const files = sources[name];
    assert.ok(Array.isArray(files) && files.length >= 20, `${name} lists its sources`);
    assert.deepEqual(files, [...new Set(files)].sort(), `${name} sources are sorted and unique`);
    for (const file of files) {
      assert.match(file, /^\/assets\/photos\/[a-z]+-\d{2}\.webp$/);
      assert.doesNotMatch(file, /gyeongbuk-|usa-05/, `${file} must not be baked into the ${name}`);
      await access(url(`../public${file}`));
      assert.ok(credits.has(file), `${file} (in the ${name}) has no credit`);
    }
  }
});

test("the story's 경주 photos exist and are credited", async () => {
  const credits = new Map((await readJson("../src/data/photo-credits.json")).map((credit) => [credit.file, credit]));
  for (const name of STORY_PHOTOS) {
    const file = `/assets/photos/${name}.webp`;
    await access(url(`../public${file}`));
    const credit = credits.get(file);
    assert.ok(credit, `missing credit for ${file}`);
    assert.match(credit.source, /^https:\/\//);
  }
});
