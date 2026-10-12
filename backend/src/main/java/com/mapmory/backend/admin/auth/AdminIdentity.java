package com.mapmory.backend.admin.auth;

import java.io.Serializable;

public record AdminIdentity(Long id, String username) implements Serializable {
}
