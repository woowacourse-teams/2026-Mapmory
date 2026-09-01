# Mapmory Play Console 제출 체크리스트

기준: `main` 출시 AAB에서 실제로 실행되는 기능.

## 제출 정보

| 항목 | 입력값 |
| --- | --- |
| 개인정보처리방침 | GitHub Pages로 공개한 `docs/privacy-policy.html`의 HTTPS URL |
| 데이터 수집·공유 | 수집: 예 / 공유: 현재 앱 기능 기준 아니오 |
| 광고 포함 | 아니오 |
| 로그인 필요 | 아니오 |
| 리뷰어 계정 | 불필요 |
| 타겟 연령 | 전체 이용자, 아동을 주된 대상으로 하지 않음 |

상세 근거는 [`play-console-data-safety.md`](./play-console-data-safety.md)를 사용합니다.

## 출시 직전 확인

- [ ] Play Console에 올릴 AAB가 실제 `main` 기준인가
- [ ] 개인정보처리방침 URL이 HTTPS로 공개되고 로그인 없이 열리는가
- [ ] 공개 페이지에 개인정보처리자 한수진, 팀명 Mapmory, 문의 이메일이 표시되는가
- [ ] 게스트 인증과 여행 기록 원격 저장이 제출 AAB의 실제 실행 경로에 연결되는가
- [ ] 사진 원본 업로드와 EXIF 위치정보 포함 가능성을 데이터 보안 응답에 반영했는가
- [ ] 사진 접근·미디어 위치 권한의 목적과 거부 시 제한 기능을 정확히 설명했는가
- [ ] Firebase Analytics 활성화 여부를 최종 AAB 기준으로 확인했는가
- [ ] Firebase Analytics가 활성화되어 있다면 개인정보처리방침과 데이터 보안 응답을 갱신했는가
- [ ] 사용자 ID, 사용자 생성 콘텐츠, 사진, 위치, 앱 정보 및 성능 항목을 최종 AAB 기준으로 검증했는가
- [ ] 개인정보처리방침의 항목·목적·보유기간이 Play Console 응답과 일치하는가

## 기능 추가 시 갱신 대상

로그인 방식, 사진 메타데이터 제거 여부, 계정 삭제 기능, GPS 위치, 오류 로그 또는 외부 SDK가
변경되면 제출 전에 수집 항목, 처리 목적, 보관·삭제 정책과 제3자 처리 여부를 다시 검토합니다.

## 근거

- [Google Play 데이터 보안 섹션 공식 안내](https://support.google.com/googleplay/android-developer/answer/10787469?hl=ko)
- [Mapmory 개인정보처리방침](../../docs/privacy-policy.html)
