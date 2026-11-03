# ADR 0028. 회원과 refresh 토큰은 회원 → refresh 순서로 잠근다

- 상태: 제안
- 날짜: 2026-11-03
- 관련: #574, #579, ADR 0021, ADR 0023

## 문제

같은 회원의 `member` 행과 `refresh_token` 행을 한 트랜잭션에서 함께 잠그는 흐름이 여럿이다.
두 흐름이 서로 반대 순서로 잠그면 각자 상대가 가진 잠금을 기다리다 데드락이 나고, 한쪽이 `CannotAcquireLockException`(500)이 된다.

순서가 코드에 드러나지 않는 것이 문제를 키운다.

- **FK 확인이 회원을 잠근다.** `refresh_token`을 INSERT하면 InnoDB가 `fk_refresh_member` 확인을 위해 `member` 행에 공유 잠금을 건다. 회전은 refresh를 잠근 뒤 새 refresh를 넣으므로, 코드에 회원 조회가 없어도 refresh → 회원 순서가 된다.
- **회원 UPDATE는 커밋 때 나간다.** 엔티티를 바꾸면 Hibernate가 커밋 직전에 UPDATE를 보낸다. 그 사이 `MemberWithdrawn` 같은 이벤트로 refresh를 일괄 폐기하면(JPQL 일괄 UPDATE는 다른 테이블의 변경을 먼저 flush하지 않는다) 순서는 refresh → 회원이다.
- **이벤트 리스너가 같은 트랜잭션에 들어온다.** `MemberRefreshTokenRevoker`는 동기 `@EventListener`라 회원 쪽 서비스의 트랜잭션 안에서 refresh를 잠근다.

실제로 생긴 데드락은 다음과 같다.

- 게스트 승격(#575, 회원 `FOR UPDATE` → refresh 일괄 폐기)과 refresh 회전(refresh `FOR UPDATE` → FK로 회원): 60회 중 55회
- 회전을 회원 먼저로 바꾼 뒤(#579) 잠금 없는 탈퇴(refresh 일괄 폐기 → 커밋 때 회원 UPDATE)와 회전: 40회 중 39회

## 결정

### 같은 회원의 회원 행과 refresh 행을 함께 잠그면 회원을 먼저 잠근다

트랜잭션의 첫 잠금으로 회원 행을 명시적으로 잠그고, 그다음 refresh 행을 잠근다.

- **회원을 바꾸는 흐름**(승격·버림·탈퇴)은 회원을 `FOR UPDATE`(`MemberRepository#findByIdForUpdate`)로 잠근다.
- **회원을 읽기만 하는 흐름**(refresh 회전)은 회원을 `FOR SHARE`(`MemberRepository#findByIdForShare`)로 잠근다. 같은 회원의 회전끼리는 서로 막지 않는다.
- refresh 행끼리는 **제시된 토큰 → 후속 토큰** 순서로 잠근다(ADR 0023, #462).
- 회원 행을 건드리지 않고 refresh 행만 잠그는 흐름은 회원을 잠그지 않아도 된다. 로그아웃(`RefreshTokenService#revoke`)은 refresh를 UPDATE만 하고 INSERT하지 않으므로 회원 잠금이 생기지 않는다.

| 흐름 | 회원 | refresh |
|---|---|---|
| 게스트 승격·버림 (`MemberJoinService#findGuest`) | `FOR UPDATE` | 일괄 폐기 |
| 탈퇴 (`MemberService#withdraw`, `#withdrawUnlinked`) | `FOR UPDATE` | 일괄 폐기 |
| refresh 회전 (`RefreshTokenService#rotate`) | `FOR SHARE` | 제시 토큰 `FOR UPDATE` → 후속 토큰 `FOR UPDATE`, 새 토큰 INSERT |
| 로그아웃 (`RefreshTokenService#revoke`) | 잠그지 않음 | 제시 토큰 `FOR UPDATE` → 후속 토큰 `FOR UPDATE` |
| 재사용 판정 (`RefreshTokenService#handleReuse`, 회전 안에서) | `FOR SHARE` | 제시 토큰 `FOR UPDATE` → 후속 토큰 `FOR UPDATE` → **회원의 refresh 전체 일괄 UPDATE** (아래 예외) |

### 예외: 재사용 판정의 일괄 폐기는 refresh끼리의 순서를 따르지 않는다

회전된 토큰이 다시 와 탈취로 판정하면 `handleReuse`가 `revokeAllActiveByMember`로 회원의 refresh를 모두 폐기한다.
이 일괄 UPDATE는 `idx_refresh_member`로 그 회원의 행을 훑으며 잠근다. REPEATABLE READ에서는 `revoked_at` 조건에 맞지 않아도
훑은 행의 잠금을 풀지 않으므로, 이미 폐기된 **앞선 토큰**까지 잠근다. "제시 토큰 → 후속 토큰" 순서를 따르지 않는다.

그래서 체인 P → R → S에서 두 요청이 겹치면 드물게 데드락이 날 수 있다.

- 로그아웃(P): P 잠금 → 후속 R 잠금 대기
- 재사용(R): R 잠금 → 후속 S 잠금 → 일괄 폐기가 P 잠금 대기

피해자와 탈취자가 서로 다른 회전된 토큰으로 동시에 재사용해도 같다. 이 시나리오는 코드를 분석해 얻은 결론이고 재현하지는 않았다.
탈취 판정이라는 드문 경로와 겹쳐야 하므로 지금은 두고, 세션(계열) 단위 폐기로 바꿀 때(#467) 함께 정리한다.

### 회전은 회원 id를 잠그지 않는 조회로 먼저 얻는다

회전은 토큰 원문만 받으므로 회원 id를 모른다. `RefreshTokenRepository#findMemberIdByTokenHash`로 회원 id를 읽고, 회원을 공유 잠금한 뒤 refresh를 `FOR UPDATE`로 다시 읽는다.
첫 조회가 REPEATABLE READ 스냅샷을 만들지만, 뒤의 두 조회는 잠금 조회라 최신 커밋을 읽는다. 폐기 여부와 회원 상태는 잠근 뒤의 값으로 판단한다.

### 새 흐름을 추가할 때

- 같은 회원의 `member`와 `refresh_token`을 한 트랜잭션에서 바꾸거나 잠그면 이 순서를 따른다.
- refresh를 INSERT하는 것도 회원을 잠그는 일이다. refresh를 먼저 잠그고 INSERT하는 흐름을 만들지 않는다.
- 회원 이벤트(`GuestPromoted`, `GuestAbandoned`, `MemberWithdrawn`)를 발행하는 서비스는 발행 전에 회원을 `FOR UPDATE`로 잠근다. 리스너가 같은 트랜잭션에서 refresh를 잠근다.
- 두 회원을 함께 잠그면 **게스트 → 기존 회원** 순서로 잠근다. 지금 두 회원을 잠그는 흐름은 버림 하나다
  (게스트 `FOR UPDATE` → 게스트 refresh 일괄 폐기 → 기존 회원에게 refresh INSERT로 기존 회원 공유 잠금).
  기존 회원을 먼저 잠그고 게스트를 잠그는 흐름이 생기면 버림과 데드락이 난다.
  예: ADR 0021이 열어 둔 "버려진 게스트의 기록을 기존 회원에게 합치기"를 만들 때 이 순서를 따른다.

## 검토한 대안

### 모든 흐름이 refresh를 먼저 잠근다

회전의 원래 순서다. 하지만 승격은 `findGuest`의 잠금 조회가 트랜잭션의 첫 조회여야 먼저 끝난 요청의 커밋을 본다(#574). 승격 전에 회원의 refresh를 모두 잠그면 그 조회가 첫 조회가 아니게 되고, 잠글 refresh 목록도 회원을 읽어야 안다.
회원이 refresh의 주인이라 회원 → refresh가 자연스럽다.

### 데드락이 나면 재시도한다

순서를 맞추지 않고 `CannotAcquireLockException`을 잡아 재시도한다. 원인을 그대로 두고, 재시도가 끝날 때까지 실패가 반복될 수 있으며, 회전처럼 응답을 놓치면 안 되는 흐름에서 재시도 중 상태를 다시 판단해야 한다.

### refresh 폐기를 커밋 뒤로 미룬다 (최종 일관성)

회원 변경은 커밋하고, refresh 폐기는 `AFTER_COMMIT` 리스너나 Outbox로 별도 트랜잭션에서 한다. 회원 잠금이 풀린 뒤 refresh를 잠그므로 데드락은 사라진다.
같은 트랜잭션 안에서 폐기만 뒤로 미루는 것은 해결이 아니다. 승격·탈퇴는 처음부터 회원을 잠그고 있어 순서가 그대로다.

택하지 않았다. 늦게 맞는 동안 권한이 샌다.

- 승격된 회원은 활동 중이라 회전의 `isActive()` 검사가 막지 못한다. 폐기가 늦거나 실패하면 게스트 refresh로 소셜 회원 세션이 이어진다.
- 탈퇴는 `isActive()` 검사가 한 번 더 막지만, 그것은 이중 안전장치다. 주 장치가 늦게 맞으면 안 된다.
- 최종 일관성으로 맞추려면 폐기 실패 재시도와 Outbox, 폐기 전까지 막는 검사가 필요하다. 순서를 맞추는 것보다 복잡하다.

**기준: 권한을 바꾸는 상태(회원 상태와 세션)는 한 트랜잭션에서 강하게 맞춘다. 늦게 맞아도 아무도 손해 보지 않는 부수 효과(알림 발송, 통계)는 최종 일관성으로 둔다(ADR 0027).**

권한에 닿는 부수 효과(예: #547 탈퇴 회원의 그룹 탈퇴)를 최종 일관성으로 둘 때는, 늦게 맞는 동안 그 경로를 막는 가드
(`ActiveMemberGuard`, 그룹의 `SocialMemberGuard`)가 있어야 한다. 가드가 없으면 그 사이 권한이 샌다.

### 낙관적 락(`@Version`)

회원 행 충돌은 막지만, FK 공유 잠금과 refresh 일괄 UPDATE의 행 잠금은 그대로라 데드락은 남는다. 실패 시 재시도 코드도 필요하다. 마이그레이션도 든다.

## 결과

### 장점

- 회원과 refresh를 함께 잠그는 흐름 사이에 데드락이 생기지 않는다.
- 같은 회원의 탈퇴 요청이 직렬화된다. 동시에 두 번 와도 늦은 쪽은 탈퇴된 회원을 읽어 `MemberWithdrawn`이 한 번만 발행된다.

### 비용과 주의점

- 회전 트랜잭션 동안 같은 회원 행을 바꾸는 요청(닉네임 변경 등)은 회전이 끝날 때까지 기다린다. 회전은 짧은 트랜잭션이라 감수한다.
- 회전마다 잠그지 않는 조회가 하나 늘어난다.
- 순서는 코드 규칙이라 컴파일러가 잡아 주지 않는다. 새 흐름은 위 표에 추가하고, 동시성 테스트로 데드락이 없음을 확인한다.
- 이 ADR은 `member`와 `refresh_token`만 다룬다. 하지만 `member`를 FK로 참조하는 테이블에 INSERT하면 회원 행에 공유 잠금이 걸린다(MySQL 외래 키 검사).
  회원과 그런 테이블을 함께 잠그는 새 흐름도 회원을 먼저 잠근다. 그런 흐름이 생기면 이 ADR을 넓힌다.

## 검증

- `GuestPromotionRefreshIntegrationTest`: 게스트의 refresh 갱신과 승격, 갱신과 버림을 각각 20회 동시에 보내도 데드락이 없다. 버림은 게스트와 기존 회원 두 행을 잠그는 유일한 흐름이라 따로 본다.
- 로그아웃은 회원을 잠그지 않고 refresh를 INSERT하지 않아 회원 잠금을 기다릴 일이 없으므로, **회원 잠금에 대해서는** 동시성 테스트를 두지 않는다.
  로그아웃에 회원 잠금이나 INSERT가 생기면 테스트를 추가한다. refresh 행끼리는 위 "예외"의 데드락 가능성이 있으며, 이는 #467에서 다룬다.
- `WithdrawalRefreshRotationIntegrationTest`: 탈퇴와 refresh 갱신을 20회 동시에 보내도 데드락이 없고, 쓸 수 있는 refresh가 남지 않는다.
- SQL 로그에서 회전은 `select member_id …` → `… from member … for share of m1_0` → `… from refresh_token … for update of rt1_0` 순서로 나간다.
