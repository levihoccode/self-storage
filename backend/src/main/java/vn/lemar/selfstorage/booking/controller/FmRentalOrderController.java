package vn.lemar.selfstorage.booking.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.lemar.selfstorage.ApiEnvelope;
import vn.lemar.selfstorage.booking.application.ProposalQueryService;
import vn.lemar.selfstorage.booking.application.ProposalService;
import vn.lemar.selfstorage.booking.application.ProposalView;
import vn.lemar.selfstorage.booking.application.dto.FmRentalOrderResponse;
import vn.lemar.selfstorage.identity.application.AccountQueryService;

/**
 * Flow 1.3 phía FM: xem đơn và đề xuất lại khoang. Role-bound {@code /api/fm/**} (FM) + permission theo cơ sở
 * ({@code rental_order.read}, {@code proposal_feedback.repropose}, {@code proposal_feedback.override}) — cùng cách
 * B1 (#126) đã được duyệt.
 */
@RestController
@RequestMapping("/api/fm/rental-orders")
public class FmRentalOrderController {

    static final String DETAIL_MESSAGE = "Lấy chi tiết đơn thuê thành công";
    static final String REPROPOSE_MESSAGE = "Đã gửi đề xuất khoang mới cho khách";

    private final ProposalService proposalService;
    private final ProposalQueryService proposalQueryService;
    private final AccountQueryService accountQueryService;

    public FmRentalOrderController(ProposalService proposalService, ProposalQueryService proposalQueryService,
                                   AccountQueryService accountQueryService) {
        this.proposalService = proposalService;
        this.proposalQueryService = proposalQueryService;
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

    @Operation(summary = "Đề xuất lại khoang",
            description = "Tạo `ProposalFeedback` `Pending` mới cho đơn, khách nhận `PROPOSAL_REPROPOSED`. Khoang phải "
                    + "cùng cơ sở và còn `Available`. Khoang khách đã từ chối trong đơn: bắt buộc `overrideReason` + "
                    + "quyền `proposal_feedback.override`, lý do ghi vào AuditLog. Đơn đã đóng hoặc đang có proposal "
                    + "`Pending` → 409.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đã tạo proposal mới"),
            @ApiResponse(responseCode = "400", description = "Thiếu `unitId`, hoặc thiếu `overrideReason` khi chọn "
                    + "lại khoang khách đã từ chối"),
            @ApiResponse(responseCode = "401", description = "Thiếu / sai / hết hạn token"),
            @ApiResponse(responseCode = "403", description = "Không phải role FM, đơn thuộc cơ sở khác, hoặc thiếu "
                    + "quyền override"),
            @ApiResponse(responseCode = "404",
                    description = "Đơn không tồn tại, hoặc khoang không thuộc cơ sở của đơn"),
            @ApiResponse(responseCode = "409", description = "Khoang không còn trống, đơn đã đóng, hoặc đơn đang có "
                    + "proposal chờ khách phản hồi")
    })
    @PostMapping("/{orderId}/re-propose")
    public ResponseEntity<ApiEnvelope<ProposalView>> repropose(Authentication authentication,
                                                               @PathVariable Long orderId,
                                                               @Valid @RequestBody ReproposeRequest request) {
        ProposalView proposal = proposalService.repropose(orderId, request.unitId(), request.overrideReason(),
                accountQueryService.requireIdByEmail(authentication.getName()));
        return ResponseEntity.ok(ApiEnvelope.ok(REPROPOSE_MESSAGE, proposal));
    }

    @Schema(description = "Khoang FM đề xuất lại")
    public record ReproposeRequest(
            @Schema(description = "Id khoang mới", example = "2") @NotNull(message = "Vui lòng chọn khoang")
            Long unitId,
            @Schema(description = "Bắt buộc khi khoang đã bị khách từ chối trong đơn") String overrideReason) {
    }
}
