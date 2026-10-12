import assert from "node:assert/strict";
import { access, readFile } from "node:fs/promises";
import test from "node:test";
import { PHOTO_FINDER_LIBRARY_PHOTOS, PHOTO_FINDER_PLACES } from "../src/photoFinderDemo.js";
import { HOW_STORY_MATCHES } from "../src/howStory.js";

const credits = JSON.parse(await readFile(new URL("../src/data/photo-credits.json", import.meta.url), "utf8"));
const mosaic = JSON.parse(await readFile(new URL("../src/data/how-mosaic.json", import.meta.url), "utf8"));

// The hero library, each hero place and the #how story's found photos each use their own files.
const sets = [
  PHOTO_FINDER_LIBRARY_PHOTOS,
  ...PHOTO_FINDER_PLACES.map(({ photos }) => photos),
  HOW_STORY_MATCHES.map(({ src }) => src),
];
const distinct = sets.flat();
// The #how library mosaic is one sprite of small crops of the other open-licence photos (never a 경주 photo);
// those photos are shown there too, so they keep their credits.
const mosaicFiles = [...new Set(mosaic.sources)].map((file) => `/assets/photos/${file}`);
const used = [...distinct, ...mosaicFiles];

test("no photo file is shared between the hero library, the hero places and the #how story's found photos", () => {
  assert.equal(new Set(distinct).size, distinct.length);
  for (const file of mosaicFiles) assert.doesNotMatch(file, /gyeongbuk-/);
});

test("every demo photo exists and has an open license credit", async () => {
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

test("every credit belongs to a photo the page shows, so removed photos leave no orphan credits", () => {
  const usedFiles = new Set(used);
  assert.equal(new Set(credits.map(({ file }) => file)).size, credits.length);
  for (const { file } of credits) assert.ok(usedFiles.has(file), `orphaned credit ${file}`);
});
