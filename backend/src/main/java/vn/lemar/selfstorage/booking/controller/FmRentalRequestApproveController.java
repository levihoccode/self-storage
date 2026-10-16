package vn.lemar.selfstorage.booking.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
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
import vn.lemar.selfstorage.ApiError;
import vn.lemar.selfstorage.booking.application.RentalRequestService;
import vn.lemar.selfstorage.booking.application.dto.ApproveRentalRequestRequest;
import vn.lemar.selfstorage.booking.application.dto.ApproveRentalRequestResponse;

/**
 * FM duyệt yêu cầu đặt kho (B1, spec draft.md §1.1) — controller riêng, không gộp vào hàng đợi.
 *
 * <p>Route nằm dưới {@code /api/fm/**} (role-bound FM trong SecurityConfig). Guard
 * {@code rental_request.approve} theo facility nằm trong service vì cần {@code facilityId} từ
 * entity — chéo cơ sở 403. Dừng ở Approved, không tạo proposal/order (sang B2).
 */
@RestController
@RequestMapping("/api/fm/rental-requests")
public class FmRentalRequestApproveController {

    private static final String APPROVED_MESSAGE = "Đã duyệt yêu cầu đặt kho";

    private final RentalRequestService rentalRequestService;

    public FmRentalRequestApproveController(RentalRequestService rentalRequestService) {
        this.rentalRequestService = rentalRequestService;
    }

    @Operation(summary = "Duyệt yêu cầu đặt kho",
            description = "Gán khoang Available cho request Pending: 404 nếu request không tồn "
                    + "tại; 404 nếu khoang không tồn tại hoặc thuộc cơ sở khác; 409 nếu request "
                    + "không còn Pending (kể cả 2 FM duyệt song song — conditional update, chỉ 1 "
                    + "thắng); 409 nếu request đã quá hạn xử lý (cron chưa chuyển kịp Expired); "
                    + "409 nếu khoang mất Available (request giữ Pending).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Duyệt thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Thiếu / sai / hết hạn token"),
            @ApiResponse(responseCode = "403", description = "Ngoài phạm vi cơ sở / thiếu quyền"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy yêu cầu / khoang",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Đã xử lý / quá hạn / khoang không khả dụng",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiEnvelope<ApproveRentalRequestResponse>> approve(
            @PathVariable Long id, @Valid @RequestBody ApproveRentalRequestRequest request) {
        return ResponseEntity.ok(ApiEnvelope.ok(APPROVED_MESSAGE,
                rentalRequestService.approve(id, request.unitId())));
    }
}
