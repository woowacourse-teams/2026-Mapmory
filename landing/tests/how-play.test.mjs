import assert from "node:assert/strict";
import { access, readFile } from "node:fs/promises";
import test from "node:test";
import { PHOTO_FINDER_LIBRARY_PHOTOS, PHOTO_FINDER_PLACES } from "../src/photoFinderDemo.js";
import { HOW_PLAY_PLACES, howPlayReducer, howPlaySaveParameters, initialHowPlayState } from "../src/howPlay.js";
import { withObjectParticle, withSubjectParticle } from "../src/koreanParticle.js";

const credits = JSON.parse(await readFile(new URL("../src/data/photo-credits.json", import.meta.url), "utf8"));
const provinces = JSON.parse(await readFile(new URL("../src/data/korea-provinces.json", import.meta.url), "utf8"));
const componentSource = await readFile(new URL("../src/HowItWorksPlay.jsx", import.meta.url), "utf8");
const reducerSource = await readFile(new URL("../src/howPlay.js", import.meta.url), "utf8");

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

test("each place sends one save per page, numbered in fill order, never again after a reset", () => {
  const actions = [
    { type: "pick-place", placeKey: "busan" }, { type: "toggle-photo", index: 0 }, { type: "save" },
    { type: "pick-place", placeKey: "gangwon" }, { type: "pick-all" }, { type: "save" },
    { type: "pick-place", placeKey: "gyeongbuk" }, { type: "toggle-photo", index: 1 }, { type: "save" },
    { type: "reset" },
    { type: "pick-place", placeKey: "busan" }, { type: "toggle-photo", index: 0 }, { type: "save" },
  ];
  const saved = new Set();
  const payloads = [];
  let state = initialHowPlayState;
  for (const action of actions) {
    state = howPlayReducer(state, action);
    const params = howPlaySaveParameters(state, saved);
    if (state.step !== 2) assert.equal(params, null);
    if (!params) continue;
    saved.add(state.placeKey);
    payloads.push(params);
    // A StrictMode or HMR re-run of the same committed state must not send it twice.
    assert.equal(howPlaySaveParameters(state, saved), null);
  }
  // The replayed 부산 save is on screen but sends nothing.
  assert.deepEqual([state.step, state.placeKey], [2, "busan"]);
  assert.equal(howPlaySaveParameters(state, saved), null);
  assert.deepEqual(payloads, [
    { experience_type: "how_play", demo_place: "busan", save_index: 1, selected_photos: 1 },
    { experience_type: "how_play", demo_place: "gangwon", save_index: 2, selected_photos: 4 },
    { experience_type: "how_play", demo_place: "gyeongbuk", save_index: 3, selected_photos: 1 },
  ]);
});

test("the first possible tap is a place pick, and demo places never mix with the hero's", () => {
  for (const action of [{ type: "toggle-photo", index: 0 }, { type: "pick-all" }, { type: "save" }]) {
    assert.equal(howPlayReducer(initialHowPlayState, action), initialHowPlayState);
  }
  const keys = HOW_PLAY_PLACES.map(({ key }) => key);
  assert.deepEqual(keys, ["busan", "gangwon", "gyeongbuk"]);
  for (const { key } of PHOTO_FINDER_PLACES) assert.ok(!keys.includes(key), key);
});

test("the demo is observed on the phone box and tracking stays out of the reducer", () => {
  assert.match(componentSource, /className="how-play-phone" ref=\{sectionRef\}/);
  assert.match(componentSource, /startExperience\("place_select", \{ fromSection: true \}\)/);
  assert.doesNotMatch(componentSource, /className="how-play" ref=/);
  assert.doesNotMatch(componentSource, /how-play-screen"[^>]*ref=\{sectionRef\}/);
  assert.doesNotMatch(reducerSource, /from "\.\/analytics|trackEvent\(/);
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
