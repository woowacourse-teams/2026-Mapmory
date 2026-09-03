export const JOURNEY_MOTION_TIMING = Object.freeze({
  initialHoldSeconds: 0.3,
  travelSeconds: 0.5,
  arrivalHoldSeconds: 0.3,
  photoCrossfadeSeconds: 0.15,
});

function clamp(value, min = 0, max = 1) {
  return Math.min(max, Math.max(min, value));
}

function clampProgress(progress) {
  return clamp(Number.isFinite(progress) ? progress : 0);
}

function easeOutCubic(progress) {
  const clamped = clamp(progress);
  return 1 - ((1 - clamped) ** 3);
}

export function getJourneyMotionDuration(pointCount, timing = JOURNEY_MOTION_TIMING) {
  const count = Math.max(0, Math.floor(pointCount));
  if (count === 0) return 0;
  if (count === 1) return timing.initialHoldSeconds + timing.arrivalHoldSeconds;
  return timing.initialHoldSeconds + (count - 1) * (timing.travelSeconds + timing.arrivalHoldSeconds);
}

function createState({
  activeIndex,
  visibleCount,
  segmentProgress = 0,
  activeSegmentIndex = -1,
  completedSegmentCount = 0,
  phase,
  phaseProgress,
  previousPhotoIndex = activeIndex,
  photoTransitionProgress = 1,
  isComplete = false,
}) {
  return {
    activeIndex,
    visibleCount,
    segmentProgress,
    activeSegmentIndex,
    completedSegmentCount,
    photoVisible: activeIndex >= 0,
    isComplete,
    phase,
    phaseProgress,
    previousPhotoIndex,
    photoTransitionProgress,
  };
}

export function getJourneyProgressState(pointCount, progress, timing = JOURNEY_MOTION_TIMING) {
  const count = Math.max(0, Math.floor(pointCount));
  const normalizedProgress = clampProgress(progress);

  if (count === 0) {
    return createState({
      activeIndex: -1,
      visibleCount: 0,
      phase: "empty",
      phaseProgress: 1,
      previousPhotoIndex: -1,
      photoTransitionProgress: 0,
      isComplete: normalizedProgress >= 1,
    });
  }

  if (normalizedProgress >= 1) {
    return createState({
      activeIndex: count - 1,
      visibleCount: count,
      completedSegmentCount: Math.max(0, count - 1),
      phase: "complete",
      phaseProgress: 1,
      isComplete: true,
    });
  }

  if (count === 1) {
    return createState({
      activeIndex: 0,
      visibleCount: 1,
      phase: "arrival",
      phaseProgress: normalizedProgress,
    });
  }

  const totalDuration = getJourneyMotionDuration(count, timing);
  let elapsed = normalizedProgress * totalDuration;

  if (elapsed < timing.initialHoldSeconds) {
    return createState({
      activeIndex: 0,
      visibleCount: 1,
      phase: "initial",
      phaseProgress: clamp(elapsed / timing.initialHoldSeconds),
    });
  }

  elapsed -= timing.initialHoldSeconds;
  for (let segmentIndex = 0; segmentIndex < count - 1; segmentIndex += 1) {
    if (elapsed < timing.travelSeconds) {
      const phaseProgress = clamp(elapsed / timing.travelSeconds);
      return createState({
        activeIndex: segmentIndex,
        visibleCount: segmentIndex + 1,
        segmentProgress: easeOutCubic(phaseProgress),
        activeSegmentIndex: segmentIndex,
        completedSegmentCount: segmentIndex,
        phase: "travel",
        phaseProgress,
      });
    }

    elapsed -= timing.travelSeconds;
    if (elapsed < timing.arrivalHoldSeconds) {
      const phaseProgress = clamp(elapsed / timing.arrivalHoldSeconds);
      const crossfadeDuration = Math.min(timing.photoCrossfadeSeconds, timing.arrivalHoldSeconds);
      return createState({
        activeIndex: segmentIndex + 1,
        visibleCount: segmentIndex + 2,
        completedSegmentCount: segmentIndex + 1,
        phase: "arrival",
        phaseProgress,
        previousPhotoIndex: segmentIndex,
        photoTransitionProgress: crossfadeDuration > 0 ? clamp(elapsed / crossfadeDuration) : 1,
      });
    }

    elapsed -= timing.arrivalHoldSeconds;
  }

  return createState({
    activeIndex: count - 1,
    visibleCount: count,
    completedSegmentCount: count - 1,
    phase: "complete",
    phaseProgress: 1,
    isComplete: true,
  });
}
