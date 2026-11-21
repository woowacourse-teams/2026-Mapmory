package com.mapmory.backend.upload.controller;

import com.mapmory.backend.auth.security.LoginMember;
import com.mapmory.backend.common.dto.ApiResponse;
import com.mapmory.backend.dropoff.DropOffContext;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.upload.dto.CreatePresignedUrlsRequest;
import com.mapmory.backend.upload.dto.CreatePresignedUrlsResponse;
import com.mapmory.backend.upload.service.UploadService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/uploads")
public class UploadController {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping("/presigned-urls")
    public ApiResponse<CreatePresignedUrlsResponse> createPresignedUrls(
            @LoginMember Member member,
            @Valid @RequestBody CreatePresignedUrlsRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent
    ) {
        return ApiResponse.from(CreatePresignedUrlsResponse.from(
                uploadService.createPresignedUrls(new DropOffContext(member.getId(), userAgent), member,
                        request.toCommands())
        ));
    }
}
