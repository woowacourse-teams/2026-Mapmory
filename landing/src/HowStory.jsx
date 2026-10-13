import { memo, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ANALYTICS_EVENTS, trackEvent } from "./analytics.js";
import { occupiesEnoughOfViewport } from "./useExperienceAnalytics.js";
import { createPassiveViewTracker, createSceneReachTracker, latestEntry } from "./howStoryAnalytics.js";
import { useHowStoryScroll } from "./useHowStoryScroll.js";
import { PhoneFrame } from "./how/PhoneFrame.jsx";
import { ImportScreen, LOADING_START, LOADING_STILL, LoadingScreen, MapScreen, PickerCardChrome, PickerScreen } from "./how/AppScreens.jsx";
import { HowStoryBand } from "./how/HowStoryBand.jsx";
import { LibraryMosaic, LibraryStill, MatchPin } from "./how/LibraryMosaic.jsx";
import { HOW_STORY_SAMPLE_METRICS } from "./howStorySample.js";
import {
  HOW_STORY_BEATS,
  HOW_STORY_EXPERIENCE_TYPE,
  HOW_STORY_LIBRARY_COUNT,
  HOW_STORY_MATCHES,
  HOW_STORY_SCENE_COUNT,
  HOW_STORY_TEXT,
  fillHowStoryText,
  formatHowStoryNumber,
  getHowStoryBand,
  getHowStorySceneDescription,
} from "./howStory.js";

const MIN_VIEWPORT_HEIGHT = 560;
// A static list re-checks scroll mode when a height-only resize grows the window by more than this...
const STATIC_RECHECK_GROWTH = 100;
// ...or grows the small viewport height (what the story's frame is sized in) by this much since the last check or
// fit failure: a desktop window made taller again. A phone's or tablet's collapsing URL bar never changes it.
const SMALL_VIEWPORT_RECHECK_GROWTH = 16;
const VIEW_OBSERVER_THRESHOLDS = Array.from({ length: 51 }, (_, index) => index / 50);

const OVERLAY_START = fillHowStoryText(HOW_STORY_TEXT.overlayValue, { n: 0 });
const OVERLAY_TOTAL = fillHowStoryText(HOW_STORY_TEXT.overlayValue, { n: formatHowStoryNumber(HOW_STORY_LIBRARY_COUNT) });
const OVERLAY_FOUND = fillHowStoryText(HOW_STORY_TEXT.overlayValue, { n: HOW_STORY_MATCHES.length });
const OVERLAY_SCOPE = fillHowStoryText(HOW_STORY_TEXT.overlayScope, { total: formatHowStoryNumber(HOW_STORY_LIBRARY_COUNT) });
const PICKED = HOW_STORY_MATCHES.filter((match) => match.picked);

// The band shows measured numbers only (HOW_STORY_METRICS). In development, ?how-sample fills it with the test
// sample so it can be looked at; a production build has no such path (import.meta.env.DEV is false there).
function bandOptions() {
  if (import.meta.env.DEV && typeof window !== "undefined" && new URLSearchParams(window.location.search).has("how-sample")) {
    return { metrics: HOW_STORY_SAMPLE_METRICS };
  }
  return {};
}

function detectMode() {
  if (typeof window === "undefined") return "static";
  if (window.matchMedia?.("(prefers-reduced-motion: reduce)").matches) return "static";
  if (!("IntersectionObserver" in window)) return "static";
  if (window.innerHeight < MIN_VIEWPORT_HEIGHT) return "static";
  return "scroll";
}

// 100svh in CSS px (100vh where svh is unsupported), read from a throwaway probe; only on resizes of a static list.
function smallViewportHeight() {
  const probe = document.createElement("div");
  probe.style.cssText = "position:absolute;top:0;left:0;width:0;height:100vh;height:100svh;visibility:hidden;pointer-events:none";
  document.body.append(probe);
  const height = probe.offsetHeight;
  probe.remove();
  return height || window.innerHeight;
}

function BeatCopy({ beat, description }) {
  return (
    <>
      <p className="how-copy-eyebrow">{beat.eyebrow}</p>
      <h3 className="how-copy-headline">{beat.headline.map((line) => <span key={line}>{line}</span>)}</h3>
      <p className="how-copy-sub">{beat.sub}</p>
      {description && <p className="sr-only">{description}</p>}
    </>
  );
}

// Scroll mode shows one beat's copy at a time (the others are hidden), so screen readers get all five beats here,
// each with what its stage shows, and the visual copy column is hidden from them.
function BeatList({ descriptions }) {
  return (
    <ol className="sr-only">
      {HOW_STORY_BEATS.map((beat, index) => (
        <li key={beat.id}>
          <h3>{beat.headline.join(" ")}</h3>
          <p>{beat.sub}</p>
          <p>{descriptions[index]}</p>
        </li>
      ))}
    </ol>
  );
}

// The big number over the library: the checked count while the phone checks, then the Gyeongju result lands.
// The running count is one text node in a fixed box sized by an invisible copy of the final value, so a write
// never relayouts anything outside it. It is set naturally (LINE Seed has no tabular digits) and anchored at
// the start, so the leading digits stay put while the last ones run; at rest it is the final value, centred.
function Overlay({ value, found = false, node }) {
  return (
    <div className="how-overlay" data-node={node} data-found={found ? "1" : undefined}>
      <span className="how-overlay-scrim" />
      <p className="how-overlay-label">
        <span className="how-overlay-a">{HOW_STORY_TEXT.overlayChecked}</span>
        <span className="how-overlay-b">{OVERLAY_SCOPE}</span>
      </p>
      <p className="how-overlay-value">
        <span className="how-overlay-a how-overlay-count">
          <span className="how-overlay-sizer">{OVERLAY_TOTAL}</span>
          <span className="how-overlay-live" data-text={node && "overlayValue"}>{value}</span>
        </span>
        <span className="how-overlay-b">{OVERLAY_FOUND}</span>
      </p>
      <span className="how-overlay-bar" data-node={node && "overlayBar"}><i data-node={node && "overlayBarFill"} /></span>
    </div>
  );
}

// The pinned stage. Static markup (memoised, so a scene change re-renders only the copy); the scroll hook writes
// every frame onto its [data-node] / [data-text] elements and its data-* state.
const HowStoryStage = memo(function HowStoryStage({ stageRef }) {
  return (
    <div className="how-stage" ref={stageRef} aria-hidden="true" data-scene="1">
      <span className="how-glow" />
      <span className="how-ground" data-node="ground" />
      <LibraryMosaic matches={HOW_STORY_MATCHES} />
      <div className="how-phone-slot">
        <div className="how-phone-enter">
          <div className="how-phone-camera" data-node="phoneCamera">
            <PhoneFrame>
              {/* The screen (content and background) goes clear before the bezel on the way in. The screens switch
                  by the stage's state, in time (how-story.css). */}
              <div className="how-screen-fade" data-node="screenFade">
                <ImportScreen />
                <LoadingScreen {...LOADING_START} />
                <PickerScreen cardsOnStage />
                <MapScreen variant="story" />
              </div>
            </PhoneFrame>
          </div>
        </div>
      </div>
      <Overlay node="overlay" value={OVERLAY_START} />
      {/* The four Gyeongju photos: library -> cluster (beat 03) -> the picker's cards (beat 04), whose border, preview
          button and tap rings they carry. They leave the library with its green ring and pin, which fade in flight. */}
      {HOW_STORY_MATCHES.map((match, index) => (
        <div className="how-match" data-node={`match${index}`} key={match.id}>
          <span className="how-match-shadow" data-node={`matchShadow${index}`} />
          <img src={match.src} alt="" width="120" height="120" loading="lazy" decoding="async" />
          <span className="how-match-mark" data-node={`matchRing${index}`}><MatchPin /></span>
          <span className="how-match-chrome" data-node={`matchChrome${index}`} data-card={index}>
            <PickerCardChrome />
            {match.picked && <span className="how-tap" data-tap={`pick-${PICKED.indexOf(match)}`} />}
          </span>
        </div>
      ))}
      {/* Saving: a copy of each picked photo lifts off its card into a stack of prints, which rises off the top
          as beat 05 opens (the upload), before the map shows. */}
      <div className="how-copy-stack">
        {PICKED.map((match, index) => (
          <div className="how-copy-node" data-node={`copy${index}`} key={match.id}>
            <img src={match.src} alt="" width="120" height="120" loading="lazy" decoding="async" />
          </div>
        ))}
      </div>
      <span className="how-bottom-fade" data-node="bottomFade" />
    </div>
  );
});

// The sticky frame in scroll mode: the beat copy, the stage, and the one sample-screen pill for the whole pin. The pill
// sits on the frame, not in the stage: beside the eyebrow on phones and tablets (the copy never fades, so it
// shows at every p), and beside the phone's top on desktop.
function HowStoryScrollFrame({ frameRef, copyRef, stageRef, scene, descriptions }) {
  return (
    <div className="how-story-frame" ref={frameRef}>
      <BeatList descriptions={descriptions} />
      <div className="how-copy" ref={copyRef} aria-hidden="true" data-direction={scene.direction} data-swap={scene.previous === null ? undefined : "1"}>
        {HOW_STORY_BEATS.map((beat, index) => (
          <article
            className="how-copy-beat"
            key={beat.id}
            data-copy={index === scene.index ? "active" : index === scene.previous ? "leaving" : undefined}
          >
            <BeatCopy beat={beat} />
          </article>
        ))}
      </div>
      <HowStoryStage stageRef={stageRef} />
      <p className="how-pill how-frame-pill" data-node="pill" aria-hidden="true">{HOW_STORY_TEXT.pill}</p>
    </div>
  );
}

// End-state figures for the static list (reduced motion, short windows, large text). No motion, one pill each.
function StaticFigure({ index }) {
  let figure;
  if (index === 0) {
    figure = <PhoneFrame><ImportScreen /></PhoneFrame>;
  } else if (index === 1) {
    figure = <PhoneFrame><LoadingScreen {...LOADING_STILL} /></PhoneFrame>;
  } else if (index === 2) {
    figure = (
      <div className="how-static-library">
        <LibraryStill />
        <Overlay value={OVERLAY_FOUND} found />
        <div className="how-static-cluster">
          {HOW_STORY_MATCHES.map((match) => <img src={match.src} alt="" width="120" height="120" loading="lazy" decoding="async" key={match.id} />)}
        </div>
      </div>
    );
  } else if (index === 3) {
    figure = <PhoneFrame><PickerScreen /></PhoneFrame>;
  } else {
    figure = <PhoneFrame><MapScreen variant="korea" /></PhoneFrame>;
  }
  return (
    <figure className="how-static-figure" aria-hidden="true" data-figure={index + 1} data-typed="1" data-results="1" data-row="1" data-sel="3">
      {figure}
      <p className="how-pill">{HOW_STORY_TEXT.pill}</p>
    </figure>
  );
}

// The section heading (rendered in App.jsx) fades up once, the first time it comes into view.
function useHeadingEntrance(trackRef) {
  useEffect(() => {
    const heading = trackRef.current?.closest("section")?.querySelector(".how-heading");
    if (!heading || typeof IntersectionObserver === "undefined") return undefined;
    if (window.matchMedia?.("(prefers-reduced-motion: reduce)").matches) return undefined;
    let first = true;
    const observer = new IntersectionObserver((entries) => {
      const entry = latestEntry(entries);
      if (first) {
        first = false;
        // Already on screen when the page loads: leave it be.
        if (entry.isIntersecting) {
          observer.disconnect();
          return;
        }
        heading.classList.add("is-waiting");
        return;
      }
      if (entry.isIntersecting) {
        heading.classList.remove("is-waiting");
        heading.classList.add("is-shown");
        observer.disconnect();
      }
    }, { threshold: 0.25 });
    observer.observe(heading);
    return () => {
      observer.disconnect();
      heading.classList.remove("is-waiting");
    };
  }, [trackRef]);
}

export function HowStory() {
  const trackRef = useRef(null);
  const frameRef = useRef(null);
  const stageRef = useRef(null);
  const copyRef = useRef(null);
  const [baseMode, setBaseMode] = useState(detectMode);
  const [fitFailed, setFitFailed] = useState(false);
  const [scene, setScene] = useState({ index: 0, previous: null, direction: "down" });
  const mode = baseMode === "scroll" && !fitFailed ? "scroll" : "static";
  const modeRef = useRef(mode);
  modeRef.current = mode;

  const descriptions = useMemo(() => HOW_STORY_BEATS.map((_, index) => getHowStorySceneDescription(index)), []);
  const band = useMemo(() => getHowStoryBand(bandOptions()), []);

  // Reduced motion, missing IntersectionObserver and short windows get the static list. A width change
  // (rotation, a new window size) re-runs the check and gives scroll mode another chance to fit. A height-only
  // change never leaves scroll mode (a phone's collapsing URL bar would flap it), but a static list re-checks
  // once the window grows past the minimum height or by more than 100px, or once the small viewport height grows
  // 16px past where the last check (or fit failure) left it, so a desktop window shrunk and restored recovers.
  const checkedSmallHeightRef = useRef(0);
  useEffect(() => {
    const media = window.matchMedia?.("(prefers-reduced-motion: reduce)");
    let width = window.innerWidth;
    let lowestHeight = window.innerHeight;
    checkedSmallHeightRef.current = smallViewportHeight();
    const reevaluate = () => {
      width = window.innerWidth;
      lowestHeight = window.innerHeight;
      checkedSmallHeightRef.current = smallViewportHeight();
      setBaseMode(detectMode());
      setFitFailed(false);
    };
    const handleResize = () => {
      if (window.innerWidth !== width) {
        reevaluate();
        return;
      }
      const height = window.innerHeight;
      if (modeRef.current !== "static") {
        lowestHeight = Math.min(lowestHeight, height);
        return;
      }
      const small = smallViewportHeight();
      const grew = height - lowestHeight > STATIC_RECHECK_GROWTH
        || (lowestHeight < MIN_VIEWPORT_HEIGHT && height >= MIN_VIEWPORT_HEIGHT)
        || small - checkedSmallHeightRef.current >= SMALL_VIEWPORT_RECHECK_GROWTH;
      if (grew) reevaluate();
      else {
        lowestHeight = Math.min(lowestHeight, height);
        checkedSmallHeightRef.current = Math.min(checkedSmallHeightRef.current, small);
      }
    };
    media?.addEventListener?.("change", reevaluate);
    window.addEventListener("resize", handleResize);
    return () => {
      media?.removeEventListener?.("change", reevaluate);
      window.removeEventListener("resize", handleResize);
    };
  }, []);

  // how_story_view: once per page load, after the story fills enough of the viewport for 1 s in a visible tab,
  // or right before the first how_story_scene if a reader settles in a hold sooner (the funnel is ordered).
  const viewTrackerRef = useRef(null);
  useEffect(() => {
    const track = trackRef.current;
    if (!track || typeof IntersectionObserver === "undefined") return undefined;
    const tracker = createPassiveViewTracker({
      onView: () => trackEvent(ANALYTICS_EVENTS.HOW_STORY_VIEW, {
        experience_type: HOW_STORY_EXPERIENCE_TYPE,
        story_mode: modeRef.current,
      }),
    });
    viewTrackerRef.current = tracker;
    let inView = false;
    const sync = () => tracker.update(inView && document.visibilityState !== "hidden");
    // Crossings queued between callbacks arrive oldest first; only the newest one says where the track is now.
    const observer = new IntersectionObserver((entries) => {
      inView = occupiesEnoughOfViewport(latestEntry(entries));
      sync();
    }, { threshold: VIEW_OBSERVER_THRESHOLDS });
    observer.observe(track);
    document.addEventListener("visibilitychange", sync);
    return () => {
      observer.disconnect();
      document.removeEventListener("visibilitychange", sync);
      tracker.stop();
      if (viewTrackerRef.current === tracker) viewTrackerRef.current = null;
    };
  }, []);

  // how_story_scene: scroll mode only, once per scene, after its hold has stayed on screen for 700 ms.
  const reachTrackerRef = useRef(null);
  if (!reachTrackerRef.current) {
    reachTrackerRef.current = createSceneReachTracker({
      onReach: (index) => {
        viewTrackerRef.current?.flush();
        trackEvent(ANALYTICS_EVENTS.HOW_STORY_SCENE, {
          experience_type: HOW_STORY_EXPERIENCE_TYPE,
          scene_index: index + 1,
        });
      },
    });
  }
  useEffect(() => () => reachTrackerRef.current.stop(), []);
  const handleFrameState = useCallback((state) => reachTrackerRef.current.update(state), []);
  const handleFitFail = useCallback(() => {
    checkedSmallHeightRef.current = smallViewportHeight();
    setFitFailed(true);
  }, []);
  const handleSceneChange = useCallback((index) => {
    setScene((current) => (current.index === index ? current : { index, previous: current.index, direction: index > current.index ? "down" : "up" }));
  }, []);

  useHowStoryScroll({
    enabled: mode === "scroll",
    trackRef,
    frameRef,
    stageRef,
    copyRef,
    onSceneChange: handleSceneChange,
    onFrameState: handleFrameState,
    onFitFail: handleFitFail,
  });
  useHeadingEntrance(trackRef);

  return (
    <>
      <a className="how-story-skip" href="#how-end">{HOW_STORY_TEXT.skip}</a>
      <div className="how-story-track" ref={trackRef} data-mode={mode}>
        {mode === "scroll" ? (
          <HowStoryScrollFrame frameRef={frameRef} copyRef={copyRef} stageRef={stageRef} scene={scene} descriptions={descriptions} />
        ) : (
          <ol className="how-static">
            {HOW_STORY_BEATS.map((beat, index) => (
              <li className="how-static-beat" key={beat.id}>
                <div className="how-static-copy"><BeatCopy beat={beat} description={descriptions[index]} /></div>
                <StaticFigure index={index} />
              </li>
            ))}
          </ol>
        )}
      </div>
      {band && <HowStoryBand band={band} animate={mode === "scroll"} />}
    </>
  );
}

export { HOW_STORY_SCENE_COUNT, HowStoryScrollFrame, HowStoryStage };
