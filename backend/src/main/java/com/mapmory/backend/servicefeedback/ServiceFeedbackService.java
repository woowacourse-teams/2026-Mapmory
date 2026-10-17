package com.mapmory.backend.servicefeedback;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.Member;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServiceFeedbackService {

    private final ServiceFeedbackRepository serviceFeedbackRepository;
    private final AdminAuditLogRepository adminAuditLogRepository;

    public ServiceFeedbackService(
            ServiceFeedbackRepository serviceFeedbackRepository,
            AdminAuditLogRepository adminAuditLogRepository
    ) {
        this.serviceFeedbackRepository = serviceFeedbackRepository;
        this.adminAuditLogRepository = adminAuditLogRepository;
    }

    @Transactional
    public ServiceFeedbackSubmitResponse submit(Member member, ServiceFeedbackRequest request) {
        ServiceFeedback feedback = ServiceFeedback.create(
                member,
                request.category(),
                request.content().trim(),
                request.platform()
        );
        return ServiceFeedbackSubmitResponse.from(serviceFeedbackRepository.saveAndFlush(feedback));
    }

    @Transactional(readOnly = true)
    public AdminServiceFeedbackListResponse getFeedback(
            ServiceFeedbackStatus status,
            ServiceFeedbackCategory category,
            ServiceFeedbackPlatform platform,
            LocalDate from,
            LocalDate to,
            int page,
            int size
    ) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(ServiceFeedbackErrorCode.INVALID_DATE_RANGE);
        }

        Page<ServiceFeedback> result = serviceFeedbackRepository.searchForAdmin(
                status,
                category,
                platform,
                from == null ? null : toUtcDateTime(from),
                to == null ? null : toUtcDateTime(to.plusDays(1)),
                PageRequest.of(
                        page,
                        size,
                        Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))
                )
        );

        return new AdminServiceFeedbackListResponse(
                result.getContent().stream().map(ServiceFeedbackResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public ServiceFeedbackResponse getFeedbackDetail(Long feedbackId) {
        return ServiceFeedbackResponse.from(findFeedback(feedbackId));
    }

    @Transactional
    public ServiceFeedbackResponse changeStatus(Long feedbackId, ServiceFeedbackStatus status) {
        ServiceFeedback feedback = findFeedback(feedbackId);
        ServiceFeedbackStatus before = feedback.getStatus();
        feedback.changeStatus(status);
        adminAuditLogRepository.save(AdminAuditLog.serviceFeedbackStatus(feedbackId, before, status));
        return ServiceFeedbackResponse.from(feedback);
    }

    private LocalDateTime toUtcDateTime(LocalDate date) {
        return date.atStartOfDay(ZoneId.of("Asia/Seoul"))
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }

    private ServiceFeedback findFeedback(Long feedbackId) {
        return serviceFeedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new BusinessException(ServiceFeedbackErrorCode.SERVICE_FEEDBACK_NOT_FOUND));
    }
}
