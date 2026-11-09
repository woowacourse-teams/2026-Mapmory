> 2026-11-09: `/trips/` 실험을 삭제하면서 `landing/trips/MEASUREMENT_PLAN.md`를 여기로 옮겼다. 그 전 기간 `trips_*` 데이터를 읽기 위한 기록이다.

# Trips 실험 측정 계획

2026-09-23 · 읽기 전용 분류 결과 · `surface=trips` · `analytics_schema_version=3` · `experiment_version=trips_v2`

v1의 생활 지역 선택 퍼널과 직접 합산하지 않는다. main 리뷰 후 별도 release PR로 배포하며,
배포 성공과 운영 GA 수집 활성화는 별개다. 현재 GA 수집은 OFF다.

## 무엇을 판단하는가

설문 미응답자의 행동도 참고하되 **사용 행동을 만족도로 바꾸어 해석하지 않는다.**
질문은 (1) 사진을 가져올 수 있는가, (2) 메타데이터가 얼마나 읽히는가,
(3) 자동 분류된 후보를 펼쳐보고 설문으로 이동하는가이다. 편집·확정 단계는 없다.
ZIP은 위치·날짜 폴더를 내보내며 여행 후보를 내보내는 기능이 아니다.

### 분모와 환경 제외 기준

통계를 허용하고 차단 도구 등으로 수집이 막히지 않은 **측정 가능한 참여자**만 관찰한다.
동의 전 이벤트는 쌓거나 재전송하지 않는다. 따라서 전체 방문자 수나 전체 참여자의
만족도를 대표한다고 단정할 수 없다. GA 사용자는 쿠키/브라우저 기준이며 실제 고유 인원과
일치하지 않는다. 같은 사람이 크롬에서 카카오톡으로 옮기면 별도 사용자로 잡힐 수 있다.

- 공통: `surface=trips`, `experiment_version=trips_v2`, `traffic_type=external`.
- **우테코 참여자도 외부 실험 참여자**다. `internal`은 개발팀 QA에만 사용한다.
- 사용자의 실험 기준에 따라 Android + 비카카오톡 환경은
  `environment_group=android_non_kakao`로 모든 단계에서 분리한다.
  사진이 실제로 읽혔더라도 이번 제품 효과 평가의 분모/분자에서는 제외한다.
  접속 및 누락 진단에는 포함해 제외 규모를 공개한다. 앱 사용 자체를 막지는 않는다.
- 나머지는 `environment_group=standard`. 정상 동작 보장이 아니라 이번 제외 규칙의 반대다.
  OS/브라우저는 UA에서 Android/iOS/other, Kakao/Slack/Chrome/Safari/other 범주만 추정한다.
  UA가 숨겨지거나 변경되면 오분류될 수 있다. 원문 UA는 자체 이벤트에 넣지 않는다.
- 읽기 완료 시 날짜와 GPS가 **같은 사진에 하나 이상** 있어야
  `evaluation_group=eligible`. 둘을 갖춘 사진이 0장이지만 날짜가 있으면 `date_only`,
  날짜도 없으면 `no_usable_metadata`. 날짜 전용 경로는 위치 기반 경로와 별도 평가한다.
  Android 비카카오톡의 평가 그룹은 항상 `android_non_kakao`.
  처리 시작 시에는 아직 모르므로 `pending`이다.
- 500장 실험은 실제 처리한 수 기준 `photo_bucket=500_plus`로 따로 본다.
  50MB 초과로 제외한 사진은 이 수에 포함하지 않는다.

### 우선 확인할 지표

| 질문 | 계산 / 해석 |
|---|---|
| 위치정보가 전혀 없는 사람의 비율 | `trips_processing_complete`에서 `gps_coverage=none`을 경험한 총 사용자 / 같은 기간 읽기 완료 총 사용자. `some`(일부), `all`(전부)도 나란히 확인 |
| 촬영시간도 없는가 | 위와 동일하게 `date_coverage=none/some/all`로 비교. 시간 있음·위치 없음은 둘 다 없음과 구분 |
| 환경 탓으로 제외되는 규모 | 비카카오톡 Android 읽기 완료 사용자 / 전체 읽기 완료 사용자. 전체 진단과 `standard`만의 누락률을 모두 제시 |
| 어떤 근거로 후보를 만들 수 있는가 | 읽기 완료 중 eligible / date_only / no_usable_metadata 분포. 날짜만 있어도 촬영량 비교가 충분하다는 뜻은 아님 |
| 처리 성공·실패·취소 | 시작 / 완료 / 실패 / 명시적 취소 이벤트 건수 비교. 새로고침·창 닫기는 원인을 알 수 없는 미완료이며 강제로 취소 이벤트를 만들어내지 않음 |
| 제안 이후 무엇을 하는가 | 읽기 완료 후 grouping_start/results_view는 자동 표시 단계이며 의도적 클릭이 아니다. 이후 album_open(`album_kind=trip`)과 survey_click을 능동 행동으로 본다. eligible/date_only와 환경/500장 조건을 분리 |
| 얼마나 기다렸는가 | `processing_seconds`의 분포. 실제 대기 경과 시간으로 백그라운드 정지 영향도 포함. 적극 이용 시간이나 만족도 아님 |
| 결과를 활용하려 하는가 | `trips_download_request` 총 사용자. 브라우저에 저장을 요청한 것이며 파일 저장 완료 아님 |
| 설문으로 이동하려 하는가 | `trips_survey_click` 총 사용자. 새 탭 링크 클릭만 측정. iframe 내부 응답 및 제출 완료는 모름 |

**재시도 주의:** 한 참여자가 재선택하여 `none`과 `all`을 모두 경험할 수 있다.
위 사람 기준 비율은 ‘한 번이라도 해당 상태를 경험한 사용자’이며 세 비율의 합이 100%가
아닐 수 있다. 합계 100%인 분포가 필요하면 읽기 완료 **이벤트 건수** 기준으로 별도 제시한다.
둘을 같은 표에서 사람 수처럼 섞지 않는다. 소수 표본은 분자/분모도 함께 제시한다.
메타데이터 진단의 분모를 `eligible`로 잡으면 누락자가 빠지므로 반드시 완료 전체에서 계산한다.
‘GPS 없음’은 원본에 없었는지, 선택 앱이 제거했는지, 파서가 못 읽었는지 단독으로 증명하지 않는다.

## 이벤트 계약

사진/파일별 반복 이벤트는 만들지 않는다. 아래 이름 밖의 이벤트나 키, 허용되지 않은 값은
코드에서 버린다. 건수는 유한한 정수, 시간은 유한한 초만 받는다. 에러는 원문을 보내지 않는다.
공통 필드: `surface`, `analytics_schema_version`, `experiment_version`, `traffic_type`,
`platform`, `browser_context`, `environment_group`.
정리 시작 이후: `picker_type`, `photo_count`, `photo_bucket`, `gps_coverage`, `date_coverage`,
`evaluation_group`. 완료 이후 메타데이터 상태가 갱신된다.
새 파일 선택 이벤트에는 이전 결과의 메타데이터 상태를 붙이지 않는다.

| 이벤트 | 실제 발화 시점 / 고유 파라미터 | 중복 기준 |
|---|---|---|
| `page_view` | 통계 허용 후 처음 태그 초기화 | 페이지 로드당 1회. 결과 화면 전환은 새 page_view 아님 |
| `trips_picker_open` | 사진/원본 파일 버튼 클릭 · picker_type=photos/files | 클릭마다. 시스템 선택 창이 실제 열렸음을 보장하지 않음 |
| `trips_selection_received` | 비어 있지 않은 FileList 반환 · selected_count, photo_count, oversized_count, non_photo_count, picker_type | 반환마다 |
| `trips_selection_rejected` | 전체 제외/선택 오류 · 위 건수 + reason=no_photos/all_oversized/too_many/invalid_size | 거절마다. 기존 결과는 유지 |
| `trips_processing_start` | 유효한 선택으로 처리 시작 · 위 선택 건수 | 처리 시도당 1회 |
| `trips_processing_complete` | 메타데이터 읽기 성공, 후보 표시 직전 · processing_seconds, gps_count, dated_count, usable_count, read_error_count, geo_data_available | 시도당 1회. 결과 화면 재방문으로 재발화 안 함 |
| `trips_processing_failed` | 현재 시도 처리 실패 · processing_seconds | 실패 시. 취소 후 늦게 끝난 작업은 무시 |
| `trips_processing_cancelled` | 사용자가 읽기 취소 버튼 클릭 · processing_seconds | 취소 시 |
| `trips_grouping_start` | 후보 검토 화면 진입 · grouping_mode=location/mixed/pattern | 유효한 사진 선택당 첫 진입 |
| `trips_results_view` | 최초 자동 제안 표시 · grouping_mode, candidate_count, candidate_photo_count, other_photo_count | 사진 선택당 1회. 후보 0개도 기록 |
| `trips_album_open` | 앨범 펼침 · album_kind=trip/other, album_photo_count, classification_method | 사진 선택당 종류별 최초 1회. 모든 후보 근거의 분포를 대표하지 않음 |
| `trips_archive_start` / `trips_archive_ready` | ZIP/원본 준비 시작 및 준비 완료 · archive_photo_count | 준비 시도별. 이미 준비된 항목 요청은 재발화 안 함 |
| `trips_archive_failed` / `trips_archive_cancelled` | 생성 실패 / 명시적 생성 취소 | 실제 실패/취소 시 |
| `trips_download_request` | 준비된 파일의 저장 링크 클릭 · archive_photo_count | 사진 선택당 분할 파일별 1회. 경로나 파일명/인덱스는 전송 안 함 |
| `trips_survey_click` | 새 탭 설문 링크 클릭 | 사진 선택당 1회 |

`picker_type`은 웹의 두 입력 버튼을 구분할 뿐 실제 OS에서 고른 앱 이름을 뜻하지 않는다.
사진 수는 처리 대상으로 남은 수다. selected_count는 사진 외/용량 초과도 포함한 반환 수다.

결과 집계는 grouping_mode, candidate_count, candidate_photo_count, other_photo_count다.
candidate_count는 위치·시간·촬영량 후보 수이며 실제 여행 수나 성공 건수가 아니다.
날짜만으로 남은 사진과 촬영일 없는 사진은 단일 보류 앨범으로 묶고 other_photo_count에 포함한다.
grouping_mode=pattern은 GPS 기준 사진이 없는 경로이며, 충분한 기록으로 추정에 성공했다는 뜻은 아니다.
classification_method는 location/time_assisted/time/pattern/date/combined이며 사진별 근거를 합친 값이다.
편집·확정 이벤트는 현재 UI에서 발화하지 않는다. 후보 표시가 사용자 확정이나 만족도를 뜻하지 않는다.
Android 비카카오톡 제외는 날짜 전용 추정에도 그대로 유지한다. 추정 경로 추가를 이유로 평가에 재포함하지 않는다.

## 모집 링크

- 우테코: https://map-mory.com/trips/?utm_source=wooteco&utm_medium=community&utm_campaign=trips_test
- 에브리타임: https://map-mory.com/trips/?utm_source=everytime&utm_medium=community&utm_campaign=trips_test
- 개발팀 QA: https://map-mory.com/trips/?internal=1
- QA 표시 해제: https://map-mory.com/trips/?internal=0

UTM은 GA4의 **세션 소스/매체, 세션 캠페인**으로 비교한다. QA 표시는 동일 출처에서
기존 landing/Recap과 공유한다. 위 QA 링크는 참가자에게 배포하지 않는다.
허용된 UTM(source 두 가지, medium=community, campaign=trips_test, 선택 content=post_1/post_2)만
page_location에 남기며 다른 query/hash와 referrer 경로는 제거한다. 다른 모집 캠페인을
추가하려면 `safePageLocation` 허용 목록과 이 계획을 함께 수정한다.

## GA4 콘솔 설정 — 별도 작업, 코드 변경만으로 완료되지 않음

기존 공개 웹 스트림 `G-MC93CZWLZF` / Mapmory Landing Page(속성 551158914)를 재사용한다.
빌드가 기존 파이프라인의 `VITE_GA_MEASUREMENT_ID`를 명시적으로 읽는다. 추측한 ID fallback은 없다.
현재는 **운영 수집 OFF**: 공개 태그에서 자동 폼/링크/다운로드 측정이 활성화된 것을 확인했고,
콘솔은 로그아웃 상태다. `scripts/analytics-config.mjs`의 `TRIPS_GA_RELEASE_APPROVED=false`가
운영 ID를 비워 방문자에게 태그가 실행되지 않게 한다. UI 개선 배포와 GA 활성화를 구분한다.
콘솔 확인 및 필요한 설정 조정 후 별도 리뷰 변경으로 활성화하고 실제 수신을 확인한다.
이 상태에서 모집하면 설문은 가능하지만 미응답자의 GA 행동 데이터는 수집되지 않는다.

1. 맞춤 정의에 이벤트 범위 차원을 필요한 것만 등록:
   `surface`, `experiment_version`, `traffic_type`, `platform`, `browser_context`,
   `environment_group`, `picker_type`, `photo_bucket`, `gps_coverage`, `date_coverage`,
   `evaluation_group`, `album_kind`, `reason`, `grouping_mode`, `classification_method`. 기존 정의가 있으면 재사용.
2. 수치 분석이 필요하면 `processing_seconds`(초), `photo_count`, `gps_count`,
   `dated_count`, `usable_count`, `candidate_count`를 이벤트 범위 맞춤 측정항목으로 등록.
3. 자유 형식 탐색에 완료 이벤트의 총 사용자/이벤트 수와 GPS·날짜 coverage를 두고,
   환경 그룹과 세션 소스/매체로 나눈다. 퍼널 탐색은 위 단계/조건으로 별도로 만든다.
4. 기존 앱 다운로드 key event는 유지한다. 후보 펼침은 행동 지표이며 만족도 전환이 아니다.
   새 key event/영구 필터/보존 기간/광고 설정을 임의로 바꾸지 않는다.
5. 배포 전 공유 스트림의 **향상된 측정** 설정을 확인한다. 자동 파일 다운로드/폼/외부 링크
   수집이 사진 파일명·DOM 텍스트·blob URL 등을 수집하지 않는지 실제 요청을 검사한다.
   자체 이벤트의 허용 목록은 Google의 자동 이벤트까지 제어하지 못한다.
   위험한 자동 수집이 켜져 있다면 기존 landing/Recap 영향을 고려해 소유자와 별도 스트림 또는
   설정 조정을 결정한 뒤 활성화한다. 아직 이 계정 설정은 확인/변경하지 않았다.

## 개인정보 및 검증

통계 선택 안내는 모달이 아니며 ‘허용 안 함’도 동등하게 제공한다. 기본 거절 상태에서
태그를 로드하지 않는 basic consent 방식이다. 동의 전/거절 후 사용자 정의 이벤트 없음,
동의 전 이벤트 소급 전송 없음. 설정 변경으로 이후 수집 중단 가능. 이미 수집된 데이터의
삭제는 별도 절차이고, 거절 버튼이 과거 수집 데이터를 지우는 기능은 아니다.
Google Signals/광고 개인화는 이 페이지 태그에서 사용하지 않는다. GA 자체 쿠키/브라우저
식별자를 사용하므로 ‘완전 익명’이라고 표현하지 않는다. 새로운 user_id는 만들지 않는다.

테스트 범위: consent, host gate, UA 환경 분리, 누락 집계, 파라미터 유출 방지,
재선택 중복, 공개 빌드 설정, 기존 파서/분류/ZIP/라우트 회귀.
실제 휴대폰 재검증 및 GA DebugView/Realtime 수신은 별도 확인이다.
이전 v1 검증 기록: Trips 52개 테스트 통과, 공개 GA 설정을 넣은 정적 빌드 통과.
기존 landing 빌드 및 회귀 테스트도 통과(83 통과, Linux 전용 24개는 Windows에서 제외).
390×844 브라우저에서 통계 거절/허용/철회 UI와 오류 로그 없음 확인.
브라우저 검증은 `node tests/browser-preview.mjs`의 **로컬 GA 대역**을 사용했다.
운영 GA 수신을 확인한 것이 아니며, 이 대역 서버는 배포 파일에 포함되지 않는다.
로컬에서는 기본 미수집. QA build만 `VITE_GA_CAPTURE_LOCAL=true`, `VITE_GA_DEBUG=true`를
명시하고 QA 표시를 유지한다. 운영 빌드에는 이 두 옵션을 켜지 않는다.

v2 읽기 전용 결과 로컬 검증: Trips 64개 테스트 통과. 1,000장 누락·중복 방지,
부분 GPS 시간 연결, 촬영량 자동 추정/근거 부족, 편집 컨트롤 제거, 단일 보류 앨범,
개인정보 허용 목록을 포함한다. 모바일 너비에서 앨범이 첫 화면에 보이고 펼쳐지는지 확인했다.
합성 데이터 검증이며 실제 사용자 정확도나 운영 GA 수신을 검증한 것은 아니다.

공식 참고: [Consent mode](https://developers.google.com/tag-platform/security/guides/consent),
[GA4 config](https://developers.google.com/analytics/devguides/collection/ga4/reference/config),
[PII 전송 방지](https://support.google.com/analytics/answer/6366371?hl=en).
