import { memo, useEffect, useRef } from "react";
import { ANALYTICS_EVENTS, trackEvent } from "./analytics.js";
import { createPassiveViewTracker, createSceneReachTracker } from "./howStoryAnalytics.js";
import { STORY_TRACKS } from "./photoStory.js";
import { mountPhotoStory } from "./photoStoryController.js";

const EXPERIENCE_TYPE = "how_story";

// The #how section's content (2026-10-18, sketch v11): #roll (would it work on my camera roll?) then #rec (how a trip
// is kept), six beats in all. React renders the words once as real headings; the controller pins them to the scroll
// and the canvas engines draw the phone and the globe. App.jsx renders this inside section#how (aria-labelledby="how-title").
function PhotoStoryInner() {
  const rootRef = useRef(null);

  useEffect(() => {
    const root = rootRef.current;
    if (!root) return undefined;
    // how_story_view: once, after the stories have been at least half in view (tab visible) for 1 s.
    // how_story_scene: once per beat (1-6), after it has rested on screen for 700 ms; the view always goes first.
    let state = { mode: "scroll", inView: false, scene: 0, atRest: false };
    let story = null;
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
          story?.sample();
          check();
        });
      },
    });
    const sync = () => {
      const eligible = state.inView && document.visibilityState !== "hidden";
      view.update(eligible);
      reach.update({ sceneIndex: state.scene, atRest: state.atRest, eligible });
    };
    const destroy = mountPhotoStory(root, { onState: (next) => { state = next; sync(); } });
    story = destroy;
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
    <div className="ps" ref={rootRef}>
      <h2 className="sr-only" id="how-title">사용 방법</h2>
      {STORY_TRACKS.map(({ key, label, beats }) => (
        <section className={`ps-track ps-${key}`} id={key} data-story-track={key} aria-label={label} key={key}>
          <div className="ps-stage" data-story-stage="">
            <canvas className="ps-canvas" aria-hidden="true" />
            <div className="ps-copy" data-story-copy="">
              {beats.map(({ title, body, note, footnote, short, shortNote }, index) => (
                <div className={`ps-beat${index === 0 ? " is-active" : ""}`} data-story-step="" key={title.join(" ")}>
                  <h3>{title[0]}<br /><em>{title[1]}</em>{footnote && <sup className="ps-fn">*</sup>}</h3>
                  {body.map((text) => <p className="ps-long" key={text}>{text}</p>)}
                  {note && <p className="ps-note ps-long">{note}</p>}
                  {short && <p className="ps-short">{short}</p>}
                  {shortNote && <p className="ps-note ps-short">{shortNote}</p>}
                </div>
              ))}
            </div>
          </div>
        </section>
      ))}
    </div>
  );
}

// Never re-rendered by its parent (a theme switch included): the controller owns the classes it sets inside.
export const PhotoStory = memo(PhotoStoryInner);
