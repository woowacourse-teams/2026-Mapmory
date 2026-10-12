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
import gyeongbuk from "../data/gyeongbuk-districts.json";
import koreaProvinces from "../data/korea-provinces.json";
import { APP_SCREEN_TEXT, HOW_STORY_LIBRARY_COUNT, HOW_STORY_MATCHES, fillHowStoryText, mapPercent } from "../howStory.js";
import { HOW_STORY_APP_PROGRESS, boundsOfRings, createAppMapProjection } from "../howStoryLayout.js";

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

function AppTopBar({ title, action, statusSlot = false }) {
  return (
    <div className="how-app-top">
      <div className="how-app-bar">
        <span className="how-app-back">{T.back}</span>
        <span className="how-app-title">{title}</span>
        {action && (
          <span className="how-app-action">
            <span className="how-app-action-label">{action}</span>
            {statusSlot && <span className="how-app-action-status"><span className="how-app-spinner" /></span>}
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

// PhotoLoadingStep: ring, PHOTO LIBRARY, the app's own counter, bar, percent and message. done: the end state
// (static list); in scroll mode the hook places the bar's indicator and track every frame.
export function LoadingScreen({ node, count, percent, done = false }) {
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
        <span className="how-loading-bar" data-done={done ? "1" : undefined}>
          <i className="how-loading-track" data-node="loadingTrack" />
          <i className="how-loading-fill" data-node="loadingFill" />
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
export const LOADING_DONE = Object.freeze({
  count: fillHowStoryText(T.loading.count, { processed: HOW_STORY_LIBRARY_COUNT, total: HOW_STORY_LIBRARY_COUNT }),
  percent: fillHowStoryText(T.loading.percent, { p: 100 }),
  done: true,
});

// PhotoPickerStep: header, buttons, note, the recorded-photo filter, one date group and a 2-column grid.
// cardsOnStage: in scroll mode the flying stage tiles are the cards, so the grid holds empty placeholders.
export function PickerScreen({ node, cardsOnStage = false }) {
  const t = T.picker;
  return (
    <div className="how-app-screen how-app-picker" data-node={node}>
      <AppTopBar title={t.title} action={t.action} statusSlot />
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
                    <span className="how-picker-card-border" />
                    <span className="how-picker-preview"><SearchGlyph className="how-picker-preview-glyph" /></span>
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

// Map viewport in dp (the screen below the header, card and spacer, above the bottom bar). The header height
// depends on font metrics, so this is an estimate (unverified against a device capture).
export const APP_MAP_VIEWPORT = Object.freeze({ width: 360, height: 511 });

const toPath = (rings, project) => rings.map((ring) => `${ring.map(([lng, lat], index) => {
  const [x, y] = project(lng, lat);
  return `${index ? "L" : "M"}${x.toFixed(1)} ${y.toFixed(1)}`;
}).join("")}Z`).join("");

// Drill view: develop fits the 22 districts to the bounds of every raw ring (Ulleungdo and Dokdo included).
const projectDrill = createAppMapProjection(gyeongbuk.bounds, APP_MAP_VIEWPORT.width, APP_MAP_VIEWPORT.height);
const DRILL_DISTRICTS = gyeongbuk.districts.map(({ code, name, rings, labelPoint }) => ({
  code,
  name,
  d: toPath(rings, projectDrill),
  label: projectDrill(labelPoint[0], labelPoint[1]).map((value) => Number(value.toFixed(1))),
}));
const projectKorea = createAppMapProjection(boundsOfRings(koreaProvinces), APP_MAP_VIEWPORT.width, APP_MAP_VIEWPORT.height);
const KOREA_PROVINCES = koreaProvinces.map(({ code, rings }) => ({ code, d: toPath(rings, projectKorea) }));
const VISITED_DISTRICT = "47130";
const VISITED_PROVINCE = gyeongbuk.provinceCode;

const MAP_VARIANTS = Object.freeze({
  drill: Object.freeze({ title: T.map.titleKorea, visited: 1, total: gyeongbuk.districts.length, back: true, scope: "korea" }),
  korea: Object.freeze({ title: T.map.titleKorea, visited: 1, total: koreaProvinces.length, back: false, scope: "korea" }),
});

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

// TripMapScreen with MapRoute's content: drill (Gyeongbuk's districts, Gyeongju filled) or korea (the 17 provinces, Gyeongbuk filled).
export function MapScreen({ node, variant = "drill" }) {
  const v = MAP_VARIANTS[variant];
  return (
    <div className="how-app-screen how-app-map" data-node={node} data-variant={variant}>
      <div className="how-map-header">
        <div className="how-map-logo-row">
          <span className="how-map-logo"><span>{T.map.logo[0]}</span><span className="how-map-logo-accent">{T.map.logo[1]}</span></span>
          <span className="how-map-scope" data-scope={v.scope}>
            <span className="how-map-scope-thumb" />
            <span className="how-map-scope-chip" data-chip="korea">{T.map.scopeKorea}</span>
            <span className="how-map-scope-chip" data-chip="world">{T.map.scopeWorld}</span>
          </span>
        </div>
        <div className="how-map-card">
          {v.back && <p className="how-map-back">{fillHowStoryText(T.map.back, { province: T.map.province })}</p>}
          <div className="how-map-card-row">
            <div>
              <p className="how-map-card-title">{v.title}</p>
              <p className="how-map-card-count">
                <span className="how-map-card-n">{v.visited}</span>
                <span className="how-map-card-total">{fillHowStoryText(T.map.total, { total: v.total })}</span>
              </p>
            </div>
            <span className="how-map-card-badge">{fillHowStoryText(T.map.percent, { pct: mapPercent(v.visited, v.total) })}</span>
          </div>
        </div>
      </div>
      <div className="how-map-viewport">
        <svg className="how-map-svg" viewBox={`0 0 ${APP_MAP_VIEWPORT.width} ${APP_MAP_VIEWPORT.height}`} preserveAspectRatio="xMidYMid meet" focusable="false">
          {variant === "drill" ? (
            <>
              {DRILL_DISTRICTS.map(({ code, d }) => <path key={code} className={code === VISITED_DISTRICT ? "is-visited" : undefined} d={d} />)}
              {DRILL_DISTRICTS.map(({ code, name, label }) => <text key={code} x={label[0]} y={label[1]}>{name}</text>)}
            </>
          ) : (
            KOREA_PROVINCES.map(({ code, d }) => <path key={code} className={code === VISITED_PROVINCE ? "is-visited" : undefined} d={d} />)
          )}
        </svg>
        <span className="how-map-fab"><svg viewBox="0 0 24 24" focusable="false"><path d="M4.32 12H19.68 M12 4.32V19.68" /></svg></span>
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
