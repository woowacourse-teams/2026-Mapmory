package com.mapmory.backend.servicefeedback;

import com.mapmory.backend.auth.security.LoginMember;
import com.mapmory.backend.common.dto.ApiResponse;
import com.mapmory.backend.member.Member;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/service-feedback")
public class ServiceFeedbackController {

    private final ServiceFeedbackService serviceFeedbackService;

    public ServiceFeedbackController(ServiceFeedbackService serviceFeedbackService) {
        this.serviceFeedbackService = serviceFeedbackService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ServiceFeedbackSubmitResponse> submit(
            @LoginMember Member member,
            @Valid @RequestBody ServiceFeedbackRequest request
    ) {
        return ApiResponse.from(serviceFeedbackService.submit(member, request));
    }
}
