package vn.lemar.selfstorage.booking.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.lemar.selfstorage.ApiEnvelope;
import vn.lemar.selfstorage.ApiError;
import vn.lemar.selfstorage.booking.application.FmTransactionQueryService;
import vn.lemar.selfstorage.booking.application.dto.FmTransactionPageView;

/**
 * Lịch sử dòng tiền của cơ sở FM: invoice/fee, payment và refund trong một danh sách, mới nhất
 * trước. Read-only; quyền {@code invoice.read} scope FACILITY.
 */
@RestController
@RequestMapping("/api/fm/transactions")
public class FmTransactionController {

    static final String LIST_MESSAGE = "Lấy lịch sử giao dịch cơ sở thành công";

    private final FmTransactionQueryService queryService;

    public FmTransactionController(FmTransactionQueryService queryService) {
        this.queryService = queryService;
    }

    @Operation(summary = "Lịch sử giao dịch của cơ sở",
            description = "Union invoice/fee, payment và refund thuộc cơ sở, sort `occurredAt` giảm dần. "
                    + "`kind` lọc INVOICE/FEE/PAYMENT/REFUND; `direction` lọc IN/OUT; `status` lọc theo "
                    + "trạng thái của từng nguồn; `refundStatus` lọc invoice theo trạng thái hoàn tiền; "
                    + "`from`/`to` là ISO-8601 UTC, `to` loại trừ. "
                    + "Invoice/fee là chứng từ, payment là tiền vào, refund là tiền ra — không cộng dồn. "
                    + "Refund kèm `refundConfirmedAt`/`refundDisputedAt`/`refundDisputeNote`. Read-only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy lịch sử thành công"),
            @ApiResponse(responseCode = "400", description = "Filter `kind`, `direction`, `status`, "
                    + "`from`, `to`, `page` hoặc `size` không hợp lệ",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Thiếu / sai / hết hạn token"),
            @ApiResponse(responseCode = "403", description = "Không phải role FM, hoặc ngoài phạm vi cơ sở"),
            @ApiResponse(responseCode = "404", description = "Cơ sở không tồn tại",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping
    public ResponseEntity<ApiEnvelope<FmTransactionPageView>> list(
            @Parameter(description = "Id cơ sở") @RequestParam Long facilityId,
            @Parameter(description = "INVOICE / FEE / PAYMENT / REFUND")
            @RequestParam(required = false) String kind,
            @Parameter(description = "IN / OUT") @RequestParam(required = false) String direction,
            @Parameter(description = "Unpaid/Paid/Canceled/Expired/Pending/Failed/Success/Duplicate")
            @RequestParam(required = false) String status,
            @Parameter(description = "Lọc invoice theo refundStatus: None/Required/Processing/"
                    + "AwaitingConfirmation/Refunded")
            @RequestParam(required = false) String refundStatus,
            @Parameter(description = "Chỉ giữ PAYMENT Pending đã quá cửa sổ đối soát (15 phút)")
            @RequestParam(defaultValue = "false") boolean needsReconciliation,
            @Parameter(description = "Từ thời điểm, ISO-8601 UTC (bao gồm)")
            @RequestParam(required = false) String from,
            @Parameter(description = "Đến thời điểm, ISO-8601 UTC (loại trừ)")
            @RequestParam(required = false) String to,
            @Parameter(description = "Trang, bắt đầu từ 0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số dòng mỗi trang, tối đa 100") @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiEnvelope.ok(LIST_MESSAGE,
                queryService.listForFacility(facilityId, kind, direction, status, refundStatus,
                        needsReconciliation, from, to, page, size)));
    }
}
