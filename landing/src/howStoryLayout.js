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
  phoneMin: Object.freeze({ mobile: 220, tablet: 220, desktop: 260 }),
  desktopPhone: Object.freeze([280, 340]),
  desktopMargin: 28,
  sideRoom: 40,
  tile: Object.freeze({ mobile: 16, tablet: 16, desktop: 18 }),
  cluster: Object.freeze({
    mobile: Object.freeze({ tile: 84, gap: 10 }),
    tablet: Object.freeze({ tile: 84, gap: 10 }),
    desktop: Object.freeze({ tile: 120, gap: 14 }),
  }),
  clusterCentreY: 0.58,
  overlayTop: 0.16,
  stageRadius: Object.freeze({ mobile: 24, tablet: 24, desktop: 32 }),
  // The flying match tiles are drawn at this size and only ever scaled down, so they stay crisp at rest.
  nodeBase: 120,
  pinOffset: 2,
  pinHeight: 8,
});

// Where each found photo sits in the library window, as fractions of the window [fx, fy] (top-left of its cell
// before snapping to the tile grid). All four sit inside the visible band on every target viewport.
export const HOW_STORY_MATCH_CELLS = Object.freeze([
  Object.freeze([0.22, 0.46]),
  Object.freeze([0.7, 0.52]),
  Object.freeze([0.38, 0.63]),
  Object.freeze([0.8, 0.7]),
]);

export function getHowStoryMode(viewportWidth) {
  if (viewportWidth >= 901) return "desktop";
  if (viewportWidth >= 561) return "tablet";
  return "mobile";
}

const screenHeightFor = (screenWidth) => (screenWidth * HOW_STORY_SCREEN.heightDp) / HOW_STORY_SCREEN.widthDp;
const round = (value, digits = 2) => Number(value.toFixed(digits));

// Phone width for a stage, or null when the phone would be too small (the story then falls back to static).
export function fitHowStoryPhone({ mode, stageWidth, stageHeight, viewportWidth }) {
  const { bezel, visibleShare, phoneMax, phoneMin, desktopPhone, desktopMargin, sideRoom } = HOW_STORY_GEOMETRY;
  if (mode === "desktop") {
    const height = stageHeight - desktopMargin * 2;
    const screenWidth = ((height - bezel * 2) * HOW_STORY_SCREEN.widthDp) / HOW_STORY_SCREEN.heightDp;
    const natural = screenWidth + bezel * 2;
    if (natural < phoneMin.desktop) return null;
    return Math.floor(Math.min(Math.max(natural, desktopPhone[0]), desktopPhone[1], stageWidth - sideRoom));
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
  const phoneY = mode === "desktop" ? Math.round((stageHeight - phoneHeight) / 2) : g.phoneTopInset;
  const screen = { x: phoneX + g.bezel, y: phoneY + g.bezel, w: screenWidth, h: screenHeight };
  const kIn = stageWidth / screenWidth;
  const d = { x: stageWidth / 2 - (screen.x + screen.w / 2), y: stageHeight / 2 - (screen.y + screen.h / 2) };

  // The library window: full stage width at the screen's aspect, centred on the stage (taller than it).
  const windowHeight = screenHeightFor(stageWidth);
  const windowY = (stageHeight - windowHeight) / 2;
  const vTop = Math.max(0, (windowHeight - stageHeight) / 2 / windowHeight);
  const tile = g.tile[mode];
  const cells = HOW_STORY_MATCH_CELLS.map(([fx, fy]) => {
    const x = Math.round((fx * stageWidth) / tile) * tile;
    const y = Math.round((fy * windowHeight) / tile) * tile;
    // The wave edge reaches a cell's pin once it has passed the pin's bottom.
    return { x, y, stageY: round(y + windowY), size: tile, popAt: round((y + g.pinOffset + g.pinHeight) / windowHeight, 5) };
  });

  const { tile: clusterTile, gap } = g.cluster[mode];
  const clusterLeft = stageWidth / 2 - clusterTile - gap / 2;
  const clusterTop = stageHeight * g.clusterCentreY - clusterTile - gap / 2;
  const cluster = HOW_STORY_MATCH_CELLS.map((_, index) => ({
    x: round(clusterLeft + (index % 2) * (clusterTile + gap)),
    y: round(clusterTop + Math.floor(index / 2) * (clusterTile + gap)),
    size: clusterTile,
  }));

  return Object.freeze({
    mode,
    stageWidth,
    stageHeight,
    viewportWidth,
    phone: Object.freeze({ x: phoneX, y: phoneY, w: phoneWidth, h: round(phoneHeight) }),
    screen: Object.freeze({ x: screen.x, y: screen.y, w: screen.w, h: round(screen.h) }),
    screenRadius: round(phoneWidth * 0.127),
    dp: screenWidth / HOW_STORY_SCREEN.widthDp,
    kIn: round(kIn, 5),
    d: Object.freeze({ x: round(d.x), y: round(d.y) }),
    window: Object.freeze({ x: 0, y: round(windowY), w: stageWidth, h: round(windowHeight), vTop: round(vTop, 5), vBottom: round(1 - vTop, 5) }),
    tile,
    cells: Object.freeze(cells.map(Object.freeze)),
    cluster: Object.freeze(cluster.map(Object.freeze)),
    nodeBase: g.nodeBase,
    overlayTop: round(stageHeight * g.overlayTop),
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
  return (lng, lat) => [left + (lng - bounds.minLng) * longitudeFactor * scale, top + (bounds.maxLat - lat) * scale];
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
