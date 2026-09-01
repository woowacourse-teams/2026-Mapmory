# 사진 메타데이터 자동화 테스트

사진 추천 기능은 다음 세 층으로 확인한다.

| 대상 | 테스트 | 확인 내용 |
| --- | --- | --- |
| 지도·추천 경계 로직 | `commonTest` | 지도 Polygon 경계 터치, 잘못된 좌표·퇴화한 링, 추천 사진 최대 개수와 캐시 재사용 규칙 |
| 순수 캐시 규칙 | `commonTest` | 수정 시각이 같고 좌표가 있을 때만 재사용하고, 그 외에는 재조회하는지 |
| 실제 Room 동기화 | `androidDeviceTest` | 신규·변경·삭제·빈 스냅샷, GPS 누락 재시도, 사진별 재사용/재조회, DAO 필터·정렬 |
| 실제 지도 리소스 연결 | `androidDeviceTest` | canonical 시·군·구 코드가 번들된 경계 리소스를 정확히 선택하는지 |
| 실제 MediaStore·EXIF·미리보기 | `androidDeviceTest` | ContentResolver 조회, EXIF GPS 읽기, 원본·미리보기 바이트 생성 |
| 기록 작성 화면 추천 흐름 | `androidApp connectedAndroidTest` | 선택 장소 전달, 추천 사진 표시·추가, 장소 미선택 안내 |
| 권한·Photo Picker UI | 수동 확인 | 권한 허용·거부·부분 허용, Photo Picker에서 실제 사진 선택 |

## 실행

`client` 디렉터리에서 실행한다. 운영체제에 따라 Gradle Wrapper 실행 명령이 다르다.

### macOS/Linux

```bash
# 공통 캐시 규칙과 기존 공통 테스트
./gradlew :shared:jvmTest

# Android 호스트 컴파일·단위 테스트
./gradlew :shared:testAndroidHostTest

# 실제 Android 기기 또는 에뮬레이터에서 Room 계측 테스트
./gradlew :shared:androidConnectedCheck

# 기록 작성 화면 Compose 계측 테스트
./gradlew :androidApp:connectedDebugAndroidTest

# 앱 전체 Android 컴파일
./gradlew :androidApp:assembleDebug
```

### Windows

Windows에서는 `./gradlew` 대신 `gradlew.bat`을 사용한다.

```bat
gradlew.bat :shared:jvmTest
gradlew.bat :shared:testAndroidHostTest
gradlew.bat :shared:androidConnectedCheck
gradlew.bat :androidApp:connectedDebugAndroidTest
gradlew.bat :androidApp:assembleDebug
```

`androidConnectedCheck`는 연결된 모든 기기에서 실행된다. 특정 기기만 사용할 때는 다른 기기를 종료하거나 Gradle의 device 선택 옵션을 사용한다.

## 합격 기준

- 신규 사진은 EXIF를 읽고 좌표와 메타데이터를 Room에 저장한다.
- 같은 `mediaId`와 `modifiedAtSeconds`를 가진 사진은 기존 좌표를 재사용한다.
- 수정 시각이 바뀐 사진은 EXIF를 다시 읽고 좌표를 갱신한다.
- 현재 MediaStore 스냅샷에 없는 사진은 Room에서 삭제된다.
- MediaStore 조회가 실패한 경우에는 기존 스냅샷을 임의로 삭제하지 않는다.
- EXIF GPS가 없는 사진은 좌표를 캐시된 것으로 간주하지 않고 다음 동기화에서 다시 시도한다.
- 지도 경계의 선 위를 눌러도 해당 지역을 선택한다.
- 실제 MediaStore 이미지의 EXIF GPS와 미리보기 생성 흐름이 동작한다.

## 성능 로그

기능 테스트의 합격 여부는 좌표 재사용·Room 스냅샷 테스트로 판단한다. 실제 기기에서 시간 변화를 관찰할 때만 다음 로그를 사용한다.

```bash
adb -s <serial> shell setprop log.tag.MapmoryPhotoPerf DEBUG
adb -s <serial> logcat -v time -s MapmoryPhotoPerf:D
```

`exif_reads`, `reused_coordinates`, `metadata_sync_ms`를 비교할 수 있지만, 현재는 고정된 성능 기준선으로 사용하지 않는다. Photo Picker UI 자동화와 권한별 UI 자동화는 계속 수동 검증 대상이다. 앱 시작 시간은 [모바일 모니터링 기준](../android-monitoring.md)의 로컬 측정 스크립트를 사용하고, 지도 전환 Macrobenchmark와 CI 성능 기준선은 필요성이 확인될 때 별도 도입한다.
