import { geoMercator, geoPath } from "d3-geo";
import { feature } from "topojson-client";
import countries from "world-atlas/countries-50m.json" with { type: "json" };
import { getJourneyProgressState } from "./journeyProgress.js";

const land = feature(countries, countries.objects.countries);
const imageCache = new Map();

function getExtentFeature(points) {
  if (points.length === 1) {
    const [point] = points;
    return {
      type: "Feature",
      geometry: {
        type: "Polygon",
        coordinates: [[
          [point.lng - 4, point.lat - 3],
          [point.lng + 4, point.lat - 3],
          [point.lng + 4, point.lat + 3],
          [point.lng - 4, point.lat + 3],
          [point.lng - 4, point.lat - 3],
        ]],
      },
    };
  }

  return {
    type: "FeatureCollection",
    features: points.map((point) => ({
      type: "Feature",
      geometry: { type: "Point", coordinates: [point.lng, point.lat] },
    })),
  };
}

export function createMapProjection(width, height, points, padding = 44) {
  return geoMercator().fitExtent(
    [[padding, padding], [Math.max(padding + 1, width - padding), Math.max(padding + 1, height - padding)]],
    points.length > 0 ? getExtentFeature(points) : land,
  );
}

function roundedRect(ctx, x, y, width, height, radius) {
  ctx.beginPath();
  ctx.roundRect(x, y, width, height, radius);
}

function loadImage(src, onReady) {
  if (!src) return null;
  const cached = imageCache.get(src);
  if (cached?.complete) return cached.naturalWidth > 0 ? cached : null;
  const image = cached ?? new Image();
  if (onReady) image.addEventListener("load", onReady, { once: true });
  if (!cached) {
    image.decoding = "async";
    imageCache.set(src, image);
    image.src = src;
  }
  return null;
}

export function drawImageCover(ctx, image, x, y, width, height) {
  const scale = Math.max(width / image.naturalWidth, height / image.naturalHeight);
  const sourceWidth = width / scale;
  const sourceHeight = height / scale;
  const sourceX = (image.naturalWidth - sourceWidth) / 2;
  const sourceY = (image.naturalHeight - sourceHeight) / 2;
  ctx.drawImage(image, sourceX, sourceY, sourceWidth, sourceHeight, x, y, width, height);
}

function drawLand(ctx, projection) {
  const path = geoPath(projection, ctx);
  ctx.beginPath();
  path(land);
  ctx.fillStyle = "#f2f7f4";
  ctx.fill();
  ctx.strokeStyle = "#c8d9d0";
  ctx.lineWidth = 0.8;
  ctx.stroke();
}

function drawRoute(ctx, projection, points, progress) {
  if (points.length === 0) return;
  const {
    activeIndex,
    activeSegmentIndex,
    completedSegmentCount,
    visibleCount,
    segmentProgress,
  } = getJourneyProgressState(points.length, progress);

  ctx.save();
  ctx.strokeStyle = "#0bc984";
  ctx.lineWidth = 3;
  ctx.lineCap = "round";
  ctx.lineJoin = "round";

  for (let index = 0; index < points.length - 1; index += 1) {
    const isCompleteSegment = index < completedSegmentCount;
    const isActiveSegment = index === activeSegmentIndex;
    if (!isCompleteSegment && !isActiveSegment) break;
    const start = projection([points[index].lng, points[index].lat]);
    const end = projection([points[index + 1].lng, points[index + 1].lat]);
    const segmentEnd = isActiveSegment
      ? [start[0] + (end[0] - start[0]) * segmentProgress, start[1] + (end[1] - start[1]) * segmentProgress]
      : end;

    const changesTrip = points[index].tripId !== points[index + 1].tripId;
    ctx.setLineDash(changesTrip ? [7, 8] : []);

    ctx.beginPath();
    ctx.moveTo(start[0], start[1]);
    ctx.lineTo(segmentEnd[0], segmentEnd[1]);
    ctx.stroke();
  }
  ctx.restore();

  for (let index = 0; index < visibleCount; index += 1) {
    const [x, y] = projection([points[index].lng, points[index].lat]);
    if (index === activeIndex) {
      const pulseProgress = Math.min(1, segmentProgress * 4);
      ctx.beginPath();
      ctx.arc(x, y, 10 + pulseProgress * 7, 0, Math.PI * 2);
      ctx.fillStyle = `rgba(11, 201, 132, ${0.24 * (1 - pulseProgress)})`;
      ctx.fill();
    }
    ctx.beginPath();
    ctx.arc(x, y, index === activeIndex ? 7 : 5, 0, Math.PI * 2);
    ctx.fillStyle = "#08bf7c";
    ctx.fill();
    ctx.lineWidth = 3;
    ctx.strokeStyle = "#ffffff";
    ctx.stroke();
    ctx.fillStyle = "#142019";
    ctx.font = '700 11px "LINE Seed Sans KR", sans-serif';
    ctx.textAlign = "center";
    ctx.fillText(points[index].name, x, y + 20);
  }
}

export function getActivePoint(points, progress) {
  const { activeIndex } = getJourneyProgressState(points.length, progress);
  return activeIndex < 0 ? null : points[activeIndex];
}

export function drawJourneyMap(ctx, width, height, points, progress, onImageReady) {
  ctx.clearRect(0, 0, width, height);
  ctx.fillStyle = "#eaf5fb";
  ctx.fillRect(0, 0, width, height);
  const projection = createMapProjection(width, height, points, Math.min(width, height) * 0.13);
  drawLand(ctx, projection);
  drawRoute(ctx, projection, points, progress);

  const progressState = getJourneyProgressState(points.length, progress);
  const activePoint = progressState.photoVisible ? getActivePoint(points, progress) : null;
  if (!activePoint) return projection;
  const image = loadImage(activePoint.image, onImageReady);
  if (!image) return projection;

  const [pointX, pointY] = projection([activePoint.lng, activePoint.lat]);
  const cardWidth = Math.min(142, width * 0.38);
  const cardHeight = cardWidth * 0.72;
  const cardX = Math.max(10, Math.min(width - cardWidth - 10, pointX + (pointX > width * 0.6 ? -cardWidth - 14 : 14)));
  const cardY = Math.max(10, Math.min(height - cardHeight - 26, pointY - cardHeight * 0.55));
  const cardAnchorX = cardX > pointX ? cardX : cardX + cardWidth;
  const cardAnchorY = Math.max(cardY + 12, Math.min(cardY + cardHeight - 12, pointY));
  ctx.save();
  ctx.beginPath();
  ctx.moveTo(pointX, pointY);
  ctx.lineTo(cardAnchorX, cardAnchorY);
  ctx.setLineDash([3, 4]);
  ctx.strokeStyle = "rgba(7, 155, 102, 0.8)";
  ctx.lineWidth = 1.5;
  ctx.stroke();
  ctx.restore();
  ctx.save();
  ctx.shadowColor = "rgba(17, 42, 30, 0.18)";
  ctx.shadowBlur = 16;
  roundedRect(ctx, cardX, cardY, cardWidth, cardHeight, 10);
  ctx.fillStyle = "#ffffff";
  ctx.fill();
  ctx.shadowColor = "transparent";
  roundedRect(ctx, cardX + 4, cardY + 4, cardWidth - 8, cardHeight - 22, 7);
  ctx.clip();
  drawImageCover(ctx, image, cardX + 4, cardY + 4, cardWidth - 8, cardHeight - 22);
  ctx.restore();
  ctx.fillStyle = "#08734f";
  ctx.font = '700 9.5px "LINE Seed Sans KR", sans-serif';
  ctx.textAlign = "left";
  ctx.fillText(`사진 기록 · ${activePoint.name}`, cardX + 8, cardY + cardHeight - 7);
  return projection;
}

export async function loadJourneyImages(points) {
  await Promise.all(points.map((point) => new Promise((resolve) => {
    if (!point.image) {
      resolve();
      return;
    }
    const cached = imageCache.get(point.image);
    if (cached?.complete) {
      resolve();
      return;
    }
    const image = cached ?? new Image();
    image.decoding = "async";
    const settle = () => {
      image.removeEventListener("load", settle);
      image.removeEventListener("error", settle);
      resolve();
    };
    image.addEventListener("load", settle, { once: true });
    image.addEventListener("error", settle, { once: true });
    if (!cached) {
      imageCache.set(point.image, image);
      image.src = point.image;
    }
  })));
}

export function getCachedImage(src) {
  const image = imageCache.get(src);
  return image?.complete && image.naturalWidth > 0 ? image : null;
}
