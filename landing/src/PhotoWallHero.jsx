import { useCallback, useEffect, useRef, useState } from "react";
import { ANALYTICS_EVENTS, trackEvent } from "./analytics.js";
import { mountHeroWall } from "./heroWallEngine.js";
import { HERO_AUTO_PICK, HERO_AUTO_PICK_MS, HERO_COPY, HERO_PLACES, heroPickedCopy } from "./photoStory.js";
import { prefersReducedMotion } from "./storyCanvas.js";

// The first screen (2026-10-18, sketch v11): an example camera roll as a wall of photos, a lens that shows where each
// photo was taken, and 제주 / 일본 / 미국 chips that keep only that place. If nobody touches the wall, 제주 is picked
// once after 4.5 s (not measured). Chip taps send hero_demo_select. The wall itself is a canvas (src/heroWallEngine.js).
function PhotoWallHero({ storeActions }) {
  const heroRef = useRef(null);
  const canvasRef = useRef(null);
  const engineRef = useRef(null);
  const pickRef = useRef(null);
  const touchedRef = useRef(false);
  const [pick, setPick] = useState(null);

  useEffect(() => {
    const engine = mountHeroWall(heroRef.current, canvasRef.current, { reducedMotion: prefersReducedMotion() });
    engineRef.current = engine;
    engine.setPick(pickRef.current);
    return () => { engine.destroy(); engineRef.current = null; };
  }, []);

  const choose = useCallback((key, source) => {
    if (source === "chip") touchedRef.current = true;
    const next = pickRef.current === key ? null : key;
    pickRef.current = next;
    setPick(next);
    engineRef.current?.setPick(next);
    if (next && source === "chip") trackEvent(ANALYTICS_EVENTS.HERO_DEMO_SELECT, { experience_type: "hero_demo", demo_place: next });
  }, []);

  useEffect(() => {
    const hero = heroRef.current;
    const onPointerDown = () => { touchedRef.current = true; };
    hero.addEventListener("pointerdown", onPointerDown, { once: true });
    const timer = window.setTimeout(() => {
      if (touchedRef.current || pickRef.current || hero.getBoundingClientRect().top <= -50) return;
      choose(HERO_AUTO_PICK, "autoplay");
    }, HERO_AUTO_PICK_MS);
    return () => { window.clearTimeout(timer); hero.removeEventListener("pointerdown", onPointerDown); };
  }, [choose]);

  const picked = HERO_PLACES.find(({ key }) => key === pick);
  const copy = picked ? heroPickedCopy(picked) : HERO_COPY;

  return (
    <section className="wall-hero" id="hero" ref={heroRef} aria-labelledby="hero-title">
      <canvas className="wall-hero-canvas" ref={canvasRef} aria-hidden="true" />
      <div className="wall-hero-copy">
        <span className="wall-hero-kicker" aria-live="polite">{copy.kicker}</span>
        <h1 id="hero-title">{HERO_COPY.title}<span>{copy.sub}</span></h1>
        <div className="wall-hero-chips" role="group" aria-label="장소 고르기">
          {HERO_PLACES.map(({ key, name, count }) => (
            <button key={key} type="button" aria-pressed={pick === key} onClick={() => choose(key, "chip")}>
              {name}<small>{count}</small>
            </button>
          ))}
        </div>
        <p className="wall-hero-diff">{HERO_COPY.diff}</p>
        {/* the store buttons sit right under the promise (2026-10-29 review: at the bottom they floated under an empty third) */}
        <div className="wall-hero-actions">{storeActions}</div>
        {pick && <a className="wall-hero-ask" href="#how">{HERO_COPY.ask}</a>}
      </div>
      <div className="wall-hero-foot">
        <span>{HERO_COPY.made}</span>
      </div>
    </section>
  );
}

export { PhotoWallHero };
