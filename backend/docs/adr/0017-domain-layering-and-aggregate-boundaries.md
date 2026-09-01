# ADR 0017. 도메인 계층 구조와 애그리거트 경계

- 상태: 채택
- 날짜: 2026-08-31
- 관련: ADR 0013, ADR 0016, PR #200, PR #201(닫힘), `backend/docs/erd.md`

---

## 문제

도메인 규칙을 어디에 둘 것인가. 그리고 엔티티들 사이에 어떤 경계를 그을 것인가.

현재는 경계가 없다. 그 결과 애그리거트가 지켜야 할 불변식이 응용 서비스로 올라와 있고,
서비스가 커질수록 그 안에서 도메인 규칙과 트랜잭션 조율이 섞인다.

---

## 배경

### 엔티티와 리포지토리가 1:1이다

| 엔티티 | 리포지토리 |
|---|---|
| `Member` | `MemberRepository` |
| `Region` | `RegionRepository` |
| `TravelRecord` | `TravelRecordRepository` |
| `RecordMedia` | `RecordMediaRepository` |
| `TravelRecordTag` | `TravelRecordTagRepository` |
| `Tag` | `TagRepository` |
| `RefreshToken` | `RefreshTokenRepository` |
| `LaunchWaitlistEntry` | `LaunchWaitlistRepository` |

엔티티 8개에 리포지토리 8개. **경계가 아예 없다는 뜻이다.**
모든 엔티티가 동등하게 외부에서 직접 조회·저장 가능하므로, 어떤 규칙이 어디서 깨지지 않는지를
코드 구조가 보장하지 못한다.

VO는 `TagName` 하나뿐이다.

### 불변식이 응용 서비스에 올라와 있다

`TravelRecordService`(391줄)에 있던 것들은 대부분 `TravelRecord`가 스스로 지켜야 할 규칙이다.

| 응용 서비스에 있는 로직 | 실제 소속 |
|---|---|
| `validateTravelDates` — 시작일 ≤ 종료일, 미래 금지 | 여행 기간 VO |
| `validateUniqueObjectKeys` — 한 일지 안에서 objectKey 중복 금지 | TravelRecord 애그리거트 |
| `synchronizeMedia` — 미디어 정렬 순서 재계산, 제거분 판별 | TravelRecord 애그리거트 |
| `newObjectKeys` — 기존 미디어와의 차집합 | TravelRecord 애그리거트 |
| `MAX_TAGS_PER_RECORD = 5` (`TravelRecordTagService`) | TravelRecord 애그리거트 |
| `MAX_TAGS_PER_MEMBER = 10` (`TagService`) | Tag 애그리거트 또는 Member |

특히 `synchronizeMedia`는 리포지토리 호출이 마지막 두 줄뿐인 **거의 순수한 도메인 로직**인데
응용 서비스에 있다.

### 계층 분리만으로는 줄어들지 않았다

PR #200(Tag)과 PR #201(TravelRecord)에서 서비스의 웹 DTO 의존을 제거했다.
Tag는 속성이 하나뿐이라 원시 값으로 풀렸고, TravelRecord는 커맨드와 도메인 조합 결과가 필요했다.

TravelRecord 쪽에서 확인한 것은 **DTO를 걷어내도 서비스 본문은 줄지 않는다**는 사실이다.
391줄이 옆으로 옮겨졌을 뿐이다. 응용 서비스가 두꺼운 진짜 원인은 DTO 결합이 아니라
애그리거트가 없다는 것이었다. 이 ADR은 그 후속이다.

### 응답 변환 규칙은 이미 선례가 있다

ADR 0016은 여행 통계에 대해 다음을 채택했다.

> Service는 HTTP 응답 DTO가 아닌 `TravelStatistics` 읽기 모델을 반환하고,
> Controller가 이를 `TravelStatisticsResponse`로 변환한다.

PR #200·#201이 하려던 것과 같은 규칙이 통계 쪽에서는 이미 채택되어 있다.
이 ADR은 그 규칙을 뒤집지 않고, **쓰기 경로의 애그리거트 쪽으로 확장한다.**

### ADR 0013의 유보를 갱신한다

ADR 0013은 "별도 Tags·MemberTags aggregate는 지금 도입하지 않는다"고 결정했다.
그 판단은 **Tag에 한해서는 지금도 유효하다.** 태그는 여전히 개수 제한과 이름 중복 검증뿐이다.

다만 그 ADR이 재검토 조건으로 든 "컬렉션 자체의 행위"가 `TravelRecord`에서는 이미 나타났다.
미디어 정렬 순서 재계산, 태그 전체 교체, objectKey 중복 판별이 그것이다.
따라서 이 ADR은 0013을 뒤집지 않고, **적용 대상을 TravelRecord로 한정해 확장한다.**

---

## 결정

### 1. 애그리거트 경계를 다음과 같이 정한다

| 애그리거트 루트 | 내부 엔티티 | 근거 |
|---|---|---|
| **TravelRecord** | `RecordMedia`, `TravelRecordTag` | 일지 없이 존재 의미가 없고, 일지와 함께 삭제되며, 정렬 순서·개수 제한이 일지 내부 불변식이다 |
| **Tag** | 없음 | 회원 소유의 독립 수명주기. 일지에서 삭제해도 태그는 남는다 |
| **Member** | 없음 | — |
| **RefreshToken** | 없음 | 회원과 수명주기가 다르고 인증 관심사다. Member 내부로 넣지 않는다 |
| **LaunchWaitlistEntry** | 없음 | — |

`Region`은 애그리거트가 아니라 **참조 데이터**로 취급한다.
쓰기 경로가 마이그레이션뿐이고 전 서비스가 공유한다. 다른 애그리거트는 `Region`을 참조로만 갖는다.

### 2. 애그리거트 사이는 ID로 참조한다

`TravelRecord`가 `Tag` 컬렉션을 직접 들지 않는다. 이는 ADR 0013의 결정과 같으며 유지한다.
`TravelRecordTag`는 TravelRecord 애그리거트 내부의 연관 엔티티로, `Tag`의 **ID만** 참조한다.

### 3. 리포지토리는 애그리거트 루트당 하나만 둔다

`RecordMediaRepository`와 `TravelRecordTagRepository`는 애그리거트 내부 엔티티의 리포지토리이므로
외부에서 직접 쓰지 않는다. 조회는 `TravelRecordRepository`를 통하거나 루트를 거친다.

**단, `TravelRecordTag`의 쓰기는 리포지토리에 남긴다.** ADR 0013이 태그 교체를 위해
`@Modifying` 벌크 DELETE 한 문장을 고른 것은 성능을 근거로 한 결정이었다. 컬렉션 cascade로
바꾸면 같은 작업이 행 단위 DELETE N개가 되어 그 결정을 근거 없이 되돌리게 된다.
연결 엔티티는 정렬 순서 같은 컬렉션 자체의 불변식도 없어 루트가 소유해서 얻는 것이 적다.
이 예외는 `TravelRecordTag`에 한하며, 컬렉션 자체의 규칙이 생기면 다시 검토한다.

**조회 전용 집계 리포지토리도 이 규칙의 예외로 둔다.** `TravelStatisticsRepository`(ADR 0016)처럼
애그리거트 상태를 바꾸지 않고 원본 테이블을 집계만 하는 리포지토리는 루트당 하나 제한을 적용하지 않는다.
제한의 목적은 불변식이 깨질 수 있는 **쓰기 경로**를 좁히는 것이지 조회 수단을 줄이는 것이 아니다.

지금 프로덕션에서 호출되지 않는 리포지토리 메서드 4개는 이 단계에서 제거한다.

- `TravelRecordRepository.findByMemberId`
- `TravelRecordRepository.findByMemberIdAndCountryId`
- `TravelRecordRepository.findByMemberIdAndProvinceId`
- `TravelRecordRepository.findByMemberIdAndRegionId`

### 4. 어셈블러는 애그리거트당 하나, 응용 계층에 둔다

읽기 모델 조립이 필요하면 **애그리거트 루트당 어셈블러 하나**로 맞춘다.
`TravelRecordAssembler`가 TravelRecord 애그리거트의 모든 읽기 모델을 조립한다.

축을 "유스케이스당 하나"가 아니라 "애그리거트당 하나"로 잡는 이유는, 전자는 엔드포인트 수만큼
클래스가 늘지만 후자는 도메인 크기만큼만 늘기 때문이다. 조립 책임의 소재도 한 곳으로 고정된다.

두 가지 단서를 붙인다.

- **애그리거트당 최대 하나**다. 읽기 모델이 단순한 애그리거트(Tag)는 어셈블러를 만들지 않는다.
  통일성을 위해 빈 클래스를 두지 않는다.
- 하나의 애그리거트에 읽기 모양이 많아져 어셈블러가 비대해지면 읽기 모델별로 쪼개되,
  **해당 애그리거트 패키지 안에** 둔다.

어셈블러는 리포지토리를 호출하고 presigned URL 같은 인프라 산출물을 붙이므로 **애그리거트 내부가 아니다.**
애그리거트는 I/O를 하지 않는다. 어셈블러는 응용 계층에 속한다.

`travelrecord.statistics`(ADR 0016)는 어셈블러를 두지 않는다. 자체 집계 쿼리와 읽기 모델을 갖는
조회 전용 모듈이므로, 애그리거트 상태를 조립하는 어셈블러와는 역할이 다르다.

애그리거트가 제자리를 찾을수록 어셈블러는 얇아진다. `synchronizeMedia`가 `TravelRecord`로 들어가면
어셈블러에는 조회와 URL 장식만 남는다. 이것이 목표 상태다.

### 5. 도메인 서비스는 선제적으로 만들지 않는다

`XxxDomainService`를 계층으로 먼저 만들면 갈 곳 없는 로직의 적재소가 된다.

다음 조건을 **둘 다** 만족할 때만 만든다.

- 규칙이 애그리거트 하나에 담기지 않는다 (둘 이상에 걸치거나 리포지토리 조회가 반드시 필요하다)
- 그 규칙을 응용 서비스에 두면 여러 유스케이스에 중복된다

현재 이 조건을 만족하는 것은 `RegionResolver` 정도다. 태그 이름 중복 검증은 후보이나
지금은 `TagService`에서 충분히 드러나므로(ADR 0013) 옮기지 않는다.

### 6. 패키지는 `domain` / `application`으로 가르지 않는다

현재의 기능별 패키지(`travelrecord/`, `tag/`)를 유지하고, 그 안에서 **이름과 의존 규칙으로** 구분한다.

```
travelrecord/
├── TravelRecord.java             # 애그리거트 루트
├── TravelPeriod.java             # VO
├── RecordMedia.java              # 애그리거트 내부 엔티티
├── TravelRecordTag.java          # 애그리거트 내부 엔티티
├── TravelRecordRepository.java   # 애그리거트당 하나
├── TravelRecordCommand.java      # 응용 계층 입력
├── TravelRecordService.java      # 응용 서비스 (트랜잭션 · 조율)
├── TravelRecordAssembler.java    # 응용 계층 조립
├── TravelRecordController.java
├── dto/                          # 웹 계약
└── statistics/                   # 조회 전용 모듈 (ADR 0016)
```

의존 규칙은 다음 한 줄이다.

> `Controller → Service → Aggregate`. 역방향과 `Aggregate → dto`는 금지한다.

폴더를 가르는 대신 규칙을 세우는 이유는, 폴더 이동은 diff가 크고 다른 브랜치와 충돌하는 반면
얻는 것은 가독성뿐이기 때문이다. 규칙 위반은 폴더가 아니라 리뷰와 import로 잡는다.

`RecordMedia`(엔티티)는 TravelRecord 애그리거트 내부이므로 `travelrecord` 패키지로 옮긴다.
`RecordMediaUrlService`(presigned URL 발급)는 인프라 관심사이므로 `recordmedia`에 남는다.

**엔티티를 옮기는 것만으로는 순환 참조가 끊기지 않는다.** `RecordMediaUrlService`가 목록 썸네일을
만들기 위해 `RecordMedia`와 그 리포지토리를 쓰고 있어서, 엔티티를 옮기면 방향만 뒤집힐 뿐이다.
썸네일 조회를 루트 리포지토리로 올리고 URL 서비스가 **Object Key 하나를 URL로 바꾸는 일만** 하도록
줄여야 끊긴다. 그 결과 `recordmedia`에는 `ExpiringUrl`과 `RecordMediaUrlService`만 남고,
`RecordMediaRepository`는 사라진다.

### 7. 단계적으로 적용한다

경계를 먼저 확정하고(이 ADR), 코드는 되돌리기 쉬운 것부터 바꾼다.
각 단계를 하나의 PR로 하고, 한 PR에 둘 이상을 섞지 않는다.

| 단계 | 작업 | 난이도 | 되돌리기 | 실질 이득 |
|---|---|---|---|---|
| 1 | VO 추출 — `TravelPeriod`, `ObjectKey` | 낮음 | 쉬움 | 중 |
| 2 | 불변식을 애그리거트로 — `syncMedia`, 태그 개수 제한 | 중간 | 보통 | **높음** |
| 3 | 리포지토리 정리 — 미디어 컬렉션 소유, 죽은 메서드 제거, 생성 경로 검증 정합 | 중간 | 어려움 | 중 |
| 4 | `RecordMedia` 패키지 이동과 순환 참조 해소 | 중간 | 보통 | 낮음 |

1단계를 먼저 두는 이유는 `TagName`이라는 선례가 이미 있어 판단 기준이 서 있고,
`validateTravelDates`가 `TravelPeriod`로 들어가면 응용 서비스에서 검증 메서드가 통째로 빠지기 때문이다.

2단계가 실질 이득의 대부분이다. 3단계는 쿼리 실행 계획에 영향을 주므로 측정을 동반한다.
4단계는 파일 이동이 대부분이라 리뷰는 쉽지만 diff가 크므로, 진행 중인 다른 작업이 없을 때 한다.
순수 이동만으로 끝나지 않는다는 점은 결정 6에 적었다.

3단계에서 함께 정리할 항목은 다음과 같다.

- **생성 경로의 Object Key 중복 검증.** 수정 경로에는 `validateObjectKeys`가 있으나 생성 경로에는
  없어 DB UNIQUE 제약에 의존한다. 같은 규칙이 경로에 따라 다르게 적용되는 상태다.
  애그리거트가 미디어 컬렉션을 소유하면 두 경로가 같은 지점을 지나므로 그때 함께 맞춘다.
  중복 요청이 500 대신 400으로 바뀌는 동작 변경이므로 단독으로 처리하지 않는다.
- **`synchronizeMedia`의 현재 미디어 인자 제거.** 2단계에서는 애그리거트가 컬렉션을 소유하지 않아
  현재 미디어를 인자로 받는다. 리포지토리를 흡수하면서 인자를 없앤다.
- **`TravelRecordTag`는 대상이 아니다.** 위 결정 3의 예외에 따라 벌크 DELETE를 유지한다.

### 2026-09-01 보완: 3단계 실측 결과

미디어 컬렉션 소유가 쿼리를 늘리지 않는지 MySQL 8.4에서 실제 생성 SQL로 확인했다.

| 경로 | 결과 |
|---|---|
| 목록 조회 | `record_media` 쿼리 0회. 컬렉션이 LAZY라 목록 경로가 건드리지 않는다 |
| 상세 조회 | 미디어 조회 1회. 기존 `findByTravelRecordIdOrderBySortOrderAsc`와 같다 |
| 미디어 동기화 | insert 1 + update 1 + delete 1. 기존 `saveAll`·`deleteAll`과 같다 |
| Object Key 존재 확인 | 조인 없이 `object_key` 인덱스를 탄다. 엔티티를 만들지 않는 count 조회로 바뀌었다 |

가장 큰 위험이던 목록 조회 N+1은 발생하지 않는다. 다만 이는 목록 경로가 썸네일을
`RecordMediaUrlService`의 일괄 조회로 가져오기 때문이며, **목록에서 `getMedia()`를 호출하는
코드가 생기면 즉시 N+1이 된다.** 목록 응답에 미디어를 더 노출해야 할 때 이 지점을 다시 본다.

---

## 검토한 대안

| 대안 | 기각 사유 |
|---|---|
| 전면 CQRS (읽기·쓰기 모델과 저장소 분리) | 단일 DB에 읽기 부하 문제가 없다. 결과적 일관성이라는 비용만 남는다 |
| 상세·목록도 조회 전용 모듈로 분리 | 조회 전용 모듈 자체는 `travelrecord.statistics`로 이미 존재한다. 다만 상세·목록은 수정 후 재조회가 필요해 쿼리가 늘고, 쓰기·읽기 트랜잭션이 갈리면서 "수정 성공 후 404" 엣지 케이스가 생긴다. 목록 조회가 프로젝션 쿼리를 요구할 때 재검토한다 |
| 어셈블러를 애그리거트 내부에 배치 | 애그리거트가 리포지토리와 presigned URL 발급을 알게 된다 |
| 어셈블러를 유스케이스당 하나로 | 엔드포인트 수만큼 클래스가 늘고 조립 책임의 소재가 흩어진다 |
| 패키지를 `domain` / `application`으로 분리 | 이득이 가독성뿐인데 diff가 크고 충돌을 유발한다. 규칙으로 대체한다 |
| `Region`을 애그리거트로 승격 | 쓰기 경로가 마이그레이션뿐이다. 참조 데이터로 두는 편이 정확하다 |
| `RefreshToken`을 Member 애그리거트 내부로 | 수명주기와 회전 규칙이 회원과 무관하다 |

---

## 결과

### 장점

- 불변식이 깨질 수 있는 지점이 애그리거트 루트로 좁혀진다.
- 응용 서비스가 트랜잭션 경계와 조율만 남기고 얇아진다.
- 도메인 규칙을 리포지토리 없이 단위 테스트할 수 있다.
- 어셈블러의 위치와 개수 기준이 고정되어 논쟁이 반복되지 않는다.

### 비용과 주의점

- **애그리거트 내부 엔티티를 루트 경유로만 다루면 쿼리가 늘 수 있다.** 3단계에서 실행 계획을 측정한다.
- `RecordMedia` 패키지 이동은 `upload`·`recordmedia`와의 경계를 다시 확인해야 한다.
- 이 ADR은 **TravelRecord에만 적용한다.** Tag·Member에 같은 구조를 선제 적용하지 않는다.
  ADR 0013의 유보 근거가 그대로 유효하다.
- 단계별 PR 사이에 다른 기능 작업이 들어오면 충돌한다. 1~2단계는 연속으로 처리하는 편이 낫다.

---

## 미해결 사항

- **`findAll`의 지역 계층 분기.** 지역 레벨에 따라 리포지토리 메서드 3개를 골라 부르는데,
  JPQL이 region 조건 한 줄을 빼면 동일하다. 통합 가능해 보이나 `region.id` / `parent.id` / `root.id`로
  조건이 달라 별도 검토가 필요하다. 이 ADR의 범위 밖이다.
- **수정 API의 응답 형태.** `PUT`이 전체 상세를 반환해서 어셈블러가 쓰기 트랜잭션 안으로 끌려 들어온다.
  클라이언트가 수정 직후 화면을 다시 그리지 않아도 된다면 응답을 줄이는 편이 구조상 유리하다. 기획·안드로이드 확인 필요.
- **폴더 분리 재검토 시점.** 팀이 `domain` / `application` 폴더를 명시적으로 원하면 4단계 이후 언제든 가능하다.
- **태그 개수 제한(회원 10개, 기록 5개)** 은 ADR 0013대로 여전히 잠정값이다. 2단계에서 위치만 옮기고 값은 바꾸지 않는다.

---

## 2026-09-01 보완: 4단계 실측 결과

썸네일 조회를 `RecordMediaRepository`의 파생 쿼리에서 `TravelRecordRepository`의 명시 쿼리로
옮겼다. 생성 SQL은 컬럼·WHERE·ORDER BY까지 이전과 같다.

```sql
select rm1_0.id, rm1_0.created_at, rm1_0.object_key,
       rm1_0.sort_order, rm1_0.thumb_key, rm1_0.travel_record_id
from record_media rm1_0
where rm1_0.travel_record_id in (?)
order by rm1_0.travel_record_id, rm1_0.sort_order, rm1_0.id
```

4단계까지 마치면 패키지 의존은 `travelrecord → recordmedia` 한 방향만 남는다.

### 뒤늦게 확인한 3단계의 대가: 삭제 SQL

3단계에서 목록·상세·동기화·존재 확인은 측정했으나 **삭제 경로를 빠뜨렸다.**
4단계 병합 직전에 다시 재보니 문장 수가 늘어 있었다.

```sql
-- 이전: DB의 ON DELETE CASCADE 가 처리한다
delete from travel_record where id=?

-- 이후: JPA cascade 가 처리한다
select ... from record_media where travel_record_id=?   -- 컬렉션 로딩
delete from record_media where id=?                      -- 미디어마다 한 문장
delete from travel_record where id=?
```

1문장에서 **N+2문장**이 됐다. 애그리거트가 컬렉션을 소유하면 JPA가 삭제를 관리하므로
되돌리려면 소유 자체를 포기해야 한다. 즉 결정 1의 대가이지 버그가 아니다.

**수용한다.** N이 일지당 사진 수로 묶여 있고 삭제는 드문 연산이다.
`record_media`의 `ON DELETE CASCADE`는 안전망으로 남는다.

다만 다음 두 경우에는 다시 검토한다.

- 일지를 대량으로 지우는 배치가 생길 때
- 일지당 미디어 수 상한이 크게 늘 때

**측정에서 얻은 교훈:** 애그리거트 소유 관계를 바꿀 때는 조회뿐 아니라 **삭제 경로도 함께**
측정한다. 조회는 LAZY로 막을 수 있지만 삭제는 cascade가 반드시 개입한다.
