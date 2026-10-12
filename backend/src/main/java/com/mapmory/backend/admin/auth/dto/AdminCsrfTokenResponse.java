package com.mapmory.backend.admin.auth.dto;

public record AdminCsrfTokenResponse (
        String headerName,
        String token
) {
}
