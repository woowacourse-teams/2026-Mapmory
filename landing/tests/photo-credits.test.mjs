import assert from "node:assert/strict";
import { access, readdir, readFile, stat } from "node:fs/promises";
import test from "node:test";
import { FOOD_URL, SPRITE, SPRITE_URL } from "../src/photoStory.js";

const readJson = async (path) => JSON.parse(await readFile(new URL(path, import.meta.url), "utf8"));
const credits = await readJson("../src/data/photo-credits.json");
// 2026-10-18: the landing's photos are one sprite (sketch v11, one openly licensed photo per cell) and the two food
// photos. Each cell has its own credit, keyed "<file>#<cell>"; team photos stay in the globe with their own credit.
const fileOf = (key) => key.split("#")[0];

test("every sprite cell and food photo has exactly one open license credit, and nothing else is credited", () => {
  assert.equal(new Set(credits.map(({ file }) => file)).size, credits.length);
  const expected = [
    ...SPRITE.tiles.map((_, k) => `${SPRITE_URL}#${k}`),
    `${FOOD_URL}#0`, `${FOOD_URL}#1`,
  ];
  assert.deepEqual(credits.map(({ file }) => file), expected);
  for (const credit of credits) {
    assert.ok(["cc0", "pdm", "pd", "by"].includes(credit.license), `${credit.file}: ${credit.license}`);
    assert.match(credit.source, /^https:\/\//);
    if (credit.license === "by") {
      assert.ok(credit.creator, `${credit.file} needs a creator`);
      assert.match(credit.licenseUrl, /^https:\/\/creativecommons\.org\/licenses\/by\/\d\.\d\/$/);
    }
    for (const field of ["creator", "title"]) assert.doesNotMatch(credit[field] ?? "", /%[0-9a-f]{2}|%u[0-9a-f]{4}/i, `${credit.file} ${field}`);
  }
  // the sprite's place labels match the credits cell by cell (the lens shows them as where the photo was taken)
  SPRITE.tiles.forEach(({ label }, k) => assert.equal(credits[k].place, label, `cell ${k}`));
});

test("the credited files ship as small webp, and the old per-photo files are gone", async () => {
  for (const file of new Set(credits.map(({ file }) => fileOf(file)))) {
    assert.match(file, /\.webp$/);
    await access(new URL(`../public${file}`, import.meta.url));
  }
  const story = (await readdir(new URL("../public/assets/story/", import.meta.url))).sort();
  assert.deepEqual(story, ["food.webp", "tiles.webp"]);
  assert.ok((await stat(new URL(`../public${SPRITE_URL}`, import.meta.url))).size < 300 * 1024);
  await assert.rejects(access(new URL("../public/assets/photos/", import.meta.url)));
  assert.equal(SPRITE.colors.length, SPRITE.tiles.length);
});

test("the footer credits load only when 사진 출처 is opened", async () => {
  const source = await readFile(new URL("../src/PhotoCredits.jsx", import.meta.url), "utf8");
  assert.match(source, /<summary>사진 출처<\/summary>/);
  assert.match(source, /import\("\.\/data\/photo-credits\.json"\)/);
  const app = await readFile(new URL("../src/App.jsx", import.meta.url), "utf8");
  assert.match(app, /<footer>[\s\S]*<PhotoCredits \/>/);
});
