// Browser checks for the #how photo story's two reach events, shared by verify-analytics-browser.mjs.
// They read the section only through these hooks (its QA contract, not styling):
//   section#how [data-how-mode]       "scroll" | "static", the mode the story runs in now (= how_story_view.story_mode);
//                                     on section#how itself or on the story root inside it
//   [data-how-track]                  the pinned film's tall track (scroll mode), carrying
//     data-how-beat="0-5"             the beat the film shows (0 = the title)
//     data-how-step="0|1"             that beat's step (01: copy and tag; 02, 04: the timed inner step)
//   [data-how-frame]                  the sticky frame inside the track
//   [data-how-scene="1".."5"]         the five items of the static version (static mode)
import assert from "node:assert/strict";

// The step each beat's point lands on (01: copy and 경주 tag; 02: the 경주 photos out of the heap; 04: the copies up).
const FINAL_STEP = [null, 1, 1, 0, 1, 0];
// Beats whose final step comes on a timer after the beat starts, not with the scroll.
const TIMED_STEP = new Set([2, 4]);
const MODE = "section#how[data-how-mode], section#how [data-how-mode]";
const TRACK = "section#how [data-how-track]";

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
  return page.evaluate((selector) => document.querySelector(selector).dataset.howMode, MODE);
};
export const settle = (page) => page.evaluate(() => new Promise((resolve) => {
  let last = -1;
  const check = () => (window.scrollY === last ? resolve(window.scrollY) : (last = window.scrollY, requestAnimationFrame(() => requestAnimationFrame(check))));
  check();
}));
const waitForStoryEvent = (page, name) => page.waitForFunction((eventName) => (window.dataLayer ?? [])
  .some((args) => args[0] === "event" && args[1] === eventName && args[2]?.experience_type === "how_story"), name);

// Static version (reduced motion, a landscape phone, big text): one passive view (static), and a scene once its item
// has stayed at least half in view for 700 ms. An item jumped over never counts, and nothing is sent twice.
export async function checkStaticStory(page) {
  assert.equal(await storyMode(page), "static");
  const items = page.locator("section#how [data-how-scene]");
  assert.deepEqual(await items.evaluateAll((nodes) => nodes.map((node) => node.dataset.howScene)), ["1", "2", "3", "4", "5"]);
  const center = (scene) => page.locator(`section#how [data-how-scene="${scene}"]`).evaluate((node) => node.scrollIntoView({ block: "center", behavior: "instant" }));
  await page.locator("section#how").evaluate((node) => window.scrollTo({ top: window.scrollY + node.getBoundingClientRect().top, behavior: "instant" }));
  await waitForStoryEvent(page, "how_story_view");
  await center(5);
  await page.waitForFunction(() => (window.dataLayer ?? []).some((args) => args[0] === "event" && args[1] === "how_story_scene" && args[2]?.scene_index === 5));
  await page.waitForTimeout(300);
  // Item 1 may have rested under the section heading before the jump; 2-4 were never on screen.
  assert.deepEqual((await sceneIndexes(page)).filter((index) => index !== 1), [5], "items 2-4 were jumped over");
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

// Scroll mode, on a freshly loaded page: a fling past 01-04 counts none of them and 05 at rest sends the view then
// scene 5; stopping on every point sends 04-01 once each; a second read re-sends nothing. `reopen()` loads the page
// again; landing straight on 03 (a restored scroll or a deep link) must still send the view before the scene.
export async function checkScrollStory(page, { mobile, reopen }) {
  const state = () => page.evaluate((selector) => {
    const track = document.querySelector(selector);
    const frame = track.querySelector("[data-how-frame]");
    const rect = track.getBoundingClientRect();
    return {
      beat: Number(track.dataset.howBeat ?? -1),
      step: Number(track.dataset.howStep ?? -1),
      top: rect.top,
      bottom: rect.bottom,
      pinned: rect.top <= 1 && rect.bottom >= frame.getBoundingClientRect().height - 1,
      y: window.scrollY,
      vh: window.innerHeight,
    };
  }, TRACK);
  const ready = async () => {
    assert.equal(await storyMode(page), "scroll");
    await page.waitForFunction((selector) => document.querySelector(selector)?.dataset.howBeat !== undefined, TRACK);
  };
  const scrollStep = (dy) => (mobile ? page.evaluate((delta) => window.scrollBy(0, delta), dy) : page.mouse.wheel(0, dy));
  const atPoint = (st) => st.beat >= 1 && st.pinned && st.step === FINAL_STEP[st.beat];
  // How far into the track (px) each beat's point was first seen on the latest pass.
  const pointOffset = new Map();
  // One 120 px step at a time, like a wheel notch or a short swipe; on each beat's point, stops 900 ms once.
  const pass = async (direction, paused = new Set()) => {
    for (let step = 0; step < 400; step += 1) {
      await scrollStep(120 * direction);
      await settle(page);
      await page.waitForTimeout(60);
      let st = await state();
      if (st.beat >= 1 && st.pinned && !paused.has(st.beat)) {
        if (TIMED_STEP.has(st.beat) && st.step !== FINAL_STEP[st.beat]) {
          await page.waitForFunction(([selector, beat, k]) => {
            const track = document.querySelector(selector);
            return Number(track.dataset.howBeat) !== beat || Number(track.dataset.howStep) === k;
          }, [TRACK, st.beat, FINAL_STEP[st.beat]], { timeout: 3000 });
          st = await state();
        }
        if (atPoint(st)) {
          paused.add(st.beat);
          pointOffset.set(st.beat, -st.top);
          await page.waitForTimeout(900);
        }
      }
      if (direction > 0 && st.bottom < 0) return [...paused];
      if (direction < 0 && (st.top > st.vh || st.y === 0)) return [...paused];
    }
    throw new Error(`a ${direction > 0 ? "downward" : "upward"} pass never left the track`);
  };

  await ready();
  await page.mouse.move(200, 300);
  // Start with the track's top half way down the screen, then fling: 120 px a frame until 05 is pinned
  // (scrollBy on both, so no smooth-scroll animation carries the page past 05 after the last step).
  await page.evaluate((selector) => {
    const track = document.querySelector(selector);
    window.scrollTo({ top: window.scrollY + track.getBoundingClientRect().top - window.innerHeight / 2, behavior: "instant" });
  }, TRACK);
  await settle(page);
  let flung = null;
  for (let step = 0; step < 400 && !flung; step += 1) {
    await page.evaluate(() => window.scrollBy(0, 120));
    await page.waitForTimeout(16);
    const st = await state();
    if (st.beat === 5 && st.pinned) flung = st;
  }
  assert.ok(flung, "the fling reached beat 05");
  await settle(page);
  const landed = await state();
  assert.ok(landed.beat === 5 && landed.pinned, JSON.stringify(landed));
  await page.waitForTimeout(900);
  assert.deepEqual(await storyOrder(page), ["view:scroll", "scene:5"]);
  // Back up and down again, stopping on every point: 04-01 arrive once each, and the second read re-sends nothing.
  assert.deepEqual((await pass(-1, new Set([5]))).sort(), [1, 2, 3, 4, 5]);
  assert.deepEqual(await sceneIndexes(page), [5, 4, 3, 2, 1]);
  assert.deepEqual((await pass(1)).sort(), [1, 2, 3, 4, 5]);
  assert.deepEqual(await sceneIndexes(page), [5, 4, 3, 2, 1]);
  const views = await storyEvents(page, "how_story_view");
  assert.equal(views.length, 1);
  assert.equal(views[0][2].story_mode, "scroll");
  assert.equal((await storyExperienceEvents(page)).length, 0);
  assert.equal(JSON.stringify(await dataLayerEvents(page)).includes("/assets/"), false);

  // 03 has no timed step, so its 700 ms scene clock starts with the 1 s view clock and would end first: the view
  // has to be flushed ahead of it. The jump lands half way between where 03's and 04's points were first seen.
  assert.ok(pointOffset.has(3) && pointOffset.has(4), "03's and 04's points were measured");
  const offset = (pointOffset.get(3) + pointOffset.get(4)) / 2;
  await reopen();
  await ready();
  await page.evaluate(() => document.fonts.ready);
  await page.evaluate(([selector, px]) => {
    const track = document.querySelector(selector);
    window.scrollTo({ top: window.scrollY + track.getBoundingClientRect().top + px, behavior: "instant" });
  }, [TRACK, offset]);
  await settle(page);
  await page.waitForFunction((selector) => {
    const track = document.querySelector(selector);
    return track.dataset.howBeat === "3";
  }, TRACK, { timeout: 3000 });
  await waitForStoryEvent(page, "how_story_scene");
  await page.waitForTimeout(600);
  assert.deepEqual(await storyOrder(page), ["view:scroll", "scene:3"]);
}
