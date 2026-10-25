package vn.lemar.selfstorage.booking.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.lemar.selfstorage.ApiEnvelope;
import vn.lemar.selfstorage.ApiError;
import vn.lemar.selfstorage.booking.application.RentalRequestService;
import vn.lemar.selfstorage.booking.application.dto.CreateRentalRequestRequest;
import vn.lemar.selfstorage.booking.application.dto.RentalRequestResponse;

/**
 * Form yêu cầu đặt kho public — không cần đăng nhập (B1, spec draft.md §1.1).
 */
@RestController
@RequestMapping("/api/rental-requests")
public class RentalRequestController {

    private static final String CREATED_MESSAGE = "Đã gửi yêu cầu đặt kho";

    private final RentalRequestService rentalRequestService;

    public RentalRequestController(RentalRequestService rentalRequestService) {
        this.rentalRequestService = rentalRequestService;
    }

    @SecurityRequirements
    @Operation(summary = "Gửi yêu cầu đặt kho",
            description = "Form public: tạo RentalRequest Pending, expires_at = created_at + "
                    + "request.pending_expiry_days.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Gửi yêu cầu thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ (gồm loại "
                    + "khoang không tồn tại / không được cơ sở cung cấp)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(value = """
                                    {"message":"Dữ liệu không hợp lệ",
                                    "timestamp":"2026-10-04T08:14:28.048550698Z"}"""))),
            @ApiResponse(responseCode = "404",
                    description = "Không tìm thấy cơ sở",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping
    public ResponseEntity<ApiEnvelope<RentalRequestResponse>> create(
            @Valid @RequestBody CreateRentalRequestRequest request) {
        RentalRequestResponse data = rentalRequestService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.ok(CREATED_MESSAGE, data));
    }
}
