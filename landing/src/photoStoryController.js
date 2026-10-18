// Drives the #how scroll stories (#roll, then #rec): reads where the reader is, shows that beat's words, hands each
// canvas engine its progress and reports state for the two passive reach events. It follows the page scroll and
// never steers it: no wheel/touch/key handlers, no preventDefault, no scroll-snap, no scroll lock, no scrollTo.
// The engines (and d3/topojson/the maps they import) are lazy chunks, loaded once the section is within a viewport.
//
// QA hooks (read by scripts/how-story-browser-checks.mjs):
//   root [data-mode]                 "scroll" | "static" (= how_story_view.story_mode)
//   root [data-scene]                the beat on screen, 1-6 (= how_story_scene.scene_index), 0 before the stories
//   [data-story-track][data-beat]    each track's beat, 0-2
//   [data-story-step]                the six beats' words (a list in static mode)
import { STATIC_MAX_HEIGHT, STORY_TRACKS, beatOf, sceneOf } from "./photoStory.js";
import { clamp } from "./storyCanvas.js";

// mountPhotoStory(root, { onState }) -> destroy(); destroy.sample() re-reads the scroll and reports state now.
// onState({ mode, inView, scene: 1-6 | 0, atRest }) runs whenever any of those change.
export function mountPhotoStory(root, { onState } = {}) {
  const tracks = [...root.querySelectorAll("[data-story-track]")].map((el, i) => ({
    el,
    def: STORY_TRACKS[i],
    stage: el.querySelector("[data-story-stage]"),
    canvas: el.querySelector("canvas"),
    copy: el.querySelector("[data-story-copy]"),
    steps: [...el.querySelectorAll("[data-story-step]")],
    beat: -1,
    engine: null,
  }));
  const steps = tracks.flatMap((t) => t.steps);
  const reduceQuery = window.matchMedia("(prefers-reduced-motion: reduce)");
  let mode = "", destroyed = false, raf = 0, near = false, loading = null;

  function decideMode() {
    const next = reduceQuery.matches || window.innerHeight < STATIC_MAX_HEIGHT ? "static" : "scroll";
    if (next === mode) return false;
    mode = next;
    root.dataset.mode = mode;
    root.classList.toggle("is-static", mode === "static");
    tracks.forEach((t) => t.engine?.setMode(mode));
    return true;
  }

  function loadEngines() {
    if (loading) return;
    loading = Promise.all([import("./rollStoryEngine.js"), import("./recStoryEngine.js")]).then(([{ mountRollStory }, { mountRecStory }]) => {
      if (destroyed) return;
      const [roll, rec] = tracks;
      roll.engine = mountRollStory({ track: roll.el, canvas: roll.canvas, copy: roll.copy }, { mode });
      rec.engine = mountRecStory({ track: rec.el, canvas: rec.canvas }, { mode });
      req();
    }).catch(() => { loading = null; }); // a failed chunk (an old tab after a deploy): the words still read, retry when near again
  }

  function setBeat(t, beat) {
    if (beat === t.beat) return;
    t.beat = beat;
    t.el.dataset.beat = String(beat);
    t.steps.forEach((el, k) => el.classList.toggle("is-active", k === beat));
  }

  let reported = "";
  function report(next) {
    const key = `${next.mode}|${next.inView}|${next.scene}|${next.atRest}`;
    if (key === reported) return;
    reported = key;
    root.dataset.scene = String(next.scene);
    onState?.(next);
  }

  function frame() {
    raf = 0;
    if (destroyed) return;
    const vh = window.innerHeight;
    const rr = root.getBoundingClientRect();
    const visible = Math.min(rr.bottom, vh) - Math.max(rr.top, 0);
    if (mode === "static") {
      // of the beats at least half in view, the one nearest the middle of the screen
      let scene = 0, best = Infinity;
      steps.forEach((el, k) => {
        const s = el.getBoundingClientRect();
        const seen = Math.min(s.bottom, vh) - Math.max(s.top, 0);
        const off = Math.abs((s.top + s.bottom) / 2 - vh / 2);
        if (s.height > 0 && seen >= s.height / 2 && off < best) { best = off; scene = k + 1; }
      });
      report({ mode, inView: visible >= Math.min(vh, rr.height) / 2, scene, atRest: scene > 0 });
      return;
    }
    // read every rect first, then write
    const reads = tracks.map((t) => ({ r: t.el.getBoundingClientRect(), sh: t.stage.offsetHeight }));
    let scene = 0, atRest = false;
    reads.forEach(({ r, sh }, i) => {
      const t = tracks[i];
      const p = clamp(-r.top / Math.max(1, r.height - sh));
      const beat = beatOf(t.def, p);
      setBeat(t, beat);
      t.engine?.setProgress(p);
      const pinned = r.top <= 1 && r.bottom >= sh - 1;
      // the track pinned on screen is the scene; between the two, the one that holds the middle of the screen
      if (pinned) { scene = sceneOf(i, beat); atRest = true; }
      else if (!atRest && r.top < vh / 2 && r.bottom > vh / 2) scene = sceneOf(i, beat);
    });
    report({ mode, inView: visible >= vh / 2, scene, atRest });
  }
  const req = () => { if (!raf && !destroyed) raf = requestAnimationFrame(frame); };

  const io = new IntersectionObserver((entries) => {
    near = entries[entries.length - 1].isIntersecting; // one callback can carry an enter and an exit; the last is current
    if (near) { loadEngines(); req(); } else report({ mode, inView: false, scene: 0, atRest: false });
  }, { rootMargin: "100% 0px" });
  io.observe(root);
  const onScroll = () => { if (near) req(); };
  const onResize = () => { decideMode(); req(); };
  window.addEventListener("scroll", onScroll, { passive: true });
  window.addEventListener("resize", onResize);
  reduceQuery.addEventListener("change", onResize);

  decideMode();
  tracks.forEach((t) => setBeat(t, 0));
  req();

  const destroy = () => {
    destroyed = true;
    cancelAnimationFrame(raf);
    io.disconnect();
    window.removeEventListener("scroll", onScroll);
    window.removeEventListener("resize", onResize);
    reduceQuery.removeEventListener("change", onResize);
    tracks.forEach((t) => { t.engine?.destroy(); t.engine = null; delete t.el.dataset.beat; });
    delete root.dataset.mode;
    delete root.dataset.scene;
    root.classList.remove("is-static");
  };
  // re-read where the reader is now (the analytics confirm calls this on a fresh frame)
  destroy.sample = () => { if (!destroyed) { cancelAnimationFrame(raf); frame(); } };
  return destroy;
}
