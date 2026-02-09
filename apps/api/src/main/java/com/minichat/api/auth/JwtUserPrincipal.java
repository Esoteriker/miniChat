package com.minichat.api.auth;

import java.util.UUID;

public record JwtUserPrincipal(UUID userId, String email) {
}
