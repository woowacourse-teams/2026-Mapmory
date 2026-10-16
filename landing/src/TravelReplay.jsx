import { memo, useEffect, useRef } from "react";
import { ANALYTICS_EVENTS, trackEvent } from "./analytics.js";
import { createPassiveViewTracker, createSceneReachTracker } from "./howStoryAnalytics.js";
import { BEATS, END_CARD, REPLAY_PLACES, REPLAY_TOTAL, replayPhoto } from "./travelReplay.js";
import { mountTravelReplay } from "./travelReplayEngine.js";

const EXPERIENCE_TYPE = "how_story";

const LOCK_PATH = "M4.5 7V5a3.5 3.5 0 0 1 7 0v2h.5a1 1 0 0 1 1 1v6a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V8a1 1 0 0 1 1-1h.5Zm1.5 0h4V5a2 2 0 1 0-4 0v2Z";
const SHIELD_PATH = "M8 1.2 2.5 3.4v4.1c0 3.3 2.3 6.1 5.5 7.3 3.2-1.2 5.5-4 5.5-7.3V3.4L8 1.2Zm-1 9.6L4.6 8.4l1-1L7 8.8l3.4-3.4 1 1L7 10.8Z";

function Eyebrow({ text }) {
  const numbered = /^(\d\d) (.+)$/.exec(text);
  return <span className="tr-eyebrow">{numbered ? <><b>{numbered[1]}</b>{numbered[2]}</> : text}</span>;
}

// The #how section's content: the travel replay. React renders the skeleton once; the engine builds the photos and
// markers inside it and follows the page scroll. App.jsx renders this inside section#how (aria-labelledby="how-title")
// and hands in the end card's store buttons.
function TravelReplayInner({ storeActions }) {
  const rootRef = useRef(null);

  useEffect(() => {
    const root = rootRef.current;
    if (!root) return undefined;
    // how_story_view: once, after the replay has been at least half in view (tab visible) for 1 s.
    // how_story_scene: once per caption (1-5), after it has rested on screen for 700 ms; the view always goes first.
    let state = { mode: "scroll", inView: false, scene: 0, atRest: false };
    let engine = null;
    let confirmFrame = 0;
    const view = createPassiveViewTracker({
      onView: () => trackEvent(ANALYTICS_EVENTS.HOW_STORY_VIEW, { experience_type: EXPERIENCE_TYPE, story_mode: state.mode }),
    });
    const reach = createSceneReachTracker({
      onReach: (index) => {
        view.flush();
        trackEvent(ANALYTICS_EVENTS.HOW_STORY_SCENE, { experience_type: EXPERIENCE_TYPE, scene_index: index });
      },
      // a scene counts only if it is still at rest on a fresh frame (a dwell timer held up by a long task can fire
      // after a fling has already left the scene)
      confirm: (check) => {
        cancelAnimationFrame(confirmFrame);
        confirmFrame = requestAnimationFrame(() => {
          confirmFrame = 0;
          engine?.sample();
          check();
        });
      },
    });
    const sync = () => {
      const eligible = state.inView && document.visibilityState !== "hidden";
      view.update(eligible);
      reach.update({ sceneIndex: state.scene, atRest: state.atRest, eligible });
    };
    const destroy = mountTravelReplay(root, { onState: (next) => { state = next; sync(); } });
    engine = destroy;
    document.addEventListener("visibilitychange", sync);
    return () => {
      document.removeEventListener("visibilitychange", sync);
      cancelAnimationFrame(confirmFrame);
      destroy();
      view.stop();
      reach.stop();
    };
  }, []);

  return (
    <div className="tr" ref={rootRef}>
      <h2 className="tr-title" id="how-title">사용 방법</h2>
      {/* the five captions as real text: read by screen readers in the film, shown as a list in the static version */}
      <ol className="tr-steps">
        {BEATS.map(([eyebrow, title, sub]) => (
          <li key={title} data-tr-step="">
            <Eyebrow text={eyebrow} />
            <h3>{title}</h3>
            <p>{sub}</p>
          </li>
        ))}
      </ol>
      <div className="tr-track" data-tr-track="">
        <div className="tr-stage" data-tr-stage="">
          <canvas className="tr-globe" data-tr-globe="" aria-hidden="true" />
          <div className="tr-layer" data-tr-markers="" aria-hidden="true" />
          <div className="tr-phone" data-tr-phone="" aria-hidden="true">
            <canvas className="tr-mosaic" data-tr-mosaic="" />
            <span className="tr-chip"><svg width="12" height="12" viewBox="0 0 16 16"><path fill="currentColor" d={LOCK_PATH} /></svg><span data-tr-chip="">내 폰 안</span></span>
          </div>
          <div className="tr-layer" data-tr-cards="" aria-hidden="true" />
          <div className="tr-caption" aria-hidden="true">
            {BEATS.map(([eyebrow, title, sub], index) => (
              <div className={`tr-beat${index === 0 ? " is-active" : ""}`} data-tr-beat="" key={title}>
                <Eyebrow text={eyebrow} />
                <p className="tr-beat-title">{title}</p>
                <p className="tr-beat-sub">{sub}</p>
              </div>
            ))}
          </div>
          <div className="tr-tally is-off" data-tr-tally="" aria-hidden="true">
            <div className="tr-tally-dots">
              {REPLAY_PLACES.map(({ key, cover }) => <span key={key} data-tr-dot=""><img alt="" src={replayPhoto(cover)} loading="lazy" decoding="async" /></span>)}
            </div>
            <div className="tr-tally-nums"><span><b data-tr-places="">0</b><small>곳</small></span><span><b data-tr-photos="">0</b><small>장</small></span></div>
          </div>
          <div className="tr-endcard" data-tr-endcard="">
            <p className="tr-end-stats"><span>{REPLAY_PLACES.length}<small>곳</small></span><span>{REPLAY_TOTAL}<small>장</small></span></p>
            <ul className="tr-end-rows">
              {REPLAY_PLACES.map(({ key, name, count, cover }) => (
                <li key={key}><img alt="" src={replayPhoto(cover)} loading="lazy" decoding="async" /><span>{name}<b>{count}장</b></span></li>
              ))}
            </ul>
            {storeActions}
            <p className="tr-end-trust"><svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true"><path fill="currentColor" d={SHIELD_PATH} /></svg>{END_CARD.trust}</p>
            <p className="tr-end-demo">{END_CARD.demo}</p>
          </div>
        </div>
      </div>
    </div>
  );
}

// Never re-rendered by its parent (a theme switch included): the engine owns the nodes it builds inside.
export const TravelReplay = memo(TravelReplayInner);
