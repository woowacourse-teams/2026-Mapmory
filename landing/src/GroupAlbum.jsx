import { useEffect, useRef } from "react";
import { ANALYTICS_EVENTS, trackEvent } from "./analytics.js";
import { createPassiveViewTracker } from "./howStoryAnalytics.js";
import "./group-album.css";

// 2026-11-09 (티온 picked "그룹 앨범만"): one scroll-driven scene after the tech story. Three friends' photos fly into one
// shared album and each lands with its uploader's name, like the app's group album (backend ADR 0022: an album gathers
// each member's own records and shows who added every photo). Scroll position is the playhead; no timers or taps.
// Names and dates are examples; the photos are small crops of the team's own US trip photos (public/assets/group/,
// 256px, loaded only when the scene comes near).
const PEOPLE = [
  { name: "성우", photos: [0, 1] },
  { name: "민지", photos: [2, 3] },
  { name: "지우", photos: [4] },
];
const PHOTOS = [
  "/assets/group/usa-antelope-canyon.webp",
  "/assets/group/usa-bryce-canyon.webp",
  "/assets/group/usa-las-vegas-day.webp",
  "/assets/group/usa-las-vegas-fountain.webp",
  "/assets/group/usa-las-vegas-venetian.webp",
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
    const screen = scene.querySelector("[data-screen]");
    const who = scene.querySelector("[data-who]");
    const reduce = window.matchMedia("(prefers-reduced-motion: reduce)");
    const short = window.matchMedia("(max-height: 499px)"); // landscape phones: not pinned, the finished album
    let boxes = [];
    let frame = 0;
    let onScreen = false;
    let half = false;
    let whoText = "";
    let cancelled = false;

    const progress = () => {
      const rect = track.getBoundingClientRect();
      const room = rect.height - window.innerHeight;
      // no room to scroll (reduced motion, landscape phones: the track is not pinned) shows the finished album
      return room > 0 ? Math.min(1, Math.max(0, -rect.top / room)) : 1;
    };

    function draw() {
      frame = 0;
      const p = reduce.matches || short.matches ? 1 : progress();
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
      const next = landed.length ? `함께 올린 사람 · ${landed.map((person) => person.name).join(", ")}` : "함께 올린 사람";
      if (next !== whoText) { whoText = next; who.textContent = next; }
    }

    // where each photo starts (its friend's slot) and lands (its album slot), relative to the scene
    function measure() {
      const origin = scene.getBoundingClientRect();
      boxes = flyers.map((_, i) => {
        const a = sources[i].getBoundingClientRect();
        const b = targets[i].getBoundingClientRect();
        return { ax: a.left - origin.left, ay: a.top - origin.top, as: a.width, bx: b.left - origin.left, by: b.top - origin.top, bs: b.width };
      });
      draw();
    }

    const schedule = () => { if (onScreen && !frame) frame = window.requestAnimationFrame(draw); };
    // the slots move when the scene or the phone screen changes size, when the window does (the album grid goes from
    // 2 to 3 columns under 760px tall without either box changing), and when the web font arrives
    const resize = new ResizeObserver(measure);
    resize.observe(scene);
    resize.observe(screen);
    document.fonts?.ready.then(() => { if (!cancelled) measure(); });
    reduce.addEventListener?.("change", measure);
    short.addEventListener?.("change", measure);
    window.addEventListener("resize", measure);

    // the photos load and the scroll is followed only while the track is near or on screen
    const near = new IntersectionObserver(([entry]) => {
      onScreen = entry.isIntersecting;
      if (!onScreen) return;
      flyers.forEach((el, i) => { if (!el.style.backgroundImage) el.style.backgroundImage = `url(${PHOTOS[i]})`; });
      measure();
    }, { rootMargin: "100% 0px" });
    near.observe(track);
    window.addEventListener("scroll", schedule, { passive: true });

    // group_album_view: once, after the scene has lain across the middle of the window in a visible tab for 1 s. That
    // means at least half of the scene is on screen, or half of the window when the scene is the taller one (unpinned
    // on a phone on its side, where a share of the scene can never be reached).
    const view = createPassiveViewTracker({ onView: () => trackEvent(ANALYTICS_EVENTS.GROUP_ALBUM_VIEW, { experience_type: "group_album" }) });
    const syncView = () => view.update(half && document.visibilityState === "visible");
    const seen = new IntersectionObserver(([entry]) => { half = entry.isIntersecting; syncView(); }, { rootMargin: "-50% 0px -50% 0px" });
    seen.observe(scene);
    document.addEventListener("visibilitychange", syncView);

    measure();
    return () => {
      cancelled = true;
      resize.disconnect();
      near.disconnect();
      seen.disconnect();
      view.stop();
      document.removeEventListener("visibilitychange", syncView);
      window.removeEventListener("scroll", schedule);
      reduce.removeEventListener?.("change", measure);
      short.removeEventListener?.("change", measure);
      window.removeEventListener("resize", measure);
      if (frame) window.cancelAnimationFrame(frame);
    };
  }, []);

  return (
    <section className="ga" id="group" aria-labelledby="group-title" ref={trackRef}>
      <div className="ga-stage">
        <div className="ga-copy">
          <p className="ga-label">함께 모으기</p>
          <h2 id="group-title">친구와 다녀온 여행은<br /><span className="title-soft">한 앨범에 모여요</span></h2>
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
            <div className="ga-screen" data-screen>
              <span className="ga-notch" />
              <span className="ga-bar">그룹 앨범</span>
              <span className="ga-meta">라스베이거스 · 2025.07.14 – 07.19</span>
              <span className="ga-title">미국에서 함께한 여행</span>
              <span className="ga-who" data-who>함께 올린 사람</span>
              <div className="ga-grid">{PHOTOS.map((src) => <span key={src} data-dst className="ga-slot" />)}</div>
            </div>
          </div>
          {PHOTOS.map((src, i) => (
            <span className="ga-photo" data-fly key={src}><i>{ownerOf(i)}</i></span>
          ))}
        </div>
      </div>
    </section>
  );
}
