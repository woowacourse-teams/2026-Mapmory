// The #tech section (Sprint 3 "기술 소개", 2026-10-27, 티온's approved sketch v8; 10-28 scroll-driven): one phone pinned
// in a sticky stage, two steps. Step 1 (사진 찾기) keeps the phone on the left, step 2 (지도 모으기) on the right; it
// slides only between steps. Each scene shows a wobbling hand-written step label and one title. No small explanatory
// text under the titles (티온, 10-23). Copy rules from AGENTS.md apply: `3만 장도 30초` keeps its footnote.
//
// 10-28 (티온: visitors flick through without reading): every scene's one visual change is driven by the scroll
// position, not by timers or taps. A fast scroll shows the story in fast-forward; a stop shows a readable still. Taps
// stay as optional extras. The last two scenes are one continuous trip: the globe leaves the phone, flies through the
// recorded places in date order at the speed of the scroll, and goes back into the phone as the video to share.

export const TECH_COPY = Object.freeze({
  eyebrow: "기술 소개",
  title: ["사진은 빨리 찾아 기록하고,", "기록은 3D 지도에 모아요"],
  // the first full read; later syncs reuse stored coordinates (PhotoMetadataSync.shouldReuseCoordinates)
  footnote: "* 팀 테스트 폰에서 사진 3만 장을 처음 훑는 데 걸린 시간이에요. 다음부터는 새 사진만 읽어요. 기기에 따라 달라질 수 있어요.",
});

// Layout variant (4단계 시안, gates/variants.json): "base" words under the phone in a phone frame, "above" the words
// above the phone on phones, "bare" the screen as a plain card with no phone frame. `?tsv=<key>` on the URL shows
// another one for a side-by-side check; the chosen one is the default here.
export const TECH_VARIANTS = Object.freeze(["base", "above", "bare"]);
export const TECH_VARIANT = "base";

export const TECH_STEPS = Object.freeze([
  { key: "find", label: "사진 찾기", side: "left" },
  { key: "map", label: "지도 모으기", side: "right" },
]);

// scene_index for analytics is the position here + 1 (1-6). `interaction` names the optional tap each scene still
// answers to. `trip` marks the scene whose scroll room is long (the globe's trip plays through it). A   keeps a
// phrase on one line where a 360px phone would split it.
export const TECH_SCENES = Object.freeze([
  { step: "find", title: "사진마다 찍은 곳이 담겨 있어요", interaction: "flip" },
  // "누른 곳": the place of the photo you press, in the hint's own verb (cold readers asked how a place gets "chosen")
  { step: "find", title: "3만 장도 30초면 누른 곳 사진만 모여요", footnote: true, interaction: "gather" },
  // the safety line (brief C): the UT sessions asked where the photos go; only the picked ones are uploaded
  { step: "find", title: "고른 사진만 앱에 올라가고 나머지는 폰에 남아요", interaction: "save" },
  { step: "map", title: "다녀온 곳이 지도에 칠해져요", interaction: "map_toggle" },
  { step: "map", title: "모은 기록을 3D 지구본에서 돌려 봐요", interaction: "globe_drag", trip: true },
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

// The example records, one source for the whole story (티온, 10-28: the trip must visit the places that were really
// recorded, not random countries): the place and month on the back of each photo in the roll (the sprite's place
// labels, src/data/storySprite.js), the dots on the globe, and the trip the globe flies in date order. Places abroad
// are recorded as countries and sit at the country's middle; places at home are cities. 경주 is the city the map scene
// fills. Dates are "YYYY. MM. DD"; the photo backs show the month.
export const TECH_PLACES = Object.freeze([
  { name: "여수", lon: 127.66, lat: 34.76, date: "2023. 08. 12" },
  { name: "경주", lon: 129.22, lat: 35.86, date: "2023. 09. 23" },
  { name: "베트남", lon: 106.3, lat: 16.5, date: "2024. 02. 09" },
  { name: "전주", lon: 127.15, lat: 35.82, date: "2024. 04. 13" },
  { name: "서울", lon: 126.98, lat: 37.57, date: "2024. 05. 11" },
  { name: "속초", lon: 128.59, lat: 38.21, date: "2024. 07. 06" },
  { name: "부산", lon: 129.08, lat: 35.18, date: "2024. 08. 17" },
  { name: "강릉", lon: 128.88, lat: 37.75, date: "2024. 12. 24" },
  { name: "미국", lon: -98.5, lat: 39.8, date: "2025. 01. 05" },
  { name: "태국", lon: 100.99, lat: 15.87, date: "2025. 01. 25" },
  { name: "일본", lon: 138.25, lat: 36.2, date: "2025. 03. 14" },
  { name: "대만", lon: 120.96, lat: 23.7, date: "2025. 05. 02" },
  { name: "제주", lon: 126.53, lat: 33.38, date: "2025. 10. 03" },
  { name: "스페인", lon: -3.7, lat: 40.4, date: "2026. 06. 27" },
  { name: "프랑스", lon: 2.2, lat: 46.2, date: "2026. 07. 18" },
]);
// month on the back of a flipped photo
export const TECH_MONTHS = Object.freeze(Object.fromEntries(TECH_PLACES.map((p) => [p.name, p.date.slice(0, 8)])));
// the dots on the globe
export const TECH_VISITED = Object.freeze(TECH_PLACES.map((p) => [p.lon, p.lat]));
// the trip: every recorded place, oldest first
export const TECH_TRIP = Object.freeze([...TECH_PLACES].sort((a, b) => a.date.localeCompare(b.date)));
