package vn.lemar.selfstorage.booking.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.lemar.selfstorage.ApiEnvelope;
import vn.lemar.selfstorage.booking.application.ManualRefundObligationService;
import vn.lemar.selfstorage.payment.application.dto.CreateRefundObligation;
import vn.lemar.selfstorage.payment.application.dto.RefundObligationView;

/** FM/Admin tạo nghĩa vụ hoàn tiền thủ công, có breakdown và snapshot cách tính. */
@RestController
@RequestMapping("/api/refunds/obligations")
public class RefundObligationController {

    private final ManualRefundObligationService refundObligations;

    public RefundObligationController(ManualRefundObligationService refundObligations) {
        this.refundObligations = refundObligations;
    }

    @Operation(summary = "Tạo refund obligation thủ công",
            description = "FM/Admin tạo nghĩa vụ hoàn tiền. Final amount do server tính từ lines; "
                    + "obligation chưa chi tiền và chưa đổi invoice state.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đã tạo refund obligation"),
            @ApiResponse(responseCode = "400", description = "Lines/reason/liên kết không hợp lệ"),
            @ApiResponse(responseCode = "403", description = "Không có quyền tại facility"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy invoice/đơn"),
            @ApiResponse(responseCode = "409", description = "Đã có obligation đang mở")
    })
    @PostMapping
    public ResponseEntity<ApiEnvelope<RefundObligationView>> create(
            @Valid @RequestBody CreateRefundObligation obligation) {
        return ResponseEntity.ok(ApiEnvelope.ok("Đã tạo refund obligation",
                refundObligations.create(obligation)));
    }
}
