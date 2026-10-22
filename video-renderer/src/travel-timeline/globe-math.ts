export type Vec3 = {x: number; y: number; z: number};
export type Camera = {longitude: number; latitude: number};
export type ScreenPoint = Vec3 & {screenX: number; screenY: number};

const radians = Math.PI / 180;

export const clamp = (value: number, min: number, max: number) =>
  Math.max(min, Math.min(max, value));

export const easeInOutCubic = (value: number) => {
  const t = clamp(value, 0, 1);
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
};

export const toSphere = (longitude: number, latitude: number): Vec3 => {
  const lon = longitude * radians;
  const lat = latitude * radians;
  const latitudeRadius = Math.cos(lat);
  return {
    x: latitudeRadius * Math.sin(lon),
    y: Math.sin(lat),
    z: latitudeRadius * Math.cos(lon),
  };
};

const normalize = (point: Vec3): Vec3 => {
  const length = Math.hypot(point.x, point.y, point.z) || 1;
  return {x: point.x / length, y: point.y / length, z: point.z / length};
};

export const slerp = (from: Vec3, to: Vec3, progress: number): Vec3 => {
  const dot = clamp(from.x * to.x + from.y * to.y + from.z * to.z, -1, 1);
  const angle = Math.acos(dot);
  if (angle < 0.00001) return from;
  const sin = Math.sin(angle);
  return normalize({
    x: (from.x * Math.sin((1 - progress) * angle)) / sin + (to.x * Math.sin(progress * angle)) / sin,
    y: (from.y * Math.sin((1 - progress) * angle)) / sin + (to.y * Math.sin(progress * angle)) / sin,
    z: (from.z * Math.sin((1 - progress) * angle)) / sin + (to.z * Math.sin(progress * angle)) / sin,
  });
};

export const toGeo = (point: Vec3) => ({
  longitude: (Math.atan2(point.x, point.z) / Math.PI) * 180,
  latitude: (Math.asin(clamp(point.y, -1, 1)) / Math.PI) * 180,
});

export const rotate = (point: Vec3, camera: Camera): Vec3 => {
  const longitude = -camera.longitude * radians;
  const latitude = camera.latitude * radians;
  const cosLon = Math.cos(longitude);
  const sinLon = Math.sin(longitude);
  const cosLat = Math.cos(latitude);
  const sinLat = Math.sin(latitude);
  const x = cosLon * point.x + sinLon * point.z;
  const z = -sinLon * point.x + cosLon * point.z;
  return {x, y: cosLat * point.y - sinLat * z, z: sinLat * point.y + cosLat * z};
};

const horizon = (from: Vec3, to: Vec3): Vec3 => {
  const difference = from.z - to.z;
  if (Math.abs(difference) < 0.00001) return from;
  const progress = clamp(from.z / difference, 0, 1);
  const x = from.x + (to.x - from.x) * progress;
  const y = from.y + (to.y - from.y) * progress;
  const length = Math.hypot(x, y) || 1;
  return {x: x / length, y: y / length, z: 0};
};

export const clipToFront = (points: Vec3[]): Vec3[] => {
  if (points.length === 0) return [];
  const result: Vec3[] = [];
  let previous = points[points.length - 1];
  let previousVisible = previous.z >= 0;

  for (const current of points) {
    const currentVisible = current.z >= 0;
    if (previousVisible && currentVisible) result.push(current);
    else if (previousVisible && !currentVisible) result.push(horizon(previous, current));
    else if (!previousVisible && currentVisible) {
      result.push(horizon(previous, current));
      result.push(current);
    }
    previous = current;
    previousVisible = currentVisible;
  }
  return result;
};

export const project = (
  point: Vec3,
  camera: Camera,
  centerX: number,
  centerY: number,
  radius: number,
): ScreenPoint => {
  const rotated = rotate(point, camera);
  return {
    ...rotated,
    screenX: centerX + rotated.x * radius,
    screenY: centerY - rotated.y * radius,
  };
};

export const mixHex = (from: string, to: string, progress: number) => {
  const parse = (color: string) => color.match(/\w\w/g)?.map((value) => Number.parseInt(value, 16)) ?? [0, 0, 0];
  const start = parse(from);
  const end = parse(to);
  const t = clamp(progress, 0, 1);
  return `rgb(${start.map((value, index) => Math.round(value + (end[index] - value) * t)).join(",")})`;
};
