# iOS HEIC 가져오기와 표시

PhotoKit 미리보기 실패 시 원본 리소스를 읽어 ImageIO로 JPEG 미리보기를 만든다. 원본 리소스도 읽지 못한 수동 선택은 PHPicker 데이터로 재시도한다. 추천 사진은 PHPicker provider가 없어 원본 리소스 재시도까지만 수행한다. 최종 실패한 사진은 제외한다.

추천·선택 미리보기를 위한 PhotoKit 원본 재시도는 요청마다 고유한 임시 파일을 사용한다. ImageIO가 파일에서 직접 640px JPEG를 만든 뒤 원본을 삭제하며 다운로드 실패·디코딩 실패·코루틴 취소에도 정리한다. PhotoKit 파일 쓰기 API는 취소할 수 없으므로 취소 시 쓰기 완료 콜백까지 기다려 디코딩을 건너뛰고 삭제한다. 업로드 재시도에 필요한 `mapmory-upload-originals` 캐시나 PHPicker에서 선택하여 보관 중인 원본은 건드리지 않는다. 저장 전 추천을 둘러보는 행동이 업로드 원본의 보관 수명을 늘리지 않도록 한다.

ImageIO 메타데이터와 미리보기 옵션에는 CFString 포인터를 직접 Kotlin Map 키로 사용하지 않고 NSString/String으로 브리징한다. JPEG 변환은 primary image index와 EXIF 방향을 적용하며 내장 썸네일이 없어도 픽셀에서 생성한다. 촬영일은 변환 전 원본 EXIF/TIFF에서 읽는다.

PHPicker로 보관한 원본은 `mapmory-cache://` 식별자로 저장된다. 확대 화면은 이 식별자를 PHAsset으로 조회하지 않고 원본 파일을 읽는다. 화면을 닫거나 사진이 바뀌면 이전 읽기/PhotoKit 요청을 취소하고 이전 요청의 결과를 버린다.

iOS 앱 시작 시 Coil에 HEIF 디코더를 등록한다. 이 디코더는 MIME/확장자 대신 ftyp 헤더를 확인하므로 기존 서버 HEIC URL, 원본 바이트, 확장자 없는 캐시 파일을 처리한다. ImageIO로 방향을 적용한 JPEG를 생성한 뒤 Skia에 전달한다. 요청의 FIT/FILL 크기와 최대 비트맵 크기를 반영하고, 사진이 아닌 입력 및 JPEG/PNG 등은 기본 디코더에 맡긴다. 업로드 원본과 서버 저장 형식은 변경하지 않는다.

## 자동 검증

```sh
./gradlew :shared:iosSimulatorArm64Test --tests 'com.mapmory.shared.data.media.*' --tests 'com.mapmory.shared.presentation.photo.*'
./gradlew :shared:compileKotlinIosArm64
```

`IosPhotoFixtures`는 개인정보가 없는 합성 JPEG/HEIC다. 32×24 빨간 이미지의 EXIF 촬영일, 1600×900 빨강/파랑 이미지의 orientation=6을 사용한다. 테스트는 촬영일, 내장 썸네일 없는 HEIC 변환, 회전된 픽셀/치수, FIT/FILL, 원본 캐시 읽기 및 취소, 기본 디코더 위임을 확인한다. 추천 임시 원본은 성공·다운로드 실패·변환 실패·취소·동시 요청에서 정리되고 별도 업로드 원본을 보존하는지도 확인한다.

실제 문제 사진, iCloud 원본 다운로드 실패, 실기기 메모리 사용량, 서버 업로드 및 Firebase 이벤트 수신은 이 자동 검증 범위에 포함되지 않는다.
