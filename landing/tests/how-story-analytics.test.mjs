import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import {
  HOW_STORY_SCENE_DWELL_MS,
  HOW_STORY_VIEW_MS,
  createPassiveViewTracker,
  createSceneReachTracker,
} from "../src/howStoryAnalytics.js";

const analyticsSource = await readFile(new URL("../src/analytics.js", import.meta.url), "utf8");

// A manual clock: timers run in due order, and `now` is the due time while a callback runs.
function fakeClock() {
  const timers = new Map();
  let now = 0;
  let nextId = 1;
  return {
    schedule: (callback, delay) => {
      const id = nextId++;
      timers.set(id, { callback, at: now + delay });
      return id;
    },
    cancel: (id) => timers.delete(id),
    advance(ms) {
      const end = now + ms;
      for (;;) {
        const due = [...timers].filter(([, timer]) => timer.at <= end).sort((a, b) => a[1].at - b[1].at)[0];
        if (!due) break;
        timers.delete(due[0]);
        now = due[1].at;
        due[1].callback();
      }
      now = end;
    },
    get now() { return now; },
    get pending() { return timers.size; },
  };
}

test("the passive view waits 1 s and a scene rest waits 700 ms", () => {
  assert.equal(HOW_STORY_VIEW_MS, 1000);
  assert.equal(HOW_STORY_SCENE_DWELL_MS, 700);
});

test("the scene reach tracker needs 700 ms at rest and fires each scene once", () => {
  const clock = fakeClock();
  const reached = [];
  const tracker = createSceneReachTracker({ onReach: (index) => reached.push(index), schedule: clock.schedule, cancel: clock.cancel });
  tracker.update({ sceneIndex: 1, atRest: true, eligible: true });
  clock.advance(699);
  assert.deepEqual(reached, []);
  // The same state reported again (every scroll frame does) keeps the running timer.
  tracker.update({ sceneIndex: 1, atRest: true, eligible: true });
  clock.advance(1);
  assert.deepEqual(reached, [1]);
  // Coming back to a reached scene never re-sends it.
  tracker.update({ sceneIndex: 2, atRest: false, eligible: true });
  tracker.update({ sceneIndex: 1, atRest: true, eligible: true });
  clock.advance(2000);
  assert.deepEqual(reached, [1]);
  // A fling past scene 2, then a hidden tab during scene 3, never counts.
  tracker.update({ sceneIndex: 2, atRest: true, eligible: true });
  clock.advance(300);
  tracker.update({ sceneIndex: 3, atRest: false, eligible: true });
  clock.advance(1000);
  tracker.update({ sceneIndex: 3, atRest: true, eligible: true });
  clock.advance(300);
  tracker.update({ sceneIndex: 3, atRest: true, eligible: false });
  clock.advance(1000);
  assert.deepEqual(reached, [1]);
  tracker.update({ sceneIndex: 3, atRest: true, eligible: true });
  clock.advance(700);
  assert.deepEqual(reached, [1, 3]);
  assert.deepEqual([...tracker.reached], [1, 3]);
  // stop() drops a pending reach (the section unmounts or switches mode mid-dwell).
  tracker.update({ sceneIndex: 4, atRest: true, eligible: true });
  tracker.stop();
  clock.advance(1000);
  assert.deepEqual(reached, [1, 3]);
  assert.equal(clock.pending, 0);
});

test("beats 02 and 04 count only from their inner step: the dwell starts when the rest starts", () => {
  const clock = fakeClock();
  const reached = [];
  const tracker = createSceneReachTracker({ onReach: (index) => reached.push([index, clock.now]), schedule: clock.schedule, cancel: clock.cancel });
  // Beat 02 is current at t=0 but its inner step starts on a 300 ms timer: not at rest until then.
  tracker.update({ sceneIndex: 2, atRest: false, eligible: true });
  clock.advance(300);
  tracker.update({ sceneIndex: 2, atRest: true, eligible: true });
  clock.advance(699);
  assert.deepEqual(reached, []);
  clock.advance(1);
  assert.deepEqual(reached, [[2, 1000]]);
});

test("the title (beat 0) and no beat are never scenes", () => {
  const clock = fakeClock();
  const reached = [];
  const tracker = createSceneReachTracker({ onReach: (index) => reached.push(index), schedule: clock.schedule, cancel: clock.cancel });
  for (const sceneIndex of [0, null, undefined, -1, 1.5]) {
    tracker.update({ sceneIndex, atRest: true, eligible: true });
    clock.advance(2000);
  }
  assert.deepEqual(reached, []);
  assert.equal(clock.pending, 0);
});

test("the passive view tracker fires once after 1 s of continuous eligibility", () => {
  const clock = fakeClock();
  let views = 0;
  const tracker = createPassiveViewTracker({ onView: () => { views += 1; }, schedule: clock.schedule, cancel: clock.cancel });
  tracker.update(true);
  clock.advance(999);
  tracker.update(false);
  clock.advance(5000);
  assert.equal(views, 0);
  tracker.update(true);
  tracker.update(true);
  assert.equal(clock.pending, 1);
  clock.advance(1000);
  assert.equal(views, 1);
  assert.equal(tracker.fired, true);
  tracker.update(false);
  tracker.update(true);
  clock.advance(5000);
  tracker.flush();
  assert.equal(views, 1);
  assert.equal(clock.pending, 0);
});

test("how_story_view always reaches analytics before the first how_story_scene", () => {
  const clock = fakeClock();
  const sent = [];
  const view = createPassiveViewTracker({ onView: () => sent.push(["view", clock.now]), schedule: clock.schedule, cancel: clock.cancel });
  // The section wires it like this: a scene reach flushes the view first.
  const reach = createSceneReachTracker({
    onReach: (index) => { view.flush(); sent.push([`scene ${index}`, clock.now]); },
    schedule: clock.schedule,
    cancel: clock.cancel,
  });
  // A reload that lands straight on a beat at rest starts both clocks together; the scene's 700 ms ends first.
  view.update(true);
  clock.advance(200);
  reach.update({ sceneIndex: 1, atRest: true, eligible: true });
  clock.advance(700);
  assert.deepEqual(sent, [["view", 900], ["scene 1", 900]]);
  clock.advance(2000);
  view.update(true);
  view.flush();
  clock.advance(2000);
  assert.deepEqual(sent.map(([name]) => name), ["view", "scene 1"]);
  assert.equal(view.fired, true);
});

test("analytics accept only the two how_story events and keep scene_index to 1..5", () => {
  assert.match(analyticsSource, /HOW_STORY_VIEW: "how_story_view",\s*HOW_STORY_SCENE: "how_story_scene",/);
  assert.match(analyticsSource, /\["scroll", "static"\]\.includes\(parameters\.story_mode\)/);
  assert.match(analyticsSource, /parameters\.scene_index >= 1\s*&& parameters\.scene_index <= 5/);
  assert.doesNotMatch(analyticsSource, /how_play|HOW_PLAY|save_index|selected_photos/);
});
