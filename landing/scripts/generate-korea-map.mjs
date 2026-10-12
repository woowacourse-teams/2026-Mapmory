import { readdirSync, readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";

// node scripts/generate-korea-map.mjs [provinces] [gyeongju]; no argument runs both steps.
const steps = new Set(process.argv.slice(2));
const runStep = (name) => steps.size === 0 || steps.has(name);

if (runStep("provinces")) {
  const sourceDir = resolve(
    "../client/shared/src/commonMain/kotlin/com/mapmory/shared/presentation/map/data",
  );

  // The client splits the provinces across as many part files as it needs; read them all in order.
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
