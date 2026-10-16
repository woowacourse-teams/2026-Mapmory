// The #how travel replay (2026-10-15, 티온: "올려보자"): one example trip replayed on a lit globe as the page scrolls.
// Copy and places live here so the component, the engine and tests/travel-replay.test.mjs share one source.

// [eyebrow, title, sub]. Beat 0 is the find in the phone, 1 the privacy hold, 2 Jeju, 3 Japan and the US, 4 the globe.
export const BEATS = Object.freeze([
  ["사용 방법", "수천 장 속에서 그곳 사진만 모여요", "사진에 저장된 위치로 찾아요"],
  ["사용 방법", "찾는 건 내 폰 안에서 해요", "찾는 동안 사진을 서버로 보내지 않아요"],
  ["01 제주", "제주 사진은 제주로", "제주도부터 대한민국까지 칠해져요"],
  ["02 일본 · 미국", "일본도, 미국도 같은 방법으로", "사진을 한 장씩 옮길 필요 없어요"],
  ["사용 방법", "다녀온 곳이 지구본에 쌓여요", "이제 아래에서 직접 돌려보세요"],
]);

// The trip uses the hero demo's places, photos and counts (src/photoFinderDemo.js), so both read as one story.
export const REPLAY_LIBRARY_COUNT = 21474;
export const REPLAY_PLACES = Object.freeze([
  { key: "jeju", name: "제주", count: 31, lonlat: [126.53, 33.38], country: "410", island: true, cover: "jeju-01", lean: [-1, -0.2], leanN: [-0.5, -1] },
  { key: "japan", name: "일본", count: 86, lonlat: [138.5, 36.2], country: "392", cover: "japan-01", lean: [1, -0.35], leanN: [0.7, -1] },
  { key: "usa", name: "미국", count: 52, lonlat: [-112.1, 36.1], country: "840", cover: "usa-04", lean: [0, -1] },
]);
export const REPLAY_PHOTOS_PER_PLACE = 5;
export const REPLAY_TOTAL = REPLAY_PLACES.reduce((sum, { count }) => sum + count, 0);

export const END_CARD = Object.freeze({
  trust: "사진은 폰 안에서 찾고, 고른 사진만 올라가요.",
  demo: "예시 여행이에요. 숫자는 히어로 데모와 같아요.",
});

// Scroll units: the section is SECTION_VH tall and its progress maps to b0 = 0..BEAT_SPAN.
export const SECTION_VH = 740;
export const BEAT_SPAN = 6;
// Which caption shows for each whole scroll unit (unit 4 keeps the Japan/US caption while the camera crosses).
export const BEAT_OF_UNIT = Object.freeze([0, 1, 2, 3, 3, 4]);

export const replayPhoto = (name) => `/assets/photos/${name}.webp`;
