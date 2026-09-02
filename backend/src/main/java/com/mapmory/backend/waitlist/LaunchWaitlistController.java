package com.mapmory.backend.waitlist;

import com.mapmory.backend.common.dto.ApiResponse;
import com.mapmory.backend.waitlist.dto.LaunchWaitlistRequest;
import com.mapmory.backend.waitlist.dto.LaunchWaitlistResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/waitlist")
public class LaunchWaitlistController {

    private final LaunchWaitlistService service;

    public LaunchWaitlistController(LaunchWaitlistService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<LaunchWaitlistResponse>> subscribe(
            @Valid @RequestBody LaunchWaitlistRequest request
    ) {
        LaunchWaitlistStatus status = service.subscribe(request.email());
        LaunchWaitlistResponse response = LaunchWaitlistResponse.from(status);
        if (status == LaunchWaitlistStatus.ALREADY_SUBSCRIBED) {
            return ResponseEntity.ok(ApiResponse.from(response));
        }
        return ResponseEntity.created(URI.create("/api/v1/waitlist"))
                .body(ApiResponse.from(response));
    }
}
