import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { TECH_COPY, TECH_SCENES, TECH_SHARE, TECH_STEPS, techScenes } from "../src/techStory.js";
import { TECH_STORY_INTERACTIONS } from "../src/analytics.js";

// 2026-10-27: the #tech section (Sprint 3 기술 소개), 티온's approved sketch v8.
const read = (file) => readFile(new URL(file, import.meta.url), "utf8");
const app = await read("../src/App.jsx");
const component = await read("../src/TechStory.jsx");
const engine = await read("../src/techStoryEngine.js");
const css = await read("../src/tech-story.css");
const plan = await read("../ANALYTICS_MEASUREMENT_PLAN.md");
const stripComments = (source) => source.replace(/\/\*[\s\S]*?\*\//g, "").replace(/(^|[^:"'`])\/\/.*$/gm, "$1");

test("two steps, six scenes, one title each, in the approved words", () => {
  assert.deepEqual(TECH_STEPS.map(({ label, side }) => [label, side]), [["사진 찾기", "left"], ["지도 모으기", "right"]]);
  assert.deepEqual(TECH_SCENES.map(({ step, title }) => [step, title]), [
    ["find", "사진마다 찍은 곳이 담겨 있어요"],
    ["find", "3만 장도 30초면 그곳 사진만 모여요"],
    ["find", "고른 사진으로 바로 기록해요"],
    ["map", "다녀온 곳이 지도에 칠해져요"],
    ["map", "모은 기록을 3D 지구본에서 돌려 봐요"],
    ["map", "다녀온 곳을 타임라인 영상으로 공유해요"],
  ]);
  assert.deepEqual(TECH_COPY.title, ["사진은 빨리 찾아 기록하고,", "기록은 3D 지도에 모아요"]);
});

test("`3만 장도 30초` keeps its footnote, and no other small text sits under the titles", () => {
  assert.equal(TECH_SCENES.filter((scene) => scene.footnote).map((scene) => scene.title).join(), "3만 장도 30초면 그곳 사진만 모여요");
  assert.match(TECH_COPY.footnote, /팀 테스트 폰/);
  assert.match(component, /<p className="ts-footnote">\{TECH_COPY\.footnote\}<\/p>/);
  // a beat is the step label and the title only
  assert.doesNotMatch(component.slice(component.indexOf('className="ts-copy"')), /<p className="ts-(sub|note|body)/);
});

test("the share scene is configured in one place and can be swapped or turned off", () => {
  assert.equal(TECH_SHARE.enabled, true);
  assert.ok(TECH_SHARE.stops.length >= 2, "the app needs two countries for a video");
  for (const stop of TECH_SHARE.stops) {
    assert.ok(Math.abs(stop.lon) <= 180 && Math.abs(stop.lat) <= 90);
    assert.match(stop.date, /^\d{4}\. \d{2}\. \d{2}$/);
  }
  assert.equal(TECH_SHARE.media, null);
  assert.equal(techScenes({ ...TECH_SHARE, enabled: false }).length, 5);
  assert.match(component, /media\.type === "video"/);
  assert.match(component, /TECH_SHARE\.enabled && <ShareScreen/);
});

test("the section takes #how's place between the hero and the 3D globe, and loads its engine lazily", () => {
  assert.ok(app.indexOf("<PhotoWallHero") < app.indexOf("<TechStory />") && app.indexOf("<TechStory />") < app.indexOf('id="experience"'));
  assert.doesNotMatch(app, /<PhotoStory \/>/);
  assert.match(component, /import\("\.\/techStoryEngine\.js"\)/);
  assert.doesNotMatch(component, /from "\.\/techStoryEngine\.js"/);
});

test("the engine follows the scroll and never steers it", () => {
  const code = stripComments(engine);
  for (const banned of [/"wheel"/, /"touchmove"/, /"touchstart"/, /scrollTo\(/, /scrollBy\(/, /scrollIntoView/, /window, "keydown"/]) assert.doesNotMatch(code, banned);
  assert.match(code, /on\(window, "scroll", schedule, \{ passive: true \}\)/);
  assert.doesNotMatch(css, /scroll-snap/);
  assert.match(css, /\.ts-globe \{[^}]*touch-action: pan-y/);
  assert.match(css, /prefers-reduced-motion: reduce/);
  assert.match(css, /:root\[data-theme="dark"\] \.ts \{/);
});

test("every scene's touchable element is one measured interaction, and the plan describes the loop", () => {
  assert.deepEqual(TECH_SCENES.map((scene) => scene.interaction), ["flip", "gather", "save", "map_toggle", "globe_drag", "year_pick"]);
  assert.deepEqual([...TECH_STORY_INTERACTIONS], TECH_SCENES.map((scene) => scene.interaction));
  for (const name of ["tech_story_view", "tech_story_scene", "tech_story_interact"]) assert.match(plan, new RegExp("`" + name + "`"));
  assert.match(plan, /### 기술 소개 피드백 루프/);
});
