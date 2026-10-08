import { lazy, Suspense, useCallback, useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import {
  AppleLogo,
  ArrowLeft,
  ArrowRight,
  Bell,
  CaretDown,
  CloudArrowUp,
  DeviceMobile,
  DownloadSimple,
  EnvelopeSimple,
  GlobeHemisphereEast,
  HandSwipeLeft,
  MapPin,
  Moon,
  NavigationArrow,
  Play,
  ShieldCheck,
  Sun,
} from "@phosphor-icons/react";
import { ANALYTICS_EVENTS, trackEvent } from "./analytics.js";
import { classifyGlobeGesture } from "./globe-gesture.js";
import { useWorldCountries } from "./worldCountries.js";
import { PhotoFinderHero } from "./PhotoFinderHero.jsx";
import { HowItWorksPlay } from "./HowItWorksPlay.jsx";
import { PhotoCredits } from "./PhotoCredits.jsx";
import {
  createWorldMemoryHistoryState,
  isWorldMemoryHistoryEntry,
} from "./worldMemoryHistory.js";
import { subscribeToLaunchWaitlist } from "./waitlist.js";
import { useExperienceAnalytics } from "./useExperienceAnalytics.js";

const GOOGLE_PLAY_URL = import.meta.env.VITE_GOOGLE_PLAY_URL?.trim()
  || "https://play.google.com/store/apps/details?id=com.mapmory.android";
const APP_STORE_URL = "https://apps.apple.com/kr/app/mapmory-%EC%97%AC%ED%96%89-%EA%B8%B0%EB%A1%9D-%EC%95%84%EC%B9%B4%EC%9D%B4%EB%B8%8C/id6807056166";
const Globe = lazy(() => import("react-globe.gl"));
const WORLD_SELECTION_MOTION_MS = 720;
const GLOBE_RENDERER_CONFIG = Object.freeze({ antialias: true, alpha: true, powerPreference: "high-performance" });

function currentTimeMs() {
  return typeof performance !== "undefined" ? performance.now() : Date.now();
}

function elapsedSeconds(startedAt) {
  return Math.max(0, Math.round((currentTimeMs() - startedAt) / 100) / 10);
}

const memories = [
  {
    key: "jeju-coast",
    id: "410",
    country: "대한민국",
    location: "제주 · 바닷가",
    title: "검은 바위 사이로 밀려오던 제주 바다",
    shortDescription: "파도 소리와 해 질 무렵의 빛만으로도 그날의 제주 여행이 선명하게 돌아와요.",
    image: "/assets/team-jeju-coast.jpg",
    photoCredit: "Mapmory 개발팀 촬영",
    lat: 33.4996,
    lng: 126.5312,
    viewpoint: { lat: 36.35, lng: 127.8, altitude: 2.05 },
  },
  {
    key: "shanghai",
    id: "156",
    country: "중국",
    location: "상하이 · 와이탄",
    title: "황푸강 건너로 번지던 상하이의 밤",
    shortDescription: "불빛이 켜진 푸둥의 스카이라인을 오래 바라보던 여행의 한 장면이에요.",
    image: "/assets/team-shanghai-bund.jpg",
    photoCredit: "Mapmory 개발팀 촬영",
    lat: 31.2304,
    lng: 121.4737,
    viewpoint: { lat: 35.86, lng: 104.2, altitude: 2.05 },
  },
  {
    key: "tokyo",
    id: "392",
    country: "일본",
    location: "도쿄",
    title: "초록불을 따라 걷던 도쿄의 골목",
    shortDescription: "복잡한 전선과 작은 가게, 평범해서 더 오래 남은 도쿄의 오후예요.",
    image: "/assets/team-tokyo-street.jpeg",
    photoCredit: "Mapmory 개발팀 촬영",
    lat: 35.6762,
    lng: 139.6503,
    viewpoint: { lat: 36.2, lng: 138.25, altitude: 2.05 },
  },
  {
    key: "usa-west",
    id: "840",
    country: "미국",
    location: "미국 · 서부 여행",
    title: "붉은 협곡에서 라스베이거스의 밤까지",
    shortDescription: "브라이스와 앤텔로프의 붉은 결, 야자수 아래의 오후와 불빛이 켜진 라스베이거스까지 한 번의 여행으로 이어져요.",
    image: "/assets/team-usa-bryce-canyon.jpg",
    photos: [
      {
        src: "/assets/team-usa-bryce-canyon.jpg",
        caption: "브라이스 캐니언의 끝없는 기둥",
        alt: "푸른 하늘 아래 주황빛 암석 기둥이 펼쳐진 브라이스 캐니언",
      },
      {
        src: "/assets/team-usa-antelope-canyon.jpg",
        caption: "빛이 스며든 앤텔로프 캐니언",
        alt: "붉은 사암 사이로 햇빛이 들어오는 앤텔로프 캐니언",
      },
      {
        src: "/assets/team-usa-las-vegas-day.jpg",
        caption: "야자수 아래 라스베이거스의 오후",
        alt: "맑고 푸른 하늘과 야자수가 보이는 라스베이거스 거리",
      },
      {
        src: "/assets/team-usa-las-vegas-fountain.jpg",
        caption: "분수에 불이 켜진 라스베이거스의 밤",
        alt: "조명이 켜진 분수와 건물이 보이는 라스베이거스 야경",
      },
      {
        src: "/assets/team-usa-las-vegas-venetian.jpg",
        caption: "베네시안 앞에서 마주한 야경",
        alt: "조명이 켜진 베네시안 건물과 광장이 보이는 라스베이거스의 밤",
      },
    ],
    photoCredit: "Mapmory 개발팀 촬영",
    lat: 37.0902,
    lng: -95.7129,
    viewpoint: { lat: 39.8, lng: -98.6, altitude: 2.05 },
  },
];

const TRUST_POINTS = [
  { Icon: DeviceMobile, title: "사진 찾기는 폰 안에서", body: "그 장소에서 찍은 사진인지 폰 안에서 위치·날짜 정보로만 확인해요. 사진첩을 서버로 보내지 않아요." },
  { Icon: CloudArrowUp, title: "고른 사진만 올라가요", body: "기록을 저장할 때 내가 직접 고른 사진만 업로드돼요." },
];

// The panel is only the on-device -> on-save boundary; deleting a record is answered in the FAQ.
const [TRUST_ON_DEVICE, TRUST_ON_SAVE] = TRUST_POINTS;
const { Icon: TrustOnDeviceIcon } = TRUST_ON_DEVICE;
const { Icon: TrustOnSaveIcon } = TRUST_ON_SAVE;

const FAQ_ITEMS = [
  { question: "사진첩 사진을 전부 가져가나요?", answer: "아니요. 사진을 찾는 일은 폰 안에서만 이뤄지고, 서버에는 기록을 저장할 때 직접 고른 사진만 올라가요." },
  { question: "위치 정보가 없는 사진은요?", answer: "자동으로 찾지는 못하지만, 사진첩에서 직접 골라 기록에 넣을 수 있어요." },
  { question: "남긴 기록은 지울 수 있나요?", answer: "네. 여행 기록은 앱에서 언제든 삭제할 수 있어요." },
  { question: "무료인가요?", answer: "네. App Store와 Google Play에서 무료로 받아 바로 쓸 수 있어요." },
];

const memoryByCountry = new Map(memories.map((memory) => [memory.id, memory]));
const memoryByKey = new Map(memories.map((memory) => [memory.key, memory]));
function getGlobePalette(theme) {
  return theme === "dark"
    ? {
        atmosphere: "#93a6b8",
        visited: "#3fd09a",
        visitedHover: "#72efbd",
        unvisited: "#303b4d",
        visitedSide: "#189a6d",
        unvisitedSide: "#1b2532",
        visitedStroke: "#a3f4d3",
        unvisitedStroke: "#778497",
      }
    : {
        atmosphere: "#c5ded2",
        visited: "#65d7a7",
        visitedHover: "#8be9c4",
        unvisited: "#e7ebe6",
        visitedSide: "#2cab7b",
        unvisitedSide: "#c4cec7",
        visitedStroke: "#f7fffb",
        unvisitedStroke: "#aab8af",
      };
}

function applyGlobeRenderQuality(globe) {
  if (!globe) return;
  const maxPixelRatio = window.matchMedia("(max-width: 560px)").matches ? 1.5 : 2.25;
  const pixelRatio = Math.min(Math.max(window.devicePixelRatio || 1, 1), maxPixelRatio);
  globe.renderer()?.setPixelRatio(pixelRatio);
  globe.postProcessingComposer()?.setPixelRatio?.(pixelRatio);
}

function Brand() {
  return (
    <a className="brand" href="#top" aria-label="Mapmory 홈">
      <span>Map</span><strong>mory</strong>
    </a>
  );
}

function ThemeToggle({ theme, onChange }) {
  return (
    <div className="theme-toggle" role="group" aria-label="색상 테마 선택">
      <button type="button" className={theme === "light" ? "is-active" : ""} onClick={() => onChange("light")} aria-pressed={theme === "light"} aria-label="라이트 테마"><Sun size={17} weight="bold" /></button>
      <button type="button" className={theme === "dark" ? "is-active" : ""} onClick={() => onChange("dark")} aria-pressed={theme === "dark"} aria-label="다크 테마"><Moon size={17} weight="fill" /></button>
    </div>
  );
}

function StoreButton({ className = "", placement, platform, label, onSelect, tabIndex }) {
  const isAppStore = platform === "ios";
  const url = isAppStore ? APP_STORE_URL : GOOGLE_PLAY_URL;
  const Icon = isAppStore ? AppleLogo : Play;
  const handleClick = () => {
    trackEvent(
      ANALYTICS_EVENTS.DOWNLOAD_CLICK,
      { cta_placement: placement, store: isAppStore ? "app_store" : "google_play" },
    );
    onSelect?.();
  };

  return <a className={`button button-primary button-store ${className}`} href={url} target="_blank" rel="noreferrer" tabIndex={tabIndex} onClick={handleClick}><Icon size={18} weight="fill" />{label}</a>;
}

function HeaderStoreMenu() {
  const menuRef = useRef(null);
  const [isOpen, setIsOpen] = useState(false);
  const closeMenu = useCallback(() => {
    menuRef.current?.removeAttribute("open");
    setIsOpen(false);
  }, []);

  useEffect(() => {
    if (!isOpen) return undefined;
    const handlePointerDown = (event) => {
      if (!menuRef.current?.contains(event.target)) closeMenu();
    };
    const handleKeyDown = (event) => {
      if (event.key !== "Escape") return;
      closeMenu();
      menuRef.current?.querySelector("summary")?.focus();
    };
    document.addEventListener("pointerdown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("pointerdown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [closeMenu, isOpen]);

  return (
    <details className="header-store-menu" ref={menuRef} onToggle={(event) => setIsOpen(event.currentTarget.open)}>
      <summary className="button button-primary header-store-trigger" aria-label="앱 다운로드 메뉴 열기">
        <DownloadSimple size={18} weight="bold" /><span>앱 받기</span>
      </summary>
      <div className="header-store-popover" role="group" aria-label="Mapmory 앱 다운로드">
        <StoreButton placement="header" platform="ios" label="App Store" onSelect={closeMenu} />
        <StoreButton placement="header" platform="android" label="Google Play" onSelect={closeMenu} />
      </div>
    </details>
  );
}

function LaunchWaitlistForm() {
  const sectionRef = useRef(null);
  const emailRef = useRef(null);
  const hasTrackedView = useRef(false);
  const hasTrackedStart = useRef(false);
  const submitAttemptCountRef = useRef(0);
  const viewTimerRef = useRef(null);
  const [email, setEmail] = useState("");
  const [privacyConsent, setPrivacyConsent] = useState(false);
  const [ageConfirmed, setAgeConfirmed] = useState(false);
  const [submission, setSubmission] = useState({ state: "idle", message: "" });

  useEffect(() => {
    const section = sectionRef.current;
    if (!section || hasTrackedView.current) return undefined;

    const clearViewTimer = () => {
      if (viewTimerRef.current) {
        window.clearTimeout(viewTimerRef.current);
        viewTimerRef.current = null;
      }
    };
    const observer = new IntersectionObserver(([entry]) => {
      const isVisible = entry.isIntersecting && entry.intersectionRatio >= 0.5;
      if (!isVisible) {
        clearViewTimer();
        return;
      }
      if (!hasTrackedView.current && !viewTimerRef.current) {
        viewTimerRef.current = window.setTimeout(() => {
          viewTimerRef.current = null;
          hasTrackedView.current = true;
          trackEvent(ANALYTICS_EVENTS.WAITLIST_FORM_VIEW);
          observer.disconnect();
        }, 1000);
      }
    }, { threshold: 0.5 });
    observer.observe(section);
    return () => {
      clearViewTimer();
      observer.disconnect();
    };
  }, []);

  const trackFormStart = () => {
    if (hasTrackedStart.current) return;
    hasTrackedStart.current = true;
    trackEvent(ANALYTICS_EVENTS.WAITLIST_FORM_START);
  };

  const failValidation = (message, reason, focusTarget) => {
    setSubmission({ state: "error", message });
    trackEvent(ANALYTICS_EVENTS.WAITLIST_SUBMIT_ERROR, {
      error_type: "validation",
      validation_field: reason,
    });
    focusTarget?.focus();
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    submitAttemptCountRef.current += 1;
    trackEvent(ANALYTICS_EVENTS.WAITLIST_SUBMIT_ATTEMPT, {
      attempt_number: submitAttemptCountRef.current,
    });
    if (!emailRef.current?.checkValidity()) {
      failValidation("올바른 이메일 주소를 입력해 주세요.", "invalid_email", emailRef.current);
      return;
    }
    if (!privacyConsent) {
      failValidation("개인정보 수집 및 이용에 동의해 주세요.", "privacy_consent_required");
      return;
    }
    if (!ageConfirmed) {
      failValidation("만 14세 이상임을 확인해 주세요.", "age_confirmation_required");
      return;
    }

    setSubmission({ state: "submitting", message: "" });
    try {
      const status = await subscribeToLaunchWaitlist({
        email: email.trim(),
        privacyConsent,
        ageConfirmed,
      });
      const alreadySubscribed = status === "ALREADY_SUBSCRIBED";
      setSubmission({
        state: "success",
        message: alreadySubscribed
          ? "이미 출시 알림을 신청한 이메일이에요. 출시되면 알려드릴게요."
          : "신청됐어요. Mapmory가 출시되면 가장 먼저 알려드릴게요.",
      });
      trackEvent(ANALYTICS_EVENTS.WAITLIST_SUBMIT, {
        result: alreadySubscribed ? "already_subscribed" : "subscribed",
      });
      setEmail("");
    } catch (error) {
      const reason = error?.reason || "unknown";
      setSubmission({
        state: "error",
        message: reason === "network"
          ? "네트워크 연결을 확인한 뒤 다시 시도해 주세요."
          : "잠시 후 다시 시도해 주세요. 계속되면 Mapmory 팀에 알려주세요.",
      });
      trackEvent(ANALYTICS_EVENTS.WAITLIST_SUBMIT_ERROR, {
        error_type: reason,
      });
    }
  };

  return (
    <div className="waitlist-panel" ref={sectionRef}>
      <form className="waitlist-form" onSubmit={handleSubmit} onFocusCapture={trackFormStart} onChangeCapture={trackFormStart} noValidate>
        <label className="email-field" htmlFor="waitlist-email">
          <span className="sr-only">출시 알림을 받을 이메일</span>
          <EnvelopeSimple size={21} weight="duotone" aria-hidden="true" />
          <input
            id="waitlist-email"
            ref={emailRef}
            type="email"
            inputMode="email"
            autoComplete="email"
            maxLength={254}
            placeholder="이메일 주소"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            required
          />
        </label>
        <button className="button button-primary" type="submit" disabled={submission.state === "submitting"}>
          <Bell size={19} weight="fill" />
          {submission.state === "submitting" ? "신청 중…" : "출시 알림 받기"}
        </button>
        <div className="waitlist-agreements">
          <label>
            <input type="checkbox" checked={privacyConsent} onChange={(event) => setPrivacyConsent(event.target.checked)} />
            <span><b>[필수]</b> 출시 알림을 위한 이메일 수집·이용에 동의합니다.</span>
          </label>
          <p>수집 항목: 이메일 · 이용 목적: Mapmory 출시 알림 · 보유 기간: 출시 알림 발송 후 지체 없이 파기</p>
          <label>
            <input type="checkbox" checked={ageConfirmed} onChange={(event) => setAgeConfirmed(event.target.checked)} />
            <span><b>[필수]</b> 만 14세 이상입니다.</span>
          </label>
        </div>
        {submission.message && (
          <p className={`waitlist-feedback is-${submission.state}`} role={submission.state === "error" ? "alert" : "status"}>
            {submission.message}
          </p>
        )}
      </form>
    </div>
  );
}

function InteractiveGlobe({ selected, focusRequest, onSelect, onInteract, theme, guideVisible, onGuideDismiss, isSelecting }) {
  const globeRef = useRef(null);
  const gestureStartRef = useRef(null);
  const containerRef = useRef(null);
  const [size, setSize] = useState({ width: 540, height: 540 });
  const [hoveredId, setHoveredId] = useState(null);
  const [globeMaterial, setGlobeMaterial] = useState(null);
  const [hasGlobeMounted, setHasGlobeMounted] = useState(false);
  const countries = useWorldCountries(hasGlobeMounted);
  const [isGlobeReady, setIsGlobeReady] = useState(false);
  const [isGlobeInView, setIsGlobeInView] = useState(false);
  const hasFocusedRef = useRef(false);
  const lastFocusRequestRef = useRef(0);

  useEffect(() => {
    let active = true;
    let material;
    import("three").then(({ MeshPhongMaterial }) => {
      material = new MeshPhongMaterial({
        color: theme === "dark" ? "#0b111c" : "#f4f6f2",
        emissive: theme === "dark" ? "#07121b" : "#e8eee9",
        shininess: theme === "dark" ? 12 : 7,
      });
      if (active) setGlobeMaterial(material);
      else material.dispose();
    });
    return () => { active = false; material?.dispose(); };
  }, [theme]);

  useEffect(() => {
    if (!containerRef.current) return undefined;
    const observer = new ResizeObserver(([entry]) => {
      const availableHeight = entry.contentRect.height || entry.contentRect.width;
      const next = Math.max(260, Math.min(500, Math.floor(Math.min(entry.contentRect.width, availableHeight))));
      setSize({ width: next, height: next });
    });
    observer.observe(containerRef.current);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    const element = containerRef.current;
    if (!element || !("IntersectionObserver" in window)) {
      setIsGlobeInView(true);
      setHasGlobeMounted(true);
      return undefined;
    }
    const observer = new IntersectionObserver(
      ([entry]) => {
        setIsGlobeInView(entry.isIntersecting);
        if (entry.isIntersecting) setHasGlobeMounted(true);
      },
      { rootMargin: "120px 0px" },
    );
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    if (!isGlobeReady || !globeRef.current) return;
    if (isGlobeInView) globeRef.current.resumeAnimation();
    else globeRef.current.pauseAnimation();
  }, [isGlobeInView, isGlobeReady]);

  useEffect(() => {
    if (!isGlobeReady || !globeRef.current) return;
    if (!hasFocusedRef.current) {
      const initialViewpoint = selected.viewpoint ?? { lat: selected.lat, lng: selected.lng, altitude: 2.05 };
      globeRef.current.pointOfView(initialViewpoint, 0);
      hasFocusedRef.current = true;
      return;
    }
    if (!focusRequest || focusRequest.id === lastFocusRequestRef.current) return;
    lastFocusRequestRef.current = focusRequest.id;
    if (focusRequest.selectionSource !== "shortcut") return;

    const currentViewpoint = globeRef.current.pointOfView();
    const requestedViewpoint = selected.viewpoint ?? { lat: selected.lat, lng: selected.lng, altitude: 2.05 };
    const duration = window.matchMedia("(prefers-reduced-motion: reduce)").matches ? 0 : WORLD_SELECTION_MOTION_MS;
    globeRef.current.pointOfView({
      ...requestedViewpoint,
      altitude: currentViewpoint?.altitude ?? requestedViewpoint.altitude,
    }, duration);
  }, [focusRequest, isGlobeReady, selected]);

  useEffect(() => {
    if (!isGlobeReady) return undefined;
    const controls = globeRef.current?.controls();
    if (!controls) return undefined;
    controls.enablePan = false;
    controls.minDistance = 210;
    controls.maxDistance = 410;
    controls.autoRotate = true;
    controls.autoRotateSpeed = 0.25;
    const stopAutoRotate = () => { controls.autoRotate = false; };
    const element = containerRef.current;
    element?.addEventListener("pointerdown", stopAutoRotate, { once: true });
    return () => element?.removeEventListener("pointerdown", stopAutoRotate);
  }, [isGlobeReady, size, globeMaterial]);

  const isVisited = (polygon) => memoryByCountry.has(String(polygon.id));
  const globePalette = getGlobePalette(theme);

  return (
    <div
      className={`globe-shell ${isSelecting ? "is-selecting" : ""}`}
      ref={containerRef}
      role="region"
      aria-label="회전 가능한 Mapmory 세계 지구본"
      aria-busy={isSelecting}
      onPointerDown={(event) => {
        if (!isGlobeReady || event.target.tagName !== "CANVAS") return;
        gestureStartRef.current = { pointerId: event.pointerId, pointerType: event.pointerType, clientX: event.clientX, clientY: event.clientY };
      }}
      onPointerMove={(event) => {
        const gesture = classifyGlobeGesture(gestureStartRef.current, event);
        if (gesture === "pending") return;
        gestureStartRef.current = null;
        if (gesture === "globe_drag") {
          onInteract(gesture);
          onGuideDismiss();
        }
      }}
      onPointerUp={() => { gestureStartRef.current = null; }}
      onPointerCancel={() => { gestureStartRef.current = null; }}
      onPointerLeave={() => { gestureStartRef.current = null; }}
      onWheel={(event) => {
        if (isGlobeReady && event.target.tagName === "CANVAS" && event.deltaY !== 0) {
          onInteract("globe_zoom");
          onGuideDismiss();
        }
      }}
    >
      <Suspense fallback={<div className="globe-loading"><GlobeHemisphereEast size={28} weight="duotone" /><span>지구본을 준비하고 있어요</span></div>}>
        {hasGlobeMounted && globeMaterial && countries.length > 0 && <Globe ref={globeRef} width={size.width} height={size.height} backgroundColor="rgba(0,0,0,0)" globeMaterial={globeMaterial} rendererConfig={GLOBE_RENDERER_CONFIG} showAtmosphere atmosphereColor={globePalette.atmosphere} atmosphereAltitude={0.12} polygonsData={countries}
          onGlobeReady={() => { setIsGlobeReady(true); applyGlobeRenderQuality(globeRef.current); }}
          polygonCapColor={(polygon) => { const id = String(polygon.id); if (id === selected.id) return "#f6c66f"; if (id === hoveredId && isVisited(polygon)) return globePalette.visitedHover; return isVisited(polygon) ? globePalette.visited : globePalette.unvisited; }}
          polygonSideColor={(polygon) => (String(polygon.id) === selected.id ? "#b87924" : isVisited(polygon) ? globePalette.visitedSide : globePalette.unvisitedSide)}
          polygonStrokeColor={(polygon) => (String(polygon.id) === selected.id ? "#fff1c7" : isVisited(polygon) ? globePalette.visitedStroke : globePalette.unvisitedStroke)}
          polygonAltitude={(polygon) => (String(polygon.id) === selected.id ? 0.04 : isVisited(polygon) ? 0.012 : 0.003)}
          polygonsTransitionDuration={WORLD_SELECTION_MOTION_MS}
          onPolygonHover={(polygon) => { const visited = polygon && isVisited(polygon); setHoveredId(visited ? String(polygon.id) : null); if (containerRef.current) containerRef.current.style.cursor = visited ? "pointer" : "grab"; }}
          onPolygonClick={(polygon) => { const memory = memoryByCountry.get(String(polygon.id)); if (memory) onSelect(memory, "globe"); }} />}
      </Suspense>
      {guideVisible && <GlobeOnboarding />}
      <p className="globe-instruction" aria-live="polite"><NavigationArrow size={18} weight="fill" />{
        isSelecting
          ? "선택한 나라로 이동하는 중이에요"
          : guideVisible
            ? "지구본을 좌우로 움직여보세요"
            : "민트색 나라를 눌러 사진을 열어보세요"
      }</p>
    </div>
  );
}

function LocationSelector({ selected, onSelect, disabled }) {
  return (
    <div className="globe-country-dock" onPointerDown={(event) => event.stopPropagation()}>
      <strong className="selector-copy">기억이 있는 나라</strong>
      <div className="location-shortcuts" role="group" aria-label="기억이 있는 나라 바로 선택">
        {memories.map((memory) => <button type="button" key={memory.id} disabled={disabled} className={selected.id === memory.id ? "is-active" : ""} aria-pressed={selected.id === memory.id} onClick={() => onSelect(memory, "shortcut")}><MapPin size={16} weight={selected.id === memory.id ? "fill" : "regular"} />{memory.country}</button>)}
      </div>
    </div>
  );
}

function PhotoCredit({ label, url }) {
  if (url) return <a className="photo-credit" href={url} target="_blank" rel="noreferrer">Photo: {label}</a>;
  return <span className="photo-credit photo-credit-owned">Photo: {label}</span>;
}

function MemoryCard({ memory, onClose, onPhotoChange, openSequence = 0, priority = false, isModal = false }) {
  const photos = memory.photos ?? [{
    src: memory.image,
    caption: memory.location,
    alt: `${memory.location}에서 남긴 실제 여행 장면`,
  }];
  const [photoIndex, setPhotoIndex] = useState(0);
  const swipeStartRef = useRef(null);
  const activePhoto = photos[photoIndex];
  const hasGallery = photos.length > 1;

  useEffect(() => {
    setPhotoIndex(0);
    swipeStartRef.current = null;
  }, [memory.key, openSequence]);

  const movePhoto = (offset, source = "button") => {
    setPhotoIndex((current) => {
      const next = Math.min(photos.length - 1, Math.max(0, current + offset));
      if (next !== current) onPhotoChange?.({ photoIndex: next, photoCount: photos.length, source });
      return next;
    });
  };

  const handleSwipeStart = (event) => {
    if (event.pointerType === "mouse") return;
    swipeStartRef.current = {
      pointerId: event.pointerId,
      x: event.clientX,
      y: event.clientY,
    };
  };

  const handleSwipeEnd = (event) => {
    const start = swipeStartRef.current;
    swipeStartRef.current = null;
    if (!start || start.pointerId !== event.pointerId) return;
    const deltaX = start.x - event.clientX;
    const deltaY = start.y - event.clientY;
    if (Math.abs(deltaX) < 36 || Math.abs(deltaX) <= Math.abs(deltaY)) return;
    movePhoto(deltaX > 0 ? 1 : -1, "swipe");
  };

  return (
    <article
      className="memory-card world-memory-card"
      aria-live="polite"
      aria-modal={isModal ? "true" : undefined}
      aria-label={isModal ? `${memory.location} 기억 사진` : undefined}
      role={isModal ? "dialog" : undefined}
    >
      <header>
        <MapPin size={18} weight="fill" />
        <span className="memory-location"><span className="memory-location-full">{memory.location}</span><span className="memory-location-compact">{memory.location.replace(" · ", " ").replace(" 여행", "")}</span></span>
        <small>{memory.country}</small>
        {onClose && <button type="button" className="world-memory-close" onClick={() => onClose("button")} aria-label="기억 닫기"><CaretDown size={16} weight="bold" aria-hidden="true" /><span>닫기</span></button>}
      </header>
      <div
        className={`memory-image-wrap ${hasGallery ? "is-gallery" : ""}`}
        onPointerDown={handleSwipeStart}
        onPointerUp={handleSwipeEnd}
        onPointerCancel={() => { swipeStartRef.current = null; }}
      >
        <img key={activePhoto.src} src={activePhoto.src} alt={activePhoto.alt} loading={priority ? "eager" : "lazy"} fetchPriority={priority ? "high" : "auto"} decoding={priority ? "auto" : "async"} />
        {hasGallery && (
          <>
            <span className="memory-photo-count" aria-hidden="true">{photoIndex + 1} / {photos.length}</span>
            <button type="button" className="memory-gallery-arrow is-prev" disabled={photoIndex === 0} onClick={() => movePhoto(-1)} aria-label={`이전 ${memory.country} 여행 사진`}><ArrowLeft size={18} weight="bold" /></button>
            <button type="button" className="memory-gallery-arrow is-next" disabled={photoIndex === photos.length - 1} onClick={() => movePhoto(1)} aria-label={`다음 ${memory.country} 여행 사진`}><ArrowRight size={18} weight="bold" /></button>
            <div className="memory-photo-meta">
              <span className="memory-photo-caption">{activePhoto.caption}</span>
              <div className="memory-photo-dots" role="group" aria-label={`${memory.country} 여행 사진 선택`}>
                {photos.map((photo, index) => (
                  <button
                    key={photo.src}
                    type="button"
                    className={index === photoIndex ? "is-active" : ""}
                    onClick={() => {
                      if (index === photoIndex) return;
                      setPhotoIndex(index);
                      onPhotoChange?.({ photoIndex: index, photoCount: photos.length, source: "dot" });
                    }}
                    aria-label={`${index + 1}번째 사진: ${photo.caption}`}
                    aria-pressed={index === photoIndex}
                  />
                ))}
              </div>
            </div>
          </>
        )}
      </div>
      {hasGallery && (
        <div className="memory-sheet-gallery-controls">
          <button type="button" disabled={photoIndex === 0} onClick={() => movePhoto(-1)} aria-label={`이전 ${memory.country} 여행 사진`}><ArrowLeft size={18} weight="bold" /></button>
          <div>
            <p className="memory-mobile-photo-caption">{activePhoto.caption}</p>
            <span className="memory-sheet-photo-count">{photoIndex + 1} / {photos.length}</span>
            <div className="memory-sheet-photo-dots" aria-hidden="true">
              {photos.map((photo, index) => <span key={photo.src} className={index === photoIndex ? "is-active" : ""} />)}
            </div>
          </div>
          <button type="button" disabled={photoIndex === photos.length - 1} onClick={() => movePhoto(1)} aria-label={`다음 ${memory.country} 여행 사진`}><ArrowRight size={18} weight="bold" /></button>
        </div>
      )}
      {!hasGallery && <div className="memory-single-title"><h2>{memory.title}</h2></div>}
      <div className="memory-card-body">
        <span className="memory-kind">실제 사진으로 열린 기억</span>
        <h2>{memory.title}</h2>
        <p>{memory.shortDescription}</p>
        <PhotoCredit label={memory.photoCredit} url={memory.photoCreditUrl} />
      </div>
    </article>
  );
}

function GlobeOnboarding() {
  return (
    <div
      className="globe-onboarding-overlay"
      role="status"
      aria-label="지구본을 좌우로 움직여보세요. 첫 움직임부터 바로 반응합니다."
    >
      <span className="globe-onboarding-card">
        <span className="globe-onboarding-gesture" aria-hidden="true"><HandSwipeLeft size={46} weight="duotone" /></span>
        <strong>지구본을 좌우로 움직여보세요</strong>
        <span className="globe-onboarding-copy">첫 움직임부터 바로 반응해요</span>
      </span>
    </div>
  );
}

function useMediaQuery(query) {
  const [matches, setMatches] = useState(() => (
    typeof window !== "undefined" && window.matchMedia(query).matches
  ));

  useEffect(() => {
    const media = window.matchMedia(query);
    const updateMatch = () => setMatches(media.matches);
    updateMatch();
    media.addEventListener("change", updateMatch);
    return () => media.removeEventListener("change", updateMatch);
  }, [query]);

  return matches;
}

function App() {
  const [theme, setTheme] = useState(() => localStorage.getItem("mapmory-theme") || "light");
  const [selectedMemory, setSelectedMemory] = useState(memories[0]);
  const [displayedMemory, setDisplayedMemory] = useState(memories[0]);
  const [globeFocusRequest, setGlobeFocusRequest] = useState({ id: 0, selectionSource: "initial" });
  const [isGlobeGuideVisible, setIsGlobeGuideVisible] = useState(false);
  const [isGlobeFocused, setIsGlobeFocused] = useState(false);
  const [isWorldMemoryOpen, setIsWorldMemoryOpen] = useState(false);
  const [isWorldSelecting, setIsWorldSelecting] = useState(false);
  const [worldMemoryOpenSequence, setWorldMemoryOpenSequence] = useState(0);
  const isMobileExperience = useMediaQuery("(max-width: 900px)");
  const experienceRef = useRef(null);
  const experienceStageRef = useRef(null);
  const globePanelRef = useRef(null);
  const worldSelectionTimerRef = useRef(null);
  const pendingWorldMemorySourceRef = useRef(null);
  const isWorldMemoryOpenRef = useRef(false);
  const sheetSessionRef = useRef(null);
  const globeAnalytics = useExperienceAnalytics("globe");

  const beginSheetSession = useCallback((memory) => {
    sheetSessionRef.current = {
      memoryId: memory.key,
      startedAt: currentTimeMs(),
      maxPhotoIndex: 1,
      viewedPhotos: new Set([0]),
      hasTrackedSwipe: false,
    };
  }, []);

  const handleMemoryPhotoChange = useCallback(({ photoIndex, photoCount, source }) => {
    const session = sheetSessionRef.current;
    if (!session) return;
    session.maxPhotoIndex = Math.max(session.maxPhotoIndex, photoIndex + 1);
    session.viewedPhotos.add(photoIndex);
    if (source !== "swipe" || session.hasTrackedSwipe) return;
    session.hasTrackedSwipe = true;
    trackEvent(ANALYTICS_EVENTS.MEMORY_PHOTO_SWIPED, {
      experience_type: "globe",
      memory_id: session.memoryId,
      photo_index: photoIndex + 1,
      photo_count: photoCount,
      time_since_memory_open_seconds: elapsedSeconds(session.startedAt),
    });
  }, []);

  const finishSheetSession = useCallback((closeMethod) => {
    const session = sheetSessionRef.current;
    if (!session) return;
    trackEvent(ANALYTICS_EVENTS.MEMORY_SHEET_CLOSED, {
      experience_type: "globe",
      memory_id: session.memoryId,
      close_method: closeMethod,
      max_photo_index: session.maxPhotoIndex,
      photos_viewed: session.viewedPhotos.size,
      time_since_memory_open_seconds: elapsedSeconds(session.startedAt),
    });
    sheetSessionRef.current = null;
  }, []);

  const dismissWorldMemory = useCallback((closeMethod = "button") => {
    if (!isWorldMemoryOpenRef.current) return;
    window.clearTimeout(worldSelectionTimerRef.current);
    finishSheetSession(closeMethod);
    isWorldMemoryOpenRef.current = false;
    setIsWorldSelecting(false);
    setIsWorldMemoryOpen(false);
  }, [finishSheetSession]);

  const closeWorldMemory = useCallback((closeMethod = "button", { consumeHistory = true } = {}) => {
    if (!isWorldMemoryOpenRef.current) return;
    const shouldConsumeHistory = consumeHistory && isWorldMemoryHistoryEntry(window.history.state);
    dismissWorldMemory(closeMethod);
    if (shouldConsumeHistory) window.history.back();
  }, [dismissWorldMemory]);

  const handleWorldSelect = (memory, selectionSource) => {
    globeAnalytics.startExperience("place_select");
    dismissGlobeGuide();
    clearTimeout(worldSelectionTimerRef.current);
    if (selectionSource === "shortcut") {
      setGlobeFocusRequest((current) => ({ id: current.id + 1, selectionSource }));
    }
    const isNewSelection = selectedMemory.id !== memory.id;
    setIsWorldSelecting(true);
    if (selectedMemory.id !== memory.id) setSelectedMemory(memory);
    const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    const selectionDelay = reduceMotion
      ? 80
      : selectionSource === "shortcut"
        ? WORLD_SELECTION_MOTION_MS + 120
        : 260;
    worldSelectionTimerRef.current = setTimeout(() => {
      setDisplayedMemory(memory);
      setWorldMemoryOpenSequence((current) => current + 1);
      beginSheetSession(memory);
      if (window.matchMedia("(max-width: 900px)").matches && !isWorldMemoryHistoryEntry(window.history.state)) {
        window.history.pushState(
          createWorldMemoryHistoryState(window.history.state, memory.key),
          "",
          window.location.href,
        );
      }
      isWorldMemoryOpenRef.current = true;
      setIsWorldMemoryOpen(true);
      setIsWorldSelecting(false);
      pendingWorldMemorySourceRef.current = selectionSource;
    }, isNewSelection || selectionSource === "shortcut" ? selectionDelay : 220);
  };

  useEffect(() => () => clearTimeout(worldSelectionTimerRef.current), []);

  useEffect(() => {
    const handleHistoryBack = () => {
      if (!isWorldMemoryOpenRef.current) return;
      closeWorldMemory("browser_back", { consumeHistory: false });
    };
    window.addEventListener("popstate", handleHistoryBack);
    return () => window.removeEventListener("popstate", handleHistoryBack);
  }, [closeWorldMemory]);

  useEffect(() => {
    if (!isMobileExperience || !isWorldMemoryOpen) return undefined;
    const root = document.getElementById("root");
    const previousBodyOverflow = document.body.style.overflow;
    const previousRootAriaHidden = root?.getAttribute("aria-hidden");
    const rootWasInert = root?.hasAttribute("inert") ?? false;
    document.body.style.overflow = "hidden";
    root?.setAttribute("inert", "");
    root?.setAttribute("aria-hidden", "true");
    const focusTimer = window.setTimeout(() => {
      document.querySelector(".world-memory-modal .world-memory-close")?.focus({ preventScroll: true });
    }, 0);
    return () => {
      window.clearTimeout(focusTimer);
      document.body.style.overflow = previousBodyOverflow;
      if (!rootWasInert) root?.removeAttribute("inert");
      if (previousRootAriaHidden === null) root?.removeAttribute("aria-hidden");
      else root?.setAttribute("aria-hidden", previousRootAriaHidden);
    };
  }, [isMobileExperience, isWorldMemoryOpen]);

  useEffect(() => { document.documentElement.dataset.theme = theme; localStorage.setItem("mapmory-theme", theme); }, [theme]);

  useEffect(() => {
    if (!globePanelRef.current) return undefined;

    const observer = new IntersectionObserver(([entry]) => {
      if (!entry.isIntersecting || entry.intersectionRatio < 0.25) return;
      setIsGlobeGuideVisible(true);
      observer.disconnect();
    }, { threshold: [0.25] });
    observer.observe(globePanelRef.current);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    if (!experienceStageRef.current) return undefined;
    const observer = new IntersectionObserver(([entry]) => {
      setIsGlobeFocused(entry.isIntersecting && entry.intersectionRatio >= 0.35);
    }, { threshold: [0, 0.35, 0.7] });
    observer.observe(experienceStageRef.current);
    return () => observer.disconnect();
  }, []);

  const dismissGlobeGuide = () => {
    setIsGlobeGuideVisible(false);
  };

  useEffect(() => {
    if (!isWorldMemoryOpen || !pendingWorldMemorySourceRef.current) return;
    globeAnalytics.trackMemoryOpen(displayedMemory.key, pendingWorldMemorySourceRef.current);
    pendingWorldMemorySourceRef.current = null;
  }, [displayedMemory, isWorldMemoryOpen, globeAnalytics.trackMemoryOpen]);

  const setExperienceSectionRef = (node) => {
    experienceRef.current = node;
    globeAnalytics.sectionRef.current = node;
  };

  return (
    <main id="top">
      <header className="site-header">
        <Brand />
        <nav aria-label="주요 메뉴">
          <a href="#how" onClick={() => trackEvent(ANALYTICS_EVENTS.EXPERIENCE_CTA_CLICK, { experience_type: "how_play", cta_placement: "header_nav" })}>사용 방법</a>
          <a href="#experience" onClick={() => globeAnalytics.trackEntryClick("header_nav")}>지구본 체험</a>
          <a href="#privacy">안심하고 쓰기</a>
        </nav>
        <div className="header-actions"><ThemeToggle theme={theme} onChange={setTheme} /><HeaderStoreMenu /></div>
      </header>

      <PhotoFinderHero
        storeActions={(
          <div className="store-buttons" role="group" aria-label="Mapmory 앱 다운로드">
            <StoreButton placement="hero" platform="ios" label="App Store" />
            <StoreButton placement="hero" platform="android" label="Google Play" />
          </div>
        )}
      />

      <section className="how-section" id="how" aria-labelledby="how-title">
        <div className="section-heading">
          <h2 id="how-title">3단계면 끝나요.</h2>
        </div>
        <HowItWorksPlay />
        <a className="how-experience-link" href="#experience" onClick={() => globeAnalytics.trackEntryClick("how_section")}><GlobeHemisphereEast size={18} weight="duotone" />기록이 쌓인 지도 미리 보기</a>
      </section>

      <section className={`experience-section ${isGlobeFocused ? "is-focused" : ""}`} id="experience" ref={setExperienceSectionRef}>
        <div className="experience-pin">
          <div className="section-heading section-heading-flow">
            <div><h2>지구본에서 기억을 꺼내봐요.</h2></div>
            <p>지구본을 움직이고 민트색 나라를 눌러보세요. 지도는 그대로, 그곳의 사진만 열려요.</p>
          </div>
          <div className={`experience-stage ${isWorldMemoryOpen ? "is-memory-open" : ""}`} ref={experienceStageRef}>
            <article className="globe-panel" id="globe-demo" ref={globePanelRef}>
              <header><span><GlobeHemisphereEast size={19} weight="duotone" />3D 기억 지도</span></header>
              <InteractiveGlobe selected={selectedMemory} focusRequest={globeFocusRequest} onSelect={handleWorldSelect} onInteract={globeAnalytics.startExperience} theme={theme} guideVisible={isGlobeGuideVisible} onGuideDismiss={dismissGlobeGuide} isSelecting={isWorldSelecting} />
              <LocationSelector selected={selectedMemory} onSelect={handleWorldSelect} disabled={isWorldSelecting} />
            </article>
            {!isMobileExperience && (
              <MemoryCard
                key={displayedMemory.id}
                memory={displayedMemory}
                onClose={closeWorldMemory}
                onPhotoChange={handleMemoryPhotoChange}
                openSequence={worldMemoryOpenSequence}
                priority
              />
            )}
          </div>
        </div>
      </section>

      {isMobileExperience && isWorldMemoryOpen && createPortal(
        <div className="world-memory-modal">
          <div className="world-memory-backdrop" aria-hidden="true" />
          <MemoryCard
            key={`${displayedMemory.id}-${worldMemoryOpenSequence}`}
            memory={displayedMemory}
            onClose={closeWorldMemory}
            onPhotoChange={handleMemoryPhotoChange}
            openSequence={worldMemoryOpenSequence}
            priority
            isModal
          />
        </div>,
        document.body,
      )}

      <section className="trust-section" id="privacy" aria-labelledby="privacy-title">
        <div className="section-heading">
          <h2 id="privacy-title">사진첩은 폰 안에서만 살펴봐요.</h2>
        </div>
        <div className="trust-flow">
          <div className="trust-zone is-device">
            <span className="trust-zone-label"><TrustOnDeviceIcon size={18} weight="duotone" />내 폰 안</span>
            <h3>{TRUST_ON_DEVICE.title}</h3>
            <p>{TRUST_ON_DEVICE.body}</p>
          </div>
          <span className="trust-arrow" aria-hidden="true"><ArrowRight size={20} weight="bold" /></span>
          <div className="trust-zone is-saved">
            <span className="trust-zone-label"><TrustOnSaveIcon size={18} weight="duotone" />저장할 때</span>
            <h3>{TRUST_ON_SAVE.title}</h3>
            <p>{TRUST_ON_SAVE.body}</p>
          </div>
        </div>
        <div className="faq-list">
          <h3 className="faq-title">자주 묻는 질문</h3>
          {FAQ_ITEMS.map(({ question, answer }) => (
            <details key={question}>
              <summary>{question}<CaretDown size={16} weight="bold" aria-hidden="true" /></summary>
              <p>{answer}</p>
            </details>
          ))}
        </div>
      </section>

      <section className="download-section" id="download">
        <h2>사진첩 속 여행,<br />지금 지도로 꺼내 보세요.</h2>
        <p>iPhone과 Android에서 바로 시작할 수 있어요.</p>
        <div className="download-actions" role="group" aria-label="Mapmory 앱 다운로드">
          <StoreButton placement="final" platform="ios" label="App Store" />
          <StoreButton placement="final" platform="android" label="Google Play" />
        </div>
        <p className="finder-trust-note download-trust"><ShieldCheck size={18} weight="fill" />사진은 폰 안에서 찾고, 고른 사진만 올라가요.</p>
      </section>

      <footer><div><Brand /><p>기억은 흩어져도, 지도는 남아요.</p></div><div className="footer-meta"><PhotoCredits /><p>© 2026 Mapmory. All rights reserved.</p></div></footer>
    </main>
  );
}

export { App };
