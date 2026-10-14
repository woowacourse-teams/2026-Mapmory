// Timing rules for the #how photo story's two passive reach events (how_story_view, how_story_scene).
// Pure, with injectable timers, so tests drive a fake clock. The section decides what "eligible" and "at rest"
// mean and feeds them in; these trackers only own the clocks and the once-per-page rules.
export const HOW_STORY_VIEW_MS = 1000;
export const HOW_STORY_SCENE_DWELL_MS = 700;

const defaultSchedule = (callback, delay) => setTimeout(callback, delay);
const defaultCancel = (id) => clearTimeout(id);

// Fires onView once, after the story has been continuously eligible (in view, tab visible) for 1 s.
// flush() sends it at once if it has not gone yet: a scene reach calls it so the view always comes first.
export function createPassiveViewTracker({ onView, delayMs = HOW_STORY_VIEW_MS, schedule = defaultSchedule, cancel = defaultCancel }) {
  let timer = null;
  let done = false;
  const clear = () => {
    if (timer === null) return;
    cancel(timer);
    timer = null;
  };
  const fire = () => {
    clear();
    done = true;
    onView();
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
        fire();
      }, delayMs);
    },
    flush() {
      if (!done) fire();
    },
    stop: clear,
    get fired() { return done; },
  };
}

// Fires onReach(sceneIndex) at most once per scene, after that scene has stayed at rest on screen for 700 ms.
// sceneIndex is the beat number, 1-5, passed through unchanged; 0 or null (the title, or no beat) is never a scene.
// Any change of scene, rest or eligibility cancels the pending timer, so a fling past a scene never counts.
// When the dwell timer fires, the scene still has to be confirmed: confirm(check) re-reads where the reader is on a
// fresh frame (calling update() if anything changed) and then calls check(), which counts the scene only if nothing
// did. A timer held up by a long main-thread task fires before the page has caught up with a fling that already left
// the scene; without the confirm that fling would count. The default confirm checks at once (tests, no DOM).
export function createSceneReachTracker({ onReach, dwellMs = HOW_STORY_SCENE_DWELL_MS, schedule = defaultSchedule, cancel = defaultCancel, confirm = (check) => check() }) {
  const reached = new Set();
  let timer = null;
  let pendingScene = null;
  let generation = 0;
  const clear = () => {
    if (timer !== null) cancel(timer);
    timer = null;
    pendingScene = null;
    generation += 1;
  };
  return {
    update({ sceneIndex, atRest, eligible }) {
      const isScene = Number.isInteger(sceneIndex) && sceneIndex >= 1;
      const candidate = isScene && atRest && eligible && !reached.has(sceneIndex) ? sceneIndex : null;
      if (candidate === pendingScene) return;
      clear();
      if (candidate === null) return;
      pendingScene = candidate;
      const armed = generation;
      timer = schedule(() => {
        timer = null;
        confirm(() => {
          // anything that changed since the timer was armed (a fling, the tab hidden, stop()) cleared it
          if (armed !== generation || pendingScene !== candidate) return;
          pendingScene = null;
          reached.add(candidate);
          onReach(candidate);
        });
      }, dwellMs);
    },
    stop: clear,
    get reached() { return new Set(reached); },
  };
}
