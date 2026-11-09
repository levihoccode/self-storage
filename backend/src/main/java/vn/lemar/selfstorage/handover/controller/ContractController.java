package vn.lemar.selfstorage.handover.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.lemar.selfstorage.ApiEnvelope;
import vn.lemar.selfstorage.ApiError;
import vn.lemar.selfstorage.handover.application.ContractQueryService;
import vn.lemar.selfstorage.handover.application.dto.ContractSummaryResponse;
import vn.lemar.selfstorage.identity.application.AccountQueryService;

/**
 * Flow 3.1 phía khách: xem hợp đồng đang hiệu lực của chính mình. Route ownership trong {@code /api/**}
 * ({@code ACCOUNT_ACTIVE}) — service lọc theo {@code customer_id}, không có prefix role-bound
 * {@code /api/customer/**}.
 */
@RestController
@RequestMapping("/api/contracts")
public class ContractController {

    static final String LIST_MESSAGE = "Lấy danh sách hợp đồng thành công";

    private final ContractQueryService contractQueryService;
    private final AccountQueryService accountQueryService;

    public ContractController(ContractQueryService contractQueryService,
                             AccountQueryService accountQueryService) {
        this.contractQueryService = contractQueryService;
        this.accountQueryService = accountQueryService;
    }

    @Operation(summary = "Hợp đồng đang hiệu lực của tôi",
            description = "Hợp đồng `Active` của account đang đăng nhập, hạn gần nhất trước. Mỗi dòng kèm "
                    + "khoang, cơ sở và cờ `overdue` tính sẵn theo ngày ở Asia/Ho_Chi_Minh để FE không tự so "
                    + "`end_date`.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy danh sách thành công"),
            @ApiResponse(responseCode = "401", description = "Thiếu / sai / hết hạn token",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Tài khoản không còn hoạt động",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping
    public ResponseEntity<ApiEnvelope<List<ContractSummaryResponse>>> list(Authentication authentication) {
        Long customerId = accountQueryService.requireIdByEmail(authentication.getName());
        return ResponseEntity.ok(ApiEnvelope.ok(LIST_MESSAGE,
                contractQueryService.listForCustomer(customerId)));
    }
}
