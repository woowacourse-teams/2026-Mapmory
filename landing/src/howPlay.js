// "3단계면 끝나요" as three taps: pick a place -> tap the photos to keep -> save and the province fills.
const photo = (name) => `/assets/photos/${name}.webp`;

// Korean provinces only, and none of the hero's places, so the two demos never show the same photos.
export const HOW_PLAY_PLACES = Object.freeze([
  {
    key: "busan",
    label: "부산",
    province: "부산",
    regionCode: "KR-26",
    photos: [
      { src: photo("busan-01"), alt: "해변 열차가 숲길 레일 위를 달리는 부산 해안" },
      { src: photo("busan-02"), alt: "소나무 사이로 보이는 바닷가 바위와 정자" },
      { src: photo("busan-03"), alt: "고층 건물 아래 펼쳐진 해운대 해변" },
      { src: photo("busan-04"), alt: "알록달록한 벽 사이 감천문화마을 계단" },
    ],
  },
  {
    key: "gangwon",
    label: "강원",
    province: "강원",
    regionCode: "KR-42",
    photos: [
      { src: photo("gangwon-01"), alt: "단풍 든 설악산 골짜기와 그 안의 절" },
      { src: photo("gangwon-02"), alt: "단풍 사이 설악산의 큰 불상" },
      { src: photo("gangwon-03"), alt: "단풍 든 산 아래 돌다리" },
      { src: photo("gangwon-04"), alt: "강릉 바닷가의 파란 사진 프레임 조형물" },
    ],
  },
  {
    key: "gyeongbuk",
    label: "경주",
    // The map paints provinces, so 경주 fills 경북 and the result line names the province.
    province: "경북",
    regionCode: "KR-47",
    photos: [
      { src: photo("gyeongbuk-01"), alt: "연등이 걸린 불국사 돌계단" },
      { src: photo("gyeongbuk-02"), alt: "잔디밭 위에 선 첨성대" },
      { src: photo("gyeongbuk-03"), alt: "분홍 억새 너머 커다란 나무" },
      { src: photo("gyeongbuk-04"), alt: "물에 비친 월정교" },
    ],
  },
]);

export const HOW_PLAY_STEPS = Object.freeze(["장소 고르기", "사진 고르기", "저장"]);
export const HOW_PLAY_PROVINCE_TOTAL = 17;

export const initialHowPlayState = Object.freeze({ step: 0, placeKey: null, picked: [], filled: [] });

export function howPlayReducer(state, action) {
  switch (action.type) {
    case "pick-place": {
      const place = HOW_PLAY_PLACES.find(({ key }) => key === action.placeKey);
      if (!place) return state;
      // Like the app's new-record screen, nothing starts selected; tap the keepers or pick them all. Saved provinces stay filled.
      return { ...state, step: 1, placeKey: place.key, picked: [] };
    }
    case "toggle-photo": {
      if (state.step !== 1) return state;
      const picked = state.picked.includes(action.index)
        ? state.picked.filter((index) => index !== action.index)
        : [...state.picked, action.index].sort((a, b) => a - b);
      return { ...state, picked };
    }
    case "pick-all": {
      if (state.step !== 1) return state;
      const place = HOW_PLAY_PLACES.find(({ key }) => key === state.placeKey);
      return { ...state, picked: place.photos.map((_, index) => index) };
    }
    case "save": {
      if (state.step !== 1 || state.picked.length === 0) return state;
      const filled = state.filled.includes(state.placeKey) ? state.filled : [...state.filled, state.placeKey];
      return { ...state, step: 2, filled };
    }
    case "reset":
      return initialHowPlayState;
    default:
      return state;
  }
}
