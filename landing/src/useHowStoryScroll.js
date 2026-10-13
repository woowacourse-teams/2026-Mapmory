import { useLayoutEffect, useRef } from "react";
import {
  HOW_STORY_BEATS,
  HOW_STORY_WINDOWS,
  createHowStoryJumpTracker,
  getHowStoryFrame,
  getHowStoryNodeStyle,
  getHowStoryPlan,
  getHowStoryProgress,
  getHowStoryScene,
  isHowStoryHold,
} from "./howStory.js";
import { createHowStoryLayout, getHowStoryMediaMode, getHowStoryMode } from "./howStoryLayout.js";
import { latestEntry } from "./howStoryAnalytics.js";
import { loadHowMapData } from "./how/mapData.js";

// Below this the frame is the same to a hundredth of a pixel, so the frame is not recomputed.
const P_EPSILON = 0.00001;
const ATTACH_MARGIN = "100% 0px";
// The phone and the first copy enter once, when the frame's top first comes this far up the viewport: by then the
// section heading has moved up toward the header and has had its own screen.
const ENTER_AT = 0.4;

// Drives the #how stage from scroll progress. Reads one rect per animation frame, writes only changed
// transform/opacity/text values and data-* state straight to the DOM, and never touches wheel, touch or key input.
export function useHowStoryScroll({ enabled, trackRef, frameRef, stageRef, copyRef, onSceneChange, onFrameState, onFitFail }) {
  const callbackRef = useRef(null);
  callbackRef.current = { onSceneChange, onFrameState, onFitFail };

  useLayoutEffect(() => {
    if (!enabled) return undefined;
    const track = trackRef.current;
    const frame = frameRef.current;
    const stage = stageRef.current;
    const copy = copyRef.current;
    if (!track || !frame || !stage || !copy) return undefined;

    // The stage holds almost everything; the 예시 화면 pill sits on the frame, beside the copy or the phone.
    const nodeElements = new Map([...frame.querySelectorAll("[data-node]")].map((element) => [element.dataset.node, element]));
    const textElements = new Map([...stage.querySelectorAll("[data-text]")].map((element) => [element.dataset.text, element]));
    const attrElements = new Map([...stage.querySelectorAll("[data-attr]")].map((element) => [element.dataset.attr, element]));
    const tapElements = new Map([...stage.querySelectorAll("[data-tap]")].map((element) => [element.dataset.tap, element]));
    // What plays in time as a beat opens (data-enter names the beat): beat 05's print stack leaving and the map's
    // wait for it. Each sits with the beat's start p, so a jump is handled like a tap.
    const enterElements = [...stage.querySelectorAll("[data-enter]")].map((element) => {
      const index = HOW_STORY_BEATS.findIndex(({ id }) => id === element.dataset.enter);
      return { element, p: HOW_STORY_WINDOWS[index]?.[0] ?? Infinity };
    });
    const cellElements = [...stage.querySelectorAll("[data-cell]")];
    const written = new Map();
    const plan = getHowStoryPlan();
    const moments = [
      ...plan.beats.map((beat) => ({ element: tapElements.get(beat.id), p: beat.p, tap: true })),
      ...enterElements,
    ].filter(({ element }) => element);
    const jumped = createHowStoryJumpTracker();

    let layout = null;
    let stickyTop = 0;
    let frameHeight = 1;
    let lastP = -1;
    let lastScene = -1;
    let rafId = 0;
    let listening = false;
    let entered = false;
    let disposed = false;

    const write = (element, property, value) => {
      if (!element || value === undefined) return;
      let previous = written.get(element);
      if (!previous) {
        previous = {};
        written.set(element, previous);
      }
      if (previous[property] === value) return;
      previous[property] = value;
      if (property === "text") {
        // Rewrite the existing text node in place rather than replacing it, which is cheaper on every frame.
        if (element.childNodes.length === 1 && element.firstChild.nodeType === Node.TEXT_NODE) element.firstChild.data = value;
        else element.textContent = value;
      } else if (property.startsWith("attr:")) element.setAttribute(property.slice(5), value);
      else if (property.startsWith("data:")) element.dataset[property.slice(5)] = value;
      else element.style[property] = value;
    };

    // The copy slot holds the tallest beat; past its cap (large text) the story falls back to the static list.
    // Fallback fonts are wider than LINE Seed, so the check waits for the web fonts (fonts.ready remeasures).
    // It compares layout heights, not scrollHeight: the copy swap animates its lines in from 20px below, and a
    // measure during that (a reload straight into the story) would count the transform as overflow.
    const fontsLoaded = () => !document.fonts || document.fonts.status === "loaded";
    const copyBeats = [...copy.querySelectorAll(".how-copy-beat")];
    const copyOverflows = () => {
      if (!fontsLoaded()) return false;
      const padding = parseFloat(getComputedStyle(copy).paddingTop) || 0;
      return padding + Math.max(0, ...copyBeats.map((beat) => beat.offsetHeight)) > copy.clientHeight + 1;
    };

    const measure = () => {
      const frameStyle = getComputedStyle(frame);
      stickyTop = parseFloat(frameStyle.top) || 0;
      const viewportWidth = document.documentElement.clientWidth || window.innerWidth;
      const mode = typeof window.matchMedia === "function" ? getHowStoryMediaMode((query) => window.matchMedia(query)) : getHowStoryMode(viewportWidth);
      const next = createHowStoryLayout({ mode, stageWidth: stage.clientWidth, stageHeight: stage.clientHeight, viewportWidth });
      if (!next || copyOverflows()) {
        callbackRef.current.onFitFail();
        return false;
      }
      layout = next;
      // On the frame, so the pill (a sibling of the stage) can place itself beside the phone on desktop.
      const set = (name, value) => frame.style.setProperty(name, value);
      set("--phone-w", `${layout.phone.w}px`);
      set("--phone-x", `${layout.phone.x}px`);
      set("--phone-y", `${layout.phone.y}px`);
      set("--tile", `${layout.tile}px`);
      set("--win-y", `${layout.window.y}px`);
      set("--win-h", `${layout.window.h}px`);
      set("--win-radius", `${(layout.screenRadius * layout.kIn).toFixed(1)}px`);
      set("--stage-radius", `${layout.stageRadius}px`);
      set("--node-base", `${layout.nodeBase}px`);
      set("--match-k", String(layout.matchK));
      set("--overlay-top", `${layout.overlayTop}px`);
      set("--stack-rise", `${layout.stackRise}px`);
      cellElements.forEach((element, index) => {
        const cell = layout.cells[index];
        if (!cell) return;
        element.style.left = `${cell.x}px`;
        element.style.top = `${cell.y}px`;
      });
      frameHeight = frame.offsetHeight;
      return true;
    };

    const apply = (storyFrame) => {
      for (const [key, node] of Object.entries(storyFrame.nodes)) {
        const element = nodeElements.get(key);
        if (!element) continue;
        const nodeStyle = getHowStoryNodeStyle(node);
        write(element, "transform", nodeStyle.transform);
        write(element, "opacity", nodeStyle.opacity);
      }
      // One text node per value, rewritten only when it changes (101 values per counter at most).
      for (const [key, value] of Object.entries(storyFrame.texts)) write(textElements.get(key), "text", value);
      for (const [key, attrs] of Object.entries(storyFrame.attrs)) {
        for (const [name, value] of Object.entries(attrs)) write(attrElements.get(key), `attr:${name}`, value);
      }
      for (const [key, value] of Object.entries(storyFrame.state)) write(stage, `data:${key}`, String(value));
    };

    const update = (force = false) => {
      rafId = 0;
      if (disposed || !layout) return;
      const trackRect = track.getBoundingClientRect();
      const frameRect = frame.getBoundingClientRect();
      const p = getHowStoryProgress({ trackTop: trackRect.top, trackHeight: trackRect.height, frameHeight, stickyTop });
      const viewportHeight = window.innerHeight;
      if (!entered && frameRect.top < viewportHeight * ENTER_AT) {
        entered = true;
        track.classList.add("is-entered");
      }
      const visibleHeight = Math.min(frameRect.bottom, viewportHeight) - Math.max(frameRect.top, 0);
      const eligible = visibleHeight / Math.max(1, frameRect.height) >= 0.5 && document.visibilityState !== "hidden";
      // Every update feeds the jump tracker, so a fast move is seen as one however its frames fall.
      const fast = jumped(performance.now(), window.scrollY);
      if (force || Math.abs(p - lastP) >= P_EPSILON) {
        // A jump (a fast flick, a spun wheel or a key press, or the first frame after a reload into the story; see
        // HOW_STORY_JUMP) shows the end state without a burst of rings: each tap it turns on is marked skipped, and
        // stays so until the reader scrolls back above it. A tap that was already on is left alone, so its finished
        // ring never replays. A beat's opening moment (data-enter) is skipped the same way: a jump into beat 05 shows
        // the prints already gone and the map tab without its wait for them. Taps that one ordinary update turns on
        // together (one wheel notch crosses two or three) play one after another, in the order crossed (data-order).
        const jump = lastP < 0 || fast;
        let order = 0;
        for (const moment of moments) {
          if (p < moment.p) {
            write(moment.element, "data:skip", "0");
            if (moment.tap) write(moment.element, "data:order", "0");
          } else if (!(lastP >= moment.p)) {
            if (jump) write(moment.element, "data:skip", "1");
            else if (moment.tap) write(moment.element, "data:order", String(order++));
          }
        }
        lastP = p;
        apply(getHowStoryFrame(p, layout));
      }
      const { index } = getHowStoryScene(lastP);
      if (index !== lastScene) {
        lastScene = index;
        callbackRef.current.onSceneChange(index);
      }
      callbackRef.current.onFrameState({ sceneIndex: index, inHold: isHowStoryHold(lastP), eligible });
    };

    const schedule = () => {
      if (!rafId) rafId = window.requestAnimationFrame(() => update(false));
    };
    const remeasure = () => {
      if (disposed || !measure()) return;
      update(true);
    };
    const handleVisibility = () => update(true);

    const attach = () => {
      if (listening) return;
      listening = true;
      track.classList.add("is-live", "has-library");
      // The map's data (beat 05) loads with the library sprite, as the track comes near; once, cached.
      loadHowMapData().catch(() => {});
      window.addEventListener("scroll", schedule, { passive: true });
      window.addEventListener("resize", schedule, { passive: true });
      update(true);
    };
    const detach = () => {
      if (!listening) return;
      listening = false;
      track.classList.remove("is-live");
      window.removeEventListener("scroll", schedule);
      window.removeEventListener("resize", schedule);
      if (rafId) window.cancelAnimationFrame(rafId);
      rafId = 0;
      callbackRef.current.onFrameState({ sceneIndex: lastScene, inHold: false, eligible: false });
    };

    if (!measure()) return undefined;
    update(true);

    // Use the newest of any queued crossings, or a stale one could leave the listener on far away (or off nearby).
    const intersection = new IntersectionObserver((entries) => {
      if (latestEntry(entries).isIntersecting) attach();
      else detach();
    }, { rootMargin: ATTACH_MARGIN });
    intersection.observe(track);

    const resize = typeof ResizeObserver === "undefined" ? null : new ResizeObserver(remeasure);
    resize?.observe(frame);
    resize?.observe(stage);
    // Copy changes size with text zoom even when the frame does not; the fit check must see that.
    for (const line of copy.querySelectorAll("h3, .how-copy-sub")) resize?.observe(line);
    document.fonts?.ready.then(remeasure).catch(() => {});
    document.addEventListener("visibilitychange", handleVisibility);

    return () => {
      disposed = true;
      detach();
      callbackRef.current.onFrameState({ sceneIndex: lastScene, inHold: false, eligible: false });
      intersection.disconnect();
      resize?.disconnect();
      document.removeEventListener("visibilitychange", handleVisibility);
    };
  }, [enabled, trackRef, frameRef, stageRef, copyRef, callbackRef]);
}
