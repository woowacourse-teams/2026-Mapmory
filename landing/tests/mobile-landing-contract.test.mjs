import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const appSource = await readFile(new URL("../src/App.jsx", import.meta.url), "utf8");
const stylesSource = await readFile(new URL("../src/styles.css", import.meta.url), "utf8");
const heroStylesSource = await readFile(new URL("../src/hero-memory-story.css", import.meta.url), "utf8");

test("mobile hero has one focal photo, a clear title hierarchy, and a non-looping entry sequence", () => {
  assert.match(appSource, /<span>여행의 순간을,<\/span><em>나만의 지도로\.<\/em>/);
  assert.match(appSource, /useMediaQuery\("\(max-width: 560px\)"\)/);
  assert.equal((appSource.match(/className="hero-mobile-source-record"/g) ?? []).length, 1);
  assert.match(appSource, /waitForIdle/);
  assert.match(appSource, /polygonsTransitionDuration=\{360\}/);
  assert.match(heroStylesSource, /hero-mobile-record-absorb 1800ms/);
  assert.doesNotMatch(heroStylesSource, /hero-mobile-record-absorb[^;]*infinite/);
  assert.match(appSource, /HERO_CUE_POST_MOTION_DELAY_MS = 400/);
  assert.match(appSource, /entryState\.isComplete/);
  assert.match(appSource, /useState\(prefersReducedMotion\)/);
  assert.match(appSource, /아래로 내려 기록 과정을 보세요/);
  assert.match(heroStylesSource, /\.hero-mobile-title em \{[^}]*font-family: "Nanum Pen Script"[^}]*font-size: clamp\(43px, 12vw, 52px\);[^}]*text-shadow:/s);
  assert.match(heroStylesSource, /hero-mobile-cue-nudge 560ms[^;]* 1;/);
  assert.match(heroStylesSource, /hero-mobile-experience-cue\.is-hidden/);
  assert.doesNotMatch(heroStylesSource, /\.hero-mobile-experience-cue \{ display: none; \}/);
});

test("desktop relay keeps the completed map message and globe in one centered scene", () => {
  assert.match(appSource, /mapLine: "기록이 쌓일수록, 나만의 지도가 완성돼요\."/);
  assert.match(appSource, /className="hero-map-line-keyword"><em>나만의 지도<\/em>가<\/span>/);
  assert.match(heroStylesSource, /\.hero-line-map em \{[^}]*font-family: "Nanum Pen Script"[^}]*font-size: 1\.28em;/s);
  assert.match(heroStylesSource, /\.hero-memory-story \.hero-line-map \{[^}]*left: 43%;/s);
  assert.match(appSource, /const tabletLayout = window\.innerWidth <= 680;/);
  assert.match(heroStylesSource, /@media \(min-width: 681px\) and \(max-width: 900px\)/);
  assert.match(heroStylesSource, /\.hero-memory-story:not\(\[data-relay-phase="intro"\]\) \.hero-map-story \{[\s\S]*?min-height: min\(520px, calc\(100svh - 150px\)\);/);
  assert.match(heroStylesSource, /\.hero-memory-story:not\(\[data-relay-phase="intro"\]\) \.hero-globe-preview \{[\s\S]*?top: 46%;[\s\S]*?translate3d\(-50%, -50%, 0\)/);
});

test("mobile gallery keeps controls outside the uncropped photo and touch targets readable", () => {
  assert.match(appSource, /memory-mobile-photo-caption/);
  assert.match(appSource, /memory-sheet-gallery-controls/);
  assert.match(appSource, /!hasGallery && <div className="memory-single-title"><h2>\{memory\.title\}<\/h2><\/div>/);
  assert.match(appSource, /<CaretDown size=\{16\}/);
  assert.match(stylesSource, /\.world-memory-modal \.memory-image-wrap\.is-gallery img \{ object-fit: contain; \}/);
  assert.match(stylesSource, /\.world-memory-modal \.memory-photo-meta \{ display: none; \}/);
  assert.match(stylesSource, /\.world-memory-modal \.memory-sheet-gallery-controls > button \{ width: 44px; height: 44px;/);
  assert.match(stylesSource, /\.world-memory-modal \.world-memory-close \{[^}]*min-height: 44px;[^}]*border-color: var\(--accent\);/s);
  assert.match(stylesSource, /\.world-memory-modal \.memory-single-title \{[^}]*display: flex;/s);
  assert.match(stylesSource, /\.world-memory-modal \.memory-single-title h2 \{[^}]*Nanum Pen Script/s);
  assert.match(stylesSource, /\.world-memory-modal \.memory-mobile-photo-caption \{[^}]*Nanum Pen Script/s);
  assert.match(stylesSource, /--memory-diary-title-size: clamp\(22px, 3vw, 26px\)/);
});

test("mobile memory panel is a modal sheet that owns one reversible browser history entry", () => {
  assert.match(appSource, /window\.history\.pushState\(/);
  assert.match(appSource, /window\.addEventListener\("popstate", handleHistoryBack\)/);
  assert.match(appSource, /isWorldMemoryHistoryEntry\(window\.history\.state\)/);
  assert.match(appSource, /window\.history\.back\(\)/);
  assert.match(appSource, /createPortal\(/);
  assert.match(appSource, /root\?\.setAttribute\("inert", ""\)/);
  assert.match(appSource, /document\.body\.style\.overflow = "hidden"/);
  assert.match(stylesSource, /\.world-memory-modal \{ position: fixed;[^}]*inset: 0;/);
  assert.match(stylesSource, /\.world-memory-backdrop \{[^}]*background: rgba\(4, 11, 8, 0\.58\);[^}]*touch-action: none;/s);
  assert.match(stylesSource, /\.world-memory-modal \.world-memory-card \{ position: relative;[^}]*animation: memory-sheet-in/s);
  assert.match(stylesSource, /\.world-memory-modal \.memory-card-body \{ display: none; \}/);
  assert.match(appSource, /onPointerUp=\{handleSwipeEnd\}/);
  assert.match(appSource, /onClose\("button"\)/);
  assert.doesNotMatch(appSource, /scrollIntoView/);
});

test("globe guidance reacts to the first real gesture and keeps the current zoom", () => {
  assert.match(appSource, /지구본을 좌우로 움직여보세요/);
  assert.match(appSource, /onGuideDismiss\(\)/);
  assert.match(appSource, /altitude: currentViewpoint\?\.altitude/);
  assert.match(stylesSource, /\.globe-onboarding-overlay \{[^}]*pointer-events: none;/s);
});

test("Korea detail keeps an overview route after every sample is recorded", () => {
  assert.match(appSource, /기록 목록 보기/);
  assert.match(appSource, /createKoreaDetailHistoryState\(window\.history\.state, memory\.key\)/);
  assert.match(appSource, /window\.addEventListener\("popstate", handleKoreaHistoryBack\)/);
  assert.match(appSource, /showKoreaOverview\(\{ consumeHistory: false \}\)/);
  assert.match(appSource, /대한민국 지도로 돌아가기/);
});
