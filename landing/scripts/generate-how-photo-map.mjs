import { readdirSync, readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

// node scripts/generate-how-photo-map.mjs
// Writes src/data/how-photo-map.json: the projected Korea map the #how photo story's last beat fills
// (경주시 -> 경상북도 -> 대한민국). It reproduces the approved sketch's map exactly, in three steps:
//   1. the client's 17 provinces, thinned for the landing (the old #how branch's how-korea-provinces.json),
//      and 경주시 from the client's district file (its gyeongju-boundary.json);
//   2. the sketch's projection and ring split (build_assets.py: STROKE_MIN, OUTLINE_MIN, 울릉도, 독도);
//   3. the sketch page's derived fields (build_html.py: outIdx, tip, gjc).
// Python's number formatting is mirrored where the sketch used it (see pyFixed), so the paths match byte for byte.

const LANDING = fileURLToPath(new URL("..", import.meta.url));
const CLIENT_MAP_DIR = resolve(LANDING, "../client/shared/src/commonMain/kotlin/com/mapmory/shared/presentation/map/data");
const DISTRICTS_47 = resolve(LANDING, "../client/shared/src/commonMain/composeResources/files/korea-districts-47.json");
const OUTPUT = resolve(LANDING, "src/data/how-photo-map.json");
const MAX_BYTES = 50 * 1024;

// ---------- 1. Source data ----------

// The client's provinces (GeneratedKoreaMapData, the same files on main and develop), split across as many part
// files as the client needs: read them all, in order.
export function readClientProvinces() {
  const sources = readdirSync(CLIENT_MAP_DIR)
    .filter((file) => /^GeneratedKoreaMapDataPart\d+\.kt$/.test(file))
    .sort()
    .map((file) => readFileSync(resolve(CLIENT_MAP_DIR, file), "utf8"))
    .join("\n");

  const provinces = [];
  const pointPattern = /p\((-?\d+(?:\.\d+)?)f,\s*(-?\d+(?:\.\d+)?)f\)/g;
  let current = null;
  for (const line of sources.split(/\r?\n/)) {
    const provinceMatch = line.match(/province\("([^"]+)",\s*"([^"]+)"/);
    if (provinceMatch) {
      if (current?.rings.length) provinces.push(current);
      current = { code: provinceMatch[1], name: provinceMatch[2], rings: [] };
    }
    if (!current || !line.includes("listOf(p(")) continue;
    const points = [...line.matchAll(pointPattern)].map((match) => [Number(match[1]), Number(match[2])]);
    if (points.length > 2) current.rings.push(points);
  }
  if (current?.rings.length) provinces.push(current);
  if (provinces.length !== 17) throw new Error(`Expected 17 provinces, parsed ${provinces.length}.`);
  return provinces;
}

function distanceToSegment([x, y], [x1, y1], [x2, y2]) {
  const dx = x2 - x1;
  const dy = y2 - y1;
  const lengthSquared = dx * dx + dy * dy;
  const t = lengthSquared === 0 ? 0 : Math.max(0, Math.min(1, ((x - x1) * dx + (y - y1) * dy) / lengthSquared));
  return Math.hypot(x - (x1 + t * dx), y - (y1 + t * dy));
}

function douglasPeucker(points, tolerance) {
  if (points.length < 3) return points;
  let farthest = 0;
  let index = 0;
  for (let i = 1; i < points.length - 1; i += 1) {
    const distance = distanceToSegment(points[i], points[0], points.at(-1));
    if (distance > farthest) {
      farthest = distance;
      index = i;
    }
  }
  if (farthest <= tolerance) return [points[0], points.at(-1)];
  return [...douglasPeucker(points.slice(0, index + 1), tolerance).slice(0, -1), ...douglasPeucker(points.slice(index), tolerance)];
}

// A closed ring has no natural end points, so split it at the point farthest from its start.
function simplifyClosedRing(ring, tolerance) {
  const open = ring.at(-1)[0] === ring[0][0] && ring.at(-1)[1] === ring[0][1] ? ring.slice(0, -1) : ring;
  let split = 0;
  let farthest = 0;
  open.forEach(([lng, lat], index) => {
    const distance = Math.hypot(lng - open[0][0], lat - open[0][1]);
    if (distance > farthest) {
      farthest = distance;
      split = index;
    }
  });
  const first = douglasPeucker(open.slice(0, split + 1), tolerance);
  const second = douglasPeucker([...open.slice(split), open[0]], tolerance);
  return [...first.slice(0, -1), ...second.slice(0, -1)];
}

const round = (value, digits) => Number(value.toFixed(digits));

// 경주시 from the district file the app bundles for photo search, lightly thinned.
export function gyeongjuBoundary() {
  const GYEONGJU_CODE = "47130";
  const TOLERANCE = 0.003;
  const districts = JSON.parse(readFileSync(DISTRICTS_47, "utf8"));
  const gyeongju = districts.districts.find(({ code }) => code === GYEONGJU_CODE);
  if (!gyeongju) throw new Error(`District ${GYEONGJU_CODE} is missing from ${DISTRICTS_47}.`);
  const rings = gyeongju.rings.filter((ring) => ring.length >= 10).map((ring) => simplifyClosedRing(ring, TOLERANCE));
  return { code: GYEONGJU_CODE, name: gyeongju.name, tolerance: TOLERANCE, rings };
}

// develop's Korea map view of the trip screen, in dp: a 360x780 screen less the 64dp bottom bar and the header
// above the map (status inset, logo row with the scope toggle, 14dp, the summary card, 6dp) at LINE Seed Sans KR's
// 1.35 line height. KoreaMapArtwork fits the bounds into it (KoreaProjection.from: longitude squashed by
// cos(mid latitude), 8% side and 6% top/bottom padding, nudged up by 7% of the height).
function appKoreaView(bounds) {
  const lineHeight = 1.35;
  const toggle = 1 + 3 + 6 + 11 * lineHeight + 6 + 3 + 1;
  const logoRow = Math.max(18 * lineHeight, toggle);
  const cardTop = 38 + logoRow + 14;
  const koreaCard = 1 + 15 + 11 * lineHeight + 3 + 25 * lineHeight + 14 + 1;
  const top = cardTop + koreaCard + 6;
  const width = 360;
  const height = 780 - 64 - top;
  const longitudeFactor = Math.max(0.1, Math.cos((((bounds.minLat + bounds.maxLat) / 2) * Math.PI) / 180));
  const spanX = (bounds.maxLng - bounds.minLng) * longitudeFactor;
  const spanY = bounds.maxLat - bounds.minLat;
  const scale = Math.min((width - width * 0.16) / spanX, (height - height * 0.12) / spanY);
  const mapTop = Math.max((height - spanY * scale) / 2 - height * 0.07, height * 0.06);
  return (lat) => mapTop + (bounds.maxLat - lat) * scale;
}

// The provinces thinned so no source point moves more than 0.4dp at the app's Korea view, each ring a flat list of
// integers in thousandths of a degree: the first point from the origin, then the step from the point before.
export function howProvinces(provinces = readClientProvinces()) {
  const MAX_ERROR_DP = 0.4;
  const points = provinces.flatMap(({ rings }) => rings.flat());
  const bounds = {
    minLng: Math.min(...points.map(([lng]) => lng)),
    maxLng: Math.max(...points.map(([lng]) => lng)),
    minLat: Math.min(...points.map(([, lat]) => lat)),
    maxLat: Math.max(...points.map(([, lat]) => lat)),
  };
  const projectY = appKoreaView(bounds);
  const degreesPerDp = (bounds.maxLat - bounds.minLat) / (projectY(bounds.minLat) - projectY(bounds.maxLat));
  // The thinning tolerance plus the 0.0005-degree rounding stays under MAX_ERROR_DP.
  const tolerance = round(MAX_ERROR_DP * degreesPerDp - 0.0006, 4);
  const origin = [Math.floor(bounds.minLng * 1000), Math.floor(bounds.minLat * 1000)];
  const encode = (ring) => ring.flatMap(([lng, lat], index) => {
    const x = Math.round(lng * 1000) - origin[0];
    const y = Math.round(lat * 1000) - origin[1];
    if (index === 0) return [x, y];
    const [previousLng, previousLat] = ring[index - 1];
    return [x - (Math.round(previousLng * 1000) - origin[0]), y - (Math.round(previousLat * 1000) - origin[1])];
  });
  return {
    tolerance,
    maxErrorDp: MAX_ERROR_DP,
    bounds,
    origin,
    provinces: provinces.map(({ code, name, rings }) => ({
      code,
      name,
      rings: rings
        .map((ring) => simplifyClosedRing(ring, tolerance))
        .filter((ring) => ring.length >= 3)
        .map(encode),
    })),
  };
}

// ---------- 2. Projection and ring split (the sketch's build_assets.py) ----------

// Python's format(value, ".1f") and round(value, 1): correctly rounded like toFixed, but an exact binary tie
// (x.x5 exactly, only possible for values ending in .25 or .75) goes to the even digit, where toFixed goes up.
export function pyFixed1(value) {
  const quarters = value * 4;
  if (Number.isInteger(quarters) && quarters % 2 !== 0) {
    const tenths = Math.abs(value) * 10;
    const low = Math.floor(tenths);
    const even = low % 2 === 0 ? low : low + 1;
    return `${value < 0 ? "-" : ""}${Math.floor(even / 10)}.${even % 10}`;
  }
  return value.toFixed(1);
}
const pyRound1 = (value) => Number(pyFixed1(value));

const LON0 = 124.55;
const LON1 = 131.95;
const LAT0 = 33.05;
const LAT1 = 38.66;
const COSL = Math.cos(36.0 * (Math.PI / 180));
const VW = 600;
const K = VW / ((LON1 - LON0) * COSL);
const VH = pyRound1((LAT1 - LAT0) * K);
const project = ([lon, lat]) => [(lon - LON0) * COSL * K, (LAT1 - lat) * K];
const ringPath = (points) => `M${points.map(([x, y]) => `${pyFixed1(x)} ${pyFixed1(y)}`).join("L")}Z`;
function ringArea(points) {
  let sum = 0;
  for (let i = 0; i < points.length; i += 1) {
    const [x1, y1] = points[i];
    const [x2, y2] = points[(i + 1) % points.length];
    sum += x1 * y2 - x2 * y1;
  }
  return Math.abs(sum) / 2;
}

// Rings smaller than this (about 6 map units across, ~4 css px) get no white stroke.
const STROKE_MIN = 40;
// The glowing coast outline: the mainland, 제주, 강화, 거제, 진도, 남해 ... plus 울릉도.
const OUTLINE_MIN = 240;
// 독도: two tiny islets, drawn as circles (the client's data has no ring for them at this scale).
const DOKDO = "M591.0 142.4a1.7 1.7 0 1 0 3.4 0a1.7 1.7 0 1 0-3.4 0ZM594.6 141.6a1.3 1.3 0 1 0 2.6 0a1.3 1.3 0 1 0-2.6 0Z";

function splitRings(how, gyeongju) {
  const [ox, oy] = how.origin;
  const decode = (ring) => {
    const points = [];
    let x = 0;
    let y = 0;
    for (let i = 0; i < ring.length; i += 2) {
      x += ring[i];
      y += ring[i + 1];
      points.push([(ox + x) / 1000, (oy + y) / 1000]);
    }
    return points;
  };
  const landBig = [];
  const landSmall = [];
  const outline = [];
  const gbBig = [];
  const gbSmall = [];
  for (const province of how.provinces) {
    for (const ring of province.rings) {
      const points = decode(ring).map(project);
      const area = ringArea(points);
      const path = ringPath(points);
      const isUlleung = province.code === "KR-47" && Math.min(...points.map(([x]) => x)) > 480;
      (area >= STROKE_MIN ? landBig : landSmall).push(path);
      if (area >= OUTLINE_MIN || isUlleung) outline.push(path);
      if (province.code === "KR-47") (area >= STROKE_MIN && !isUlleung ? gbBig : gbSmall).push(path);
    }
  }
  landSmall.push(DOKDO);
  gbSmall.push(DOKDO);
  const gjRings = gyeongju.rings.map((ring) => ring.map(project));
  const gjPoints = gjRings.flat();
  const gjBox = [
    Math.min(...gjPoints.map(([x]) => x)),
    Math.min(...gjPoints.map(([, y]) => y)),
    Math.max(...gjPoints.map(([x]) => x)),
    Math.max(...gjPoints.map(([, y]) => y)),
  ].map(pyRound1);
  return {
    landBig: landBig.join(""),
    landSmall: landSmall.join(""),
    out: outline.join(""),
    gbBig: gbBig.join(""),
    gbSmall: gbSmall.join(""),
    gj: gjRings.map(ringPath).join(""),
    gjBox,
  };
}

// ---------- 3. Derived fields (the sketch's build_html.py) ----------

export function buildHowPhotoMap({ how = howProvinces(), gyeongju = gyeongjuBoundary() } = {}) {
  const split = splitRings(how, gyeongju);
  const [x0, y0, x1, y1] = split.gjBox;
  const gjc = [pyRound1((x0 + x1) / 2), pyRound1((y0 + y1) / 2)];
  // The coast outline is a subset of landBig's rings, so it ships as ring indices, leaving out rings under 12 map
  // units across (울릉도): a 5px mint coast turns those into a solid dot that reads like a GPS pin.
  const rings = (path) => path.match(/M[^M]*/g) ?? [];
  const span = (ring) => {
    const values = ring.match(/-?[\d.]+/g).map(Number);
    const xs = values.filter((_, index) => index % 2 === 0);
    const ys = values.filter((_, index) => index % 2 === 1);
    return Math.max(Math.max(...xs) - Math.min(...xs), Math.max(...ys) - Math.min(...ys));
  };
  const big = rings(split.landBig);
  const outlineRings = rings(split.out);
  const outIdx = outlineRings.filter((ring) => span(ring) >= 12).map((ring) => big.indexOf(ring));
  if (outIdx.includes(-1) || outIdx.length !== outlineRings.length - 1) {
    throw new Error("The coast outline must be landBig rings, less 울릉도.");
  }
  return {
    w: VW,
    h: VH,
    landBig: split.landBig,
    landSmall: split.landSmall,
    gbBig: split.gbBig,
    gbSmall: split.gbSmall,
    gj: split.gj,
    outIdx,
    // The tag's point sits just inside 경주's northern edge, so 경주시 stays visible below it.
    tip: [gjc[0], pyRound1(y0 + 4.4)],
    gjc,
  };
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? "").href) {
  const map = buildHowPhotoMap();
  const text = `${JSON.stringify(map)}\n`;
  if (Buffer.byteLength(text) > MAX_BYTES) throw new Error(`how-photo-map.json is ${Buffer.byteLength(text)} bytes, over ${MAX_BYTES}.`);
  writeFileSync(OUTPUT, text, "utf8");
  console.log(`Generated how-photo-map.json (${Buffer.byteLength(text)} bytes, ${map.outIdx.length} coast rings).`);
}
