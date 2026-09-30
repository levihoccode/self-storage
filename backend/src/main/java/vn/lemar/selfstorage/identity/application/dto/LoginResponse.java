package vn.lemar.selfstorage.identity.application.dto;

public record LoginResponse(
        Long accountId,
        String email,
        String role,
        String accessToken,
        long expiresInSeconds
) {
}