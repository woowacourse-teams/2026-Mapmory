package com.mapmory.backend.travelrecord.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willReturn;

import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.common.exception.AcceptedLockConflictException;
import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import com.mapmory.backend.recordmedia.ExpiringUrl;
import com.mapmory.backend.tag.Tag;
import com.mapmory.backend.tag.TagRepository;
import com.mapmory.backend.tag.TagService;
import com.mapmory.backend.upload.storage.S3StorageProperties;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 같은 기록을 두 곳(두 기기, 같은 요청의 재전송)에서 동시에 고쳐도 한 요청의 결과만 남는지 실제 DB로 확인한다.
 *
 * <p>수정은 기록과 지금 붙은 사진·태그를 읽고, 요청과 비교해 빠진 것만 떼고 새것만 붙인다.
 * 두 요청이 같은 옛 상태를 읽으면 몫이 합쳐지거나(상한 초과), 같은 행을 두 번 넣고 지우다 실패하거나, 서로의 잠금을 기다려 데드락이 난다.
 * 한 번으로는 겹치지 않을 수 있어 기록을 바꿔 여러 번 동시에 출발시킨다.
 */
class TravelRecordConcurrentUpdateIntegrationTest extends IntegrationTest {

    private static final int ROUNDS = 20;
    private static final int MAX_TAGS = 5;
    private static final int MAX_PHOTOS = 100;
    private static final String OK = "OK";
    private static final String RETRY_LATER = "RETRY_LATER";

    @Autowired
    private TravelRecordService travelRecordService;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private TagService tagService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private S3StorageProperties s3StorageProperties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        willReturn(true).given(uploadedObjectChecker).exists(anyString());
        willAnswer(invocation -> new ExpiringUrl("https://cdn.test/" + invocation.getArgument(0), 300))
                .given(recordMediaUrlService).createViewUrl(anyString());
    }

    @Test
    void 제목과_사진을_다르게_동시에_고치면_한_요청의_결과만_남는다() throws Exception {
        List<String> problems = new ArrayList<>();
        for (int round = 0; round < ROUNDS; round++) {
            Member member = socialMember();
            String kept = photoKey(member);
            Long recordId = create(member, List.of(kept));
            List<String> photosA = List.of(kept, photoKey(member));
            List<String> photosB = List.of(kept, photoKey(member));

            List<String> outcomes = runAtOnce(List.of(
                    () -> travelRecordService.update(member, recordId, command("A", photosA, List.of())),
                    () -> travelRecordService.update(member, recordId, command("B", photosB, List.of()))));

            String title = jdbcTemplate.queryForObject(
                    "SELECT title FROM travel_record WHERE id = ?", String.class, recordId);
            List<String> media = mediaOf(recordId);
            boolean whole = ("A".equals(title) && sameSet(media, photosA))
                    || ("B".equals(title) && sameSet(media, photosB));
            if (!outcomes.equals(List.of(OK, OK)) || !whole) {
                problems.add("round " + round + ": " + outcomes + " 제목 " + title + ", 사진 " + media.size() + "장");
            }
        }

        assertThat(problems).isEmpty();
    }

    @Test
    void 새_사진을_붙이는_같은_요청이_동시에_두_번_와도_둘_다_성공한다() throws Exception {
        List<String> problems = new ArrayList<>();
        for (int round = 0; round < ROUNDS; round++) {
            Member member = socialMember();
            String kept = photoKey(member);
            Long recordId = create(member, List.of(kept));
            List<String> photos = List.of(kept, photoKey(member));

            List<String> outcomes = runAtOnce(List.of(
                    () -> travelRecordService.update(member, recordId, command("여행", photos, List.of())),
                    () -> travelRecordService.update(member, recordId, command("여행", photos, List.of()))));

            if (!outcomes.equals(List.of(OK, OK)) || !sameSet(mediaOf(recordId), photos)) {
                problems.add("round " + round + ": " + outcomes + " 사진 " + mediaOf(recordId).size() + "장");
            }
        }

        assertThat(problems).isEmpty();
    }

    @Test
    void 사진을_빼는_같은_요청이_동시에_두_번_와도_둘_다_성공한다() throws Exception {
        List<String> problems = new ArrayList<>();
        for (int round = 0; round < ROUNDS; round++) {
            Member member = socialMember();
            String first = photoKey(member);
            Long recordId = create(member, List.of(first, photoKey(member)));

            List<String> outcomes = runAtOnce(List.of(
                    () -> travelRecordService.update(member, recordId, command("여행", List.of(first), List.of())),
                    () -> travelRecordService.update(member, recordId, command("여행", List.of(first), List.of()))));

            if (!outcomes.equals(List.of(OK, OK)) || !mediaOf(recordId).equals(List.of(first))) {
                problems.add("round " + round + ": " + outcomes + " 사진 " + mediaOf(recordId).size() + "장");
            }
        }

        assertThat(problems).isEmpty();
    }

    /**
     * 빼는 쪽이 먼저 끝나면 뺀 사진은 정리 큐에 있어, 늦은 쪽이 그 사진을 다시 붙이려다 INVALID_OBJECT_KEY로 거절된다(ADR 0018).
     * 늦은 쪽이 빼는 쪽이면 둘 다 성공한다. 어느 순서든 500 없이 뺀 결과만 남는다.
     */
    @Test
    void 한쪽이_뺀_사진의_순서를_다른_쪽이_바꿔도_500_없이_뺀_결과만_남는다() throws Exception {
        List<String> problems = new ArrayList<>();
        for (int round = 0; round < ROUNDS; round++) {
            Member member = socialMember();
            String first = photoKey(member);
            String second = photoKey(member);
            Long recordId = create(member, List.of(first, second));

            List<String> outcomes = runAtOnce(List.of(
                    () -> travelRecordService.update(member, recordId, command("여행", List.of(first), List.of())),
                    () -> travelRecordService.update(member, recordId,
                            command("여행", List.of(second, first), List.of()))));

            List<String> media = mediaOf(recordId);
            boolean expectedOutcomes = OK.equals(outcomes.get(0))
                    && (OK.equals(outcomes.get(1)) || "INVALID_OBJECT_KEY".equals(outcomes.get(1)));
            if (!expectedOutcomes || !media.equals(List.of(first))) {
                problems.add("round " + round + ": " + outcomes + " 사진 " + media.size() + "장");
            }
        }

        assertThat(problems).isEmpty();
    }

    @Test
    void 다른_태그_다섯_개씩_동시에_붙여도_다섯_개를_넘지_않는다() throws Exception {
        List<String> problems = new ArrayList<>();
        for (int round = 0; round < ROUNDS; round++) {
            Member member = socialMember();
            List<Long> tagIds = IntStream.range(0, MAX_TAGS * 2)
                    .mapToObj(i -> tagRepository.save(Tag.of(member, "태그 " + i)).getId())
                    .toList();
            List<String> photos = List.of(photoKey(member));
            Long recordId = create(member, photos);

            List<String> outcomes = runAtOnce(List.of(
                    () -> travelRecordService.update(member, recordId,
                            command("여행", photos, tagIds.subList(0, MAX_TAGS))),
                    () -> travelRecordService.update(member, recordId,
                            command("여행", photos, tagIds.subList(MAX_TAGS, MAX_TAGS * 2)))));

            int count = count("travel_record_tag", recordId);
            if (!outcomes.equals(List.of(OK, OK)) || count != MAX_TAGS) {
                problems.add("round " + round + ": " + outcomes + " 태그 " + count + "개");
            }
        }

        assertThat(problems).isEmpty();
    }

    @Test
    void 다른_사진_예순_장씩_동시에_붙여도_백_장을_넘지_않는다() throws Exception {
        List<String> problems = new ArrayList<>();
        for (int round = 0; round < ROUNDS; round++) {
            Member member = socialMember();
            String first = photoKey(member);
            Long recordId = create(member, List.of(first));

            List<String> outcomes = runAtOnce(List.of(
                    () -> travelRecordService.update(member, recordId,
                            command("여행", sixtyPhotosStartingWith(first, member), List.of())),
                    () -> travelRecordService.update(member, recordId,
                            command("여행", sixtyPhotosStartingWith(first, member), List.of()))));

            int count = count("record_media", recordId);
            if (!outcomes.equals(List.of(OK, OK)) || count > MAX_PHOTOS) {
                problems.add("round " + round + ": " + outcomes + " 사진 " + count + "장");
            }
        }

        assertThat(problems).isEmpty();
    }

    /**
     * 삭제가 잠그지 않고 사진을 읽으면, 겹친 수정이 붙인 사진을 정리 큐에 넣지 못한다. 사진 행은 ON DELETE CASCADE로 사라져
     * 그 S3 객체를 아무도 지우지 않는다. 수정이 성공했다면 그 사진은 정리 큐에 있어야 한다.
     */
    @Test
    void 삭제와_수정이_겹쳐도_수정이_붙인_사진을_정리_큐에_넣는다() throws Exception {
        List<String> problems = new ArrayList<>();
        for (int round = 0; round < ROUNDS; round++) {
            Member member = socialMember();
            String first = photoKey(member);
            String added = photoKey(member);
            Long recordId = create(member, List.of(first));

            List<String> outcomes = runAtOnce(List.of(
                    () -> travelRecordService.update(member, recordId,
                            command("여행", List.of(first, added), List.of())),
                    () -> {
                        travelRecordService.delete(member, recordId);
                        return null;
                    }));

            // 삭제가 먼저 끝나면 수정은 TRAVEL_RECORD_NOT_FOUND다. 그때 붙인 사진은 기록에 붙은 적이 없다(#186).
            boolean updated = OK.equals(outcomes.get(0));
            boolean queued = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM orphaned_media WHERE object_key = ?", Integer.class, added) == 1;
            if (!OK.equals(outcomes.get(1)) || (updated && !queued)) {
                problems.add("round " + round + ": " + outcomes + " 붙인 사진 큐에 " + (queued ? "있음" : "없음"));
            }
        }

        assertThat(problems).isEmpty();
    }

    /**
     * 한 태그는 빼고 다른 태그는 붙이는 수정과, 그 두 태그의 삭제를 함께 출발시킨다.
     *
     * <p>뺄 태그가 그 순간 지워지면 두 가지가 생길 수 있다(#753). 태그 삭제의 ON DELETE CASCADE가 연결 행을 먼저 지워 수정이 그 행을
     * 다시 지우려다 0행이 되거나, 연결 행의 PK와 유니크 인덱스를 서로 반대 순서로 잠가 데드락이 난다. 둘 다 데이터는 롤백되어 그대로이고,
     * 잠금을 더해 막지 않고 수정은 한 번 다시 하며 남은 잠금 충돌은 다시 시도할 오류(503)로 응답하기로 했다.
     * 그래서 이 테스트는 그 밖의 실패(FK 위반, 엉뚱한 예외)가 없는지와, 겹쳐도 기록이 남아 있는지를 본다.
     *
     * <p>바로 출발시키면 로컬에서는 태그 삭제가 먼저 끝나 거의 겹치지 않는다. 삭제를 0~30ms 무작위로 늦춰 수정의 쓰기와 겹치게 한다.
     */
    @Test
    void 태그를_바꾸는_수정과_태그_삭제가_겹쳐도_다시_시도할_수_있는_잠금_충돌_말고는_실패하지_않는다() throws Exception {
        List<String> problems = new ArrayList<>();
        for (int round = 0; round < ROUNDS; round++) {
            Member member = socialMember();
            Long removedTag = tagRepository.save(Tag.of(member, "뺄 태그")).getId();
            Long addedTag = tagRepository.save(Tag.of(member, "붙일 태그")).getId();
            List<String> photos = List.of(photoKey(member));
            Long recordId = travelRecordService.create(member, command("여행", photos, List.of(removedTag))).getId();
            long deleteDelayMillis = ThreadLocalRandom.current().nextLong(0, 31);

            List<String> outcomes = runAtOnce(List.of(
                    () -> travelRecordService.update(member, recordId, command("여행", photos, List.of(addedTag))),
                    () -> {
                        Thread.sleep(deleteDelayMillis);
                        tagService.delete(member, removedTag);
                        return null;
                    },
                    () -> {
                        Thread.sleep(deleteDelayMillis);
                        tagService.delete(member, addedTag);
                        return null;
                    }));

            // 수정이 태그 삭제보다 늦으면 붙일 태그가 없어 TAG_NOT_FOUND다. 수정의 잠금 충돌은 한 번 다시 해서 성공해야 하므로 실패로 본다.
            // 태그 삭제는 다시 하지 않으므로, 데드락에서 희생되면 다시 시도할 오류(RETRY_LATER)로 본다.
            boolean updateEnded = List.of(OK, "TAG_NOT_FOUND").contains(outcomes.get(0));
            boolean deletesEnded = okOrRetryLater(outcomes.get(1)) && okOrRetryLater(outcomes.get(2));
            boolean recordKept = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM travel_record WHERE id = ?", Integer.class, recordId) == 1;
            if (!updateEnded || !deletesEnded || !recordKept) {
                problems.add("round " + round + " (삭제 " + deleteDelayMillis + "ms 늦춤): " + outcomes
                        + (recordKept ? "" : ", 기록이 사라짐"));
            }
        }

        assertThat(problems).as("%d번 중 다시 시도할 수 없는 실패: %s", ROUNDS, problems).isEmpty();
    }

    /**
     * 태그 삭제가 커밋되기 직전에 그 태그로 저장하는 경우다. 저장이 태그를 잠그지 않고 읽으면 스냅샷에는 태그가 있어 연결을 넣고,
     * 그 INSERT의 FK 확인이 삭제의 커밋을 기다렸다가 FK 위반(500)으로 끝난다. 동시에 출발시키면 드물게만 겹쳐,
     * 삭제를 커밋하지 않은 채 저장을 출발시키고 저장이 그 삭제를 기다릴 때까지 둔 뒤 커밋해 순서를 고정한다.
     */
    @Test
    void 태그를_지우는_중에_그_태그로_저장하면_TAG_NOT_FOUND로_끝난다() throws Exception {
        Member member = socialMember();
        Long tagId = tagRepository.save(Tag.of(member, "지울 태그")).getId();
        List<String> photos = List.of(photoKey(member));
        Long recordId = create(member, photos);
        TransactionTemplate deletion = new TransactionTemplate(transactionManager);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<String> outcome = deletion.execute(status -> {
                tagService.delete(member, tagId);
                // JPA는 DELETE를 커밋 때 내보낸다. 지금 DB에 보내야 커밋 전까지 태그 행의 잠금을 쥔다.
                tagRepository.flush();
                Future<String> update = executor.submit(() -> outcomeOf(
                        () -> travelRecordService.update(member, recordId, command("여행", photos, List.of(tagId)))));
                try {
                    waitUntilBlocked(update, "%from tag%for share%");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                }
                return update;
            });

            assertThat(outcome.get(30, TimeUnit.SECONDS)).isEqualTo("TAG_NOT_FOUND");
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    /**
     * 기록 수정이 빼려는 태그를 그 순간 지우는 경우다. 태그 삭제의 ON DELETE CASCADE가 연결 행을 먼저 지우면, 수정은 잠금 전에 읽은
     * 연결 목록대로 그 행을 PK로 지우려다 0행이 되어 ObjectOptimisticLockingFailureException이 난다(#753). 수정은 이때 한 번 다시 해서,
     * 태그 삭제가 커밋된 뒤를 보고 성공해야 한다. 무작위로 겹치게 하면 드물게만 생겨, 태그 삭제를 커밋하지 않은 채 수정을 출발시키고
     * 수정이 그 연결 행을 지우다 멈춘 것을 확인한 뒤 커밋해 순서를 고정한다.
     */
    @Test
    void 빼려는_태그를_지우는_중에_저장해도_수정은_한_번_다시_해서_성공한다() throws Exception {
        Member member = socialMember();
        Long tagId = tagRepository.save(Tag.of(member, "뺄 태그")).getId();
        List<String> photos = List.of(photoKey(member));
        Long recordId = travelRecordService.create(member, command("여행", photos, List.of(tagId))).getId();
        TransactionTemplate deletion = new TransactionTemplate(transactionManager);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<String> outcome = deletion.execute(status -> {
                tagService.delete(member, tagId);
                // JPA는 DELETE를 커밋 때 내보낸다. 지금 DB에 보내야 CASCADE가 연결 행을 지우고 커밋 전까지 그 행의 잠금을 쥔다.
                tagRepository.flush();
                Future<String> update = executor.submit(() -> outcomeOf(
                        () -> travelRecordService.update(member, recordId, command("여행", photos, List.of()))));
                try {
                    waitUntilBlocked(update, "%delete from travel_record_tag%");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                }
                return update;
            });

            assertThat(outcome.get(30, TimeUnit.SECONDS)).as("수정 결과").isEqualTo(OK);
            assertThat(count("travel_record_tag", recordId)).as("남은 태그 연결").isZero();
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    // 저장의 문장(statementPattern)이 삭제의 잠금에 걸려 멈출 때까지 둔다. 시간만 재면 느린 실행에서 저장이 그 문장에 닿기 전에 커밋해,
    // 지워진 뒤를 읽고도 통과할 수 있다. 테스트 DB 사용자는 PROCESS 권한이 없어 innodb_trx를 못 읽지만, 같은 사용자의 연결은 PROCESSLIST로 보인다.
    private void waitUntilBlocked(Future<String> update, String statementPattern) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (update.isDone()) {
                throw new AssertionError("저장이 태그 삭제의 커밋을 기다리지 않고 끝났다");
            }
            Integer waiting = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.PROCESSLIST
                    WHERE ID <> CONNECTION_ID() AND COMMAND = 'Query' AND INFO LIKE ?
                    """, Integer.class, statementPattern);
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("저장이 10초 안에 태그 삭제의 잠금을 기다리지 않았다");
    }

    private static String outcomeOf(Callable<?> action) throws Exception {
        try {
            action.call();
            return OK;
        } catch (BusinessException exception) {
            return exception.getErrorCode().toString();
        } catch (RuntimeException exception) {
            return exception.getClass().getSimpleName();
        }
    }

    // 원래 사진 한 장에 새 사진 59장을 더한 60장. 요청마다 새 사진이 달라 둘이 합쳐지면 119장이다.
    private List<String> sixtyPhotosStartingWith(String first, Member member) {
        List<String> keys = new ArrayList<>(List.of(first));
        for (int i = 0; i < 59; i++) {
            keys.add(photoKey(member));
        }
        return keys;
    }

    private Long create(Member member, List<String> photos) {
        return travelRecordService.create(member, command("여행", photos, List.of())).getId();
    }

    private TravelRecordCommand command(String title, List<String> objectKeys, List<Long> tagIds) {
        return new TravelRecordCommand("JP", null, null, title, "", LocalDate.of(2026, 8, 1), null,
                objectKeys, tagIds, null);
    }

    private String photoKey(Member member) {
        return s3StorageProperties.travelRecordsDirectory() + member.getId() + "/" + UUID.randomUUID() + ".jpg";
    }

    private List<String> mediaOf(Long recordId) {
        return jdbcTemplate.queryForList(
                "SELECT object_key FROM record_media WHERE travel_record_id = ? ORDER BY sort_order",
                String.class, recordId);
    }

    private int count(String table, Long recordId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE travel_record_id = ?", Integer.class, recordId);
    }

    private static boolean sameSet(List<String> actual, List<String> expected) {
        return actual.size() == expected.size() && actual.containsAll(expected);
    }

    private static boolean okOrRetryLater(String outcome) {
        return OK.equals(outcome) || outcome.startsWith(RETRY_LATER + "(");
    }

    private static String lockFailureName(RuntimeException exception) {
        if (exception instanceof AcceptedLockConflictException accepted) {
            return accepted.getDecision() + " " + accepted.getLockFailure().getClass().getSimpleName();
        }
        return exception.getClass().getSimpleName();
    }

    // 모든 스레드가 준비된 뒤 한꺼번에 실행하고, 각 결과를 OK, 에러 코드, RETRY_LATER(잠금 충돌 예외 이름) 또는 예외 이름으로 돌려준다.
    private List<String> runAtOnce(List<Callable<?>> actions) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(actions.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (Callable<?> action : actions) {
            results.add(executor.submit(() -> {
                start.await();
                try {
                    action.call();
                    return OK;
                } catch (BusinessException exception) {
                    return exception.getErrorCode().toString();
                } catch (PessimisticLockingFailureException | OptimisticLockingFailureException
                         | AcceptedLockConflictException exception) {
                    // 웹 계층에서 503 RETRY_LATER로 응답하는 잠금 충돌이다(LockConflictExceptionHandler).
                    // 판정은 RETRY_LATER로 하되, 실패 메시지에서 원인(데드락·지워진 행·대기 시간 초과)을 가리도록 예외 이름을 붙인다.
                    return RETRY_LATER + "(" + lockFailureName(exception) + ")";
                } catch (RuntimeException exception) {
                    return exception.getClass().getSimpleName();
                }
            }));
        }
        start.countDown();
        List<String> outcomes = new ArrayList<>();
        try {
            for (Future<String> result : results) {
                outcomes.add(result.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            // 잠금 대기로 시간이 넘어 빠져나가도 남은 스레드가 커넥션과 행 잠금을 쥔 채 다른 테스트로 넘어가지 않게 한다.
            results.forEach(result -> result.cancel(true));
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private Member socialMember() {
        return memberRepository.save(
                Member.ofOAuth(AuthProvider.KAKAO, UUID.randomUUID().toString(), "회원", UUID.randomUUID()));
    }
}
