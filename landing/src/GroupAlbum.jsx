import { useEffect, useRef } from "react";
import { ANALYTICS_EVENTS, trackEvent } from "./analytics.js";
import "./group-album.css";

// 2026-11-09 (티온 picked "그룹 앨범만"): one scroll-driven scene after the tech story. Three friends' photos fly into one
// shared album and each lands with its uploader's name, like the app's group album (client/docs/design/group-album-ui-plan.md:
// the album gathers each member's own records and names who uploaded every photo). Scroll position is the playhead; no
// timers or taps. Names and dates are examples; the photos are the team's own US trip photos.
const PEOPLE = [
  { name: "성우", photos: [0, 1] },
  { name: "민지", photos: [2, 3] },
  { name: "지우", photos: [4] },
];
const PHOTOS = [
  "/assets/team-usa-antelope-canyon.webp",
  "/assets/team-usa-bryce-canyon.webp",
  "/assets/team-usa-las-vegas-day.webp",
  "/assets/team-usa-las-vegas-fountain.webp",
  "/assets/team-usa-las-vegas-venetian.webp",
];
const ORDER = [0, 2, 4, 1, 3];
const ownerOf = (index) => PEOPLE.find((person) => person.photos.includes(index)).name;
const easeOut = (t) => 1 - Math.pow(1 - t, 3);

export function GroupAlbum() {
  const trackRef = useRef(null);
  const sceneRef = useRef(null);

  useEffect(() => {
    const track = trackRef.current;
    const scene = sceneRef.current;
    if (!track || !scene) return undefined;
    const sources = [...scene.querySelectorAll("[data-src]")];
    const targets = [...scene.querySelectorAll("[data-dst]")];
    const flyers = [...scene.querySelectorAll("[data-fly]")];
    const minis = [...scene.querySelectorAll("[data-mini]")];
    const who = scene.querySelector("[data-who]");
    const reduce = window.matchMedia("(prefers-reduced-motion: reduce)");
    let boxes = [];
    let frame = 0;

    const measure = () => {
      const origin = scene.getBoundingClientRect();
      boxes = flyers.map((_, i) => {
        const a = sources[i].getBoundingClientRect();
        const b = targets[i].getBoundingClientRect();
        return { ax: a.left - origin.left, ay: a.top - origin.top, as: a.width, bx: b.left - origin.left, by: b.top - origin.top, bs: b.width };
      });
      draw();
    };

    const progress = () => {
      const rect = track.getBoundingClientRect();
      const room = rect.height - window.innerHeight;
      return room > 0 ? Math.min(1, Math.max(0, -rect.top / room)) : 1;
    };

    function draw() {
      frame = 0;
      const p = reduce.matches ? 1 : progress();
      ORDER.forEach((i, k) => {
        const box = boxes[i];
        if (!box) return;
        const t = easeOut(Math.min(1, Math.max(0, (p - (0.1 + k * 0.09)) / 0.22)));
        const x = box.ax + (box.bx - box.ax) * t;
        const y = box.ay + (box.by - box.ay) * t;
        const size = box.as + (box.bs - box.as) * t;
        const el = flyers[i];
        el.style.width = `${box.bs}px`;
        el.style.height = `${box.bs}px`;
        el.style.transform = `translate(${x}px, ${y}px) scale(${size / box.bs})`;
        el.classList.toggle("is-in", t >= 1);
      });
      const landed = PEOPLE.filter((person) => person.photos.some((i) => flyers[i].classList.contains("is-in")));
      PEOPLE.forEach((person, j) => {
        const left = person.photos.some((i) => !flyers[i].classList.contains("is-in"));
        minis[j].classList.toggle("is-empty", !left);
      });
      who.textContent = landed.length ? `함께 올린 사람 · ${landed.map((person) => person.name).join(", ")}` : "함께 올린 사람";
    }

    const schedule = () => { if (!frame) frame = window.requestAnimationFrame(draw); };
    const resize = new ResizeObserver(measure);
    resize.observe(scene);
    window.addEventListener("scroll", schedule, { passive: true });
    reduce.addEventListener?.("change", measure);
    measure();
    // experience_view (experience_type group_album): once, after the scene has been at least half on screen for 1 s
    let seenTimer = 0;
    const seen = new IntersectionObserver(([entry]) => {
      window.clearTimeout(seenTimer);
      if (!entry.isIntersecting) return;
      seenTimer = window.setTimeout(() => {
        if (document.visibilityState !== "visible") return;
        trackEvent(ANALYTICS_EVENTS.EXPERIENCE_VIEW, { experience_type: "group_album" });
        seen.disconnect();
      }, 1000);
    }, { threshold: 0.5 });
    seen.observe(scene);
    return () => {
      seen.disconnect();
      window.clearTimeout(seenTimer);
      resize.disconnect();
      window.removeEventListener("scroll", schedule);
      reduce.removeEventListener?.("change", measure);
      if (frame) window.cancelAnimationFrame(frame);
    };
  }, []);

  return (
    <section className="ga" id="group" aria-labelledby="group-title" ref={trackRef}>
      <div className="ga-stage">
        <div className="ga-copy">
          <p className="ga-label">함께 모으기</p>
          <h2 id="group-title">친구와 다녀온 여행은<br />한 앨범에 모여요</h2>
        </div>
        <div className="ga-scene" ref={sceneRef} aria-hidden="true">
          <div className="ga-friends">
            {PEOPLE.map((person) => (
              <div className="ga-mini" data-mini key={person.name}>
                <b>{person.name}</b>
                <div className="ga-mini-slots">{person.photos.map((i) => <span key={i} data-src data-i={i} className="ga-slot" />)}</div>
              </div>
            ))}
          </div>
          <div className="ga-phone">
            <div className="ga-screen">
              <span className="ga-notch" />
              <span className="ga-bar">그룹 앨범</span>
              <span className="ga-meta">라스베이거스 · 2025.07.14 – 07.19</span>
              <span className="ga-title">미국에서 함께한 여행</span>
              <span className="ga-who" data-who>함께 올린 사람</span>
              <div className="ga-grid">{PHOTOS.map((_, i) => <span key={i} data-dst className="ga-slot" />)}</div>
            </div>
          </div>
          {PHOTOS.map((src, i) => (
            <span className="ga-photo" data-fly key={src} style={{ backgroundImage: `url(${src})` }}><i>{ownerOf(i)}</i></span>
          ))}
        </div>
      </div>
    </section>
  );
}
