// Browser checks for the #how travel replay's two reach events, shared by verify-analytics-browser.mjs.
// They read the replay only through its QA hooks (listed in src/travelReplayEngine.js), not its styling:
//   section#how [data-mode]            "scroll" | "static", the mode the replay runs in now (= how_story_view.story_mode)
//   [data-tr-track][data-beat]         the caption on screen, 0-4 (= scene_index - 1)
//   [data-tr-step]                     the five steps, shown as a list in the static version
import assert from "node:assert/strict";
import { BEAT_SPAN } from "../src/travelReplay.js";

const MODE = "section#how [data-mode]";
const TRACK = "section#how [data-tr-track]";
// Where in the scroll (b0 = 0..6) each caption sits comfortably: the middle of its span.
const SCENE_AT = [0.45, 1.4, 2.4, 3.9, 5.45];

export const dataLayerEvents = (page) => page.evaluate(() => (window.dataLayer ?? []).map((args) => [...args]).filter(([command]) => command === "event"));
export const storyEvents = async (page, eventName) => (await dataLayerEvents(page)).filter(([, name, props]) => name === eventName && props?.experience_type === "how_story");
const sceneIndexes = async (page) => (await storyEvents(page, "how_story_scene")).map(([, , props]) => props.scene_index);
export const storyOrder = async (page) => (await dataLayerEvents(page))
  .filter(([, name, props]) => /^how_story_(view|scene)$/.test(name) && props?.experience_type === "how_story")
  .map(([, name, props]) => (name === "how_story_scene" ? `scene:${props.scene_index}` : `view:${props.story_mode}`));
export const storyExperienceEvents = async (page) => (await dataLayerEvents(page))
  .filter(([, name, props]) => /^(experience_(view|start|end)|memory_open)$/.test(name) && props?.experience_type === "how_story");
export const storyMode = async (page) => {
  await page.waitForFunction((selector) => document.querySelector(selector), MODE);
  assert.equal(await page.locator("section#how").getAttribute("aria-labelledby"), "how-title");
  return page.evaluate((selector) => document.querySelector(selector).dataset.mode, MODE);
};
export const settle = (page) => page.evaluate(() => new Promise((resolve) => {
  let last = -1;
  const check = () => (window.scrollY === last ? resolve(window.scrollY) : (last = window.scrollY, requestAnimationFrame(() => requestAnimationFrame(check))));
  check();
}));
const waitForStoryEvent = (page, name) => page.waitForFunction((eventName) => (window.dataLayer ?? [])
  .some((args) => args[0] === "event" && args[1] === eventName && args[2]?.experience_type === "how_story"), name);
// scrolls so the replay sits at b0 (0..6)
const scrollToBeat = (page, b0) => page.evaluate(([selector, at, span]) => {
  const track = document.querySelector(selector);
  const top = window.scrollY + track.getBoundingClientRect().top;
  window.scrollTo({ top: top + (track.offsetHeight - window.innerHeight) * at / span, behavior: "instant" });
}, [TRACK, b0, BEAT_SPAN]);

// Static version (reduced motion, a landscape phone): one passive view (static), and a scene once its step has stayed
// at least half in view, nearest the middle of the screen, for 700 ms. A step jumped over never counts; nothing twice.
export async function checkStaticStory(page) {
  assert.equal(await storyMode(page), "static");
  const items = page.locator("section#how [data-tr-step]");
  assert.equal(await items.count(), 5);
  const center = (scene) => items.nth(scene - 1).evaluate((node) => node.scrollIntoView({ block: "center", behavior: "instant" }));
  await page.locator("section#how").evaluate((node) => window.scrollTo({ top: window.scrollY + node.getBoundingClientRect().top, behavior: "instant" }));
  await waitForStoryEvent(page, "how_story_view");
  // the step nearest the middle at the section's start may count (the short list can sit there); then jump to 5
  await page.waitForTimeout(900);
  const before = (await sceneIndexes(page)).length;
  await center(5);
  await page.waitForFunction(() => (window.dataLayer ?? []).some((args) => args[0] === "event" && args[1] === "how_story_scene" && args[2]?.scene_index === 5));
  await page.waitForTimeout(300);
  assert.deepEqual((await sceneIndexes(page)).slice(before), [5], "the jump to step 5 counts only step 5");
  for (const scene of [1, 2, 3, 4]) {
    await center(scene);
    await page.waitForTimeout(900);
  }
  assert.deepEqual([...(await sceneIndexes(page))].sort(), [1, 2, 3, 4, 5]);
  for (const scene of [5, 4, 3, 2, 1]) {
    await center(scene);
    await page.waitForTimeout(900);
  }
  assert.deepEqual([...(await sceneIndexes(page))].sort(), [1, 2, 3, 4, 5]);
  const views = await storyEvents(page, "how_story_view");
  assert.equal(views.length, 1);
  assert.equal(views[0][2].story_mode, "static");
  assert.equal((await storyOrder(page))[0], "view:static");
  assert.equal((await storyExperienceEvents(page)).length, 0);
  assert.equal(JSON.stringify(await dataLayerEvents(page)).includes("/assets/"), false);
}

// Scroll mode, on a freshly loaded page: a fling past captions 1-4 (with a 900 ms main-thread stall while caption 1 is
// pinned) counts none of them and caption 5 at rest sends the view then scene 5; stopping on every caption on the way
// back sends 4-1 once each; a second read re-sends nothing. `reopen()` loads the page again; landing straight on
// caption 3 (a restored scroll or a deep link) must still send the view before the scene.
export async function checkScrollStory(page, { reopen }) {
  const beat = () => page.evaluate((selector) => Number(document.querySelector(selector).dataset.beat ?? -1), TRACK);
  const ready = async () => {
    assert.equal(await storyMode(page), "scroll");
    await page.waitForFunction((selector) => document.querySelector(selector)?.dataset.beat !== undefined, TRACK);
  };

  await ready();
  await page.evaluate((selector) => {
    const track = document.querySelector(selector);
    window.scrollTo({ top: window.scrollY + track.getBoundingClientRect().top - window.innerHeight / 2, behavior: "instant" });
  }, TRACK);
  await settle(page);
  // The fling runs inside the page as a momentum coast on a fixed curve in time, so a slow machine skips frames
  // instead of lingering on a caption. While caption 1 is pinned the main thread stalls for 900 ms: its dwell timer then
  // fires late, after the fling has left it, and must not count.
  const fling = await page.evaluate(([selector, span, target]) => new Promise((resolve) => {
    const track = document.querySelector(selector);
    const base = window.scrollY + track.getBoundingClientRect().top;
    const length = track.offsetHeight - window.innerHeight;
    const s0 = window.scrollY, s1 = base + length * target / span;
    const tau = 350;
    let stalledAt = null;
    requestAnimationFrame(() => {
      const start = performance.now();
      const step = () => {
        const t = performance.now() - start;
        if (stalledAt === null && track.dataset.beat === "0" && track.getBoundingClientRect().top <= 1) {
          stalledAt = "1";
          setTimeout(() => { const end = performance.now() + 900; while (performance.now() < end); }, 50);
        }
        window.scrollTo({ top: t < tau * 7 ? s0 + (s1 - s0) * (1 - Math.exp(-t / tau)) : s1, behavior: "instant" });
        if (t < tau * 7) requestAnimationFrame(step);
        else resolve({ stalledAt });
      };
      step();
    });
  }), [TRACK, BEAT_SPAN, SCENE_AT[4]]);
  assert.equal(fling.stalledAt, "1", JSON.stringify(fling));
  await settle(page);
  assert.equal(await beat(), 4);
  await page.waitForTimeout(900);
  assert.deepEqual(await storyOrder(page), ["view:scroll", "scene:5"]);
  // Back up and down again, resting on every caption: 4-1 arrive once each, and the second read re-sends nothing.
  for (const scene of [4, 3, 2, 1]) {
    await scrollToBeat(page, SCENE_AT[scene - 1]);
    await settle(page);
    assert.equal(await beat(), scene - 1);
    await page.waitForTimeout(900);
  }
  assert.deepEqual(await sceneIndexes(page), [5, 4, 3, 2, 1]);
  for (const scene of [1, 2, 3, 4, 5]) {
    await scrollToBeat(page, SCENE_AT[scene - 1]);
    await settle(page);
    await page.waitForTimeout(900);
  }
  assert.deepEqual(await sceneIndexes(page), [5, 4, 3, 2, 1]);
  const views = await storyEvents(page, "how_story_view");
  assert.equal(views.length, 1);
  assert.equal(views[0][2].story_mode, "scroll");
  assert.equal((await storyExperienceEvents(page)).length, 0);
  assert.equal(JSON.stringify(await dataLayerEvents(page)).includes("/assets/"), false);

  // Caption 3's 700 ms scene clock starts with the 1 s view clock and would end first: the view is flushed ahead of it.
  await reopen();
  await ready();
  await page.evaluate(() => document.fonts.ready);
  await scrollToBeat(page, SCENE_AT[2]);
  await settle(page);
  await page.waitForFunction((selector) => document.querySelector(selector).dataset.beat === "2", TRACK, { timeout: 3000 });
  await waitForStoryEvent(page, "how_story_scene");
  await page.waitForTimeout(600);
  assert.deepEqual(await storyOrder(page), ["view:scroll", "scene:3"]);
}
