import { memo, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ANALYTICS_EVENTS, trackEvent } from "./analytics.js";
import { occupiesEnoughOfViewport } from "./useExperienceAnalytics.js";
import { createPassiveViewTracker, createSceneReachTracker, latestEntry } from "./howStoryAnalytics.js";
import { useHowStoryScroll } from "./useHowStoryScroll.js";
import { PhoneFrame } from "./how/PhoneFrame.jsx";
import { ImportScreen, LOADING_DONE, LOADING_START, LoadingScreen, MapScreen, PickerScreen } from "./how/AppScreens.jsx";
import { LibraryMosaic, LibraryStill } from "./how/LibraryMosaic.jsx";
import { DIGIT_CELLS, digitKind } from "./how/digits.js";
import {
  HOW_STORY_BEATS,
  HOW_STORY_EXPERIENCE_TYPE,
  HOW_STORY_LIBRARY_COUNT,
  HOW_STORY_MATCHES,
  HOW_STORY_SCENE_COUNT,
  HOW_STORY_TEXT,
  fillHowStoryText,
  formatHowStoryNumber,
  getHowStorySceneDescription,
} from "./howStory.js";

const MIN_VIEWPORT_HEIGHT = 560;
// A static list re-checks scroll mode when a height-only resize grows the window by more than this.
const STATIC_RECHECK_GROWTH = 100;
const VIEW_OBSERVER_THRESHOLDS = Array.from({ length: 51 }, (_, index) => index / 50);

const OVERLAY_START = fillHowStoryText(HOW_STORY_TEXT.overlayValue, { n: 0 });
const OVERLAY_FOUND = fillHowStoryText(HOW_STORY_TEXT.overlayValue, { n: HOW_STORY_MATCHES.length });
const OVERLAY_SCOPE = fillHowStoryText(HOW_STORY_TEXT.overlayScope, { total: formatHowStoryNumber(HOW_STORY_LIBRARY_COUNT) });

function detectMode() {
  if (typeof window === "undefined") return "static";
  if (window.matchMedia?.("(prefers-reduced-motion: reduce)").matches) return "static";
  if (!("IntersectionObserver" in window)) return "static";
  if (window.innerHeight < MIN_VIEWPORT_HEIGHT) return "static";
  return "scroll";
}

function BeatCopy({ beat, description }) {
  return (
    <>
      <p className="how-copy-eyebrow">{beat.eyebrow}</p>
      <h3 className="how-copy-headline">{beat.headline.map((line) => <span key={line}>{line}</span>)}</h3>
      <p className="how-copy-sub">{beat.sub}</p>
      <p className="sr-only">{description}</p>
    </>
  );
}

// The running number sits in fixed-width cells, so its per-frame writes never shift the digits or relayout the
// block. The hook rewrites the cells in place.
function DigitCells({ value, textKey }) {
  const chars = [...value];
  return (
    <span className="how-digits" data-text={textKey} data-digits="">
      {Array.from({ length: DIGIT_CELLS }, (_, index) => <i data-c={digitKind(chars[index])} key={index}>{chars[index] ?? ""}</i>)}
    </span>
  );
}

// The big number over the library: the checked count while the phone checks, then the Gyeongju result lands.
function Overlay({ value, found = false, node }) {
  return (
    <div className="how-overlay" data-node={node} data-found={found ? "1" : undefined}>
      <span className="how-overlay-scrim" />
      <p className="how-overlay-label">
        <span className="how-overlay-a">{HOW_STORY_TEXT.overlayChecked}</span>
        <span className="how-overlay-b">{OVERLAY_SCOPE}</span>
      </p>
      <p className="how-overlay-value">
        <span className="how-overlay-a"><DigitCells value={value} textKey={node && "overlayValue"} /></span>
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
            <div className="how-phone-fade" data-node="phoneFade">
              <PhoneFrame>
                <ImportScreen node="screenImport" />
                <LoadingScreen node="screenLoading" {...LOADING_START} />
                <PickerScreen node="screenPicker" />
                <MapScreen node="screenMap" variant="drill" />
              </PhoneFrame>
            </div>
          </div>
        </div>
      </div>
      <Overlay node="overlay" value={OVERLAY_START} />
      {HOW_STORY_MATCHES.map((match, index) => (
        <div className="how-match" data-node={`match${index}`} key={match.id}>
          <span className="how-match-shadow" data-node={`matchShadow${index}`} />
          <img src={match.src} alt="" width="120" height="120" loading="lazy" decoding="async" />
        </div>
      ))}
      <span className="how-bottom-fade" data-node="bottomFade" />
      <p className="how-pill" data-node="pill">{HOW_STORY_TEXT.pill}</p>
    </div>
  );
});

// End-state figures for the static list (reduced motion, short windows, large text). No motion, one pill each.
function StaticFigure({ index }) {
  let figure;
  if (index === 0) {
    figure = <PhoneFrame><ImportScreen /></PhoneFrame>;
  } else if (index === 1) {
    figure = <PhoneFrame><LoadingScreen {...LOADING_DONE} /></PhoneFrame>;
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
    <figure className="how-static-figure" aria-hidden="true" data-figure={index + 1} data-typed="1" data-results="1" data-row="1" data-sel="3" data-instant="1">
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

  // Reduced motion, missing IntersectionObserver and short windows get the static list. A width change
  // (rotation, a new window size) re-runs the check and gives scroll mode another chance to fit. A height-only
  // change never leaves scroll mode (a phone's collapsing URL bar would flap it), but a static list re-checks
  // once the window grows past the minimum height or by more than 100px, so an enlarged short window recovers.
  useEffect(() => {
    const media = window.matchMedia?.("(prefers-reduced-motion: reduce)");
    let width = window.innerWidth;
    let lowestHeight = window.innerHeight;
    const reevaluate = () => {
      width = window.innerWidth;
      lowestHeight = window.innerHeight;
      setBaseMode(detectMode());
      setFitFailed(false);
    };
    const handleResize = () => {
      if (window.innerWidth !== width) {
        reevaluate();
        return;
      }
      const height = window.innerHeight;
      const grew = height - lowestHeight > STATIC_RECHECK_GROWTH || (lowestHeight < MIN_VIEWPORT_HEIGHT && height >= MIN_VIEWPORT_HEIGHT);
      if (modeRef.current === "static" && grew) reevaluate();
      else lowestHeight = Math.min(lowestHeight, height);
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
  const handleFitFail = useCallback(() => setFitFailed(true), []);
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
          <div className="how-story-frame" ref={frameRef}>
            <div className="how-copy" ref={copyRef} data-direction={scene.direction}>
              {HOW_STORY_BEATS.map((beat, index) => (
                <article
                  className="how-copy-beat"
                  key={beat.id}
                  data-copy={index === scene.index ? "active" : index === scene.previous ? "leaving" : undefined}
                >
                  <BeatCopy beat={beat} description={descriptions[index]} />
                </article>
              ))}
            </div>
            <HowStoryStage stageRef={stageRef} />
          </div>
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
    </>
  );
}

export { HOW_STORY_SCENE_COUNT, HowStoryStage };
