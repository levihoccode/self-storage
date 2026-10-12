package vn.lemar.selfstorage.identity.application.dto;

public record LoginResponse(
        Long accountId,
        String email,
        String token
) {
}