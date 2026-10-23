export const START_FRAMES = 21;
export const MOVE_FRAMES = 45;
export const HOLD_FRAMES = 17;
export const LEG_FRAMES = MOVE_FRAMES + HOLD_FRAMES;
export const END_FRAMES = 27;

export const getTravelTimelineDuration = (stopCount: number) =>
  START_FRAMES + Math.max(0, stopCount - 1) * LEG_FRAMES + END_FRAMES;
