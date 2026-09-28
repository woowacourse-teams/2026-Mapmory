import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  ArrowLeft, ArrowRight, CheckCircle, Circle, CircleNotch, DownloadSimple,
  ImageSquare, MapPin, Pause, Play, ShareNetwork, ShieldCheck, Sparkle,
  UploadSimple, VideoCamera,
} from "@phosphor-icons/react";
import { trackCampaignEvent } from "./analytics.js";
import { APP_ACQUISITION_URL, CAMPAIGN_LANDING_URL } from "./campaignConfig.js";
import { analyzePhotoFiles, createPlaybackJourney, demoJourney, formatShortDate, getAutoDuration, getJourneyStats, getReplayDuration } from "./journeyData.js";
import { drawJourneyMap, getActivePoint, loadJourneyImages } from "./mapRenderer.js";
import { getJourneyProgressState } from "./journeyProgress.js";
import { pickOriginalPhotoFiles } from "./photoPicker.js";
import { downloadBlob, drawShareFrame, renderJourneyVideo, renderShareImage } from "./videoRenderer.js";
import { shareVideo } from "./shareVideo.js";

const processingSteps = ["촬영 날짜 정리", "GPS로 도시 묶기", "여행 경로 연결"];

function Brand() {
  return (
    <button className="brand" type="button" onClick={() => window.location.reload()} aria-label="Mapmory 캠페인 처음으로">
      <span className="brand-mark"><MapPin size={15} weight="fill" /></span><span>Mapmory</span>
    </button>
  );
}

function CampaignHeader({ onBack }) {
  return (
    <header className="campaign-header">
      {onBack ? <button className="icon-button" type="button" onClick={onBack} aria-label="이전 화면"><ArrowLeft size={20} weight="bold" /></button> : <span className="header-spacer" />}
      <Brand />
      <span className="header-tag">WEB TEST</span>
    </header>
  );
}

function JourneyMap({ journey, progress, className = "" }) {
  const canvasRef = useRef(null);
  const [revision, setRevision] = useState(0);
  const drawRef = useRef(null);
  const onImageReady = useCallback(() => setRevision((value) => value + 1), []);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas || journey.points.length === 0) return undefined;
    const draw = () => {
      const bounds = canvas.getBoundingClientRect();
      const width = Math.max(1, Math.round(bounds.width));
      const height = Math.max(1, Math.round(bounds.height));
      const density = Math.min(2, window.devicePixelRatio || 1);
      canvas.width = Math.round(width * density);
      canvas.height = Math.round(height * density);
      const ctx = canvas.getContext("2d");
      ctx.setTransform(density, 0, 0, density, 0, 0);
      drawJourneyMap(ctx, width, height, journey.points, progress, onImageReady);
    };
    drawRef.current = draw;
    draw();
  }, [journey, progress, revision, onImageReady]);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return undefined;
    const observer = new ResizeObserver(() => drawRef.current?.());
    observer.observe(canvas);
    return () => { observer.disconnect(); drawRef.current = null; };
  }, []);

  return <canvas ref={canvasRef} className={`journey-map ${className}`} data-testid="journey-map" aria-label="사진 GPS를 시간순으로 연결한 여행 지도" />;
}

function StatPills({ journey }) {
  const stats = getJourneyStats(journey);
  return <div className="stat-pills" aria-label="여행 통계"><span><strong>{stats.trips}</strong> TRIPS</span><span><strong>{stats.cities}</strong> CITIES</span><span><strong>{stats.photos}</strong> PHOTOS</span></div>;
}

function EntryScreen({ onPickFiles, onDemo }) {
  return (
    <section className="screen screen-entry" aria-labelledby="entry-title">
      <CampaignHeader />
      <div className="screen-content">
        <span className="campaign-badge"><Sparkle size={13} weight="fill" /> 2026 TRAVEL MAP</span>
        <h1 id="entry-title">구글 타임라인이 없어도,<br />여행은 사진에 남아 있어요.</h1>
        <p className="lead-copy">올해 찍은 사진을 고르면 날짜와 위치를 읽어 나만의 여행 지도를 자동으로 완성해드려요.</p>
        <figure className="map-preview-card">
          <figcaption>사진 GPS 기반 경로</figcaption>
          <JourneyMap journey={demoJourney} progress={1} />
          <small>Map data · Natural Earth</small>
        </figure>
        <button className="primary-button" type="button" onClick={onPickFiles}><UploadSimple size={20} weight="bold" /> 원본 사진 선택하고 지도 만들기</button>
        <p className="accuracy-note">최대 200장 · 총 500MB · 사진 한 장 50MB 이하</p>
        <button className="text-button" type="button" onClick={onDemo}>사진 없이 샘플 결과 먼저 보기 <ArrowRight size={16} weight="bold" /></button>
        <aside className="privacy-note"><ShieldCheck size={23} weight="duotone" /><div><strong>원본 사진은 업로드하지 않아요</strong><p>이 브라우저에서 날짜·GPS만 읽고 결과를 만들어요.</p></div></aside>
      </div>
    </section>
  );
}

function ProcessingScreen({ progress, selectedCount }) {
  const activeStep = progress < 38 ? 0 : progress < 72 ? 1 : 2;
  return (
    <section className="screen screen-processing" aria-labelledby="processing-title">
      <CampaignHeader />
      <div className="screen-content">
        <p className="eyebrow">MAPMORY LAB</p>
        <h1 id="processing-title">사진 속 여행을 찾는 중이에요</h1>
        <p className="lead-copy">촬영 정보는 이 기기 안에서만 분석하고 있어요.</p>
        <div className="processing-hero" role="status" aria-live="polite"><CircleNotch className="processing-spinner" size={118} weight="regular" /><strong>{progress}%</strong><span>{processingSteps[activeStep]}</span></div>
        <div className="photo-count-card"><ImageSquare size={24} weight="duotone" /><div><strong>{selectedCount}장의 사진</strong><span>브라우저에서 로컬 분석 중</span></div><b>{selectedCount}</b></div>
        <div className="process-list">
          {processingSteps.map((step, index) => {
            const complete = index < activeStep || progress === 100;
            const active = index === activeStep && progress < 100;
            return <div className={complete ? "is-complete" : active ? "is-active" : ""} key={step}>{complete ? <CheckCircle size={20} weight="fill" /> : active ? <CircleNotch size={20} /> : <Circle size={20} />}<span>{step}</span><small>{complete ? "완료" : active ? "진행 중" : "대기"}</small></div>;
          })}
        </div>
      </div>
    </section>
  );
}

function EmptyScreen({ analysis, selectedCount, onRetry, onRetryOriginals, onDemo }) {
  const supportedCount = analysis?.supportedPhotoCount ?? 0;
  const readFailedCount = analysis?.readFailedCount ?? 0;
  const unsupportedCount = analysis?.unsupportedCount ?? 0;
  const metadataMissingCount = analysis?.metadataMissingCount ?? 0;
  const parserFailedCompletely = supportedCount > 0 && readFailedCount >= supportedCount;
  const unsupportedCompletely = selectedCount > 0 && unsupportedCount === selectedCount;
  const title = analysis?.error ? "사진을 분석하지 못했어요." : unsupportedCompletely
    ? "선택한 파일을 분석할 수 없어요."
    : parserFailedCompletely
      ? "파일 정보를 읽는 중 문제가 생겼어요."
      : "웹에 전달된 사진에서 GPS가 빠졌어요.";
  const description = analysis?.error || (unsupportedCompletely
    ? "JPG, HEIC, HEIF, PNG, TIFF, AVIF, WEBP 형식의 사진을 선택해주세요."
    : parserFailedCompletely
      ? "사진은 정상적으로 선택됐지만 파일 내부 정보를 판독하지 못했습니다."
      : "Android 사진 선택기는 사진 앱에 위치가 표시되어도 웹에 넘기는 파일에서 좌표를 가릴 수 있어요.");
  const resultMessage = analysis?.error ? "사진 수와 용량을 확인한 뒤 다시 선택해주세요. 선택한 사진은 일부만 처리하지 않아요." : unsupportedCompletely
    ? "선택한 파일 형식을 확인해주세요."
    : parserFailedCompletely
      ? "원본 파일로 다시 시도하면 해결될 수 있어요."
      : "사진 앱의 위치정보가 없다는 뜻은 아니에요. ‘내 파일’의 DCIM/Camera 폴더에서 원본을 다시 선택해주세요.";

  return (
    <section className="screen screen-empty" aria-labelledby="empty-title">
      <CampaignHeader onBack={onRetry} />
      <div className="screen-content centered-content diagnostic-content">
        <span className="empty-icon"><MapPin size={34} weight="duotone" /></span>
        <span className="file-result-badge"><ShieldCheck size={14} weight="fill" /> 웹에서 받은 파일 기준</span>
        <h1 id="empty-title">{title}</h1>
        <p className="lead-copy">{description}</p>
        <div className="diagnostic-card" aria-label="사진 메타데이터 분석 결과">
          <div><span>웹에서 받은 파일</span><strong>{selectedCount}장</strong></div>
          <div><span>분석 가능한 사진</span><strong>{supportedCount}장</strong></div>
          <div><span>EXIF·XMP 메타데이터</span><strong>{analysis?.metadataReadCount ?? 0}장</strong></div>
          <div><span>파일에 포함된 GPS</span><strong className={(analysis?.validPhotoCount ?? 0) > 0 ? "is-success" : "is-empty"}>{analysis?.validPhotoCount ?? 0}장</strong></div>
        </div>
        <div className="diagnostic-notes">
          {analysis?.formats?.length > 0 && <p><strong>파일 형식</strong> {analysis.formats.join(", ")}</p>}
          {metadataMissingCount > 0 && <p><strong>EXIF·XMP 미포함</strong> {metadataMissingCount}장</p>}
          {readFailedCount > 0 && <p className="is-error"><strong>파일 판독 오류</strong> {readFailedCount}장</p>}
          {unsupportedCount > 0 && <p className="is-error"><strong>지원하지 않는 형식</strong> {unsupportedCount}장</p>}
        </div>
        <p className="file-result-note">{resultMessage}</p>
        <button className="primary-button" type="button" onClick={onRetryOriginals}>내 파일에서 원본 다시 선택하기</button>
        <button className="text-button" type="button" onClick={onDemo}>샘플 지도로 결과 미리보기 <ArrowRight size={16} /></button>
      </div>
    </section>
  );
}

function ReplayScreen({ journey, onBack, onNext }) {
  const playbackJourney = useMemo(() => createPlaybackJourney(journey), [journey]);
  const duration = getReplayDuration(playbackJourney);
  const [progress, setProgress] = useState(0);
  const [playing, setPlaying] = useState(() => !window.matchMedia("(prefers-reduced-motion: reduce)").matches);
  const startedAt = useRef(performance.now());
  const startedFrom = useRef(0);

  useEffect(() => {
    if (!playing) return undefined;
    startedAt.current = performance.now();
    startedFrom.current = progress;
    let frame = 0;
    const tick = (now) => {
      const next = Math.min(1, startedFrom.current + ((now - startedAt.current) / 1000) / duration);
      setProgress(next);
      if (next < 1) frame = requestAnimationFrame(tick);
      else { setPlaying(false); trackCampaignEvent("travel_map_replay_complete", { duration_seconds: duration, journey_source: journey.source }); }
    };
    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [playing, duration, journey.source]);

  useEffect(() => {
    if (journey.source !== "demo" || progress < 1 || playing) return undefined;
    const timer = window.setTimeout(onNext, 700);
    return () => window.clearTimeout(timer);
  }, [journey.source, onNext, playing, progress]);

  const activePoint = getActivePoint(playbackJourney.points, progress);
  const progressState = getJourneyProgressState(playbackJourney.points.length, progress);
  const togglePlayback = () => { if (progress >= 1) setProgress(0); setPlaying((value) => !value); };
  const seekPlayback = (event) => { setPlaying(false); setProgress(Number(event.currentTarget.value) / 1000); };

  return (
    <section className="screen screen-replay" aria-labelledby="replay-title">
      <CampaignHeader onBack={onBack} />
      <div className="screen-content">
        <div className="replay-heading"><p className="eyebrow">MY 2026 JOURNEY</p><h1 id="replay-title">2026 지금까지의 여행</h1><p>사진 GPS를 시간순으로 연결한 경로예요.</p></div>
        <div className="replay-map-card">
          <JourneyMap journey={playbackJourney} progress={progress} />
          <div className="map-recording-status" aria-live="polite">
            <ImageSquare size={15} weight="duotone" />
            <ArrowRight size={13} weight="bold" />
            <MapPin size={15} weight="fill" />
            <span>{progressState.isComplete ? "사진으로 여행 지도 완성" : "사진 위치를 지도에 기록 중"}</span>
            <strong>{progressState.visibleCount}/{playbackJourney.points.length}</strong>
          </div>
          <span className="map-attribution">Natural Earth · GPS points</span>
        </div>
        <StatPills journey={journey} />
        {(journey.missingGpsCount > 0 || journey.readFailedCount > 0 || journey.unsupportedCount > 0) && <p className="gps-summary">{journey.photoCount}장 중 GPS {journey.validPhotoCount}장 · 위치 없음 {journey.missingGpsCount ?? 0}장 · 판독 실패 {(journey.readFailedCount ?? 0) + (journey.unsupportedCount ?? 0)}장</p>}
        <div className="current-trip">
          <div><ImageSquare size={14} weight="duotone" /><span>{activePoint ? formatShortDate(activePoint.date) : "-"} 사진</span><ArrowRight size={13} weight="bold" /><MapPin size={14} weight="fill" /><b>{activePoint?.name ?? "여행"}에 기록</b></div>
          <strong>{progressState.isComplete ? "사진으로 여행 지도를 완성했어요" : `${activePoint?.name ?? "여행"} 기록이 지도에 추가됐어요`}</strong>
          <small>{progressState.visibleCount} / {playbackJourney.points.length}</small>
        </div>
        <div className="playback-control"><button type="button" onClick={togglePlayback} aria-label={playing ? "일시정지" : "재생"}>{playing ? <Pause size={20} weight="fill" /> : <Play size={20} weight="fill" />}</button><input aria-label="영상 진행 위치" type="range" min="0" max="1000" value={Math.round(progress * 1000)} onInput={seekPlayback} onChange={seekPlayback} /><time>{Math.round(progress * duration).toString().padStart(2, "0")} / {duration}초</time></div>
        <p className="accuracy-note">사진의 촬영 좌표를 시간순으로 연결했어요. 실제 이동 경로와는 다를 수 있어요.</p>
        <button className="primary-button" type="button" onClick={onNext}>내 여행 영상 보기 <ArrowRight size={19} weight="bold" /></button>
      </div>
    </section>
  );
}

function SharePreview({ journey, duration }) {
  const canvasRef = useRef(null);
  const [progress, setProgress] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [imageRevision, setImageRevision] = useState(0);
  const draw = useCallback((nextProgress) => { const canvas = canvasRef.current; if (canvas) drawShareFrame(canvas.getContext("2d"), journey, nextProgress, duration); }, [duration, journey]);

  useEffect(() => {
    let active = true;
    loadJourneyImages(journey.points).then(() => { if (active) setImageRevision((value) => value + 1); });
    return () => { active = false; };
  }, [journey]);
  useEffect(() => { draw(progress); }, [draw, imageRevision, progress]);
  useEffect(() => {
    if (!playing) return undefined;
    const startedAt = performance.now();
    const initial = progress >= 1 ? 0 : progress;
    let frame = 0;
    const tick = (now) => { const next = Math.min(1, initial + (now - startedAt) / (duration * 1000)); setProgress(next); if (next < 1) frame = requestAnimationFrame(tick); else setPlaying(false); };
    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [playing, duration]);

  return <div className="share-preview-wrap"><canvas ref={canvasRef} width="720" height="1280" aria-label="9대16 여행 영상 미리보기" /><button className="preview-play" type="button" onClick={() => setPlaying((value) => !value)} aria-label={playing ? "미리보기 일시정지" : "미리보기 재생"}>{playing ? <Pause size={30} weight="fill" /> : <Play size={30} weight="fill" />}</button><div className="preview-progress"><span style={{ width: `${progress * 100}%` }} /></div></div>;
}

function RecapScreen({ journey, onBack, onNext }) {
  const playbackJourney = useMemo(() => createPlaybackJourney(journey), [journey]);
  const duration = getAutoDuration(playbackJourney);
  const [renderState, setRenderState] = useState({ status: "idle", progress: 0, error: "" });
  useEffect(() => { trackCampaignEvent("travel_map_recap_view", { duration_seconds: duration, journey_source: journey.source }); }, [duration, journey.source]);

  const makeVideo = async () => {
    setRenderState({ status: "rendering", progress: 0, error: "" });
    try {
      const blob = await renderJourneyVideo(playbackJourney, duration, (progress) => setRenderState({ status: "rendering", progress, error: "" }));
      setRenderState({ status: "complete", progress: 1, error: "" });
      return blob;
    } catch (error) { setRenderState({ status: "error", progress: 0, error: error.message }); throw error; }
  };

  const handleSaveVideo = async () => {
    trackCampaignEvent("travel_map_video_save_start", { duration_seconds: duration, journey_source: journey.source });
    try {
      const blob = await makeVideo();
      const extension = blob.type.includes("mp4") ? "mp4" : "webm";
      downloadBlob(blob, `mapmory-2026-travel-map.${extension}`);
      trackCampaignEvent("travel_map_video_saved", { format: extension, journey_source: journey.source, result: "download_started" });
    } catch (error) {
      trackCampaignEvent("travel_map_export_failed", { format: "video", journey_source: journey.source, error_type: "export_failed" });
      setRenderState({ status: "error", progress: 0, error: error?.message || "영상을 저장하지 못했어요. 다시 시도해주세요." });
    }
  };
  const handleShare = async () => {
    trackCampaignEvent("travel_map_share_click", { duration_seconds: duration, journey_source: journey.source });
    try {
      const blob = await makeVideo();
      const outcome = await shareVideo(blob);
      trackCampaignEvent("travel_map_share_result", { journey_source: journey.source, result: outcome === "downloaded" ? "download_started" : outcome });
      if (outcome === "cancelled") {
        setRenderState({ status: "idle", progress: 0, error: "" });
        return;
      }
      onNext();
    } catch (error) {
      trackCampaignEvent("travel_map_share_result", { journey_source: journey.source, result: "failed", error_type: "share_failed" });
      setRenderState({ status: "error", progress: 0, error: error?.message || "공유하지 못했어요. 다시 시도해주세요." });
    }
  };
  const handleSaveImage = async () => {
    trackCampaignEvent("travel_map_image_save_start", { journey_source: journey.source });
    try {
      const blob = await renderShareImage(playbackJourney, 1);
      downloadBlob(blob, "mapmory-2026-travel-map.png");
      trackCampaignEvent("travel_map_image_saved", { journey_source: journey.source, format: "png", result: "download_started" });
      setRenderState({ status: "complete", progress: 1, error: "" });
    } catch (error) {
      trackCampaignEvent("travel_map_export_failed", { journey_source: journey.source, format: "png", error_type: "export_failed" });
      setRenderState({ status: "error", progress: 0, error: error?.message || "이미지를 저장하지 못했어요. 다시 시도해주세요." });
    }
  };
  const handleAppInterest = () => {
    trackCampaignEvent("travel_map_app_bridge_click", { cta_placement: "recap", destination: "demand_screen", journey_source: journey.source });
    onNext();
  };
  const isRendering = renderState.status === "rendering";

  return (
    <section className="screen screen-recap" aria-labelledby="recap-title">
      <CampaignHeader onBack={onBack} />
      <div className="screen-content">
        <div className="recap-heading"><div><h1 id="recap-title">여행 영상 미리보기</h1><span>9:16</span></div><p>약 {duration}초 · 여행 밀도에 맞춰 자동 편집했어요.</p></div>
        <SharePreview journey={playbackJourney} duration={duration} />
        {isRendering && <div className="render-progress" role="status" aria-live="polite"><span style={{ width: `${renderState.progress * 100}%` }} /><p>영상 만드는 중 · {Math.round(renderState.progress * 100)}%</p></div>}
        {renderState.error && <p className="error-message">{renderState.error}</p>}
        <button className="primary-button" type="button" onClick={handleShare} disabled={isRendering}>{isRendering ? <CircleNotch className="button-spinner" size={20} /> : <ShareNetwork size={20} weight="bold" />}{isRendering ? "영상 만드는 중" : "영상으로 공유하기"}</button>
        <div className="secondary-actions"><button type="button" onClick={handleSaveVideo} disabled={isRendering}><VideoCamera size={18} /> 영상 저장</button><button type="button" onClick={handleSaveImage} disabled={isRendering}><DownloadSimple size={18} /> 지도 이미지 저장</button></div>
        <button className="app-bridge-button" type="button" onClick={handleAppInterest}>
          <span><small>MAPMORY APP</small><strong>Mapmory 앱 알아보기</strong></span>
          <ArrowRight size={19} weight="bold" />
        </button>
      </div>
    </section>
  );
}

function DemandScreen({ journeySource, onBack }) {
  useEffect(() => { trackCampaignEvent("travel_map_demand_view", { journey_source: journeySource }); }, [journeySource]);
  const handleAppVisit = () => {
    trackCampaignEvent("download_click", { cta_placement: "demand_primary", store: "google_play", journey_source: journeySource });
  };
  const handleLandingVisit = () => {
    trackCampaignEvent("travel_map_landing_click", { cta_placement: "demand_secondary", destination: "main_landing", journey_source: journeySource });
  };
  return (
    <section className="screen screen-demand" aria-labelledby="demand-title">
        <CampaignHeader onBack={onBack} />
      <div className="screen-content centered-content">
        <span className="campaign-badge">MAPMORY APP</span>
        <h1 id="demand-title">다음 여행의 기억은<br />Mapmory에 남겨보세요.</h1>
        <p className="lead-copy">Mapmory는 사진과 장소에 이야기를 기록하는 기억 지도 앱이에요. 바로 다운로드하거나, 설치하기 전에 서비스를 더 둘러보세요.</p>
        <div className="benefit-list">
          <div><span><MapPin size={20} weight="duotone" /></span><p><strong>장소마다 기억 남기기</strong><small>사진과 이야기를 지도 위에 기록해요.</small></p></div>
          <div><span><ImageSquare size={20} weight="duotone" /></span><p><strong>나만의 기억 지도</strong><small>흩어진 여행의 순간을 한눈에 모아봐요.</small></p></div>
        </div>
        <div className="app-destination-actions">
          <a className="primary-button app-conversion-link" href={APP_ACQUISITION_URL} target="_blank" rel="noreferrer" onClick={handleAppVisit}><DownloadSimple size={20} weight="bold" /> Google Play에서 바로 다운로드 <ArrowRight size={18} weight="bold" /></a>
          <a className="app-secondary-link" href={CAMPAIGN_LANDING_URL} target="_blank" rel="noreferrer" onClick={handleLandingVisit}>앱 설치 전 Mapmory 둘러보기 <ArrowRight size={16} weight="bold" /></a>
        </div>
        <p className="promise-note"><ShieldCheck size={18} /> 방금 만든 여행 영상 기능은 아직 앱에 포함되지 않았어요.</p>
      </div>
    </section>
  );
}

export function App() {
  const [screen, setScreen] = useState("entry");
  const [journey, setJourney] = useState(demoJourney);
  const [selectedCount, setSelectedCount] = useState(0);
  const [processingProgress, setProcessingProgress] = useState(0);
  const objectUrlsRef = useRef([]);
  const fallbackInputRef = useRef(null);
  useEffect(() => () => objectUrlsRef.current.forEach((url) => URL.revokeObjectURL(url)), []);

  const navigate = (nextScreen) => { setScreen(nextScreen); window.scrollTo({ top: 0, behavior: "smooth" }); };
  const runProcessing = async (analysisPromise, count, journeySource, minimumDelay = 1100) => {
    setSelectedCount(count); setProcessingProgress(8); navigate("processing");
    const startedAt = performance.now();
    const timer = window.setInterval(() => setProcessingProgress((value) => Math.min(92, value + (value < 42 ? 9 : value < 76 ? 6 : 2))), 180);
    try {
      const result = await analysisPromise;
      const remaining = Math.max(0, minimumDelay - (performance.now() - startedAt));
      if (remaining) await new Promise((resolve) => window.setTimeout(resolve, remaining));
      window.clearInterval(timer); setProcessingProgress(100); await new Promise((resolve) => window.setTimeout(resolve, 320));
      objectUrlsRef.current.forEach((url) => URL.revokeObjectURL(url)); objectUrlsRef.current = result.objectUrls ?? [];
      setJourney(result);
      if (result.points.length === 0) {
        trackCampaignEvent("travel_map_photo_analysis_empty", {
          journey_source: journeySource,
          picker_source: result.pickerSource,
          selected_photos: count,
          metadata_missing_photos: result.metadataMissingCount,
          read_failed_photos: result.readFailedCount,
          unsupported_photos: result.unsupportedCount,
        });
        navigate("empty");
        return;
      }
      trackCampaignEvent("travel_map_processing_complete", { journey_source: journeySource, picker_source: result.pickerSource, selected_photos: count, valid_gps_photos: result.validPhotoCount, duration_seconds: getAutoDuration(result) });
      navigate("replay");
    } catch (error) {
      window.clearInterval(timer);
      trackCampaignEvent("travel_map_processing_failed", { journey_source: journeySource, selected_photos: count, error_type: "analysis_failed" });
      setJourney({
        error: error?.message || "사진 처리 중 오류가 발생했어요. 다시 시도해주세요.",
        source: "photos",
        photoCount: count,
        supportedPhotoCount: 0,
        validPhotoCount: 0,
        missingGpsCount: 0,
        metadataReadCount: 0,
        metadataMissingCount: 0,
        parseFailedCount: count,
        gpsReadFailedCount: count,
        readFailedCount: count,
        unsupportedCount: 0,
        formats: [],
        points: [],
      });
      navigate("empty");
    }
  };
  const handleFiles = (files, pickerSource) => {
    trackCampaignEvent("travel_map_photo_select", { journey_source: "photos", selected_photos: files.length, picker_source: pickerSource });
    const analysisPromise = analyzePhotoFiles(files).then((result) => ({ ...result, pickerSource }));
    runProcessing(analysisPromise, files.length, "photos");
  };
  const handlePickFiles = async () => {
    const result = await pickOriginalPhotoFiles(window);
    if (result.status === "selected" && result.files.length > 0) {
      handleFiles(result.files, "file_system_access");
      return;
    }
    if (result.status === "unsupported" || result.status === "failed") {
      fallbackInputRef.current?.click();
    }
  };
  const handleFallbackFiles = (event) => {
    const files = event.target.files;
    if (files?.length) handleFiles(files, "legacy_input");
    event.target.value = "";
  };
  const handleDemo = () => { trackCampaignEvent("travel_map_demo_start", { journey_source: "demo" }); runProcessing(Promise.resolve(demoJourney), demoJourney.photoCount, "demo", 700); };

  return (
    <main className="campaign-page">
      <input ref={fallbackInputRef} className="visually-hidden" type="file" accept=".jpg,.jpeg,.heic,.heif,.png,.tif,.tiff,.avif,.webp" multiple onChange={handleFallbackFiles} />
      <div className="campaign-stage" data-screen={screen}>
        {screen === "entry" && <EntryScreen onPickFiles={handlePickFiles} onDemo={handleDemo} />}
        {screen === "processing" && <ProcessingScreen progress={processingProgress} selectedCount={selectedCount} />}
        {screen === "empty" && <EmptyScreen analysis={journey} selectedCount={selectedCount} onRetry={() => navigate("entry")} onRetryOriginals={handlePickFiles} onDemo={handleDemo} />}
        {screen === "replay" && <ReplayScreen journey={journey} onBack={() => navigate("entry")} onNext={() => navigate("recap")} />}
        {screen === "recap" && <RecapScreen journey={journey} onBack={() => navigate("replay")} onNext={() => navigate("demand")} />}
        {screen === "demand" && <DemandScreen journeySource={journey.source} onBack={() => navigate("recap")} />}
      </div>
      <p className="desktop-caption">사진에서 찾은 올해의 여행 · Mapmory 웹 스프린트</p>
    </main>
  );
}
