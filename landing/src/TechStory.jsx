import { memo, useEffect, useRef } from "react";
import { ANALYTICS_EVENTS, trackEvent } from "./analytics.js";
import { createPassiveViewTracker, createSceneReachTracker } from "./howStoryAnalytics.js";
import { TECH_COPY, TECH_PIN, TECH_SHARE, TECH_STEPS, TECH_VARIANT, TECH_VARIANTS, techScenes } from "./techStory.js";

const EXPERIENCE_TYPE = "tech_story";
const Pin = () => <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d={TECH_PIN} /></svg>;

// Hand-written step label: one span per letter (the boil animation they were for is gone since 10-29). Assistive
// tech reads the whole word once from the visually hidden span (aria-label is not allowed on a <p>, axe 5.5).
function StepLabel({ text }) {
  return (
    <p className="ts-label">
      <span className="sr-only">{text}</span>
      {[...text].map((ch, i) => <span className="ts-ch" aria-hidden="true" style={{ "--i": i }} key={i}>{ch}</span>)}
    </p>
  );
}

// The share screen: the app's real share video (TECH_SHARE.media) with its year buttons, or the dark screen alone.
// The scroll-driven trip lands the globe in this screen and the share button shows as pressed; the video never plays
// by itself, a tap on a year or on the video plays it.
function ShareScreen() {
  const { media, button } = TECH_SHARE;
  return (
    <div className={`ts-screen is-dark${media ? " is-media" : ""}`} data-scene="">
      {media && (
        <div className="ts-media-box" data-share-box="">
          {media.type === "video"
            ? (
              <video className="ts-media" poster={media.poster} muted loop playsInline preload="none" data-share-video="" tabIndex={0} aria-label="타임라인 공유 영상 예시, 누르면 재생하거나 멈춰요">
                {media.sources.map((source) => <source src={source.src} type={source.type} key={source.src} />)}
              </video>
            )
            : <img className="ts-media" src={media.src} alt={media.alt ?? "타임라인 공유 영상 예시"} loading="lazy" decoding="async" />}
        </div>
      )}
      {!media && <div className="ts-media-box is-empty" data-share-box="" aria-hidden="true" />}
      {media?.type === "video" && media.years?.length > 0 && (
        <div className="ts-years" data-share-years="" role="group" aria-label="연도부터 재생">
          {media.years.map((year, i) => <button type="button" data-at={year.at} aria-pressed={i === 0} key={year.label}>{year.label}</button>)}
        </div>
      )}
      <div className="ts-share-btn" data-share-btn="" aria-hidden="true">{button}</div>
    </div>
  );
}

// The #how section, the tech story (see src/techStory.js). React renders the words and the phone's fixed parts once; the engine
// (src/techStoryEngine.js, a lazy chunk with d3-geo, the 110m land and the Korea map) fills the screens, follows the
// page scroll (never steers it) and reports scenes and first tries for analytics.
function TechStoryInner() {
  const rootRef = useRef(null);
  const scenes = techScenes();

  useEffect(() => {
    const root = rootRef.current;
    if (!root) return undefined;
    // `?tsv=above` (or another key in TECH_VARIANTS) shows a layout variant for a side-by-side check (gates/run-variants.mjs).
    // Only on a dev server or a local preview: a shared production link must not open an unapproved layout.
    const local = import.meta.env.DEV || ["localhost", "127.0.0.1"].includes(window.location.hostname);
    const variant = local ? new URLSearchParams(window.location.search).get("tsv") : null;
    if (TECH_VARIANTS.includes(variant)) root.dataset.variant = variant;
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
      // count a scene only if it is still on screen a frame later: on that fresh frame the engine re-reads the scroll
      // position first (a fling during a long task may have left the scene before the timer fired), then check()
      confirm: (check) => {
        cancelAnimationFrame(confirmFrame);
        confirmFrame = requestAnimationFrame(() => { confirmFrame = 0; destroy?.sample?.(); check(); });
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
    <div className="ts" data-variant={TECH_VARIANT} ref={rootRef}>
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

                {/* scenes 1 and 2: the record form, one place in the field and that place's photos gathering under it */}
                <div className="ts-screen" data-scene="">
                  <button type="button" className="ts-field is-empty" data-field="" aria-label="위치 바꾸기"><Pin /><span data-field-text="" /></button>
                  <div className="ts-gather" data-gather="" />
                  <div className="ts-found" data-found="" role="status"><Pin /><span data-found-text="" /></div>
                </div>

                <div className="ts-screen" data-scene="">
                  <div className="ts-pick" data-pick="">
                    <div className="ts-rec-card"><Pin /><span data-rec-text="">제주 여행</span></div>
                  </div>
                  <button type="button" className="ts-save" data-save="">기록하기</button>
                </div>

                <div className="ts-screen" data-scene="">
                  <div className="ts-regions" data-regions="" role="button" tabIndex={0} aria-label="지도 칠하기, 다시 누르면 지우기" aria-pressed="true" />
                </div>

                <div className="ts-screen" data-scene="">
                  <canvas className="ts-globe" data-globe="" tabIndex={0} aria-label="다녀온 나라가 칠해진 지구본, 옆으로 끌거나 화살표 키로 돌려 보세요" />
                </div>

                {TECH_SHARE.enabled && <ShareScreen />}
              </div>
            </div>
          </div>

          {/* The words stay in one place and change with the scene, so a quick scroll never has to wait for them to
              pass by (티온, 10-27). Only the current scene's words show; all of them stay in the page for screen readers. */}
          <div className="ts-captions">
            {scenes.map((scene, i) => {
              const step = TECH_STEPS.find((s) => s.key === scene.step);
              return (
                <div className={`ts-copy is-${step.side === "left" ? "right" : "left"}`} data-caption={i} key={scene.title}>
                  <StepLabel text={step.label} />
                  <h3>{scene.title}</h3>
                </div>
              );
            })}
          </div>
        </div>

        {/* the scroll room: one spacer per scene (and one between the steps); the engine picks the scene from these */}
        <div className="ts-beats" aria-hidden="true">
          {scenes.map((scene, i) => {
            const bridge = lastStep !== null && lastStep !== scene.step;
            lastStep = scene.step;
            return [bridge && <div className="ts-bridge" data-bridge="" key={`bridge-${i}`} />, <div className="ts-beat" data-beat={i} key={scene.title} />];
          })}
        </div>
      </div>
    </div>
  );
}

// Never re-rendered by its parent: the engine owns the classes and children it sets inside.
export const TechStory = memo(TechStoryInner);
