import assert from "node:assert/strict";
import { access, readdir, readFile } from "node:fs/promises";
import test from "node:test";
import { PHOTO_FINDER_LIBRARY_PHOTOS, PHOTO_FINDER_PLACES } from "../src/photoFinderDemo.js";
import { REPLAY_PHOTOS_PER_PLACE, REPLAY_PLACES, replayPhoto } from "../src/travelReplay.js";

const readJson = async (path) => JSON.parse(await readFile(new URL(path, import.meta.url), "utf8"));
const credits = await readJson("../src/data/photo-credits.json");
// The hero library and each hero place use their own files. The #how travel replay (2026-10-15) replays the hero's
// own example trip, so it reuses the hero place photos on purpose and adds none of its own.
const distinct = [
  PHOTO_FINDER_LIBRARY_PHOTOS,
  ...PHOTO_FINDER_PLACES.map(({ photos }) => photos),
].flat();
const used = [...new Set(distinct)];

test("no photo file is shared between the hero library and the hero places, and the replay adds no photos", () => {
  assert.equal(new Set(distinct).size, distinct.length);
  const placePhotos = new Set(PHOTO_FINDER_PLACES.flatMap(({ photos }) => photos));
  for (const pl of REPLAY_PLACES) {
    assert.ok(placePhotos.has(replayPhoto(pl.cover)), pl.cover);
    for (let i = 1; i <= REPLAY_PHOTOS_PER_PLACE; i++) assert.ok(placePhotos.has(replayPhoto(`${pl.key}-0${i}`)), `${pl.key}-0${i}`);
  }
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
