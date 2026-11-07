package vn.lemar.selfstorage.payment.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Một dòng client gửi lên để server dựng final amount. */
public record RefundRequestLineInput(
        @NotBlank @Size(max = 40) String componentCode,
        @NotBlank @Size(max = 10) String direction,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotBlank @Size(max = 200) String label,
        @Size(max = 30) String sourceType,
        Long sourceId,
        @Size(max = 500) String note,
        Integer sortOrder) {
}
