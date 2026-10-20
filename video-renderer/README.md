# Mapmory video renderer

여행 기록을 세로형 SNS 공유 영상으로 렌더링하는 Remotion 프로젝트입니다.

## 실행

```bash
npm install
npm run generate:map
npm run dev
```

Studio에서 `TravelTimeline` Composition을 선택하면 실제 지구본 이동 영상을 확인할 수 있습니다.

```bash
# 샘플 MP4 생성: out/travel-timeline.mp4
npm run render:sample

# 서버 코드에서 사용하는 Renderer API 경로 확인
npm run render:worker
```

## 입력 데이터

```json
{
  "stops": [
    {
      "countryCode": "KR",
      "countryName": "대한민국",
      "date": "2024. 03. 12",
      "longitude": 126.978,
      "latitude": 37.5665
    }
  ]
}
```

- `countryCode`: ISO 3166-1 alpha-2 국가 코드
- `countryName`: 영상 하단에 표시할 이름
- `date`: 영상 하단 및 연도 타임라인에 표시할 날짜
- `longitude`, `latitude`: 이동 경로와 카메라 중심 계산용 좌표
- 최소 두 개의 방문지가 필요합니다.

## 프로젝트 데이터 연결

- 지도 원본: Android의 `GeneratedWorldMapDataPart*.kt`
- 지도 JSON 생성: `scripts/generate-world-map.mjs`
- 백엔드 상세 응답: 국가 코드·이름과 시작일·종료일 사용 가능
- 백엔드 목록 응답: 현재 `regionName`만 있어 국가 코드가 부족함
- 좌표: 현재 여행 기록 응답에 없으므로 렌더 요청을 만들 때 국가 대표 좌표를 조회하거나 별도 스냅샷에 넣어야 함

운영 단계에서는 영상 생성 요청 시점의 방문지 목록을 스냅샷으로 저장하고, 그 스냅샷을 이 렌더러의 `inputProps`로 전달합니다.
