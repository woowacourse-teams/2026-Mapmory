// The #how map screen's geometry: Gyeongsangbuk-do's districts (the drill view), develop's 17 provinces (the Korea view) and
// the pre-projected globe (the world view), as SVG paths in the map area's dp. The data files load lazily, once,
// when the story's track first comes near (the scroll hook) or a static map figure mounts, so none of it is in the
// entry chunk; until then the map's groups render empty.
import { useEffect, useState } from "react";
import { createCachedAsyncLoader } from "../cachedAsyncLoader.js";
import { APP_MAP_VIEW } from "../howStoryLayout.js";

const VIEW = APP_MAP_VIEW;
const GYEONGJU = "47130";

// A flat list of integers (the first point, then the step from the point before) back to [x, y] pairs.
export function decodeSteps(flat) {
  const points = [];
  let x = 0;
  let y = 0;
  for (let index = 0; index < flat.length; index += 2) {
    x += flat[index];
    y += flat[index + 1];
    points.push([x, y]);
  }
  return points;
}

const pathOf = (rings) => rings.map((ring) => `${ring.map(([x, y], index) => `${index ? "L" : "M"}${x.toFixed(1)} ${y.toFixed(1)}`).join("")}Z`).join("");
const projectRings = (rings) => rings.map((ring) => ring.map(([lng, lat]) => VIEW.project(lng, lat)));

// Everything MapScreen draws, from the three data files (pure, so the tests build it straight from the JSON).
export function buildHowMapData({ gyeongbuk, provinces, globe }) {
  // The drill view's labels sit in the area at that view (develop draws them at a fixed 10sp on the transformed
  // label points), so the layer needs no scale at rest.
  const drillZoom = VIEW.area.width / VIEW.drillBox[2];
  const districts = gyeongbuk.districts.map(({ code, name, rings, labelPoint }) => {
    const [x, y] = VIEW.project(labelPoint[0], labelPoint[1]);
    return { code, name, d: pathOf(projectRings(rings)), label: [(x - VIEW.drillBox[0]) * drillZoom, (y - VIEW.drillBox[1]) * drillZoom] };
  });
  const [originX, originY] = provinces.origin;
  const koreaProvinces = provinces.provinces.map(({ code, name, rings }) => ({
    code,
    name,
    d: pathOf(rings.map((ring) => decodeSteps(ring).map(([x, y]) => VIEW.project((x + originX) / 1000, (y + originY) / 1000)))),
  }));
  // Gyeongju fills from its label point: the transform origin as a share of its own box (transform-box: fill-box).
  const gyeongju = gyeongbuk.districts.find(({ code }) => code === GYEONGJU);
  const points = projectRings(gyeongju.rings).flat();
  const [minX, maxX] = [Math.min(...points.map(([x]) => x)), Math.max(...points.map(([x]) => x))];
  const [minY, maxY] = [Math.min(...points.map(([, y]) => y)), Math.max(...points.map(([, y]) => y))];
  const [labelX, labelY] = VIEW.project(...gyeongju.labelPoint);
  // The globe at its drawn radius (develop's 0.42 of the area's shorter side, times the story's zoom).
  const { cx, cy, r } = VIEW.globe;
  const scale = r / globe.radius;
  const globePath = (rings) => pathOf(rings.map((ring) => decodeSteps(ring).map(([x, y]) => [cx + x * scale, cy - y * scale])));
  return {
    districts,
    districtLand: districts.map(({ d }) => d).join(""),
    visitedDistrict: districts.find(({ code }) => code === GYEONGJU).d,
    fillOrigin: `${(((labelX - minX) / (maxX - minX)) * 100).toFixed(1)}% ${(((labelY - minY) / (maxY - minY)) * 100).toFixed(1)}%`,
    provinces: koreaProvinces,
    visitedProvince: koreaProvinces.find(({ code }) => code === gyeongbuk.provinceCode).d,
    globeLand: globe.countries.filter(({ code }) => code !== "KR").map(({ rings }) => globePath(rings)).join(""),
    globeKorea: globePath(globe.countries.find(({ code }) => code === "KR").rings),
  };
}

let loaded = null;
const listeners = new Set();
const loadOnce = createCachedAsyncLoader(() => Promise.all([
  import("../data/gyeongbuk-districts.json"),
  import("../data/how-korea-provinces.json"),
  import("../data/world-globe.json"),
]).then(([{ default: gyeongbuk }, { default: provinces }, { default: globe }]) => buildHowMapData({ gyeongbuk, provinces, globe })));

export function loadHowMapData() {
  return loadOnce().then((data) => {
    if (!loaded) {
      loaded = data;
      listeners.forEach((listener) => listener(data));
    }
    return data;
  });
}

// The map data once it has loaded (null before). eager: start the load now (a static figure); otherwise wait for
// the scroll hook to start it as the track comes near.
export function useHowMapData(eager = false) {
  const [data, setData] = useState(loaded);
  useEffect(() => {
    if (loaded) {
      setData(loaded);
      return undefined;
    }
    listeners.add(setData);
    if (eager) loadHowMapData().catch(() => {});
    return () => {
      listeners.delete(setData);
    };
  }, [eager]);
  return data;
}
