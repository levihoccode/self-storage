package vn.lemar.selfstorage.booking.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.lemar.selfstorage.ApiEnvelope;
import vn.lemar.selfstorage.booking.application.ProposalQueryService;
import vn.lemar.selfstorage.booking.application.RentalOrderService;
import vn.lemar.selfstorage.booking.application.RentalOrderView;
import vn.lemar.selfstorage.booking.application.dto.FmRentalOrderResponse;
import vn.lemar.selfstorage.identity.application.AccountQueryService;

/**
 * Màn FM xem đơn để đề xuất lại / huỷ đơn. Role-bound {@code /api/fm/**} (FM) + permission theo cơ sở của đơn
 * ({@code rental_order.read}, {@code rental_order.cancel}) — giữ nguyên theo chốt PR #136 (màn FM, cùng cách
 * B1 #126), huỷ đơn theo #142. Thao tác đề xuất lại nằm ở {@link RentalOrderProposalController}.
 */
@RestController
@RequestMapping("/api/fm/rental-orders")
public class FmRentalOrderController {

    static final String DETAIL_MESSAGE = "Lấy chi tiết đơn thuê thành công";
    static final String CANCEL_MESSAGE = "Đã huỷ đơn thuê";

    private final ProposalQueryService proposalQueryService;
    private final RentalOrderService rentalOrderService;
    private final AccountQueryService accountQueryService;

    public FmRentalOrderController(ProposalQueryService proposalQueryService, RentalOrderService rentalOrderService,
                                   AccountQueryService accountQueryService) {
        this.proposalQueryService = proposalQueryService;
        this.rentalOrderService = rentalOrderService;
        this.accountQueryService = accountQueryService;
    }

    @Operation(summary = "Chi tiết đơn thuê để đề xuất lại",
            description = "Đơn + nhu cầu ở form yêu cầu + lịch sử proposal (mới nhất trước, kèm lý do từ chối) + "
                    + "số lần từ chối / ngưỡng + khoang `Available` cùng cơ sở, cùng loại (đánh dấu khoang khách đã "
                    + "từ chối). Quyền `rental_order.read` theo cơ sở của đơn.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy chi tiết thành công"),
            @ApiResponse(responseCode = "401", description = "Thiếu / sai / hết hạn token"),
            @ApiResponse(responseCode = "403", description = "Không phải role FM, hoặc đơn thuộc cơ sở khác"),
            @ApiResponse(responseCode = "404", description = "Đơn không tồn tại")
    })
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiEnvelope<FmRentalOrderResponse>> detail(@PathVariable Long orderId) {
        FmRentalOrderResponse order = proposalQueryService.orderForFacilityManager(orderId);
        return ResponseEntity.ok(ApiEnvelope.ok(DETAIL_MESSAGE, order));
    }

    @Operation(summary = "FM huỷ đơn thuê",
            description = "Không body. Lý do cố định \"Khách không còn nhu cầu\" (khách không phản hồi proposal, FM "
                    + "liên hệ ngoài hệ thống không được). Chạy Cancellation Cascade: đơn `Canceled`, proposal "
                    + "`Pending`/`Agreed` → `Canceled`, hoá đơn `Unpaid` → `Canceled`, khoang `Reserved` → "
                    + "`Available`, AuditLog từng entity, khách + FM nhận `RENTAL_ORDER_CANCELED`. Quyền "
                    + "`rental_order.cancel` theo cơ sở của đơn.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đã huỷ đơn"),
            @ApiResponse(responseCode = "401", description = "Thiếu / sai / hết hạn token"),
            @ApiResponse(responseCode = "403", description = "Không phải role FM, thiếu quyền, hoặc đơn thuộc cơ sở "
                    + "khác"),
            @ApiResponse(responseCode = "404", description = "Đơn không tồn tại"),
            @ApiResponse(responseCode = "409", description = "Đơn đã đóng (`Canceled`/`Done`)")
    })
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<ApiEnvelope<RentalOrderView>> cancel(Authentication authentication,
                                                               @PathVariable Long orderId) {
        RentalOrderView order = rentalOrderService.cancelByFacilityManager(orderId,
                accountQueryService.requireIdByEmail(authentication.getName()));
        return ResponseEntity.ok(ApiEnvelope.ok(CANCEL_MESSAGE, order));
    }
}
