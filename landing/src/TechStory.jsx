import { memo, useEffect, useRef } from "react";
import { ANALYTICS_EVENTS, trackEvent } from "./analytics.js";
import { createPassiveViewTracker, createSceneReachTracker } from "./howStoryAnalytics.js";
import { TECH_COPY, TECH_SHARE, TECH_STEPS, techScenes } from "./techStory.js";

const EXPERIENCE_TYPE = "tech_story";
const PIN = "M12 2a7 7 0 0 0-7 7c0 5 7 13 7 13s7-8 7-13a7 7 0 0 0-7-7Zm0 9.5A2.5 2.5 0 1 1 12 6.5a2.5 2.5 0 0 1 0 5Z";
const Pin = () => <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d={PIN} /></svg>;
const stepLabel = (key) => TECH_STEPS.find((step) => step.key === key).label;

// Wobbling hand-written step label: one span per letter, each on its own beat of the boil animation.
function StepLabel({ text }) {
  return (
    <p className="ts-label" aria-label={text}>
      {[...text].map((ch, i) => <span aria-hidden="true" style={{ "--i": i }} key={i}>{ch}</span>)}
    </p>
  );
}

function ShareScreen({ hint }) {
  const { media, button } = TECH_SHARE;
  if (media) {
    return (
      <div className="ts-screen is-dark is-media" data-scene="">
        {media.type === "video"
          ? <video className="ts-media" src={media.src} poster={media.poster} muted loop playsInline preload="none" data-share-video="" aria-label="타임라인 공유 영상 예시" />
          : <img className="ts-media" src={media.src} alt="타임라인 공유 영상 예시" loading="lazy" decoding="async" />}
      </div>
    );
  }
  return (
    <div className="ts-screen is-dark" data-scene="">
      <p className="ts-hint">{hint}</p>
      <canvas className="ts-globe" data-tl-globe="" aria-label="다녀온 나라를 날짜순으로 도는 지구본" />
      <div className="ts-tl-label" data-tl-label="" aria-live="polite"><b>{TECH_SHARE.stops[0].name}</b><span>{TECH_SHARE.stops[0].date}</span></div>
      <div className="ts-years" data-tl-years="" role="group" aria-label="연도">
        {TECH_SHARE.stops.map((stop, i) => <button type="button" data-i={i} key={stop.date}>{stop.date.slice(0, 4)}</button>)}
      </div>
      <div className="ts-share-btn" data-share-btn="" aria-hidden="true">{button}</div>
    </div>
  );
}

// The #tech section (see src/techStory.js). React renders the words and the phone's fixed parts once; the engine
// (src/techStoryEngine.js, a lazy chunk with d3-geo, the 110m land and the Korea map) fills the screens, follows the
// page scroll (never steers it) and reports scenes and first tries for analytics.
function TechStoryInner() {
  const rootRef = useRef(null);
  const scenes = techScenes();

  useEffect(() => {
    const root = rootRef.current;
    if (!root) return undefined;
    let destroy = null;
    let cancelled = false;
    let state = { inView: false, scene: 0 };
    const tried = new Set();
    const view = createPassiveViewTracker({ onView: () => trackEvent(ANALYTICS_EVENTS.TECH_STORY_VIEW, { experience_type: EXPERIENCE_TYPE }) });
    let confirmFrame = 0;
    const reach = createSceneReachTracker({
      onReach: (index) => {
        view.flush();
        trackEvent(ANALYTICS_EVENTS.TECH_STORY_SCENE, { experience_type: EXPERIENCE_TYPE, scene_index: index });
      },
      // count a scene only if it is still on screen a frame later: the engine's scroll frame runs first and reports
      // a scene a fling has already left (as #how does)
      confirm: (check) => {
        cancelAnimationFrame(confirmFrame);
        confirmFrame = requestAnimationFrame(() => { confirmFrame = 0; check(); });
      },
    });
    const sync = () => {
      const eligible = state.inView && document.visibilityState !== "hidden";
      view.update(eligible);
      reach.update({ sceneIndex: state.scene, atRest: true, eligible });
    };
    const onInteract = (sceneIndex, interactionType) => {
      if (tried.has(interactionType)) return;
      tried.add(interactionType);
      view.flush();
      trackEvent(ANALYTICS_EVENTS.TECH_STORY_INTERACT, { experience_type: EXPERIENCE_TYPE, scene_index: sceneIndex, interaction_type: interactionType });
    };
    const inView = new IntersectionObserver(([entry]) => { state = { ...state, inView: entry.isIntersecting }; sync(); }, { threshold: 0.35 });
    inView.observe(root.querySelector("[data-tech-stage]"));
    // load the engine when the section is within a viewport
    const near = new IntersectionObserver(([entry]) => {
      if (!entry.isIntersecting) return;
      near.disconnect();
      import("./techStoryEngine.js").then(({ mountTechStory }) => {
        if (cancelled) return;
        destroy = mountTechStory(root, {
          share: TECH_SHARE,
          onScene: (index) => { state = { ...state, scene: index }; sync(); },
          onInteract,
        });
      }).catch(() => {}); // the first screen's frame and the words stay as they are
    }, { rootMargin: "100% 0px" });
    near.observe(root);
    document.addEventListener("visibilitychange", sync);
    return () => {
      cancelled = true;
      near.disconnect();
      inView.disconnect();
      document.removeEventListener("visibilitychange", sync);
      cancelAnimationFrame(confirmFrame);
      destroy?.();
      view.stop();
      reach.stop();
    };
  }, []);

  let lastStep = null;
  return (
    <div className="ts" ref={rootRef}>
      <header className="ts-head">
        <p className="ts-eyebrow">{TECH_COPY.eyebrow}</p>
        <h2 id="tech-title">{TECH_COPY.title[0]}<br />{TECH_COPY.title[1]}</h2>
      </header>

      <div className="ts-story">
        <div className="ts-stage" data-tech-stage="">
          <div className="ts-phone-wrap" data-phone-wrap="">
            <div className="ts-phone" data-phone="">
              <div className="ts-screen-box">
                <div className="ts-notch" aria-hidden="true" />

                <div className="ts-screen" data-scene="">
                  <p className="ts-hint" data-hint-flip="">{scenes[0].hint}</p>
                  <div className="ts-roll-view"><div className="ts-roll" data-roll="" /></div>
                </div>

                <div className="ts-screen" data-scene="">
                  <p className="ts-hint">{scenes[1].hint}</p>
                  <div className="ts-gather" data-gather="" />
                  <div className="ts-found" data-found="" role="status"><Pin /><span data-found-text="" /></div>
                  <div className="ts-counter" aria-hidden="true"><span className="ts-counter-place" data-count-place="">제주</span><span><span data-count-n="">0</span>장 · <span data-count-t="">0</span>초</span></div>
                </div>

                <div className="ts-screen" data-scene="">
                  <p className="ts-hint">{scenes[2].hint}</p>
                  <div className="ts-pick" data-pick="">
                    <div className="ts-rec-card"><Pin /><span data-rec-text="">제주 여행</span></div>
                  </div>
                  <button type="button" className="ts-save" data-save="">기록하기</button>
                </div>

                <div className="ts-screen" data-scene="">
                  <p className="ts-hint">{scenes[3].hint}</p>
                  <div className="ts-regions" data-regions="" role="button" tabIndex={0} aria-label="지도 칠하기, 다시 누르면 지우기" aria-pressed="true" />
                </div>

                <div className="ts-screen" data-scene="">
                  <p className="ts-hint">{scenes[4].hint}</p>
                  <canvas className="ts-globe" data-globe="" aria-label="다녀온 곳이 찍힌 지구본, 옆으로 끌어 돌려 보세요" />
                </div>

                {TECH_SHARE.enabled && <ShareScreen hint={scenes[5].hint} />}
              </div>
            </div>
          </div>
        </div>

        <div className="ts-beats">
          {scenes.map((scene, i) => {
            const bridge = lastStep !== null && lastStep !== scene.step;
            lastStep = scene.step;
            const side = TECH_STEPS.find((step) => step.key === scene.step).side;
            return [
              bridge && <div className="ts-bridge" data-bridge="" key={`bridge-${i}`} />,
              <article className={`ts-beat is-${side === "left" ? "right" : "left"}`} data-beat={i} key={scene.title}>
                <div className="ts-copy">
                  <StepLabel text={stepLabel(scene.step)} />
                  <h3>{scene.title}{scene.footnote && <sup className="ts-fn">*</sup>}</h3>
                </div>
              </article>,
            ];
          })}
        </div>
      </div>
      <p className="ts-footnote">{TECH_COPY.footnote}</p>
    </div>
  );
}

// Never re-rendered by its parent: the engine owns the classes and children it sets inside.
export const TechStory = memo(TechStoryInner);
