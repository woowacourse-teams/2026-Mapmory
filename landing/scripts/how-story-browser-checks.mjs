// Browser checks for the #how stories' two reach events (#roll then #rec, six beats), shared by verify-analytics-browser.mjs.
// They read the stories only through the controller's QA hooks (listed in src/photoStoryController.js), not styling:
//   section#how [data-mode]                "scroll" | "static", the mode the stories run in now (= how_story_view.story_mode)
//   section#how [data-scene]               the beat on screen, 1-6 (= scene_index), 0 before the stories
//   [data-story-track][data-beat]          each track's beat, 0-2
//   [data-story-step]                      the six beats' words, shown as a list in the static version
import assert from "node:assert/strict";
import { SCENE_AT, STORY_TRACKS } from "../src/photoStory.js";

const MODE = "section#how [data-mode]";
const TRACKS = STORY_TRACKS.map(({ key }) => `section#how [data-story-track="${key}"]`);

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
// scrolls so scene n (1-6) sits at its comfortable point inside its track
const scrollToScene = (page, n) => page.evaluate(([selectors, [trackIndex, at]]) => {
  const track = document.querySelector(selectors[trackIndex]);
  const stage = track.querySelector("[data-story-stage]");
  const top = window.scrollY + track.getBoundingClientRect().top;
  window.scrollTo({ top: top + (track.offsetHeight - stage.offsetHeight) * at, behavior: "instant" });
}, [TRACKS, SCENE_AT[n - 1]]);
const sceneOnScreen = (page) => page.evaluate((selector) => Number(document.querySelector(selector).dataset.scene ?? -1), MODE);

// Static version (reduced motion, a landscape phone): one passive view (static), and a scene once its beat has stayed
// at least half in view, nearest the middle of the screen, for 700 ms. A beat jumped over never counts; nothing twice.
export async function checkStaticStory(page) {
  assert.equal(await storyMode(page), "static");
  const items = page.locator("section#how [data-story-step]");
  assert.equal(await items.count(), 6);
  const center = (scene) => items.nth(scene - 1).evaluate((node) => node.scrollIntoView({ block: "center", behavior: "instant" }));
  await page.locator("section#how").evaluate((node) => window.scrollTo({ top: window.scrollY + node.getBoundingClientRect().top, behavior: "instant" }));
  await waitForStoryEvent(page, "how_story_view");
  // the beat nearest the middle at the section's start may count; then jump to 6
  await page.waitForTimeout(900);
  const before = (await sceneIndexes(page)).length;
  await center(6);
  await page.waitForFunction(() => (window.dataLayer ?? []).some((args) => args[0] === "event" && args[1] === "how_story_scene" && args[2]?.scene_index === 6));
  await page.waitForTimeout(300);
  assert.deepEqual((await sceneIndexes(page)).slice(before), [6], "the jump to beat 6 counts only beat 6");
  for (const scene of [1, 2, 3, 4, 5]) {
    await center(scene);
    // 700 ms at rest, then a fresh frame (the globe below may be starting up on a slow software renderer)
    await page.waitForFunction((n) => (window.dataLayer ?? []).some((args) => args[0] === "event" && args[1] === "how_story_scene" && args[2]?.scene_index === n), scene, { timeout: 5000 });
  }
  assert.deepEqual([...(await sceneIndexes(page))].sort(), [1, 2, 3, 4, 5, 6]);
  for (const scene of [6, 5, 4, 3, 2, 1]) {
    await center(scene);
    await page.waitForTimeout(900);
  }
  assert.deepEqual([...(await sceneIndexes(page))].sort(), [1, 2, 3, 4, 5, 6]);
  const views = await storyEvents(page, "how_story_view");
  assert.equal(views.length, 1);
  assert.equal(views[0][2].story_mode, "static");
  assert.equal((await storyOrder(page))[0], "view:static");
  assert.equal((await storyExperienceEvents(page)).length, 0);
  assert.equal(JSON.stringify(await dataLayerEvents(page)).includes("/assets/"), false);
}

// Scroll mode, on a freshly loaded page: a fling from beat 1 (armed, not yet counted) past beats 2-5, with a 900 ms
// main-thread stall as it leaves beat 1, counts none of them and beat 6 at rest sends the view then scene 6; stopping on every beat on the way back
// sends 5-1 once each; a second read re-sends nothing. `reopen()` loads the page again; landing straight on beat 3
// (a restored scroll or a deep link) must still send the view before the scene.
export async function checkScrollStory(page, { reopen }) {
  const ready = async () => {
    assert.equal(await storyMode(page), "scroll");
    await page.waitForFunction((selector) => document.querySelector(selector)?.dataset.beat !== undefined, TRACKS[0]);
  };

  await ready();
  // Start pinned on beat 1 for less than its 700 ms, so its dwell timer is armed when the fling leaves.
  await scrollToScene(page, 1);
  await settle(page);
  await page.waitForTimeout(250);
  // The fling runs inside the page as a momentum coast on a fixed curve in time, so a slow machine skips frames
  // instead of lingering on a beat. While beat 1 is pinned the main thread stalls for 900 ms: its dwell timer then
  // fires late, after the fling has left it, and must not count.
  const fling = await page.evaluate(([selectors, [trackIndex, target]]) => new Promise((resolve) => {
    const first = document.querySelector(selectors[0]);
    const track = document.querySelector(selectors[trackIndex]);
    const stage = track.querySelector("[data-story-stage]");
    const s0 = window.scrollY, s1 = window.scrollY + track.getBoundingClientRect().top + (track.offsetHeight - stage.offsetHeight) * target;
    const tau = 350;
    let stalledAt = null;
    requestAnimationFrame(() => {
      const start = performance.now();
      const step = () => {
        const t = performance.now() - start;
        if (stalledAt === null && first.dataset.beat === "0" && first.getBoundingClientRect().top <= 1) {
          stalledAt = "1";
          setTimeout(() => { const end = performance.now() + 900; while (performance.now() < end); }, 50);
        }
        window.scrollTo({ top: t < tau * 7 ? s0 + (s1 - s0) * (1 - Math.exp(-t / tau)) : s1, behavior: "instant" });
        if (t < tau * 7) requestAnimationFrame(step);
        else resolve({ stalledAt });
      };
      step();
    });
  }), [TRACKS, SCENE_AT[5]]);
  assert.equal(fling.stalledAt, "1", JSON.stringify(fling));
  await settle(page);
  await page.waitForFunction((selector) => document.querySelector(selector).dataset.scene === "6", MODE);
  await page.waitForTimeout(900);
  assert.deepEqual(await storyOrder(page), ["view:scroll", "scene:6"]);
  // Back up and down again, resting on every beat: 5-1 arrive once each, and the second read re-sends nothing.
  for (const scene of [5, 4, 3, 2, 1]) {
    await scrollToScene(page, scene);
    await settle(page);
    await page.waitForFunction(([selector, n]) => document.querySelector(selector).dataset.scene === String(n), [MODE, scene]);
    assert.equal(await sceneOnScreen(page), scene);
    await page.waitForTimeout(900);
  }
  assert.deepEqual(await sceneIndexes(page), [6, 5, 4, 3, 2, 1]);
  for (const scene of [1, 2, 3, 4, 5, 6]) {
    await scrollToScene(page, scene);
    await settle(page);
    await page.waitForTimeout(900);
  }
  assert.deepEqual(await sceneIndexes(page), [6, 5, 4, 3, 2, 1]);
  const views = await storyEvents(page, "how_story_view");
  assert.equal(views.length, 1);
  assert.equal(views[0][2].story_mode, "scroll");
  assert.equal((await storyExperienceEvents(page)).length, 0);
  assert.equal(JSON.stringify(await dataLayerEvents(page)).includes("/assets/"), false);

  // Beat 3's 700 ms scene clock starts with the 1 s view clock and would end first: the view is flushed ahead of it.
  await reopen();
  await ready();
  await page.evaluate(() => document.fonts.ready);
  await scrollToScene(page, 3);
  await settle(page);
  await page.waitForFunction((selector) => document.querySelector(selector).dataset.scene === "3", MODE, { timeout: 3000 });
  await waitForStoryEvent(page, "how_story_scene");
  await page.waitForTimeout(600);
  assert.deepEqual(await storyOrder(page), ["view:scroll", "scene:3"]);
}
