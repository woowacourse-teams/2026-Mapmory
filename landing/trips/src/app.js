import { formatBytes, formatNumber as n, isPhoto, MAX_ARCHIVE_BYTES, planArchives } from './organize.js';
import { createPhotoViewer } from './viewer.js';
import { groupByDate, localDay } from './dates.js';
import { validateTripSelection, excludeOversizedPhotos } from './selection.js';
import {organizePhotos} from './pipeline.js';
import {createTripReview} from './trip-review.js';
import {createAlbumResources,createPhotoAlbum} from './albums.js';
import {analyticsConfig} from './analytics-config.js';
import {createAnalytics,metadataSummary} from './analytics.js';
import {mountAnalyticsConsent} from './analytics-consent.js';
const analytics=createAnalytics({config:analyticsConfig});
mountAnalyticsConsent(analytics);
let processingStarted=0;
const processingSeconds=()=>Math.max(0,Math.round((performance.now()-processingStarted)/1000));
let readGeneration=0, tripReview=null;

const app = document.querySelector('#app');
const input = document.querySelector('#photo-input');
const originalInput = document.querySelector('#original-input');
const announcements = document.querySelector('#announcements');
const icons = {
  photo: '<rect x="3" y="3" width="18" height="18" rx="5"/><circle cx="8.5" cy="8.5" r="1.5"/><path d="m3 17 5-5 4 4 3-3 6 6"/>',
  folder: '<path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Z"/>',
  check: '<path d="m6 12 4 4 8-8"/>',
  download: '<path d="M12 3v12m-5-5 5 5 5-5M4 16v4h16v-4"/>',
  arrow: '<path d="m9 5 7 7-7 7"/>',
  lock: '<rect x="5" y="10" width="14" height="11" rx="3"/><path d="M8 10V7a4 4 0 0 1 8 0v3m-4 4v3"/>',
  info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v6m0-10v1"/>',
};
const state = { phase: 'idle', files: [], records: [], archives: [], worker: null, skipped: 0, oversized: 0, geoUnavailable: false, readyPart: -1, readyUrl: null, busyPart: -1, progress: 0, downloaded: new Set(), archivePromise: null, archiveReject: null };
const viewer = createPhotoViewer(index => state.files[index]);
const albumResources = createAlbumResources(index => state.files[index]);
function clearAlbums() { albumResources.clear(); }
function photoAlbum(options) { return createPhotoAlbum(options, {resources:albumResources,onOpen:(photos,index)=>viewer.open(photos,index)}); }

function el(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}
function icon(name) {
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('viewBox', '0 0 24 24'); svg.setAttribute('fill', 'none');
  svg.setAttribute('stroke', 'currentColor'); svg.setAttribute('stroke-width', '1.7'); svg.setAttribute('aria-hidden', 'true');
  svg.innerHTML = icons[name]; // Fixed, trusted icon paths only.
  return svg;
}
function button(text, action, className = 'button button-primary', iconName) {
  const node = el('button', className);
  node.type = 'button';
  if (iconName) node.append(icon(iconName));
  node.append(el('span', '', text)); node.addEventListener('click', action);
  return node;
}
function announce(text) { announcements.textContent = text; }
function focusHeading() { const heading = app.querySelector('h1'); if (heading) { heading.tabIndex = -1; heading.focus({ preventScroll: true }); } }
function releaseReady() {
  if (state.readyUrl) URL.revokeObjectURL(state.readyUrl);
  state.readyPart = -1; state.readyUrl = null;
}
function terminate() {
  state.worker?.terminate(); state.worker = null;
  state.archiveReject?.(new Error('작업을 취소했습니다.'));
  state.archiveReject = null; state.archivePromise = null;
}
function reset() {
  readGeneration++; tripReview=null;
  viewer.reset(); terminate(); releaseReady(); clearAlbums();
  Object.assign(state, { phase: 'idle', files: [], records: [], archives: [], busyPart: -1, progress: 0, skipped: 0, oversized: 0, geoUnavailable: false, downloaded: new Set() });
}
function choosePhotos() { analytics.track('trips_picker_open',{picker_type:'photos'}); input.value = ''; input.click(); }
function renderStart(message = '') {
  app.className = 'start-screen'; app.replaceChildren();
  const content = el('div', 'start-content');
  content.append(button('여행 사진 정리하기', choosePhotos, 'button button-primary start-button', 'photo'));
  content.append(button('선택이 안 되나요? 파일에서 원본 선택',()=>{analytics.track('trips_picker_open',{picker_type:'files'});originalInput.value='';originalInput.click();},'button button-secondary'));
  content.append(el('p','keep-open','한 번에 최대 1,000장 · 50MB를 넘는 사진은 제외하고 나머지를 정리해요.'));
  content.append(el('p','keep-open','실험 기준: /recap 사진 읽기 · iOS 웹 / 안드로이드 카톡에서 확인한 선택 경로를 이용해주세요. 선택 경로에서 빠진 GPS는 복구할 수 없어요.'));
  if (message) { const note = el('p', 'start-error', message); note.setAttribute('role', 'alert'); content.append(note); }
  app.append(content);
}
function privacyNote() {
  const note = el('p', 'privacy-note'); note.append(icon('lock'), el('span', '', '사진은 서버로 전송되지 않아요.')); return note;
}
function renderProgress() {
  app.className = 'processing-screen'; app.replaceChildren();
  const content = el('section', 'processing-content');
  content.append(el('div', 'eyebrow', '사진 정리 중'), el('h1', '', '사진의 정보를 읽고 있어요'));
  const percentage = el('div', 'percentage'); percentage.append(el('span', 'percentage-number', '0'), el('span', 'percentage-unit', '%'));
  percentage.setAttribute('aria-hidden', 'true'); content.append(percentage);
  const progress = el('progress', 'progress-bar'); progress.max = 100; progress.value = 0; progress.setAttribute('aria-label', '사진 분류 진행률');
  content.append(progress, el('p', 'progress-detail', `분류 기준 준비 중 · ${n(state.files.length)}장`));
  const steps = el('div', 'process-steps');
  for (const [i, label] of ['촬영 정보 읽기', '위치·날짜 분류', '폴더 완성'].entries()) {
    const step = el('span', 'process-step'); step.append(el('span', 'step-number', `${i + 1}`), document.createTextNode(label)); steps.append(step);
  }
  content.append(steps, el('p', 'keep-open', '완료될 때까지 이 화면을 열어 두세요.'), button('취소', () => { analytics.track('trips_processing_cancelled',{processing_seconds:processingSeconds()}); reset(); renderStart(); app.querySelector('button').focus(); }, 'button button-text'), privacyNote());
  app.append(content); focusHeading();
}
function progressUpdate(data) {
  state.progress = data.percent;
  app.querySelector('.percentage-number').textContent = String(data.percent);
  app.querySelector('progress').value = data.percent;
  app.querySelector('.progress-detail').textContent = data.phase === 'prepare' ? `분류 기준 준비 중 · ${n(data.total)}장` : `${n(data.completed)} / ${n(data.total)}장 정리 중`;
  if (data.completed === data.total || data.completed === 1) announce(`${n(data.total)}장 중 ${n(data.completed)}장 정리 중`);
}
function workerFor(onMessage, onError) {
  const worker = new Worker(new URL('./worker.js', import.meta.url), { type: 'module' });
  worker.addEventListener('message', ({ data }) => data.type === 'error' ? onError(new Error(data.message)) : onMessage(data));
  worker.addEventListener('error', event => { event.preventDefault(); onError(new Error('사진 처리 기능을 불러오지 못했어요. 연결을 확인하고 다시 시도해 주세요.')); });
  worker.addEventListener('messageerror', () => onError(new Error('사진 데이터를 읽지 못했어요. 사진을 나누어 다시 선택해 주세요.')));
  return worker;
}
async function startOrganization(files,pickerType) {
  const selectedPhotos = files.filter(isPhoto);
  const {photos, oversized} = excludeOversizedPhotos(selectedPhotos);
  const selection={picker_type:pickerType,selected_count:files.length,photo_count:photos.length,oversized_count:oversized.length,non_photo_count:files.length-selectedPhotos.length};
  analytics.track('trips_selection_received',selection);
  if (!photos.length) {
    analytics.track('trips_selection_rejected',{...selection,reason:oversized.length?'all_oversized':'no_photos'});
    const message = oversized.length ? `선택한 사진 ${n(oversized.length)}장이 모두 50MB를 넘어 제외됐어요. 50MB 이하 사진을 선택해주세요.` : '선택한 파일에서 사진을 찾지 못했어요. 사진 파일을 선택해 주세요.';
    if (!state.records.length) renderStart(message); else showResultError(message);
    return;
  }
  try { validateTripSelection(photos); }
  catch (error) { analytics.track('trips_selection_rejected',{...selection,reason:photos.length>1000?'too_many':'invalid_size'}); if (!state.records.length) renderStart(error.message); else showResultError(error.message); return; }
  reset();
  analytics.resetFlow({picker_type:pickerType,photo_count:photos.length,photo_bucket:photos.length>=500?'500_plus':'under_500',gps_coverage:'unknown',date_coverage:'unknown',evaluation_group:analytics.environment.environment_eligible?'pending':'android_non_kakao'});
  analytics.track('trips_processing_start',selection);
  processingStarted=performance.now();
  Object.assign(state, { phase: 'organizing', files: photos, skipped: files.length - selectedPhotos.length, oversized: oversized.length });
  renderProgress();
  const generation=readGeneration;
  const fail = () => { analytics.track('trips_processing_failed',{processing_seconds:processingSeconds()}); reset(); renderStart('사진을 처리하지 못했어요. 사진을 나누어 선택하거나 연결을 확인한 뒤 다시 시도해 주세요.'); };
  try {
    const data=await organizePhotos(state.files,{cancelled:()=>generation!==readGeneration,onProgress:progressUpdate});
    if(generation!==readGeneration)return;
    Object.assign(state,{phase:'complete',records:data.records,archives:planArchives(data.records),geoUnavailable:data.locationDataUnavailable,progress:100});
    const summary=metadataSummary(data.records,analytics.environment);
    analytics.setContext(summary);
    analytics.track('trips_processing_complete',{...summary,processing_seconds:processingSeconds(),geo_data_available:!data.locationDataUnavailable});
    renderTripSetup();focusHeading();announce(`${n(state.records.length)}장의 사진 분류를 마쳤어요.${state.oversized ? ` 50MB 초과 사진 ${n(state.oversized)}장은 제외했습니다.` : ''}`);
  } catch { if(generation===readGeneration)fail(); }
}

function buildTree(records) {
  const root = { children: new Map(), files: [], count: 0 };
  for (const record of records) {
    let node = root; node.count++;
    for (const segment of record.folder.split('/')) {
      if (!node.children.has(segment)) node.children.set(segment, { children: new Map(), files: [], count: 0 });
      node = node.children.get(segment); node.count++;
    }
    node.files.push(record);
  }
  return root;
}
function treeContents(tree) {
  const list = el('div', 'tree-children');
  for (const [name, child] of tree.children) {
    const details = el('details', 'folder-node');
    const summary = el('summary', 'folder-summary');
    summary.append(icon('arrow'), icon('folder'), el('span', 'folder-name', name), el('span', 'file-count', `${n(child.count)}장`));
    details.append(summary);
    details.addEventListener('toggle', () => {
      if (details.open && details.childElementCount === 1) details.append(treeContents(child));
    });
    list.append(details);
  }
  let shown = 0;
  const more = button('사진 더 보기', appendFiles, 'button button-text more-files');
  function appendFiles() {
    more.remove();
    for (const record of tree.files.slice(shown, shown + 60)) {
      const row = el('button', 'file-row'); row.type = 'button';
      row.setAttribute('aria-label', `${record.name} 크게 보기`);
      row.setAttribute('aria-haspopup', 'dialog');
      row.addEventListener('click', () => viewer.open(state.records, record.index));
      const copy = el('span', 'file-copy');
      copy.append(el('span', 'file-name', record.path.split('/').at(-1)), el('span', 'file-meta', `${record.date ? `${record.date.time} · ` : ''}${formatBytes(record.size)}`));
      row.append(icon('photo'), copy, el('span', 'file-open-label', '보기')); list.append(row);
    }
    shown += 60;
    if (shown < tree.files.length) { more.querySelector('span').textContent = `사진 더 보기 · ${n(tree.files.length - shown)}장 남음`; list.append(more); }
  }
  appendFiles();
  return list;
}
function renderResults() {
  clearAlbums();
  app.className = 'result-screen'; app.replaceChildren();
  const header = el('header', 'result-header');
  const badge = el('div', 'complete-badge'); badge.append(icon('check'), document.createTextNode('정리 완료'));
  header.append(badge, el('h1', '', '사진 속 장소를 찾았어요'), el('p', 'result-description', `${n(state.records.length)}장의 사진을 위치별로 모았어요.`));
  const nextStep=el('section','trip-next-step');
  nextStep.setAttribute('aria-label','여행 묶어서 보기');
  nextStep.append(button('여행 묶어서 보기',renderTripSetup,'button button-trip'),el('p','trip-next-description','위치·날짜 또는 촬영량으로 분류한 사진 묶음을 확인하세요.'));
  const summary = el('div', 'summary-grid');
  const places = new Set(state.records.filter(r => r.city).map(r => `${r.countryCode}:${r.cityId ?? r.city}`)).size;
  const dates = new Set(state.records.filter(r => r.date).map(r => r.date.day)).size;
  for (const [value, label] of [[`${n(places)}곳`, '도시·지역'], [`${n(dates)}일`, '촬영 날짜'], [formatBytes(state.records.reduce((sum, r) => sum + r.size, 0)), '원본 용량']]) {
    const item = el('div', 'summary-item'); item.append(el('strong', '', value), el('span', '', label)); summary.append(item);
  }
  app.append(header, nextStep, summary);
  const missingGps = state.records.filter(r => !r.gps).length;
  const missingDate = state.records.filter(r => !r.date).length;
  const readErrors = state.records.filter(r => r.readError).length;
  if (missingGps || missingDate || readErrors || state.skipped || state.oversized || state.geoUnavailable) {
    const notice = el('aside', 'notice'); notice.append(icon('info'));
    const text = el('div', 'notice-copy');
    if (state.oversized) text.append(el('p', '', `용량 초과로 제외한 사진 ${n(state.oversized)}장`), el('p', 'notice-detail', '50MB를 넘는 사진은 정리 결과와 ZIP에 포함되지 않아요. 원본 파일은 변경하지 않았어요.'));
    if (missingGps || missingDate) text.append(el('p', '', [missingGps && `위치 정보 없는 사진 ${n(missingGps)}장`, missingDate && `촬영일 없는 사진 ${n(missingDate)}장`].filter(Boolean).join(' · ')), el('p', 'notice-detail', '정보가 없는 사진은 별도 폴더에 모았어요. 휴대폰 사진 선택 과정에서 메타데이터가 빠질 수도 있어요.'));
    if (readErrors) text.append(el('p', 'notice-detail', `${n(readErrors)}장은 촬영 정보를 완전히 읽지 못했지만 원본은 포함했어요.`));
    if (state.skipped) text.append(el('p', 'notice-detail', `사진이 아닌 파일 ${n(state.skipped)}개는 제외했어요.`));
    if (state.geoUnavailable) text.append(el('p', 'notice-detail', '일부 지역명 자료를 불러오지 못했어요. 확인할 수 없는 곳은 정보 없음으로 분류했어요.'));
    notice.append(text); app.append(notice);
  }
  const locationSection=el('section','location-section');
  locationSection.append(el('p','flow-label','01 · 위치별로 모인 사진'));
  const locationView=el('details','tree-card location-card'); locationView.open=true;
  locationView.append(el('summary','folder-summary location-summary','위치별 폴더보기'),treeContents(buildTree(state.records)));
  locationSection.append(locationView);app.append(locationSection);
  const treeCard = el('details', 'tree-card date-card'); treeCard.setAttribute('aria-label', '날짜별 사진 보기');
  const treeHeader = el('summary', 'tree-header'); const rootName = el('div', 'tree-root-name'); rootName.append(icon('folder'), el('h2', '', '날짜별 사진 보기'));
  treeHeader.append(rootName, el('span', 'tree-hint', '촬영일 → 사진'));
  const dateTree={children:new Map(groupByDate(state.records).map(({day,photos})=>[day,{children:new Map(),files:photos,count:photos.length}])),files:[],count:state.records.length};
  treeCard.append(treeHeader);
  treeCard.addEventListener('toggle',()=>{if(treeCard.open&&treeCard.childElementCount===1)treeCard.append(treeContents(dateTree));});
  app.append(treeCard);
  app.append(el('p', 'viewer-hint', '사진 이름을 누르면 크게 볼 수 있어요.'));
  const regionNote = el('p', 'region-note', '도시는 GPS와 가까운 곳을 기준으로 찾아요. LA·라스베이거스는 인근 여행 권역을 함께 묶었어요. 도시를 찾지 못하면 ‘도시 정보 없음’으로 표시해요.');
  const credit = el('a', 'data-credit', '도시 자료: GeoNames');
  credit.href = 'https://www.geonames.org/'; credit.target = '_blank'; credit.rel = 'noopener noreferrer';
  const license = el('a', 'data-credit', 'CC BY 4.0');
  license.href = 'https://creativecommons.org/licenses/by/4.0/'; license.target = '_blank'; license.rel = 'noopener noreferrer';
  regionNote.append(document.createElement('br'), credit, document.createTextNode(' · '), license); app.append(regionNote);
  app.append(el('p','viewer-hint','여행 분류 체험 설문은 다음 단계의 여행 후보 결과에서 참여할 수 있어요.'));
  const downloads = el('section', 'downloads'); downloads.id = 'downloads'; downloads.setAttribute('aria-label', '정리한 사진 다운로드'); app.append(downloads);
  renderDownloads();
  app.append(button('다른 사진 정리하기', choosePhotos, 'button button-text restart-button'), privacyNote());
}

function tripImportNotice(){
  const box=el('aside','notice trip-import-notice');
  const gps=state.records.filter(r=>r.gps).length, dated=state.records.filter(r=>r.date).length;
  box.append(el('p','',`${n(state.records.length)}장 읽음 · 촬영일 ${n(dated)}장 · 위치 ${n(gps)}장`));
  if(state.oversized)box.append(el('p','',`50MB 초과 사진 ${n(state.oversized)}장은 제외했어요. 결과와 ZIP에 포함되지 않으며 원본은 변경하지 않았어요.`));
  if(state.skipped)box.append(el('p','notice-detail',`사진이 아닌 파일 ${n(state.skipped)}개는 제외했어요.`));
  const errors=state.records.filter(r=>r.readError).length;
  if(errors)box.append(el('p','notice-detail',`${n(errors)}장은 촬영 정보를 완전히 읽지 못했지만 원본은 보존했어요.`));
  if(state.geoUnavailable)box.append(el('p','notice-detail','지역명 자료 일부를 불러오지 못했어요. 좌표가 있으면 분류에 사용하지만 지역명은 표시되지 않을 수 있어요.'));
  return box;
}
function renderTripSetup(){
  tripReview ??= createTripReview({records:state.records,root:app,photoAlbum,clearAlbums,
    photoUrl:record=>albumResources.url(record),onBack:renderResults,onChoose:choosePhotos,
    createNotice:tripImportNotice,createSurvey,analytics,announce});
  tripReview.open();
}

function createSurvey() {
  const formUrl = 'https://docs.google.com/forms/d/e/1FAIpQLSc-T1vvHkTTN48DnbWPaDIhdFA1J_gU2RI977XgOGpoXPcK9A/viewform';
  const section = el('section', 'survey-card');
  section.setAttribute('aria-labelledby', 'survey-title');
  const header = el('div', 'survey-header');
  const title = el('h2', '', '여행 사진 자동 분류 체험 설문'); title.id = 'survey-title';
  const link = el('a', 'survey-link', '새 탭에서 설문 열기');
  link.href = formUrl; link.target = '_blank'; link.rel = 'noopener noreferrer';
  link.addEventListener('click',()=>analytics.track('trips_survey_click',{},'survey_click'));
  header.append(title, link);
  const frame = el('iframe', 'survey-frame');
  frame.title = '여행 사진 자동 분류 체험 설문 · Google Forms';
  frame.src = `${formUrl}?embedded=true`;
  frame.loading = 'lazy'; frame.referrerPolicy = 'no-referrer';
  section.append(header, el('p', 'survey-note', '설문 참여는 선택이에요. 응답은 Google Forms로 제출되며, 선택한 사진과 위치 정보는 설문에 자동으로 전달되지 않아요.'), frame);
  return section;
}
function showResultError(message) {
  let alert = app.querySelector('.result-error');
  if (!alert) { alert = el('p', 'result-error'); alert.setAttribute('role', 'alert'); (app.querySelector('#downloads') ?? app).append(alert); }
  alert.textContent = message;
}
function archiveName(index) {
  const day = localDay(new Date());
  return `정리한_사진_${day}${state.archives.length > 1 ? `_${String(index + 1).padStart(2, '0')}` : ''}.zip`;
}
function renderDownloads() {
  const container = app.querySelector('#downloads'); if (!container) return;
  container.replaceChildren();
  if (state.archives.length > 1) container.append(el('h2', 'downloads-title', '나누어 다운로드'), el('p', 'download-explanation', `휴대폰에서 안정적으로 저장할 수 있도록 ${formatBytes(MAX_ARCHIVE_BYTES)}씩 나눴어요. 각 파일을 모두 저장해 주세요.`));
  state.archives.forEach((archive, index) => {
    const row = el('div', 'download-row');
    const info = el('div', 'download-info');
    const name = archive.kind === 'original' ? archive.records[0].name : state.archives.length > 1 ? `ZIP ${String(index + 1).padStart(2, '0')}` : '정리한 사진.zip';
    info.append(el('strong', '', name), el('span', '', `${n(archive.records.length)}장 · ${formatBytes(archive.size)}${state.downloaded.has(index) ? ' · 저장 요청됨' : ''}`)); row.append(info);
    if (state.busyPart === index) {
      const busy = el('div', 'archive-progress'); busy.setAttribute('role', 'status');
      busy.append(el('span', '', `ZIP 만드는 중 · ${state.progress}%`));
      const bar = el('progress', 'progress-bar'); bar.max = 100; bar.value = state.progress; bar.setAttribute('aria-label', `ZIP ${index + 1} 생성 진행률`); busy.append(bar); row.append(busy);
    } else if (state.readyPart === index && state.readyUrl) {
      const link = el('a', 'button button-primary save-link');
      link.href = state.readyUrl; link.download = archive.kind === 'original' ? archive.records[0].path.split('/').at(-1) : archiveName(index);
      link.append(icon('download'), el('span', '', archive.kind === 'original' ? '원본 저장하기' : 'ZIP 저장하기'));
      link.addEventListener('click', () => { analytics.track('trips_download_request',{archive_photo_count:archive.records.length},`download_${index}`); state.downloaded.add(index); info.lastChild.textContent = `${n(archive.records.length)}장 · ${formatBytes(archive.size)} · 저장 요청됨`; announce('브라우저에 저장을 요청했어요. 다운로드 목록을 확인해 주세요.'); });
      row.append(link);
    } else {
      const download = button(archive.kind === 'original' ? '원본 다운로드' : 'ZIP 다운로드', () => { void prepareArchive(index).catch(() => {}); }, 'button button-primary', 'download');
      download.disabled = state.busyPart >= 0;
      row.append(download);
    }
    if (archive.kind === 'original') row.append(el('p', 'large-file-note', `큰 파일은 압축 없이 원본으로 저장해요. 분류 위치: ${archive.records[0].folder}`));
    container.append(row);
  });
  if (state.readyPart >= 0) container.append(el('p', 'ready-hint', '파일이 준비됐어요. 저장하기를 눌러 내려받으세요.'));
  if (state.busyPart >= 0) container.append(button('ZIP 만들기 취소', () => { analytics.track('trips_archive_cancelled'); terminate(); state.busyPart = -1; state.phase = 'complete'; renderDownloads(); }, 'button button-text'));
}
async function prepareArchive(index) {
  if (state.phase !== 'complete' || !Number.isInteger(index) || !state.archives[index] || state.busyPart >= 0) throw new Error('지금은 이 파일을 준비할 수 없습니다.');
  if (state.readyPart === index) return { part: index + 1, status: 'ready' };
  releaseReady();
  const archive = state.archives[index];
  analytics.track('trips_archive_start',{archive_photo_count:archive.records.length});
  if (archive.kind === 'original') {
    state.readyUrl = URL.createObjectURL(state.files[archive.records[0].index]); state.readyPart = index;
    renderDownloads(); analytics.track('trips_archive_ready',{archive_photo_count:archive.records.length}); return { part: index + 1, status: 'ready', kind: 'original' };
  }
  Object.assign(state, { busyPart: index, progress: 0, phase: 'archiving' }); renderDownloads();
  const promise = new Promise((resolve, reject) => {
    state.archiveReject = reject;
    const fail = () => {
      analytics.track('trips_archive_failed');
      state.worker?.terminate(); state.worker = null; state.archiveReject = null;
      Object.assign(state, { busyPart: -1, phase: 'complete' }); renderDownloads();
      showResultError('ZIP을 만들지 못했어요. 다시 시도하거나 사진을 더 적게 선택해 주세요.');
      reject(new Error('ZIP 생성에 실패했습니다.'));
    };
    try {
      state.worker = workerFor(data => {
        if (data.type === 'zip-progress') {
          state.progress = data.percent;
          const progress = app.querySelector('.archive-progress');
          if (progress) { progress.querySelector('span').textContent = `ZIP 만드는 중 · ${data.percent}%`; progress.querySelector('progress').value = data.percent; }
        }
        if (data.type === 'zip-ready') {
          state.worker?.terminate(); state.worker = null; state.archiveReject = null;
          Object.assign(state, { readyUrl: URL.createObjectURL(data.blob), readyPart: index, busyPart: -1, phase: 'complete' }); renderDownloads();
          announce('ZIP 파일이 준비됐어요. 저장하기를 눌러 내려받으세요.');
          analytics.track('trips_archive_ready',{archive_photo_count:archive.records.length});
          app.querySelector('.save-link')?.focus({ preventScroll: true });
          resolve({ part: index + 1, status: 'ready', kind: 'zip' });
        }
      }, fail);
      state.worker.postMessage({ type: 'zip', entries: archive.records.map(record => ({ record, file: state.files[record.index] })) });
    } catch { fail(); }
  });
  state.archivePromise = promise;
  return promise;
}

renderStart();
input.addEventListener('change', () => { const files = Array.from(input.files ?? []); if (files.length) startOrganization(files,'photos'); });
originalInput.addEventListener('change', () => { const files = Array.from(originalInput.files ?? []); if (files.length) startOrganization(files,'files'); });
window.addEventListener('beforeunload', event => {
  if (state.files.length) { event.preventDefault(); event.returnValue = ''; }
});
window.addEventListener('pagehide', event => { if (!event.persisted) { terminate(); releaseReady(); clearAlbums(); } });

// Optional agent interface. It cannot access a phone's library or open a picker.
const context = document.modelContext;
if (context?.registerTool) {
  const lifecycle = new AbortController();
  const tools = [
    { name: 'get_photo_organization_status', title: '사진 정리 상태 확인', description: 'Return counts and progress for photos the user has already selected. No file contents or coordinates are returned.', inputSchema: { type: 'object', properties: {}, additionalProperties: false }, annotations: { readOnlyHint: true }, execute(value) { if (!value || Object.keys(value).length) throw new Error('Expected an empty object'); return { phase: state.phase, progress: state.progress, photos: state.files.length, parts: state.archives.length, readyPart: state.readyPart >= 0 ? state.readyPart + 1 : null }; } },
    { name: 'prepare_photo_archive', title: '사진 ZIP 준비', description: 'Prepare one download part for already organized photos. Updates the visible download area; the user must tap Save to download.', inputSchema: { type: 'object', properties: { part: { type: 'integer', minimum: 1 } }, required: ['part'], additionalProperties: false }, annotations: { readOnlyHint: false }, async execute(value) { if (!value || Object.keys(value).some(key => key !== 'part') || !Number.isInteger(value.part) || value.part < 1) throw new Error('A positive integer part is required'); return prepareArchive(value.part - 1); } },
  ];
  for (const tool of tools) { try { Promise.resolve(context.registerTool(tool, { signal: lifecycle.signal })).catch(() => {}); } catch {} }
  window.addEventListener('pagehide', event => { if (!event.persisted) lifecycle.abort(); });
}
