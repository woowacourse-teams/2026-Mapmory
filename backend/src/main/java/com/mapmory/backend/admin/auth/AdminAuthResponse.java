package com.mapmory.backend.admin.auth;

public record AdminAuthResponse(String username) {

    public static AdminAuthResponse from(AdminIdentity identity) {
        return new AdminAuthResponse(identity.username());
    }
}
