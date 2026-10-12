import { readdirSync, readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";

import { APP_MAP_VIEW, HOW_STORY_MAP } from "../src/howStoryLayout.js";

// node scripts/generate-korea-map.mjs [provinces] [gyeongju] [gyeongbuk] [how-provinces] [globe]; no argument runs
// every step. Run from landing/ (the client sources are read from ../client).
const steps = new Set(process.argv.slice(2));
const runStep = (name) => steps.size === 0 || steps.has(name);

// The client's 17 provinces (GeneratedKoreaMapData, the same files on main and develop). The client splits them
// across as many part files as it needs; read them all in order.
function readClientProvinces() {
  const sourceDir = resolve(
    "../client/shared/src/commonMain/kotlin/com/mapmory/shared/presentation/map/data",
  );
  const sources = readdirSync(sourceDir)
    .filter((file) => /^GeneratedKoreaMapDataPart\d+\.kt$/.test(file))
    .sort()
    .map((file) => readFileSync(resolve(sourceDir, file), "utf8"))
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

    const points = [...line.matchAll(pointPattern)].map((match) => [
      Number(match[1]),
      Number(match[2]),
    ]);
    if (points.length > 2) current.rings.push(points);
  }

  if (current?.rings.length) provinces.push(current);

  if (provinces.length !== 17) {
    throw new Error(`Expected 17 provinces, parsed ${provinces.length}.`);
  }
  return provinces;
}

// The hero's map. Left as it was generated: the hero is not regenerated with the #how data.
if (runStep("provinces")) {
  const provinces = readClientProvinces();
  writeFileSync(
    resolve("src/data/korea-provinces.json"),
    `${JSON.stringify(provinces)}\n`,
    "utf8",
  );

  console.log(`Generated ${provinces.length} provinces.`);
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

if (runStep("gyeongju")) {
  // The #how story compares its example photos with the same 경주시 boundary the app bundles for photo search.
  const GYEONGJU_CODE = "47130";
  const GYEONGJU_TOLERANCE = 0.003;
  const districtSource = "../client/shared/src/commonMain/composeResources/files/korea-districts-47.json";
  const districts = JSON.parse(readFileSync(resolve(districtSource), "utf8"));
  const gyeongju = districts.districts.find(({ code }) => code === GYEONGJU_CODE);
  if (!gyeongju) throw new Error(`District ${GYEONGJU_CODE} is missing from ${districtSource}.`);

  const rings = gyeongju.rings
    .filter((ring) => ring.length >= 10)
    .map((ring) => simplifyClosedRing(ring, GYEONGJU_TOLERANCE));

  writeFileSync(
    resolve("src/data/gyeongju-boundary.json"),
    `${JSON.stringify({
      code: GYEONGJU_CODE,
      name: gyeongju.name,
      provinceCode: districts.provinceCode,
      source: "client korea-districts-47.json",
      tolerance: GYEONGJU_TOLERANCE,
      rings,
    })}\n`,
    "utf8",
  );

  console.log(`Generated ${gyeongju.name} boundary with ${rings.map((ring) => ring.length).join(", ")} points.`);
}

const round = (value, digits) => Number(value.toFixed(digits));

// The same label point develop's KoreaMapArtwork uses (labelPoint()): the largest ring's area centroid when
// it falls inside that ring, else the ring's bounding-box centre when inside, else the ring's first point.
function signedAreaTwice(ring) {
  let sum = 0;
  ring.forEach(([x, y], index) => {
    const [nx, ny] = ring[(index + 1) % ring.length];
    sum += x * ny - nx * y;
  });
  return sum;
}
function insideRing([px, py], ring) {
  let inside = false;
  let [qx, qy] = ring.at(-1);
  for (const [x, y] of ring) {
    if ((y > py) !== (qy > py) && px < ((qx - x) * (py - y)) / (qy - y) + x) inside = !inside;
    qx = x;
    qy = y;
  }
  return inside;
}
function labelPoint(rings) {
  const largest = rings.reduce((best, ring) => (Math.abs(signedAreaTwice(ring)) > Math.abs(signedAreaTwice(best)) ? ring : best));
  const area = signedAreaTwice(largest);
  if (Math.abs(area) >= 0.000001) {
    let cx = 0;
    let cy = 0;
    largest.forEach(([x, y], index) => {
      const [nx, ny] = largest[(index + 1) % largest.length];
      const cross = x * ny - nx * y;
      cx += (x + nx) * cross;
      cy += (y + ny) * cross;
    });
    const centroid = [cx / (3 * area), cy / (3 * area)];
    if (insideRing(centroid, largest)) return centroid;
  }
  const xs = largest.map(([x]) => x);
  const ys = largest.map(([, y]) => y);
  const centre = [(Math.min(...xs) + Math.max(...xs)) / 2, (Math.min(...ys) + Math.max(...ys)) / 2];
  return insideRing(centre, largest) ? centre : largest[0];
}

if (runStep("gyeongbuk")) {
  // The #how map screen's drill view: 경상북도's 22 시·군 from the district file develop draws (the file is the
  // same on main and develop), simplified for the landing.
  const TOLERANCE = 0.005;
  const MAX_BYTES = 30 * 1024;
  const districtSource = "../client/shared/src/commonMain/composeResources/files/korea-districts-47.json";
  const source = JSON.parse(readFileSync(resolve(districtSource), "utf8"));
  const districts = source.districts.map(({ code, name, rings }) => ({
    code,
    name,
    labelPoint: labelPoint(rings).map((value) => round(value, 4)),
    rings: rings
      .filter((ring) => ring.length >= 10)
      .map((ring) => simplifyClosedRing(ring, TOLERANCE).map(([lng, lat]) => [round(lng, 3), round(lat, 3)]))
      // Drop slivers the simplification collapsed (a 4-point ring with no area).
      .filter((ring) => ring.length >= 4 && Math.abs(signedAreaTwice(ring)) > 0.00002),
  }));
  if (districts.length !== 22) throw new Error(`Expected 22 districts in ${districtSource}, got ${districts.length}.`);
  // develop fits the drill map to the bounds of every raw ring (울릉도 and 독도 included), so keep them.
  const points = source.districts.flatMap(({ rings }) => rings.flat());
  const bounds = {
    minLng: Math.min(...points.map(([lng]) => lng)),
    maxLng: Math.max(...points.map(([lng]) => lng)),
    minLat: Math.min(...points.map(([, lat]) => lat)),
    maxLat: Math.max(...points.map(([, lat]) => lat)),
  };
  const text = `${JSON.stringify({ provinceCode: source.provinceCode, source: "client korea-districts-47.json", tolerance: TOLERANCE, bounds, districts })}\n`;
  if (Buffer.byteLength(text) > MAX_BYTES) throw new Error(`gyeongbuk-districts.json is ${Buffer.byteLength(text)} bytes, over ${MAX_BYTES}.`);
  writeFileSync(resolve("src/data/gyeongbuk-districts.json"), text, "utf8");
  console.log(`Generated ${districts.length} 경상북도 districts (${Buffer.byteLength(text)} bytes).`);
}

if (runStep("how-provinces")) {
  // The #how map screen's Korea view: develop's provinces as the app draws them (울릉도 and 독도 included, and 강원's
  // southern edge at 37.03 degrees), thinned so no source point moves more than 0.4dp at the Korea view. The
  // landing's older korea-provinces.json (the hero's) predates that data.
  const MAX_BYTES = 30 * 1024;
  const MAX_ERROR_DP = 0.4;
  const provinces = readClientProvinces();
  const points = provinces.flatMap(({ rings }) => rings.flat());
  const bounds = {
    minLng: Math.min(...points.map(([lng]) => lng)),
    maxLng: Math.max(...points.map(([lng]) => lng)),
    minLat: Math.min(...points.map(([, lat]) => lat)),
    maxLat: Math.max(...points.map(([, lat]) => lat)),
  };
  for (const key of Object.keys(bounds)) {
    if (bounds[key] !== HOW_STORY_MAP.koreaBounds[key]) throw new Error(`HOW_STORY_MAP.koreaBounds.${key} is ${HOW_STORY_MAP.koreaBounds[key]}, the data says ${bounds[key]}.`);
  }
  // Degrees per dp at the Korea view, in latitude (the longer of the two: longitude is squashed by cos(lat)).
  const [, yTop] = APP_MAP_VIEW.project(bounds.minLng, bounds.maxLat);
  const [, yBottom] = APP_MAP_VIEW.project(bounds.minLng, bounds.minLat);
  const degreesPerDp = (bounds.maxLat - bounds.minLat) / (yBottom - yTop);
  // The thinning tolerance plus the 0.0005-degree rounding stays under MAX_ERROR_DP.
  const TOLERANCE = round(MAX_ERROR_DP * degreesPerDp - 0.0006, 4);
  // Each ring is a flat list of integers in thousandths of a degree: the first point from the origin, then the
  // step from the point before (decodeSteps in src/how/mapData.js reverses it).
  const origin = [Math.floor(bounds.minLng * 1000), Math.floor(bounds.minLat * 1000)];
  const encode = (ring) => ring.flatMap(([lng, lat], index) => {
    const x = Math.round(lng * 1000) - origin[0];
    const y = Math.round(lat * 1000) - origin[1];
    if (index === 0) return [x, y];
    const [previousLng, previousLat] = ring[index - 1];
    return [x - (Math.round(previousLng * 1000) - origin[0]), y - (Math.round(previousLat * 1000) - origin[1])];
  });
  const out = provinces.map(({ code, name, rings }) => ({
    code,
    name,
    rings: rings
      .map((ring) => simplifyClosedRing(ring, TOLERANCE))
      .filter((ring) => ring.length >= 3)
      .map(encode),
  }));
  const text = `${JSON.stringify({ source: "client GeneratedKoreaMapData", tolerance: TOLERANCE, maxErrorDp: MAX_ERROR_DP, bounds, origin, provinces: out })}\n`;
  if (Buffer.byteLength(text) > MAX_BYTES) throw new Error(`how-korea-provinces.json is ${Buffer.byteLength(text)} bytes, over ${MAX_BYTES}.`);
  writeFileSync(resolve("src/data/how-korea-provinces.json"), text, "utf8");
  const after = out.reduce((sum, { rings }) => sum + rings.reduce((n, ring) => n + ring.length / 2, 0), 0);
  console.log(`Generated ${out.length} provinces for #how (${points.length} -> ${after} points, ${Buffer.byteLength(text)} bytes).`);
}

// The #how map screen's world view. develop draws the world as a globe (MapArtwork -> WorldGlobe) from Natural
// Earth 110m countries (GeneratedWorldMapData*.kt, the same files on main and develop). The story shows it turned and
// pinched in (HOW_STORY_MAP.globeTiltDeg and globeZoom, unverified fidelity), so it is projected once here with
// WorldGlobe's own math (toSphere, the longitude turn then the latitude tilt, clipToFrontHemisphere,
// projectToScreen) and stored as rings on a disc of radius 1000 (x right, y up), each ring a flat list of integers:
// the first point, then the step from the point before (decodeSteps in src/how/mapData.js). Only what the map area
// can show is kept.
const GLOBE_LONGITUDE = -127;
const GLOBE_LATITUDE = HOW_STORY_MAP.globeTiltDeg;
function globePoint([lng, lat]) {
  const lambda = (lng * Math.PI) / 180;
  const phi = (lat * Math.PI) / 180;
  const theta = (GLOBE_LONGITUDE * Math.PI) / 180;
  const tilt = (GLOBE_LATITUDE * Math.PI) / 180;
  const x = Math.cos(phi) * Math.sin(lambda);
  const y = Math.sin(phi);
  const z = Math.cos(phi) * Math.cos(lambda);
  // (tiltRotation * polarRotation).rotate: a turn by theta about the y axis, then a tilt about the x axis.
  const px = x * Math.cos(theta) + z * Math.sin(theta);
  const pz = -x * Math.sin(theta) + z * Math.cos(theta);
  return [px, y * Math.cos(tilt) - pz * Math.sin(tilt), y * Math.sin(tilt) + pz * Math.cos(tilt)];
}
function horizonIntersection(from, to) {
  const denominator = from[2] - to[2];
  if (Math.abs(denominator) <= 0.00001) return from;
  const t = Math.min(1, Math.max(0, from[2] / denominator));
  const point = from.map((value, index) => value + (to[index] - value) * t);
  const length = Math.hypot(point[0], point[1]);
  return length <= 0.00001 ? [0, 0, 0] : [point[0] / length, point[1] / length, 0];
}
function clipToFrontHemisphere(points) {
  const result = [];
  let previous = points.at(-1);
  let previousVisible = previous[2] >= 0;
  for (const current of points) {
    const currentVisible = current[2] >= 0;
    if (previousVisible && currentVisible) result.push(current);
    else if (previousVisible && !currentVisible) result.push(horizonIntersection(previous, current));
    else if (!previousVisible && currentVisible) result.push(horizonIntersection(previous, current), current);
    previous = current;
    previousVisible = currentVisible;
  }
  return result;
}
// Sutherland-Hodgman against an axis-aligned box: a filled ring stays filled inside the box, and the box sits
// outside the visible area, so the cut edges are never seen.
function clipToBox(ring, [minX, minY, maxX, maxY]) {
  const edges = [
    [(p) => p[0] >= minX, (a, b) => [minX, a[1] + ((b[1] - a[1]) * (minX - a[0])) / (b[0] - a[0])]],
    [(p) => p[0] <= maxX, (a, b) => [maxX, a[1] + ((b[1] - a[1]) * (maxX - a[0])) / (b[0] - a[0])]],
    [(p) => p[1] >= minY, (a, b) => [a[0] + ((b[0] - a[0]) * (minY - a[1])) / (b[1] - a[1]), minY]],
    [(p) => p[1] <= maxY, (a, b) => [a[0] + ((b[0] - a[0]) * (maxY - a[1])) / (b[1] - a[1]), maxY]],
  ];
  let points = ring;
  for (const [inside, cut] of edges) {
    if (points.length === 0) break;
    const next = [];
    let previous = points.at(-1);
    for (const current of points) {
      if (inside(current)) {
        if (!inside(previous)) next.push(cut(previous, current));
        next.push(current);
      } else if (inside(previous)) next.push(cut(previous, current));
      previous = current;
    }
    points = next;
  }
  return points;
}

if (runStep("globe")) {
  const MAX_BYTES = 25 * 1024;
  // The visible part of the disc: the map area around the globe's centre, in disc units, plus a margin.
  const { cx, cy, r } = APP_MAP_VIEW.globe;
  const margin = 0.04;
  const view = [-cx / r - margin, -(APP_MAP_VIEW.area.height - cy) / r - margin, (APP_MAP_VIEW.area.width - cx) / r + margin, cy / r + margin];
  // In disc units: under half a dp at the drawn radius.
  const TOLERANCE = round(0.45 / r, 4);
  const sourceDir = resolve("../client/shared/src/commonMain/kotlin/com/mapmory/shared/presentation/map/data");
  const sources = readdirSync(sourceDir)
    .filter((file) => /^GeneratedWorldMapDataPart\d+\.kt$/.test(file))
    .sort()
    .map((file) => readFileSync(resolve(sourceDir, file), "utf8"))
    .join("\n");
  const pointPattern = /p\((-?\d+(?:\.\d+)?)f,\s*(-?\d+(?:\.\d+)?)f\)/g;
  const parsed = [];
  let current = null;
  for (const line of sources.split(/\r?\n/)) {
    const countryMatch = line.match(/country\("([^"]+)",\s*"([^"]+)"/);
    if (countryMatch) {
      current = { code: countryMatch[1], name: countryMatch[2], rings: [] };
      parsed.push(current);
    }
    if (!current || !line.includes("listOf(p(")) continue;
    const points = [...line.matchAll(pointPattern)].map((match) => [Number(match[1]), Number(match[2])]);
    if (points.length > 2) current.rings.push(points);
  }
  if (parsed.length < 170) throw new Error(`Expected the 110m world, parsed ${parsed.length} countries.`);
  const countries = parsed.flatMap(({ code, name, rings }) => {
    const visible = rings
      .map((ring) => clipToFrontHemisphere(ring.map(globePoint)))
      .filter((ring) => ring.length >= 3)
      .map((ring) => clipToBox(ring.map(([x, y]) => [x, y]), view))
      .filter((ring) => ring.length >= 3)
      .map((ring) => simplifyClosedRing(ring, TOLERANCE).map(([x, y]) => [Math.round(x * 1000), Math.round(y * 1000)]))
      .filter((ring) => ring.length >= 3 && Math.abs(signedAreaTwice(ring)) > 4)
      .map((ring) => ring.flatMap(([x, y], index) => (index === 0 ? [x, y] : [x - ring[index - 1][0], y - ring[index - 1][1]])));
    return visible.length ? [{ code, name, rings: visible }] : [];
  });
  if (!countries.some(({ code }) => code === "KR")) throw new Error("South Korea (KR) is missing from the globe.");
  const text = `${JSON.stringify({ source: "client GeneratedWorldMapData (Natural Earth 110m)", longitude: GLOBE_LONGITUDE, latitude: GLOBE_LATITUDE, zoom: HOW_STORY_MAP.globeZoom, tolerance: TOLERANCE, radius: 1000, view: view.map((value) => round(value, 3)), countries })}\n`;
  if (Buffer.byteLength(text) > MAX_BYTES) throw new Error(`world-globe.json is ${Buffer.byteLength(text)} bytes, over ${MAX_BYTES}.`);
  writeFileSync(resolve("src/data/world-globe.json"), text, "utf8");
  console.log(`Generated ${countries.length} countries on the globe's front (${Buffer.byteLength(text)} bytes).`);
}
