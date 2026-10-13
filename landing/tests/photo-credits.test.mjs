import assert from "node:assert/strict";
import { access, readdir, readFile } from "node:fs/promises";
import test from "node:test";
import { PHOTO_FINDER_LIBRARY_PHOTOS, PHOTO_FINDER_PLACES } from "../src/photoFinderDemo.js";

const credits = JSON.parse(await readFile(new URL("../src/data/photo-credits.json", import.meta.url), "utf8"));
const hero = [...new Set([PHOTO_FINDER_LIBRARY_PHOTOS, ...PHOTO_FINDER_PLACES.map(({ photos }) => photos)].flat())];

test("every hero photo exists and has an open license credit", async () => {
  const byFile = new Map(credits.map((credit) => [credit.file, credit]));
  for (const src of hero) {
    await access(new URL(`../public${src}`, import.meta.url));
    const credit = byFile.get(src);
    assert.ok(credit, `missing credit for ${src}`);
    assert.ok(["cc0", "pdm", "by"].includes(credit.license), `${src}: ${credit.license}`);
    assert.match(credit.source, /^https:\/\//);
    if (credit.license === "by") assert.ok(credit.creator, `${src} needs a creator`);
  }
});

test("every credit is listed once and its photo exists, so removed photos leave no orphan credits", async () => {
  assert.equal(new Set(credits.map(({ file }) => file)).size, credits.length);
  for (const { file } of credits) await access(new URL(`../public${file}`, import.meta.url));
});

test("every photo shipped in public/assets/photos is credited", async () => {
  const credited = new Set(credits.map(({ file }) => file));
  const files = (await readdir(new URL("../public/assets/photos/", import.meta.url))).filter((name) => name.endsWith(".webp"));
  assert.ok(files.length > 0);
  for (const name of files) assert.ok(credited.has(`/assets/photos/${name}`), `uncredited photo ${name}`);
  // The artwork and sculpture photos the old 3-tap demo used are gone with their credits.
  for (const name of ["busan-04.webp", "gangwon-02.webp", "gangwon-04.webp"]) assert.ok(!files.includes(name), name);
});
