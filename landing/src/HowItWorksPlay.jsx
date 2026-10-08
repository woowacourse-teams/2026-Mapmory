import { useEffect, useMemo, useReducer, useRef } from "react";
import { ArrowCounterClockwise, ArrowRight, Check, MapPin } from "@phosphor-icons/react";
import koreaProvinces from "./data/korea-provinces.json";
import {
  HOW_PLAY_PLACES,
  HOW_PLAY_PROVINCE_TOTAL,
  HOW_PLAY_STEPS,
  howPlayReducer,
  initialHowPlayState,
} from "./howPlay.js";
import { withSubjectParticle } from "./koreanParticle.js";

const KOREA_BOUNDS = { minLng: 124.5, maxLng: 130.05, minLat: 33, maxLat: 38.75 };
const KOREA_LONGITUDE_SCALE = 0.81;

function useKoreaPaths() {
  return useMemo(() => {
    const project = (lng, lat) => [
      (lng - KOREA_BOUNDS.minLng) * KOREA_LONGITUDE_SCALE * 100,
      (KOREA_BOUNDS.maxLat - lat) * 100,
    ];
    const width = (KOREA_BOUNDS.maxLng - KOREA_BOUNDS.minLng) * KOREA_LONGITUDE_SCALE * 100;
    const height = (KOREA_BOUNDS.maxLat - KOREA_BOUNDS.minLat) * 100;
    return {
      viewBox: `-10 -10 ${width + 20} ${height + 20}`,
      regions: koreaProvinces.map((province) => ({
        id: province.code,
        // The largest ring's average point marks small provinces such as 부산 with a pulse.
        center: (() => {
          const ring = province.rings.reduce((largest, current) => (current.length > largest.length ? current : largest));
          const points = ring.map(([lng, lat]) => project(lng, lat));
          return points.reduce(([x, y], [px, py]) => [x + px / points.length, y + py / points.length], [0, 0]);
        })(),
        d: province.rings.map((ring) => `${ring.map(([lng, lat], index) => {
          const [x, y] = project(lng, lat);
          return `${index === 0 ? "M" : "L"}${x.toFixed(1)} ${y.toFixed(1)}`;
        }).join("")}Z`).join(""),
      })),
    };
  }, []);
}

function HowItWorksPlay() {
  const [state, dispatch] = useReducer(howPlayReducer, initialHowPlayState);
  const map = useKoreaPaths();
  const place = HOW_PLAY_PLACES.find(({ key }) => key === state.placeKey);
  const filledCodes = HOW_PLAY_PLACES.filter(({ key }) => state.filled.includes(key)).map(({ regionCode }) => regionCode);
  const remaining = HOW_PLAY_PLACES.filter(({ key }) => !state.filled.includes(key));
  const promptRef = useRef(null);
  const hasInteractedRef = useRef(false);
  const announcement = state.step === 0
    ? "어디 다녀왔어요?"
    : state.step === 1
      ? `${place.label} 사진 ${place.photos.length}장을 찾았어요`
      : `${withSubjectParticle(place.province)} 칠해졌어요. ${filledCodes.length} / ${HOW_PLAY_PROVINCE_TOTAL}`;

  // Each step replaces the screen, so hand focus to its prompt instead of dropping it on <body>.
  useEffect(() => {
    if (!hasInteractedRef.current) return;
    promptRef.current?.focus({ preventScroll: true });
  }, [state.step, state.placeKey]);

  const act = (action) => {
    hasInteractedRef.current = true;
    dispatch(action);
  };

  return (
    <div className="how-play">
      <ol className="how-play-steps" aria-label="진행 단계">
        {HOW_PLAY_STEPS.map((label, index) => (
          <li key={label} className={index < state.step ? "is-done" : index === state.step ? "is-current" : ""} aria-current={index === state.step ? "step" : undefined}>
            <span aria-hidden="true">{index < state.step ? <Check size={14} weight="bold" /> : index + 1}</span>
            {label}
          </li>
        ))}
      </ol>

      <div className="how-play-phone">
        <p className="sr-only" aria-live="polite">{hasInteractedRef.current ? announcement : ""}</p>
        <div className="how-play-screen" key={`${state.step}-${state.placeKey}`}>
          {state.step === 0 && (
            <>
              <p className="how-play-prompt" ref={promptRef} tabIndex={-1}>어디 다녀왔어요?</p>
              <div className="how-play-places">
                {HOW_PLAY_PLACES.map((option) => (
                  <button
                    key={option.key}
                    type="button"
                    aria-label={state.filled.includes(option.key) ? `${option.label}, 기록함` : option.label}
                    onClick={() => act({ type: "pick-place", placeKey: option.key })}
                  >
                    <img src={option.photos[0].src} alt="" loading="lazy" decoding="async" />
                    <span>{option.label}</span>
                    {state.filled.includes(option.key) && <Check className="how-play-place-done" size={16} weight="bold" aria-hidden="true" />}
                  </button>
                ))}
              </div>
            </>
          )}

          {state.step === 1 && place && (
            <>
              <p className="how-play-prompt" ref={promptRef} tabIndex={-1}>{place.label} 사진 {place.photos.length}장을 찾았어요</p>
              <div className="how-play-hint">
                <span>남길 사진을 탭해요</span>
                {state.picked.length < place.photos.length && (
                  <button type="button" onClick={() => act({ type: "pick-all" })}>모두 선택</button>
                )}
              </div>
              <div className="how-play-photos">
                {place.photos.map((item, index) => {
                  const isPicked = state.picked.includes(index);
                  return (
                    <button
                      key={item.src}
                      type="button"
                      className={isPicked ? "is-picked" : ""}
                      style={{ "--i": index }}
                      aria-pressed={isPicked}
                      aria-label={item.alt}
                      onClick={() => act({ type: "toggle-photo", index })}
                    >
                      <img src={item.src} alt="" loading="lazy" decoding="async" />
                      <span className="how-play-check" aria-hidden="true"><Check size={14} weight="bold" /></span>
                    </button>
                  );
                })}
              </div>
              <button className="how-play-save" type="button" disabled={state.picked.length === 0} onClick={() => act({ type: "save" })}>
                {state.picked.length > 0 ? `${state.picked.length}장 저장하기` : "사진을 골라 주세요"}
              </button>
            </>
          )}

          {state.step === 2 && place && (
            <>
              <div className="how-play-map">
                <svg viewBox={map.viewBox} role="img" aria-label={`대한민국 지도에 ${filledCodes.length}곳이 칠해짐`}>
                  {map.regions.map((region) => (
                    <path
                      key={region.id}
                      d={region.d}
                      className={filledCodes.includes(region.id) ? (region.id === place.regionCode ? "is-new" : "is-filled") : ""}
                    />
                  ))}
                  {map.regions.filter((region) => region.id === place.regionCode).map((region) => (
                    <circle key={region.id} className="how-play-pulse" cx={region.center[0]} cy={region.center[1]} r="18" />
                  ))}
                </svg>
                <div className="how-play-stamp" aria-hidden="true">
                  <MapPin size={16} weight="fill" />
                  <strong>{place.label}</strong>
                  <small>기록 완료</small>
                </div>
              </div>
              <p className="how-play-prompt" ref={promptRef} tabIndex={-1}>
                <strong>{withSubjectParticle(place.province)}</strong> 칠해졌어요
                <span className="how-play-count">{filledCodes.length} / {HOW_PLAY_PROVINCE_TOTAL}</span>
              </p>
              {remaining.length > 0 ? (
                <button className="how-play-again" type="button" onClick={() => act({ type: "pick-place", placeKey: remaining[0].key })}>
                  {remaining[0].label}도 칠해 보기
                  <ArrowRight size={16} weight="bold" />
                </button>
              ) : (
                <button className="how-play-again" type="button" onClick={() => act({ type: "reset" })}>
                  <ArrowCounterClockwise size={16} weight="bold" />
                  처음부터 다시
                </button>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}

export { HowItWorksPlay };
