// The #tech section (Sprint 3 "기술 소개", 2026-10-27, 티온's approved sketch v8; 10-28 scroll-driven): one phone pinned
// in a sticky stage, two steps. Step 1 (사진 찾기) keeps the phone on the left, step 2 (지도 모으기) on the right; it
// slides only between steps. Each scene shows a hand-written step label and one title. No small explanatory
// text under the titles (티온, 10-23).
//
// 10-28 (티온: visitors flick through without reading): every scene's one visual change is driven by the scroll
// position, not by timers or taps. A fast scroll shows the story in fast-forward; a stop shows a readable still. Taps
// stay as optional extras. The last two scenes are one continuous trip: the globe leaves the phone, flies through the
// recorded places in date order at the speed of the scroll, and goes back into the phone as the video to share.
//
// 10-29 (티온): step 1 shows the app's record form: one place typed in, and that place's photos gather by themselves
// (the turning photo roll and the 3만 장 counter are gone). Step 2 only shows visited places filling in: 시·도 on the
// Korea map, then countries on a globe that turns with the scroll (the trip out of the phone and the share video are
// off). Titles are short.

export const TECH_COPY = Object.freeze({
  eyebrow: "기술 소개",
  title: ["장소만 넣으면", "사진이 지도에 모여요"],
});

// Layout variant (4단계 시안, gates/variants.json): "base" words under the phone in a phone frame, "above" the words
// above the phone on phones, "bare" the screen as a plain card with no phone frame. `?tsv=<key>` on the URL shows
// another one for a side-by-side check; the chosen one is the default here.
export const TECH_VARIANTS = Object.freeze(["base", "above", "bare"]);

// the map pin glyph (24×24 path) drawn by the component (the record field, the found pill)
export const TECH_PIN = "M12 2a7 7 0 0 0-7 7c0 5 7 13 7 13s7-8 7-13a7 7 0 0 0-7-7Zm0 9.5A2.5 2.5 0 1 1 12 6.5a2.5 2.5 0 0 1 0 5Z";
export const TECH_VARIANT = "base";

export const TECH_STEPS = Object.freeze([
  { key: "find", label: "사진 찾기", side: "left" },
  { key: "map", label: "지도 모으기", side: "right" },
]);

// scene_index for analytics is the position here + 1 (1-7 with the share scene on). `key` names the scene for the
// engine; `interaction` names the optional tap a scene answers to (the safety scene has none). The first three scenes
// share one screen, the app's own "어디 사진을 불러올까요?" form (티온, 10-29).
export const TECH_SCENES = Object.freeze([
  { key: "type", step: "find", title: "장소만 넣으면", interaction: "place_pick" },
  { key: "gather", step: "find", title: "사진첩에서 알아서 뽑아와요", interaction: "gather" },
  // the safety line, while scrolling (티온, 10-29; UT: "서버로 넘어간다는 생각이 계속 들어서 불안하다"). True on main and
  // develop: the find runs on the phone against bundled region outlines (PhotoRecommendationRegion); only the typed
  // place text is searched online, never a photo
  { key: "safe", step: "find", title: "찾는 동안 사진은 서버로 안 가요" },
  { key: "save", step: "find", title: "고른 사진만 앱에 올라가요", interaction: "save" },
  { key: "map", step: "map", title: "다녀온 곳이 지도에 칠해져요", interaction: "map_toggle" },
  { key: "globe", step: "map", title: "해외는 나라별로 칠해져요", interaction: "globe_drag" },
  { step: "map", title: "다녀온 곳을 타임라인 영상으로 공유해요", interaction: "year_pick", share: true },
]);

// The share scene (the app's timeline video, app PR #436). Change it here, nothing else needs to move:
// - `media` is the real share video shown in the phone (10-27, 티온's captures; the "classic" style). Another style:
//   run scripts/encode-tech-share-video.sh on its capture (it crops off the video's own year bar and date line, which
//   are about 6px at phone-mockup size) and point `sources`/`poster` at the new files in public/assets/tech/.
//   For a still image instead: { type: "image", src, alt }.
//   `years` are the year buttons drawn under the video and the second each year starts in it (the same in all four
//   captures); a button seeks there and plays, and the playing year lights up. The video never plays by itself
//   (티온, 10-28: waiting for a replay feels slow); the scroll-driven trip before it shows the same journey.
//   `media: null` shows the dark screen with the share button only; the trip still lands in it.
// - `enabled: false` drops the share scene and its title (the trip ends back in the globe screen), for example while
//   the share feature is not in the released app.
// Off for a few hours on 10-29, back on as the seventh scene the same day (티온: "마지막 공유파트만 넣어서 배포하자");
// the analytics check on scene_index (src/analytics.js) allows 1-7.
export const TECH_SHARE = Object.freeze({
  enabled: true,
  button: "영상 공유하기",
  media: Object.freeze({
    type: "video",
    // the browser plays the first it can: VP9 (Chrome, Firefox, Android), then H.264 (Safari)
    sources: Object.freeze([
      { src: "/assets/tech/share-classic.webm", type: "video/webm" },
      { src: "/assets/tech/share-classic.mp4", type: "video/mp4" },
    ]),
    poster: "/assets/tech/share-classic.webp",
    years: Object.freeze([
      { label: "2023", at: 0 },
      { label: "2024", at: 1.6 },
      { label: "2025", at: 5.8 },
      { label: "2026", at: 16.1 },
    ]),
  }),
});

export const techScenes = (share = TECH_SHARE) => TECH_SCENES.filter((scene) => !scene.share || share.enabled);

// The example records, one source for the whole story (티온, 10-28: the map must show the places that were really
// recorded, not random ones). Places at home are cities and fill their 시·도 (`sd`, a key of `pv` in
// src/data/tech-korea-map.json); places abroad are countries and fill the country (`country`, its name in
// world-atlas). Both fill in date order. Dates are "YYYY. MM. DD".
export const TECH_PLACES = Object.freeze([
  { name: "여수", lon: 127.66, lat: 34.76, date: "2023. 08. 12", sd: "전남" },
  { name: "경주", lon: 129.22, lat: 35.86, date: "2023. 09. 23", sd: "경북" },
  { name: "베트남", lon: 106.3, lat: 16.5, date: "2024. 02. 09", country: "Vietnam" },
  { name: "전주", lon: 127.15, lat: 35.82, date: "2024. 04. 13", sd: "전북" },
  { name: "서울", lon: 126.98, lat: 37.57, date: "2024. 05. 11", sd: "서울" },
  { name: "속초", lon: 128.59, lat: 38.21, date: "2024. 07. 06", sd: "강원" },
  { name: "부산", lon: 129.08, lat: 35.18, date: "2024. 08. 17", sd: "부산" },
  { name: "강릉", lon: 128.88, lat: 37.75, date: "2024. 12. 24", sd: "강원" },
  { name: "미국", lon: -98.5, lat: 39.8, date: "2025. 01. 05", country: "United States of America" },
  { name: "태국", lon: 100.99, lat: 15.87, date: "2025. 01. 25", country: "Thailand" },
  { name: "일본", lon: 138.25, lat: 36.2, date: "2025. 03. 14", country: "Japan" },
  { name: "대만", lon: 120.96, lat: 23.7, date: "2025. 05. 02", country: "Taiwan" },
  { name: "제주", lon: 126.53, lat: 33.38, date: "2025. 10. 03", sd: "제주" },
  { name: "스페인", lon: -3.7, lat: 40.4, date: "2026. 06. 27", country: "Spain" },
  { name: "프랑스", lon: 2.2, lat: 46.2, date: "2026. 07. 18", country: "France" },
]);
// what fills, oldest first, each once: the 시·도 on the Korea map and the countries on the globe (home first)
const byDate = [...TECH_PLACES].sort((a, b) => a.date.localeCompare(b.date));
export const TECH_PROVINCES = Object.freeze([...new Set(byDate.filter((p) => p.sd).map((p) => p.sd))]);
export const TECH_COUNTRIES = Object.freeze(["South Korea", ...new Set(byDate.filter((p) => p.country).map((p) => p.country))]);
