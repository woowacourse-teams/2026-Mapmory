import assert from "node:assert/strict";
import { access, readFile } from "node:fs/promises";
import test from "node:test";
import { PHOTO_FINDER_LIBRARY_PHOTOS, PHOTO_FINDER_PLACES } from "../src/photoFinderDemo.js";
import { HOW_STORY_TILES } from "../src/howStory.js";

const credits = JSON.parse(await readFile(new URL("../src/data/photo-credits.json", import.meta.url), "utf8"));

// The hero library, each hero place and the #how story each use their own files.
const sets = [
  PHOTO_FINDER_LIBRARY_PHOTOS,
  ...PHOTO_FINDER_PLACES.map(({ photos }) => photos),
  HOW_STORY_TILES.map(({ src }) => src),
];
const used = sets.flat();

test("no photo file is shared between the hero library, the hero places and the #how story", () => {
  assert.equal(new Set(used).size, used.length);
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
