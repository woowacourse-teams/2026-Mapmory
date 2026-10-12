// Map projection shared by the landing's Korea drawings. The hero keeps its own copy of the
// two constants for now (it is out of scope for the #how story), so keep them in sync.
export const KOREA_BOUNDS = Object.freeze({ minLng: 124.5, maxLng: 130.05, minLat: 33, maxLat: 38.75 });
export const KOREA_LONGITUDE_SCALE = 0.81;

// Fits a lng/lat view into width x height with a fixed longitude squash, centred on both axes.
export function createProjector(view, { width, height, longitudeScale = KOREA_LONGITUDE_SCALE }) {
  const spanX = (view.maxLng - view.minLng) * longitudeScale;
  const spanY = view.maxLat - view.minLat;
  const scale = Math.min(width / spanX, height / spanY);
  const offsetX = (width - spanX * scale) / 2;
  const offsetY = (height - spanY * scale) / 2;
  return (lng, lat) => [
    offsetX + (lng - view.minLng) * longitudeScale * scale,
    offsetY + (view.maxLat - lat) * scale,
  ];
}

export function ringsToPath(rings, project, digits = 1) {
  return rings.map((ring) => `${ring.map(([lng, lat], index) => {
    const [x, y] = project(lng, lat);
    return `${index === 0 ? "M" : "L"}${x.toFixed(digits)} ${y.toFixed(digits)}`;
  }).join("")}Z`).join("");
}
