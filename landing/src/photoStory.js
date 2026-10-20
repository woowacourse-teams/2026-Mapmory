// The photo-roll landing (2026-10-18, sketch v11): the hero photo wall and the #how scroll stories (#roll, #rec).
// Copy, places and timings live here so the components, the engines and the tests share one source.
// Every sentence must stay true on main and develop of the app; change it only with a code check of both.
import sprite from "./data/storySprite.js";
import { withTopicParticle } from "./koreanParticle.js";

// The sprite: one openly licensed photo per cell (credits in src/data/photo-credits.json, file "...tiles.webp#<k>").
export const SPRITE_URL = "/assets/story/tiles.webp";
export const FOOD_URL = "/assets/story/food.webp"; // two 160px cells: Korean BBQ, then a citrus
export const SPRITE = Object.freeze({
  cols: sprite.cols,
  cell: sprite.cell,
  colors: sprite.colors,
  tiles: sprite.tiles.map(([place, label]) => ({ place, label })),
});

let spriteImage = null;
// One shared Image for the hero, the roll and the record story (created on first use, so tests never touch the DOM).
export function getSpriteImage() {
  if (!spriteImage && typeof Image !== "undefined") {
    spriteImage = new Image();
    spriteImage.decoding = "async";
    spriteImage.src = SPRITE_URL;
  }
  return spriteImage;
}

// ── hero: an example camera roll of 21,474 photos; a place chip keeps only that place's photos.
export const HERO_LIBRARY = 21474;
export const HERO_PLACES = Object.freeze([
  { key: "jeju", name: "제주", count: 31 },
  { key: "japan", name: "일본", count: 86 },
  { key: "usa", name: "미국", count: 52 },
]);
// Nobody has touched the wall after this long: pick 제주 once so the idea lands without effort (no analytics event).
export const HERO_AUTO_PICK_MS = 4500;
export const HERO_AUTO_PICK = "jeju";
export const HERO_COPY = Object.freeze({
  kicker: "예시 사진첩이에요 · 장소를 눌러 보세요",
  title: `사진 ${HERO_LIBRARY.toLocaleString("ko-KR")}장`,
  sub: "장소를 누르면 그곳 사진만",
  diff: "기록 안 해도, 찍은 사진으로 여행 지도",
  ask: "어떻게 찾았는지 보기 ↓",
  made: "Mapmory 팀이 만들었어요",
});

export const heroPickedCopy = ({ name, count }) => ({
  kicker: `${name} 사진 ${count}장을 불러왔어요`,
  sub: `${withTopicParticle(name)} ${count}장`,
});

// ── #how: two pinned scroll stories, six beats in all. scene_index (how_story_scene) is the global beat, 1-6.
// Each beat is { title: [line 1, emphasized line 2], body: [paragraphs], note?, kicker?, noteShort? }.
// Phones show the title only, not the body and note: short words that never cover the phone. Only the three core beats
// (find by place, food photos, only picked photos go up) carry a coloured hand-written kicker: one impact, 2-3 times in all.
export const ROLL_BEATS = Object.freeze([
  { title: ["내 사진첩에서도", "될까요?"], body: ["여행이 쌓일수록 사진은 묻혀요. 다시 꺼내 보려면 끝없이 넘겨야 하죠."] },
  {
    title: ["사진에 담긴 위치로,", "3만 장도 30초 안에"],
    footnote: true,
    body: ["사진마다 찍은 곳이 이미 담겨 있어요. 그 위치만 읽어 장소별로 나눠요. 이 사진첩(21,474장)은 21초면 끝나요. 사진은 그대로, 모두 내 폰 안에서요."],
    note: "* 팀 테스트 폰에서 사진 3만 장을 잰 시간이에요. 기기에 따라 달라질 수 있어요.",
    kicker: "위치만 읽어요 · 내 폰 안에서",
    noteShort: "* 팀 테스트 폰에서 잰 시간이에요.",
  },
  { title: ["음식 사진도", "되나요?"], body: ["사진 속 내용이 아니라 찍은 곳으로 찾아요. 제주에서 찍었다면 흑돼지 사진도 제주로 모여요. 위치가 없는 캡처나 받은 사진은 자동으로 모이지 않아요. 필요하면 직접 골라 넣을 수 있어요."], kicker: "흑돼지 사진도 제주로" },
]);
export const REC_BEATS = Object.freeze([
  { title: ["그래서", "어떻게 기록되나요?"], body: ["찾은 31장 중 마음에 드는 사진만 고르면 돼요. 기록을 저장할 때 고른 사진만 앱에 올라가고, 나만 볼 수 있어요. 나머지는 폰에만 있어요."], kicker: "고른 사진만 올라가요" },
  { title: ["다녀온 곳이", "지도에 칠해져요"], body: ["제주를 저장하면 지도에 제주도가 칠해지고, 고른 사진이 그 자리에 꽂혀요. 국내는 시·도 단위로, 해외는 나라 단위로 칠해져요."] },
  { title: ["여행이 쌓일수록", "지구본이 채워져요"], body: ["일본, 대만, 미국… 다녀온 곳이 하나씩. 지구본을 직접 돌려 보세요."] },
]);

// Track heights (svh) and where each beat starts in the track's progress p (0..1), as in the sketch.
export const STORY_TRACKS = Object.freeze([
  { key: "roll", vh: 330, starts: [0, 0.3, 0.66], beats: ROLL_BEATS, label: "어떻게 찾았냐면" },
  { key: "rec", vh: 320, starts: [0, 0.34, 0.58], beats: REC_BEATS, label: "어떻게 기록되나요" },
]);
export const STORY_SCENES = STORY_TRACKS.reduce((n, t) => n + t.beats.length, 0); // 6
// A comfortable p inside each beat (QA scripts scroll here): scene n is SCENE_AT[n - 1] = [track index, p].
export const SCENE_AT = Object.freeze([[0, 0.15], [0, 0.5], [0, 0.86], [1, 0.17], [1, 0.47], [1, 0.82]]);

export function beatOf(track, p) {
  let beat = 0;
  track.starts.forEach((start, i) => { if (p >= start) beat = i; });
  return beat;
}
// global scene (1-6) of beat `beat` in track `trackIndex`
export function sceneOf(trackIndex, beat) {
  let offset = 0;
  for (let i = 0; i < trackIndex; i++) offset += STORY_TRACKS[i].beats.length;
  return offset + beat + 1;
}

// Below this viewport height (landscape phones) or with reduced motion the stories show their finished frames.
export const STATIC_MAX_HEIGHT = 500;

// ── trust + FAQ and the download section (sketch v11, the end of the page)
export const TRUST_LINE = "사진을 찾는 일은 폰 안에서만. 앱에는 직접 고른 사진만 올라가고, 나만 볼 수 있어요.";
export const FREE_LINE = "무료로 받을 수 있어요";
export const FAQ_ITEMS = Object.freeze([
  { question: "사진첩 사진을 전부 가져가나요?", answer: "아니요. 사진을 찾는 일은 폰 안에서만 이뤄지고, 서버에는 기록을 저장할 때 직접 고른 사진만 올라가요." },
  { question: "사진 접근을 전부 허용해야 하나요?", answer: "장소로 자동으로 찾으려면 사진 전체 접근이 필요해요. 일부만 허용하면 앱이 전체 허용을 안내해요. 직접 골라서 올리는 건 일부만 허용해도 돼요." },
  { question: "위치 정보가 없는 사진은요?", answer: "자동으로 찾지는 못하지만, 사진첩에서 직접 골라 기록에 넣을 수 있어요." },
  { question: "남긴 기록은 지울 수 있나요?", answer: "네. 여행 기록은 앱에서 언제든 삭제할 수 있어요." },
  { question: "가입해야 하나요?", answer: "아니요. 로그인 없이 바로 써 볼 수 있어요. 기록을 계속 남기고 싶을 때 카카오 계정을 연결하면 돼요." },
  { question: "동영상도 찾아 주나요?", answer: "지금은 사진만 찾아요." },
  { question: "iCloud에 올라간 사진도 되나요?", answer: "네. 아이폰에서 iCloud 사진을 써도 찾을 수 있고, 고른 사진은 원본을 받아와서 올려요." },
  { question: "무료인가요?", answer: "네. App Store와 Google Play에서 무료로 받아 바로 쓸 수 있어요." },
]);
