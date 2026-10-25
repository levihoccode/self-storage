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
import vn.lemar.selfstorage.booking.application.dto.RejectRentalRequestRequest;
import vn.lemar.selfstorage.booking.application.dto.RejectRentalRequestResponse;

/**
 * FM từ chối yêu cầu đặt kho (B1 phần 4, spec draft.md §1.1) — controller riêng, không gộp vào
 * hàng đợi hay approve.
 *
 * <p>Route nằm dưới {@code /api/fm/**} (role-bound FM trong SecurityConfig). Guard
 * {@code rental_request.reject} theo facility nằm trong service vì cần {@code facilityId} từ
 * entity — chéo cơ sở 403. Rejected là trạng thái cuối, không sang B2.
 */
@RestController
@RequestMapping("/api/fm/rental-requests")
public class FmRentalRequestRejectController {

    private static final String REJECTED_MESSAGE = "Đã từ chối yêu cầu đặt kho";

    private final RentalRequestService rentalRequestService;

    public FmRentalRequestRejectController(RentalRequestService rentalRequestService) {
        this.rentalRequestService = rentalRequestService;
    }

    @Operation(summary = "Từ chối yêu cầu đặt kho",
            description = "Chuyển request Pending sang Rejected với lý do theo bộ chuẩn "
                    + "(OTHER bắt buộc kèm ghi chú): 404 nếu request không tồn tại; "
                    + "409 nếu request không còn Pending. Luôn gửi mail từ chối; "
                    + "tạo thông báo web chỉ khi email đã có account.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Từ chối thành công"),
            @ApiResponse(responseCode = "400", description = "Thiếu / sai lý do / ghi chú",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Thiếu / sai / hết hạn token"),
            @ApiResponse(responseCode = "403", description = "Ngoài phạm vi cơ sở / thiếu quyền"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy yêu cầu",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Yêu cầu đã được xử lý",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiEnvelope<RejectRentalRequestResponse>> reject(
            @PathVariable Long id, @Valid @RequestBody RejectRentalRequestRequest request) {
        return ResponseEntity.ok(ApiEnvelope.ok(REJECTED_MESSAGE, rentalRequestService
                .reject(id, request.rejectReason(), request.rejectNote())));
    }
}
