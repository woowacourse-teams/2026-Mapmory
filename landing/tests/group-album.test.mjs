import assert from "node:assert/strict";
import { access, mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import path from "node:path";
import test from "node:test";
import { fileURLToPath, pathToFileURL } from "node:url";
import { build } from "esbuild";
import { ANALYTICS_EVENTS, isSupportedEvent, trackEvent } from "../src/analytics.js";

// 2026-11-10: the #group scene (GroupAlbum.jsx), the hero's jump to it, the own-store button and the desktop QR.
const landing = fileURLToPath(new URL("../", import.meta.url));
const read = (file) => readFile(new URL(file, import.meta.url), "utf8");
const source = await read("../src/GroupAlbum.jsx");
const css = await read("../src/group-album.css");
const app = await read("../src/App.jsx");
const hero = await read("../src/PhotoWallHero.jsx");

async function render() {
  const stub = {
    name: "stubs",
    setup(api) {
      api.onResolve({ filter: /\/analytics\.js$/ }, () => ({ path: "analytics", namespace: "stub" }));
      api.onLoad({ filter: /.*/, namespace: "stub" }, () => ({ contents: "export const ANALYTICS_EVENTS = {}; export const trackEvent = () => false;", loader: "js" }));
      api.onResolve({ filter: /\.css$/ }, () => ({ path: "css", namespace: "empty" }));
      api.onLoad({ filter: /.*/, namespace: "empty" }, () => ({ contents: "", loader: "js" }));
    },
  };
  const result = await build({
    stdin: { contents: 'export { GroupAlbum } from "./src/GroupAlbum.jsx"; export { renderToStaticMarkup } from "react-dom/server"; export { createElement } from "react";', resolveDir: landing, loader: "js" },
    bundle: true, format: "esm", platform: "node", write: false, jsx: "automatic", logLevel: "silent", plugins: [stub],
    external: ["react", "react/*", "react-dom", "react-dom/*"],
  });
  const cache = path.join(landing, "node_modules/.cache");
  await mkdir(cache, { recursive: true });
  const dir = await mkdtemp(path.join(cache, "group-album-test-"));
  try {
    const file = path.join(dir, "bundle.mjs");
    await writeFile(file, result.outputFiles[0].text);
    const { GroupAlbum, renderToStaticMarkup, createElement } = await import(pathToFileURL(file).href);
    return renderToStaticMarkup(createElement(GroupAlbum));
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
}

test("the group album scene renders a real heading, five slots on each side and five named photos", async () => {
  const html = await render();
  assert.match(html, /^<section class="ga" id="group" aria-labelledby="group-title">/);
  // 2026-11-10 (direction 다): the second line of a two-line title is muted
  assert.match(html, /<h2 id="group-title">친구와 다녀온 여행은<br\/><span class="title-soft">한 앨범에 모여요<\/span><\/h2>/);
  assert.match(html, /<div class="ga-scene" aria-hidden="true">/); // the picture is decoration; the heading says it
  assert.equal(html.match(/data-src="true"/g).length, 5);
  assert.equal(html.match(/data-dst="true"/g).length, 5);
  assert.deepEqual([...html.matchAll(/data-fly="true"><i>([^<]+)<\/i>/g)].map(([, name]) => name), ["성우", "성우", "민지", "민지", "지우"]);
  assert.doesNotMatch(html, /background-image|<img/); // photos load only when the scene comes near
});

test("the scene's photos are small crops that exist, and the hero and the page link to the scene", async () => {
  const files = [...source.matchAll(/"\/assets\/group\/([^"]+\.webp)"/g)].map(([, file]) => file);
  assert.equal(files.length, 5);
  for (const file of files) await access(new URL(`../public/assets/group/${file}`, import.meta.url));
  assert.doesNotMatch(source, /team-usa-/); // never the 1600px globe photos
  assert.match(hero, /<a className="wall-hero-new" href="#group"/);
  assert.match(app, /<GroupAlbum \/>/);
});

test("the scene follows the scroll only near the screen and shows the finished album when it cannot be pinned", () => {
  assert.match(source, /const schedule = \(\) => \{ if \(onScreen && !frame\)/);
  assert.match(source, /if \(next !== whoText\)/); // no DOM write per scroll frame
  assert.match(source, /reduce\.matches \|\| short\.matches \? 1 : progress\(\)/);
  assert.match(source, /resize\.observe\(screen\)/);
  assert.match(source, /document\.fonts\?\.ready/);
  assert.match(css, /@media \(max-height: 499px\) \{ \.ga \{[^}]*height: auto;/);
  assert.match(css, /@media \(prefers-reduced-motion: reduce\) \{ \.ga \{[^}]*height: auto;/);
  // one line each, so the names line can never push the photo slots down
  assert.match(css, /\.ga-bar, \.ga-meta, \.ga-title, \.ga-who \{[^}]*white-space: nowrap;/);
  assert.match(css, /:root\[data-theme="dark"\] \.ga \{[^}]*--ga-screen:/);
});

test("group album measurement: its own passive view event and the hero jump, never experience_view", () => {
  assert.equal(ANALYTICS_EVENTS.GROUP_ALBUM_VIEW, "group_album_view");
  assert.equal(isSupportedEvent("group_album_view"), true);
  assert.equal(trackEvent("group_album_view", { experience_type: "globe" }), false);
  assert.match(source, /createPassiveViewTracker\(\{ onView: \(\) => trackEvent\(ANALYTICS_EVENTS\.GROUP_ALBUM_VIEW, \{ experience_type: "group_album" \}\) \}\)/);
  assert.match(source, /document\.addEventListener\("visibilitychange", syncView\)/);
  // the scene must lie across the middle of the window: a share of the scene cannot be reached when it is unpinned
  // and taller than the window (a phone on its side)
  assert.match(source, /new IntersectionObserver\(\(\[entry\]\) => \{ half = entry\.isIntersecting; syncView\(\); \}, \{ rootMargin: "-50% 0px -50% 0px" \}\)/);
  // the album grid changes columns with the window's height, which neither observed box reports
  assert.match(source, /window\.addEventListener\("resize", measure\)/);
  assert.match(source, /window\.removeEventListener\("resize", measure\)/);
  assert.doesNotMatch(source, /EXPERIENCE_VIEW/);
  assert.match(hero, /trackEvent\(ANALYTICS_EVENTS\.EXPERIENCE_CTA_CLICK, \{ experience_type: "group_album", cta_placement: "hero_group" \}\)/);
});

test("the desktop QR keeps a quiet zone and square corners and shows only where it fits", async () => {
  const svg = await read("../public/assets/qr-desktop-hero.svg");
  const size = Number(svg.match(/viewBox="0 0 (\d+) \d+"/)[1]);
  assert.equal((size - 8 - 17) % 4, 0); // modules + a 4-module quiet zone on each side
  assert.match(svg, /fill="#ffffff"/);
  const styles = await read("../src/styles.css");
  assert.match(styles, /\.hero-qr img \{ display: block; width: 112px; height: 112px; background: #fff; \}/);
  // the QR makes the hero words taller, so it shows only in windows tall enough
  assert.match(styles, /@media \(min-width: 900px\) and \(min-height: 700px\) and \(hover: hover\) and \(pointer: fine\) \{ \.hero-qr \{ display: flex; \} \}/);
});

test("direction 다: a quiet hero whose words sit in a left column on wide screens", async () => {
  const hero = await read("../src/photo-story.css");
  const styles = await read("../src/styles.css");
  const engine = await read("../src/heroWallEngine.js");
  // no pills or bold secondary text: a plain kicker, a muted second title line, black buttons
  assert.match(hero, /\.wall-hero-kicker \{ display: inline-block; color: var\(--muted\); font-size: 14px; font-weight: 400;/);
  assert.match(hero, /\.wall-hero h1 span \{ display: block; color: var\(--muted\);/);
  assert.match(hero, /\.wall-hero-chips button\[aria-pressed="true"\] \{ background: var\(--btn\); border-color: var\(--btn\); color: var\(--on-btn\); \}/);
  assert.match(styles, /\.button-primary \{ color: var\(--on-btn\); background: var\(--btn\); border-color: var\(--btn\); border-radius: 999px; \}/);
  assert.match(styles, /--btn: #18201b;\s*--on-btn: #f6f9f7;/);
  assert.match(styles, /--btn: #edf3ef;\s*--on-btn: #101513;/);
  // the side layout starts where the buttons and the QR fit one row of the column, and tells the engine
  const side = hero.match(/@media \(min-width: 1100px\) \{\n([\s\S]*?)\n\}\n/)[1];
  assert.match(side, /\.wall-hero \{ --hero-side: 1; --hero-left: max\(24px, calc\(50% - 540px\)\); \}/);
  assert.match(side, /\.wall-hero-copy \{ left: var\(--hero-left\); transform: translateY\(-50%\); width: min\(600px, 54vw\); text-align: left; \}/);
  // the column is centred between the header and the foot: the title follows the window's height, sooner with the QR
  assert.match(side, /\.wall-hero h1 \{ font-size: clamp\(34px, min\(5vw, 28\.7svh - 122px\), 72px\); \}/);
  assert.match(hero, /@media \(min-width: 1100px\) and \(min-height: 700px\) and \(hover: hover\) and \(pointer: fine\) \{ \.wall-hero h1 \{ font-size: clamp\(34px, min\(5vw, 28\.7svh - 140px\), 72px\); \} \}/);
  assert.match(styles, /@media \(min-width: 1100px\) \{ \.hero-store \{ justify-content: flex-start; \} \}/);
  // the engine keeps the lens, the taps and a picked place's photos in the open part beside the words
  assert.match(engine, /side = style\.getPropertyValue\("--hero-side"\)\.trim\(\) === "1";/);
  assert.match(engine, /headerSpace = parseFloat\(style\.getPropertyValue\("--header-space"\)\) \|\| 84;/); // the grid starts under the header the CSS sets
  assert.match(engine, /placeReadable\(kept, \{ x0: open, y0: headerSpace \+ 24, x1: W - edge, y1: H - edge \}, gap, 132, readable\);/);
  assert.match(engine, /if \(side\) lens\.tx = Math\.max\(lens\.tx, open \+ 40\);/);
  // a wall photo that reaches into the words' column is never lit, labelled or tapped (review 2026-11-11: lit photos
  // sat on the QR caption, and unpicking flashed photos across the column)
  assert.match(engine, /const inColumn = \(t\) => side && t\.x < open;/);
  assert.match(engine, /const target = pick \? keep : inColumn\(t\) \? 0 : inLens;/);
  assert.match(engine, /if \(pick \? keep : t\.v > 0\.3 && !inColumn\(t\)\) top\.push\(item\); else paint\(item\);/);
  assert.match(engine, /if \(!pick && !inColumn\(t\) && d < hitD/);
  assert.match(engine, /if \(a && !pick && inColumn\(tiles\[i\]\)\) continue;/);
  // the clearing is measured from the column in pixels, so a very wide window does not fade photos under the words
  assert.match(engine, /createLinearGradient\(open - 200, 0, open \+ Math\.min\(300, \(W - open\) \/ 2\), 0\)/);
});

test("direction 다: the centred hero keeps its words under the header in short windows", async () => {
  const hero = await read("../src/photo-story.css");
  // the words never rise above the header: they move down instead (the hero is 100svh, at least 560px)
  assert.match(hero, /\.wall-hero-copy \{ --hero-lift: -54%;[^}]*transform: translate\(-50%, var\(--hero-lift\)\); transform: translate\(-50%, max\(var\(--hero-lift\), calc\(var\(--header-space\) \+ 8px - max\(50svh, 280px\)\)\)\);/);
  assert.match(hero, /@media \(max-width: 760px\) \{\n\s*\.wall-hero-copy \{ --hero-lift: -60%; \}/);
  // between the phone layout and the side layout the title follows the window's height (review 2026-11-11: at
  // 1000x560 the kicker sat behind the header after a pick)
  assert.match(hero, /@media \(min-width: 761px\) and \(max-width: 1099px\) \{ \.wall-hero h1 \{ font-size: clamp\(34px, min\(5\.4vw, 39\.9svh - 191px\), 68px\); \} \}/);
});

test("direction 다: page-level fills are the button colour, and app green stays inside the phone screens", async () => {
  const styles = await read("../src/styles.css");
  const tech = await read("../src/tech-story.css");
  const engine = await read("../src/heroWallEngine.js");
  const all = `${styles}\n${await read("../src/photo-story.css")}\n${await read("../src/group-album.css")}`;
  assert.doesNotMatch(all, /background: var\(--ink\)/); // header menu, skip link, memory close button, chips: all --btn
  assert.match(styles, /\.header-store-popover \.button-store:hover \{ color: var\(--on-btn\); background: var\(--btn\); border-color: var\(--btn\); \}/);
  assert.match(styles, /\.skip-link \{[^}]*background: var\(--btn\); color: var\(--on-btn\);/);
  assert.doesNotMatch(engine, /tok\.ink|"on-ink"/); // the lens label of a place photo
  assert.match(engine, /ctx\.fillStyle = t\.place \? tok\.btn : tok\.surface;/);
  // inside the phone of the tech story the app's own green buttons stay
  assert.deepEqual([...tech.matchAll(/^(\.ts-[\w.-]+) \{[^}]*background: var\(--ink\)/gm)].map(([, sel]) => sel), [".ts-load.is-ready", ".ts-save", ".ts-share-btn.is-pressed"]);
  // the tech story head follows the same quiet type: a muted label and a muted second title line
  assert.match(tech, /\.ts-eyebrow \{ margin: 0; color: var\(--muted\); font-size: 14px; font-weight: 400; letter-spacing: 0\.01em; \}/);
  assert.match(tech, /\.ts-head h2 \{[^}]*line-height: 1\.25; letter-spacing: -0\.04em;/);
  assert.match(await read("../src/TechStory.jsx"), /<h2 id="tech-title">\{TECH_COPY\.title\[0\]\}<br \/><span className="title-soft">\{TECH_COPY\.title\[1\]\}<\/span><\/h2>/);
});
