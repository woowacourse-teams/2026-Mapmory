import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const appSource = await readFile(new URL("../src/App.jsx", import.meta.url), "utf8");
const storyCss = await readFile(new URL("../src/hero-memory-story.css", import.meta.url), "utf8");
const html = await readFile(new URL("../index.html", import.meta.url), "utf8");

test("the high-priority preload matches the eager hero image", () => {
  const preload = html.match(/<link[^>]+rel="preload"[^>]+href="([^"]+)"[^>]+as="image"[^>]+fetchpriority="high"/);
  assert.ok(preload);
  assert.match(appSource, new RegExp(preload[1].replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
  assert.equal(preload[1], "/assets/team-jeju-coast-hero.jpg");
});

test("the landing page exposes the supplied App Store URL alongside Google Play", () => {
  assert.match(appSource, /https:\/\/apps\.apple\.com\/kr\/app\/mapmory-[^\"]+\/id6807056166/);
  assert.match(appSource, /platform="ios" label="App Store"/);
  assert.match(appSource, /platform="android" label="Google Play"/);
});

test("reduced motion collapses the scroll relay and keeps a static summary", () => {
  assert.match(appSource, /className="hero-reduced-summary"/);
  assert.match(storyCss, /prefers-reduced-motion: reduce[\s\S]*\.hero-memory-story \{ height: auto; min-height: 0; \}/);
  assert.match(storyCss, /hero-reduced-summary[\s\S]*display: flex/);
});

test("the hero handoff link only accepts clicks after its reveal begins", () => {
  assert.match(appSource, /const isMapCtaReady = relayState\.progress > 0\.93;/);
  assert.match(appSource, /data-map-cta-ready=\{isMapCtaReady \? "true" : "false"\}/);
  assert.match(storyCss, /\[data-map-cta-ready="true"\] \.hero-map-cta \{ pointer-events: auto; \}/);
  assert.doesNotMatch(storyCss, /\[data-recorded="true"\] \.hero-map-cta/);
});
