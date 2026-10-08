import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import {
  PHOTO_FINDER_LIBRARY_COUNT,
  PHOTO_FINDER_PLACES,
  PHOTO_FINDER_TIMELINE,
  getPhotoFinderDuration,
  getPhotoFinderState,
} from "../src/photoFinderDemo.js";

const heroSource = await readFile(new URL("../src/PhotoFinderHero.jsx", import.meta.url), "utf8");
const heroStyles = await readFile(new URL("../src/photo-finder-hero.css", import.meta.url), "utf8");
const appSource = await readFile(new URL("../src/App.jsx", import.meta.url), "utf8");

test("the demo moves scan -> type -> match -> fly -> done once", () => {
  const query = "일본";
  const { scanEndMs, typeStepMs, matchHoldMs } = PHOTO_FINDER_TIMELINE;
  assert.equal(getPhotoFinderState(0, query).phase, "scan");
  assert.equal(getPhotoFinderState(0, query).typedQuery, "");
  assert.equal(getPhotoFinderState(scanEndMs, query).typedQuery, "일");
  assert.equal(getPhotoFinderState(scanEndMs + typeStepMs, query).typedQuery, "일본");
  assert.equal(getPhotoFinderState(scanEndMs + query.length * typeStepMs, query).phase, "match");
  assert.equal(getPhotoFinderState(scanEndMs + query.length * typeStepMs + matchHoldMs, query).phase, "fly");
  const done = getPhotoFinderState(getPhotoFinderDuration(query), query);
  assert.equal(done.phase, "done");
  assert.equal(done.isMapFilled, true);
  assert.equal(done.scannedCount, PHOTO_FINDER_LIBRARY_COUNT);
  assert.equal(getPhotoFinderState(Number.POSITIVE_INFINITY, query).phase, "done");
});

test("demo places only use units the shipping app can search: Korean provinces or whole countries", () => {
  assert.deepEqual(PHOTO_FINDER_PLACES.map(({ label }) => label), ["일본", "제주", "미국"]);
  // Autoplay and the default chip use the first place; it matches the promo copy.
  assert.equal(PHOTO_FINDER_PLACES[0].key, "japan");
  assert.equal(PHOTO_FINDER_PLACES[0].foundCount, 86);
  for (const place of PHOTO_FINDER_PLACES) {
    if (place.scope === "korea") assert.match(place.regionCode, /^KR-\d{2}$/);
    else assert.match(place.regionCode, /^\d{3}$/);
    assert.ok(place.photos.length > 0);
    assert.equal(new Set(place.photos).size, place.photos.length);
    for (const photo of place.photos) assert.match(photo, /^\/assets\/photos\//);
  }
  assert.doesNotMatch(heroSource + appSource, /오사카|도쿄를 검색|관광지/);
});

test("the hero states the trust promise and keeps store conversions", () => {
  assert.match(heroSource, /사진은 폰 안에서 찾고, 고른 사진만 올라가요\./);
  assert.match(heroSource, /검색 한 번이면 그 여행만 지도로/);
  assert.match(heroSource, /HERO_DEMO_SELECT/);
  assert.match(appSource, /<StoreButton placement="hero" platform="ios" label="App Store" \/>/);
  assert.match(appSource, /<StoreButton placement="hero" platform="android" label="Google Play" \/>/);
  assert.doesNotMatch(appSource, /<LaunchWaitlistForm/);
});

test("the demo does not loop and reduced motion shows the finished state", () => {
  assert.doesNotMatch(heroStyles, /finder-(scan|fly)[^;]*infinite/);
  assert.match(heroSource, /getPhotoFinderState\(Number\.POSITIVE_INFINITY/);
  assert.match(heroStyles, /prefers-reduced-motion: reduce[\s\S]*\.finder-flight \{ display: none; \}/);
  assert.match(heroStyles, /\.finder-chips button \{[^}]*min-height: 44px;/s);
});

test("trust copy and FAQ only claim what the client code does", () => {
  assert.match(appSource, /고른 사진만 올라가요/);
  assert.match(appSource, /사진첩 사진을 전부 가져가나요\?/);
  assert.match(appSource, /위치 정보가 없는 사진은요\?/);
  assert.match(appSource, /무료인가요\?/);
  // Deleting a record does not remove uploaded objects from storage yet, so never promise that.
  assert.doesNotMatch(appSource, /서버에서도 (사진이 )?(완전히 )?삭제/);
});
