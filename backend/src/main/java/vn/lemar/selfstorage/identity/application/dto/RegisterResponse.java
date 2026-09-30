package vn.lemar.selfstorage.identity.application.dto;

public record RegisterResponse(
        Long accountId,
        String email,
        String message
) {
}