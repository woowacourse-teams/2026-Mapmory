import assert from "node:assert/strict";
import { access, readdir, readFile } from "node:fs/promises";
import test from "node:test";
import { PHOTO_FINDER_LIBRARY_PHOTOS, PHOTO_FINDER_PLACES } from "../src/photoFinderDemo.js";

const readJson = async (path) => JSON.parse(await readFile(new URL(path, import.meta.url), "utf8"));
const credits = await readJson("../src/data/photo-credits.json");
const lqip = await readJson("../src/data/how-photo-story-lqip.json");
const textureSources = await readJson("../src/data/how-photo-story-sources.json");

// The #how photo story shows its 경주 photos as real prints (each has a placeholder in the LQIP data, beside the
// placeholders of the textures and of 05's map, which are not photos).
const HOW_STORY_PHOTOS = Object.keys(lqip)
  .filter((name) => !["heap", "gallery", "map", "map-dark"].includes(name))
  .map((name) => `/assets/photos/${name}.webp`);
// The heap and gallery textures are composites of tiny prints cropped from the other library photos: those photos
// are shown there too (never readably), so they keep their credits.
const TEXTURE_PHOTOS = [...new Set([...textureSources.heap, ...textureSources.gallery])];

// The hero library, each hero place and the #how story's 경주 photos each use their own files.
const distinct = [
  PHOTO_FINDER_LIBRARY_PHOTOS,
  ...PHOTO_FINDER_PLACES.map(({ photos }) => photos),
  HOW_STORY_PHOTOS,
].flat();
const used = [...new Set([...distinct, ...TEXTURE_PHOTOS])];

test("no photo file is shared between the hero library, the hero places and the #how story's 경주 photos", () => {
  assert.deepEqual(HOW_STORY_PHOTOS, [1, 2, 3, 4].map((n) => `/assets/photos/gyeongbuk-0${n}.webp`));
  assert.equal(new Set(distinct).size, distinct.length);
  // The textures stand for "thousands of other photos": the story's own 경주 photos are never baked into them.
  for (const file of TEXTURE_PHOTOS) assert.ok(!HOW_STORY_PHOTOS.includes(file), `${file} is baked into a texture`);
});

test("every photo the page shows exists and has an open license credit", async () => {
  const byFile = new Map(credits.map((credit) => [credit.file, credit]));
  for (const src of used) {
    await access(new URL(`../public${src}`, import.meta.url));
    const credit = byFile.get(src);
    assert.ok(credit, `missing credit for ${src}`);
    assert.ok(["cc0", "pdm", "by"].includes(credit.license), `${src}: ${credit.license}`);
    assert.match(credit.source, /^https:\/\//);
    if (credit.license === "by") assert.ok(credit.creator, `${src} needs a creator`);
    // Openverse sometimes returns names still URL-escaped ("Krzysztof%20Puszczy%u0144ski").
    for (const field of ["creator", "title"]) assert.doesNotMatch(credit[field] ?? "", /%[0-9a-f]{2}|%u[0-9a-f]{4}/i, `${src} ${field}`);
  }
});

test("every credit belongs to a photo the page shows, so removed photos leave no orphan credits", async () => {
  const usedFiles = new Set(used);
  assert.equal(new Set(credits.map(({ file }) => file)).size, credits.length);
  for (const { file } of credits) {
    assert.ok(usedFiles.has(file), `orphaned credit ${file}`);
    await access(new URL(`../public${file}`, import.meta.url));
  }
});

test("every photo shipped in public/assets/photos is credited", async () => {
  const credited = new Set(credits.map(({ file }) => file));
  const files = (await readdir(new URL("../public/assets/photos/", import.meta.url))).filter((name) => name.endsWith(".webp"));
  assert.ok(files.length > 0);
  for (const name of files) assert.ok(credited.has(`/assets/photos/${name}`), `uncredited photo ${name}`);
  // The artwork and sculpture photos the old 3-tap demo used are gone with their credits.
  for (const name of ["busan-04.webp", "gangwon-02.webp", "gangwon-04.webp"]) assert.ok(!files.includes(name), name);
});
