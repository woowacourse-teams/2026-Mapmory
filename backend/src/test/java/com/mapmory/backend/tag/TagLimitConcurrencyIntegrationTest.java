package com.mapmory.backend.tag;

import static org.assertj.core.api.Assertions.assertThat;

import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 회원당 태그 상한(10개)이 동시에 만들 때도 지켜지는지 실제 DB로 확인한다.
 *
 * <p>태그 생성은 개수를 센 뒤 넣는다. 두 요청이 모두 9개를 보고 넣으면 11개가 된다(읽은 다음 쓰기).
 * 한 번으로는 경쟁이 겹치지 않을 수 있어 회원을 바꿔 여러 번 동시에 출발시킨다. 매번 정확히 10개에서 멈추고 한 요청만 거절돼야 한다.
 * 회원 잠금이 없을 때는 60회 중 60회 11개가 됐다.
 */
class TagLimitConcurrencyIntegrationTest extends IntegrationTest {

    private static final int ROUNDS = 20;
    private static final int LIMIT = 10;

    @Autowired
    private TagService tagService;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 태그가_아홉_개일_때_서로_다른_이름으로_동시에_두_번_만들어도_열_개를_넘지_않는다() throws Exception {
        List<String> overflows = new ArrayList<>();
        for (int round = 0; round < ROUNDS; round++) {
            Member member = memberWithTags(LIMIT - 1);

            List<String> outcomes = createAtOnce(member, List.of("동시 태그 A", "동시 태그 B"));

            int count = tagCount(member);
            long rejected = outcomes.stream().filter(TagErrorCode.TAG_LIMIT_EXCEEDED.toString()::equals).count();
            if (count != LIMIT || rejected != 1) {
                overflows.add("round " + round + ": " + count + "개 " + outcomes);
            }
            // 데드락이나 커넥션 대기 같은 다른 실패가 섞이지 않았는지 본다. 개수 초과 여부는 아래에서 모아 판단한다.
            assertThat(outcomes).isSubsetOf("OK", TagErrorCode.TAG_LIMIT_EXCEEDED.toString());
        }

        assertThat(overflows)
                .as("%d번 중 10개에서 멈추지 않은 경우", ROUNDS)
                .isEmpty();
    }

    // 모든 스레드가 준비된 뒤 한꺼번에 만들고, 각 결과를 OK 또는 에러 코드로 돌려준다.
    private List<String> createAtOnce(Member member, List<String> names) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(names.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (String name : names) {
            results.add(executor.submit(() -> {
                start.await();
                try {
                    tagService.create(member, name);
                    return "OK";
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

    private Member memberWithTags(int tagCount) {
        Member member = memberRepository.save(
                Member.ofOAuth(AuthProvider.KAKAO, UUID.randomUUID().toString(), "회원", UUID.randomUUID()));
        for (int i = 0; i < tagCount; i++) {
            tagRepository.save(Tag.of(member, "기존 태그 " + i));
        }
        return member;
    }

    private int tagCount(Member member) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tag WHERE member_id = ?", Integer.class, member.getId());
    }
}
