import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { CheckCircle, MagnifyingGlass, MapPin, ShieldCheck } from "@phosphor-icons/react";
import koreaProvinces from "./data/korea-provinces.json";
import { ANALYTICS_EVENTS, trackEvent } from "./analytics.js";
import {
  PHOTO_FINDER_LIBRARY_COUNT,
  PHOTO_FINDER_LIBRARY_PHOTOS,
  PHOTO_FINDER_MATCH_COUNT,
  PHOTO_FINDER_PLACES,
  PHOTO_FINDER_REPLAY_TIMELINE,
  PHOTO_FINDER_TIMELINE,
  getPhotoFinderDuration,
  getPhotoFinderState,
} from "./photoFinderDemo.js";
import { loadWorldCountries, useWorldCountries } from "./worldCountries.js";

const GRID_COLUMNS = 7;
const GRID_ROWS = 16;
const VISIBLE_ROWS = 4;
const FINAL_WINDOW_START = (GRID_ROWS - VISIBLE_ROWS) * GRID_COLUMNS;
// Positions inside the final window where the searched place's photos sit: rows 2-3,
// so the first row stays free for the result pill.
const MATCH_SLOTS = [8, 11, 13, 16, 19];
// [x%, y%, zoom] crops so the rest of the library never repeats a shot next to itself.
const FILLER_CROPS = [[50, 50, 1], [30, 35, 1.35], [70, 65, 1.5], [50, 20, 1.25], [20, 80, 1.6], [80, 40, 1.4]];
const AUTOPLAY_MIN_WAIT_MS = 400;
const AUTOPLAY_MAX_WAIT_MS = 1800;
const REDUCED_MOTION_SETTLE_MS = 200;
const SOFT_RESET_KEYFRAMES = [{ opacity: 0.5, filter: "blur(4px)" }, { opacity: 1, filter: "blur(0px)" }];
const SOFT_RESET_OPTIONS = { duration: 200, easing: "cubic-bezier(0.23, 1, 0.32, 1)" };
const KOREA_BOUNDS = { minLng: 124.5, maxLng: 130.05, minLat: 33, maxLat: 38.75 };
const KOREA_LONGITUDE_SCALE = 0.81;
const numberFormat = new Intl.NumberFormat("ko-KR");

function prefersReducedMotion() {
  return typeof window !== "undefined" && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

function cropStyle([x, y, zoom]) {
  return {
    objectPosition: `${x}% ${y}%`,
    transformOrigin: `${x}% ${y}%`,
    transform: zoom === 1 ? undefined : `scale(${zoom})`,
  };
}

function ringsOf(geometry) {
  if (!geometry) return [];
  if (geometry.type === "Polygon") return geometry.coordinates;
  if (geometry.type === "MultiPolygon") return geometry.coordinates.flat();
  return [];
}

function ringPath(ring, project) {
  return `${ring.map(([lng, lat], index) => {
    const [x, y] = project(lng, lat);
    return `${index === 0 ? "M" : "L"}${x.toFixed(2)} ${y.toFixed(2)}`;
  }).join("")}Z`;
}

function useMapScene(place, worldCountries) {
  return useMemo(() => {
    if (place.scope === "korea") {
      // A place may crop the Korea view so a small province such as 제주 reads when filled.
      const bounds = place.view ?? KOREA_BOUNDS;
      const project = (lng, lat) => [
        (lng - bounds.minLng) * KOREA_LONGITUDE_SCALE * 100,
        (bounds.maxLat - lat) * 100,
      ];
      const width = (bounds.maxLng - bounds.minLng) * KOREA_LONGITUDE_SCALE * 100;
      const height = (bounds.maxLat - bounds.minLat) * 100;
      return {
        scopeLabel: "대한민국",
        viewBox: `-10 -10 ${width + 20} ${height + 20}`,
        target: project(...place.target),
        regions: koreaProvinces.map((province) => ({
          id: province.code,
          d: province.rings.map((ring) => ringPath(ring, project)).join(""),
        })),
      };
    }

    const { minLng, maxLng, minLat, maxLat } = place.view;
    const longitudeScale = Math.cos((((minLat + maxLat) / 2) * Math.PI) / 180);
    const project = (lng, lat) => [(lng - minLng) * longitudeScale * 10, (maxLat - lat) * 10];
    const width = (maxLng - minLng) * longitudeScale * 10;
    const height = (maxLat - minLat) * 10;
    const regions = worldCountries.map((country) => {
      const rings = ringsOf(country.geometry).filter((ring) => {
        const longitudes = ring.map(([lng]) => lng);
        const ringMin = Math.min(...longitudes);
        const ringMax = Math.max(...longitudes);
        // Skip antimeridian-spanning rings and anything far outside the view.
        return ringMax - ringMin < 180 && ringMax > minLng - 40 && ringMin < maxLng + 40;
      });
      return { id: String(country.id), d: rings.map((ring) => ringPath(ring, project)).join("") };
    }).filter(({ d }) => d.length > 0);
    return {
      scopeLabel: "전세계",
      viewBox: `0 0 ${width} ${height}`,
      target: project(...place.target),
      regions,
    };
  }, [place, worldCountries]);
}

function wait(ms, cleanups) {
  return new Promise((resolve) => {
    const timer = window.setTimeout(resolve, ms);
    cleanups.push(() => window.clearTimeout(timer));
  });
}

function PhotoFinderHero({ storeActions, onPlaySelect }) {
  const [placeKey, setPlaceKey] = useState(PHOTO_FINDER_PLACES[0].key);
  const [run, setRun] = useState({ id: 0, startedAt: null, timeline: PHOTO_FINDER_TIMELINE });
  const [isReducedMotion] = useState(prefersReducedMotion);
  const [demo, setDemo] = useState(() => (
    isReducedMotion
      ? getPhotoFinderState(Number.POSITIVE_INFINITY, PHOTO_FINDER_PLACES[0].query)
      : { phase: "idle", typedQuery: "", scannedCount: 0, isMapFilled: false }
  ));
  const [resultPlaceKey, setResultPlaceKey] = useState(PHOTO_FINDER_PLACES[0].key);
  const [flights, setFlights] = useState([]);
  const [pinPosition, setPinPosition] = useState(null);
  const mapRef = useRef(null);
  const cardRef = useRef(null);
  const gridWindowRef = useRef(null);
  const counterRef = useRef(null);
  const targetRef = useRef(null);
  const matchRefs = useRef([]);
  const hasPlayedRef = useRef(false);
  const flightsRunRef = useRef(-1);
  const settleTimerRef = useRef(null);
  const place = PHOTO_FINDER_PLACES.find(({ key }) => key === placeKey);
  const needsWorld = place.scope === "world" || demo.phase !== "idle";
  const worldCountries = useWorldCountries(needsWorld);
  const scene = useMapScene(place, worldCountries);

  const tiles = useMemo(() => {
    const pool = PHOTO_FINDER_LIBRARY_PHOTOS.filter((src) => !place.photos.includes(src));
    return Array.from({ length: GRID_COLUMNS * GRID_ROWS }, (_, index) => {
      const col = index % GRID_COLUMNS;
      const row = Math.floor(index / GRID_COLUMNS);
      const matchIndex = MATCH_SLOTS.indexOf(index - FINAL_WINDOW_START);
      if (matchIndex >= 0) {
        return { index, matchIndex, photo: place.photos[matchIndex % place.photos.length], crop: place.crops[matchIndex] };
      }
      // Steps of 1 across and 3 down keep neighbouring tiles on different photos for pools of 5 and 9.
      return { index, matchIndex: -1, photo: pool[(col + row * 3) % pool.length], crop: FILLER_CROPS[(col * 2 + row) % FILLER_CROPS.length] };
    });
  }, [place]);

  const play = useCallback((nextKey, source) => {
    const nextPlace = PHOTO_FINDER_PLACES.find(({ key }) => key === nextKey);
    hasPlayedRef.current = true;
    window.clearTimeout(settleTimerRef.current);
    setPlaceKey(nextKey);
    setFlights([]);
    if (isReducedMotion) {
      // No movement: hold the found photos briefly, then crossfade the fill and result in.
      setDemo({ ...getPhotoFinderState(Number.POSITIVE_INFINITY, nextPlace.query), phase: "match", isMapFilled: false });
      settleTimerRef.current = window.setTimeout(() => {
        setDemo(getPhotoFinderState(Number.POSITIVE_INFINITY, nextPlace.query));
      }, REDUCED_MOTION_SETTLE_MS);
    } else {
      const timeline = source === "chip" ? PHOTO_FINDER_REPLAY_TIMELINE : PHOTO_FINDER_TIMELINE;
      // The first frame of a new run is already its unfilled scan, never a spoiler of the next place.
      setDemo(getPhotoFinderState(0, nextPlace.query, timeline));
      setRun((current) => ({ id: current.id + 1, startedAt: performance.now(), timeline }));
    }
    if (source !== "autoplay") {
      trackEvent(ANALYTICS_EVENTS.HERO_DEMO_SELECT, { experience_type: "hero_demo", demo_place: nextKey });
      onPlaySelect?.(nextKey);
    }
  }, [isReducedMotion, onPlaySelect]);

  useEffect(() => () => window.clearTimeout(settleTimerRef.current), []);

  // Autoplay once the stage is ready (map shape and thumbnails), in view, and the tab is visible.
  useEffect(() => {
    if (isReducedMotion) return undefined;
    let cancelled = false;
    const cleanups = [];
    const assetsReady = Promise.race([
      Promise.all([
        loadWorldCountries().catch(() => {}),
        ...PHOTO_FINDER_LIBRARY_PHOTOS.map((src) => {
          const image = new Image();
          image.src = src;
          return image.decode().catch(() => {});
        }),
        wait(AUTOPLAY_MIN_WAIT_MS, cleanups),
      ]),
      wait(AUTOPLAY_MAX_WAIT_MS, cleanups),
    ]);
    const inView = new Promise((resolve) => {
      if (typeof IntersectionObserver === "undefined" || !cardRef.current) {
        resolve();
        return;
      }
      const observer = new IntersectionObserver((entries) => {
        if (!entries.some((entry) => entry.isIntersecting)) return;
        observer.disconnect();
        resolve();
      }, { threshold: 0.5 });
      observer.observe(cardRef.current);
      cleanups.push(() => observer.disconnect());
    });
    const tabVisible = () => new Promise((resolve) => {
      if (document.visibilityState === "visible") {
        resolve();
        return;
      }
      const handleVisibility = () => {
        if (document.visibilityState !== "visible") return;
        document.removeEventListener("visibilitychange", handleVisibility);
        resolve();
      };
      document.addEventListener("visibilitychange", handleVisibility);
      cleanups.push(() => document.removeEventListener("visibilitychange", handleVisibility));
    });
    Promise.all([assetsReady, inView]).then(() => (cancelled ? undefined : tabVisible())).then(() => {
      if (cancelled || hasPlayedRef.current) return;
      play(PHOTO_FINDER_PLACES[0].key, "autoplay");
    });
    return () => {
      cancelled = true;
      cleanups.forEach((cleanup) => cleanup());
    };
  }, [isReducedMotion, play]);

  useEffect(() => {
    if (run.startedAt === null) return undefined;
    const duration = getPhotoFinderDuration(place.query, run.timeline);
    let frame;
    let previous = null;
    const tick = (now) => {
      const next = getPhotoFinderState(now - run.startedAt, place.query, run.timeline);
      if (counterRef.current) counterRef.current.textContent = numberFormat.format(next.scannedCount);
      if (!previous || previous.phase !== next.phase || previous.typedQuery !== next.typedQuery || previous.isMapFilled !== next.isMapFilled) {
        previous = next;
        setDemo(next);
      }
      if (now - run.startedAt < duration) frame = requestAnimationFrame(tick);
    };
    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [run, place.query]);

  // Replays soften the grid and map back in instead of hard-cutting to the new library.
  useLayoutEffect(() => {
    if (run.id < 2 || isReducedMotion) return;
    gridWindowRef.current?.animate?.(SOFT_RESET_KEYFRAMES, SOFT_RESET_OPTIONS);
    mapRef.current?.querySelector("svg")?.animate?.(SOFT_RESET_KEYFRAMES, SOFT_RESET_OPTIONS);
  }, [run.id, isReducedMotion]);

  useLayoutEffect(() => {
    if (demo.phase !== "fly" || flightsRunRef.current === run.id || !cardRef.current || !targetRef.current) return;
    flightsRunRef.current = run.id;
    const card = cardRef.current.getBoundingClientRect();
    const target = targetRef.current.getBoundingClientRect();
    const targetX = target.left + target.width / 2 - card.left;
    const targetY = target.top + target.height / 2 - card.top;
    setFlights(matchRefs.current.slice(0, PHOTO_FINDER_MATCH_COUNT).map((node, index) => {
      if (!node) return null;
      const rect = node.getBoundingClientRect();
      return {
        index,
        photo: place.photos[index % place.photos.length],
        crop: place.crops[index],
        size: rect.width,
        fromX: rect.left - card.left,
        fromY: rect.top - card.top,
        toX: targetX - rect.width / 2,
        toY: targetY - rect.width / 2,
      };
    }).filter(Boolean));
  }, [demo.phase, run.id, place]);

  // The pin settles last, after every photo has landed.
  useLayoutEffect(() => {
    if (demo.phase !== "done" || !mapRef.current || !targetRef.current) {
      setPinPosition(null);
      return;
    }
    const map = mapRef.current.getBoundingClientRect();
    const target = targetRef.current.getBoundingClientRect();
    setPinPosition({ x: target.left + target.width / 2 - map.left, y: target.top + target.height / 2 - map.top });
  }, [demo.phase, scene]);

  // The result sentence belongs to the run that finished, so it never swaps text while fading out.
  useLayoutEffect(() => {
    if (demo.phase === "done") setResultPlaceKey(place.key);
  }, [demo.phase, place.key]);

  const isFound = demo.phase === "match" || demo.phase === "fly" || demo.phase === "done";
  const isTyping = demo.phase === "type" || demo.phase === "scan";
  const counterText = numberFormat.format(demo.phase === "idle" ? 0 : demo.phase === "scan" ? demo.scannedCount : PHOTO_FINDER_LIBRARY_COUNT);
  useLayoutEffect(() => {
    if (counterRef.current) counterRef.current.textContent = counterText;
  }, [counterText]);
  const resultPlace = PHOTO_FINDER_PLACES.find(({ key }) => key === resultPlaceKey);
  const resultText = `${numberFormat.format(PHOTO_FINDER_LIBRARY_COUNT)}장 중 ${resultPlace.label} 사진 ${resultPlace.foundCount}장을 찾았어요`;

  return (
    <section className="finder-hero" aria-labelledby="finder-hero-title">
      <div className="finder-hero-copy">
        <h1 id="finder-hero-title"><span>사진첩 {numberFormat.format(PHOTO_FINDER_LIBRARY_COUNT)}장,</span><em>검색 한 번이면 그 여행만 지도로</em></h1>
        <p className="finder-hero-description">장소만 검색하세요.<br /> 그곳에서 찍은 사진은 폰이 찾아 줘요.</p>
        <div className="finder-hero-actions">{storeActions}</div>
        <p className="finder-trust-note"><ShieldCheck size={18} weight="fill" />사진은 폰 안에서 찾고, 고른 사진만 올라가요.</p>
      </div>

      <div className="finder-demo-wrap">
        <div
          className="finder-demo"
          ref={cardRef}
          data-phase={demo.phase}
          data-place={place.key}
          aria-label={`예시: 사진첩에서 ${place.label}을 검색하면 그곳에서 찍은 사진만 찾아 지도에 칠하는 모습`}
          role="img"
        >
          <div className="finder-demo-bar" aria-hidden="true">
            <span className="finder-search">
              <MagnifyingGlass size={16} weight="bold" />
              <span className="finder-search-text">
                {demo.typedQuery || (
                  <>
                    {isTyping && <span className="finder-caret is-leading" />}
                    <span className="finder-search-placeholder">장소 검색</span>
                  </>
                )}
              </span>
              {demo.typedQuery && isTyping && <span className="finder-caret" />}
            </span>
            <span className="finder-counter">사진 <strong ref={counterRef} />장</span>
          </div>

          <div className="finder-grid-window" ref={gridWindowRef} aria-hidden="true">
            <div className="finder-grid-strip" key={`${run.id}-${place.key}`} style={{ "--scan-ms": `${run.timeline.scanEndMs}ms` }}>
              {tiles.map((tile) => (
                <span
                  key={tile.index}
                  className={`finder-tile ${tile.matchIndex >= 0 ? "is-match" : ""}`}
                  ref={tile.matchIndex >= 0 ? (node) => { matchRefs.current[tile.matchIndex] = node; } : undefined}
                  style={{ "--match-order": Math.max(tile.matchIndex, 0) }}
                >
                  <span>
                    <img src={tile.photo} alt="" decoding="async" style={cropStyle(tile.crop)} />
                  </span>
                </span>
              ))}
            </div>
            <p className={`finder-result ${demo.phase === "done" ? "is-visible" : ""}`} aria-hidden={demo.phase !== "done"}>
              <strong><CheckCircle size={16} weight="fill" />{resultText}</strong>
            </p>
            <span className="finder-sample-badge">예시</span>
          </div>

          <div className={`finder-map ${demo.isMapFilled ? "is-filled" : ""}`} ref={mapRef} aria-hidden="true">
            <span className="finder-map-scope">{scene.scopeLabel}</span>
            <svg viewBox={scene.viewBox} preserveAspectRatio="xMidYMid meet">
              {scene.regions.map((region) => (
                <path key={region.id} d={region.d} className={region.id === place.regionCode ? "is-target" : undefined} />
              ))}
              <circle ref={targetRef} cx={scene.target[0]} cy={scene.target[1]} r="1" className="finder-map-anchor" />
            </svg>
            {pinPosition && (
              <span className="finder-map-pin" style={{ left: `${pinPosition.x}px`, top: `${pinPosition.y}px` }}>
                <MapPin size={14} weight="fill" />{place.label}
              </span>
            )}
          </div>

          {flights.map((flight) => (
            <span
              key={`${run.id}-${flight.index}`}
              className="finder-flight"
              aria-hidden="true"
              style={{
                "--size": `${flight.size}px`,
                "--from-x": `${flight.fromX}px`,
                "--from-y": `${flight.fromY}px`,
                "--to-x": `${flight.toX}px`,
                "--to-y": `${flight.toY}px`,
                "--flight-ms": `${run.timeline.flightMs}ms`,
                "--delay": `${flight.index * run.timeline.flightStaggerMs}ms`,
              }}
            >
              <span>
                <img src={flight.photo} alt="" style={cropStyle(flight.crop)} />
              </span>
            </span>
          ))}
        </div>
        <p className="sr-only" aria-live="polite">{demo.phase === "done" ? resultText : ""}</p>

        <div className="finder-chips" role="group" aria-label="다른 장소로 찾아보기">
          <span className="finder-chips-label" aria-hidden="true">
            <span data-visible={!isFound}>직접 해보기</span>
            <span data-visible={isFound}>다른 곳도 찾아보기</span>
          </span>
          {PHOTO_FINDER_PLACES.map((option) => (
            <button
              key={option.key}
              type="button"
              className={option.key === place.key ? "is-active" : ""}
              aria-pressed={option.key === place.key}
              onClick={() => play(option.key, "chip")}
            >
              {option.label}
            </button>
          ))}
        </div>
      </div>
    </section>
  );
}

export { PhotoFinderHero };
