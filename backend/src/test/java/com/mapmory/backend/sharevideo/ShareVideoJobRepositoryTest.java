package com.mapmory.backend.sharevideo;

import static org.assertj.core.api.Assertions.assertThat;

import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import com.mapmory.backend.support.MySqlTestContainerSupport;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class ShareVideoJobRepositoryTest extends MySqlTestContainerSupport {

    @Autowired
    private ShareVideoJobRepository shareVideoJobRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 회원이_소유한_영상_작업만_조회한다() {
        Member owner = memberRepository.save(Member.of("작업 소유자", UUID.randomUUID()));
        Member otherMember = memberRepository.save(Member.of("다른 회원", UUID.randomUUID()));
        ShareVideoJob job = shareVideoJobRepository.saveAndFlush(
                ShareVideoJob.pending(owner, "TRAVEL_TIMELINE", snapshotJson())
        );
        Long jobId = job.getId();

        entityManager.clear();

        assertThat(shareVideoJobRepository.findByIdAndMemberId(jobId, owner.getId())).isPresent();
        assertThat(shareVideoJobRepository.findByIdAndMemberId(jobId, otherMember.getId())).isEmpty();
    }

    @Test
    void 스냅샷_JSON과_작업_상태를_저장한다() {
        Member member = memberRepository.save(Member.of("스냅샷 회원", UUID.randomUUID()));
        ShareVideoJob saved = shareVideoJobRepository.saveAndFlush(
                ShareVideoJob.pending(member, "TRAVEL_TIMELINE", snapshotJson())
        );
        Long jobId = saved.getId();

        entityManager.clear();

        ShareVideoJob found = shareVideoJobRepository.findById(jobId).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(ShareVideoStatus.PENDING);
        assertThat(found.getSnapshotJson())
                .contains("\"version\"")
                .contains("\"KR\"");
        assertThat(found.getResultObjectKey()).isNull();
        assertThat(found.getFailureReason()).isNull();
    }

    @Test
    void 완료_상태와_결과_Object_Key를_저장한다() {
        Member member = memberRepository.save(Member.of("완료 작업 회원", UUID.randomUUID()));
        ShareVideoJob job = ShareVideoJob.pending(member, "TRAVEL_TIMELINE", snapshotJson());
        job.startRendering();
        job.complete("share-videos/15/result.mp4");
        ShareVideoJob saved = shareVideoJobRepository.saveAndFlush(job);
        Long jobId = saved.getId();

        entityManager.clear();

        ShareVideoJob found = shareVideoJobRepository.findById(jobId).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(ShareVideoStatus.COMPLETED);
        assertThat(found.getResultObjectKey()).isEqualTo("share-videos/15/result.mp4");
        assertThat(found.getFailureReason()).isNull();
    }

    @Test
    void 상태를_문자열로_저장한다() {
        Member member = memberRepository.save(Member.of("상태 저장 회원", UUID.randomUUID()));
        ShareVideoJob job = shareVideoJobRepository.saveAndFlush(
                ShareVideoJob.pending(member, "TRAVEL_TIMELINE", snapshotJson())
        );

        String storedStatus = (String) entityManager.createNativeQuery("""
                        SELECT status
                        FROM share_video_job
                        WHERE id = :jobId
                        """)
                .setParameter("jobId", job.getId())
                .getSingleResult();

        assertThat(storedStatus).isEqualTo("PENDING");
    }

    @Test
    void 가장_먼저_생성된_PENDING_작업을_가져온다() {
        Member member = memberRepository.save(Member.of("선점 테스트 회원", UUID.randomUUID()));
        ShareVideoJob first = shareVideoJobRepository.saveAndFlush(
                ShareVideoJob.pending(member, "TRAVEL_TIMELINE", snapshotJson())
        );
        shareVideoJobRepository.saveAndFlush(
                ShareVideoJob.pending(member, "TRAVEL_TIMELINE", snapshotJson())
        );

        ShareVideoJob found = shareVideoJobRepository.findNextPendingForUpdate().orElseThrow();

        assertThat(found.getId()).isEqualTo(first.getId());
    }

    @Test
    void RENDERING_작업은_다음_선점_대상에서_제외한다() {
        Member member = memberRepository.save(Member.of("상태 필터 회원", UUID.randomUUID()));
        ShareVideoJob rendering = ShareVideoJob.pending(member, "TRAVEL_TIMELINE", snapshotJson());
        rendering.startRendering();
        shareVideoJobRepository.saveAndFlush(rendering);
        ShareVideoJob pending = shareVideoJobRepository.saveAndFlush(
                ShareVideoJob.pending(member, "TRAVEL_TIMELINE", snapshotJson())
        );

        ShareVideoJob found = shareVideoJobRepository.findNextPendingForUpdate().orElseThrow();

        assertThat(found.getId()).isEqualTo(pending.getId());
    }

    private String snapshotJson() {
        return """
                {
                  "version": 1,
                  "stops": [
                    {
                      "countryCode": "KR",
                      "countryName": "대한민국",
                      "visits": []
                    }
                  ]
                }
                """;
    }
}
