package com.mapmory.backend.admin.servicefeedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import com.mapmory.backend.servicefeedback.ServiceFeedbackCategory;
import com.mapmory.backend.servicefeedback.ServiceFeedbackPlatform;
import com.mapmory.backend.servicefeedback.ServiceFeedbackStatus;
import com.mapmory.backend.servicefeedback.ServiceFeedback;
import com.mapmory.backend.servicefeedback.ServiceFeedbackService;
import org.springframework.jdbc.core.JdbcTemplate;
import com.mapmory.backend.servicefeedback.ServiceFeedbackErrorCode;
import com.mapmory.backend.servicefeedback.ServiceFeedbackRepository;
import com.mapmory.backend.support.MySqlTestContainerSupport;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Import(ServiceFeedbackService.class)
class AdminServiceFeedbackPersistenceTest extends MySqlTestContainerSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ServiceFeedbackRepository feedbackRepository;

    @Autowired
    private ServiceFeedbackService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 상태_유형_OS_필터와_전체_건수가_DB에서_적용된다() {
        Member member = memberRepository.saveAndFlush(Member.of("의견 회원", UUID.randomUUID()));
        ServiceFeedback matching = submit(member, ServiceFeedbackCategory.BUG_REPORT, ServiceFeedbackPlatform.IOS);
        submit(member, ServiceFeedbackCategory.FEATURE_REQUEST, ServiceFeedbackPlatform.IOS);
        submit(member, ServiceFeedbackCategory.BUG_REPORT, ServiceFeedbackPlatform.ANDROID);
        ServiceFeedback completed = submit(member, ServiceFeedbackCategory.BUG_REPORT, ServiceFeedbackPlatform.IOS);
        completed.changeStatus(ServiceFeedbackStatus.COMPLETED);
        feedbackRepository.flush();
        entityManager.clear();

        var filtered = service.getFeedback(ServiceFeedbackStatus.UNCONFIRMED,
                ServiceFeedbackCategory.BUG_REPORT, ServiceFeedbackPlatform.IOS, null, null, 0, 20);
        assertThat(filtered.totalElements()).isEqualTo(1);
        assertThat(filtered.feedback()).extracting(com.mapmory.backend.servicefeedback.ServiceFeedbackResponse::feedbackId)
                .containsExactly(matching.getId());
        assertThat(service.getFeedback(null, null, null, null, null, 0, 20).totalElements()).isEqualTo(4);
        assertThat(service.getFeedback(null, ServiceFeedbackCategory.BUG_REPORT, null, null, null, 0, 20)
                .totalElements()).isEqualTo(3);
        assertThat(service.getFeedback(ServiceFeedbackStatus.COMPLETED, null, null, null, null, 0, 20)
                .totalElements()).isEqualTo(1);
    }

    @Test
    void 같은_접수시각도_ID_순서로_안정적으로_페이지를_나눈다() {
        Member member = memberRepository.saveAndFlush(Member.of("페이지 회원", UUID.randomUUID()));
        ServiceFeedback first = submit(member, ServiceFeedbackCategory.OTHER, ServiceFeedbackPlatform.IOS);
        ServiceFeedback second = submit(member, ServiceFeedbackCategory.OTHER, ServiceFeedbackPlatform.IOS);
        ServiceFeedback third = submit(member, ServiceFeedbackCategory.OTHER, ServiceFeedbackPlatform.IOS);
        entityManager.createQuery("UPDATE ServiceFeedback f SET f.createdAt = :createdAt")
                .setParameter("createdAt", LocalDateTime.of(2026, 10, 14, 1, 0))
                .executeUpdate();
        entityManager.clear();

        var page0 = service.getFeedback(null, null, null, null, null, 0, 2);
        var page1 = service.getFeedback(null, null, null, null, null, 1, 2);
        assertThat(page0.feedback()).extracting(com.mapmory.backend.servicefeedback.ServiceFeedbackResponse::feedbackId)
                .containsExactly(third.getId(), second.getId());
        assertThat(page1.feedback()).extracting(com.mapmory.backend.servicefeedback.ServiceFeedbackResponse::feedbackId)
                .containsExactly(first.getId());
        assertThat(page0.totalElements()).isEqualTo(3);
        assertThat(page0.totalPages()).isEqualTo(2);
        assertThat(page1.page()).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 서비스_트랜잭션이_끝나면_상태가_실제_DB에_저장된다() {
        Member member = memberRepository.saveAndFlush(Member.of("상태 회원", UUID.randomUUID()));
        ServiceFeedback feedback = null;
        try {
            feedback = submit(member, ServiceFeedbackCategory.OTHER, ServiceFeedbackPlatform.ANDROID);
            LocalDateTime receivedAt = feedbackRepository.findById(feedback.getId())
                    .orElseThrow().getCreatedAt();

            service.changeStatus(feedback.getId(), ServiceFeedbackStatus.COMPLETED);

            ServiceFeedback reloaded = feedbackRepository.findById(feedback.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo(ServiceFeedbackStatus.COMPLETED);
            assertThat(reloaded.getCreatedAt()).isEqualTo(receivedAt);
            assertThat(reloaded.getUpdatedAt()).isNotNull();
            var audit = jdbc.queryForMap("SELECT before_value, after_value FROM admin_audit_log WHERE target_id = ?", feedback.getId());
            assertThat(audit.get("before_value")).isEqualTo("UNCONFIRMED");
            assertThat(audit.get("after_value")).isEqualTo("COMPLETED");
        } finally {
            if (feedback != null) {
                jdbc.update("DELETE FROM admin_audit_log WHERE target_id = ?", feedback.getId());
                feedbackRepository.deleteById(feedback.getId());
            }
            memberRepository.deleteById(member.getId());
        }
    }

    @Test
    void 없는_의견의_상태를_변경하면_도메인_오류가_발생한다() {
        assertThatThrownBy(() -> service.changeStatus(Long.MAX_VALUE, ServiceFeedbackStatus.COMPLETED))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ServiceFeedbackErrorCode.SERVICE_FEEDBACK_NOT_FOUND));
    }

    @Test
    void 한국_날짜_필터는_UTC_경계에서_시작을_포함하고_종료를_제외한다() {
        Member member = memberRepository.saveAndFlush(Member.of("날짜 회원", UUID.randomUUID()));
        ServiceFeedback before = submit(member, ServiceFeedbackCategory.OTHER, ServiceFeedbackPlatform.IOS);
        ServiceFeedback start = submit(member, ServiceFeedbackCategory.OTHER, ServiceFeedbackPlatform.IOS);
        ServiceFeedback last = submit(member, ServiceFeedbackCategory.OTHER, ServiceFeedbackPlatform.IOS);
        ServiceFeedback after = submit(member, ServiceFeedbackCategory.OTHER, ServiceFeedbackPlatform.IOS);
        setCreatedAt(before.getId(), LocalDateTime.of(2026, 10, 12, 14, 59, 59));
        setCreatedAt(start.getId(), LocalDateTime.of(2026, 10, 12, 15, 0));
        setCreatedAt(last.getId(), LocalDateTime.of(2026, 10, 13, 14, 59, 59));
        setCreatedAt(after.getId(), LocalDateTime.of(2026, 10, 13, 15, 0));
        entityManager.clear();
        var date = java.time.LocalDate.of(2026, 10, 13);
        assertThat(service.getFeedback(null, null, null, date, date, 0, 20).feedback())
                .extracting(com.mapmory.backend.servicefeedback.ServiceFeedbackResponse::feedbackId)
                .containsExactly(last.getId(), start.getId());
    }

    private void setCreatedAt(Long id, LocalDateTime date) {
        entityManager.createQuery("UPDATE ServiceFeedback f SET f.createdAt = :date WHERE f.id = :id")
                .setParameter("date", date).setParameter("id", id).executeUpdate();
    }

    private ServiceFeedback submit(Member member, ServiceFeedbackCategory category, ServiceFeedbackPlatform platform) {
        return feedbackRepository.saveAndFlush(ServiceFeedback.create(member, category, "개선 의견", platform));
    }
}
