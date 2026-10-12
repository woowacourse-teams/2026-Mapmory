import { useLayoutEffect, useRef } from "react";
import {
  HOW_STORY_CANVAS,
  HOW_STORY_DEFAULT_STRIP_TARGET,
  getHowStoryFrame,
  getHowStoryNodeStyle,
  getHowStoryProgress,
  getHowStoryScene,
  getHowStorySegmentTransform,
  isHowStoryHold,
} from "./howStory.js";
import { latestEntry } from "./howStoryAnalytics.js";

const MAX_K = 1.5;
const MIN_K = 0.8;
// Below this the frame is the same to a hundredth of a pixel, so the frame is not recomputed.
const P_EPSILON = 0.00001;
const ATTACH_MARGIN = "100% 0px";

const floorK = (value) => Math.floor(value * 1000) / 1000;

// Drives the #how stage from scroll progress. Reads one rect per animation frame, writes only changed
// transform/opacity/text values straight to the DOM, and never touches wheel, touch or key input.
export function useHowStoryScroll({ enabled, trackRef, frameRef, stageWrapRef, stepsRef, onSceneChange, onFrameState, onFitFail }) {
  const callbacks = useRef({ onSceneChange, onFrameState, onFitFail });
  callbacks.current = { onSceneChange, onFrameState, onFitFail };

  useLayoutEffect(() => {
    if (!enabled) return undefined;
    const track = trackRef.current;
    const frame = frameRef.current;
    const wrap = stageWrapRef.current;
    const steps = stepsRef.current;
    const stage = wrap?.querySelector(".how-stage");
    if (!track || !frame || !wrap || !steps || !stage) return undefined;

    const zone = stage.querySelector(".how-zone");
    const box = stage.querySelector(".how-stage-box");
    const canvas = stage.querySelector(".how-stage-canvas");
    const counter = stage.querySelector("[data-strip-target]");
    const copy = frame.querySelector(".how-story-copy");
    const nodeElements = new Map([...stage.querySelectorAll("[data-node]")].map((element) => [element.dataset.node, element]));
    const textElements = new Map([...stage.querySelectorAll("[data-text]")].map((element) => [element.dataset.text, element]));
    const segmentElements = [...frame.querySelectorAll("[data-seg]")];
    const written = new Map();

    let stickyTop = 0;
    let frameHeight = 1;
    let stripTarget = HOW_STORY_DEFAULT_STRIP_TARGET;
    let lastP = -1;
    let lastScene = -1;
    let rafId = 0;
    let listening = false;
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
      if (property !== "text") element.style[property] = value;
      // Rewrite the existing text node in place rather than replacing it, which is cheaper on every frame.
      else if (element.childNodes.length === 1 && element.firstChild.nodeType === Node.TEXT_NODE) element.firstChild.data = value;
      else element.textContent = value;
    };

    // The caption slot grows with its longest caption up to a cap; past the cap (large text) the story falls back.
    const stepOverflows = () => steps.scrollHeight > steps.clientHeight + 1;

    // Sizes the canvas to the room the frame leaves for it; gives up (static list) when it would be too small.
    const measure = () => {
      const frameStyle = getComputedStyle(frame);
      stickyTop = parseFloat(frameStyle.top) || 0;
      const stacked = frameStyle.display !== "grid";
      // Stacked layouts size the frame to its content, so it sits right under the heading, and keep the
      // viewport room in max-height; the side-by-side desktop frame is the room itself.
      const maxRoom = parseFloat(frameStyle.maxHeight);
      const room = stacked && Number.isFinite(maxRoom) ? maxRoom : frame.clientHeight;
      const innerHeight = room - parseFloat(frameStyle.paddingTop) - parseFloat(frameStyle.paddingBottom);
      const rowGap = parseFloat(frameStyle.rowGap) || 0;
      const notesHeight = [...wrap.querySelectorAll(".how-story-note")].reduce((sum, note) => sum + note.offsetHeight, 0);
      const roomHeight = (stacked ? innerHeight - copy.offsetHeight - rowGap : innerHeight) - notesHeight;
      const chromeY = stage.offsetHeight - box.offsetHeight;
      const zoneStyle = getComputedStyle(zone);
      const chromeX = [zoneStyle.paddingLeft, zoneStyle.paddingRight, zoneStyle.borderLeftWidth, zoneStyle.borderRightWidth]
        .reduce((sum, value) => sum + (parseFloat(value) || 0), 0);
      const k = floorK(Math.min(
        (wrap.clientWidth - chromeX) / HOW_STORY_CANVAS.width,
        (roomHeight - chromeY) / HOW_STORY_CANVAS.height,
        MAX_K,
      ));
      if (k < MIN_K || stepOverflows()) {
        callbacks.current.onFitFail();
        return false;
      }
      wrap.style.setProperty("--how-k", String(k));
      frameHeight = frame.offsetHeight;
      // A stacked frame shorter than its room sticks centred in it instead of hugging the header.
      frame.style.setProperty("--how-sticky-extra", `${stacked ? Math.max(0, Math.floor((room - frameHeight) / 2)) : 0}px`);
      stickyTop = parseFloat(getComputedStyle(frame).top) || 0;
      const canvasRect = canvas.getBoundingClientRect();
      const counterRect = counter.getBoundingClientRect();
      stripTarget = {
        x: (counterRect.left + counterRect.width / 2 - canvasRect.left) / k,
        y: (counterRect.top + counterRect.height / 2 - canvasRect.top) / k,
      };
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
      storyFrame.segments.forEach((value, index) => write(segmentElements[index], "transform", getHowStorySegmentTransform(value)));
      for (const [key, value] of Object.entries(storyFrame.texts)) {
        if (value !== null) write(textElements.get(key), "text", value);
      }
    };

    const update = (force = false) => {
      rafId = 0;
      if (disposed) return;
      const trackRect = track.getBoundingClientRect();
      const frameRect = frame.getBoundingClientRect();
      const p = getHowStoryProgress({ trackTop: trackRect.top, trackHeight: trackRect.height, frameHeight, stickyTop });
      const viewportHeight = window.innerHeight;
      const visibleHeight = Math.min(frameRect.bottom, viewportHeight) - Math.max(frameRect.top, 0);
      const eligible = visibleHeight / Math.max(1, frameRect.height) >= 0.5 && document.visibilityState !== "hidden";
      if (force || Math.abs(p - lastP) >= P_EPSILON) {
        lastP = p;
        apply(getHowStoryFrame(p, { stripTarget }));
      }
      const { index } = getHowStoryScene(lastP);
      if (index !== lastScene) {
        lastScene = index;
        callbacks.current.onSceneChange(index);
      }
      callbacks.current.onFrameState({ sceneIndex: index, inHold: isHowStoryHold(lastP), eligible });
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
      track.classList.add("is-live");
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
      callbacks.current.onFrameState({ sceneIndex: lastScene, inHold: false, eligible: false });
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
    resize?.observe(wrap);
    // Captions change size with text zoom even when the frame does not; the fit check must see that.
    for (const caption of steps.querySelectorAll("h3, .how-story-support")) resize?.observe(caption);
    document.fonts?.ready.then(remeasure).catch(() => {});
    document.addEventListener("visibilitychange", handleVisibility);

    return () => {
      disposed = true;
      detach();
      callbacks.current.onFrameState({ sceneIndex: lastScene, inHold: false, eligible: false });
      intersection.disconnect();
      resize?.disconnect();
      document.removeEventListener("visibilitychange", handleVisibility);
      wrap.style.removeProperty("--how-k");
      frame.style.removeProperty("--how-sticky-extra");
    };
  }, [enabled, trackRef, frameRef, stageWrapRef, stepsRef]);
}
