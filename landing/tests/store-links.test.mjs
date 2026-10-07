import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const appSource = await readFile(new URL("../src/App.jsx", import.meta.url), "utf8");
const html = await readFile(new URL("../index.html", import.meta.url), "utf8");

test("the photo-finder hero does not preload an image that is no longer above the fold", () => {
  assert.doesNotMatch(html, /rel="preload"[^>]+as="image"/);
});

test("the landing page exposes the supplied App Store URL alongside Google Play", () => {
  assert.match(appSource, /https:\/\/apps\.apple\.com\/kr\/app\/mapmory-[^\"]+\/id6807056166/);
  assert.match(appSource, /platform="ios" label="App Store"/);
  assert.match(appSource, /platform="android" label="Google Play"/);
});
