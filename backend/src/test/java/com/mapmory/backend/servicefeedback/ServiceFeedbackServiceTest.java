package com.mapmory.backend.servicefeedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mapmory.backend.member.Member;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ServiceFeedbackServiceTest {

    @Mock
    private ServiceFeedbackRepository serviceFeedbackRepository;

    @Mock
    private AdminAuditLogRepository adminAuditLogRepository;

    @InjectMocks
    private ServiceFeedbackService serviceFeedbackService;

    @Test
    void submitsTrimmedFeedbackWithUnconfirmedStatus() {
        Member member = Member.ofGuest("guest-1", "guest", UUID.randomUUID());
        when(serviceFeedbackRepository.saveAndFlush(any(ServiceFeedback.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        serviceFeedbackService.submit(
                member,
                new ServiceFeedbackRequest(ServiceFeedbackCategory.FEATURE_REQUEST, "  여러 장 등록하고 싶어요.  ", ServiceFeedbackPlatform.ANDROID)
        );

        ArgumentCaptor<ServiceFeedback> captor = ArgumentCaptor.forClass(ServiceFeedback.class);
        verify(serviceFeedbackRepository).saveAndFlush(captor.capture());
        ServiceFeedback saved = captor.getValue();
        assertThat(saved.getContent()).isEqualTo("여러 장 등록하고 싶어요.");
        assertThat(saved.getStatus()).isEqualTo(ServiceFeedbackStatus.UNCONFIRMED);
    }

    @Test
    void changesStatusAndWritesAuditLog() {
        ServiceFeedback feedback = ServiceFeedback.create(
                Member.ofGuest("guest-1", "guest", UUID.randomUUID()),
                ServiceFeedbackCategory.BUG_REPORT,
                "오류가 있어요.",
                ServiceFeedbackPlatform.IOS
        );
        ReflectionTestUtils.setField(feedback, "id", 7L);
        when(serviceFeedbackRepository.findById(7L)).thenReturn(Optional.of(feedback));

        ServiceFeedbackResponse response = serviceFeedbackService.changeStatus(7L, ServiceFeedbackStatus.IN_PROGRESS);

        assertThat(response.status()).isEqualTo(ServiceFeedbackStatus.IN_PROGRESS);
        verify(adminAuditLogRepository).save(any(AdminAuditLog.class));
    }
}
