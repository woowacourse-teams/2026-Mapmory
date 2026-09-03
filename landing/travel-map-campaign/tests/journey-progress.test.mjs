import assert from "node:assert/strict";
import test from "node:test";
import { JOURNEY_MOTION_TIMING, getJourneyMotionDuration, getJourneyProgressState } from "../src/journeyProgress.js";

function progressAtSeconds(pointCount, seconds) {
  return seconds / getJourneyMotionDuration(pointCount);
}

test("reveals a location only after the straight-line travel reaches it", () => {
  const { initialHoldSeconds, travelSeconds } = JOURNEY_MOTION_TIMING;
  const beforeArrival = getJourneyProgressState(5, progressAtSeconds(5, initialHoldSeconds + travelSeconds - 0.001));
  const atArrival = getJourneyProgressState(5, progressAtSeconds(5, initialHoldSeconds + travelSeconds));

  assert.equal(beforeArrival.activeIndex, 0);
  assert.equal(beforeArrival.visibleCount, 1);
  assert.equal(beforeArrival.phase, "travel");
  assert.equal(atArrival.activeIndex, 1);
  assert.equal(atArrival.visibleCount, 2);
  assert.equal(atArrival.phase, "arrival");
  assert.equal(atArrival.activeSegmentIndex, -1);
  assert.equal(atArrival.completedSegmentCount, 1);
});

test("finishes with every mapped highlight visible", () => {
  const state = getJourneyProgressState(5, 1);
  assert.equal(state.activeIndex, 4);
  assert.equal(state.visibleCount, 5);
  assert.equal(state.segmentProgress, 0);
  assert.equal(state.photoVisible, true);
  assert.equal(state.isComplete, true);
  assert.equal(state.phase, "complete");
});

test("keeps a single representative photo stationary without a synthetic travel segment", () => {
  const duringHold = getJourneyProgressState(1, 0.5);
  const complete = getJourneyProgressState(1, 1);

  assert.equal(getJourneyMotionDuration(1), 0.6);
  assert.equal(duringHold.activeIndex, 0);
  assert.equal(duringHold.visibleCount, 1);
  assert.equal(duringHold.activeSegmentIndex, -1);
  assert.equal(duringHold.completedSegmentCount, 0);
  assert.equal(duringHold.phase, "arrival");
  assert.equal(complete.completedSegmentCount, 0);
});

test("keeps the departure photo visible while the route moves", () => {
  const { initialHoldSeconds, travelSeconds } = JOURNEY_MOTION_TIMING;
  const state = getJourneyProgressState(5, progressAtSeconds(5, initialHoldSeconds + travelSeconds / 2));

  assert.equal(state.phase, "travel");
  assert.equal(state.activeIndex, 0);
  assert.equal(state.activeSegmentIndex, 0);
  assert.equal(state.completedSegmentCount, 0);
  assert.equal(state.previousPhotoIndex, 0);
  assert.equal(state.photoTransitionProgress, 1);
  assert.equal(state.photoVisible, true);
});

test("holds the arrived photo before starting the next leg", () => {
  const { initialHoldSeconds, travelSeconds, arrivalHoldSeconds, photoCrossfadeSeconds } = JOURNEY_MOTION_TIMING;
  const arrivalStart = initialHoldSeconds + travelSeconds;
  const atArrival = getJourneyProgressState(5, progressAtSeconds(5, arrivalStart));
  const duringCrossfade = getJourneyProgressState(5, progressAtSeconds(5, arrivalStart + photoCrossfadeSeconds / 2));
  const afterCrossfade = getJourneyProgressState(5, progressAtSeconds(5, arrivalStart + photoCrossfadeSeconds));
  const beforeNextLeg = getJourneyProgressState(5, progressAtSeconds(5, arrivalStart + arrivalHoldSeconds - 0.001));
  const nextLeg = getJourneyProgressState(5, progressAtSeconds(5, arrivalStart + arrivalHoldSeconds + 0.001));

  assert.equal(atArrival.photoTransitionProgress, 0);
  assert.equal(duringCrossfade.activeIndex, 1);
  assert.equal(duringCrossfade.previousPhotoIndex, 0);
  assert.ok(duringCrossfade.photoTransitionProgress > 0 && duringCrossfade.photoTransitionProgress < 1);
  assert.equal(afterCrossfade.photoTransitionProgress, 1);
  assert.equal(beforeNextLeg.phase, "arrival");
  assert.equal(beforeNextLeg.activeIndex, 1);
  assert.equal(nextLeg.phase, "travel");
  assert.equal(nextLeg.activeIndex, 1);
});

test("caps five-highlight line travel at two seconds", () => {
  assert.equal(getJourneyMotionDuration(5), 3.5);
  assert.equal((5 - 1) * JOURNEY_MOTION_TIMING.travelSeconds, 2);
});

test("clamps invalid progress without revealing a future location", () => {
  assert.equal(getJourneyProgressState(3, -1).activeIndex, 0);
  assert.equal(getJourneyProgressState(3, Number.NaN).activeIndex, 0);
  assert.equal(getJourneyProgressState(3, 4).activeIndex, 2);
});
