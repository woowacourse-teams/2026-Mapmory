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
});

// Saving: the copies of the three picked photos lift off their cards into a small fanned stack of prints over the
// picker's header (the upload), centred between the title row and the date row, clear of both. Screen dp; size is
// the photo's share of a card, and the white print border (how-story.css) sits outside it. While the prints are up,
// the header rows under them are blanked by a band of the app's background (pickerPrintClearDp), so the hold reads as
// one message rather than a stack over clipped button and note fragments (round-3 V4).
export const HOW_STORY_PRINTS = Object.freeze({
  centre: Object.freeze([180, 211]),
  size: 0.55,
  slots: Object.freeze([
    Object.freeze({ dx: -50, dy: 10, r: -8 }),
    Object.freeze({ dx: 50, dy: 6, r: 6 }),
    Object.freeze({ dx: 0, dy: -4, r: -1 }),
  ]),
});

// The picker list's rows as shown on the screen, [top, bottom] in screen dp (the list scrolled by PICKER_SCROLL_DP):
// PHOTO LIBRARY, the title row (경주시 사진 고르기, 3장 선택), the two small buttons, the two-line note, the
// recorded-photo filter row, the date header's row, and the top of the first card row.
export function pickerRowsDp(lineHeight = HOW_STORY_APP_LINE_HEIGHT) {
  const k = HOW_STORY_PICKER;
  let y = k.listTop + k.padTop - PICKER_SCROLL_DP;
  const row = (gap, height) => {
    const top = y + gap;
    y = top + height;
    return Object.freeze([top, y]);
  };
  const eyebrow = row(0, 12 * lineHeight);
  const title = row(14, 25 * lineHeight);
  const buttons = row(20, k.smallButton);
  const note = row(8, 2 * 16);
  const filter = row(0, k.filterRow);
  const date = row(k.dateGap, k.smallButton);
  return Object.freeze({ eyebrow, title, buttons, note, filter, date, cards: y + k.firstRowGap });
}

// The band the prints' clear ground covers, [top, bottom] in screen dp: the buttons, the note and the filter row,
// with its edges halfway into the empty gaps above and below, so over the app's own background it never shows as a
// shape. Its sides are 8dp in from the screen's edges, in the empty margins.
export function pickerPrintClearDp(lineHeight = HOW_STORY_APP_LINE_HEIGHT) {
  const { title, buttons, filter, date } = pickerRowsDp(lineHeight);
  return Object.freeze([(title[1] + buttons[0]) / 2, (filter[1] + date[0]) / 2]);
}

export function pickerCardRectsDp(lineHeight = HOW_STORY_APP_LINE_HEIGHT) {
  const k = HOW_STORY_PICKER;
  const row1 = pickerRowsDp(lineHeight).cards;
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
  // data files by the tests: the drill view is 경상북도's 22 districts, the Korea view the 17 provinces of
  // develop's GeneratedKoreaMapData (how-korea-provinces.json); both include 울릉도 and 독도.
  drillBounds: Object.freeze({ minLng: 127.79615, maxLng: 131.87147, minLat: 35.56689, maxLat: 37.54914 }),
  koreaBounds: Object.freeze({ minLng: 124.60972, maxLng: 131.87023, minLat: 33.11236, maxLat: 38.61371 }),
  // Unverified fidelity: the drill map as a reader leaves it after pinching in on the mainland. develop opens the
  // drill at zoom 1, fitted to bounds that include 울릉도, so the mainland is small and low; its map takes pinch
  // zoom (1-6x) and a pan held by clampKoreaMapPan (PanSlackFraction 0.1), and this is one such state: 2.2x,
  // dragged toward the mainland's middle longitude and the south (경주) until the clamp stops the vertical pan,
  // so the whole mainland shows with 경주 clear of a small phone's cropped bottom.
  drillZoom: 2.2,
  drillCentre: Object.freeze([128.69, 35.93]),
  panSlack: 0.1,
  // WorldGlobe: radius share of the viewport's shorter side.
  globeRadius: 0.42,
  // Unverified fidelity: the globe turned the way a reader drags it to see what was filled. develop's first frame
  // is latitude 0 and zoom 1 (Australia fills it, 대한민국 is a speck at the top); its drag tilts the latitude up to
  // 1.3 rad and its pinch zooms 0.75-8x. 36 degrees brings 대한민국's middle to the centre.
  globeTiltDeg: 36,
  globeZoom: 1.8,
});

// develop's clampKoreaMapPan: a map wider (taller) than the viewport may pan until its edge is 10% of the viewport
// inside; a smaller one only by that 10% slack. map: the projected bounds in viewport units.
export function clampAppMapPan([panX, panY], zoom, viewport, map, slackFraction = HOW_STORY_MAP.panSlack) {
  const axis = (value, leading, trailing, size) => {
    const slack = size * slackFraction;
    if (trailing - leading <= size) return Math.min(slack, Math.max(-slack, value));
    return Math.min(-leading + slack, Math.max(size - trailing - slack, value));
  };
  const cx = viewport.width / 2;
  const cy = viewport.height / 2;
  return [
    axis(panX, cx + (map.left - cx) * zoom, cx + (map.left + map.width - cx) * zoom, viewport.width),
    axis(panY, cy + (map.top - cy) * zoom, cy + (map.top + map.height - cy) * zoom, viewport.height),
  ];
}

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
  // develop's drill projection fills the area below the taller drill card; the reader's zoom and pan act on it
  // about that viewport's centre (transformMapPoint). The drill viewBox is that view seen in the Korea space (the
  // drill's own longitude factor differs from Korea's by under 1%).
  const viewport = { width, height: height - backRow };
  const drill = createAppMapProjection(m.drillBounds, viewport.width, viewport.height);
  const ratio = drill.scale / korea.scale;
  const zoom = m.drillZoom;
  const centre = [viewport.width / 2, viewport.height / 2];
  const [targetX, targetY] = drill.project(...m.drillCentre);
  const pan = clampAppMapPan([(centre[0] - targetX) * zoom, (centre[1] - targetY) * zoom], zoom, viewport, drill);
  // Drill viewport point -> drill base point -> Korea space.
  const [refLng, refLat] = [(m.drillBounds.minLng + m.drillBounds.maxLng) / 2, m.drillBounds.maxLat];
  const [refDrillX, refDrillY] = drill.project(refLng, refLat);
  const [refKoreaX, refKoreaY] = korea.project(refLng, refLat);
  const toKorea = ([x, y]) => {
    const baseX = centre[0] + (x - centre[0] - pan[0]) / zoom;
    const baseY = centre[1] + (y - centre[1] - pan[1]) / zoom;
    return [refKoreaX + (baseX - refDrillX) / ratio, refKoreaY + (baseY - refDrillY) / ratio];
  };
  const [boxX, boxY] = toKorea([0, -backRow]);
  const drillBox = [boxX, boxY, width / (zoom * ratio), height / (zoom * ratio)];
  const koreaBox = [0, 0, width, height];
  return {
    cardTop,
    koreaCard,
    drillCard: koreaCard + backRow,
    backRow,
    area: { top, width, height },
    project: korea.project,
    drillBox,
    koreaBox,
    // The reader's drill camera, in develop's terms, for the tests.
    drillCamera: { viewport, projection: drill, zoom, pan },
    globe: { cx: width / 2, cy: height / 2, r: m.globeRadius * Math.min(width, height) * m.globeZoom },
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
// before snapping to the tile grid). createHowStoryLayout moves a cell a row or two on short stages, so all four
// stay inside the visible band, clear of the overlay, on every stage that fits.
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
  // short stages a cell moves down a row or two so its lifted ring stays clear of the overlay's bar, and up a row
  // or two so its lifted ring stays inside the visible band (the stage's bottom edge): the pin wave stops at the
  // band's bottom, so a cell below it would never pop (round-2 F1: 1366x657 and 768x700 showed three found
  // photos). A band too short for both bounds does not fit (static list).
  const grow = (tile * (g.matchLift - 1)) / 2;
  const lowestClearRow = Math.ceil((overlayBottom + g.matchClear + g.matchHalo + grow - windowY) / tile);
  const bandBottom = (1 - vTop) * windowHeight;
  const highestClearRow = Math.floor((bandBottom - g.matchClear - g.matchHalo - grow - tile) / tile);
  if (highestClearRow < lowestClearRow) return null;
  const cells = HOW_STORY_MATCH_CELLS.map(([fx, fy]) => {
    const x = Math.round((fx * stageWidth) / tile) * tile;
    const y = Math.min(Math.max(Math.round((fy * windowHeight) / tile), lowestClearRow), highestClearRow) * tile;
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

  // Beat 04: the picker's cards at rest (the stage tiles land on them), and the print stack the three copies lift
  // into at save (centre, size and turn of each print; the copies are placed about their centres).
  const dp = screenWidth / HOW_STORY_SCREEN.widthDp;
  const cards = pickerCardRectsDp().map(({ x, y, size }) => Object.freeze({
    x: round(screen.x + x * dp),
    y: round(screen.y + y * dp),
    size: round(size * dp),
  }));
  const prints = HOW_STORY_PRINTS.slots.map(({ dx, dy, r }) => Object.freeze({
    cx: round(screen.x + (HOW_STORY_PRINTS.centre[0] + dx) * dp),
    cy: round(screen.y + (HOW_STORY_PRINTS.centre[1] + dy) * dp),
    size: round(HOW_STORY_PICKER.card * HOW_STORY_PRINTS.size * dp),
    r,
  }));
  const nodeBase = Math.ceil(Math.max(g.nodeBase, clusterTile, cards[0].size));
  // Beat 05 opens with the stack rising until its lowest print (white border, turn and shadow included: under one
  // print size below its centre) is past the stage's top edge.
  const stackRise = Math.ceil(Math.max(...prints.map(({ cy, size }) => cy + size)) + 8);

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
    // scaled down, so they stay crisp at rest. matchK: nodeBase over a lifted cell's size, so the ring and pin a
    // photo carries out of the library (sized in the node) match the library's at the flight's start.
    nodeBase,
    matchK: round(nodeBase / (tile * g.matchLift), 4),
    cards: Object.freeze(cards),
    prints: Object.freeze(prints),
    stackRise,
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
    width: spanX * scale,
    height: spanY * scale,
    longitudeFactor,
    project: (lng, lat) => [left + (lng - bounds.minLng) * longitudeFactor * scale, top + (bounds.maxLat - lat) * scale],
  };
}
