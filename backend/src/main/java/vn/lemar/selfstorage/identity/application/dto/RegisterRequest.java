package vn.lemar.selfstorage.identity.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RegisterRequest(

        @NotBlank
        @Email
        String email,

        @NotBlank
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$",
                message = "Mật khẩu tối thiểu 8 ký tự, gồm chữ hoa, chữ thường và số"
        )
        String password
) {
}