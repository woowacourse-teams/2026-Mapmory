// Geometry for the #how app tour. Pure: every rect comes from the stage size and the viewport width, so the
// scroll hook, the frame function and the tests share one set of numbers.
// Stage coordinates: origin at the stage's top-left, in CSS px. "dp" is the app's unit: 1dp = screen width / 360.

export const HOW_STORY_SCREEN = Object.freeze({ widthDp: 360, heightDp: 780 });

export const HOW_STORY_GEOMETRY = Object.freeze({
  bezel: 9,
  // Mobile: at least this share of the phone stays visible; the bottom is cropped Toss-style.
  visibleShare: 0.84,
  phoneTopInset: 4,
  phoneMax: Object.freeze({ mobile: 300, tablet: 320 }),
  // Desktop's 220px floor (spec 9.3 said 260) lets laptop windows about 630-720px tall (1366x768 and 1280x720
  // with the browser's toolbars) keep the story instead of the static list; owner to confirm.
  phoneMin: Object.freeze({ mobile: 220, tablet: 220, desktop: 220 }),
  // Desktop: the whole phone shows, with more room below it than above so its drop shadow (0 30px 60px -24px,
  // about 66px deep) ends inside the stage box instead of on its clipped edge. On short windows the phone keeps
  // 260px while 28px margins allow, then shrinks toward the floor.
  desktopPhone: Object.freeze([260, 340]),
  desktopMargin: Object.freeze({ top: 20, bottom: 68 }),
  sideRoom: 40,
  tile: Object.freeze({ mobile: 16, tablet: 16, desktop: 18 }),
  cluster: Object.freeze({
    mobile: Object.freeze({ tile: 84, gap: 10 }),
    tablet: Object.freeze({ tile: 84, gap: 10 }),
    desktop: Object.freeze({ tile: 120, gap: 14 }),
  }),
  clusterCentreY: 0.58,
  overlayTop: 0.16,
  // The overlay's label and value line boxes (value: font size x 1.15 + its 4px top margin), and the gap the
  // cluster keeps below the landed number on short stages.
  overlayLabel: Object.freeze({ mobile: 20, tablet: 20, desktop: 22 }),
  overlayValue: Object.freeze({ mobile: 54.6, tablet: 54.6, desktop: 77.6 }),
  // The bar under the number: 12px margin and 4px tall.
  overlayBarSpace: 16,
  clusterGap: 24,
  // A lifted photo's ring and halo reach 7px outside it; it keeps 8px more from the overlay's bar.
  matchHalo: 7,
  matchClear: 8,
  overlayBar: Object.freeze({ mobile: 160, tablet: 160, desktop: 220 }),
  // A found photo grows to this scale when its pin pops, so the four stand out in the library.
  matchLift: 1.8,
  stageRadius: Object.freeze({ mobile: 24, tablet: 24, desktop: 32 }),
  // The smallest size the flying photos are drawn at (see nodeBase in the layout).
  nodeBase: 120,
  pinOffset: 2,
  pinHeight: 8,
});

// Material3 1.4 progress details in develop's PhotoLoadingStep (library defaults, not custom drawing): the gap
// between indicator and track, and the stop dot at the linear track's end, in dp. Unverified fidelity (no
// device capture yet); how-app-screens.css mirrors them as --app-progress-gap and --app-progress-stop.
export const HOW_STORY_APP_PROGRESS = Object.freeze({ gapDp: 4, stopDp: 4 });

// Line box of the app's text as a multiple of its size: Compose's default for LINE Seed Sans KR. Unverified
// fidelity (no device capture); how-app-screens.css mirrors it as --app-line-height, and the picker card and map
// rects below are derived from it, so the stage tiles land exactly on the DOM's cards.
export const HOW_STORY_APP_LINE_HEIGHT = 1.35;
// The picker list is shown scrolled by this much so both card rows sit high on the screen.
export const PICKER_SCROLL_DP = 48;

// PhotoPickerStep (NewTripRecordFlowScreen.kt on develop), in dp: the top bar (24 status + 64 bar + 1 divider), the
// list's content padding, the header item (PHOTO LIBRARY, title row, two FlowSmallButtons of min 44dp, the
// two-line note, the 48dp recorded-photo filter row), the date header (24dp above a 44dp row) and the 2-column grid.
export const HOW_STORY_PICKER = Object.freeze({
  listTop: 89,
  padStart: 20,
  padTop: 24,
  padEnd: 24,
  card: 153,
  gap: 10,
  firstRowGap: 12,
  smallButton: 44,
  filterRow: 48,
  dateGap: 24,
  // A picked card's copy at save: lifted this far and scaled about its centre.
  copyLift: 24,
  copyScale: 0.72,
});

export function pickerCardRectsDp(lineHeight = HOW_STORY_APP_LINE_HEIGHT) {
  const k = HOW_STORY_PICKER;
  const header = 12 * lineHeight + 14 + 25 * lineHeight + 20 + k.smallButton + 8 + 2 * 16 + k.filterRow;
  const dateHeader = k.dateGap + k.smallButton;
  const row1 = k.listTop + k.padTop - PICKER_SCROLL_DP + header + dateHeader + k.firstRowGap;
  return [0, 1, 2, 3].map((index) => ({
    x: k.padStart + (index % 2) * (k.card + k.gap),
    y: row1 + Math.floor(index / 2) * (k.card + k.gap),
    size: k.card,
  }));
}

// TripMapScreen (develop), in dp: the header (status inset, logo row with the scope toggle, 14dp, the summary card,
// 6dp) and the 64dp bottom bar. The map area is the space between. The drill card carries the back row
// (7 + 11sp line + 7, then 10dp), so the drill map starts that much lower than the Korea and world maps.
export const HOW_STORY_MAP = Object.freeze({
  statusDp: 38,
  bottomBar: 64,
  cardMargin: 12,
  // Bounds develop fits each view to (KoreaBounds.from: every ring of the regions drawn). Checked against the
  // data files by the tests: the drill view is 경상북도's 22 districts (울릉도 and 독도 included), the Korea view
  // the 17 provinces.
  drillBounds: Object.freeze({ minLng: 127.79615, maxLng: 131.87147, minLat: 35.56689, maxLat: 37.54914 }),
  koreaBounds: Object.freeze({ minLng: 124.61362, maxLng: 130.92066, minLat: 33.19758, maxLat: 38.62434 }),
  // 경주시's label point (KoreaMapArtwork labelPoint()), where the saved copies land.
  gyeongjuLabel: Object.freeze([129.236, 35.8266]),
  // WorldGlobe: radius share of the viewport's shorter side.
  globeRadius: 0.42,
});

export function createAppMapView(lineHeight = HOW_STORY_APP_LINE_HEIGHT) {
  const m = HOW_STORY_MAP;
  const toggle = 1 + 3 + 6 + 11 * lineHeight + 6 + 3 + 1;
  const logoRow = Math.max(18 * lineHeight, toggle);
  const cardTop = m.statusDp + logoRow + 14;
  const koreaCard = 1 + 15 + 11 * lineHeight + 3 + 25 * lineHeight + 14 + 1;
  const backRow = 7 + 11 * lineHeight + 7 + 10;
  const top = cardTop + koreaCard + 6;
  const width = HOW_STORY_SCREEN.widthDp;
  const height = HOW_STORY_SCREEN.heightDp - m.bottomBar - top;
  // One coordinate space for the drill and Korea views: develop's Korea projection of the full map area.
  const korea = createAppMapProjection(m.koreaBounds, width, height);
  // develop's drill projection fills the area below the taller drill card. The drill viewBox is that framing seen
  // in the Korea space: same scale, the bounds' centre and top edge where develop puts them (the drill's own
  // longitude factor differs from Korea's by under 1%).
  const drill = createAppMapProjection(m.drillBounds, width, height - backRow);
  const zoom = drill.scale / korea.scale;
  const [centreX, topY] = korea.project((m.drillBounds.minLng + m.drillBounds.maxLng) / 2, m.drillBounds.maxLat);
  const drillBox = [centreX - width / 2 / zoom, topY - (drill.top + backRow) / zoom, width / zoom, height / zoom];
  const koreaBox = [0, 0, width, height];
  const [labelX, labelY] = korea.project(...m.gyeongjuLabel);
  return {
    cardTop,
    koreaCard,
    drillCard: koreaCard + backRow,
    backRow,
    area: { top, width, height },
    project: korea.project,
    drillBox,
    koreaBox,
    // 경주시's label point in area dp at the drill view.
    gyeongju: { x: (labelX - drillBox[0]) * zoom, y: (labelY - drillBox[1]) * zoom },
    globe: { cx: width / 2, cy: height / 2, r: m.globeRadius * Math.min(width, height) },
  };
}

// The map camera between two viewBoxes: a zoom about their common fixed point, the scale moving
// geometrically so the pull-back reads at an even pace.
export function mapCameraBox(from, to, u) {
  if (u <= 0) return from;
  if (u >= 1) return to;
  const w = from[2] * (to[2] / from[2]) ** u;
  const ratio = w / from[2];
  const span = to[2] - from[2];
  if (Math.abs(span) < 1e-9) return from.map((value, index) => value + (to[index] - value) * u);
  const fx = (from[0] * to[2] - to[0] * from[2]) / span;
  const fy = (from[1] * to[2] - to[1] * from[2]) / span;
  return [fx - (fx - from[0]) * ratio, fy - (fy - from[1]) * ratio, w, from[3] * ratio];
}

export const APP_MAP_VIEW = Object.freeze(createAppMapView());

// Where each found photo sits in the library window, as fractions of the window [fx, fy] (top-left of its cell
// before snapping to the tile grid). All four sit inside the visible band on every target viewport.
export const HOW_STORY_MATCH_CELLS = Object.freeze([
  Object.freeze([0.22, 0.46]),
  Object.freeze([0.7, 0.52]),
  Object.freeze([0.38, 0.63]),
  Object.freeze([0.8, 0.7]),
]);

// The widths how-story.css switches its layout at (min-width media queries).
export const HOW_STORY_BREAKPOINTS = Object.freeze({ tablet: 561, desktop: 901 });

export function getHowStoryMode(viewportWidth) {
  if (viewportWidth >= HOW_STORY_BREAKPOINTS.desktop) return "desktop";
  if (viewportWidth >= HOW_STORY_BREAKPOINTS.tablet) return "tablet";
  return "mobile";
}

// The mode the CSS is in. Media queries measure the viewport including a classic scrollbar, which clientWidth
// leaves out, so the JS asks the same queries rather than comparing a width (901-916px windows used to get the
// desktop grid with tablet geometry).
export function getHowStoryMediaMode(matchMedia) {
  if (matchMedia(`(min-width: ${HOW_STORY_BREAKPOINTS.desktop}px)`).matches) return "desktop";
  if (matchMedia(`(min-width: ${HOW_STORY_BREAKPOINTS.tablet}px)`).matches) return "tablet";
  return "mobile";
}

const screenHeightFor = (screenWidth) => (screenWidth * HOW_STORY_SCREEN.heightDp) / HOW_STORY_SCREEN.widthDp;
const round = (value, digits = 2) => Number(value.toFixed(digits));

// Phone width for a stage, or null when the phone would be too small (the story then falls back to static).
export function fitHowStoryPhone({ mode, stageWidth, stageHeight, viewportWidth }) {
  const { bezel, visibleShare, phoneMax, phoneMin, desktopPhone, desktopMargin, sideRoom } = HOW_STORY_GEOMETRY;
  if (mode === "desktop") {
    const naturalFor = (margins) => ((stageHeight - margins - bezel * 2) * HOW_STORY_SCREEN.widthDp) / HOW_STORY_SCREEN.heightDp + bezel * 2;
    // The largest phone with 28px above and below decides the fit; the size prefers the one that leaves the
    // shadow room (desktopMargin), and a short window may grow it toward the preferred minimum within the 28px.
    const tight = naturalFor(56);
    if (tight < phoneMin.desktop) return null;
    const roomy = naturalFor(desktopMargin.top + desktopMargin.bottom);
    return Math.floor(Math.min(Math.max(roomy, Math.min(tight, desktopPhone[0])), desktopPhone[1], stageWidth - sideRoom));
  }
  const height = stageHeight / visibleShare;
  const screenWidth = ((height - bezel * 2) * HOW_STORY_SCREEN.widthDp) / HOW_STORY_SCREEN.heightDp;
  const width = Math.floor(Math.min(phoneMax[mode] ?? phoneMax.mobile, screenWidth + bezel * 2, viewportWidth - sideRoom));
  return width < phoneMin[mode] ? null : width;
}

// Everything the frame function needs, measured once per resize. Returns null when the phone does not fit.
export function createHowStoryLayout({ mode, stageWidth, stageHeight, viewportWidth }) {
  const phoneWidth = fitHowStoryPhone({ mode, stageWidth, stageHeight, viewportWidth });
  if (phoneWidth === null) return null;
  const g = HOW_STORY_GEOMETRY;
  const screenWidth = phoneWidth - g.bezel * 2;
  const screenHeight = screenHeightFor(screenWidth);
  const phoneHeight = screenHeight + g.bezel * 2;
  const phoneX = Math.round((stageWidth - phoneWidth) / 2);
  // Desktop: 20px above and at least 68px below; a window too short for that splits what room there is.
  const room = stageHeight - phoneHeight;
  const { top: marginTop, bottom: marginBottom } = g.desktopMargin;
  const phoneY = mode === "desktop"
    ? Math.max(0, Math.round(room >= marginTop + marginBottom ? marginTop : (room * marginTop) / (marginTop + marginBottom)))
    : g.phoneTopInset;
  const screen = { x: phoneX + g.bezel, y: phoneY + g.bezel, w: screenWidth, h: screenHeight };
  const kIn = stageWidth / screenWidth;
  const d = { x: stageWidth / 2 - (screen.x + screen.w / 2), y: stageHeight / 2 - (screen.y + screen.h / 2) };

  // The library window: full stage width at the screen's aspect, centred on the stage (taller than it).
  const windowHeight = screenHeightFor(stageWidth);
  const windowY = (stageHeight - windowHeight) / 2;
  const vTop = Math.max(0, (windowHeight - stageHeight) / 2 / windowHeight);
  const tile = g.tile[mode];
  const overlayTop = stageHeight * g.overlayTop;
  const overlayBottom = overlayTop + g.overlayLabel[mode] + g.overlayValue[mode] + g.overlayBarSpace;
  // lift: the cell once its pin has popped (scaled about its centre), where the flight to the cluster starts. On
  // short stages a cell moves down a row or two so its lifted ring stays clear of the overlay's bar.
  const grow = (tile * (g.matchLift - 1)) / 2;
  const lowestClearRow = Math.ceil((overlayBottom + g.matchClear + g.matchHalo + grow - windowY) / tile);
  const cells = HOW_STORY_MATCH_CELLS.map(([fx, fy]) => {
    const x = Math.round((fx * stageWidth) / tile) * tile;
    const y = Math.max(Math.round((fy * windowHeight) / tile), lowestClearRow) * tile;
    // The wave edge reaches a cell's pin once it has passed the pin's bottom.
    return {
      x,
      y,
      stageY: round(y + windowY),
      size: tile,
      popAt: round((y + g.pinOffset + g.pinHeight) / windowHeight, 5),
      lift: Object.freeze({ x: round(x - grow), y: round(y + windowY - grow), size: round(tile * g.matchLift) }),
    };
  });

  const { tile: clusterTile, gap } = g.cluster[mode];
  const clusterLeft = stageWidth / 2 - clusterTile - gap / 2;
  // Centred at 58% of the stage, but never closer than clusterGap below the landed number (short phones).
  const clusterTop = Math.max(
    stageHeight * g.clusterCentreY - clusterTile - gap / 2,
    overlayTop + g.overlayLabel[mode] + g.overlayValue[mode] + g.clusterGap,
  );
  const cluster = HOW_STORY_MATCH_CELLS.map((_, index) => ({
    x: round(clusterLeft + (index % 2) * (clusterTile + gap)),
    y: round(clusterTop + Math.floor(index / 2) * (clusterTile + gap)),
    size: clusterTile,
  }));

  // Beat 04: the picker's cards at rest (the stage tiles land on them), and the hover rects of the three copies
  // lifted at save. Beat 05: 경주시's label point on the drill map, where the copies land.
  const dp = screenWidth / HOW_STORY_SCREEN.widthDp;
  const cards = pickerCardRectsDp().map(({ x, y, size }) => Object.freeze({
    x: round(screen.x + x * dp),
    y: round(screen.y + y * dp),
    size: round(size * dp),
  }));
  const { copyLift, copyScale } = HOW_STORY_PICKER;
  const hover = cards.map(({ x, y, size }) => {
    const lifted = size * copyScale;
    return Object.freeze({ x: round(x + (size - lifted) / 2), y: round(y - copyLift + (size - lifted) / 2), size: round(lifted) });
  });
  const map = APP_MAP_VIEW;
  const mapTarget = { x: round(screen.x + map.gyeongju.x * dp), y: round(screen.y + (map.area.top + map.gyeongju.y) * dp) };

  return Object.freeze({
    mode,
    stageWidth,
    stageHeight,
    viewportWidth,
    phone: Object.freeze({ x: phoneX, y: phoneY, w: phoneWidth, h: round(phoneHeight) }),
    screen: Object.freeze({ x: screen.x, y: screen.y, w: screen.w, h: round(screen.h) }),
    screenRadius: round(phoneWidth * 0.127),
    dp,
    kIn: round(kIn, 5),
    d: Object.freeze({ x: round(d.x), y: round(d.y) }),
    window: Object.freeze({ x: 0, y: round(windowY), w: stageWidth, h: round(windowHeight), vTop: round(vTop, 5), vBottom: round(1 - vTop, 5) }),
    tile,
    cells: Object.freeze(cells.map(Object.freeze)),
    cluster: Object.freeze(cluster.map(Object.freeze)),
    // The flying photos are drawn at the largest size they take (a cluster tile or a picker card) and only ever
    // scaled down, so they stay crisp at rest.
    nodeBase: Math.ceil(Math.max(g.nodeBase, clusterTile, cards[0].size)),
    cards: Object.freeze(cards),
    hover: Object.freeze(hover),
    mapTarget: Object.freeze(mapTarget),
    overlayTop: round(overlayTop),
    overlayBar: g.overlayBar[mode],
    // The loading screen's progress bar: full width inside 34dp side padding, 8dp tall (PhotoLoadingBar).
    loadingBar: Object.freeze({ w: round((HOW_STORY_SCREEN.widthDp - 68) * (screenWidth / HOW_STORY_SCREEN.widthDp)), h: round(8 * (screenWidth / HOW_STORY_SCREEN.widthDp)) }),
    stageRadius: g.stageRadius[mode],
  });
}

// Fixed layouts for tests: 390x844 (stage 390x593) and 1280x800 (stage 600x680).
export const HOW_STORY_TEST_LAYOUT = Object.freeze({
  mobile: createHowStoryLayout({ mode: "mobile", stageWidth: 390, stageHeight: 593, viewportWidth: 390 }),
  desktop: createHowStoryLayout({ mode: "desktop", stageWidth: 600, stageHeight: 680, viewportWidth: 1280 }),
});

// develop's KoreaMapArtwork projection (KoreaProjection.from): longitude squashed by cos(mid latitude),
// 8% side and 6% top/bottom padding, then nudged up by 7% of the height. Returns a lng/lat -> [x, y] function
// in the viewport's own units (dp here).
export function createAppMapProjection(bounds, width, height) {
  const longitudeFactor = Math.max(0.1, Math.cos((((bounds.minLat + bounds.maxLat) / 2) * Math.PI) / 180));
  const spanX = (bounds.maxLng - bounds.minLng) * longitudeFactor;
  const spanY = bounds.maxLat - bounds.minLat;
  const scale = Math.min((width - width * 0.16) / spanX, (height - height * 0.12) / spanY);
  const left = (width - spanX * scale) / 2;
  const top = Math.max((height - spanY * scale) / 2 - height * 0.07, height * 0.06);
  return {
    scale,
    left,
    top,
    longitudeFactor,
    project: (lng, lat) => [left + (lng - bounds.minLng) * longitudeFactor * scale, top + (bounds.maxLat - lat) * scale],
  };
}

export function boundsOfRings(regions) {
  const points = regions.flatMap(({ rings }) => rings.flat());
  return {
    minLng: Math.min(...points.map(([lng]) => lng)),
    maxLng: Math.max(...points.map(([lng]) => lng)),
    minLat: Math.min(...points.map(([, lat]) => lat)),
    maxLat: Math.max(...points.map(([, lat]) => lat)),
  };
}
