import assert from "node:assert/strict";
import { access, readFile, stat } from "node:fs/promises";
import test from "node:test";

// 2026-10-16 review: the page must survive a failed globe chunk and blocked storage, and the first load stays light.
const read = (file) => readFile(new URL(file, import.meta.url), "utf8");
const app = await read("../src/App.jsx");
const css = await read("../src/styles.css");
const engines = await Promise.all(["rollStoryEngine.js", "recStoryEngine.js", "heroWallEngine.js", "photoStoryController.js", "storyCanvas.js"].map((f) => read(`../src/${f}`)));
const firstLoad = await Promise.all(["App.jsx", "PhotoWallHero.jsx", "PhotoStory.jsx", "photoStory.js", "photoStoryController.js", "heroWallEngine.js", "storyCanvas.js", "PhotoCredits.jsx"].map((f) => read(`../src/${f}`)));

// KS X 1001 Hangul = the 2,350 syllables at 0xB0A1-0xC8FE in EUC-KR (what scripts/split-landing-font.py keeps in core)
const KS_X_1001 = new Set();
{
  const decoder = new TextDecoder("euc-kr");
  for (let hi = 0xb0; hi <= 0xc8; hi++) for (let lo = 0xa1; lo <= 0xfe; lo++) KS_X_1001.add(decoder.decode(new Uint8Array([hi, lo])));
}

test("a globe chunk that fails to load shows a message instead of unmounting the page", () => {
  assert.match(app, /class GlobeLoadBoundary extends Component/);
  assert.match(app, /<GlobeLoadBoundary>\{materialFailed \? <GlobeUnavailable \/> : <Suspense/);
  assert.match(app, /import\("three"\)\.then\([\s\S]*?\}\)\.catch\(\(\) => \{ if \(active\) setMaterialFailed\(true\); \}\);/);
});

test("three waits for the globe to come near", () => {
  assert.match(app, /if \(!hasGlobeMounted\) return undefined;\s*let active = true;\s*let material;\s*import\("three"\)/);
});

test("localStorage is only touched inside try/catch", () => {
  for (const line of app.split("\n").filter((l) => l.includes("localStorage."))) assert.match(line, /try \{/, line);
});

test("the stories avoid Array.prototype.at (iOS 14-15.3) and keep maps, d3 and the engines out of the first load", () => {
  for (const source of engines) assert.doesNotMatch(source, /\.at\(/);
  // the first-load modules never import geography or the story engines statically
  for (const source of firstLoad) assert.doesNotMatch(source, /^import [^;]*(d3-geo|topojson-client|world-atlas|story-globe-dots|rollStoryEngine|recStoryEngine|photo-credits)/m);
  const controller = firstLoad[4];
  assert.match(controller, /import\("\.\/rollStoryEngine\.js"\), import\("\.\/recStoryEngine\.js"\)/);
  assert.match(controller, /\{ rootMargin: "100% 0px" \}/);
});

test("the landing font is the core subset; the ext family loads only for rare syllables", async () => {
  for (const weight of ["regular", "bold"]) {
    assert.ok(css.includes(`/assets/fonts/line-seed-kr-${weight}-core.woff2`));
    assert.ok(css.includes(`/assets/fonts/line-seed-kr-${weight}-ext.woff2`));
    assert.ok((await stat(new URL(`../public/assets/fonts/line-seed-kr-${weight}-core.woff2`, import.meta.url))).size < 260 * 1024);
  }
  assert.doesNotMatch(css, /line-seed-kr-(regular|bold)\.woff2/);
  assert.match(css, /font-family: "LINE Seed Sans KR", "LINE Seed Sans KR Ext", system-ui, sans-serif;/);
  // every Hangul syllable in the landing's source is in core (KS X 1001), so the ext files are never needed today
  const { readdir } = await import("node:fs/promises");
  const files = (await readdir(new URL("../src/", import.meta.url), { recursive: true })).filter((f) => /\.(jsx?|json|css)$/.test(f));
  const text = (await Promise.all(files.map((f) => read(`../src/${f}`)))).join("") + (await read("../index.html"));
  const outside = [...new Set(text.match(/[\uAC00-\uD7A3]/g))].filter((c) => !KS_X_1001.has(c));
  assert.deepEqual(outside, []);
});

test("team photos ship as webp", async () => {
  assert.doesNotMatch(app, /\/assets\/team-[^"]+\.jpe?g"/);
  for (const [, file] of app.matchAll(/"\/assets\/(team-[^"]+\.webp)"/g)) await access(new URL(`../public/assets/${file}`, import.meta.url));
});
