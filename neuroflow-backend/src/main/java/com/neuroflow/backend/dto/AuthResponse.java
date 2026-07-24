package com.neuroflow.backend.dto;

import java.util.List;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInMs,
        String username,
        List<String> roles
) {
}
