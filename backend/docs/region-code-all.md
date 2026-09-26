# 전체 Region 코드표

> 기준일: 2026-09-15
> 적용 상태: `V19__restore_gwangju_jeonnam_district_codes.sql` 적용 기준 최종 데이터

## 1. 범위

이 문서는 백엔드 Region 마스터에 저장할 전체 국가·시도·시군구 코드를 기록한다.
국가는 ISO 3166-1 alpha-2, 대한민국 시·도는 ISO 3166-2의 숫자 부분, 대한민국 시·군·구는 서비스 canonical 행정표준코드를 사용한다.

- `COUNTRY`: 249개
- `PROVINCE`: 17개
- `DISTRICT`: 230개
- 전체: 496개

DB의 `id`는 환경과 삽입 순서에 따라 달라질 수 있으므로 표에 포함하지 않는다.
API와 데이터 대조에는 `region_code`, `region_type`, 부모 코드를 사용한다.

V8 초기 데이터의 일반구 39개는 V18에서 제거되고 canonical 시 코드 13개로 통합된다.
V8에서 잘못 선반영한 광주·전남 `12xxx` 코드는 V19에서 클라이언트와 같은 `29xxx`·`46xxx` 코드로 복원된다.
이 표는 모든 버전 마이그레이션을 적용한 최종 상태를 나타낸다.
폐기 코드 매핑은 [대한민국 여행 기록 지역 코드표](region-code-table.md)를 참고한다.

## 2. 국가

| 코드 | 이름 | 타입 | 부모 코드 |
| --- | --- | --- | --- |
| `AD` | 안도라 | `COUNTRY` | - |
| `AE` | 아랍에미리트 | `COUNTRY` | - |
| `AF` | 아프가니스탄 | `COUNTRY` | - |
| `AG` | 앤티가 바부다 | `COUNTRY` | - |
| `AI` | 앵귈라 | `COUNTRY` | - |
| `AL` | 알바니아 | `COUNTRY` | - |
| `AM` | 아르메니아 | `COUNTRY` | - |
| `AO` | 앙골라 | `COUNTRY` | - |
| `AQ` | 남극 대륙 | `COUNTRY` | - |
| `AR` | 아르헨티나 | `COUNTRY` | - |
| `AS` | 아메리칸 사모아 | `COUNTRY` | - |
| `AT` | 오스트리아 | `COUNTRY` | - |
| `AU` | 오스트레일리아 | `COUNTRY` | - |
| `AW` | 아루바 | `COUNTRY` | - |
| `AX` | 올란드 제도 | `COUNTRY` | - |
| `AZ` | 아제르바이잔 | `COUNTRY` | - |
| `BA` | 보스니아 헤르체고비나 | `COUNTRY` | - |
| `BB` | 바베이도스 | `COUNTRY` | - |
| `BD` | 방글라데시 | `COUNTRY` | - |
| `BE` | 벨기에 | `COUNTRY` | - |
| `BF` | 부르키나파소 | `COUNTRY` | - |
| `BG` | 불가리아 | `COUNTRY` | - |
| `BH` | 바레인 | `COUNTRY` | - |
| `BI` | 부룬디 | `COUNTRY` | - |
| `BJ` | 베냉 | `COUNTRY` | - |
| `BL` | 생바르텔레미 | `COUNTRY` | - |
| `BM` | 버뮤다 | `COUNTRY` | - |
| `BN` | 브루나이 | `COUNTRY` | - |
| `BO` | 볼리비아 | `COUNTRY` | - |
| `BQ` | 네덜란드령 카리브 | `COUNTRY` | - |
| `BR` | 브라질 | `COUNTRY` | - |
| `BS` | 바하마 | `COUNTRY` | - |
| `BT` | 부탄 | `COUNTRY` | - |
| `BV` | 부베섬 | `COUNTRY` | - |
| `BW` | 보츠와나 | `COUNTRY` | - |
| `BY` | 벨라루스 | `COUNTRY` | - |
| `BZ` | 벨리즈 | `COUNTRY` | - |
| `CA` | 캐나다 | `COUNTRY` | - |
| `CC` | 코코스 제도 | `COUNTRY` | - |
| `CD` | 콩고-킨샤사 | `COUNTRY` | - |
| `CF` | 중앙 아프리카 공화국 | `COUNTRY` | - |
| `CG` | 콩고-브라자빌 | `COUNTRY` | - |
| `CH` | 스위스 | `COUNTRY` | - |
| `CI` | 코트디부아르 | `COUNTRY` | - |
| `CK` | 쿡 제도 | `COUNTRY` | - |
| `CL` | 칠레 | `COUNTRY` | - |
| `CM` | 카메룬 | `COUNTRY` | - |
| `CN` | 중국 | `COUNTRY` | - |
| `CO` | 콜롬비아 | `COUNTRY` | - |
| `CR` | 코스타리카 | `COUNTRY` | - |
| `CU` | 쿠바 | `COUNTRY` | - |
| `CV` | 카보베르데 | `COUNTRY` | - |
| `CW` | 퀴라소 | `COUNTRY` | - |
| `CX` | 크리스마스섬 | `COUNTRY` | - |
| `CY` | 키프로스 | `COUNTRY` | - |
| `CZ` | 체코 | `COUNTRY` | - |
| `DE` | 독일 | `COUNTRY` | - |
| `DJ` | 지부티 | `COUNTRY` | - |
| `DK` | 덴마크 | `COUNTRY` | - |
| `DM` | 도미니카 | `COUNTRY` | - |
| `DO` | 도미니카 공화국 | `COUNTRY` | - |
| `DZ` | 알제리 | `COUNTRY` | - |
| `EC` | 에콰도르 | `COUNTRY` | - |
| `EE` | 에스토니아 | `COUNTRY` | - |
| `EG` | 이집트 | `COUNTRY` | - |
| `EH` | 서사하라 | `COUNTRY` | - |
| `ER` | 에리트리아 | `COUNTRY` | - |
| `ES` | 스페인 | `COUNTRY` | - |
| `ET` | 에티오피아 | `COUNTRY` | - |
| `FI` | 핀란드 | `COUNTRY` | - |
| `FJ` | 피지 | `COUNTRY` | - |
| `FK` | 포클랜드 제도 | `COUNTRY` | - |
| `FM` | 미크로네시아 | `COUNTRY` | - |
| `FO` | 페로 제도 | `COUNTRY` | - |
| `FR` | 프랑스 | `COUNTRY` | - |
| `GA` | 가봉 | `COUNTRY` | - |
| `GB` | 영국 | `COUNTRY` | - |
| `GD` | 그레나다 | `COUNTRY` | - |
| `GE` | 조지아 | `COUNTRY` | - |
| `GF` | 프랑스령 기아나 | `COUNTRY` | - |
| `GG` | 건지 | `COUNTRY` | - |
| `GH` | 가나 | `COUNTRY` | - |
| `GI` | 지브롤터 | `COUNTRY` | - |
| `GL` | 그린란드 | `COUNTRY` | - |
| `GM` | 감비아 | `COUNTRY` | - |
| `GN` | 기니 | `COUNTRY` | - |
| `GP` | 과들루프 | `COUNTRY` | - |
| `GQ` | 적도 기니 | `COUNTRY` | - |
| `GR` | 그리스 | `COUNTRY` | - |
| `GS` | 사우스조지아 사우스샌드위치 제도 | `COUNTRY` | - |
| `GT` | 과테말라 | `COUNTRY` | - |
| `GU` | 괌 | `COUNTRY` | - |
| `GW` | 기니비사우 | `COUNTRY` | - |
| `GY` | 가이아나 | `COUNTRY` | - |
| `HK` | 홍콩 | `COUNTRY` | - |
| `HM` | 허드 맥도널드 제도 | `COUNTRY` | - |
| `HN` | 온두라스 | `COUNTRY` | - |
| `HR` | 크로아티아 | `COUNTRY` | - |
| `HT` | 아이티 | `COUNTRY` | - |
| `HU` | 헝가리 | `COUNTRY` | - |
| `ID` | 인도네시아 | `COUNTRY` | - |
| `IE` | 아일랜드 | `COUNTRY` | - |
| `IL` | 이스라엘 | `COUNTRY` | - |
| `IM` | 맨 섬 | `COUNTRY` | - |
| `IN` | 인도 | `COUNTRY` | - |
| `IO` | 영국령 인도양 식민지 | `COUNTRY` | - |
| `IQ` | 이라크 | `COUNTRY` | - |
| `IR` | 이란 | `COUNTRY` | - |
| `IS` | 아이슬란드 | `COUNTRY` | - |
| `IT` | 이탈리아 | `COUNTRY` | - |
| `JE` | 저지 | `COUNTRY` | - |
| `JM` | 자메이카 | `COUNTRY` | - |
| `JO` | 요르단 | `COUNTRY` | - |
| `JP` | 일본 | `COUNTRY` | - |
| `KE` | 케냐 | `COUNTRY` | - |
| `KG` | 키르기스스탄 | `COUNTRY` | - |
| `KH` | 캄보디아 | `COUNTRY` | - |
| `KI` | 키리바시 | `COUNTRY` | - |
| `KM` | 코모로 | `COUNTRY` | - |
| `KN` | 세인트키츠 네비스 | `COUNTRY` | - |
| `KP` | 북한 | `COUNTRY` | - |
| `KR` | 대한민국 | `COUNTRY` | - |
| `KW` | 쿠웨이트 | `COUNTRY` | - |
| `KY` | 케이맨 제도 | `COUNTRY` | - |
| `KZ` | 카자흐스탄 | `COUNTRY` | - |
| `LA` | 라오스 | `COUNTRY` | - |
| `LB` | 레바논 | `COUNTRY` | - |
| `LC` | 세인트루시아 | `COUNTRY` | - |
| `LI` | 리히텐슈타인 | `COUNTRY` | - |
| `LK` | 스리랑카 | `COUNTRY` | - |
| `LR` | 라이베리아 | `COUNTRY` | - |
| `LS` | 레소토 | `COUNTRY` | - |
| `LT` | 리투아니아 | `COUNTRY` | - |
| `LU` | 룩셈부르크 | `COUNTRY` | - |
| `LV` | 라트비아 | `COUNTRY` | - |
| `LY` | 리비아 | `COUNTRY` | - |
| `MA` | 모로코 | `COUNTRY` | - |
| `MC` | 모나코 | `COUNTRY` | - |
| `MD` | 몰도바 | `COUNTRY` | - |
| `ME` | 몬테네그로 | `COUNTRY` | - |
| `MF` | 생마르탱 | `COUNTRY` | - |
| `MG` | 마다가스카르 | `COUNTRY` | - |
| `MH` | 마셜 제도 | `COUNTRY` | - |
| `MK` | 북마케도니아 | `COUNTRY` | - |
| `ML` | 말리 | `COUNTRY` | - |
| `MM` | 미얀마 | `COUNTRY` | - |
| `MN` | 몽골 | `COUNTRY` | - |
| `MO` | 마카오 | `COUNTRY` | - |
| `MP` | 북마리아나제도 | `COUNTRY` | - |
| `MQ` | 마르티니크 | `COUNTRY` | - |
| `MR` | 모리타니 | `COUNTRY` | - |
| `MS` | 몬트세라트 | `COUNTRY` | - |
| `MT` | 몰타 | `COUNTRY` | - |
| `MU` | 모리셔스 | `COUNTRY` | - |
| `MV` | 몰디브 | `COUNTRY` | - |
| `MW` | 말라위 | `COUNTRY` | - |
| `MX` | 멕시코 | `COUNTRY` | - |
| `MY` | 말레이시아 | `COUNTRY` | - |
| `MZ` | 모잠비크 | `COUNTRY` | - |
| `NA` | 나미비아 | `COUNTRY` | - |
| `NC` | 뉴칼레도니아 | `COUNTRY` | - |
| `NE` | 니제르 | `COUNTRY` | - |
| `NF` | 노퍽섬 | `COUNTRY` | - |
| `NG` | 나이지리아 | `COUNTRY` | - |
| `NI` | 니카라과 | `COUNTRY` | - |
| `NL` | 네덜란드 | `COUNTRY` | - |
| `NO` | 노르웨이 | `COUNTRY` | - |
| `NP` | 네팔 | `COUNTRY` | - |
| `NR` | 나우루 | `COUNTRY` | - |
| `NU` | 니우에 | `COUNTRY` | - |
| `NZ` | 뉴질랜드 | `COUNTRY` | - |
| `OM` | 오만 | `COUNTRY` | - |
| `PA` | 파나마 | `COUNTRY` | - |
| `PE` | 페루 | `COUNTRY` | - |
| `PF` | 프랑스령 폴리네시아 | `COUNTRY` | - |
| `PG` | 파푸아뉴기니 | `COUNTRY` | - |
| `PH` | 필리핀 | `COUNTRY` | - |
| `PK` | 파키스탄 | `COUNTRY` | - |
| `PL` | 폴란드 | `COUNTRY` | - |
| `PM` | 생피에르 미클롱 | `COUNTRY` | - |
| `PN` | 핏케언 섬 | `COUNTRY` | - |
| `PR` | 푸에르토리코 | `COUNTRY` | - |
| `PS` | 팔레스타인 지구 | `COUNTRY` | - |
| `PT` | 포르투갈 | `COUNTRY` | - |
| `PW` | 팔라우 | `COUNTRY` | - |
| `PY` | 파라과이 | `COUNTRY` | - |
| `QA` | 카타르 | `COUNTRY` | - |
| `RE` | 리유니온 | `COUNTRY` | - |
| `RO` | 루마니아 | `COUNTRY` | - |
| `RS` | 세르비아 | `COUNTRY` | - |
| `RU` | 러시아 | `COUNTRY` | - |
| `RW` | 르완다 | `COUNTRY` | - |
| `SA` | 사우디아라비아 | `COUNTRY` | - |
| `SB` | 솔로몬 제도 | `COUNTRY` | - |
| `SC` | 세이셸 | `COUNTRY` | - |
| `SD` | 수단 | `COUNTRY` | - |
| `SE` | 스웨덴 | `COUNTRY` | - |
| `SG` | 싱가포르 | `COUNTRY` | - |
| `SH` | 세인트헬레나 | `COUNTRY` | - |
| `SI` | 슬로베니아 | `COUNTRY` | - |
| `SJ` | 스발바르제도-얀마웬섬 | `COUNTRY` | - |
| `SK` | 슬로바키아 | `COUNTRY` | - |
| `SL` | 시에라리온 | `COUNTRY` | - |
| `SM` | 산마리노 | `COUNTRY` | - |
| `SN` | 세네갈 | `COUNTRY` | - |
| `SO` | 소말리아 | `COUNTRY` | - |
| `SR` | 수리남 | `COUNTRY` | - |
| `SS` | 남수단 | `COUNTRY` | - |
| `ST` | 상투메 프린시페 | `COUNTRY` | - |
| `SV` | 엘살바도르 | `COUNTRY` | - |
| `SX` | 신트마르턴 | `COUNTRY` | - |
| `SY` | 시리아 | `COUNTRY` | - |
| `SZ` | 에스와티니 | `COUNTRY` | - |
| `TC` | 터크스 케이커스 제도 | `COUNTRY` | - |
| `TD` | 차드 | `COUNTRY` | - |
| `TF` | 프랑스 남부 지방 | `COUNTRY` | - |
| `TG` | 토고 | `COUNTRY` | - |
| `TH` | 태국 | `COUNTRY` | - |
| `TJ` | 타지키스탄 | `COUNTRY` | - |
| `TK` | 토켈라우 | `COUNTRY` | - |
| `TL` | 동티모르 | `COUNTRY` | - |
| `TM` | 투르크메니스탄 | `COUNTRY` | - |
| `TN` | 튀니지 | `COUNTRY` | - |
| `TO` | 통가 | `COUNTRY` | - |
| `TR` | 튀르키예 | `COUNTRY` | - |
| `TT` | 트리니다드 토바고 | `COUNTRY` | - |
| `TV` | 투발루 | `COUNTRY` | - |
| `TW` | 대만 | `COUNTRY` | - |
| `TZ` | 탄자니아 | `COUNTRY` | - |
| `UA` | 우크라이나 | `COUNTRY` | - |
| `UG` | 우간다 | `COUNTRY` | - |
| `UM` | 미국령 해외 제도 | `COUNTRY` | - |
| `US` | 미국 | `COUNTRY` | - |
| `UY` | 우루과이 | `COUNTRY` | - |
| `UZ` | 우즈베키스탄 | `COUNTRY` | - |
| `VA` | 바티칸 시국 | `COUNTRY` | - |
| `VC` | 세인트빈센트그레나딘 | `COUNTRY` | - |
| `VE` | 베네수엘라 | `COUNTRY` | - |
| `VG` | 영국령 버진아일랜드 | `COUNTRY` | - |
| `VI` | 미국령 버진아일랜드 | `COUNTRY` | - |
| `VN` | 베트남 | `COUNTRY` | - |
| `VU` | 바누아투 | `COUNTRY` | - |
| `WF` | 왈리스-푸투나 제도 | `COUNTRY` | - |
| `WS` | 사모아 | `COUNTRY` | - |
| `YE` | 예멘 | `COUNTRY` | - |
| `YT` | 마요트 | `COUNTRY` | - |
| `ZA` | 남아프리카 | `COUNTRY` | - |
| `ZM` | 잠비아 | `COUNTRY` | - |
| `ZW` | 짐바브웨 | `COUNTRY` | - |

## 3. 대한민국 시·도

| 코드 | 이름 | 타입 | 부모 코드 |
| --- | --- | --- | --- |
| `11` | 서울특별시 | `PROVINCE` | `KR` |
| `26` | 부산광역시 | `PROVINCE` | `KR` |
| `27` | 대구광역시 | `PROVINCE` | `KR` |
| `28` | 인천광역시 | `PROVINCE` | `KR` |
| `29` | 광주광역시 | `PROVINCE` | `KR` |
| `30` | 대전광역시 | `PROVINCE` | `KR` |
| `31` | 울산광역시 | `PROVINCE` | `KR` |
| `41` | 경기도 | `PROVINCE` | `KR` |
| `42` | 강원특별자치도 | `PROVINCE` | `KR` |
| `43` | 충청북도 | `PROVINCE` | `KR` |
| `44` | 충청남도 | `PROVINCE` | `KR` |
| `45` | 전북특별자치도 | `PROVINCE` | `KR` |
| `46` | 전라남도 | `PROVINCE` | `KR` |
| `47` | 경상북도 | `PROVINCE` | `KR` |
| `48` | 경상남도 | `PROVINCE` | `KR` |
| `49` | 제주특별자치도 | `PROVINCE` | `KR` |
| `50` | 세종특별자치시 | `PROVINCE` | `KR` |

## 4. 대한민국 시·군·구

일반 도의 일반구는 별도 Region으로 저장하지 않고 시 단위 canonical 코드로 통합한다.

### 서울특별시 (11)

| 코드 | 이름 | 타입 | 부모 코드 |
| --- | --- | --- | --- |
| `11110` | 종로구 | `DISTRICT` | `11` |
| `11140` | 중구 | `DISTRICT` | `11` |
| `11170` | 용산구 | `DISTRICT` | `11` |
| `11200` | 성동구 | `DISTRICT` | `11` |
| `11215` | 광진구 | `DISTRICT` | `11` |
| `11230` | 동대문구 | `DISTRICT` | `11` |
| `11260` | 중랑구 | `DISTRICT` | `11` |
| `11290` | 성북구 | `DISTRICT` | `11` |
| `11305` | 강북구 | `DISTRICT` | `11` |
| `11320` | 도봉구 | `DISTRICT` | `11` |
| `11350` | 노원구 | `DISTRICT` | `11` |
| `11380` | 은평구 | `DISTRICT` | `11` |
| `11410` | 서대문구 | `DISTRICT` | `11` |
| `11440` | 마포구 | `DISTRICT` | `11` |
| `11470` | 양천구 | `DISTRICT` | `11` |
| `11500` | 강서구 | `DISTRICT` | `11` |
| `11530` | 구로구 | `DISTRICT` | `11` |
| `11545` | 금천구 | `DISTRICT` | `11` |
| `11560` | 영등포구 | `DISTRICT` | `11` |
| `11590` | 동작구 | `DISTRICT` | `11` |
| `11620` | 관악구 | `DISTRICT` | `11` |
| `11650` | 서초구 | `DISTRICT` | `11` |
| `11680` | 강남구 | `DISTRICT` | `11` |
| `11710` | 송파구 | `DISTRICT` | `11` |
| `11740` | 강동구 | `DISTRICT` | `11` |

### 부산광역시 (26)

| 코드 | 이름 | 타입 | 부모 코드 |
| --- | --- | --- | --- |
| `26110` | 중구 | `DISTRICT` | `26` |
| `26140` | 서구 | `DISTRICT` | `26` |
| `26170` | 동구 | `DISTRICT` | `26` |
| `26200` | 영도구 | `DISTRICT` | `26` |
| `26230` | 부산진구 | `DISTRICT` | `26` |
| `26260` | 동래구 | `DISTRICT` | `26` |
| `26290` | 남구 | `DISTRICT` | `26` |
| `26320` | 북구 | `DISTRICT` | `26` |
| `26350` | 해운대구 | `DISTRICT` | `26` |
| `26380` | 사하구 | `DISTRICT` | `26` |
| `26410` | 금정구 | `DISTRICT` | `26` |
| `26440` | 강서구 | `DISTRICT` | `26` |
| `26470` | 연제구 | `DISTRICT` | `26` |
| `26500` | 수영구 | `DISTRICT` | `26` |
| `26530` | 사상구 | `DISTRICT` | `26` |
| `26710` | 기장군 | `DISTRICT` | `26` |

### 대구광역시 (27)

| 코드 | 이름 | 타입 | 부모 코드 |
| --- | --- | --- | --- |
| `27110` | 중구 | `DISTRICT` | `27` |
| `27140` | 동구 | `DISTRICT` | `27` |
| `27170` | 서구 | `DISTRICT` | `27` |
| `27200` | 남구 | `DISTRICT` | `27` |
| `27230` | 북구 | `DISTRICT` | `27` |
| `27260` | 수성구 | `DISTRICT` | `27` |
| `27290` | 달서구 | `DISTRICT` | `27` |
| `27710` | 달성군 | `DISTRICT` | `27` |
| `27720` | 군위군 | `DISTRICT` | `27` |

### 인천광역시 (28)

| 코드 | 이름 | 타입 | 부모 코드 |
| --- | --- | --- | --- |
| `28125` | 제물포구 | `DISTRICT` | `28` |
| `28155` | 영종구 | `DISTRICT` | `28` |
| `28177` | 미추홀구 | `DISTRICT` | `28` |
| `28185` | 연수구 | `DISTRICT` | `28` |
| `28200` | 남동구 | `DISTRICT` | `28` |
| `28237` | 부평구 | `DISTRICT` | `28` |
| `28245` | 계양구 | `DISTRICT` | `28` |
| `28275` | 서해구 | `DISTRICT` | `28` |
| `28290` | 검단구 | `DISTRICT` | `28` |
| `28710` | 강화군 | `DISTRICT` | `28` |
| `28720` | 옹진군 | `DISTRICT` | `28` |

### 광주광역시 (29)

| 코드 | 이름 | 타입 | 부모 코드 |
| --- | --- | --- | --- |
| `29110` | 동구 | `DISTRICT` | `29` |
| `29140` | 서구 | `DISTRICT` | `29` |
| `29155` | 남구 | `DISTRICT` | `29` |
| `29170` | 북구 | `DISTRICT` | `29` |
| `29200` | 광산구 | `DISTRICT` | `29` |

### 대전광역시 (30)

| 코드 | 이름 | 타입 | 부모 코드 |
| --- | --- | --- | --- |
| `30110` | 동구 | `DISTRICT` | `30` |
| `30140` | 중구 | `DISTRICT` | `30` |
| `30170` | 서구 | `DISTRICT` | `30` |
| `30200` | 유성구 | `DISTRICT` | `30` |
| `30230` | 대덕구 | `DISTRICT` | `30` |

### 울산광역시 (31)

| 코드 | 이름 | 타입 | 부모 코드 |
| --- | --- | --- | --- |
| `31110` | 중구 | `DISTRICT` | `31` |
| `31140` | 남구 | `DISTRICT` | `31` |
| `31170` | 동구 | `DISTRICT` | `31` |
| `31200` | 북구 | `DISTRICT` | `31` |
| `31710` | 울주군 | `DISTRICT` | `31` |

### 경기도 (41)

| 코드 | 이름 | 타입 | 부모 코드 |
| --- | --- | --- | --- |
| `41110` | 수원시 | `DISTRICT` | `41` |
| `41130` | 성남시 | `DISTRICT` | `41` |
| `41150` | 의정부시 | `DISTRICT` | `41` |
| `41170` | 안양시 | `DISTRICT` | `41` |
| `41190` | 부천시 | `DISTRICT` | `41` |
| `41210` | 광명시 | `DISTRICT` | `41` |
| `41220` | 평택시 | `DISTRICT` | `41` |
| `41250` | 동두천시 | `DISTRICT` | `41` |
| `41270` | 안산시 | `DISTRICT` | `41` |
| `41280` | 고양시 | `DISTRICT` | `41` |
| `41290` | 과천시 | `DISTRICT` | `41` |
| `41310` | 구리시 | `DISTRICT` | `41` |
| `41360` | 남양주시 | `DISTRICT` | `41` |
| `41370` | 오산시 | `DISTRICT` | `41` |
| `41390` | 시흥시 | `DISTRICT` | `41` |
| `41410` | 군포시 | `DISTRICT` | `41` |
| `41430` | 의왕시 | `DISTRICT` | `41` |
| `41450` | 하남시 | `DISTRICT` | `41` |
| `41460` | 용인시 | `DISTRICT` | `41` |
| `41480` | 파주시 | `DISTRICT` | `41` |
| `41500` | 이천시 | `DISTRICT` | `41` |
| `41550` | 안성시 | `DISTRICT` | `41` |
| `41570` | 김포시 | `DISTRICT` | `41` |
| `41590` | 화성시 | `DISTRICT` | `41` |
| `41610` | 광주시 | `DISTRICT` | `41` |
| `41630` | 양주시 | `DISTRICT` | `41` |
| `41650` | 포천시 | `DISTRICT` | `41` |
| `41670` | 여주시 | `DISTRICT` | `41` |
| `41800` | 연천군 | `DISTRICT` | `41` |
| `41820` | 가평군 | `DISTRICT` | `41` |
| `41830` | 양평군 | `DISTRICT` | `41` |

### 강원특별자치도 (42)

| 코드 | 이름 | 타입 | 부모 코드 |
| --- | --- | --- | --- |
| `51110` | 춘천시 | `DISTRICT` | `42` |
| `51130` | 원주시 | `DISTRICT` | `42` |
| `51150` | 강릉시 | `DISTRICT` | `42` |
| `51170` | 동해시 | `DISTRICT` | `42` |
| `51190` | 태백시 | `DISTRICT` | `42` |
| `51210` | 속초시 | `DISTRICT` | `42` |
| `51230` | 삼척시 | `DISTRICT` | `42` |
| `51720` | 홍천군 | `DISTRICT` | `42` |
| `51730` | 횡성군 | `DISTRICT` | `42` |
| `51750` | 영월군 | `DISTRICT` | `42` |
| `51760` | 평창군 | `DISTRICT` | `42` |
| `51770` | 정선군 | `DISTRICT` | `42` |
| `51780` | 철원군 | `DISTRICT` | `42` |
| `51790` | 화천군 | `DISTRICT` | `42` |
| `51800` | 양구군 | `DISTRICT` | `42` |
| `51810` | 인제군 | `DISTRICT` | `42` |
| `51820` | 고성군 | `DISTRICT` | `42` |
| `51830` | 양양군 | `DISTRICT` | `42` |

### 충청북도 (43)

| 코드 | 이름 | 타입 | 부모 코드 |
| --- | --- | --- | --- |
| `43110` | 청주시 | `DISTRICT` | `43` |
| `43130` | 충주시 | `DISTRICT` | `43` |
| `43150` | 제천시 | `DISTRICT` | `43` |
| `43720` | 보은군 | `DISTRICT` | `43` |
| `43730` | 옥천군 | `DISTRICT` | `43` |
| `43740` | 영동군 | `DISTRICT` | `43` |
| `43745` | 증평군 | `DISTRICT` | `43` |
| `43750` | 진천군 | `DISTRICT` | `43` |
| `43760` | 괴산군 | `DISTRICT` | `43` |
| `43770` | 음성군 | `DISTRICT` | `43` |
| `43800` | 단양군 | `DISTRICT` | `43` |

### 충청남도 (44)

| 코드 | 이름 | 타입 | 부모 코드 |
| --- | --- | --- | --- |
| `44130` | 천안시 | `DISTRICT` | `44` |
| `44150` | 공주시 | `DISTRICT` | `44` |
| `44180` | 보령시 | `DISTRICT` | `44` |
| `44200` | 아산시 | `DISTRICT` | `44` |
| `44210` | 서산시 | `DISTRICT` | `44` |
| `44230` | 논산시 | `DISTRICT` | `44` |
| `44250` | 계룡시 | `DISTRICT` | `44` |
| `44270` | 당진시 | `DISTRICT` | `44` |
| `44710` | 금산군 | `DISTRICT` | `44` |
| `44760` | 부여군 | `DISTRICT` | `44` |
| `44770` | 서천군 | `DISTRICT` | `44` |
| `44790` | 청양군 | `DISTRICT` | `44` |
| `44800` | 홍성군 | `DISTRICT` | `44` |
| `44810` | 예산군 | `DISTRICT` | `44` |
| `44825` | 태안군 | `DISTRICT` | `44` |

### 전북특별자치도 (45)

| 코드 | 이름 | 타입 | 부모 코드 |
| --- | --- | --- | --- |
| `52110` | 전주시 | `DISTRICT` | `45` |
| `52130` | 군산시 | `DISTRICT` | `45` |
| `52140` | 익산시 | `DISTRICT` | `45` |
| `52180` | 정읍시 | `DISTRICT` | `45` |
| `52190` | 남원시 | `DISTRICT` | `45` |
| `52210` | 김제시 | `DISTRICT` | `45` |
| `52710` | 완주군 | `DISTRICT` | `45` |
| `52720` | 진안군 | `DISTRICT` | `45` |
| `52730` | 무주군 | `DISTRICT` | `45` |
| `52740` | 장수군 | `DISTRICT` | `45` |
| `52750` | 임실군 | `DISTRICT` | `45` |
| `52770` | 순창군 | `DISTRICT` | `45` |
| `52790` | 고창군 | `DISTRICT` | `45` |
| `52800` | 부안군 | `DISTRICT` | `45` |

### 전라남도 (46)

| 코드 | 이름 | 타입 | 부모 코드 |
| --- | --- | --- | --- |
| `46110` | 목포시 | `DISTRICT` | `46` |
| `46130` | 여수시 | `DISTRICT` | `46` |
| `46150` | 순천시 | `DISTRICT` | `46` |
| `46170` | 나주시 | `DISTRICT` | `46` |
| `46230` | 광양시 | `DISTRICT` | `46` |
| `46710` | 담양군 | `DISTRICT` | `46` |
| `46720` | 곡성군 | `DISTRICT` | `46` |
| `46730` | 구례군 | `DISTRICT` | `46` |
| `46770` | 고흥군 | `DISTRICT` | `46` |
| `46780` | 보성군 | `DISTRICT` | `46` |
| `46790` | 화순군 | `DISTRICT` | `46` |
| `46800` | 장흥군 | `DISTRICT` | `46` |
| `46810` | 강진군 | `DISTRICT` | `46` |
| `46820` | 해남군 | `DISTRICT` | `46` |
| `46830` | 영암군 | `DISTRICT` | `46` |
| `46840` | 무안군 | `DISTRICT` | `46` |
| `46860` | 함평군 | `DISTRICT` | `46` |
| `46870` | 영광군 | `DISTRICT` | `46` |
| `46880` | 장성군 | `DISTRICT` | `46` |
| `46890` | 완도군 | `DISTRICT` | `46` |
| `46900` | 진도군 | `DISTRICT` | `46` |
| `46910` | 신안군 | `DISTRICT` | `46` |

### 경상북도 (47)

| 코드 | 이름 | 타입 | 부모 코드 |
| --- | --- | --- | --- |
| `47110` | 포항시 | `DISTRICT` | `47` |
| `47130` | 경주시 | `DISTRICT` | `47` |
| `47150` | 김천시 | `DISTRICT` | `47` |
| `47170` | 안동시 | `DISTRICT` | `47` |
| `47190` | 구미시 | `DISTRICT` | `47` |
| `47210` | 영주시 | `DISTRICT` | `47` |
| `47230` | 영천시 | `DISTRICT` | `47` |
| `47250` | 상주시 | `DISTRICT` | `47` |
| `47280` | 문경시 | `DISTRICT` | `47` |
| `47290` | 경산시 | `DISTRICT` | `47` |
| `47730` | 의성군 | `DISTRICT` | `47` |
| `47750` | 청송군 | `DISTRICT` | `47` |
| `47760` | 영양군 | `DISTRICT` | `47` |
| `47770` | 영덕군 | `DISTRICT` | `47` |
| `47820` | 청도군 | `DISTRICT` | `47` |
| `47830` | 고령군 | `DISTRICT` | `47` |
| `47840` | 성주군 | `DISTRICT` | `47` |
| `47850` | 칠곡군 | `DISTRICT` | `47` |
| `47900` | 예천군 | `DISTRICT` | `47` |
| `47920` | 봉화군 | `DISTRICT` | `47` |
| `47930` | 울진군 | `DISTRICT` | `47` |
| `47940` | 울릉군 | `DISTRICT` | `47` |

### 경상남도 (48)

| 코드 | 이름 | 타입 | 부모 코드 |
| --- | --- | --- | --- |
| `48120` | 창원시 | `DISTRICT` | `48` |
| `48170` | 진주시 | `DISTRICT` | `48` |
| `48220` | 통영시 | `DISTRICT` | `48` |
| `48240` | 사천시 | `DISTRICT` | `48` |
| `48250` | 김해시 | `DISTRICT` | `48` |
| `48270` | 밀양시 | `DISTRICT` | `48` |
| `48310` | 거제시 | `DISTRICT` | `48` |
| `48330` | 양산시 | `DISTRICT` | `48` |
| `48720` | 의령군 | `DISTRICT` | `48` |
| `48730` | 함안군 | `DISTRICT` | `48` |
| `48740` | 창녕군 | `DISTRICT` | `48` |
| `48820` | 고성군 | `DISTRICT` | `48` |
| `48840` | 남해군 | `DISTRICT` | `48` |
| `48850` | 하동군 | `DISTRICT` | `48` |
| `48860` | 산청군 | `DISTRICT` | `48` |
| `48870` | 함양군 | `DISTRICT` | `48` |
| `48880` | 거창군 | `DISTRICT` | `48` |
| `48890` | 합천군 | `DISTRICT` | `48` |

### 제주특별자치도 (49)

| 코드 | 이름 | 타입 | 부모 코드 |
| --- | --- | --- | --- |
| `50110` | 제주시 | `DISTRICT` | `49` |
| `50130` | 서귀포시 | `DISTRICT` | `49` |

### 세종특별자치시 (50)

| 코드 | 이름 | 타입 | 부모 코드 |
| --- | --- | --- | --- |
| `36110` | 세종시 | `DISTRICT` | `50` |

## 5. 데이터 관리 규칙

1. Region의 계층은 코드 접두사가 아니라 `parent_id`로 판단한다.
2. 해외 여행 기록은 `COUNTRY`, 대한민국 여행 기록은 `DISTRICT`를 참조한다.
3. 서울·광역시는 자치구·군, 세종은 세종시, 제주는 제주시·서귀포시, 일반 도는 시·군을 저장 단위로 사용한다.
4. 일반 시의 일반구는 여행 기록 Region에 추가하지 않는다.
5. 코드 변경 시 이 문서, DB 마이그레이션, 클라이언트 지도 데이터와 자동화 테스트를 함께 갱신한다.

## 6. 원본과 관련 문서

- 국가 seed: `src/main/resources/db/migration/V6__insert_country.sql`
- 대한민국 시·도 seed: `src/main/resources/db/migration/V7__insert_location_province.sql`
- 대한민국 시·군·구 seed: `src/main/resources/db/migration/V8__insert_location_district.sql`
- Region 통합: `src/main/resources/db/migration/V10__unify_country_and_location_as_region.sql`
- 일반구 통합: `src/main/resources/db/migration/V18__canonicalize_city_district_regions.sql`
- [대한민국 여행 기록 지역 코드표](region-code-table.md)
- [일반구와 시 단위 지역 코드 불일치 트러블슈팅](region-code-troubleshooting.md)
- [지역 코드 체계 ADR](adr/0004-region-code-system.md)
