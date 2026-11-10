# 카카오톡 그룹 초대 웹 변경 요청

## 현재 실패 경로

수신 갤럭시 실행 기록에서 카카오톡 내부 브라우저 → Play 스토어 → ACTION_MAIN으로 Mapmory 실행을 확인했다. 마지막 실행에는 초대 URL이 없다. 설치된 앱에도 초대를 전달해야 한다.

## 수정 대상

- landing/public/invite/index.html
- landing/public/.well-known/assetlinks.json
- landing/public/.well-known/apple-app-site-association

별도 작업 폴더 C:/2026-Mapmory/invite-landing의 landing/feat/group-invite-handoff 브랜치에 Android 버튼 초안이 있다. 운영 배포 전이며 카카오톡 실기기 검증이 필요하다.

## Android 초대 페이지

1. /invite/{token}의 토큰을 보존한다. 허용 형식은 ASCII 영문, 숫자, 밑줄, 하이픈이다.
2. 앱에서 초대 열기 버튼을 아래 intent URL로 연결한다. 사용자의 버튼 클릭으로 실행한다.
3. 미설치 fallback은 초대 토큰을 담은 Play 스토어 URL을 사용한다.
4. 설치된 앱을 무조건 스토어로 보내는 자동 이동을 사용하지 않는다. 카카오톡이 페이지를 먼저 열면 앱에서 초대 열기 버튼을 제공한다.

```html
<!-- 기존 스토어 링크 영역에 추가한다. 기존 iOS 스토어 링크는 유지한다. -->
<a id="invite-open-android" hidden>앱에서 초대 열기</a>
<a id="invite-play-store" hidden>Google Play에서 설치</a>
<script>
(() => {
if (!/Android/i.test(navigator.userAgent)) return;
const openAppLink = document.getElementById("invite-open-android");
const playStoreLink = document.getElementById("invite-play-store");
const token = /^\/invite\/([A-Za-z0-9_-]{6,512})$/.exec(location.pathname)?.[1];
if (token) {
  const store = new URL("https://play.google.com/store/apps/details");
  store.searchParams.set("id", "com.mapmory.android");
  store.searchParams.set("referrer", new URLSearchParams({ invite_token: token }).toString());
  openAppLink.href =
    "intent://map-mory.com/invite/" + token +
    "#Intent;scheme=https;package=com.mapmory.android;S.browser_fallback_url=" +
    encodeURIComponent(store.href) + ";end";
  playStoreLink.href = store.href;
  openAppLink.hidden = false;
  playStoreLink.hidden = false;
}
})();
</script>
```

기존 Android 스토어 버튼은 위 버튼으로 교체해 중복을 없앤다. 유효하지 않은 경로에는 초대 링크 오류를 표시한다. intent URI에는 원래 HTTPS 호스트와 경로를 유지한다. 현재 APK의 intent-filter가 이 URL을 처리한다.

intent URI와 browser_fallback_url은 Chrome이 문서화한 방식이다. 카카오톡 내부 브라우저 지원 여부는 실제 배포 페이지에서 검증해야 한다. fallback 발생만으로 앱 미설치를 단정할 수 없다. 브라우저가 외부 앱 실행을 제한해도 fallback으로 갈 수 있다. 설치된 앱이 있는데도 스토어로 이동하면 인수 실패로 처리하고 카카오톡 버전과 전달된 Intent를 확인한다.

## 도메인 연결

SHA-256은 앱 서명 인증서의 지문이다. 같은 서명으로 배포한 앱을 설치한 모든 사용자가 해당 인증서 등록의 대상이다. 기기마다 SHA를 추가하는 절차는 없다.

운영 담당자는 Play Console의 앱 서명 화면에서 Digital Asset Links JSON을 확인하고, assetlinks.json의 com.mapmory.android 항목을 그 값과 대조한다. Google 로그인용 SHA-1이나 업로드 키 값을 대신 넣지 않는다. 앱 서명 키를 교체해 여러 인증서로 배포되고 있다면 지원 중인 인증서를 모두 포함한다. 기존 다른 패키지 항목도 유지한다.

연결 파일은 HTTPS에서 인증이나 리다이렉트 없이 HTTP 200 application/json으로 반환한다. 앱의 autoVerify와 일치하는 호스트에서 제공한다. 검증된 링크도 사용자가 기본 연결을 직접 해제했거나 인앱 브라우저가 가로채면 웹으로 열릴 수 있어 위 앱 열기 버튼이 필요하다.

개발 APK는 빌드에 사용한 개발용 키로 서명된다. 같은 개발 APK를 여러 폰에 설치하면 모두 같은 인증서를 가진다. 운영 전달 문서에 로컬 디버그 인증서를 운영 등록값으로 제시하지 않는다. 운영 연결 값의 적합성은 Play Console과 대조하기 전까지 미확인이다. 일반 사용자 검증은 수동 링크 허용 이력이 없는 기기에 실제 Play 배포 빌드를 설치해 수행한다.

iOS AASA는 22YUT65LMV.com.mapmory.ios의 /invite/*를 유지한다. 앱의 Associated Domains와 프로비저닝을 확인한다. App Store 주소는 https://apps.apple.com/app/id6807056166 이다. iOS 설치 후에는 공유 메시지의 6자리 코드를 사용한다. 이 Android intent 방식으로 iOS 전환까지 검증됐다고 판단하지 않는다.

## API 환경

확인한 backend-develop 2c265622에는 발급 응답 inviteUrl이 있고 backend-release 78096644에는 없다. 운영 발급에서도 링크를 제공하려면 해당 응답 변경의 운영 반영이 필요하다.

초대를 만든 앱과 받는 앱이 같은 API 환경을 사용해야 한다. 개발 DB에서 만든 초대는 운영 DB에서 조회되지 않는다. 현재 공개 URL에는 환경 구분이 없으므로 개발용 APK 두 대를 이용한 테스트와 운영 배포 앱 테스트를 각각 진행한다.

## 인수 확인

1. 에뮬레이터에서 초대를 만들고 카카오톡으로 실제 링크와 코드를 공유한다.
2. 같은 API 환경의 최신 앱이 설치된 수신 폰에서 카카오톡 링크를 누른다.
3. 직접 앱이 열리거나, 카카오톡 웹의 앱에서 초대 열기를 누르면 동일한 그룹 초대 화면이 표시된다.
4. 미로그인 수신자는 그룹 탭에서 초대를 확인하고 로그인 후에도 같은 초대가 유지된다.
5. 초대 수락은 사용자가 참여 버튼을 눌렀을 때 수행한다.
6. 발급자는 초대창을 닫고 다시 열어도 동일한 링크와 코드를 확인한다.
7. Android 신규 설치는 Play 배포 빌드로 referrer 복원까지 확인한다. 이미 설치된 앱을 스토어에서 여는 동작은 신규 설치 검증에 해당하지 않는다.

카카오톡 내부 브라우저의 실제 버튼 동작, 설치 여부별 전환, iOS 실기기 동작은 배포 후 확인해야 한다.

웹 변경과 함께 초대 링크 수신 기능이 포함된 앱 및 inviteUrl을 반환하는 운영 API가 배포돼야 한다. 이 문서의 웹 수정만으로 기존 모든 앱 버전의 초대 수신 기능을 추가할 수는 없다.

참고 문서

- [Android 도메인 연결과 앱 서명 인증서](https://developer.android.com/training/app-links/configure-assetlinks)
- [Chrome Android Intent와 fallback 조건](https://developer.chrome.com/docs/android/intents)

현재 발급 결과 보관 범위는 계정별 화면의 저장 상태이며 초대창 재진입, 탭 상태 복원에 적용된다. 로그아웃, 앱 데이터 삭제, 저장 상태가 없는 새 실행까지 동일한 초대를 보장하려면 서버의 기존 유효 초대 조회 또는 계정별 영구 저장이 추가로 필요하다.
