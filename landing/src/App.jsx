import { lazy, Suspense, useCallback, useEffect, useMemo, useRef, useState } from "react";
import koreaProvinces from "./data/korea-provinces.json";
import {
  AppleLogo,
  ArrowDown,
  ArrowLeft,
  ArrowRight,
  Bell,
  CheckCircle,
  DownloadSimple,
  EnvelopeSimple,
  GlobeHemisphereEast,
  HandSwipeLeft,
  MapPin,
  MapTrifold,
  Moon,
  NavigationArrow,
  Play,
  Plus,
  Sun,
  X,
} from "@phosphor-icons/react";
import { ANALYTICS_EVENTS, trackEvent } from "./analytics.js";
import { classifyGlobeGesture } from "./globe-gesture.js";
import { createCachedAsyncLoader } from "./cachedAsyncLoader.js";
import {
  HERO_MOBILE_ENTRY_APPLY_AT_MS,
  HERO_MOBILE_ENTRY_DURATION_MS,
  HERO_MEMORY_RELAY_STEPS,
  MEMORY_DENSITY_LEVELS,
  clampUnit,
  getHeroMobileEntryState,
  getHeroMemoryRelayState,
  getHeroGlobeRenderSize,
  getHeroMobileMapShift,
  getHeroRelayProgress,
} from "./heroMemoryRelay.js";
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
const WORLD_SELECTION_MOTION_MS = 1050;
const KOREA_FILL_MOTION_MS = 1500;
const GLOBE_RENDERER_CONFIG = Object.freeze({ antialias: true, alpha: true, powerPreference: "high-performance" });

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

const usaWestMemory = memories.find(({ key }) => key === "usa-west");
const HERO_JOURNEY_RECORD = Object.freeze({
  key: usaWestMemory.key,
  country: usaWestMemory.country,
  location: "미국 서부",
  dateLabel: "2025 · 미국 서부",
  title: "붉은 협곡에서 라스베이거스의 밤까지",
  quote: "빛이 들어오던 순간, 한참을 올려다봤어요.",
  recordLine: "흩어진 순간이, 여행 하나로.",
  mapLine: "기록이 쌓일수록, 지도는 나다워져요.",
  photoCount: usaWestMemory.photos.length,
  representative: usaWestMemory.photos[1],
  supporting: Object.freeze([usaWestMemory.photos[0], usaWestMemory.photos[3]]),
  photoCredit: usaWestMemory.photoCredit,
});

const koreaMemories = [
  {
    key: "hapjeong",
    provinceCode: "KR-11",
    districtCode: "11440",
    province: "서울특별시",
    provinceShort: "서울",
    location: "합정 · 희옥",
    category: "라멘",
    title: "기다림 끝에 만난 희옥의 시오 라멘",
    description: "감칠맛이 선명하고 산미를 아주 영리하게 살린 시오 라멘. 긴 웨이팅까지도 합정의 한 장면으로 남았어요.",
    image: "/assets/team-hapjeong-huiok.jpg",
    photoCredit: "Mapmory 개발팀 촬영",
    lat: 37.549,
    lng: 126.914,
  },
  {
    key: "yeosu",
    provinceCode: "KR-46",
    districtCode: "46130",
    province: "전라남도",
    provinceShort: "전남",
    location: "여수 · 딸기모찌",
    category: "디저트",
    title: "상자를 열자마자 웃음이 나던 딸기모찌",
    description: "여수 바닷길을 걷다 고른 모찌 한 상자. 함께 나눠 먹던 달콤함이 여행 전체를 다시 불러와요.",
    image: "/assets/team-yeosu-mochi.jpg",
    photoCredit: "Mapmory 개발팀 촬영",
    lat: 34.76,
    lng: 127.662,
  },
  {
    key: "jeju",
    provinceCode: "KR-49",
    districtCode: "50110",
    province: "제주특별자치도",
    provinceShort: "제주",
    location: "제주 · 바닷가",
    category: "여행",
    title: "검은 바위 사이로 밀려오던 제주 바다",
    description: "파도 소리와 해 질 무렵의 빛만으로도 그날의 제주가 선명하게 돌아와요.",
    image: "/assets/team-jeju-coast.jpg",
    photoCredit: "Mapmory 개발팀 촬영",
    lat: 33.4996,
    lng: 126.5312,
  },
];
const koreaAddMemories = [koreaMemories[2], koreaMemories[1], koreaMemories[0]];

const memoryByCountry = new Map(memories.map((memory) => [memory.id, memory]));
const memoryByKey = new Map(memories.map((memory) => [memory.key, memory]));
const koreaBounds = { minLng: 124.5, maxLng: 130.05, minLat: 33, maxLat: 38.75 };
const districtMapCache = new Map();
const loadWorldCountries = createCachedAsyncLoader(() => Promise.all([
  import("topojson-client"),
  import("world-atlas/countries-110m.json"),
]).then(([{ feature }, { default: topology }]) => feature(topology, topology.objects.countries).features));

function useWorldCountries(enabled = true) {
  const [countries, setCountries] = useState([]);

  useEffect(() => {
    if (!enabled) return undefined;
    let active = true;
    loadWorldCountries().then((features) => {
      if (active) setCountries(features);
    }).catch(() => {
      // Keep the fallback visible; a later mount can retry the cleared cache.
      if (active) setCountries([]);
    });
    return () => { active = false; };
  }, [enabled]);

  return countries;
}

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

function getHeroDensityPalette(theme, level) {
  const palettes = theme === "dark"
    ? {
        NONE: { cap: "#303b4d", side: "#1b2532", stroke: "#667589" },
        LOW: { cap: "#286f59", side: "#19503f", stroke: "#55b890" },
        MEDIUM: { cap: "#3fd09a", side: "#1f8f68", stroke: "#8ae8c2" },
        HIGH: { cap: "#72efbd", side: "#25b681", stroke: "#d0ffec" },
      }
    : {
        NONE: { cap: "#e7ebe6", side: "#c4cec7", stroke: "#aab8af" },
        LOW: { cap: "#bdeed7", side: "#83cbae", stroke: "#dff8ec" },
        MEDIUM: { cap: "#65d7a7", side: "#2cab7b", stroke: "#effff8" },
        HIGH: { cap: "#0a9d67", side: "#08794f", stroke: "#d8ffed" },
      };
  return palettes[level] ?? palettes.NONE;
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
    const viewpoint = selected.viewpoint ?? { lat: selected.lat, lng: selected.lng, altitude: 2.05 };
    globeRef.current.pointOfView(viewpoint, hasFocusedRef.current ? 850 : 0);
    hasFocusedRef.current = true;
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
        if (!isGlobeReady || guideVisible || event.target.tagName !== "CANVAS") return;
        gestureStartRef.current = { pointerId: event.pointerId, pointerType: event.pointerType, clientX: event.clientX, clientY: event.clientY };
      }}
      onPointerMove={(event) => {
        const gesture = classifyGlobeGesture(gestureStartRef.current, event);
        if (gesture === "pending") return;
        gestureStartRef.current = null;
        if (gesture === "globe_drag") onInteract(gesture);
      }}
      onPointerUp={() => { gestureStartRef.current = null; }}
      onPointerCancel={() => { gestureStartRef.current = null; }}
      onPointerLeave={() => { gestureStartRef.current = null; }}
      onWheel={(event) => {
        if (isGlobeReady && !guideVisible && event.target.tagName === "CANVAS" && event.deltaY !== 0) onInteract("globe_zoom");
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
      {guideVisible && <GlobeOnboarding onDismiss={onGuideDismiss} />}
      <p className="globe-instruction"><NavigationArrow size={18} weight="fill" />{isSelecting ? "선택한 나라가 올라오는 중 · 잠시만 기다려주세요" : "잡고 돌려보세요 · 민트색 나라를 누르면 기억이 열려요"}</p>
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

function MemoryCard({ memory, onClose, priority = false }) {
  const photos = memory.photos ?? [{
    src: memory.image,
    caption: memory.location,
    alt: `${memory.location}에서 남긴 실제 여행 장면`,
  }];
  const [photoIndex, setPhotoIndex] = useState(0);
  const activePhoto = photos[photoIndex];
  const hasGallery = photos.length > 1;

  const movePhoto = (offset) => {
    setPhotoIndex((current) => (current + offset + photos.length) % photos.length);
  };

  return (
    <article className="memory-card world-memory-card" aria-live="polite">
      <header>
        <MapPin size={18} weight="fill" />
        <span className="memory-location"><span className="memory-location-full">{memory.location}</span><span className="memory-location-compact">{memory.location.replace(" · ", " ").replace(" 여행", "")}</span></span>
        <small>{memory.country}</small>
        {onClose && <button type="button" className="world-memory-close" onClick={onClose} aria-label="기억 닫고 지구본으로 돌아가기"><X size={18} weight="bold" /><span>지구본으로</span></button>}
      </header>
      <div className={`memory-image-wrap ${hasGallery ? "is-gallery" : ""}`}>
        <img key={activePhoto.src} src={activePhoto.src} alt={activePhoto.alt} loading={priority ? "eager" : "lazy"} fetchPriority={priority ? "high" : "auto"} decoding={priority ? "auto" : "async"} />
        {hasGallery && (
          <>
            <span className="memory-photo-count" aria-hidden="true">{photoIndex + 1} / {photos.length}</span>
            <button type="button" className="memory-gallery-arrow is-prev" onClick={() => movePhoto(-1)} aria-label={`이전 ${memory.country} 여행 사진`}><ArrowLeft size={18} weight="bold" /></button>
            <button type="button" className="memory-gallery-arrow is-next" onClick={() => movePhoto(1)} aria-label={`다음 ${memory.country} 여행 사진`}><ArrowRight size={18} weight="bold" /></button>
            <div className="memory-photo-meta">
              <span className="memory-photo-caption">{activePhoto.caption}</span>
              <div className="memory-photo-dots" role="group" aria-label={`${memory.country} 여행 사진 선택`}>
                {photos.map((photo, index) => (
                  <button
                    key={photo.src}
                    type="button"
                    className={index === photoIndex ? "is-active" : ""}
                    onClick={() => setPhotoIndex(index)}
                    aria-label={`${index + 1}번째 사진: ${photo.caption}`}
                    aria-pressed={index === photoIndex}
                  />
                ))}
              </div>
            </div>
          </>
        )}
      </div>
      {hasGallery && <p className="memory-mobile-photo-caption">{activePhoto.caption}</p>}
      <div className="memory-card-body">
        <span className="memory-kind">실제 사진으로 열린 기억</span>
        <h2>{memory.title}</h2>
        <p>{memory.shortDescription}</p>
        <PhotoCredit label={memory.photoCredit} url={memory.photoCreditUrl} />
        <a className="memory-next" href="#korea-map-demo"><span>대한민국 상세지도 체험하기</span><ArrowRight size={18} weight="bold" /></a>
      </div>
    </article>
  );
}

function projectPoint(lng, lat, width, height) {
  const padding = Math.min(width, height) * 0.08;
  const longitudeScale = 0.81;
  const projectedWidth = (koreaBounds.maxLng - koreaBounds.minLng) * longitudeScale;
  const projectedHeight = koreaBounds.maxLat - koreaBounds.minLat;
  const scale = Math.min(
    (width - padding * 2) / projectedWidth,
    (height - padding * 2) / projectedHeight,
  );
  const mapWidth = projectedWidth * scale;
  const mapHeight = projectedHeight * scale;
  const offsetX = (width - mapWidth) / 2;
  const offsetY = (height - mapHeight) / 2;
  const x = offsetX + (lng - koreaBounds.minLng) * longitudeScale * scale;
  const y = offsetY + (koreaBounds.maxLat - lat) * scale;
  return [x, y];
}

function pointInProjectedRing(x, y, ring, width, height) {
  let inside = false;
  for (let index = 0, previous = ring.length - 1; index < ring.length; previous = index, index += 1) {
    const [currentX, currentY] = projectPoint(ring[index][0], ring[index][1], width, height);
    const [previousX, previousY] = projectPoint(ring[previous][0], ring[previous][1], width, height);
    const crosses = ((currentY > y) !== (previousY > y))
      && x < ((previousX - currentX) * (y - currentY)) / ((previousY - currentY) || Number.EPSILON) + currentX;
    if (crosses) inside = !inside;
  }
  return inside;
}

function pointInProvince(x, y, province, width, height) {
  return province.rings.reduce(
    (inside, ring) => (pointInProjectedRing(x, y, ring, width, height) ? !inside : inside),
    false,
  );
}

function KoreaMap({ memories: visibleMemories, selected, onSelect, theme, transitioningKey }) {
  const shellRef = useRef(null);
  const canvasRef = useRef(null);
  const [dimensions, setDimensions] = useState({ width: 620, height: 650 });
  const [hoveredCode, setHoveredCode] = useState(null);
  const [revealProgress, setRevealProgress] = useState(1);
  const visitedCodes = useMemo(
    () => new Set(visibleMemories.map((memory) => memory.provinceCode)),
    [visibleMemories],
  );
  const transitioningMemory = useMemo(
    () => visibleMemories.find((memory) => memory.key === transitioningKey) ?? null,
    [transitioningKey, visibleMemories],
  );

  useEffect(() => {
    if (!shellRef.current) return undefined;
    const updateDimensions = (measuredWidth) => {
      const width = Math.max(300, Math.floor(measuredWidth));
      const compactDesktop = window.innerWidth > 900 && window.innerHeight <= 800;
      const height = width < 500
        ? Math.round(width * 1.15)
        : compactDesktop
          ? Math.min(340, Math.round(width * 0.34))
          : Math.min(460, Math.round(width * 0.46));
      setDimensions((current) => (
        current.width === width && current.height === height ? current : { width, height }
      ));
    };
    const observer = new ResizeObserver(([entry]) => updateDimensions(entry.contentRect.width));
    const handleViewportResize = () => updateDimensions(shellRef.current?.getBoundingClientRect().width ?? 620);
    observer.observe(shellRef.current);
    window.addEventListener("resize", handleViewportResize);
    return () => {
      observer.disconnect();
      window.removeEventListener("resize", handleViewportResize);
    };
  }, []);

  useEffect(() => {
    if (!transitioningKey || window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
      setRevealProgress(1);
      return undefined;
    }
    let frame;
    const startedAt = performance.now();
    const update = (now) => {
      const progress = Math.max(0, Math.min(1, (now - startedAt) / KOREA_FILL_MOTION_MS));
      setRevealProgress(1 - ((1 - progress) ** 3));
      if (progress < 1) frame = requestAnimationFrame(update);
    };
    setRevealProgress(0);
    frame = requestAnimationFrame(update);
    return () => cancelAnimationFrame(frame);
  }, [transitioningKey]);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const { width, height } = dimensions;
    const ratio = Math.min(window.devicePixelRatio || 1, 2);
    canvas.width = width * ratio;
    canvas.height = height * ratio;
    canvas.style.width = `${width}px`;
    canvas.style.height = `${height}px`;
    const context = canvas.getContext("2d");
    context.setTransform(ratio, 0, 0, ratio, 0, 0);
    context.clearRect(0, 0, width, height);
    context.lineJoin = "round";

    koreaProvinces.forEach((province) => {
      context.beginPath();
      province.rings.forEach((ring) => {
        ring.forEach(([lng, lat], index) => {
          const [x, y] = projectPoint(lng, lat, width, height);
          if (index === 0) context.moveTo(x, y);
          else context.lineTo(x, y);
        });
        context.closePath();
      });
      const visited = visitedCodes.has(province.code);
      const active = selected?.provinceCode === province.code || hoveredCode === province.code;
      context.fillStyle = theme === "dark" ? "#25343c" : "#f2f0ea";
      context.strokeStyle = theme === "dark" ? "#53626a" : "#c2c9c4";
      context.lineWidth = 1;
      context.fill("evenodd");
      context.stroke();

      if (visited) {
        context.save();
        context.clip("evenodd");
        if (transitioningMemory?.provinceCode === province.code && revealProgress < 1) {
          const [centerX, centerY] = projectPoint(
            transitioningMemory.lng,
            transitioningMemory.lat,
            width,
            height,
          );
          context.beginPath();
          context.arc(centerX, centerY, Math.hypot(width, height) * revealProgress, 0, Math.PI * 2);
          context.clip();
        }
        context.fillStyle = active ? "#62d6ac" : "#79dcb8";
        context.fillRect(0, 0, width, height);
        context.restore();

        context.beginPath();
        province.rings.forEach((ring) => {
          ring.forEach(([lng, lat], index) => {
            const [x, y] = projectPoint(lng, lat, width, height);
            if (index === 0) context.moveTo(x, y);
            else context.lineTo(x, y);
          });
          context.closePath();
        });
        context.strokeStyle = active ? "#116f4e" : "#278b67";
        context.lineWidth = active ? 2.4 : 1.8;
        context.stroke();
      }
    });
  }, [dimensions, hoveredCode, revealProgress, selected, theme, transitioningMemory, visitedCodes]);

  const findMemoryAtPointer = (event) => {
    const bounds = canvasRef.current?.getBoundingClientRect();
    if (!bounds) return null;
    const x = (event.clientX - bounds.left) * (dimensions.width / bounds.width);
    const y = (event.clientY - bounds.top) * (dimensions.height / bounds.height);
    const province = koreaProvinces.find(
      (candidate) => visitedCodes.has(candidate.code)
        && pointInProvince(x, y, candidate, dimensions.width, dimensions.height),
    );
    return province
      ? visibleMemories.find((memory) => memory.provinceCode === province.code) ?? null
      : null;
  };

  return (
    <div className="korea-map-shell" ref={shellRef}>
      <canvas
        ref={canvasRef}
        role="img"
        aria-label={`대한민국 17개 시도 상세 지도. 현재 ${visibleMemories.length}개 지역에 기억이 표시되어 있습니다.`}
        onClick={(event) => {
          const memory = findMemoryAtPointer(event);
          if (memory) onSelect(memory, "map");
        }}
        onPointerMove={(event) => {
          const memory = findMemoryAtPointer(event);
          setHoveredCode(memory?.provinceCode ?? null);
          event.currentTarget.style.cursor = memory ? "pointer" : "default";
        }}
        onPointerLeave={() => setHoveredCode(null)}
      />
    </div>
  );
}

function calculateDistrictBounds(districts) {
  const bounds = { minLng: Infinity, maxLng: -Infinity, minLat: Infinity, maxLat: -Infinity };
  for (const district of districts) {
    for (const ring of district.rings) {
      for (const [lng, lat] of ring) {
        bounds.minLng = Math.min(bounds.minLng, lng);
        bounds.maxLng = Math.max(bounds.maxLng, lng);
        bounds.minLat = Math.min(bounds.minLat, lat);
        bounds.maxLat = Math.max(bounds.maxLat, lat);
      }
    }
  }
  return bounds;
}

function createDistrictProjection(width, height, bounds) {
  const padding = Math.min(width, height) * 0.08;
  const availableWidth = width - padding * 2;
  const availableHeight = height - padding * 2;
  const scale = Math.min(
    availableWidth / Math.max(bounds.maxLng - bounds.minLng, 0.001),
    availableHeight / Math.max(bounds.maxLat - bounds.minLat, 0.001),
  );
  const mapWidth = (bounds.maxLng - bounds.minLng) * scale;
  const mapHeight = (bounds.maxLat - bounds.minLat) * scale;
  const offsetX = (width - mapWidth) / 2;
  const offsetY = (height - mapHeight) / 2;
  return { scale, offsetX, offsetY, minLng: bounds.minLng, maxLat: bounds.maxLat };
}

function districtLabelPoint(district) {
  let largestRing = district.rings[0] ?? [];
  for (const ring of district.rings) {
    if (ring.length > largestRing.length) largestRing = ring;
  }
  const bounds = { minLng: Infinity, maxLng: -Infinity, minLat: Infinity, maxLat: -Infinity };
  for (const [lng, lat] of largestRing) {
    bounds.minLng = Math.min(bounds.minLng, lng);
    bounds.maxLng = Math.max(bounds.maxLng, lng);
    bounds.minLat = Math.min(bounds.minLat, lat);
    bounds.maxLat = Math.max(bounds.maxLat, lat);
  }
  return [(bounds.minLng + bounds.maxLng) / 2, (bounds.minLat + bounds.maxLat) / 2];
}

async function loadDistrictMap(provinceCode) {
  if (districtMapCache.has(provinceCode)) return districtMapCache.get(provinceCode);

  const suffix = provinceCode.replace("KR-", "");
  const request = fetch(`/assets/maps/korea-districts-${suffix}.json`)
    .then((response) => {
      if (!response.ok) throw new Error(`district map ${response.status}`);
      return response.json();
    })
    .then((data) => {
      if (!Array.isArray(data?.districts) || data.districts.length === 0) {
        throw new Error("invalid district map data");
      }
      return data.districts;
    })
    .catch((error) => {
      districtMapCache.delete(provinceCode);
      throw error;
    });

  districtMapCache.set(provinceCode, request);
  return request;
}

function DistrictMap({ memory, theme }) {
  const shellRef = useRef(null);
  const canvasRef = useRef(null);
  const [dimensions, setDimensions] = useState({ width: 620, height: 540 });
  const [mapState, setMapState] = useState({ status: "loading", districts: [] });

  useEffect(() => {
    let active = true;
    setMapState({ status: "loading", districts: [] });
    loadDistrictMap(memory.provinceCode)
      .then((districts) => {
        if (active) setMapState({ status: "ready", districts });
      })
      .catch((error) => {
        if (active && error.name !== "AbortError") setMapState({ status: "error", districts: [] });
      });
    return () => { active = false; };
  }, [memory.provinceCode]);

  useEffect(() => {
    if (!shellRef.current) return undefined;
    const updateDimensions = (measuredWidth) => {
      const width = Math.max(300, Math.floor(measuredWidth));
      const compactDesktop = window.innerWidth > 900 && window.innerHeight <= 800;
      const height = compactDesktop ? 370 : Math.max(390, Math.round(width * 0.84));
      setDimensions((current) => (
        current.width === width && current.height === height ? current : { width, height }
      ));
    };
    const observer = new ResizeObserver(([entry]) => updateDimensions(entry.contentRect.width));
    const handleViewportResize = () => updateDimensions(shellRef.current?.getBoundingClientRect().width ?? 620);
    observer.observe(shellRef.current);
    window.addEventListener("resize", handleViewportResize);
    return () => {
      observer.disconnect();
      window.removeEventListener("resize", handleViewportResize);
    };
  }, []);

  const bounds = useMemo(
    () => (mapState.districts.length ? calculateDistrictBounds(mapState.districts) : null),
    [mapState.districts],
  );

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas || !bounds || mapState.status !== "ready") return;
    const { width, height } = dimensions;
    const ratio = Math.min(window.devicePixelRatio || 1, 2);
    canvas.width = width * ratio;
    canvas.height = height * ratio;
    canvas.style.width = `${width}px`;
    canvas.style.height = `${height}px`;
    const context = canvas.getContext("2d");
    context.setTransform(ratio, 0, 0, ratio, 0, 0);
    context.clearRect(0, 0, width, height);
    context.lineJoin = "round";
    const projection = createDistrictProjection(width, height, bounds);

    for (const district of mapState.districts) {
      const active = district.code === memory.districtCode;
      context.beginPath();
      for (const ring of district.rings) {
        for (let index = 0; index < ring.length; index += 1) {
          const [lng, lat] = ring[index];
          const x = projection.offsetX + (lng - projection.minLng) * projection.scale;
          const y = projection.offsetY + (projection.maxLat - lat) * projection.scale;
          if (index === 0) context.moveTo(x, y);
          else context.lineTo(x, y);
        }
        context.closePath();
      }
      context.fillStyle = active ? "#72e5b7" : theme === "dark" ? "#172334" : "#e1e9e4";
      context.strokeStyle = active ? (theme === "dark" ? "#a8f3d5" : "#21845f") : theme === "dark" ? "#45546a" : "#a8b7ae";
      context.lineWidth = active ? 2 : 1;
      context.fill("evenodd");
      context.stroke();
    }

    context.textAlign = "center";
    context.textBaseline = "middle";
    for (const district of mapState.districts) {
      const active = district.code === memory.districtCode;
      const [lng, lat] = districtLabelPoint(district);
      const x = projection.offsetX + (lng - projection.minLng) * projection.scale;
      const y = projection.offsetY + (projection.maxLat - lat) * projection.scale;
      const fontSize = width < 420 ? (active ? 10 : 8) : (active ? 12 : 10);
      context.font = `${active ? 700 : 400} ${fontSize}px "LINE Seed Sans KR", sans-serif`;
      context.fillStyle = active ? "#073521" : theme === "dark" ? "#95a5b8" : "#53645a";
      context.fillText(district.name, x, y);
    }
  }, [bounds, dimensions, mapState, memory.districtCode, theme]);

  return (
    <div className="district-map-shell" ref={shellRef}>
      <div className="district-map-caption"><span><b>3단계</b>{memory.provinceShort} 상세지역</span><small>민트색 = 기억이 있는 지역</small></div>
      {mapState.status === "loading" && <div className="district-map-status"><MapTrifold size={26} weight="duotone" />상세지도를 불러오고 있어요</div>}
      {mapState.status === "error" && <div className="district-map-status">상세지도를 불러오지 못했어요.</div>}
      <canvas ref={canvasRef} role="img" aria-label={`${memory.province} 시·군·구 상세 지도. ${memory.location}이 민트색으로 표시되어 있습니다.`} />
    </div>
  );
}

function KoreaDetailExperience({ theme }) {
  const [selected, setSelected] = useState(null);
  const [addedMemoryKeys, setAddedMemoryKeys] = useState(() => new Set());
  const [isAddPanelOpen, setIsAddPanelOpen] = useState(true);
  const [addFeedback, setAddFeedback] = useState("아래 사진을 추가해 지도가 채워지는 과정을 체험해보세요.");
  const [detailLevel, setDetailLevel] = useState(2);
  const [transitioningKey, setTransitioningKey] = useState(null);
  const detailDemoRef = useRef(null);
  const pendingMemorySourceRef = useRef(null);
  const transitionTimerRef = useRef(null);
  const analytics = useExperienceAnalytics("korea_detail");
  const addedMemories = useMemo(
    () => koreaMemories.filter((memory) => addedMemoryKeys.has(memory.key)),
    [addedMemoryKeys],
  );
  const activeAddedMemory = selected && addedMemoryKeys.has(selected.key)
    ? selected
    : addedMemories.at(-1) ?? null;

  useEffect(() => {
    void Promise.allSettled(
      koreaMemories.map((memory) => loadDistrictMap(memory.provinceCode)),
    );
    return () => clearTimeout(transitionTimerRef.current);
  }, []);

  useEffect(() => {
    if (detailLevel !== 3 || (window.innerWidth > 900 && window.innerHeight > 800)) return undefined;
    const frame = requestAnimationFrame(() => {
      detailDemoRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
    });
    return () => cancelAnimationFrame(frame);
  }, [detailLevel]);

  const openDetail = (memory, selectionSource) => {
    if (selected?.key !== memory.key || detailLevel !== 3) {
      pendingMemorySourceRef.current = selectionSource;
    }
    setSelected(memory);
    setIsAddPanelOpen(false);
    setTransitioningKey(null);
    setDetailLevel(3);
  };

  useEffect(() => {
    if (detailLevel !== 3 || !selected || !pendingMemorySourceRef.current) return;
    analytics.trackMemoryOpen(selected.key, pendingMemorySourceRef.current);
    pendingMemorySourceRef.current = null;
  }, [detailLevel, selected, analytics.trackMemoryOpen]);

  const handleSelect = (memory, selectionSource) => {
    if (!addedMemoryKeys.has(memory.key) || transitioningKey) return;
    clearTimeout(transitionTimerRef.current);
    openDetail(memory, selectionSource);
  };

  const handleAdd = (memory) => {
    if (addedMemoryKeys.has(memory.key)) {
      if (transitioningKey) return;
      handleSelect(memory, "photo_tray");
      return;
    }
    clearTimeout(transitionTimerRef.current);
    analytics.startExperience("memory_add");
    setAddedMemoryKeys((current) => new Set([...current, memory.key]));
    setSelected(memory);
    setIsAddPanelOpen(false);
    setTransitioningKey(memory.key);
    setAddFeedback(`${memory.province}가 지도에 채워지고 있어요. 색이 모두 채워질 때까지 잠시 봐주세요.`);
    transitionTimerRef.current = setTimeout(() => {
      analytics.trackMemoryAdd(memory.key);
      setTransitioningKey(null);
      setAddFeedback(`${memory.province}가 채워졌어요. 색칠된 지역이나 상세지역 보기 버튼을 눌러 기억을 열어보세요.`);
      const behavior = window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth";
      detailDemoRef.current?.scrollIntoView({ behavior, block: "start" });
    }, KOREA_FILL_MOTION_MS);
  };

  const handleBack = () => {
    clearTimeout(transitionTimerRef.current);
    setTransitioningKey(null);
    setIsAddPanelOpen(false);
    setDetailLevel(2);
  };

  return (
    <section className="detail-section" id="korea-detail" ref={analytics.sectionRef}>
      <div className="detail-heading">
        <div><p className="eyebrow">02 · 대한민국</p><h2>사진을 더하면<br /><em>지역의 기억이</em> 채워져요.</h2></div>
      </div>

      <div className={`detail-demo detail-level-${detailLevel}`} id="korea-map-demo" ref={detailDemoRef}>
        <header className="map-app-header">
          <h3>{detailLevel === 2 ? "나의 대한민국 지도" : `${selected?.provinceShort}의 기억`}</h3>
          <div className="map-progress" aria-label={`17개 시도 중 ${addedMemories.length}개 채움`}><strong>{addedMemories.length}</strong><span>/ 17</span></div>
        </header>
        {detailLevel === 2 ? (
          <div className="detail-level-content">
            <div className="detail-stage detail-stage-map">
              <div className={`map-interaction-panel ${transitioningKey ? "is-transitioning" : ""}`} aria-busy={Boolean(transitioningKey)}>
                <KoreaMap memories={addedMemories} selected={selected} onSelect={handleSelect} theme={theme} transitioningKey={transitioningKey} />
                {activeAddedMemory && !transitioningKey && !isAddPanelOpen ? (
                  <div className="region-reveal-tray" aria-live="polite">
                    <div className="region-reveal-copy">
                      <span><CheckCircle size={17} weight="fill" />{activeAddedMemory.provinceShort} · 기억 1개</span>
                      <strong>{activeAddedMemory.provinceShort}의 기억이 채워졌어요</strong>
                    </div>
                    <div className="region-reveal-actions">
                      {addedMemories.length < koreaAddMemories.length && (
                        <button className="region-reveal-add" type="button" onClick={() => setIsAddPanelOpen(true)}>
                          다른 지역 추가
                        </button>
                      )}
                      <button className="region-reveal-open" type="button" onClick={() => handleSelect(activeAddedMemory, "reveal_tray")}>
                        {activeAddedMemory.provinceShort}의 기억 보기<ArrowRight size={18} weight="bold" />
                      </button>
                    </div>
                  </div>
                ) : (
                  <div className="memory-add-panel">
                    <div className="memory-add-heading">
                      <span><b>2단계</b> 사진 한 장을 골라 지도를 채워보세요</span>
                      <p aria-live="polite">{addFeedback}</p>
                    </div>
                    <div className="memory-add-list" role="list" aria-label="지도에 추가할 예시 사진">
                      {koreaAddMemories.map((memory) => {
                        const isAdded = addedMemoryKeys.has(memory.key);
                        const isTransitioning = transitioningKey === memory.key;
                        return (
                          <article className={`memory-add-card ${isAdded ? "is-added" : ""}`} key={memory.key} role="listitem">
                            <img src={memory.image} alt={`${memory.location}의 실제 사진`} loading="lazy" decoding="async" />
                            <div><span>{memory.location}</span><small>{memory.photoCredit}</small></div>
                            <button type="button" disabled={Boolean(transitioningKey)} onClick={() => handleAdd(memory)} aria-label={isTransitioning ? `${memory.province} 지도 색칠 중` : isAdded ? `${memory.province} 기억 보기` : `${memory.province} 사진을 지도에 추가하기`}>
                              {isTransitioning ? <>색칠 중</> : isAdded ? <><CheckCircle size={15} weight="fill" />기억 보기</> : <><Plus size={15} weight="bold" />추가하기</>}
                            </button>
                          </article>
                        );
                      })}
                    </div>
                  </div>
                )}
              </div>
            </div>
          </div>
        ) : (
          <div className="region-detail-stage detail-level-content" aria-live="polite">
            <div className="region-detail-toolbar">
              <button type="button" onClick={handleBack}><ArrowLeft size={18} weight="bold" />대한민국 지도로 돌아가기</button>
            </div>
            <div className="district-detail-grid">
              <DistrictMap memory={selected} theme={theme} />
              <article className="region-memory-card is-detail">
                <div className="region-photo"><img key={selected.image} src={selected.image} alt={`${selected.location}의 실제 사진`} loading="lazy" decoding="async" /><span>{selected.category}</span></div>
                <div className="region-memory-body">
                  <p className="region-location"><NavigationArrow size={17} weight="fill" />{selected.location}<small>{selected.provinceShort}</small></p>
                  <h3>{selected.title}</h3>
                  <p>{selected.description}</p>
                  <PhotoCredit label={selected.photoCredit} url={selected.photoCreditUrl} />
                  <a
                    className="button button-primary region-cta"
                    href="#download"
                    onClick={() => trackEvent(
                      ANALYTICS_EVENTS.DOWNLOAD_CTA_CLICK,
                      { cta_placement: "korea_memory" },
                    )}
                  >
                    내 기억 지도도 만들기<ArrowRight size={18} weight="bold" />
                  </a>
                </div>
              </article>
            </div>
          </div>
        )}
      </div>
    </section>
  );
}

function GlobeOnboarding({ onDismiss }) {
  return (
    <button
      type="button"
      className="globe-onboarding-overlay"
      onPointerDown={(event) => event.stopPropagation()}
      onClick={onDismiss}
      aria-label="지구본을 돌려보세요. 민트색 나라를 누르면 기억이 열려요."
    >
      <span className="globe-onboarding-card">
        <span className="globe-onboarding-gesture" aria-hidden="true"><HandSwipeLeft size={46} weight="duotone" /></span>
        <strong>지구본을 돌려보세요</strong>
        <span className="globe-onboarding-copy">민트색 나라를 누르면 기억이 열려요 · 눌러서 시작</span>
      </span>
    </button>
  );
}

function HeroGlobe({ relayState, theme, onReady, waitForIdle = false, polygonsTransitionDuration = 720 }) {
  const globeRef = useRef(null);
  const containerRef = useRef(null);
  const [isDeferredLoadReady, setIsDeferredLoadReady] = useState(false);
  const shouldLoadGlobe = isDeferredLoadReady || (!waitForIdle && relayState.progress >= 0.18);
  const countries = useWorldCountries(shouldLoadGlobe);
  const [size, setSize] = useState({ width: 420, height: 420 });
  const [globeMaterial, setGlobeMaterial] = useState(null);
  const [isGlobeReady, setIsGlobeReady] = useState(false);
  const [isGlobeInView, setIsGlobeInView] = useState(true);
  const hasInitialFocusRef = useRef(false);
  const globePalette = getGlobePalette(theme);
  const densityByCountry = useMemo(() => new Map((relayState.phase === "intro" ? [relayState.introCard] : relayState.cards).map((card) => {
    const memory = memoryByKey.get(card.key);
    return [memory?.id, card.density.level];
  }).filter(([id]) => Boolean(id))), [relayState.cards, relayState.introCard, relayState.phase]);

  useEffect(() => {
    let timerId = 0;
    let idleId = 0;
    const enableGlobe = () => setIsDeferredLoadReady(true);

    if ("requestIdleCallback" in window) {
      idleId = window.requestIdleCallback(enableGlobe, { timeout: 900 });
    } else {
      timerId = window.setTimeout(enableGlobe, 180);
    }

    return () => {
      if (idleId) window.cancelIdleCallback(idleId);
      if (timerId) window.clearTimeout(timerId);
    };
  }, []);

  useEffect(() => {
    if (!shouldLoadGlobe) {
      setIsGlobeReady(false);
      return undefined;
    }
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
  }, [shouldLoadGlobe, theme]);

  useEffect(() => {
    const element = containerRef.current;
    if (!element) return undefined;
    const observer = new ResizeObserver(([entry]) => {
      const next = getHeroGlobeRenderSize(entry.contentRect.width);
      setSize({ width: next, height: next });
    });
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    const element = containerRef.current;
    if (!element || !("IntersectionObserver" in window)) return undefined;
    const observer = new IntersectionObserver(
      ([entry]) => setIsGlobeInView(entry.isIntersecting),
      { rootMargin: "100px 0px" },
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
    const activeCard = relayState.activeIndex >= 0 ? relayState.cards[relayState.activeIndex] : null;
    const completedCard = relayState.cards.find(({ isApplied }) => isApplied) ?? null;
    const targetCard = activeCard ?? completedCard;
    if (!targetCard && hasInitialFocusRef.current) return;
    const memory = targetCard ? memoryByKey.get(targetCard.key) : memories[0];
    if (!memory) return;
    const baseViewpoint = memory.viewpoint ?? { lat: memory.lat, lng: memory.lng, altitude: 2.05 };
    const viewpoint = { ...baseViewpoint, altitude: 1.72 };
    globeRef.current.pointOfView(viewpoint, targetCard ? 720 : 0);
    hasInitialFocusRef.current = true;
  }, [isGlobeReady, relayState.activeIndex, relayState.cards]);

  const getDensity = (polygon) => densityByCountry.get(String(polygon.id)) ?? "NONE";
  const globeAriaLabel = relayState.phase === "intro"
    ? "제주 여행 기록이 남아 있는 3D 기억 지도"
    : relayState.completedCount > 0
      ? "미국 서부 여행 기록이 더해져 같은 나라의 색이 한 단계 진해진 3D 기억 지도"
      : relayState.phase === "map"
        ? "완성된 미국 서부 여행 기록이 이동하고 있는 3D 기억 지도"
        : "미국 서부 여행 기록을 기다리고 있는 3D 기억 지도";

  return (
    <div
      className="hero-globe-preview"
      ref={containerRef}
      role="img"
      aria-label={globeAriaLabel}
      data-theme={theme}
      data-completed-count={relayState.completedCount}
    >
      <span className={`hero-globe-placeholder ${isGlobeReady ? "is-hidden" : ""}`} aria-hidden="true">
        <GlobeHemisphereEast size={68} weight="duotone" />
      </span>
      <Suspense fallback={<div className="hero-globe-loading"><GlobeHemisphereEast size={26} weight="duotone" /><span>기억 지도를 준비하고 있어요</span></div>}>
        {shouldLoadGlobe && globeMaterial && countries.length > 0 && (
          <Globe
            ref={globeRef}
            width={size.width}
            height={size.height}
            backgroundColor="rgba(0,0,0,0)"
            globeMaterial={globeMaterial}
            rendererConfig={GLOBE_RENDERER_CONFIG}
            showAtmosphere
            atmosphereColor={globePalette.atmosphere}
            atmosphereAltitude={0.1}
            polygonsData={countries}
            enablePointerInteraction={false}
            polygonCapColor={(polygon) => getHeroDensityPalette(theme, getDensity(polygon)).cap}
            polygonSideColor={(polygon) => getHeroDensityPalette(theme, getDensity(polygon)).side}
            polygonStrokeColor={(polygon) => getHeroDensityPalette(theme, getDensity(polygon)).stroke}
            polygonAltitude={(polygon) => (getDensity(polygon) === "NONE" ? 0.002 : 0.009)}
            polygonsTransitionDuration={polygonsTransitionDuration}
            onGlobeReady={() => {
              const globe = globeRef.current;
              applyGlobeRenderQuality(globe);
              const controls = globe?.controls();
              if (controls) controls.enabled = false;
              setIsGlobeReady(true);
              onReady?.();
            }}
          />
        )}
      </Suspense>
      <div className="hero-globe-state" aria-hidden="true">
        {relayState.cards.map((card) => (
          <span key={card.key} data-country={card.country} data-density={card.density.level} data-applied={card.isApplied ? "true" : "false"} />
        ))}
      </div>
      <span className="hero-target-pulse" aria-hidden="true" />
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

function MobileHeroSection({ onExperienceEntry, theme }) {
  const reducedMotionQuery = "(prefers-reduced-motion: reduce)";
  const [prefersReducedMotion, setPrefersReducedMotion] = useState(() => window.matchMedia(reducedMotionQuery).matches);
  const [entryState, setEntryState] = useState(() => getHeroMobileEntryState(0, {
    reducedMotion: window.matchMedia(reducedMotionQuery).matches,
  }));
  const [isGlobeReady, setIsGlobeReady] = useState(false);
  const visualRef = useRef(null);
  const recordRef = useRef(null);
  const playedRef = useRef(prefersReducedMotion);
  const startedRef = useRef(false);

  useEffect(() => {
    const media = window.matchMedia(reducedMotionQuery);
    const updatePreference = () => setPrefersReducedMotion(media.matches);
    media.addEventListener("change", updatePreference);
    return () => media.removeEventListener("change", updatePreference);
  }, []);

  useEffect(() => {
    const visual = visualRef.current;
    const record = recordRef.current;
    if (!visual || !record) return undefined;

    const measureTravel = () => {
      const globe = visual.querySelector(".hero-globe-preview");
      if (!globe) return;
      const recordRect = record.getBoundingClientRect();
      const globeRect = globe.getBoundingClientRect();
      visual.style.setProperty("--record-travel-x", `${((globeRect.left + (globeRect.width / 2)) - (recordRect.left + (recordRect.width / 2))).toFixed(2)}px`);
      visual.style.setProperty("--record-travel-y", `${((globeRect.top + (globeRect.height / 2)) - (recordRect.top + (recordRect.height / 2))).toFixed(2)}px`);
    };

    const observer = new ResizeObserver(measureTravel);
    observer.observe(visual);
    observer.observe(record);
    measureTravel();
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    if (prefersReducedMotion) {
      playedRef.current = true;
      startedRef.current = false;
      setEntryState(getHeroMobileEntryState(0, { reducedMotion: true }));
      return undefined;
    }
    if (!isGlobeReady || playedRef.current || startedRef.current) return undefined;

    startedRef.current = true;
    const playFrame = window.requestAnimationFrame(() => setEntryState(getHeroMobileEntryState(1)));
    const applyTimer = window.setTimeout(() => {
      setEntryState(getHeroMobileEntryState(HERO_MOBILE_ENTRY_APPLY_AT_MS));
    }, HERO_MOBILE_ENTRY_APPLY_AT_MS);
    const completeTimer = window.setTimeout(() => {
      playedRef.current = true;
      startedRef.current = false;
      setEntryState(getHeroMobileEntryState(HERO_MOBILE_ENTRY_DURATION_MS));
    }, HERO_MOBILE_ENTRY_DURATION_MS);

    return () => {
      window.cancelAnimationFrame(playFrame);
      window.clearTimeout(applyTimer);
      window.clearTimeout(completeTimer);
      if (!playedRef.current) startedRef.current = false;
    };
  }, [isGlobeReady, prefersReducedMotion]);

  const isComplete = entryState.isComplete;
  const sourceIsHidden = isComplete && !prefersReducedMotion;
  const resultIsHidden = !isComplete || prefersReducedMotion;
  const liveStatus = isComplete
    ? "미국 서부 여행 기록이 나만의 지도에 남았어요."
    : entryState.isApplied
      ? "여행 기록이 지도에 닿아 미국의 색이 진해지고 있어요."
      : entryState.phase === "playing"
        ? "미국 서부의 여행 순간을 기록 하나로 만들어 지도에 옮기고 있어요."
        : "미국 서부의 여행 순간과 나만의 3D 지도가 준비됐어요.";

  return (
    <section
      className="hero hero-mobile"
      data-entry-phase={entryState.phase}
      data-map-applied={entryState.isApplied ? "true" : "false"}
      data-reduced-motion={prefersReducedMotion ? "true" : "false"}
      aria-labelledby="hero-mobile-title"
    >
      <span className="hero-relay-anchor" id="hero-relay" aria-hidden="true" />
      <div className="hero-mobile-frame">
        <h1 className="hero-mobile-title" id="hero-mobile-title"><span>여행의 순간을,</span><em>나만의 지도로.</em></h1>
        <div className="hero-mobile-visual" ref={visualRef} aria-label="미국 서부의 여행 사진 한 장이 기록이 되어 3D 지도에 남는 모습">
          <HeroGlobe
            relayState={entryState.relayState}
            theme={theme}
            onReady={() => setIsGlobeReady(true)}
            waitForIdle
            polygonsTransitionDuration={360}
          />

          <figure className="hero-mobile-source-record" ref={recordRef} aria-hidden={sourceIsHidden}>
            <div className="hero-mobile-source-photo">
              <img
                src={HERO_JOURNEY_RECORD.representative.src}
                alt={HERO_JOURNEY_RECORD.representative.alt}
                loading="eager"
                fetchPriority="high"
                decoding="auto"
              />
              <small>{HERO_JOURNEY_RECORD.photoCredit}</small>
            </div>
            <figcaption><MapPin size={14} weight="fill" /><span><strong>미국 서부</strong><small>여행의 순간</small></span></figcaption>
            <span className="hero-mobile-record-stamp"><CheckCircle size={15} weight="fill" />기록 1개</span>
          </figure>

          <div className="hero-mobile-recorded-result" aria-hidden={resultIsHidden}>
            <img src={HERO_JOURNEY_RECORD.representative.src} alt="" />
            <span><strong>미국 서부</strong><small>나만의 지도에 기록됨</small></span>
            <CheckCircle size={19} weight="fill" />
          </div>
        </div>
        <a className="hero-mobile-experience-cue" href="#experience" onClick={() => onExperienceEntry("hero_mobile")}><span>기록된 추억 열어보기</span><ArrowDown size={18} weight="bold" /></a>
        <p className="sr-only" aria-live="polite">{liveStatus}</p>
      </div>
    </section>
  );
}

function useHeroMemoryRelay() {
  const sectionRef = useRef(null);
  const frameRef = useRef(null);
  const stateKeyRef = useRef("");
  const [relayState, setRelayState] = useState(() => getHeroMemoryRelayState(0));

  useEffect(() => {
    const section = sectionRef.current;
    const frame = frameRef.current;
    if (!section || !frame) return undefined;

    const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
    let animationFrame = 0;

    const renderProgress = () => {
      animationFrame = 0;
      const sectionRect = section.getBoundingClientRect();
      const stickyTop = Number.parseFloat(window.getComputedStyle(frame).top) || 0;
      const progress = getHeroRelayProgress({
        sectionTop: sectionRect.top,
        sectionHeight: section.offsetHeight,
        frameHeight: frame.offsetHeight,
        stickyTop,
      });
      const nextState = getHeroMemoryRelayState(progress, { reducedMotion: reducedMotion.matches });

      section.style.setProperty("--hero-intro-exit", nextState.introExit.toFixed(4));
      section.style.setProperty("--hero-stack-reveal", nextState.stackReveal.toFixed(4));
      section.style.setProperty("--hero-handoff-reveal", nextState.handoffReveal.toFixed(4));
      section.style.setProperty("--hero-copy-x", `${(-42 * nextState.introExit).toFixed(2)}px`);
      const mobileLayout = window.innerWidth <= 560;
      const tabletLayout = window.innerWidth <= 900;
      const compactShortLayout = mobileLayout && window.innerHeight <= 680;
      section.style.setProperty("--hero-map-x", `${(tabletLayout ? 0 : -150 * nextState.introExit).toFixed(2)}px`);
      const compactMapShift = mobileLayout ? getHeroMobileMapShift(nextState.introExit, window.innerHeight) : 0;
      section.style.setProperty("--hero-map-y", `${compactMapShift.toFixed(2)}px`);
      section.style.setProperty("--hero-memory-x", `${(50 * nextState.introExit).toFixed(2)}px`);
      section.style.setProperty("--hero-handoff-x", `${(24 * (1 - nextState.handoffReveal)).toFixed(2)}px`);
      section.style.setProperty("--hero-globe-opacity", nextState.globeOpacity.toFixed(4));
      section.style.setProperty("--hero-globe-scale", nextState.globeScale.toFixed(4));
      const [supportLeftReveal = 0, supportRightReveal = 0] = nextState.supportPhotoReveals;
      const formation = nextState.recordFormation;
      const travel = nextState.recordTravel;
      const visualTravel = Math.sqrt(travel);
      const lerp = (start, end, amount) => start + ((end - start) * amount);

      section.style.setProperty("--hero-moment-reveal", nextState.momentReveal.toFixed(4));
      section.style.setProperty("--hero-support-left-reveal", supportLeftReveal.toFixed(4));
      section.style.setProperty("--hero-support-right-reveal", supportRightReveal.toFixed(4));
      section.style.setProperty("--hero-record-reveal", nextState.recordReveal.toFixed(4));
      section.style.setProperty("--hero-record-formation", formation.toFixed(4));
      section.style.setProperty("--hero-map-reveal", nextState.mapReveal.toFixed(4));
      section.style.setProperty("--hero-map-result-reveal", nextState.mapResultReveal.toFixed(4));
      section.style.setProperty("--hero-record-object-opacity", nextState.recordObjectOpacity.toFixed(4));
      section.style.setProperty("--hero-personal-line-reveal", (nextState.momentReveal * (1 - formation)).toFixed(4));
      section.style.setProperty("--hero-record-line-reveal", (nextState.recordReveal * (1 - nextState.mapReveal)).toFixed(4));
      section.style.setProperty("--hero-map-line-reveal", nextState.mapResultReveal.toFixed(4));
      section.style.setProperty("--hero-map-cta-reveal", clampUnit((nextState.progress - 0.93) / 0.05).toFixed(4));
      section.style.setProperty("--hero-record-surface-opacity", formation.toFixed(4));
      section.style.setProperty("--hero-record-meta-opacity", formation.toFixed(4));
      section.style.setProperty("--hero-credit-opacity", (nextState.momentReveal * (1 - formation)).toFixed(4));

      section.style.setProperty("--hero-main-x", `${lerp(0, mobileLayout ? -34 : -40, formation).toFixed(2)}px`);
      section.style.setProperty("--hero-main-y", `${lerp(0, mobileLayout ? 42 : 55, formation).toFixed(2)}px`);
      section.style.setProperty("--hero-main-scale", lerp(1, mobileLayout ? 0.72 : 0.72, formation).toFixed(4));

      const supportLeftMomentX = compactShortLayout ? -100 : mobileLayout ? -118 : -190;
      const supportRightMomentX = compactShortLayout ? 100 : mobileLayout ? 118 : 190;
      const supportRecordX = compactShortLayout ? 80 : mobileLayout ? 95 : 130;
      const leftMomentY = mobileLayout ? 24 : 30;
      const rightMomentY = mobileLayout ? 32 : 38;
      section.style.setProperty("--hero-support-left-x", `${lerp(lerp(0, supportLeftMomentX, supportLeftReveal), supportRecordX, formation).toFixed(2)}px`);
      section.style.setProperty("--hero-support-left-y", `${lerp(lerp(8, leftMomentY, supportLeftReveal), mobileLayout ? -18 : -25, formation).toFixed(2)}px`);
      section.style.setProperty("--hero-support-left-scale", lerp(lerp(0.82, 0.94, supportLeftReveal), 0.72, formation).toFixed(4));
      section.style.setProperty("--hero-support-right-x", `${lerp(lerp(0, supportRightMomentX, supportRightReveal), supportRecordX, formation).toFixed(2)}px`);
      section.style.setProperty("--hero-support-right-y", `${lerp(lerp(8, rightMomentY, supportRightReveal), mobileLayout ? 74 : 96, formation).toFixed(2)}px`);
      section.style.setProperty("--hero-support-right-scale", lerp(lerp(0.82, 0.94, supportRightReveal), 0.72, formation).toFixed(4));

      const recordObject = section.querySelector(".hero-record-object");
      const globe = section.querySelector(".hero-globe-preview");
      const mapStory = section.querySelector(".hero-map-story");
      let travelX = mobileLayout ? 0 : -34;
      let travelY = mobileLayout ? 138 : 14;
      if (recordObject && globe && mapStory) {
        const recordCenterX = recordObject.offsetLeft;
        const recordCenterY = recordObject.offsetTop + (recordObject.offsetHeight / 2);
        const globeCenterX = globe.offsetLeft;
        const responsiveGlobeTop = compactShortLayout ? 94 : 180;
        const globeCenterY = tabletLayout
          ? responsiveGlobeTop + (globe.offsetHeight / 2)
          : mapStory.offsetHeight * 0.46;
        travelX = globeCenterX - recordCenterX;
        travelY = globeCenterY - recordCenterY;
      }
      section.style.setProperty("--hero-record-travel-x", `${(travelX * visualTravel).toFixed(2)}px`);
      section.style.setProperty("--hero-record-travel-y", `${(travelY * visualTravel).toFixed(2)}px`);
      section.style.setProperty("--hero-record-travel-scale", lerp(1, mobileLayout ? 0.24 : 0.22, visualTravel).toFixed(4));

      const nextStateKey = [
        nextState.phase,
        nextState.activeIndex,
        nextState.completedCount,
        nextState.momentReveal >= 0.5,
        nextState.recordReveal >= 0.5,
        nextState.mapResultReveal >= 0.5,
        nextState.progress >= 0.93,
        reducedMotion.matches,
      ].join(":");
      if (stateKeyRef.current !== nextStateKey) {
        stateKeyRef.current = nextStateKey;
        setRelayState(nextState);
      }
    };

    const requestRender = () => {
      if (animationFrame) return;
      animationFrame = window.requestAnimationFrame(renderProgress);
    };

    requestRender();
    window.addEventListener("scroll", requestRender, { passive: true });
    window.addEventListener("resize", requestRender);
    reducedMotion.addEventListener("change", requestRender);
    return () => {
      window.removeEventListener("scroll", requestRender);
      window.removeEventListener("resize", requestRender);
      reducedMotion.removeEventListener("change", requestRender);
      if (animationFrame) window.cancelAnimationFrame(animationFrame);
    };
  }, []);

  return { sectionRef, frameRef, relayState };
}

function DesktopHeroSection({ onExperienceEntry, theme }) {
  const { sectionRef, frameRef, relayState } = useHeroMemoryRelay();
  const activeKey = relayState.activeIndex >= 0 ? relayState.cards[relayState.activeIndex].key : "none";
  const isMapCtaReady = relayState.progress > 0.93;
  const isIntroAccessible = relayState.phase === "intro" || relayState.reducedMotion;
  const isFoldCueAccessible = relayState.phase === "intro" && !relayState.reducedMotion;
  const relayStatus = relayState.phase === "intro"
    ? "제주의 여행 기억이 지도에 남아 있어요."
    : relayState.phase === "moment"
      ? "미국 서부의 한 순간 곁으로 같은 여행의 사진들이 모이고 있어요."
      : relayState.phase === "record"
        ? "같은 여행의 사진들이 여행 기록 하나로 묶였어요."
        : relayState.completedCount > 0
          ? "미국 서부 여행 기록이 더해져 지도 색이 한 단계 진해졌어요."
          : "완성된 미국 서부 여행 기록이 3D 기억 지도로 이동하고 있어요.";

  return (
    <section className="hero hero-memory-story" ref={sectionRef} data-relay-phase={relayState.phase} data-active-memory={activeKey} data-recorded={relayState.completedCount > 0 ? "true" : "false"} data-map-cta-ready={isMapCtaReady ? "true" : "false"}>
      <span className="hero-relay-anchor" id="hero-relay" aria-hidden="true" />
      <div className="hero-story-frame" ref={frameRef}>
        <div className="hero-layout">
          <div className="hero-copy" aria-hidden={!isIntroAccessible} inert={!isIntroAccessible}>
            <h1><span>여행에서 남긴 순간을,</span><em>나만의 기억 지도로 기록해요.</em></h1>
            <p className="hero-description">다시 보고 싶은 장소를 골라 <br />그날의 사진과 함께 남겨보세요.</p>
            <div className="hero-actions">
              <div className="store-buttons" role="group" aria-label="Mapmory 앱 다운로드">
                <StoreButton placement="hero" platform="ios" label="App Store" tabIndex={isIntroAccessible ? 0 : -1} />
                <StoreButton placement="hero" platform="android" label="Google Play" tabIndex={isIntroAccessible ? 0 : -1} />
              </div>
              <a className="button button-secondary" href="#experience" tabIndex={isIntroAccessible ? 0 : -1} onClick={() => onExperienceEntry("hero")}><GlobeHemisphereEast size={19} weight="duotone" />지구본을 직접 돌려보기</a>
            </div>
            <p className="release-note"><CheckCircle size={17} weight="fill" />iPhone과 Android에서 바로 시작할 수 있어요</p>
          </div>

          <div className="hero-map-story" aria-label="한 여행의 사진과 글이 하나의 기록으로 묶이고, 같은 장소의 기록이 쌓일수록 지도 색이 진해지는 예시">
            <HeroGlobe relayState={relayState} theme={theme} />

            <article className="hero-memory-preview">
              <header><MapPin size={15} weight="fill" /><strong>제주 · 바닷가</strong><small>대한민국</small></header>
              <img src="/assets/team-jeju-coast-hero.jpg" alt="해 질 무렵 검은 바위 사이로 파도가 밀려오는 제주 바닷가" loading="eager" fetchPriority="high" decoding="auto" />
              <div><strong>파도 소리가 남은 제주 저녁</strong><span>Mapmory 개발팀의 실제 기록</span></div>
            </article>

            <div className="hero-scroll-sequence">
              <div className="hero-story-stage">
                <article className="hero-record-object" aria-label="미국 서부 여행 기록" aria-hidden={relayState.phase === "intro" || relayState.recordObjectOpacity < 0.2}>
                  <div className="hero-record-surface" aria-hidden="true" />
                  <figure className="hero-photo hero-photo-main">
                    <img src={HERO_JOURNEY_RECORD.representative.src} alt={HERO_JOURNEY_RECORD.representative.alt} loading="lazy" decoding="async" />
                  </figure>
                  <figure className="hero-photo hero-photo-support hero-photo-support-left" aria-hidden="true">
                    <img src={HERO_JOURNEY_RECORD.supporting[0].src} alt="" loading="lazy" decoding="async" />
                  </figure>
                  <figure className="hero-photo hero-photo-support hero-photo-support-right" aria-hidden="true">
                    <img src={HERO_JOURNEY_RECORD.supporting[1].src} alt="" loading="lazy" decoding="async" />
                  </figure>
                  <small className="hero-photo-credit">{HERO_JOURNEY_RECORD.photoCredit}</small>
                </article>

                <p className="hero-scene-line hero-line-moment" aria-hidden={relayState.momentReveal < 0.5}>“{HERO_JOURNEY_RECORD.quote}”</p>
                <p className="hero-scene-line hero-line-record" aria-hidden={relayState.recordReveal < 0.5}>{HERO_JOURNEY_RECORD.recordLine}</p>
                <p className="hero-scene-line hero-line-map" aria-hidden={relayState.mapResultReveal < 0.5}>{HERO_JOURNEY_RECORD.mapLine}</p>
                <a className="button button-primary hero-map-cta" href="#experience" aria-hidden={!isMapCtaReady} tabIndex={isMapCtaReady ? 0 : -1} onClick={() => onExperienceEntry("hero_handoff")}><GlobeHemisphereEast size={19} weight="duotone" />기록된 추억 직접 열어보기</a>
              </div>
            </div>
            <p className="sr-only" aria-live="polite">{relayStatus}</p>
          </div>
        </div>
        <div className="hero-reduced-summary">
          <p><CheckCircle size={19} weight="fill" />사진과 글이 하나의 여행 기록으로 묶여, 3D 기억 지도에 남아요.</p>
          <a className="button button-primary" href="#experience" onClick={() => onExperienceEntry("hero_reduced_handoff")}><GlobeHemisphereEast size={19} weight="duotone" />기록된 추억 직접 열어보기</a>
        </div>
        <a className="hero-fold-cue" href="#hero-relay" aria-hidden={!isFoldCueAccessible} tabIndex={isFoldCueAccessible ? 0 : -1}><span>아래로 내려 사진을 지도에 더해보기</span><ArrowDown size={18} weight="bold" /></a>
      </div>
    </section>
  );
}

function HeroSection(props) {
  const isMobile = useMediaQuery("(max-width: 560px)");
  return isMobile ? <MobileHeroSection {...props} /> : <DesktopHeroSection {...props} />;
}

function App() {
  const [theme, setTheme] = useState(() => localStorage.getItem("mapmory-theme") || "light");
  const [selectedMemory, setSelectedMemory] = useState(memories[0]);
  const [displayedMemory, setDisplayedMemory] = useState(memories[0]);
  const [globeFocusRequest, setGlobeFocusRequest] = useState(0);
  const [isGlobeGuideVisible, setIsGlobeGuideVisible] = useState(false);
  const [isGlobeFocused, setIsGlobeFocused] = useState(false);
  const [isWorldMemoryOpen, setIsWorldMemoryOpen] = useState(false);
  const [isWorldSelecting, setIsWorldSelecting] = useState(false);
  const experienceRef = useRef(null);
  const experienceStageRef = useRef(null);
  const globePanelRef = useRef(null);
  const worldSelectionTimerRef = useRef(null);
  const pendingWorldMemorySourceRef = useRef(null);
  const globeAnalytics = useExperienceAnalytics("globe");

  const returnToExperienceStage = useCallback(() => {
    window.requestAnimationFrame(() => {
      const behavior = window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth";
      experienceStageRef.current?.scrollIntoView({ behavior, block: "start" });
    });
  }, []);

  const dismissWorldMemory = useCallback(() => {
    window.clearTimeout(worldSelectionTimerRef.current);
    setIsWorldSelecting(false);
    setIsWorldMemoryOpen(false);
    returnToExperienceStage();
  }, [returnToExperienceStage]);

  const handleWorldSelect = (memory, selectionSource) => {
    globeAnalytics.startExperience("place_select");
    clearTimeout(worldSelectionTimerRef.current);
    setGlobeFocusRequest((current) => current + 1);
    const isNewSelection = selectedMemory.id !== memory.id;
    setIsWorldSelecting(true);
    if (selectedMemory.id !== memory.id) setSelectedMemory(memory);
    worldSelectionTimerRef.current = setTimeout(() => {
      setDisplayedMemory(memory);
      if (window.matchMedia("(max-width: 900px)").matches && !isWorldMemoryHistoryEntry(window.history.state)) {
        window.history.pushState(
          createWorldMemoryHistoryState(window.history.state, memory.key),
          "",
          window.location.href,
        );
      }
      setIsWorldMemoryOpen(true);
      setIsWorldSelecting(false);
      pendingWorldMemorySourceRef.current = selectionSource;
    }, isNewSelection ? WORLD_SELECTION_MOTION_MS + 120 : 320);
  };

  useEffect(() => () => clearTimeout(worldSelectionTimerRef.current), []);

  useEffect(() => {
    if (!isWorldMemoryOpen) return undefined;
    const handleHistoryBack = () => dismissWorldMemory();
    window.addEventListener("popstate", handleHistoryBack);
    return () => window.removeEventListener("popstate", handleHistoryBack);
  }, [dismissWorldMemory, isWorldMemoryOpen]);

  useEffect(() => { document.documentElement.dataset.theme = theme; localStorage.setItem("mapmory-theme", theme); }, [theme]);

  useEffect(() => {
    if (!isWorldMemoryOpen || window.innerWidth > 900) return undefined;
    const frame = requestAnimationFrame(() => {
      const behavior = window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth";
      experienceStageRef.current?.scrollIntoView({ behavior, block: "start" });
    });
    return () => cancelAnimationFrame(frame);
  }, [isWorldMemoryOpen]);

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

  const closeWorldMemory = () => {
    if (isWorldMemoryHistoryEntry(window.history.state)) {
      window.history.back();
      return;
    }
    dismissWorldMemory();
  };

  const setExperienceSectionRef = (node) => {
    experienceRef.current = node;
    globeAnalytics.sectionRef.current = node;
  };

  return (
    <main id="top">
      <header className="site-header">
        <Brand />
        <nav aria-label="주요 메뉴">
          <a href="#experience" onClick={() => globeAnalytics.trackEntryClick("header_nav")}>지구본 체험</a>
          <a href="#korea-detail" onClick={() => trackEvent(ANALYTICS_EVENTS.EXPERIENCE_CTA_CLICK, { experience_type: "korea_detail", cta_placement: "header_nav" })}>대한민국 지도</a>
        </nav>
        <div className="header-actions"><ThemeToggle theme={theme} onChange={setTheme} /><HeaderStoreMenu /></div>
      </header>

      <HeroSection onExperienceEntry={globeAnalytics.trackEntryClick} theme={theme} />

      <section className={`experience-section ${isGlobeFocused ? "is-focused" : ""}`} id="experience" ref={setExperienceSectionRef}>
        <div className="experience-pin">
          <div className="section-heading section-heading-flow">
            <div><p className="eyebrow">01 · 세계</p><h2>지구본을 돌려 기억을 찾아요.</h2></div>
            <p>잡고 돌린 뒤 민트색 나라를 눌러보세요. 선택한 장소의 실제 사진과 기억이 별도 패널에서 열려요.</p>
          </div>
          <div className={`experience-stage ${isWorldMemoryOpen ? "is-memory-open" : ""}`} ref={experienceStageRef}>
            <article className="globe-panel" id="globe-demo" ref={globePanelRef}>
              <header><span><GlobeHemisphereEast size={19} weight="duotone" />3D 기억 지도</span></header>
              <InteractiveGlobe selected={selectedMemory} focusRequest={globeFocusRequest} onSelect={handleWorldSelect} onInteract={globeAnalytics.startExperience} theme={theme} guideVisible={isGlobeGuideVisible} onGuideDismiss={dismissGlobeGuide} isSelecting={isWorldSelecting} />
              <LocationSelector selected={selectedMemory} onSelect={handleWorldSelect} disabled={isWorldSelecting} />
            </article>
            <MemoryCard key={displayedMemory.id} memory={displayedMemory} onClose={closeWorldMemory} priority />
          </div>
        </div>
      </section>


      <KoreaDetailExperience theme={theme} />

      <section className="download-section" id="download">
        <h2>방금 본 장소처럼,<br />당신의 기억도 지도로.</h2>
        <p>iPhone과 Android에서 Mapmory를 다운로드하고 나만의 기억 지도를 시작하세요.</p>
        <div className="download-actions" role="group" aria-label="Mapmory 앱 다운로드">
          <StoreButton placement="final" platform="ios" label="App Store" />
          <StoreButton placement="final" platform="android" label="Google Play" />
        </div>
      </section>

      <footer><div><Brand /><p>기억은 흩어져도, 지도는 남아요.</p></div><p>© 2026 Mapmory. All rights reserved.</p></footer>
    </main>
  );
}

export { App };
