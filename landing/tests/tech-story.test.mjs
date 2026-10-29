import assert from "node:assert/strict";
import { readFile, stat } from "node:fs/promises";
import test from "node:test";
import { TECH_COPY, TECH_COUNTRIES, TECH_PLACES, TECH_PROVINCES, TECH_SCENES, TECH_SHARE, TECH_STEPS, techScenes } from "../src/techStory.js";
import KOREA from "../src/data/tech-korea-map.json" with { type: "json" };
import countries110 from "world-atlas/countries-110m.json" with { type: "json" };
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

test("two steps, six scenes plus the share scene, one short title each (티온, 10-29)", () => {
  assert.deepEqual(TECH_STEPS.map(({ label, side }) => [label, side]), [["사진 찾기", "left"], ["지도 모으기", "right"]]);
  // 10-29: step 1 is the app's "어디 사진을 불러올까요?" screen (a place typed in, its photos come out of the album,
  // nothing goes to the server), step 2 only fills places; short titles
  assert.deepEqual(TECH_SCENES.map(({ step, title }) => [step, plain(title)]), [
    ["find", "장소만 넣으면"],
    ["find", "사진첩에서 알아서 뽑아와요"],
    ["find", "찾는 동안 사진은 서버로 안 가요"],
    ["find", "고른 사진만 앱에 올라가요"],
    ["map", "다녀온 곳이 지도에 칠해져요"],
    ["map", "해외는 나라별로 칠해져요"],
    ["map", "다녀온 곳을 타임라인 영상으로 공유해요"],
  ]);
  for (const { title } of TECH_SCENES) assert.ok(plain(title).length <= 22, `${title} stays short`);
  // a no-break space only ever joins two words, to keep a phrase on one line on a 360px phone
  for (const { title } of TECH_SCENES) assert.doesNotMatch(title, /(^|\s)\u00a0|\u00a0(\s|$)/);
  assert.deepEqual(TECH_COPY.title, ["장소만 넣으면", "사진이 지도에 모여요"]);
  // the screen is the app's own: its question, the 장소 label, its placeholder and its button
  for (const words of ["어디 사진을 불러올까요?", ">장소<", "사진 불러오기"]) assert.ok(component.includes(words), words);
  assert.match(css, /content: "장소 또는 행정구역 검색"/);
});

test("no small text sits under the titles, and the 3만 장 claim is gone with its counter (10-29)", () => {
  assert.equal(TECH_SCENES.filter((scene) => scene.footnote).length, 0);
  assert.equal(TECH_COPY.footnote, undefined);
  assert.doesNotMatch(component, /ts-footnote|ts-counter|3만/);
  // a beat is the step label and the title only
  assert.doesNotMatch(component.slice(component.indexOf('className="ts-copy"')), /<p className="ts-(sub|note|body)/);
});

test("the share scene is configured in one place and can be swapped or turned off", async () => {
  // off since 10-29 (티온: the map step only shows places filling in); the scene still works when turned back on
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
  assert.equal(techScenes().length, 7);
  assert.equal(techScenes({ ...TECH_SHARE, enabled: false }).length, 6);
  // the share video is fetched on intent, never on reaching the scene, and a tap on blank space in scene 3 is no try
  assert.match(engine, /for \(const ev of \["pointerenter", "pointerdown", "focusin"\]\) on\(shareBox, ev, \(\) => loadVideo\(\)/);
  assert.doesNotMatch(engine, /\(\) => loadVideo\(\),\n  \];/);
  assert.match(engine, /if \(!t\) return;\n    byHandPick\(\);/);
  // the variant switch is for local checks only, and the scene-reach confirm re-reads the scroll first
  assert.match(component, /const local = import\.meta\.env\.DEV \|\| \["localhost", "127\.0\.0\.1"\]\.includes\(window\.location\.hostname\)/);
  assert.match(component, /destroy\?\.sample\?\.\(\); check\(\);/);
});

test("the map and the globe fill the recorded places in date order, nothing else (티온, 10-28 and 10-29)", () => {
  // one source: every place label in the photo sprite is a record with a position, a date and what it fills
  const labels = new Set(SPRITE.tiles.map((tile) => tile[1]));
  assert.deepEqual(new Set(TECH_PLACES.map((p) => p.name)), labels);
  for (const p of TECH_PLACES) {
    assert.ok(Math.abs(p.lon) <= 180 && Math.abs(p.lat) <= 90);
    assert.match(p.date, /^\d{4}\. \d{2}\. \d{2}$/);
    assert.ok(!!p.sd !== !!p.country, `${p.name} fills a 시·도 or a country`);
  }
  // the 시·도 in date order, each once, each with a path in the map data
  assert.deepEqual([...TECH_PROVINCES], ["전남", "경북", "전북", "서울", "강원", "부산", "제주"]);
  for (const name of TECH_PROVINCES) assert.match(KOREA.pv[name], /^M/);
  // home first, then the countries in date order, each a country in world-atlas
  const names = new Set(countries110.objects.countries.geometries.map((g) => g.properties.name));
  assert.equal(TECH_COUNTRIES[0], "South Korea");
  for (const name of TECH_COUNTRIES) assert.ok(names.has(name), name);
  assert.equal(new Set(TECH_COUNTRIES).size, TECH_COUNTRIES.length);
  // no trip, route, dots, place pill or names on the map: only fills
  const code = stripComments(engine);
  for (const gone of [/tripFrame/, /TECH_TRIP/, /route/, /ts-tag/, /setLevel/]) assert.doesNotMatch(code, gone);
  for (const gone of [/data-trip/, /ts-region-chip/, /is-trip/]) assert.doesNotMatch(component, gone);
  assert.match(code, /const scrubMap = \(q\) => setFilled\(/);
  assert.match(code, /FILLS\.slice\(0, fills\)/);
});

test("every scene plays by the scroll position, not by timers or taps (티온, 10-28)", () => {
  const code = stripComments(engine);
  // the frame reads how far the focus line is through the scene's room and lays the scene out from it
  assert.match(code, /if \(act === at\.type\) scrubType\(q\);\s*else if \(act === at\.gather\) scrubGather\(q\);\s*else if \(act === at\.safe\) scrubSafe\(q\);\s*else if \(act === at\.save\) scrubPick\(q\);\s*else if \(act === at\.map\) scrubMap\(q\);/);
  assert.match(code, /if \(act === at\.globe\) scrubGlobe\(q\);/);
  // the first three scenes share the app's form screen
  assert.match(code, /const screenOf = \(i\) => Math\.max\(0, i - at\.safe\);/);
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
  for (const rule of [/\.ts-field \{ min-height: 44px; \}/, /\.ts-save \{ min-height: 48px;/]) assert.match(phone, rule);
  assert.match(css, /\.ts-years button \{[^}]*min-width: 48px; min-height: 48px;/);
  // gather photos: 3-4 columns of about 46px or more on phones
  assert.match(engine, /narrow\.matches \? Math\.max\(3, Math\.min\(4, Math\.floor\(\(w \+ GAP\) \/ \(46 \+ GAP\)\)\)\) : 6/);
});

test("a quick scroll never waits for words, and the steps are told apart by the angle of the phone (티온, 10-27)", () => {
  // the words live in the sticky stage and change in place; the beats are empty scroll room
  const stage = component.slice(component.indexOf('data-tech-stage=""'), component.indexOf('className="ts-beats"'));
  assert.match(stage, /<div className="ts-captions">/);
  assert.match(stage, /data-caption=\{i\}/);
  assert.match(stage, /<StepLabel text=\{step\.label\} \/>/);
  assert.match(component, /<div className="ts-beats" aria-hidden="true">/);
  assert.match(component, /<div className="ts-beat" data-beat=\{i\} key=\{scene\.title\} \/>/);
  assert.match(css, /\.ts-copy\.is-on, \.ts:not\(\.is-live\) \.ts-copy\[data-caption="0"\] \{ opacity: 1;/);
  assert.match(engine, /showCaption\(captions\[act\]\)/);
  // nothing extra to read: no step numbers, progress dots or second phone
  for (const gone of [/<Progress/, /data-phone-next/, /StepLabel n=/]) assert.doesNotMatch(component, gone);
  for (const gone of [/\.ts-dots/, /\.is-next/, /\.ts-step-n/]) assert.doesNotMatch(css, gone);
  // desktop: the phone crosses from left to right; phones: it stays centred and only its angle turns,
  // one way for step 1 and the other way for step 2, through the room between the steps
  assert.match(engine, /wrap\.style\.transform = "translate\(-50%,-50%\)";\s*phone\.style\.transform = `rotateY\(\$\{\(-14 \* side\)/);
  assert.match(engine, /wrap\.style\.transform = `translate\(calc\(-50% \+ \$\{0\.22 \* \(-1 \+ 2 \* p\) \* span\}px\),-50%\)`;/);
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

test("keyboard: the record field is a button, and the globe turns with the arrow keys (6.2)", () => {
  assert.match(component, /<button type="button" className="ts-field is-empty" data-field="" aria-label="[^"]+">/);
  assert.match(engine, /const at = Object\.fromEntries\(/);
  assert.match(engine, /on\(field, "click", \(\) => \{/);
  assert.match(component, /<canvas className="ts-globe" data-globe="" tabIndex=\{0\}/);
  assert.match(engine, /on\(globe, "keydown", \(e\) => \{\s*const step = e\.key === "ArrowRight"/);
  // the arrow keys and a drag turn the globe on top of the turn the scroll gives it
  assert.match(engine, /dragRot \+= step;/);
  for (const ring of [/\.ts-field:focus-visible \{ outline/, /\.ts-globe:focus-visible \{ outline/, /\.ts-regions:focus-visible/, /\.ts-media:focus-visible/]) assert.match(css, ring);
});

test("every scene's touchable element is one measured interaction, and the plan describes the loop", () => {
  // the safety scene has nothing to tap; it only rises with the scroll
  assert.deepEqual(TECH_SCENES.map((scene) => scene.interaction ?? null), ["place_pick", "gather", null, "save", "map_toggle", "globe_drag", "year_pick"]);
  assert.deepEqual([...TECH_STORY_INTERACTIONS], TECH_SCENES.map((scene) => scene.interaction).filter(Boolean));
  for (const name of ["tech_story_view", "tech_story_scene", "tech_story_interact"]) assert.match(plan, new RegExp("`" + name + "`"));
  assert.match(plan, /### 기술 소개 피드백 루프/);
});
