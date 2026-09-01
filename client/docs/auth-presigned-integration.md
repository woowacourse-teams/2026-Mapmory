# 인증 갱신과 이미지 조회 계약

## Access Token 갱신

- 보호 API가 `401 Unauthorized`를 반환하면 당시 요청에 사용한 Access Token을 기준으로
  Refresh Token 회전을 한 번 수행한다.
- 동시에 여러 요청이 401을 받아도 같은 Refresh Token은 한 번만 사용한다.
- 발급받은 Access Token과 Refresh Token은 함께 교체하고 플랫폼 토큰 저장소에도 함께
  저장한다.
- 원 요청은 HTTP 계층에서 최대 한 번만 재전송한다. 여행 기록 Repository 전체를 다시
  실행하지 않으므로 이미 완료된 S3 업로드는 반복하지 않는다.
- Refresh Token까지 만료된 경우 새 게스트 계정을 자동 생성하지 않는다. 기존 게스트의
  데이터 소유권이 바뀔 수 있으므로 로그인 복구 오류를 화면에 전달한다.

## 이미지 조회

클라이언트는 만료되는 URL을 저장하지 않는다. 상세 사진은 `objectKey`를 영속 캐시 키로
사용하고, 목록 썸네일은 Presigned URL에서 서명 쿼리와 fragment를 제거한 S3 리소스
경로를 캐시 키로 사용한다.

1. Object Key 캐시에 축소 미리보기가 있으면 네트워크를 호출하지 않는다.
2. 캐시에 없으면 서버 응답의 `viewUrl`로 S3에 GET 요청한다.
3. 원본을 1280px 미리보기로 축소해 Android/iOS 시스템 캐시 디렉터리에 Object Key로
   저장한다.
4. S3가 만료 URL에 `403`을 반환하면 여행 기록 상세를 한 번 다시 조회해 새 `viewUrl`로
   한 번 재시도한다.

목록 썸네일도 같은 흐름을 따른다.

1. 여행 기록 목록 응답을 받으면 제목·지역·날짜를 먼저 화면에 표시한다.
2. `thumbnailUrl`의 S3 리소스 경로를 Object Key 캐시 키로 사용한다.
3. 캐시에 없는 썸네일만 최대 3개씩 S3 GET하고 완료되는 카드부터 갱신한다.
4. Android/iOS의 이미지 축소 작업은 메인 스레드 밖에서 수행한다.
5. Android는 원본 JPEG의 EXIF 방향을 적용한 뒤 미리보기를 만든다.
6. S3가 `403`을 반환하면 같은 목록 페이지를 한 번만 다시 조회한다.
7. 새 `thumbnailUrl`로 만료된 사진만 한 번 재시도한다.

EXIF 보정 전 빌드가 저장한 미리보기를 계속 사용하지 않도록 디스크 캐시 키에는 스키마
버전을 포함한다. 앱 업데이트 후 기존 사진은 최초 한 번 다시 생성되고 이후에는 재사용된다.

## 백엔드 응답 계약

백엔드 PR #180에서 상세 조회 시 각 Object Key를 Presigned GET URL로 변환하는 기능이
구현되었다. 별도 URL 발급 endpoint를 호출하는 방식이 아니라 여행 기록 상세 응답의
`media`에 Object Key와 조회 URL을 함께 반환한다.

```json
{
  "media": [
    {
      "id": 55,
      "objectKey": "travel-records/10/{uuid}.jpg",
      "viewUrl": "https://...",
      "viewUrlExpiresIn": 300,
      "sortOrder": 0
    }
  ]
}
```

클라이언트는 위 응답을 지원하며 기존 `objectKeys`만 있는 응답도 계속 파싱하므로 서버
배포 순서와 무관하게 호환된다.

백엔드 PR #182에서 목록에 `thumbnailUrl`과 `thumbnailUrlExpiresIn`도 추가되었다. 현재
목록 응답에는 별도 `thumbnailObjectKey`가 없지만 현재 URL 경로가
`travel-records/{memberId}/{uuid}.{extension}` Object Key와 같으므로 이 경로를 캐시
식별자로 사용한다. 수정 직후 Object Key로 저장한 로컬 미리보기와 목록 썸네일이 같은
캐시를 재사용하고, URL 서명이 바뀌어도 다시 다운로드하지 않는다.
