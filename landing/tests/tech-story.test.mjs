import assert from "node:assert/strict";
import { readFile, stat } from "node:fs/promises";
import test from "node:test";
import { TECH_COPY, TECH_MONTHS, TECH_PLACES, TECH_SCENES, TECH_SHARE, TECH_STEPS, TECH_TRIP, TECH_VISITED, techScenes } from "../src/techStory.js";
import SPRITE from "../src/data/storySprite.js";
import { TECH_STORY_INTERACTIONS } from "../src/analytics.js";

// 2026-10-27: the #tech section (Sprint 3 기술 소개), 티온's approved sketch v8.
const read = (file) => readFile(new URL(file, import.meta.url), "utf8");
const app = await read("../src/App.jsx");
const component = await read("../src/TechStory.jsx");
const engine = await read("../src/techStoryEngine.js");
const css = await read("../src/tech-story.css");
const plan = await read("../ANALYTICS_MEASUREMENT_PLAN.md");
const plain = (title) => title.replace(/\u00a0/g, " ");
const stripComments = (source) => source.replace(/\/\*[\s\S]*?\*\//g, "").replace(/(^|[^:"'`])\/\/.*$/gm, "$1");

test("two steps, six scenes, one title each, in the approved words", () => {
  assert.deepEqual(TECH_STEPS.map(({ label, side }) => [label, side]), [["사진 찾기", "left"], ["지도 모으기", "right"]]);
  // 10-27 mobile pass: "그곳" became "고른 곳" so a first-time visitor knows which place
  assert.deepEqual(TECH_SCENES.map(({ step, title }) => [step, plain(title)]), [
    ["find", "사진마다 찍은 곳이 담겨 있어요"],
    ["find", "3만 장도 30초면 누른 곳 사진만 모여요"],
    ["find", "고른 사진만 앱에 올라가고 나머지는 폰에 남아요"],
    ["map", "다녀온 곳이 지도에 칠해져요"],
    ["map", "모은 기록을 3D 지구본에서 돌려 봐요"],
    ["map", "다녀온 곳을 타임라인 영상으로 공유해요"],
  ]);
  // a no-break space only ever joins two words, to keep a phrase on one line on a 360px phone
  for (const { title } of TECH_SCENES) assert.doesNotMatch(title, /(^|\s)\u00a0|\u00a0(\s|$)/);
  assert.deepEqual(TECH_COPY.title, ["사진은 빨리 찾아 기록하고,", "기록은 3D 지도에 모아요"]);
});

test("`3만 장도 30초` keeps its footnote, and no other small text sits under the titles", () => {
  assert.equal(TECH_SCENES.filter((scene) => scene.footnote).map((scene) => plain(scene.title)).join(), "3만 장도 30초면 누른 곳 사진만 모여요");
  assert.match(TECH_COPY.footnote, /팀 테스트 폰/);
  assert.match(component, /<p className="ts-footnote">\{TECH_COPY\.footnote\}<\/p>/);
  // a beat is the step label and the title only
  assert.doesNotMatch(component.slice(component.indexOf('className="ts-copy"')), /<p className="ts-(sub|note|body)/);
});

test("the share scene is configured in one place and can be swapped or turned off", async () => {
  assert.equal(TECH_SHARE.enabled, true);
  // 10-27: the real share video (티온's captures, classic style), as VP9 and H.264 with a poster, all in public/
  const { media } = TECH_SHARE;
  assert.equal(media.type, "video");
  assert.deepEqual(media.sources.map((source) => source.type), ["video/webm", "video/mp4"]);
  for (const file of [...media.sources.map((source) => source.src), media.poster]) {
    assert.match(file, /^\/assets\/tech\//);
    const { size } = await stat(new URL(`../public${file}`, import.meta.url));
    assert.ok(size < 600_000, `${file} stays small (${size} bytes)`);
  }
  // year buttons: in order, the first at the start of the video
  assert.equal(media.years[0].at, 0);
  media.years.forEach((year, i) => {
    assert.match(year.label, /^\d{4}$/);
    if (i) assert.ok(year.at > media.years[i - 1].at && +year.label === +media.years[i - 1].label + 1);
  });
  assert.match(component, /media\.sources\.map\(\(source\) => <source/);
  assert.match(component, /media\.years\.map\(/);
  assert.match(component, /media\.type === "video"/);
  assert.match(component, /TECH_SHARE\.enabled && <ShareScreen/);
  // 10-28: the video never plays by itself (티온: waiting for a replay feels slow); a year button or a tap plays it
  assert.doesNotMatch(stripComments(engine), /shareVideo\.play\(\)\.catch/);
  assert.match(engine, /const seek = \(at\) => loadVideo\(\)\.then/);
  assert.equal(techScenes({ ...TECH_SHARE, enabled: false }).length, 5);
  // the trip's room is both scenes' beats; without the share scene it ends back in the globe screen
  assert.match(engine, /const P = share\.enabled\s*\? \{ out: \[0\.06, 0\.2\], trip: \[0\.2, 0\.78\], back: \[0\.78, 0\.9\], land: 0\.9 \}/);
});

test("the trip visits every recorded place in date order, and the same places are on the photos and the globe (티온, 10-28)", () => {
  // one source: every place label in the photo sprite is a record with a position and a date
  const labels = new Set(SPRITE.tiles.map((tile) => tile[1]));
  assert.deepEqual(new Set(TECH_PLACES.map((p) => p.name)), labels);
  for (const p of TECH_PLACES) {
    assert.ok(Math.abs(p.lon) <= 180 && Math.abs(p.lat) <= 90);
    assert.match(p.date, /^\d{4}\. \d{2}\. \d{2}$/);
    assert.equal(TECH_MONTHS[p.name], p.date.slice(0, 8));
  }
  assert.equal(TECH_VISITED.length, TECH_PLACES.length);
  // the trip: oldest first, nothing left out, 경주 (the city the map scene fills) among them
  assert.equal(TECH_TRIP.length, TECH_PLACES.length);
  TECH_TRIP.forEach((stop, i) => { if (i) assert.ok(stop.date > TECH_TRIP[i - 1].date); });
  assert.ok(TECH_TRIP.some((stop) => stop.name === "경주"));
  assert.match(engine, /const stops = TECH_TRIP/);
  assert.match(engine, /points: TECH_VISITED/);
  // the globe scene has the long scroll room the trip plays through
  assert.equal(TECH_SCENES.filter((scene) => scene.trip).map((scene) => scene.interaction).join(), "globe_drag");
  assert.match(component, /className=\{`ts-beat\$\{scene\.trip \? " is-trip" : ""\}`\}/);
  assert.match(css, /\.ts-beat\.is-trip \{ min-height: 276vh; min-height: 276svh; \}/);
  assert.match(css, /@media \(max-width: 760px\)[\s\S]*\.ts-beat\.is-trip \{ min-height: 220svh; \}/);
});

test("every scene plays by the scroll position, not by timers or taps (티온, 10-28)", () => {
  const code = stripComments(engine);
  // the frame reads how far the focus line is through the scene's room and lays the scene out from it
  assert.match(code, /if \(act === 0\) layoutRoll\(q\);\s*else if \(act === 1\) scrubGather\(q\);\s*else if \(act === 2\) scrubPick\(q\);\s*else if \(act === 3\) scrubMap\(q\);/);
  assert.match(code, /const look = act >= 4 \? tripFrame\(focus\)/);
  // no self-running demos: no timers start a flip, a gather, a fold or a fill, and no rAF loop turns the globe
  for (const gone of [/demoFlip/, /foldDemo/, /startGlobe/, /autoplay/, /rot \+= 0\.3/, /mountTimeline/]) assert.doesNotMatch(code, gone);
  // a tap takes a scene over from the scroll until it is entered again
  assert.match(code, /const takeOver = \(i\) => \{ manual\.add\(i\); tried\(i\); \};/);
  assert.match(code, /if \(!manual\.has\(act\)\)/);
  // no hand-written hints or glowing nudges in the phone: the scroll shows what each screen does
  assert.doesNotMatch(component, /ts-hint/);
  assert.doesNotMatch(css, /ts-nudge|\.is-try/);
  for (const scene of TECH_SCENES) assert.equal(scene.hint, undefined);
});

test("phones: text in the phone is 13px or larger and what is tapped is about 44px or larger", () => {
  const phone = css.slice(css.indexOf("@media (max-width: 760px)"), css.indexOf("@media (prefers-reduced-motion"));
  for (const [, size] of phone.matchAll(/font-size: (\d+)px/g)) assert.ok(+size >= 13, `font-size ${size}px on phones`);
  // the back of a photo shows the place only, the map names its level in one chip, the counter repeats the title
  for (const rule of [/\.ts-flip-back small \{ display: none; \}/, /\.ts-tag \{ display: none; \}/, /\.ts-region-chip \{ display: flex; \}/, /\.ts-counter \{ display: none; \}/, /\.ts-save \{ min-height: 48px;/]) assert.match(phone, rule);
  assert.match(phone, /\.ts-roll \{ grid-template-columns: repeat\(3, 1fr\); \}/);
  assert.match(css, /\.ts-years button \{[^}]*min-width: 48px; min-height: 48px;/);
  // gather photos: 3-4 columns of about 46px or more on phones; the pill under the flying globe is 16px or larger
  assert.match(engine, /narrow\.matches \? Math\.max\(3, Math\.min\(4, Math\.floor\(\(w \+ gap\) \/ \(46 \+ gap\)\)\)\) : 6/);
  assert.match(css, /\.ts-where span \{[^}]*font-size: 16px;/);
});

test("a quick scroll never waits for words, and the steps are told apart by the angle of the phone (티온, 10-27)", () => {
  // the words live in the sticky stage and change in place; the beats are empty scroll room
  const stage = component.slice(component.indexOf('data-tech-stage=""'), component.indexOf('className="ts-beats"'));
  assert.match(stage, /<div className="ts-captions">/);
  assert.match(stage, /data-caption=\{i\}/);
  assert.match(stage, /<StepLabel text=\{step\.label\} \/>/);
  assert.match(component, /<div className="ts-beats" aria-hidden="true">/);
  assert.match(component, /<div className=\{`ts-beat\$\{scene\.trip \? " is-trip" : ""\}`\} data-beat=\{i\} key=\{scene\.title\} \/>/);
  assert.match(css, /\.ts-copy\.is-on, \.ts:not\(\.is-live\) \.ts-copy\[data-caption="0"\] \{ opacity: 1;/);
  assert.match(engine, /showCaption\(captions\[act\]\)/);
  // nothing extra to read: no step numbers, progress dots or second phone
  for (const gone of [/<Progress/, /data-phone-next/, /StepLabel n=/]) assert.doesNotMatch(component, gone);
  for (const gone of [/\.ts-dots/, /\.is-next/, /\.ts-step-n/]) assert.doesNotMatch(css, gone);
  // desktop: the phone crosses from left to right; phones: it stays centred and only its angle turns,
  // one way for step 1 and the other way for step 2, through the room between the steps
  assert.match(engine, /wrap\.style\.transform = "translate\(-50%,-50%\)";\s*phone\.style\.transform = `rotateY\(\$\{\(-14 \* side\)/);
  assert.match(engine, /wrap\.style\.transform = `translate\(calc\(-50% \+ \$\{0\.26 \* \(-1 \+ 2 \* p\) \* span\}px\),-50%\)`;/);
  assert.match(css, /@media \(max-width: 760px\)[\s\S]*\.ts-bridge \{ height: 26svh; \}/);
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

test("keyboard: the photo roll is one tab stop, and the globe turns with the arrow keys (6.2)", () => {
  assert.match(component, /<div className="ts-roll-view" tabIndex=\{0\} role="button" aria-label="[^"]+" data-roll-view="">/);
  assert.match(engine, /on\(rollView, "keydown", \(e\) => \{\s*if \(e\.key !== "Enter" && e\.key !== " "\) return;/);
  assert.match(component, /<canvas className="ts-globe" data-globe="" tabIndex=\{0\}/);
  assert.match(engine, /on\(globe, "keydown", \(e\) => \{\s*const step = e\.key === "ArrowRight"/);
  // the arrow keys and a drag turn the globe on top of the turn the scroll gives it
  assert.match(engine, /dragRot \+= step;/);
  for (const ring of [/\.ts-roll-view:focus-visible \{ outline/, /\.ts-globe:focus-visible \{ outline/, /\.ts-regions:focus-visible/, /\.ts-media:focus-visible/]) assert.match(css, ring);
});

test("every scene's touchable element is one measured interaction, and the plan describes the loop", () => {
  assert.deepEqual(TECH_SCENES.map((scene) => scene.interaction), ["flip", "gather", "save", "map_toggle", "globe_drag", "year_pick"]);
  assert.deepEqual([...TECH_STORY_INTERACTIONS], TECH_SCENES.map((scene) => scene.interaction));
  for (const name of ["tech_story_view", "tech_story_scene", "tech_story_interact"]) assert.match(plan, new RegExp("`" + name + "`"));
  assert.match(plan, /### 기술 소개 피드백 루프/);
});
