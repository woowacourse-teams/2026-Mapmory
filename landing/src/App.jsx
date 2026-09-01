import { lazy, Suspense, useEffect, useMemo, useRef, useState } from "react";
import koreaProvinces from "./data/korea-provinces.json";
import {
  ArrowDown,
  ArrowLeft,
  ArrowRight,
  Bell,
  CheckCircle,
  EnvelopeSimple,
  GlobeHemisphereEast,
  HandSwipeLeft,
  MapPin,
  MapTrifold,
  Moon,
  NavigationArrow,
  Plus,
  Sun,
  X,
} from "@phosphor-icons/react";
import { ANALYTICS_EVENTS, trackEvent } from "./analytics.js";
import { subscribeToLaunchWaitlist } from "./waitlist.js";
import { useExperienceAnalytics } from "./useExperienceAnalytics.js";

const GOOGLE_PLAY_URL = import.meta.env.VITE_GOOGLE_PLAY_URL?.trim();
const Globe = lazy(() => import("react-globe.gl"));
const WORLD_SELECTION_MOTION_MS = 1050;
const KOREA_FILL_MOTION_MS = 1500;

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
const koreaBounds = { minLng: 124.5, maxLng: 130.05, minLat: 33, maxLat: 38.75 };
const districtMapCache = new Map();

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

function DownloadButton({ className = "", placement }) {
  const handleClick = () => {
    trackEvent(
      GOOGLE_PLAY_URL ? ANALYTICS_EVENTS.DOWNLOAD_CLICK : ANALYTICS_EVENTS.WAITLIST_CTA_CLICK,
      { cta_placement: placement },
    );
  };

  if (GOOGLE_PLAY_URL) {
    return <a className={`button button-primary ${className}`} href={GOOGLE_PLAY_URL} target="_blank" rel="noreferrer" onClick={handleClick}><ArrowDown size={19} weight="bold" />Mapmory 다운로드</a>;
  }

  return <a className={`button button-primary ${className}`} href="#download" onClick={handleClick}><Bell size={19} weight="fill" />출시 알림 신청하기</a>;
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
  const containerRef = useRef(null);
  const [size, setSize] = useState({ width: 540, height: 540 });
  const [hoveredId, setHoveredId] = useState(null);
  const [globeMaterial, setGlobeMaterial] = useState(null);
  const [countries, setCountries] = useState([]);
  const [isGlobeReady, setIsGlobeReady] = useState(false);
  const [isGlobeInView, setIsGlobeInView] = useState(false);
  const hasFocusedRef = useRef(false);

  useEffect(() => {
    let active = true;
    Promise.all([
      import("topojson-client"),
      import("world-atlas/countries-110m.json"),
    ]).then(([{ feature }, { default: topology }]) => {
      if (active) setCountries(feature(topology, topology.objects.countries).features);
    });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    let active = true;
    let material;
    import("three").then(({ MeshPhongMaterial }) => {
      material = new MeshPhongMaterial({ color: theme === "dark" ? "#0b111c" : "#121c27", emissive: theme === "dark" ? "#07121b" : "#0b1118", shininess: 12 });
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
      return undefined;
    }
    const observer = new IntersectionObserver(
      ([entry]) => setIsGlobeInView(entry.isIntersecting),
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

  return (
    <div
      className={`globe-shell ${isSelecting ? "is-selecting" : ""}`}
      ref={containerRef}
      role="region"
      aria-label="회전 가능한 Mapmory 세계 지구본"
      aria-busy={isSelecting}
      onPointerDown={() => onInteract("globe_drag")}
      onWheel={() => onInteract("globe_zoom")}
    >
      <Suspense fallback={<div className="globe-loading"><GlobeHemisphereEast size={28} weight="duotone" /><span>지구본을 준비하고 있어요</span></div>}>
        {globeMaterial && countries.length > 0 && <Globe ref={globeRef} width={size.width} height={size.height} backgroundColor="rgba(0,0,0,0)" globeMaterial={globeMaterial} showAtmosphere atmosphereColor="#93a6b8" atmosphereAltitude={0.12} polygonsData={countries}
          onGlobeReady={() => setIsGlobeReady(true)}
          polygonCapColor={(polygon) => { const id = String(polygon.id); if (id === selected.id) return "#f6c66f"; if (id === hoveredId && isVisited(polygon)) return "#72efbd"; return isVisited(polygon) ? "#3fd09a" : "#303b4d"; }}
          polygonSideColor={(polygon) => (String(polygon.id) === selected.id ? "#b87924" : isVisited(polygon) ? "#189a6d" : "#1b2532")}
          polygonStrokeColor={(polygon) => (String(polygon.id) === selected.id ? "#fff1c7" : isVisited(polygon) ? "#a3f4d3" : "#778497")}
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

function MemoryCard({ memory, onClose }) {
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
        <MapPin size={18} weight="fill" /><span>{memory.location}</span><small>{memory.country}</small>
        {onClose && <button type="button" className="world-memory-close" onClick={onClose} aria-label="기억 닫고 지구본으로 돌아가기"><X size={18} weight="bold" /><span>지구본으로</span></button>}
      </header>
      <div className={`memory-image-wrap ${hasGallery ? "is-gallery" : ""}`}>
        <img key={activePhoto.src} src={activePhoto.src} alt={activePhoto.alt} loading="lazy" decoding="async" />
        {hasGallery && (
          <>
            <span className="memory-photo-count" aria-hidden="true">{photoIndex + 1} / {photos.length}</span>
            <button type="button" className="memory-gallery-arrow is-prev" onClick={() => movePhoto(-1)} aria-label="이전 미국 여행 사진"><ArrowLeft size={18} weight="bold" /></button>
            <button type="button" className="memory-gallery-arrow is-next" onClick={() => movePhoto(1)} aria-label="다음 미국 여행 사진"><ArrowRight size={18} weight="bold" /></button>
            <div className="memory-photo-meta">
              <span>{activePhoto.caption}</span>
              <div className="memory-photo-dots" role="group" aria-label="미국 여행 사진 선택">
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
      analytics.trackMemoryOpen(memory.key, selectionSource);
    }
    setSelected(memory);
    setIsAddPanelOpen(false);
    setTransitioningKey(null);
    setDetailLevel(3);
  };

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
                      GOOGLE_PLAY_URL ? ANALYTICS_EVENTS.DOWNLOAD_CLICK : ANALYTICS_EVENTS.WAITLIST_CTA_CLICK,
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

function HeroSection({ onExperienceEntry }) {
  const [photoStep, setPhotoStep] = useState(0);
  const heroRef = useRef(null);

  useEffect(() => {
    const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
    if (reducedMotion.matches) {
      setPhotoStep(2);
      return undefined;
    }

    let frame = 0;
    const updatePhotoStep = () => {
      frame = 0;
      const distance = Math.max(0, window.scrollY);
      const nextStep = distance >= 120 ? 2 : distance >= 30 ? 1 : 0;
      setPhotoStep(nextStep);
    };
    const handleScroll = () => {
      if (frame) return;
      frame = window.requestAnimationFrame(updatePhotoStep);
    };

    const startsAtHeroTop = window.location.hash === "" || window.location.hash === "#top";
    if (startsAtHeroTop) window.scrollTo(0, 0);
    frame = window.requestAnimationFrame(updatePhotoStep);
    window.addEventListener("scroll", handleScroll, { passive: true });
    return () => {
      window.removeEventListener("scroll", handleScroll);
      if (frame) window.cancelAnimationFrame(frame);
    };
  }, []);

  return (
    <section className="hero" ref={heroRef}>
      <div className="hero-intro">
        <div className="hero-copy">
          <h1>장소를 따라가면, 그날의 <span className="hero-memory-word"><em>기억</em><span aria-hidden="true">기억</span></span>이 다시 열려요.</h1>
          <p className="hero-description">사진과 장소를 모아 나만의 기억 지도를 만들어요.</p>
          <div className="hero-actions">
            <a className="button button-primary" href="#experience" onClick={() => onExperienceEntry("hero")}><GlobeHemisphereEast size={19} weight="duotone" />지구본 돌려보기</a>
          </div>
        </div>

        <div className={`hero-photo-cluster photo-step-${photoStep}`} aria-label="스크롤하며 하나씩 더해지는 Mapmory 개발팀의 실제 장소 기록 세 장">
          <figure className="hero-photo hero-photo-main">
            <img src="/assets/team-jeju-coast-hero.jpg" alt="해 질 무렵 검은 바위 사이로 파도가 밀려오는 제주 바닷가" loading="eager" fetchPriority="high" decoding="auto" />
            <figcaption><span><MapPin size={16} weight="fill" /><span className="hero-photo-handwriting">파도 소리가 남은 저녁</span></span><small>Mapmory 개발팀 촬영</small></figcaption>
          </figure>
          <figure className="hero-photo hero-photo-left">
            <img src="/assets/team-shanghai-bund.jpg" alt="황푸강 건너 푸둥의 불빛이 보이는 상하이 와이탄" loading="eager" decoding="async" />
            <figcaption><span><MapPin size={15} weight="fill" /><span className="hero-photo-handwriting">불빛이 번지던 강변</span></span><small>Mapmory 개발팀 촬영</small></figcaption>
          </figure>
          <figure className="hero-photo hero-photo-right">
            <img src="/assets/team-usa-antelope-canyon.jpg" alt="붉은 사암 사이로 햇빛이 들어오는 앤텔로프 캐니언" loading="eager" decoding="async" />
            <figcaption><span><MapPin size={15} weight="fill" /><span className="hero-photo-handwriting">빛이 스며든 붉은 협곡</span></span><small>Mapmory 개발팀 촬영</small></figcaption>
          </figure>
        </div>
        <p className="release-note"><CheckCircle size={17} weight="fill" />Google Play 출시 준비 중</p>
      </div>
      <a className="hero-fold-cue" href="#experience" onClick={() => onExperienceEntry("scroll_cue")}><span>아래로 내려 앱 경험해보기</span><ArrowDown size={18} weight="bold" /></a>
    </section>
  );
}

function App() {
  const [theme, setTheme] = useState(() => localStorage.getItem("mapmory-theme") || "light");
  const [selectedMemory, setSelectedMemory] = useState(memories[0]);
  const [displayedMemory, setDisplayedMemory] = useState(memories[0]);
  const [globeFocusRequest, setGlobeFocusRequest] = useState(0);
  const [isGlobeGuideVisible, setIsGlobeGuideVisible] = useState(false);
  const [isWorldMemoryOpen, setIsWorldMemoryOpen] = useState(false);
  const [isWorldSelecting, setIsWorldSelecting] = useState(false);
  const experienceRef = useRef(null);
  const experienceStageRef = useRef(null);
  const globePanelRef = useRef(null);
  const worldSelectionTimerRef = useRef(null);
  const globeAnalytics = useExperienceAnalytics("globe");

  const handleWorldSelect = (memory, selectionSource) => {
    globeAnalytics.startExperience("place_select");
    clearTimeout(worldSelectionTimerRef.current);
    setGlobeFocusRequest((current) => current + 1);
    const isNewSelection = selectedMemory.id !== memory.id;
    setIsWorldSelecting(true);
    if (selectedMemory.id !== memory.id) setSelectedMemory(memory);
    worldSelectionTimerRef.current = setTimeout(() => {
      setDisplayedMemory(memory);
      setIsWorldMemoryOpen(true);
      setIsWorldSelecting(false);
      globeAnalytics.trackMemoryOpen(memory.key, selectionSource);
    }, isNewSelection ? WORLD_SELECTION_MOTION_MS + 120 : 320);
  };

  useEffect(() => () => clearTimeout(worldSelectionTimerRef.current), []);

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

  const dismissGlobeGuide = () => {
    setIsGlobeGuideVisible(false);
  };

  const closeWorldMemory = () => {
    clearTimeout(worldSelectionTimerRef.current);
    setIsWorldSelecting(false);
    setIsWorldMemoryOpen(false);
    requestAnimationFrame(() => {
      const behavior = window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth";
      experienceStageRef.current?.scrollIntoView({ behavior, block: "start" });
    });
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
        <div className="header-actions"><ThemeToggle theme={theme} onChange={setTheme} /><DownloadButton className="header-download" placement="header" /></div>
      </header>

      <HeroSection onExperienceEntry={globeAnalytics.trackEntryClick} />

      <section className="experience-section" id="experience" ref={setExperienceSectionRef}>
        <div className="experience-pin">
          <div className="section-heading section-heading-flow">
            <div><p className="eyebrow">01 · 세계</p><h2>지구본을 돌려 기억을 찾아요.</h2></div>
          </div>
          <div className={`experience-stage ${isWorldMemoryOpen ? "is-memory-open" : ""}`} ref={experienceStageRef}>
            <article className="globe-panel" id="globe-demo" ref={globePanelRef}>
              <header><span><GlobeHemisphereEast size={19} weight="duotone" />3D 기억 지도</span></header>
              <InteractiveGlobe selected={selectedMemory} focusRequest={globeFocusRequest} onSelect={handleWorldSelect} onInteract={globeAnalytics.startExperience} theme={theme} guideVisible={isGlobeGuideVisible} onGuideDismiss={dismissGlobeGuide} isSelecting={isWorldSelecting} />
              <LocationSelector selected={selectedMemory} onSelect={handleWorldSelect} disabled={isWorldSelecting} />
            </article>
            <MemoryCard key={displayedMemory.id} memory={displayedMemory} onClose={closeWorldMemory} />
          </div>
        </div>
      </section>


      <KoreaDetailExperience theme={theme} />

      <section className="download-section" id="download">
        <h2>방금 본 장소처럼,<br />당신의 기억도 지도로.</h2>
        {GOOGLE_PLAY_URL ? (
          <><p>Google Play에서 Mapmory를 다운로드하고 나만의 기억 지도를 시작하세요.</p><DownloadButton placement="final" /></>
        ) : (
          <><p>정식 출시되면 입력한 이메일로 한 번만 알려드릴게요.</p><LaunchWaitlistForm /></>
        )}
      </section>

      <footer><div><Brand /><p>기억은 흩어져도, 지도는 남아요.</p></div><p>© 2026 Mapmory. All rights reserved.</p></footer>
    </main>
  );
}

export { App };
