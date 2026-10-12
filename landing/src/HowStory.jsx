import { memo, useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { Check, CheckCircle, CloudArrowUp, DeviceMobile, MagnifyingGlass, MapPin } from "@phosphor-icons/react";
import koreaProvinces from "./data/korea-provinces.json";
import gyeongjuBoundary from "./data/gyeongju-boundary.json";
import { ANALYTICS_EVENTS, trackEvent } from "./analytics.js";
import { occupiesEnoughOfViewport } from "./useExperienceAnalytics.js";
import { createPassiveViewTracker, createSceneReachTracker, latestEntry } from "./howStoryAnalytics.js";
import { useHowStoryScroll } from "./useHowStoryScroll.js";
import { KOREA_BOUNDS, createProjector, ringsToPath } from "./koreaMap.js";
import {
  HOW_STORY_CANVAS,
  HOW_STORY_COPY_SLOTS,
  HOW_STORY_EXPERIENCE_TYPE,
  HOW_STORY_FILLERS,
  HOW_STORY_GRID,
  HOW_STORY_NODE_SIZE,
  HOW_STORY_SCENE_COUNT,
  HOW_STORY_SCENES,
  HOW_STORY_TEXT,
  HOW_STORY_TILES,
  getHowStoryFrame,
  getHowStoryNodeStyle,
  getHowStoryNotes,
  getHowStoryPlan,
  getHowStorySceneDescription,
  getHowStorySegmentTransform,
  projectToStoryMap,
  stillProgress,
} from "./howStory.js";

const MIN_VIEWPORT_HEIGHT = 560;
// A static list re-checks scroll mode when a height-only resize grows the window by more than this.
const STATIC_RECHECK_GROWTH = 100;
const STATIC_STILL_MAX_K = 1.25;
const VIEW_OBSERVER_THRESHOLDS = Array.from({ length: 51 }, (_, index) => index / 50);
const MAP_NEIGHBOURS = ["KR-48", "KR-27", "KR-31", "KR-26", "KR-47"];

// Map paths are static, so they are built once per page load.
const provinceByCode = new Map(koreaProvinces.map((province) => [province.code, province]));
const STORY_MAP_PATHS = MAP_NEIGHBOURS.map((code) => ({
  code,
  d: ringsToPath(provinceByCode.get(code).rings, projectToStoryMap),
}));
const GYEONGJU_PATH = ringsToPath(gyeongjuBoundary.rings, projectToStoryMap);
const [KOREA_WIDTH, KOREA_HEIGHT] = HOW_STORY_NODE_SIZE.korea;
const projectKorea = createProjector(KOREA_BOUNDS, { width: KOREA_WIDTH, height: KOREA_HEIGHT });
const KOREA_PATHS = koreaProvinces.map(({ code, rings }) => ({ code, d: ringsToPath(rings, projectKorea) }));
const KOREA_FILL_PATH = ringsToPath(provinceByCode.get(gyeongjuBoundary.provinceCode).rings, projectKorea);
const COPY_SOURCES = HOW_STORY_COPY_SLOTS.map((slot) => HOW_STORY_TILES.find((tile) => tile.slot === slot).src);

function detectMode() {
  if (typeof window === "undefined") return "static";
  if (window.matchMedia?.("(prefers-reduced-motion: reduce)").matches) return "static";
  if (!("IntersectionObserver" in window)) return "static";
  if (window.innerHeight < MIN_VIEWPORT_HEIGHT) return "static";
  return "scroll";
}

const style = (node) => (node ? getHowStoryNodeStyle(node) : undefined);
const cellStyle = ([row, column]) => ({
  left: `${HOW_STORY_GRID.x0 + column * HOW_STORY_GRID.pitch}px`,
  top: `${HOW_STORY_GRID.y0 + row * HOW_STORY_GRID.pitch}px`,
});

function TileFlag({ hasLocation, ...props }) {
  return hasLocation
    ? <span className="how-flag is-pin" {...props}><MapPin size={11} weight="fill" /></span>
    : <span className="how-flag is-none" {...props}><span>{HOW_STORY_TEXT.noLocation}</span></span>;
}

// One stage serves both modes. It renders the frame it is given; in scroll mode the hook then writes
// transforms, opacities and counter text straight to the [data-node]/[data-text] elements.
export const HowStoryStage = memo(function HowStoryStage({ frame, plan }) {
  const { nodes, texts } = frame;
  return (
    <div className="how-stage" aria-hidden="true">
      {/* Above the line is the server side, so it holds only what the Mapmory server has received. */}
      <div className="how-server">
        <span className="how-server-label"><CloudArrowUp size={15} weight="bold" />{HOW_STORY_TEXT.hudServer}</span>
        <span className="how-server-value" data-strip-target="" data-text="server">{texts.server}</span>
      </div>
      <div className="how-line"><span>{HOW_STORY_TEXT.lineLabel}</span></div>
      <div className="how-zone">
        <span className="how-zone-frame" data-node="zoneFrame" style={style(nodes.zoneFrame)} />
        <div className="how-zone-head">
          <span className="how-zone-chip" data-node="zoneChip" style={style(nodes.zoneChip)}><DeviceMobile size={15} weight="duotone" />{HOW_STORY_TEXT.zone}</span>
          <span className="how-zone-place" data-node="placeChip" style={style(nodes.placeChip)}><MagnifyingGlass size={12} weight="bold" />{HOW_STORY_TEXT.place}</span>
          <span className="finder-sample-badge how-zone-sample">{HOW_STORY_TEXT.sample}</span>
        </div>
        {/* What the phone does stays inside the phone zone: the scan counter (with a measured timer) and taps. */}
        <div className={`how-meter ${plan.taps ? "has-taps" : ""}`}>
          <span className="how-meter-scan" data-node="checkedMeter" style={style(nodes.checkedMeter)}>
            <span className="how-meter-label">{HOW_STORY_TEXT.hudChecked}</span>
            <span className="how-meter-value" data-text="checked">{texts.checked}</span>
            {plan.scan && <span className="how-meter-timer" data-text="timer">{texts.timer}</span>}
          </span>
          {plan.taps && (
            <span className="how-meter-taps">
              <span className="how-meter-label">{HOW_STORY_TEXT.hudTaps}</span>
              <span className="how-meter-value" data-text="taps">{texts.taps}</span>
            </span>
          )}
        </div>
        <div className="how-stage-box">
          <div className="how-stage-canvas">
            <div className="how-stage-clip">
              <div className="how-strip" data-node="strip" style={style(nodes.strip)}>
                {HOW_STORY_FILLERS.map((filler) => (
                  <div className="how-tile how-filler" key={filler.cell.join(":")} style={cellStyle(filler.cell)}>
                    <span className="how-tile-photo">
                      <img
                        src={filler.src}
                        alt=""
                        width="56"
                        height="56"
                        loading="lazy"
                        decoding="async"
                        style={{ objectPosition: `${filler.crop[0]}% ${filler.crop[1]}%`, transformOrigin: `${filler.crop[0]}% ${filler.crop[1]}%`, transform: `scale(${filler.crop[2]})` }}
                      />
                    </span>
                  </div>
                ))}
              </div>
              {/* The fillers' flags move with the strip in their own layer, clipped at the scan line. */}
              <div className="how-pins-clip" data-node="pins" style={style(nodes.pins)}>
                <div className="how-pins" data-node="pinsStrip" style={style(nodes.pinsStrip)}>
                  {HOW_STORY_FILLERS.map((filler) => (
                    <div className="how-tile" key={filler.cell.join(":")} style={cellStyle(filler.cell)}>
                      <TileFlag hasLocation={filler.hasLocation} />
                    </div>
                  ))}
                </div>
              </div>
              <div className="how-veil" data-node="veil" style={style(nodes.veil)} />
              <div className="how-scan-line" data-node="scanLine" style={style(nodes.scanLine)} />
              <svg className="how-map" data-node="map" style={style(nodes.map)} viewBox={`0 0 ${HOW_STORY_CANVAS.width} ${HOW_STORY_CANVAS.height}`} focusable="false">
                {STORY_MAP_PATHS.map(({ code, d }) => <path key={code} className="how-map-land" d={d} />)}
                <path className="how-map-fill" data-node="mapFill" style={style(nodes.mapFill)} d={GYEONGJU_PATH} />
                <path className="how-map-boundary" d={GYEONGJU_PATH} />
              </svg>
              <p className="how-map-caption" data-node="mapCaption" style={style(nodes.mapCaption)}>{HOW_STORY_TEXT.mapCaption}</p>
              <div className="how-sheet" data-node="sheet" style={style(nodes.sheet)}>
                <div className="how-sheet-labels" data-node="sheetHead" style={style(nodes.sheetHead)}>
                  <div className="how-sheet-head">
                    <strong>{HOW_STORY_TEXT.sheetTitle}</strong>
                    <span className="how-sheet-save" data-node="saveText" style={style(nodes.saveText)}>{HOW_STORY_TEXT.save}</span>
                  </div>
                  <div className="how-sheet-sub">
                    <span>{HOW_STORY_TEXT.sheetSubtitle}</span>
                    <span className="how-sheet-count" data-text="sheetCount">{texts.sheetCount}</span>
                  </div>
                </div>
                <div className="how-sheet-slots">
                  {[0, 1, 2, 3].map((slot) => <span key={slot} />)}
                </div>
              </div>
              {HOW_STORY_TILES.map((tile, index) => (
                <div className={`how-tile how-story-tile is-${tile.kind}`} key={tile.src} data-node={`tile${index}`} style={style(nodes[`tile${index}`])}>
                  <span className="how-tile-photo" data-node={`tilePhoto${index}`} style={style(nodes[`tilePhoto${index}`])}>
                    <img src={tile.src} alt="" width="56" height="56" loading="lazy" decoding="async" />
                  </span>
                  <TileFlag hasLocation={tile.kind !== "no-location"} data-node={`tileFlag${index}`} style={style(nodes[`tileFlag${index}`])} />
                  <span className="how-tile-dot is-grey" data-node={`tileGrey${index}`} style={style(nodes[`tileGrey${index}`])} />
                  <span className="how-tile-dot is-mint" data-node={`tileMint${index}`} style={style(nodes[`tileMint${index}`])} />
                  {tile.kind === "match" && (
                    <span className="how-tile-badge" data-node={`tileBadge${index}`} style={style(nodes[`tileBadge${index}`])}><Check size={12} weight="bold" /></span>
                  )}
                </div>
              ))}
              <span className="how-keep-tag" data-node="keepTag" style={style(nodes.keepTag)}><DeviceMobile size={13} weight="bold" />{HOW_STORY_TEXT.keep}</span>
              <p className="how-pill" data-node="pill" style={style(nodes.pill)}><CheckCircle size={15} weight="fill" /><span data-text="pill">{texts.pill}</span></p>
              <div className="how-search" data-node="searchCard" style={style(nodes.searchCard)}>
                <div className="how-search-field">
                  <MagnifyingGlass size={16} weight="bold" />
                  <span className="how-search-text">
                    <span data-text="typed">{texts.typed}</span>
                    <span className="how-search-caret" data-node="caret" style={style(nodes.caret)} />
                    <span className="how-search-placeholder" data-node="placeholder" style={style(nodes.placeholder)}>{HOW_STORY_TEXT.searchPlaceholder}</span>
                  </span>
                </div>
                <div className="how-search-result" data-node="resultRow" style={style(nodes.resultRow)}>
                  <MapPin size={15} weight="fill" />
                  <span>{HOW_STORY_TEXT.place}</span>
                  <span className="how-search-mark" data-node="resultMark" style={style(nodes.resultMark)}><Check size={11} weight="bold" /></span>
                </div>
              </div>
              <svg className="how-korea" data-node="korea" style={style(nodes.korea)} viewBox={`0 0 ${KOREA_WIDTH} ${KOREA_HEIGHT}`} focusable="false">
                {KOREA_PATHS.map(({ code, d }) => <path key={code} d={d} />)}
                <path className="how-korea-fill" data-node="koreaFill" style={style(nodes.koreaFill)} d={KOREA_FILL_PATH} />
              </svg>
              {HOW_STORY_TEXT.levels.map(({ level, name }, index) => (
                <div className="how-level" key={level} data-node={`level${index}`} style={style(nodes[`level${index}`])}>
                  <i />
                  <span><small>{level}</small><strong>{name}</strong></span>
                </div>
              ))}
              <span className="how-tap-ring" data-node="tapRing" style={style(nodes.tapRing)} />
            </div>
            {COPY_SOURCES.map((src, index) => (
              <div className="how-copy" key={src} data-node={`copy${index}`} style={style(nodes[`copy${index}`])}>
                <img src={src} alt="" width="56" height="56" loading="lazy" decoding="async" />
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
});

function HowStoryProgress({ frame }) {
  return (
    <div className="how-story-progress" aria-hidden="true">
      {HOW_STORY_TEXT.progressLabels.map((label, index) => (
        <span className="how-story-progress-step" key={label}>
          <span className="how-story-progress-track"><i data-seg={index} style={{ transform: getHowStorySegmentTransform(frame.segments[index]) }} /></span>
          <small>{label}</small>
        </span>
      ))}
    </div>
  );
}

export function HowStory() {
  const trackRef = useRef(null);
  const frameRef = useRef(null);
  const stageWrapRef = useRef(null);
  const stepsRef = useRef(null);
  const [baseMode, setBaseMode] = useState(detectMode);
  const [fitFailed, setFitFailed] = useState(false);
  const [sceneIndex, setSceneIndex] = useState(0);
  const mode = baseMode === "scroll" && !fitFailed ? "scroll" : "static";
  const modeRef = useRef(mode);
  modeRef.current = mode;

  const plan = getHowStoryPlan();
  const firstFrame = useMemo(() => getHowStoryFrame(0), []);
  const stillFrames = useMemo(() => HOW_STORY_SCENES.map((_, index) => getHowStoryFrame(stillProgress(index))), []);
  const descriptions = useMemo(() => HOW_STORY_SCENES.map((_, index) => getHowStorySceneDescription(index)), []);
  const notes = useMemo(() => getHowStoryNotes(), []);

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

  useHowStoryScroll({
    enabled: mode === "scroll",
    trackRef,
    frameRef,
    stageWrapRef,
    stepsRef,
    onSceneChange: setSceneIndex,
    onFrameState: handleFrameState,
    onFitFail: handleFitFail,
  });

  // Static stills scale to the list width (capped), measured once per resize.
  useLayoutEffect(() => {
    if (mode !== "static") return undefined;
    const list = stepsRef.current;
    if (!list) return undefined;
    const measure = () => {
      const figure = list.querySelector(".how-story-still");
      if (!figure) return;
      const k = Math.min((figure.clientWidth - 19) / HOW_STORY_CANVAS.width, STATIC_STILL_MAX_K);
      list.style.setProperty("--how-k", String(Math.max(0.5, Math.floor(k * 1000) / 1000)));
    };
    measure();
    if (typeof ResizeObserver === "undefined") return undefined;
    const observer = new ResizeObserver(measure);
    observer.observe(list);
    return () => {
      observer.disconnect();
      list.style.removeProperty("--how-k");
    };
  }, [mode]);

  return (
    <>
      <a className="how-story-skip" href="#how-end">{HOW_STORY_TEXT.skip}</a>
      <div className="how-story-track" ref={trackRef} data-mode={mode}>
        <div className="how-story-frame" ref={frameRef}>
          <div className="how-story-copy">
            <ol className="how-story-steps" ref={stepsRef}>
              {HOW_STORY_SCENES.map((scene, index) => (
                <li className="how-story-step" key={scene.id} data-active={mode === "scroll" && index === sceneIndex ? "true" : undefined}>
                  <span className="how-story-step-no" aria-hidden="true">{index + 1}/{HOW_STORY_SCENE_COUNT}</span>
                  <h3>{scene.headline}</h3>
                  <p className="how-story-support">{scene.support}</p>
                  <p className="sr-only">{descriptions[index]}</p>
                  {mode === "static" && (
                    <figure className="how-story-still" aria-hidden="true">
                      <HowStoryStage frame={stillFrames[index]} plan={plan} />
                    </figure>
                  )}
                </li>
              ))}
            </ol>
            {mode === "scroll" && <HowStoryProgress frame={firstFrame} />}
          </div>
          {mode === "scroll" && (
            <div className="how-story-stage-wrap" ref={stageWrapRef}>
              <HowStoryStage frame={firstFrame} plan={plan} />
              {notes.map((note) => <p className="how-story-note" key={note}>{note}</p>)}
            </div>
          )}
        </div>
        {mode === "static" && notes.map((note) => <p className="how-story-note" key={note}>{note}</p>)}
      </div>
    </>
  );
}
