package vn.lemar.selfstorage.booking.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.Locale;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.lemar.selfstorage.ApiEnvelope;
import vn.lemar.selfstorage.ApiError;
import vn.lemar.selfstorage.booking.application.exception.RefundObligationValidationException;
import vn.lemar.selfstorage.booking.application.exception.RefundQueueForbiddenException;
import vn.lemar.selfstorage.identity.application.Access;
import vn.lemar.selfstorage.payment.application.PaymentReconciliationCaseService;
import vn.lemar.selfstorage.payment.application.dto.RefundCasePageView;

/**
 * Queue refund invoice — một route cho ADMIN (cross-facility, active/history) và FM (theo facility,
 * chỉ Required). Read-only; không đổi state.
 */
@RestController
@RequestMapping("/api/refunds/queues")
public class RefundQueueController {

    private static final String FM_PERMISSION = "payment_transaction.refund";
    private static final String LIST_MESSAGE = "Lấy danh sách khoản cần hoàn tiền thành công";

    private final PaymentReconciliationCaseService cases;
    private final Access access;

    public RefundQueueController(PaymentReconciliationCaseService cases, Access access) {
        this.cases = cases;
        this.access = access;
    }

    @Operation(summary = "Queue hoàn tiền invoice",
            description = "ADMIN: toàn bộ invoice refund active/history. FM: invoice Required của "
                    + "facility, chỉ view=active. page bắt đầu từ 0; size tối đa 100.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy danh sách thành công"),
            @ApiResponse(responseCode = "400", description = "page/size/view không hợp lệ",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Thiếu / sai / hết hạn token"),
            @ApiResponse(responseCode = "403", description = "Không phải ADMIN/FM hoặc ngoài facility")
    })
    @GetMapping("/invoices")
    public ResponseEntity<ApiEnvelope<RefundCasePageView>> listInvoices(
            @Parameter(description = "Facility id; bắt buộc với FM, bỏ qua với ADMIN")
            @RequestParam(required = false) Long facilityId,
            @Parameter(description = "Trang, bắt đầu từ 0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số dòng mỗi trang, tối đa 100")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "active hoặc history; FM chỉ hỗ trợ active")
            @RequestParam(defaultValue = "active") String view) {
        String normalizedView = view == null ? "active" : view.trim().toLowerCase(Locale.ROOT);
        if (hasRole("ROLE_ADMIN")) {
            return ResponseEntity.ok(ApiEnvelope.ok(LIST_MESSAGE,
                    cases.listRefunds(normalizedView, page, size)));
        }
        if (hasRole("ROLE_FM")) {
            if (facilityId == null) {
                throw new RefundObligationValidationException("facilityId bắt buộc với FM");
            }
            if (!"active".equals(normalizedView)) {
                throw new RefundObligationValidationException("FM queue chỉ hỗ trợ view=active");
            }
            access.can(FM_PERMISSION, facilityId);
            return ResponseEntity.ok(ApiEnvelope.ok(LIST_MESSAGE,
                    cases.listRefundRequiredForFacility(facilityId, page, size)));
        }
        throw new RefundQueueForbiddenException();
    }

    private static boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> role.equals(authority.getAuthority()));
    }
}
