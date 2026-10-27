// The #tech section (Sprint 3 "기술 소개", 2026-10-27, 티온's approved sketch v8): one phone pinned in a sticky stage,
// two steps. Step 1 (사진 찾기) keeps the phone on the left, step 2 (지도 모으기) on the right; it slides only between
// steps. Each scene shows a wobbling hand-written step label and one title, and the phone screen has one thing to touch
// (untried elements glow, with a one-line hand-written hint). No small explanatory text under the titles (티온, 10-23).
// Copy rules from AGENTS.md apply: `3만 장도 30초` keeps its footnote (the team test phone's measurement).

export const TECH_COPY = Object.freeze({
  eyebrow: "기술 소개",
  title: ["사진은 빨리 찾아 기록하고,", "기록은 3D 지도에 모아요"],
  footnote: "* 팀 테스트 폰에서 사진 3만 장을 잰 시간이에요. 기기에 따라 달라질 수 있어요.",
});

export const TECH_STEPS = Object.freeze([
  { key: "find", label: "사진 찾기", side: "left" },
  { key: "map", label: "지도 모으기", side: "right" },
]);

// scene_index for analytics is the position here + 1 (1-6). `hint` is the hand-written line on the phone screen;
// `hintTouch` replaces it on devices without hover.
export const TECH_SCENES = Object.freeze([
  { step: "find", title: "사진마다 찍은 곳이 담겨 있어요", hint: "사진에 올려 보세요", hintTouch: "사진을 눌러 보세요", interaction: "flip" },
  { step: "find", title: "3만 장도 30초면 그곳 사진만 모여요", footnote: true, hint: "아무 사진이나 눌러 보세요", interaction: "gather" },
  { step: "find", title: "고른 사진으로 바로 기록해요", hint: "사진을 골라 기록해 보세요", interaction: "save" },
  { step: "map", title: "다녀온 곳이 지도에 칠해져요", hint: "지도를 눌러 보세요", interaction: "map_toggle" },
  { step: "map", title: "모은 기록을 3D 지구본에서 돌려 봐요", hint: "손가락으로 돌려 보세요", interaction: "globe_drag" },
  { step: "map", title: "다녀온 곳을 타임라인 영상으로 공유해요", hint: "연도를 눌러 보세요", interaction: "year_pick", share: true },
]);

// The share scene (the app's timeline video, app PR #436). Change it here, nothing else needs to move:
// - `stops` are the example countries the drawn preview flies through, in date order (at least two, like the app).
// - `media` swaps the drawn preview for a real capture once the share design is final, e.g.
//   { type: "video", src: "/assets/tech/share.mp4", poster: "/assets/tech/share.webp" } or { type: "image", src: "/assets/tech/share.webp" }.
//   The file goes in public/assets/tech/ and is shown inside the phone (9:19.5 or taller fits best).
// - `enabled: false` drops the share scene and its title (the section ends on the globe), for example while the
//   share feature is not in the released app.
export const TECH_SHARE = Object.freeze({
  enabled: true,
  button: "영상 공유하기",
  media: null,
  stops: Object.freeze([
    { lon: 127.8, lat: 36.4, name: "대한민국", date: "2023. 05. 12" },
    { lon: 138.5, lat: 36.2, name: "일본", date: "2024. 03. 02" },
    { lon: -98.0, lat: 39.5, name: "미국", date: "2025. 01. 18" },
    { lon: 2.4, lat: 46.6, name: "프랑스", date: "2026. 07. 09" },
  ]),
});

export const techScenes = (share = TECH_SHARE) => TECH_SCENES.filter((scene) => !scene.share || share.enabled);

// Example months on the back of flipped photos (the sprite's place labels), and the dots on the scene-5 globe.
export const TECH_MONTHS = Object.freeze({
  제주: "2025. 10", 일본: "2025. 03", 미국: "2025. 01", 부산: "2024. 08", 서울: "2024. 05", 강릉: "2024. 12",
  속초: "2024. 07", 경주: "2023. 09", 전주: "2024. 04", 여수: "2023. 08", 대만: "2025. 05", 태국: "2025. 01",
  베트남: "2024. 02", 프랑스: "2026. 07", 스페인: "2026. 06",
});
export const TECH_VISITED = Object.freeze([[127.8, 36.4], [129.2, 35.8], [126.5, 33.4], [139.7, 35.7], [135.5, 34.7], [-118.2, 34.0], [-74.0, 40.7], [2.35, 48.86], [100.5, 13.75]]);
