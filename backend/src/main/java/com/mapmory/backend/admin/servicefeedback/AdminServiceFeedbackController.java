package com.mapmory.backend.admin.servicefeedback;

import com.mapmory.backend.common.dto.ApiResponse;
import com.mapmory.backend.servicefeedback.AdminServiceFeedbackListResponse;
import com.mapmory.backend.servicefeedback.AdminServiceFeedbackStatusRequest;
import com.mapmory.backend.servicefeedback.ServiceFeedbackCategory;
import com.mapmory.backend.servicefeedback.ServiceFeedbackPlatform;
import com.mapmory.backend.servicefeedback.ServiceFeedbackResponse;
import com.mapmory.backend.servicefeedback.ServiceFeedbackService;
import com.mapmory.backend.servicefeedback.ServiceFeedbackStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/admin/service-feedback")
public class AdminServiceFeedbackController {

    private final ServiceFeedbackService serviceFeedbackService;

    public AdminServiceFeedbackController(ServiceFeedbackService serviceFeedbackService) {
        this.serviceFeedbackService = serviceFeedbackService;
    }

    @GetMapping
    public ApiResponse<AdminServiceFeedbackListResponse> getFeedback(
            @RequestParam(required = false) ServiceFeedbackStatus status,
            @RequestParam(required = false) ServiceFeedbackCategory category,
            @RequestParam(required = false) ServiceFeedbackPlatform platform,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.from(serviceFeedbackService.getFeedback(status, category, platform, from, to, page, size));
    }

    @GetMapping("/{feedbackId}")
    public ApiResponse<ServiceFeedbackResponse> getFeedbackDetail(@PathVariable @Min(1) Long feedbackId) {
        return ApiResponse.from(serviceFeedbackService.getFeedbackDetail(feedbackId));
    }

    @PatchMapping("/{feedbackId}/status")
    public ApiResponse<ServiceFeedbackResponse> changeStatus(
            @PathVariable @Min(1) Long feedbackId,
            @Valid @RequestBody AdminServiceFeedbackStatusRequest request
    ) {
        return ApiResponse.from(serviceFeedbackService.changeStatus(feedbackId, request.status()));
    }
}
