package vn.lemar.selfstorage.notification.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Tạo notification {@code OTHER} thủ công — người nhận theo email, type cố định. */
public record CreateOtherRequest(

        @NotBlank
        @Email
        @Size(max = 255)
        String recipientEmail,

        @NotBlank
        @Size(max = 255)
        String title,

        @NotBlank
        String body
) {
}
