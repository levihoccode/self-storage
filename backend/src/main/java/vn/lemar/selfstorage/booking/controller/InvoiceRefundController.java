package vn.lemar.selfstorage.booking.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.lemar.selfstorage.ApiEnvelope;
import vn.lemar.selfstorage.booking.application.InvoiceRefundService;
import vn.lemar.selfstorage.booking.application.dto.RefundConfirmRequest;
import vn.lemar.selfstorage.booking.application.dto.RefundDisputeRequest;
import vn.lemar.selfstorage.booking.application.dto.RefundHandoverRequest;
import vn.lemar.selfstorage.payment.application.dto.RefundTransactionView;

/** FS/FM xác nhận đã đưa tiền; khách xác nhận đã nhận hoặc báo chưa nhận. */
@RestController
@RequestMapping("/api/invoices")
public class InvoiceRefundController {

    private final InvoiceRefundService refundService;

    public InvoiceRefundController(InvoiceRefundService refundService) {
        this.refundService = refundService;
    }

    @Operation(summary = "Xác nhận đã đưa tiền hoàn",
            description = "FS/FM ghi nhận tiền mặt/chuyển khoản đã đưa. Tạo refund_transactions "
                    + "Success và chuyển invoice sang AwaitingConfirmation. Không phải bước duyệt FM.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đã ghi nhận tiền ra"),
            @ApiResponse(responseCode = "403", description = "Không có quyền tại cơ sở"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy hoá đơn"),
            @ApiResponse(responseCode = "409", description = "Hoá đơn không còn cần hoàn")
    })
    @PostMapping("/{invoiceId}/refund")
    public ResponseEntity<ApiEnvelope<RefundTransactionView>> handover(
            @PathVariable Long invoiceId, @Valid @RequestBody RefundHandoverRequest request) {
        return ResponseEntity.ok(ApiEnvelope.ok("Đã ghi nhận tiền hoàn",
                refundService.handover(invoiceId, request)));
    }

    @Operation(summary = "Khách xác nhận đã nhận tiền hoàn",
            description = "Chỉ hợp lệ sau khi FS/FM đã ghi nhận đưa tiền. Gọi lặp lại trả kết quả cũ.")
    @PostMapping("/{invoiceId}/refund-confirm")
    public ResponseEntity<ApiEnvelope<RefundTransactionView>> confirm(
            @PathVariable Long invoiceId, @Valid @RequestBody RefundConfirmRequest request) {
        return ResponseEntity.ok(ApiEnvelope.ok("Đã xác nhận nhận tiền hoàn",
                refundService.confirm(invoiceId, request)));
    }

    @Operation(summary = "Khách báo chưa nhận tiền hoàn",
            description = "Ghi nhận đối soát, giữ invoice AwaitingConfirmation; không tạo refund lần hai.")
    @PostMapping("/{invoiceId}/refund-dispute")
    public ResponseEntity<ApiEnvelope<RefundTransactionView>> dispute(
            @PathVariable Long invoiceId, @Valid @RequestBody RefundDisputeRequest request) {
        return ResponseEntity.ok(ApiEnvelope.ok("Đã ghi nhận báo chưa nhận tiền",
                refundService.dispute(invoiceId, request)));
    }
}
