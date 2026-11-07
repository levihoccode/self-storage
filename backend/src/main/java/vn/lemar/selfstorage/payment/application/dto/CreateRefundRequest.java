package vn.lemar.selfstorage.payment.application.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Tạo nghĩa vụ hoàn tiền; final amount do server tính từ lines, không nhận từ client. */
public record CreateRefundRequest(
        Long invoiceId,
        Long orderId,
        @NotBlank @Size(max = 30) String reasonCode,
        @Size(max = 500) String reasonNote,
        @NotBlank @Size(max = 50) String calculationVersion,
        JsonNode calculationInput,
        @NotEmpty @Valid List<RefundRequestLineInput> lines) {
}
