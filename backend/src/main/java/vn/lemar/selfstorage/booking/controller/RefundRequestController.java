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
import vn.lemar.selfstorage.booking.application.ManualRefundRequestService;
import vn.lemar.selfstorage.payment.application.dto.CreateRefundRequest;
import vn.lemar.selfstorage.payment.application.dto.RefundRequestView;

/** FM/Admin tạo nghĩa vụ hoàn tiền thủ công, có breakdown và snapshot cách tính. */
@RestController
@RequestMapping("/api/refund-requests")
public class RefundRequestController {

    private final ManualRefundRequestService refundRequests;

    public RefundRequestController(ManualRefundRequestService refundRequests) {
        this.refundRequests = refundRequests;
    }

    @Operation(summary = "Tạo refund request thủ công",
            description = "FM/Admin tạo nghĩa vụ hoàn tiền. Final amount do server tính từ lines; "
                    + "request chưa chi tiền và chưa đổi invoice state.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đã tạo refund request"),
            @ApiResponse(responseCode = "400", description = "Lines/reason/liên kết không hợp lệ"),
            @ApiResponse(responseCode = "403", description = "Không có quyền tại facility"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy invoice/đơn"),
            @ApiResponse(responseCode = "409", description = "Đã có request đang mở")
    })
    @PostMapping
    public ResponseEntity<ApiEnvelope<RefundRequestView>> create(
            @Valid @RequestBody CreateRefundRequest request) {
        return ResponseEntity.ok(ApiEnvelope.ok("Đã tạo refund request",
                refundRequests.create(request)));
    }
}
