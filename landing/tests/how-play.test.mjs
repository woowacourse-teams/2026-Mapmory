import assert from "node:assert/strict";
import { access, readFile } from "node:fs/promises";
import test from "node:test";
import { PHOTO_FINDER_LIBRARY_PHOTOS, PHOTO_FINDER_PLACES } from "../src/photoFinderDemo.js";
import { HOW_PLAY_PLACES, howPlayReducer, initialHowPlayState } from "../src/howPlay.js";
import { withObjectParticle, withSubjectParticle } from "../src/koreanParticle.js";

const credits = JSON.parse(await readFile(new URL("../src/data/photo-credits.json", import.meta.url), "utf8"));
const provinces = JSON.parse(await readFile(new URL("../src/data/korea-provinces.json", import.meta.url), "utf8"));

test("three taps: pick a place, keep photos, save fills the province and keeps it", () => {
  let state = howPlayReducer(initialHowPlayState, { type: "pick-place", placeKey: "busan" });
  assert.equal(state.step, 1);
  // Matches the app: found photos start unselected, the user taps the ones to keep.
  assert.deepEqual(state.picked, []);
  state = howPlayReducer(state, { type: "toggle-photo", index: 2 });
  state = howPlayReducer(state, { type: "toggle-photo", index: 0 });
  assert.deepEqual(state.picked, [0, 2]);
  state = howPlayReducer(state, { type: "save" });
  assert.equal(state.step, 2);
  assert.deepEqual(state.filled, ["busan"]);
  state = howPlayReducer(state, { type: "pick-place", placeKey: "gangwon" });
  assert.equal(state.step, 1);
  assert.deepEqual(state.filled, ["busan"]);
  assert.deepEqual(howPlayReducer(state, { type: "reset" }), initialHowPlayState);
});

test("saving needs at least one photo, and pick-all selects every found photo", () => {
  let state = howPlayReducer(initialHowPlayState, { type: "pick-place", placeKey: "gangwon" });
  assert.equal(howPlayReducer(state, { type: "save" }), state);
  state = howPlayReducer(state, { type: "pick-all" });
  assert.deepEqual(state.picked, [0, 1, 2, 3]);
  for (const index of [0, 1, 2, 3]) state = howPlayReducer(state, { type: "toggle-photo", index });
  assert.equal(howPlayReducer(state, { type: "save" }), state);
});

test("how-play places are real provinces and never reuse the hero's photos", () => {
  const codes = new Set(provinces.map(({ code }) => code));
  const heroPhotos = new Set([...PHOTO_FINDER_LIBRARY_PHOTOS, ...PHOTO_FINDER_PLACES.flatMap(({ photos }) => photos)]);
  for (const place of HOW_PLAY_PLACES) {
    assert.ok(codes.has(place.regionCode), place.regionCode);
    for (const { src, alt } of place.photos) {
      assert.ok(!heroPhotos.has(src), src);
      assert.ok(alt.length > 0);
    }
  }
  assert.equal(withSubjectParticle("부산"), "부산이");
  assert.equal(withSubjectParticle("경주"), "경주가");
  // The result line names the painted province, not the picked city.
  assert.equal(HOW_PLAY_PLACES.find(({ key }) => key === "gyeongbuk").province, "경북");
  assert.equal(withObjectParticle("제주"), "제주를");
  assert.equal(withObjectParticle("일본"), "일본을");
});

test("every demo photo exists and has an open license credit", async () => {
  const used = [
    ...PHOTO_FINDER_LIBRARY_PHOTOS,
    ...PHOTO_FINDER_PLACES.flatMap(({ photos }) => photos),
    ...HOW_PLAY_PLACES.flatMap(({ photos }) => photos.map(({ src }) => src)),
  ];
  assert.equal(new Set(used).size, used.length);
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
