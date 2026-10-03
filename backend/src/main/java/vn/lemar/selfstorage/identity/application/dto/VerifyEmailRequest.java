package vn.lemar.selfstorage.identity.application.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyEmailRequest(
        @NotBlank
        String token
) {
}