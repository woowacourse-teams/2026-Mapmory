// HTML/CSS recreations of develop's record flow screens, in dp (see how-app-screens.css).
//
// Release gate: these screens are develop's record flow (photo import -> photo picker -> save from the picker), which main's
// editor does not have yet. Ship the #how section to production only once that flow is in the App Store and
// Google Play builds (landing/AGENTS.md).
//
// Sources on origin/develop: NewTripRecordFlowScreen.kt (LocationStep, PhotoLoadingStep, PhotoPickerStep),
// TripMapScreen.kt, TripRecordComponents.kt (top bar, FAB, bottom bar) and KoreaMapArtwork.kt (map drawing).
// In scroll mode the screens' changing states come from data-* attributes on the stage and the hook writes
// the [data-text] values; static figures pass end-state props instead.
import { useId } from "react";
import gyeongbuk from "../data/gyeongbuk-districts.json";
import koreaProvinces from "../data/korea-provinces.json";
import worldGlobe from "../data/world-globe.json";
import {
  APP_SCREEN_TEXT,
  HOW_STORY_LIBRARY_COUNT,
  HOW_STORY_MATCHES,
  appLoadingCount,
  appLoadingPercent,
  fillHowStoryText,
  linearProgressPlacement,
  mapPercent,
} from "../howStory.js";
import { APP_MAP_VIEW, HOW_STORY_APP_PROGRESS } from "../howStoryLayout.js";

const T = APP_SCREEN_TEXT;

function SearchGlyph({ className }) {
  // SearchIcon in NewTripRecordFlowScreen.kt: a circle (radius 0.28) at 43% and a handle to 86%.
  return (
    <svg className={className} viewBox="0 0 20 20" focusable="false">
      <circle cx="8.6" cy="8.6" r="5.6" />
      <path d="M12.63 12.63L17.2 17.2" />
    </svg>
  );
}

function AppTopBar({ title, action, statusSlot = false, tap }) {
  return (
    <div className="how-app-top">
      <div className="how-app-bar">
        <span className="how-app-back">{T.back}</span>
        <span className="how-app-title">{title}</span>
        {action && (
          <span className="how-app-action">
            <span className="how-app-action-label">{action}</span>
            {statusSlot && <span className="how-app-action-status"><span className="how-app-spinner" /></span>}
            {tap && <span className="how-tap" data-tap={tap} />}
          </span>
        )}
      </div>
    </div>
  );
}

// Photo import (LocationStep). Field empty -> IME typing -> administrative-area result -> row selected.
export function ImportScreen({ node }) {
  const t = T.import;
  return (
    <div className="how-app-screen how-app-import" data-node={node}>
      <AppTopBar title={t.title} action={t.action} />
      <div className="how-import-body">
        <p className="how-app-eyebrow">{t.eyebrow}</p>
        <p className="how-import-heading">{t.heading}</p>
        <div className="how-import-section">
          <span className="how-import-section-title">{t.section}</span>
          <span className="how-import-section-badge">{t.badge}</span>
        </div>
        <p className="how-import-helper">{t.helper}</p>
        <div className="how-import-field">
          <SearchGlyph className="how-import-glyph" />
          <span className="how-import-input">
            <span className="how-import-placeholder">{t.placeholder}</span>
            <span className="how-import-typing">
              {t.ime.map((step, index) => <span className="how-import-ime" data-step={index} key={step}>{step}</span>)}
            </span>
            <span className="how-import-value">{t.place}</span>
          </span>
          <span className="how-tap" data-tap="field" />
        </div>
        <div className="how-import-flow">
          <div className="how-import-results">
            <p className="how-import-results-label">{t.resultsLabel}</p>
            <div className="how-import-list">
              <div className="how-import-row">
                <span className="how-import-row-bg" />
                <span className="how-import-scope">{t.scope}</span>
                <span className="how-import-name">{t.place}</span>
                <span className="how-import-mark">
                  <span className="how-import-chevron">{t.chevron}</span>
                  <span className="how-import-check">{t.check}</span>
                </span>
                <span className="how-tap" data-tap="row" />
              </div>
            </div>
          </div>
          <div className="how-import-button">{t.button}</div>
        </div>
      </div>
    </div>
  );
}

// Material3 1.4 indeterminate CircularProgressIndicator with a track (PhotoLoadingStep): a 270-degree arc with
// round caps, and the track drawn only outside the arc, with a gap at each end. In the 64 viewBox (dp): radius
// 29.5, stroke 5; with round caps the drawn gap is the M3 gap plus one stroke width.
const RING = (() => {
  const circumference = 2 * Math.PI * 29.5;
  const arc = circumference * 0.75;
  const gap = HOW_STORY_APP_PROGRESS.gapDp + 5;
  const track = circumference - arc - gap * 2;
  return {
    arc: `${arc.toFixed(2)} ${(circumference - arc).toFixed(2)}`,
    track: `${track.toFixed(2)} ${(circumference - track).toFixed(2)}`,
    trackOffset: (-(arc + gap)).toFixed(2),
  };
})();

// PhotoLoadingStep: ring, PHOTO LIBRARY, the app's own counter, bar, percent and message. bar: where the indicator
// and track sit, in dp (the static list's still); in scroll mode the hook places them every frame.
export function LoadingScreen({ node, count, percent, bar }) {
  const t = T.loading;
  return (
    <div className="how-app-screen how-app-loading" data-node={node}>
      <AppTopBar title={t.title} />
      <div className="how-loading-body">
        {/* The ring turns on this HTML wrapper, which the compositor can animate (an SVG transform cannot). */}
        <span className="how-loading-spin">
          <svg className="how-loading-ring" viewBox="0 0 64 64" focusable="false">
            <circle className="how-loading-ring-track" cx="32" cy="32" r="29.5" strokeDasharray={RING.track} strokeDashoffset={RING.trackOffset} />
            <circle className="how-loading-ring-arc" cx="32" cy="32" r="29.5" strokeDasharray={RING.arc} />
          </svg>
        </span>
        <p className="how-app-eyebrow how-loading-eyebrow">{t.eyebrow}</p>
        <p className="how-loading-heading">{t.heading}</p>
        <p className="how-loading-count"><span data-text="loadingCount">{count}</span></p>
        <span className="how-loading-bar">
          <i className="how-loading-track" data-node="loadingTrack" style={bar && { transform: `translateX(calc(${bar.track} * var(--dp)))` }} />
          <i className="how-loading-fill" data-node="loadingFill" style={bar && { transform: `translateX(calc(${bar.fill} * var(--dp)))` }} />
          <i className="how-loading-stop" />
        </span>
        <p className="how-loading-percent"><span data-text="loadingPct">{percent}</span></p>
        <p className="how-loading-message">{fillHowStoryText(t.message, { place: t.place })}</p>
      </div>
    </div>
  );
}

export const LOADING_START = Object.freeze({
  count: fillHowStoryText(T.loading.count, { processed: 0, total: HOW_STORY_LIBRARY_COUNT }),
  percent: fillHowStoryText(T.loading.percent, { p: 0 }),
});
// The static list's still: a moment mid-run, as the app draws it (a count with the app's own percent for it). At
// 100% develop no longer shows the count: it switches to preparing the previews, which the story does not draw.
export const LOADING_STILL_PERCENT = 40;
export const LOADING_STILL = (() => {
  const processed = appLoadingCount(LOADING_STILL_PERCENT);
  const k = appLoadingPercent(processed);
  // The bar in dp: 292dp wide (34dp side padding), 8dp tall.
  const bar = linearProgressPlacement(k, { w: 360 - 68, h: 8 }, HOW_STORY_APP_PROGRESS.gapDp);
  return Object.freeze({
    count: fillHowStoryText(T.loading.count, { processed, total: HOW_STORY_LIBRARY_COUNT }),
    percent: fillHowStoryText(T.loading.percent, { p: k }),
    bar: Object.freeze(bar),
  });
})();

// A picker card's border (drawn over the photo, 1dp or 3dp when selected) and its preview button. The scroll
// story's flying tiles carry the same chrome, so they become the cards when they land.
export function PickerCardChrome() {
  return (
    <>
      <span className="how-picker-card-border" />
      <span className="how-picker-preview"><SearchGlyph className="how-picker-preview-glyph" /></span>
    </>
  );
}

// PhotoPickerStep: header, buttons, note, the recorded-photo filter, one date group and a 2-column grid.
// cardsOnStage: in scroll mode the flying stage tiles are the cards, so the grid holds empty placeholders, and the
// save-action tap is drawn.
export function PickerScreen({ node, cardsOnStage = false }) {
  const t = T.picker;
  return (
    <div className="how-app-screen how-app-picker" data-node={node}>
      <AppTopBar title={t.title} action={t.action} statusSlot tap={cardsOnStage ? "save" : undefined} />
      <div className="how-picker-viewport">
        <div className="how-picker-list">
          <p className="how-app-eyebrow">{t.eyebrow}</p>
          <div className="how-picker-title-row">
            <span className="how-picker-title">{fillHowStoryText(t.heading, { place: t.place })}</span>
            <span className="how-picker-count">
              {[0, 1, 2, 3].map((n) => <span className="how-picker-count-n" data-n={n} key={n}>{fillHowStoryText(t.count, { n })}</span>)}
            </span>
          </div>
          <div className="how-picker-buttons">
            <span className="how-app-small-button">{t.selectAll}</span>
            <span className="how-app-small-button">{t.fromGallery}</span>
          </div>
          <p className="how-picker-note">
            <span>{t.note[0]}</span>
            <span>{fillHowStoryText(t.note[1], { max: t.max })}</span>
          </p>
          <div className="how-picker-filter">
            <span className="how-picker-checkbox">
              <svg viewBox="0 0 18 18" focusable="false"><path d="M4.2 9.3l3.1 3.1 6.5-6.6" /></svg>
            </span>
            <span className="how-picker-filter-label">{t.filter}</span>
          </div>
          <div className="how-picker-date">
            <span className="how-picker-date-label">{t.date}</span>
            <span className="how-picker-date-count">{fillHowStoryText(t.dateCount, { n: HOW_STORY_MATCHES.length })}</span>
            <span className="how-app-small-button how-picker-date-button">{t.selectDate}</span>
          </div>
          <div className="how-picker-grid">
            {HOW_STORY_MATCHES.map((match, index) => (
              <div className="how-picker-card" data-card={index} data-picked={match.picked ? "true" : undefined} key={match.id}>
                {!cardsOnStage && (
                  <>
                    <img src={match.src} alt="" width="120" height="120" loading="lazy" decoding="async" />
                    <PickerCardChrome />
                  </>
                )}
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

// The map area in dp (between the header and the bottom bar) and one coordinate space for the drill and Korea
// views: develop's Korea projection of that area (howStoryLayout.js). The drill view is a viewBox into it.
const VIEW = APP_MAP_VIEW;
const box = (values) => values.map((value) => Number(value.toFixed(2))).join(" ");
// Douglas-Peucker on a polyline, in dp.
function simplifyLine(points, tolerance) {
  if (points.length < 3) return points;
  const keep = new Uint8Array(points.length);
  keep[0] = 1;
  keep[points.length - 1] = 1;
  const spans = [[0, points.length - 1]];
  while (spans.length) {
    const [a, b] = spans.pop();
    const [ax, ay] = points[a];
    const [bx, by] = points[b];
    const length = Math.hypot(bx - ax, by - ay) || 1;
    let far = -1;
    let farthest = tolerance;
    for (let index = a + 1; index < b; index += 1) {
      const [x, y] = points[index];
      const distance = Math.abs((bx - ax) * (ay - y) - (ax - x) * (by - ay)) / length;
      if (distance > farthest) {
        farthest = distance;
        far = index;
      }
    }
    if (far >= 0) {
      keep[far] = 1;
      spans.push([a, far], [far, b]);
    }
  }
  return points.filter((_, index) => keep[index]);
}
// A ring as an SVG path in the map's dp space. tolerance (dp at the Korea view) thins rings that are only ever drawn
// small: every point the map repaints costs time on each frame of the camera move.
const toPath = (rings, tolerance = 0) => rings.map((ring) => {
  let points = ring.map(([lng, lat]) => VIEW.project(lng, lat));
  if (tolerance > 0 && points.length > 8) {
    const half = Math.floor(points.length / 2);
    points = [...simplifyLine(points.slice(0, half + 1), tolerance), ...simplifyLine(points.slice(half), tolerance).slice(1)];
  }
  return `${points.map(([x, y], index) => `${index ? "L" : "M"}${x.toFixed(1)} ${y.toFixed(1)}`).join("")}Z`;
}).join("");
// The 17 provinces show from about 1.3x the Korea view down to it: 0.4dp there stays under a pixel.
const PROVINCE_TOLERANCE = 0.4;
const DRILL_ZOOM = VIEW.area.width / VIEW.drillBox[2];
const DISTRICTS = gyeongbuk.districts.map(({ code, name, rings, labelPoint }) => {
  const [x, y] = VIEW.project(labelPoint[0], labelPoint[1]);
  return { code, name, d: toPath(rings), label: [(x - VIEW.drillBox[0]) * DRILL_ZOOM, (y - VIEW.drillBox[1]) * DRILL_ZOOM] };
});
const PROVINCES = koreaProvinces.map(({ code, rings }) => ({ code, d: toPath(rings, PROVINCE_TOLERANCE) }));
const DISTRICT_LAND = DISTRICTS.map(({ d }) => d).join("");
const VISITED_DISTRICT = DISTRICTS.find(({ code }) => code === "47130");
const VISITED_PROVINCE = PROVINCES.find(({ code }) => code === gyeongbuk.provinceCode);
// Gyeongju fills from its label point: the transform origin as a share of its own box (transform-box: fill-box).
const FILL_ORIGIN = (() => {
  const points = gyeongbuk.districts.find(({ code }) => code === "47130").rings.flat().map(([lng, lat]) => VIEW.project(lng, lat));
  const [minX, maxX] = [Math.min(...points.map(([x]) => x)), Math.max(...points.map(([x]) => x))];
  const [minY, maxY] = [Math.min(...points.map(([, y]) => y)), Math.max(...points.map(([, y]) => y))];
  const [lx, ly] = VIEW.project(...gyeongbuk.districts.find(({ code }) => code === "47130").labelPoint);
  return `${(((lx - minX) / (maxX - minX)) * 100).toFixed(1)}% ${(((ly - minY) / (maxY - minY)) * 100).toFixed(1)}%`;
})();

// WorldGlobe's first frame (longitude -127 degrees, no tilt), pre-projected by scripts/generate-korea-map.mjs on a
// disc of radius 1000; drawn here at develop's radius, 0.42 of the area's shorter side.
const GLOBE = (() => {
  const { cx, cy, r } = VIEW.globe;
  const scale = r / worldGlobe.radius;
  const path = (rings) => rings.map((ring) => {
    let d = "";
    for (let index = 0; index < ring.length; index += 2) {
      d += `${index ? "L" : "M"}${(cx + ring[index] * scale).toFixed(1)} ${(cy - ring[index + 1] * scale).toFixed(1)}`;
    }
    return `${d}Z`;
  }).join("");
  return {
    cx,
    cy,
    r,
    land: worldGlobe.countries.filter(({ code }) => code !== "KR").map(({ rings }) => path(rings)).join(""),
    korea: path(worldGlobe.countries.find(({ code }) => code === "KR").rings),
  };
})();

function MapGlobe() {
  const id = useId().replace(/[^a-zA-Z0-9_-]/g, "");
  const { cx, cy, r } = GLOBE;
  const stop = (offset, color) => <stop offset={offset} style={{ stopColor: color }} />;
  return (
    <svg className="how-map-globe" viewBox={box([0, 0, VIEW.area.width, VIEW.area.height])} focusable="false">
      <defs>
        <radialGradient id={`${id}-glow`} cx={cx} cy={cy} r={r * 1.12} gradientUnits="userSpaceOnUse">
          {stop(0, "transparent")}
          {stop(0.82, "transparent")}
          {stop(0.91, "var(--app-globe-glow)")}
          {stop(1, "transparent")}
        </radialGradient>
        <radialGradient id={`${id}-sphere`} cx={cx - r * 0.28} cy={cy - r * 0.28} r={r * 1.2} gradientUnits="userSpaceOnUse">
          {stop(0, "var(--app-globe-centre)")}
          {stop(0.5, "var(--app-globe-middle)")}
          {stop(1, "var(--app-globe-edge)")}
        </radialGradient>
        <radialGradient id={`${id}-light`} cx={cx - r * 0.42} cy={cy - r * 0.42} r={r * 1.15} gradientUnits="userSpaceOnUse">
          {stop(0, "var(--app-globe-light-centre)")}
          {stop(0.42, "var(--app-globe-light-middle)")}
          {stop(1, "transparent")}
        </radialGradient>
      </defs>
      <circle cx={cx} cy={cy} r={r * 1.12} fill={`url(#${id}-glow)`} />
      <circle cx={cx} cy={cy} r={r} fill={`url(#${id}-sphere)`} />
      <path className="how-globe-land" d={GLOBE.land} />
      <path className="how-globe-visited" d={GLOBE.korea} />
      <circle cx={cx} cy={cy} r={r} fill={`url(#${id}-light)`} />
    </svg>
  );
}

// The summary card (MapSummaryCard): title, `{n} / {total}`, `{pct}% filled`; the drill variant carries the back row.
// counts: one [visited, total] or, for the drill card, the before and after of saving the record (data-v).
function MapCard({ kind, title, counts, back = false, node }) {
  const variants = counts.map(([visited, total], index) => ({ visited, total, pct: mapPercent(visited, total), v: counts.length > 1 ? index : undefined }));
  return (
    <div className={`how-map-card how-map-card-${kind}`} data-node={node}>
      {back && <p className="how-map-back">{fillHowStoryText(T.map.back, { province: T.map.province })}</p>}
      <div className="how-map-card-row">
        <div>
          <p className="how-map-card-title">{title}</p>
          <p className="how-map-card-count">
            <span className="how-map-card-n">{variants.map(({ visited, v }) => <span data-v={v} key={visited}>{visited}</span>)}</span>
            <span className="how-map-card-total">{fillHowStoryText(T.map.total, { total: variants[0].total })}</span>
          </p>
        </div>
        <span className="how-map-card-badge">{variants.map(({ pct, v }) => <span data-v={v} key={pct}>{fillHowStoryText(T.map.percent, { pct })}</span>)}</span>
      </div>
    </div>
  );
}

const DRILL_TOTAL = gyeongbuk.districts.length;
const KOREA_TOTAL = koreaProvinces.length;
// MapSummaryCard's world total.
const WORLD_TOTAL = 195;

function MapIcon({ tab }) {
  // TripBottomIcon geometry on a 25dp canvas (stroke 1.7dp, round caps).
  if (tab === 0) {
    return (
      <svg viewBox="0 0 25 25" focusable="false">
        <path d="M3 5.75L9 2.5L16.5 5.75L22.5 2.5V19.25L16.5 22.5L9 19.25L3 22.5Z M9 2.5V19.25 M16.5 5.75V22.5" />
      </svg>
    );
  }
  if (tab === 1) {
    return (
      <svg viewBox="0 0 25 25" focusable="false">
        {[0.18, 0.47, 0.76].map((y) => (
          <g key={y}>
            <rect x="3" y={25 * y} width="4.25" height="4.25" />
            <path d={`M10.75 ${(25 * (y + 0.085)).toFixed(2)}H22.5`} />
          </g>
        ))}
      </svg>
    );
  }
  return (
    <svg viewBox="0 0 25 25" focusable="false">
      <path d="M2 22H23" />
      <path className="how-map-tab-bars" d="M6 12.5V22 M12.5 4V22 M19 8.5V22" />
    </svg>
  );
}

// Where the summary card and the map area start, in dp (CSS multiplies them by --dp).
const MAP_PLACEMENT = { "--map-card-top": VIEW.cardTop.toFixed(2), "--map-area-top": VIEW.area.top.toFixed(2) };

// TripMapScreen with MapRoute's content. story (the scroll story): develop's Gyeongsangbuk-do drill map (Gyeongju fills when the
// record saves), the camera's pull-back to the Korea map (the province filled) and the world globe (Korea filled),
// driven by the stage's data-map state and the frame's nodes. korea: the Korea map alone (the static list).
export function MapScreen({ node, variant = "story" }) {
  const story = variant === "story";
  const n = (name) => (story ? name : undefined);
  return (
    <div className="how-app-screen how-app-map" data-node={node} data-variant={variant} style={MAP_PLACEMENT}>
      <div className="how-map-header">
        <div className="how-map-logo-row">
          <span className="how-map-logo"><span>{T.map.logo[0]}</span><span className="how-map-logo-accent">{T.map.logo[1]}</span></span>
          <span className="how-map-scope">
            <span className="how-map-scope-thumb" />
            <span className="how-map-scope-chip" data-chip="korea">{T.map.scopeKorea}</span>
            <span className="how-map-scope-chip" data-chip="world">{T.map.scopeWorld}</span>
          </span>
        </div>
      </div>
      <div className="how-map-area">
        <div className="how-map-korea">
          <svg
            className="how-map-svg"
            viewBox={box(story ? VIEW.drillBox : VIEW.koreaBox)}
            preserveAspectRatio="xMidYMid meet"
            data-attr={n("mapSvg")}
            focusable="false"
          >
            {story && (
              <>
                {/* The land under the district lines: the same rings in one path, so it matches them exactly. */}
                <g className="how-map-land"><path d={DISTRICT_LAND} /></g>
                <g className="how-map-lines" data-node="mapDistrictLines">{DISTRICTS.map(({ code, d }) => <path key={code} d={d} />)}</g>
              </>
            )}
            <g className="how-map-provinces" data-node={n("mapProvinces")}>{PROVINCES.map(({ code, d }) => <path key={code} d={d} />)}</g>
            {story && (
              <g data-node="mapFillDistrict">
                <path className="how-map-visited how-map-fill-district" d={VISITED_DISTRICT.d} style={{ transformOrigin: FILL_ORIGIN }} />
              </g>
            )}
            <g className="how-map-fill-province" data-node={n("mapFillProvince")}><path className="how-map-visited" d={VISITED_PROVINCE.d} /></g>
          </svg>
          {story && (
            <div className="how-map-labels" data-node="mapLabels">
              {DISTRICTS.map(({ code, name, label }) => (
                <span key={code} style={{ left: `calc(${label[0].toFixed(1)} * var(--dp))`, top: `calc(${label[1].toFixed(1)} * var(--dp))` }}>{name}</span>
              ))}
            </div>
          )}
        </div>
        {story && <div className="how-map-world"><MapGlobe /></div>}
        <span className="how-map-fab"><svg viewBox="0 0 24 24" focusable="false"><path d="M4.32 12H19.68 M12 4.32V19.68" /></svg></span>
      </div>
      <div className="how-map-cards">
        {story ? (
          <>
            <div className="how-map-cards-korea">
              <MapCard kind="drill" node="mapCardDrill" title={T.map.titleKorea} counts={[[0, DRILL_TOTAL], [1, DRILL_TOTAL]]} back />
              <MapCard kind="korea" node="mapCardKorea" title={T.map.titleKorea} counts={[[1, KOREA_TOTAL]]} />
            </div>
            <MapCard kind="world" title={T.map.titleWorld} counts={[[1, WORLD_TOTAL]]} />
          </>
        ) : (
          <MapCard kind="korea" title={T.map.titleKorea} counts={[[1, KOREA_TOTAL]]} />
        )}
      </div>
      <div className="how-map-tabs">
        {T.map.tabs.map((label, index) => (
          <span className="how-map-tab" data-selected={index === 0 ? "true" : undefined} key={label}>
            <MapIcon tab={index} />
            <span>{label}</span>
          </span>
        ))}
      </div>
    </div>
  );
}
