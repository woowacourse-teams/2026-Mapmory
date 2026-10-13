import { memo, useEffect, useRef } from "react";
import { ANALYTICS_EVENTS, trackEvent } from "./analytics.js";
import lqip from "./data/how-photo-story-lqip.json";
import { COPY, TITLE } from "./howPhotoStory.js";
import { createPassiveViewTracker, createSceneReachTracker } from "./howStoryAnalytics.js";
import { mountHowPhotoStory } from "./howPhotoStoryEngine.js";

const EXPERIENCE_TYPE = "how_story";

// The #how section's content (sketch A). React renders the static skeleton once (the heading, the pinned film's
// track/frame/copy/stage, the screen-reader list, the static box); the engine builds the stages inside it and follows
// the page scroll. App.jsx renders this inside section#how (aria-labelledby="how-title").
function HowPhotoStoryInner() {
  const rootRef = useRef(null);

  useEffect(() => {
    const root = rootRef.current;
    if (!root) return undefined;
    // how_story_view: once, after the section has been at least half in view (tab visible) for 1 s.
    // how_story_scene: once per scene, after it has rested on screen for 700 ms; the view always goes first.
    let state = { mode: "scroll", inView: false, scene: 0, atRest: false };
    const view = createPassiveViewTracker({
      onView: () => trackEvent(ANALYTICS_EVENTS.HOW_STORY_VIEW, { experience_type: EXPERIENCE_TYPE, story_mode: state.mode }),
    });
    const reach = createSceneReachTracker({
      onReach: (index) => {
        view.flush();
        trackEvent(ANALYTICS_EVENTS.HOW_STORY_SCENE, { experience_type: EXPERIENCE_TYPE, scene_index: index });
      },
    });
    const sync = () => {
      const eligible = state.inView && document.visibilityState !== "hidden";
      view.update(eligible);
      reach.update({ sceneIndex: state.scene, atRest: state.atRest, eligible });
    };
    const destroy = mountHowPhotoStory(root, { lqip, onState: (next) => { state = next; sync(); } });
    document.addEventListener("visibilitychange", sync);
    return () => {
      document.removeEventListener("visibilitychange", sync);
      destroy();
      view.stop();
      reach.stop();
    };
  }, []);

  return (
    <div className="hw" ref={rootRef}>
      <div className="hw-head">
        <p className="hw-eyebrow">{TITLE[0]}</p>
        <h2 id="how-title"><span>{TITLE[1]}</span> <span>{TITLE[2]}</span></h2>
      </div>
      <div className="hw-track" data-how-track="">
        <div className="hw-frame" data-how-frame="">
          <div className="hw-grid">
            <div className="hw-copy" aria-hidden="true" />
            <div className="hw-stage" aria-hidden="true" />
          </div>
        </div>
        {/* one clean text version for screen readers in the pinned film (the visible copy above is aria-hidden) */}
        <ol className="hw-sr">
          {COPY.map(([eyebrow, line1, line2, sub]) => <li key={eyebrow}><h3>{line1} {line2}</h3><p>{sub}</p></li>)}
        </ol>
      </div>
      <div className="hw-static" />
    </div>
  );
}

// Never re-rendered by its parent (a theme switch included): the engine owns the nodes it builds inside.
export const HowPhotoStory = memo(HowPhotoStoryInner);
