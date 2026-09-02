package com.mapmory.backend.waitlist.dto;

import com.mapmory.backend.waitlist.LaunchWaitlistStatus;

public record LaunchWaitlistResponse(LaunchWaitlistStatus status) {

    public static LaunchWaitlistResponse from(LaunchWaitlistStatus status) {
        return new LaunchWaitlistResponse(status);
    }
}
