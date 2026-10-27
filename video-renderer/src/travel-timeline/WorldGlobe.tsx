import React from "react";
import worldMapData from "../data/world-map.json";
import type {TravelStop} from "./schema";
import {palettes, type PaletteName} from "./palette";
import {
  clamp,
  easeInOutCubic,
  mixHex,
  project,
  rotate,
  slerp,
  toGeo,
  toSphere,
  type Camera,
  type Vec3,
} from "./globe-math";

const CENTER_X = 540;
const CENTER_Y = 735;
const RADIUS = 462;

type RawCountry = {
  code: string;
  name: string;
  rings: [number, number][][];
};

type PreparedCountry = Omit<RawCountry, "rings"> & {rings: Vec3[][]};

const countries: PreparedCountry[] = (worldMapData as RawCountry[]).map(
  (country) => ({
    code: country.code,
    name: country.name,
    rings: country.rings.map((ring) =>
      ring.map(([longitude, latitude]) => toSphere(longitude, latitude)),
    ),
  }),
);

const intersectionWithHorizon = (from: Vec3, to: Vec3): Vec3 => {
  const denominator = from.z - to.z;
  const progress = Math.abs(denominator) < 0.00001 ? 0 : from.z / denominator;
  const x = from.x + (to.x - from.x) * progress;
  const y = from.y + (to.y - from.y) * progress;
  const length = Math.hypot(x, y) || 1;
  return {x: x / length, y: y / length, z: 0};
};

const clipPolygonToFront = (points: Vec3[]) => {
  if (points.length === 0) return [];
  const result: Vec3[] = [];

  for (let index = 0; index < points.length; index++) {
    const current = points[index];
    const previous = points[(index + points.length - 1) % points.length];
    const currentVisible = current.z >= 0;
    const previousVisible = previous.z >= 0;

    if (currentVisible) {
      if (!previousVisible) result.push(intersectionWithHorizon(previous, current));
      result.push(current);
    } else if (previousVisible) {
      result.push(intersectionWithHorizon(previous, current));
    }
  }

  return result;
};

const polygonPath = (ring: Vec3[], camera: Camera, radius: number) => {
  const clipped = clipPolygonToFront(ring.map((point) => rotate(point, camera)));
  if (clipped.length < 3) return "";
  return `${clipped
    .map(
      (point, index) =>
        `${index === 0 ? "M" : "L"}${CENTER_X + point.x * radius},${CENTER_Y - point.y * radius}`,
    )
    .join(" ")} Z`;
};

const routePath = (from: Vec3, to: Vec3, progress: number, camera: Camera, radius: number) => {
  const sampleCount = 72;
  const commands: string[] = [];
  let drawing = false;
  const visibleProgress = clamp(progress, 0, 1);

  for (let index = 0; index <= sampleCount; index++) {
    const point = project(
      slerp(from, to, (index / sampleCount) * visibleProgress),
      camera,
      CENTER_X,
      CENTER_Y,
      radius,
    );
    if (point.z >= 0) {
      commands.push(`${drawing ? "L" : "M"}${point.screenX},${point.screenY}`);
      drawing = true;
    } else {
      drawing = false;
    }
  }
  return commands.join(" ");
};

const planeRotation = (from: Vec3, to: Vec3, progress: number, camera: Camera, radius: number) => {
  const before = project(slerp(from, to, clamp(progress - 0.006, 0, 1)), camera, CENTER_X, CENTER_Y, radius);
  const after = project(slerp(from, to, clamp(progress + 0.006, 0, 1)), camera, CENTER_X, CENTER_Y, radius);
  return (Math.atan2(after.screenY - before.screenY, after.screenX - before.screenX) * 180) / Math.PI;
};

const cameraLatitudeForVerticalTarget = (point: Vec3, longitude: number, preferredLatitude: number, targetY: number) => {
  let latitude = preferredLatitude;

  for (let iteration = 0; iteration < 6; iteration++) {
    const currentY = rotate(point, {longitude, latitude}).y;
    const error = currentY - targetY;
    if (Math.abs(error) < 0.0001) break;

    const step = 0.1;
    const before = rotate(point, {longitude, latitude: latitude - step}).y;
    const after = rotate(point, {longitude, latitude: latitude + step}).y;
    const derivative = (after - before) / (step * 2);
    if (Math.abs(derivative) < 0.0001) break;

    latitude = clamp(latitude - error / derivative, -68, 68);
  }

  return latitude;
};

type WorldGlobeProps = {
  stops: TravelStop[];
  segmentIndex: number;
  progress: number;
  arrival: number;
  zoomed?: boolean;
  horizon?: boolean;
  horizonOffsetY?: number;
  palette?: PaletteName;
};

export const WorldGlobe: React.FC<WorldGlobeProps> = ({
  stops,
  segmentIndex,
  progress,
  arrival,
  zoomed = false,
  horizon = false,
  horizonOffsetY = 655,
  palette = "midnight",
}) => {
  const colors = palettes[palette];
  const globeRadius = horizon ? 800 : zoomed ? 940 : RADIUS;
  const tallCanvas = zoomed || horizon;
  const fromStop = stops[segmentIndex];
  const toStop = stops[Math.min(segmentIndex + 1, stops.length - 1)];
  const sameCountry = fromStop.countryCode === toStop.countryCode;
  const from = toSphere(fromStop.longitude, fromStop.latitude);
  const to = toSphere(toStop.longitude, toStop.latitude);
  const cameraPoint = slerp(from, to, clamp(progress + (1 - progress) * 0.055, 0, 1));
  const cameraGeo = toGeo(cameraPoint);
  const plane = slerp(from, to, progress);
  const camera: Camera = {
    longitude: cameraGeo.longitude,
    latitude: horizon
      ? cameraLatitudeForVerticalTarget(
          plane,
          cameraGeo.longitude,
          clamp(cameraGeo.latitude, -68, 68),
          (CENTER_Y - (1920 / 2 - horizonOffsetY)) / globeRadius,
        )
      : clamp(cameraGeo.latitude, -68, 68),
  };
  const visitedIndex = new Map<string, number>();
  stops.forEach((stop, index) => {
    if (!visitedIndex.has(stop.countryCode)) {
      visitedIndex.set(stop.countryCode, index);
    }
  });
  const currentCountryProgress = easeInOutCubic((progress - 0.68) / 0.28);

  const routeEntries = stops.slice(0, segmentIndex + 1).map((stop, index) => ({
    from: toSphere(stop.longitude, stop.latitude),
    to: toSphere(stops[index + 1].longitude, stops[index + 1].latitude),
    progress: index === segmentIndex ? progress : 1,
    current: index === segmentIndex,
  }));

  const planePoint = project(plane, camera, CENTER_X, CENTER_Y, globeRadius);
  const destinationPoint = project(to, camera, CENTER_X, CENTER_Y, globeRadius);
  const angle = planeRotation(from, to, progress, camera, globeRadius);

  return (
    <svg
      width={1080}
      height={tallCanvas ? 1920 : 1270}
      viewBox={`0 0 1080 ${tallCanvas ? 1920 : 1270}`}
      style={{position: "absolute", inset: 0, overflow: horizon ? "visible" : undefined, transform: horizon ? `translateY(${horizonOffsetY}px)` : zoomed ? "translateY(-110px)" : undefined}}
    >
      <defs>
        <radialGradient id="ocean" cx="42%" cy="35%" r="72%">
          <stop offset="0%" stopColor={colors.oceanCenter} />
          <stop offset="60%" stopColor={colors.oceanMiddle} />
          <stop offset="100%" stopColor={colors.oceanEdge} />
        </radialGradient>
        <clipPath id="globe-clip">
          <circle cx={CENTER_X} cy={CENTER_Y} r={globeRadius} />
        </clipPath>
        <filter id="route-soft" x="-50%" y="-50%" width="200%" height="200%">
          <feGaussianBlur stdDeviation="7" />
        </filter>
      </defs>

      <circle cx={CENTER_X} cy={CENTER_Y} r={globeRadius} fill="url(#ocean)" />

      {horizon ? (
        <>
          <circle cx={CENTER_X} cy={CENTER_Y} r={globeRadius + 2} fill="none" stroke="#A9D6E8" strokeWidth={12} opacity={0.24} filter="url(#route-soft)" />
          <circle cx={CENTER_X} cy={CENTER_Y} r={globeRadius - 1} fill="none" stroke="#D9EDF2" strokeWidth={2} opacity={0.76} />
        </>
      ) : null}

      <g clipPath="url(#globe-clip)">
        {countries.map((country) => {
          const reachedAt = visitedIndex.get(country.code);
          const reached = reachedAt !== undefined && reachedAt <= segmentIndex;
          const isDestination = country.code === toStop.countryCode;
          const fill = isDestination
            ? mixHex(colors.unvisited, colors.active, currentCountryProgress)
            : reached
              ? colors.reached
              : colors.unvisited;

          return country.rings.map((ring, ringIndex) => {
            const d = polygonPath(ring, camera, globeRadius);
            return d ? (
              <path
                key={`${country.code}-${ringIndex}`}
                d={d}
                fill={fill}
                stroke={horizon ? "#2E5264" : colors.background}
                strokeWidth={horizon ? 1.15 : 1.35}
                strokeLinejoin="round"
              />
            ) : null;
          });
        })}

        {routeEntries.map((route, index) => {
          if (stops[index].countryCode === stops[index + 1].countryCode) {
            return null;
          }

          const d = routePath(route.from, route.to, route.progress, camera, globeRadius);
          return (
            <React.Fragment key={`${index}-${stops[index].countryCode}-${stops[index + 1].countryCode}`}>
              {route.current ? (
                <path
                  d={d}
                  fill="none"
                  stroke={colors.route}
                  strokeWidth={15}
                  strokeLinecap="round"
                  opacity={0.22}
                  filter="url(#route-soft)"
                />
              ) : null}
              <path
                d={d}
                fill="none"
                stroke={colors.route}
                strokeWidth={route.current ? 5 : 4}
                strokeLinecap="round"
                opacity={route.current ? 1 : 0.28}
              />
            </React.Fragment>
          );
        })}

        {stops.slice(0, segmentIndex + 2).map((stop, index) => {
          const point = project(toSphere(stop.longitude, stop.latitude), camera, CENTER_X, CENTER_Y, globeRadius);
          if (point.z < 0) return null;
          const isDestination = index === segmentIndex + 1;
          return (
            <g key={`${index}-${stop.countryCode}`} opacity={isDestination ? 0.45 + currentCountryProgress * 0.55 : 0.65}>
              {isDestination && arrival > 0 ? (
                <circle
                  cx={point.screenX}
                  cy={point.screenY}
                  r={10 + arrival * 27}
                  fill="none"
                  stroke={colors.route}
                  strokeWidth={3}
                  opacity={1 - arrival}
                />
              ) : null}
              <circle cx={point.screenX} cy={point.screenY} r={7} fill={colors.background} stroke={colors.route} strokeWidth={4} />
            </g>
          );
        })}
      </g>

      {!sameCountry && planePoint.z >= 0 ? (
        <g
          transform={`translate(${planePoint.screenX} ${planePoint.screenY}) rotate(${angle + 90}) scale(${horizon ? 1.25 : 1})`}
          style={{filter: "drop-shadow(0 3px 7px rgba(49,130,246,0.8))"}}
        >
          <path
            d="M0 -22 L4 -5 L18 5 L18 9 L4 5 L4 15 L10 20 L10 23 L0 20 L-10 23 L-10 20 L-4 15 L-4 5 L-18 9 L-18 5 L-4 -5 Z"
            fill="#FFFFFF"
            stroke={colors.route}
            strokeWidth={1.5}
            strokeLinejoin="round"
          />
        </g>
      ) : null}

      {destinationPoint.z >= 0 && currentCountryProgress > 0 ? (
        <circle
          cx={destinationPoint.screenX}
          cy={destinationPoint.screenY}
          r={20 + 12 * currentCountryProgress}
          fill={colors.route}
          opacity={0.1 * currentCountryProgress}
        />
      ) : null}
    </svg>
  );
};
