# Mapmory video renderer

여행 기록을 세로형 SNS 공유 영상으로 렌더링하는 Remotion 프로젝트입니다.

## 실행

```bash
npm install
npm run generate:map
npm run generate:coordinates
npm run dev
```

Studio에서 `TravelTimeline` Composition을 선택하면 실제 지구본 이동 영상을 확인할 수 있습니다.

```bash
# 샘플 MP4 생성: out/travel-timeline.mp4
npm run render:sample

# 백엔드 스냅샷 형태의 예제 JSON으로 MP4 생성
npm run render:fixture

# 입력·출력 경로를 지정해 MP4 생성
npm run render:worker -- ./fixtures/travel-timeline-snapshot.json ./out/custom.mp4

# 스타일을 지정해 MP4 생성 (CLASSIC, ZOOM, ORBIT, HORIZON)
npm run render:worker -- ./fixtures/travel-timeline-snapshot.json ./out/horizon.mp4 HORIZON
```

스타일 생략 시 `CLASSIC`을 사용합니다. 스타일은 각각 `TravelTimeline`, `TravelTimelineZoom`, `TravelTimelineOrbit`, `TravelTimelineHorizon` 구성으로 연결됩니다.

## 백엔드 스냅샷

백엔드는 좌표를 제외한 다음 JSON을 `share_video_job.snapshot_json`에 저장합니다.

```json
{
  "version": 1,
  "stops": [
    {
      "countryCode": "KR",
      "countryName": "대한민국",
      "visits": [
        {
          "visitId": 101,
          "startDate": "2024-03-12",
          "endDate": "2024-03-14"
        },
        {
          "visitId": 102,
          "startDate": "2024-04-20",
          "endDate": null
        }
      ]
    }
  ]
}
```

- `countryCode`: ISO 3166-1 alpha-2 국가 코드
- `countryName`: 영상 하단에 표시할 이름
- `visits`: 해당 정류장에 포함된 원본 여행 기록과 날짜
- 최소 두 개의 방문지가 필요합니다.

## 렌더 입력 변환

`createRenderInput`이 백엔드 스냅샷을 검증하고 각 국가 코드의 대표 좌표를 추가합니다.

```text
snapshot.json 읽기
→ version, stops, visits 검증
→ countryCode로 대표 좌표 조회
→ Remotion inputProps 검증
→ MP4 렌더링
```

등록되지 않은 국가 코드가 들어오면 렌더링을 시작하지 않고 오류를 반환합니다.

### 날짜 표시 규칙

- 방문 기록이 하나이고 종료일이 없으면 시작일만 표시합니다.
- 방문 기록이 하나이고 종료일이 있으면 `시작일 – 종료일`로 표시합니다.
- 방문 기록이 여러 개면 `첫 번째 시작일 외 N회`로 표시합니다.
- 연도 타임라인은 각 정류장의 첫 번째 시작일을 사용합니다.

예시의 대한민국 날짜 문구는 `2024. 03. 12 외 1회`입니다.

### 동일 국가가 연속되는 경우

- 백엔드는 연속된 국내 지역 기록을 대한민국 정류장 하나로 합칩니다.
- 다른 국가는 방문 기록마다 별도 정류장으로 유지합니다.
- 같은 외국이 연속되면 비행기와 경로는 표시하지 않고 국가명과 날짜만 전환합니다.

## 프로젝트 데이터 연결

- 지도 원본: Android의 `GeneratedWorldMapDataPart*.kt`
- 지도 JSON 생성: `scripts/generate-world-map.mjs`
- 백엔드 상세 응답: 국가 코드·이름과 시작일·종료일 사용 가능
- 백엔드 목록 응답: 현재 `regionName`만 있어 국가 코드가 부족함
- 백엔드 스냅샷에는 좌표를 저장하지 않습니다.
- `country-coordinates.ts`는 세계지도 데이터에서 국가별 가장 큰 폴리곤의 안쪽 대표점을 계산해 생성합니다.
- 지도 원본을 갱신하면 `npm run generate:coordinates`로 대표 좌표도 다시 생성합니다.
- `src/render/create-render-input.ts`가 스냅샷에 대표 좌표를 보강합니다.

운영 단계에서는 백엔드 작업 실행기가 저장된 스냅샷을 이 렌더 명령에 전달해야 합니다.
