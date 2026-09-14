import { drawImageCover, drawJourneyMap, getCachedImage, loadJourneyImages } from "./mapRenderer.js";
import { createPlaybackJourney, formatShortDate, getAutoDuration, getJourneyStats, SHARE_INTRO_SECONDS } from "./journeyData.js";
import { getJourneyMotionDuration, getJourneyProgressState } from "./journeyProgress.js";
import { MAPMORY_DOMAIN_LABEL } from "./campaignConfig.js";

const VIDEO_WIDTH = 720;
const VIDEO_HEIGHT = 1280;

function clamp(value, min = 0, max = 1) {
  return Math.min(max, Math.max(min, value));
}

function fillRoundedRect(ctx, x, y, width, height, radius, fillStyle) {
  ctx.beginPath();
  ctx.roundRect(x, y, width, height, radius);
  ctx.fillStyle = fillStyle;
  ctx.fill();
}

function drawBackgroundPhoto(ctx, journey, progressState) {
  const activePoint = journey.points[progressState.activeIndex] ?? journey.points[0];
  const previousPoint = journey.points[progressState.previousPhotoIndex] ?? activePoint;
  const activeImage = activePoint ? getCachedImage(activePoint.image) : null;
  const previousImage = previousPoint ? getCachedImage(previousPoint.image) : null;

  if (progressState.photoTransitionProgress < 1 && previousImage?.complete) {
    drawImageCover(ctx, previousImage, 0, 0, VIDEO_WIDTH, VIDEO_HEIGHT);
    if (activeImage?.complete) {
      ctx.save();
      ctx.globalAlpha = progressState.photoTransitionProgress;
      drawImageCover(ctx, activeImage, 0, 0, VIDEO_WIDTH, VIDEO_HEIGHT);
      ctx.restore();
    }
    return;
  }

  if (activeImage?.complete) drawImageCover(ctx, activeImage, 0, 0, VIDEO_WIDTH, VIDEO_HEIGHT);
  else if (previousImage?.complete) drawImageCover(ctx, previousImage, 0, 0, VIDEO_WIDTH, VIDEO_HEIGHT);
}

function drawLocationLabel(ctx, point, opacity = 1) {
  ctx.save();
  ctx.globalAlpha = opacity;
  ctx.fillStyle = "#8cf0c6";
  ctx.font = '700 20px "LINE Seed Sans KR", sans-serif';
  ctx.fillText(point ? formatShortDate(point.date) : "2026", 78, 1026);
  ctx.fillStyle = "#ffffff";
  ctx.font = '700 44px "LINE Seed Sans KR", sans-serif';
  ctx.fillText(point?.name ?? "나의 여행", 76, 1082);
  ctx.restore();
}

export function drawShareFrame(ctx, journey, progress, durationSeconds = getAutoDuration(journey)) {
  const playbackJourney = createPlaybackJourney(journey);
  const motionDuration = getJourneyMotionDuration(playbackJourney.points.length);
  const elapsedSeconds = clamp(progress) * durationSeconds;
  const routeProgress = motionDuration > 0
    ? clamp((elapsedSeconds - SHARE_INTRO_SECONDS) / motionDuration)
    : 1;
  const progressState = getJourneyProgressState(playbackJourney.points.length, routeProgress);
  const activePoint = playbackJourney.points[progressState.activeIndex] ?? playbackJourney.points[0];
  const previousPoint = playbackJourney.points[progressState.previousPhotoIndex] ?? activePoint;

  ctx.clearRect(0, 0, VIDEO_WIDTH, VIDEO_HEIGHT);
  ctx.fillStyle = "#dceef7";
  ctx.fillRect(0, 0, VIDEO_WIDTH, VIDEO_HEIGHT);

  if (progressState.photoVisible) {
    drawBackgroundPhoto(ctx, playbackJourney, progressState);
    ctx.fillStyle = "rgba(7, 22, 17, 0.38)";
    ctx.fillRect(0, 0, VIDEO_WIDTH, VIDEO_HEIGHT);
  }

  const stats = getJourneyStats(playbackJourney);
  ctx.fillStyle = "#ffffff";
  ctx.textAlign = "left";
  ctx.font = '700 34px "LINE Seed Sans KR", sans-serif';
  ctx.fillText("MY 2026 JOURNEY", 58, 72);
  ctx.font = '700 76px "LINE Seed Sans KR", sans-serif';
  ctx.fillText("2026", 56, 158);
  ctx.font = '700 24px "LINE Seed Sans KR", sans-serif';
  ctx.fillText(`${stats.trips} TRIPS · ${stats.cities} CITIES · ${stats.photos} PHOTOS`, 60, 204);

  const mapCanvas = document.createElement("canvas");
  mapCanvas.width = 620;
  mapCanvas.height = 680;
  const mapContext = mapCanvas.getContext("2d");
  drawJourneyMap(mapContext, 620, 680, playbackJourney.points, routeProgress);
  fillRoundedRect(ctx, 50, 270, 620, 680, 28, "rgba(255, 255, 255, 0.94)");
  ctx.save();
  ctx.beginPath();
  ctx.roundRect(50, 270, 620, 680, 28);
  ctx.clip();
  ctx.drawImage(mapCanvas, 50, 270);
  ctx.restore();

  fillRoundedRect(ctx, 50, 982, 620, 152, 24, "rgba(8, 27, 20, 0.78)");
  if (progressState.photoTransitionProgress < 1 && previousPoint !== activePoint) {
    drawLocationLabel(ctx, previousPoint, 1 - progressState.photoTransitionProgress);
    drawLocationLabel(ctx, activePoint, progressState.photoTransitionProgress);
  } else {
    drawLocationLabel(ctx, activePoint);
  }
  ctx.font = '400 21px "LINE Seed Sans KR", sans-serif';
  ctx.fillStyle = "#d9eee5";
  ctx.fillText("사진의 날짜와 GPS로 이어진 여행", 78, 1118);

  ctx.fillStyle = "#ffffff";
  ctx.font = '700 30px "LINE Seed Sans KR", sans-serif';
  ctx.fillText("Mapmory", 56, 1218);
  ctx.textAlign = "right";
  ctx.font = '700 20px "LINE Seed Sans KR", sans-serif';
  ctx.fillText(MAPMORY_DOMAIN_LABEL, 664, 1218);
}

export async function renderShareImage(journey, progress = 1) {
  const playbackJourney = createPlaybackJourney(journey);
  await document.fonts?.ready;
  await loadJourneyImages(playbackJourney.points);
  const canvas = document.createElement("canvas");
  canvas.width = VIDEO_WIDTH;
  canvas.height = VIDEO_HEIGHT;
  drawShareFrame(canvas.getContext("2d"), playbackJourney, progress, getAutoDuration(playbackJourney));
  return new Promise((resolve, reject) => {
    canvas.toBlob((blob) => blob ? resolve(blob) : reject(new Error("이미지를 만들지 못했어요.")), "image/png", 0.96);
  });
}

function supportedMimeType() {
  const candidates = [
    "video/mp4;codecs=avc1",
    "video/webm;codecs=vp9",
    "video/webm;codecs=vp8",
    "video/webm",
  ];
  return candidates.find((type) => MediaRecorder.isTypeSupported(type)) ?? "";
}

export async function renderJourneyVideo(journey, _durationSeconds, onProgress = () => {}) {
  if (!("MediaRecorder" in window) || !("captureStream" in HTMLCanvasElement.prototype)) {
    throw new Error("이 브라우저에서는 영상 저장을 지원하지 않아요. 이미지 저장을 이용해주세요.");
  }

  const playbackJourney = createPlaybackJourney(journey);
  const playbackDuration = getAutoDuration(playbackJourney);
  await document.fonts?.ready;
  await loadJourneyImages(playbackJourney.points);

  const canvas = document.createElement("canvas");
  canvas.width = VIDEO_WIDTH;
  canvas.height = VIDEO_HEIGHT;
  const ctx = canvas.getContext("2d", { alpha: false });
  const stream = canvas.captureStream(30);
  let recorder;
  let frame = null;
  let stopTimer = null;
  try {
    const mimeType = supportedMimeType();
    recorder = new MediaRecorder(stream, {
      ...(mimeType ? { mimeType } : {}),
      videoBitsPerSecond: 6_000_000,
    });
    const chunks = [];
    // Attach failure handling before starting either recording or animation.
    return await new Promise((resolve, reject) => {
      recorder.ondataavailable = (event) => {
        if (event.data.size > 0) chunks.push(event.data);
      };
      recorder.onerror = () => reject(new Error("영상 생성 중 오류가 발생했어요."));
      recorder.onstop = () => resolve(new Blob(chunks, { type: mimeType || "video/webm" }));
      recorder.start(1000);
      const startedAt = performance.now();
      const draw = (now) => {
        try {
          const progress = clamp((now - startedAt) / (playbackDuration * 1000));
          drawShareFrame(ctx, playbackJourney, progress, playbackDuration);
          onProgress(progress);
          if (progress < 1) frame = requestAnimationFrame(draw);
          else stopTimer = setTimeout(() => {
            try { recorder.stop(); } catch (error) { reject(error); }
          }, 120);
        } catch (error) { reject(error); }
      };
      frame = requestAnimationFrame(draw);
    });
  } finally {
    if (frame !== null) cancelAnimationFrame(frame);
    if (stopTimer !== null) clearTimeout(stopTimer);
    if (recorder) {
      recorder.ondataavailable = null;
      recorder.onerror = null;
      recorder.onstop = null;
      try { if (recorder.state !== "inactive") recorder.stop(); } catch { /* Always release tracks below. */ }
    }
    stream.getTracks().forEach((track) => track.stop());
  }
}

export function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  document.body.append(anchor);
  anchor.click();
  anchor.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}
