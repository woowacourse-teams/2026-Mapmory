// Timing rules for the #how story's two passive events. Pure, with injectable timers, so tests drive a fake clock.
export const HOW_STORY_VIEW_MS = 1000;
export const HOW_STORY_SCENE_DWELL_MS = 700;

const defaultSchedule = (callback, delay) => setTimeout(callback, delay);
const defaultCancel = (id) => clearTimeout(id);

// Fires onView once, after the story has been continuously eligible (in view, tab visible) for 1 s.
export function createPassiveViewTracker({ onView, delayMs = HOW_STORY_VIEW_MS, schedule = defaultSchedule, cancel = defaultCancel }) {
  let timer = null;
  let done = false;
  const clear = () => {
    if (timer === null) return;
    cancel(timer);
    timer = null;
  };
  return {
    update(eligible) {
      if (done) return;
      if (!eligible) {
        clear();
        return;
      }
      if (timer !== null) return;
      timer = schedule(() => {
        timer = null;
        done = true;
        onView();
      }, delayMs);
    },
    stop: clear,
    get fired() { return done; },
  };
}

// Fires onReach(sceneIndex) at most once per scene, after that scene's hold has stayed on screen for 700 ms.
// Any change of scene, hold or eligibility cancels the pending timer, so a fling past a scene never counts.
export function createSceneReachTracker({ onReach, dwellMs = HOW_STORY_SCENE_DWELL_MS, schedule = defaultSchedule, cancel = defaultCancel }) {
  const reached = new Set();
  let timer = null;
  let pendingScene = null;
  const clear = () => {
    if (timer !== null) cancel(timer);
    timer = null;
    pendingScene = null;
  };
  return {
    update({ sceneIndex, inHold, eligible }) {
      const candidate = inHold && eligible && !reached.has(sceneIndex) ? sceneIndex : null;
      if (candidate === pendingScene) return;
      clear();
      if (candidate === null) return;
      pendingScene = candidate;
      timer = schedule(() => {
        timer = null;
        pendingScene = null;
        reached.add(candidate);
        onReach(candidate);
      }, dwellMs);
    },
    stop: clear,
    get reached() { return new Set(reached); },
  };
}
