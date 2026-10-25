package vn.lemar.selfstorage.identity.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ResendVerificationRequest(

        @NotBlank
        @Email
        String email
) {
}
