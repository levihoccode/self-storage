package vn.lemar.selfstorage.identity.application.dto;

public record LoginResponse(
        Long accountId,
        String email,
        String role,
        String accessToken // TODO: gáº¯n JWT tháº­t khi cÃ³ JwtService
) {
}