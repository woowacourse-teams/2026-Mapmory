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
  assert.match(html, /<h2 id="group-title">친구와 다녀온 여행은<br\/>한 앨범에 모여요<\/h2>/);
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
  assert.doesNotMatch(source, /EXPERIENCE_VIEW/);
  assert.match(hero, /trackEvent\(ANALYTICS_EVENTS\.EXPERIENCE_CTA_CLICK, \{ experience_type: "group_album", cta_placement: "hero_group" \}\)/);
});

test("the desktop QR keeps a quiet zone and square corners, and points at the landing without UTMs", async () => {
  const svg = await read("../public/assets/qr-desktop-hero.svg");
  const size = Number(svg.match(/viewBox="0 0 (\d+) \d+"/)[1]);
  assert.equal((size - 8 - 17) % 4, 0); // modules + a 4-module quiet zone on each side
  assert.match(svg, /fill="#ffffff"/);
  const styles = await read("../src/styles.css");
  assert.match(styles, /\.hero-qr img \{ display: block; width: 112px; height: 112px; background: #fff; \}/);
  assert.match(styles, /@media \(min-width: 900px\) and \(hover: hover\) and \(pointer: fine\) \{ \.hero-qr \{ display: flex; \} \}/);
});
