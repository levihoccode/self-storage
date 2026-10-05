package vn.lemar.selfstorage.notification.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Tạo notification {@code OTHER} thủ công — chỉ title/body, type cố định. */
public record CreateOtherRequest(

        @NotNull
        Long recipientAccountId,

        @NotBlank
        @Size(max = 255)
        String title,

        @NotBlank
        String body
) {
}
