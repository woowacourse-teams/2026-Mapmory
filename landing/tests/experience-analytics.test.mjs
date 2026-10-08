import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import vm from "node:vm";
import test from "node:test";
import { build } from "esbuild";

// Runs the real hook with a minimal React stand-in, a fake IntersectionObserver and a manual clock.
async function loadHook() {
  const stubs = {
    name: "hook-stubs",
    setup(buildApi) {
      buildApi.onResolve({ filter: /^react$/ }, () => ({ path: "react", namespace: "stub" }));
      buildApi.onResolve({ filter: /\/analytics\.js$/ }, () => ({ path: "analytics", namespace: "stub" }));
      buildApi.onLoad({ filter: /^react$/, namespace: "stub" }, () => ({
        contents: `export const useRef = (value) => ({ current: value });
          export const useCallback = (callback) => callback;
          export const useEffect = (effect) => { globalThis.__effects.push(effect); };`,
      }));
      buildApi.onLoad({ filter: /^analytics$/, namespace: "stub" }, () => ({
        contents: `export const ANALYTICS_EVENTS = { EXPERIENCE_VIEW: "experience_view", EXPERIENCE_START: "experience_start",
            EXPERIENCE_END: "experience_end", EXPERIENCE_CTA_CLICK: "experience_cta_click", MEMORY_OPEN: "memory_open" };
          export const trackEvent = (name, parameters) => { globalThis.__events.push([name, parameters]); return true; };`,
      }));
    },
  };
  const result = await build({
    entryPoints: [fileURLToPath(new URL("../src/useExperienceAnalytics.js", import.meta.url))],
    bundle: true, write: false, format: "iife", globalName: "hookModule", plugins: [stubs],
  });

  let now = 0;
  let nextTimer = 1;
  const timers = new Map();
  const listeners = {};
  let observerCallback = null;
  const context = vm.createContext({
    __effects: [], __events: [],
    performance: { now: () => now },
    document: { hidden: false, addEventListener() {}, removeEventListener() {} },
    IntersectionObserver: class {
      constructor(callback) { observerCallback = callback; }
      observe() {}
      disconnect() {}
    },
  });
  context.window = {
    innerHeight: 1000,
    setTimeout: (callback, delay) => { timers.set(nextTimer, { at: now + delay, callback }); return nextTimer++; },
    clearTimeout: (id) => timers.delete(id),
    addEventListener: (type, callback) => { listeners[type] = callback; },
    removeEventListener: (type) => { delete listeners[type]; },
  };
  vm.runInContext(result.outputFiles[0].text, context);

  const hook = context.hookModule.useExperienceAnalytics("how_play");
  hook.sectionRef.current = {};
  for (const effect of context.__effects) effect();

  const height = 600;
  return {
    hook,
    // Reports `visible` px of a 600 px box, the way IntersectionObserver does on a threshold crossing.
    show: (visible) => observerCallback([{
      isIntersecting: visible > 0,
      boundingClientRect: { height },
      intersectionRect: { height: visible },
    }]),
    advance: (milliseconds) => {
      now += milliseconds;
      for (const [id, timer] of [...timers].sort((a, b) => a[1].at - b[1].at)) {
        if (timer.at > now || !timers.has(id)) continue;
        timers.delete(id);
        timer.callback();
      }
    },
    pagehide: () => listeners.pagehide(),
    ends: () => context.__events.filter(([name]) => name === "experience_end").map(([, parameters]) => ({ ...parameters })),
  };
}

test("a tap below the view threshold survives partial scrolling and ends once the box leaves the screen", async () => {
  const harness = await loadHook();
  harness.show(240);
  harness.hook.startExperience("place_select", { fromSection: true });
  harness.show(120);
  harness.advance(5000);
  assert.deepEqual(harness.ends(), []);
  harness.show(280);
  harness.advance(5000);
  assert.deepEqual(harness.ends(), []);
  harness.show(0);
  harness.advance(1499);
  assert.deepEqual(harness.ends(), []);
  harness.advance(1);
  const [end] = harness.ends();
  assert.equal(end.exit_reason, "section_exit");
  assert.equal(end.last_completed_step, "experience_start");
  assert.equal(end.active_duration_seconds, 0);
  harness.pagehide();
  assert.equal(harness.ends().length, 1);
});

test("a tap below the view threshold still ends on pagehide", async () => {
  const harness = await loadHook();
  harness.show(240);
  harness.hook.startExperience("place_select", { fromSection: true });
  harness.pagehide();
  assert.deepEqual(harness.ends().map(({ exit_reason }) => exit_reason), ["page_hide"]);
});

test("coming back on screen within the grace period keeps a tap-only session open", async () => {
  const harness = await loadHook();
  harness.show(240);
  harness.hook.startExperience("place_select", { fromSection: true });
  harness.show(0);
  harness.advance(1000);
  harness.show(100);
  harness.advance(5000);
  assert.deepEqual(harness.ends(), []);
});

test("once the box reaches the threshold, a tap-only session follows the normal 50% exit rule", async () => {
  const harness = await loadHook();
  harness.show(240);
  harness.hook.startExperience("place_select", { fromSection: true });
  harness.show(400);
  harness.advance(2000);
  harness.show(200);
  harness.advance(1500);
  const [end] = harness.ends();
  assert.equal(end.exit_reason, "section_exit");
  assert.equal(end.active_duration_seconds, 2);
});

test("a start from outside the box below the threshold still needs real exposure before it can end", async () => {
  const harness = await loadHook();
  harness.show(240);
  harness.hook.startExperience("place_select");
  harness.show(0);
  harness.advance(5000);
  harness.pagehide();
  assert.deepEqual(harness.ends(), []);
});
