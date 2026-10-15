package vn.lemar.selfstorage.booking.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.lemar.selfstorage.ApiEnvelope;
import vn.lemar.selfstorage.ApiError;
import vn.lemar.selfstorage.booking.application.RentalRequestService;
import vn.lemar.selfstorage.booking.application.dto.RentalRequestPageResponse;
import vn.lemar.selfstorage.booking.domain.RentalRequestStatus;
import vn.lemar.selfstorage.identity.application.Access;

/**
 * Hàng đợi yêu cầu đặt kho cho FM — chỉ thấy cơ sở của mình (B1, spec draft.md §1.1).
 *
 * <p>Route nằm dưới {@code /api/fm/**} (role-bound FM trong SecurityConfig) + guard
 * {@code Access.can("rental_request.read", facilityId)} theo facility — chéo cơ sở 403.
 */
@Validated
@RestController
@RequestMapping("/api/fm/rental-requests")
public class FmRentalRequestController {

    private static final String LIST_MESSAGE = "Lấy danh sách yêu cầu đặt kho thành công";

    private final RentalRequestService rentalRequestService;
    private final Access access;

    public FmRentalRequestController(RentalRequestService rentalRequestService, Access access) {
        this.rentalRequestService = rentalRequestService;
        this.access = access;
    }

    @Operation(summary = "Hàng đợi yêu cầu đặt kho",
            description = "Lọc theo facility + status, phân trang page/size, mới nhất trước. "
                    + "Không có field facility trong từng entry (FM chỉ thấy cơ sở của mình).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy danh sách thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Thiếu / sai / hết hạn token"),
            @ApiResponse(responseCode = "403", description = "Ngoài phạm vi cơ sở / thiếu quyền"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy cơ sở",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping
    public ResponseEntity<ApiEnvelope<RentalRequestPageResponse>> queue(
            @RequestParam(name = "facilityId") Long facilityId,
            @RequestParam(required = false) RentalRequestStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        access.can("rental_request.read", facilityId);
        return ResponseEntity.ok(ApiEnvelope.ok(LIST_MESSAGE,
                rentalRequestService.queue(facilityId, status, page, size)));
    }
}
