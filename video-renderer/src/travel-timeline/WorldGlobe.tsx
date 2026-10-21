import React from "react";
import worldMapData from "../data/world-map.json";
import type {TravelStop} from "./schema";
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

const colors = {
  active: "#4593FC",
  background: "#0F141C",
  oceanCenter: "#1D2A3C",
  oceanMiddle: "#152033",
  oceanEdge: "#0E1624",
  route: "#3182F6",
  unvisited: "#273447",
};

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

const polygonPath = (ring: Vec3[], camera: Camera) => {
  const clipped = clipPolygonToFront(ring.map((point) => rotate(point, camera)));
  if (clipped.length < 3) return "";
  return `${clipped
    .map(
      (point, index) =>
        `${index === 0 ? "M" : "L"}${CENTER_X + point.x * RADIUS},${CENTER_Y - point.y * RADIUS}`,
    )
    .join(" ")} Z`;
};

const routePath = (from: Vec3, to: Vec3, progress: number, camera: Camera) => {
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
      RADIUS,
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

const planeRotation = (from: Vec3, to: Vec3, progress: number, camera: Camera) => {
  const before = project(slerp(from, to, clamp(progress - 0.006, 0, 1)), camera, CENTER_X, CENTER_Y, RADIUS);
  const after = project(slerp(from, to, clamp(progress + 0.006, 0, 1)), camera, CENTER_X, CENTER_Y, RADIUS);
  return (Math.atan2(after.screenY - before.screenY, after.screenX - before.screenX) * 180) / Math.PI;
};

type WorldGlobeProps = {
  stops: TravelStop[];
  segmentIndex: number;
  progress: number;
  arrival: number;
};

export const WorldGlobe: React.FC<WorldGlobeProps> = ({
  stops,
  segmentIndex,
  progress,
  arrival,
}) => {
  const fromStop = stops[segmentIndex];
  const toStop = stops[Math.min(segmentIndex + 1, stops.length - 1)];
  const sameCountry = fromStop.countryCode === toStop.countryCode;
  const from = toSphere(fromStop.longitude, fromStop.latitude);
  const to = toSphere(toStop.longitude, toStop.latitude);
  const cameraPoint = slerp(from, to, clamp(progress + (1 - progress) * 0.055, 0, 1));
  const cameraGeo = toGeo(cameraPoint);
  const camera: Camera = {
    longitude: cameraGeo.longitude,
    latitude: clamp(cameraGeo.latitude, -68, 68),
  };
  const visitedIndex = new Map(stops.map((stop, index) => [stop.countryCode, index]));
  const currentCountryProgress = easeInOutCubic((progress - 0.68) / 0.28);

  const routeEntries = stops.slice(0, segmentIndex + 1).map((stop, index) => ({
    from: toSphere(stop.longitude, stop.latitude),
    to: toSphere(stops[index + 1].longitude, stops[index + 1].latitude),
    progress: index === segmentIndex ? progress : 1,
    current: index === segmentIndex,
  }));

  const planePoint = project(slerp(from, to, progress), camera, CENTER_X, CENTER_Y, RADIUS);
  const destinationPoint = project(to, camera, CENTER_X, CENTER_Y, RADIUS);
  const angle = planeRotation(from, to, progress, camera);

  return (
    <svg
      width={1080}
      height={1270}
      viewBox="0 0 1080 1270"
      style={{position: "absolute", inset: 0}}
    >
      <defs>
        <radialGradient id="ocean" cx="42%" cy="35%" r="72%">
          <stop offset="0%" stopColor={colors.oceanCenter} />
          <stop offset="60%" stopColor={colors.oceanMiddle} />
          <stop offset="100%" stopColor={colors.oceanEdge} />
        </radialGradient>
        <clipPath id="globe-clip">
          <circle cx={CENTER_X} cy={CENTER_Y} r={RADIUS} />
        </clipPath>
        <filter id="route-soft" x="-50%" y="-50%" width="200%" height="200%">
          <feGaussianBlur stdDeviation="7" />
        </filter>
      </defs>

      <circle cx={CENTER_X} cy={CENTER_Y} r={RADIUS} fill="url(#ocean)" />

      <g clipPath="url(#globe-clip)">
        {countries.map((country) => {
          const reachedAt = visitedIndex.get(country.code);
          const reached = reachedAt !== undefined && reachedAt <= segmentIndex;
          const isDestination = country.code === toStop.countryCode;
          const fill = isDestination
            ? mixHex(colors.unvisited, colors.active, currentCountryProgress)
            : reached
              ? "#255CA0"
              : colors.unvisited;

          return country.rings.map((ring, ringIndex) => {
            const d = polygonPath(ring, camera);
            return d ? (
              <path
                key={`${country.code}-${ringIndex}`}
                d={d}
                fill={fill}
                stroke={colors.background}
                strokeWidth={1.35}
                strokeLinejoin="round"
              />
            ) : null;
          });
        })}

        {routeEntries.map((route, index) => {
          if (stops[index].countryCode === stops[index + 1].countryCode) {
            return null;
          }

          const d = routePath(route.from, route.to, route.progress, camera);
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
          const point = project(toSphere(stop.longitude, stop.latitude), camera, CENTER_X, CENTER_Y, RADIUS);
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
        <text
          x={planePoint.screenX}
          y={planePoint.screenY}
          textAnchor="middle"
          dominantBaseline="central"
          fontSize={40}
          transform={`rotate(${angle + 90} ${planePoint.screenX} ${planePoint.screenY})`}
          style={{filter: "drop-shadow(0 3px 7px rgba(49,130,246,0.8))"}}
        >
          ✈️
        </text>
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
