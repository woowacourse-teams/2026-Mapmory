package com.mapmory.backend.travelrecord.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willReturn;

import com.mapmory.backend.IntegrationTest;
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
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

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

    @Test
    void 한쪽이_뺀_사진의_순서를_다른_쪽이_바꿔도_둘_다_성공한다() throws Exception {
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
            if (!outcomes.equals(List.of(OK, OK))
                    || !(sameSet(media, List.of(first)) || sameSet(media, List.of(first, second)))) {
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
     * 수정은 기록 행을 잠근 채 태그 연결을 넣고 떼며, 연결 INSERT의 FK 확인이 태그 행을 공유 잠금한다(기록 → 태그).
     * 태그 삭제는 태그 행을 지우며 ON DELETE CASCADE로 연결 행을 지운다. 두 흐름이 서로를 기다려 데드락이 나지 않는지 본다.
     * 한 태그는 빼고 다른 태그는 붙이는 수정과, 그 두 태그의 삭제를 함께 출발시킨다.
     */
    @Test
    void 태그를_바꾸는_수정과_태그_삭제가_겹쳐도_데드락이_나지_않는다() throws Exception {
        List<String> problems = new ArrayList<>();
        for (int round = 0; round < ROUNDS; round++) {
            Member member = socialMember();
            Long removedTag = tagRepository.save(Tag.of(member, "뺄 태그")).getId();
            Long addedTag = tagRepository.save(Tag.of(member, "붙일 태그")).getId();
            List<String> photos = List.of(photoKey(member));
            Long recordId = travelRecordService.create(member, command("여행", photos, List.of(removedTag))).getId();

            List<String> outcomes = runAtOnce(List.of(
                    () -> travelRecordService.update(member, recordId, command("여행", photos, List.of(addedTag))),
                    () -> {
                        tagService.delete(member, removedTag);
                        return null;
                    },
                    () -> {
                        tagService.delete(member, addedTag);
                        return null;
                    }));

            // 수정이 태그 삭제보다 늦으면 붙일 태그가 없어 TAG_NOT_FOUND다. 수정이 태그를 읽은 뒤 삭제가 커밋되면 연결 INSERT가
            // FK에 걸려 실패할 수 있는데, 이는 잠금 순서와 관계없이 이 PR 전에도 있던 경합이라 여기서는 데드락만 본다.
            boolean deadlocked = outcomes.contains("CannotAcquireLockException");
            if (deadlocked || !OK.equals(outcomes.get(1)) || !OK.equals(outcomes.get(2))) {
                problems.add("round " + round + ": " + outcomes);
            }
        }

        assertThat(problems).isEmpty();
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

    // 모든 스레드가 준비된 뒤 한꺼번에 실행하고, 각 결과를 OK, 에러 코드 또는 예외 이름으로 돌려준다.
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
                } catch (RuntimeException exception) {
                    return exception.getClass().getSimpleName();
                }
            }));
        }
        start.countDown();
        List<String> outcomes = new ArrayList<>();
        for (Future<String> result : results) {
            outcomes.add(result.get(30, TimeUnit.SECONDS));
        }
        executor.shutdown();
        return outcomes;
    }

    private Member socialMember() {
        return memberRepository.save(
                Member.ofOAuth(AuthProvider.KAKAO, UUID.randomUUID().toString(), "회원", UUID.randomUUID()));
    }
}
