# 그룹 초대 링크 연동과 배포 확인

작성일 2026-11-10. 브랜치는 `and/feat/group-invite-link`다.

## 확정한 사용자 흐름

| 상태 | 동작 |
| --- | --- |
| 설치됨·로그인됨 | 링크 → 그룹 탭 → 초대 미리보기 → 참여하기 |
| 설치됨·게스트 | 링크 → 그룹 탭에서 그룹 이름·인원 표시 → 로그인 → 같은 초대 참여 |
| Android 미설치 | 링크 → Play 스토어 → 설치 → Install Referrer로 초대 복원 |
| iOS 미설치 | 링크 → App Store → 설치 → 그룹 탭에서 6자리 코드 입력·붙여넣기 → 로그인 → 초대 참여 |

공유 메시지에는 서버의 inviteUrl과 6자리 코드를 함께 넣는다. 코드 입력란은 길게 눌러 운영체제 기본 붙여넣기를 사용할 수 있다. 앱이 클립보드를 자동으로 읽지는 않는다. 대기 중인 초대는 로그인과 앱 재실행에 걸쳐 보관하고, 수락 또는 닫기 후 정리한다.

이번 수정에서 App Clip 대상·공유 저장소·설치 전환 코드를 제거했다. iOS에서 App Store 설치만으로 초대가 자동 복원되지는 않으며 공유 메시지의 코드를 사용한다.

## 서버 API

backend-develop의 ef7eae24에 inviteUrl이 추가됐다. 확인 당시 backend-release에는 해당 변경이 없다. 운영 반영을 확인한다.

- POST /api/v1/groups/{groupId}/invitations에서 받은 inviteUrl을 그대로 공유한다.
- 이전 서버에 inviteUrl이 없으면 기존 코드 발급·복사를 제공한다.
- GET /api/v1/invitations/{tokenOrCode}와 POST /api/v1/invitations/{tokenOrCode}/accept를 재사용한다.
- 로그인 전 미리보기는 게스트 인증으로 호출하고, 수락은 소셜 로그인 후 사용자 버튼으로 처리한다.

## 웹 서버 담당 작업

이 클라이언트 브랜치에서 웹 배포는 수행하지 않았다. 랜딩 소스는 landing/AGENTS.md와 landing/DEPLOYMENT.md의 브랜치·배포 절차를 따른다.

2026-11-10 운영 확인 결과 두 연결 파일은 모두 HTTP 200 JSON을 반환했다. Android 운영 패키지 항목의 인증서와 로컬 디버그 인증서가 다르다. 디버그 APK로 테스트할 때는 기기의 Mapmory 앱 정보 → 기본으로 열기에서 map-mory.com을 허용한 뒤 메시지의 링크를 누른다. 운영 검증은 등록된 인증서로 서명한 앱으로 별도 진행한다. 현재 웹 경로는 두 스토어 버튼을 표시하며 Play 버튼 URL에는 referrer가 없다.

1. https://map-mory.com/invite/{token}에서 Android는 Play 스토어, iOS는 App Store로 이동시킨다. iOS 설치 후에는 공유 메시지의 코드를 입력한다.
2. Android는 아래처럼 invite_token을 Play 스토어 referrer에 담는다.

```js
const query = new URLSearchParams({
  id: 'com.mapmory.android',
  referrer: new URLSearchParams({ invite_token: token }).toString(),
});
const destination = `https://play.google.com/store/apps/details?${query}`;
```

3. iOS 목적지는 https://apps.apple.com/app/id6807056166 이다.
4. /.well-known/assetlinks.json을 인증 없이 JSON으로 제공하고, com.mapmory.android와 Google Play 앱 서명 인증서의 실제 SHA-256을 등록한다.
5. /.well-known/apple-app-site-association을 인증·확장자·리다이렉트 없이 제공한다.

```json
{
  "applinks": {
    "details": [{
      "appIDs": ["22YUT65LMV.com.mapmory.ios"],
      "components": [{"/": "/invite/*"}]
    }]
  }
}
```

실제 archive의 application-identifier도 대조한다. JSON 경로가 일반 랜딩 HTML로 대체되지 않도록 확인한다. iOS Associated Domains 프로파일을 갱신한다.

## 검증

- JVM 테스트와 Android 디버그 APK 빌드로 공통 코드와 API 응답 호환성을 확인한다.
- Android·iOS의 앱 종료/실행 중 링크 수신을 확인한다.
- 게스트 미리보기 → 로그인 → 같은 초대 수락, 코드 입력 → 로그인 → 수락을 확인한다.
- 링크와 코드가 함께 공유되는지, 만료·없는 초대·정원 초과를 안내하는지 확인한다.
- 설치 후 복원은 Android Play 설치 경로로 확인한다. iOS는 App Store 설치 후 코드 붙여넣기를 확인한다.
- Windows에서는 iOS 빌드와 실제 App Store 설치 경로를 검증할 수 없어 macOS 빌드·실기기 확인이 남는다.
