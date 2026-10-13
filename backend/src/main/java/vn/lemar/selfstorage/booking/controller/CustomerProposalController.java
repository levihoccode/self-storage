package vn.lemar.selfstorage.booking.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.lemar.selfstorage.ApiEnvelope;
import vn.lemar.selfstorage.ApiError;
import vn.lemar.selfstorage.booking.application.AgreeResult;
import vn.lemar.selfstorage.booking.application.InvoiceView;
import vn.lemar.selfstorage.booking.application.ProposalQueryService;
import vn.lemar.selfstorage.booking.application.ProposalService;
import vn.lemar.selfstorage.booking.application.ProposalView;
import vn.lemar.selfstorage.booking.application.RejectResult;
import vn.lemar.selfstorage.booking.application.dto.CustomerProposalResponse;
import vn.lemar.selfstorage.identity.application.AccountQueryService;

/**
 * Flow 1.3 phía khách: xem đề xuất khoang, đồng ý, từ chối. Role-bound {@code /api/customer/**} (CUSTOMER) +
 * ownership trong service — khách chỉ thấy và thao tác proposal của chính mình.
 */
@RestController
@RequestMapping("/api/customer/proposals")
public class CustomerProposalController {

    static final String LIST_MESSAGE = "Lấy danh sách đề xuất khoang thành công";
    static final String AGREE_MESSAGE = "Đã đồng ý khoang. Vui lòng thanh toán hoá đơn đặt cọc";
    static final String REJECT_MESSAGE = "Đã từ chối đề xuất khoang";
    static final String REJECT_CANCELED_MESSAGE =
            "Đã từ chối đề xuất khoang. Đơn đã bị huỷ do từ chối quá số khoang cho phép";
    /** Spec 1.3: thông báo khi khoang đã {@code Reserved}/{@code Maintenance} lúc khách bấm đồng ý. */
    static final String UNIT_UNAVAILABLE_MESSAGE = "Khoang đã có người đặt cọc / không khả dụng";

    private final ProposalService proposalService;
    private final ProposalQueryService proposalQueryService;
    private final AccountQueryService accountQueryService;

    public CustomerProposalController(ProposalService proposalService, ProposalQueryService proposalQueryService,
                                      AccountQueryService accountQueryService) {
        this.proposalService = proposalService;
        this.proposalQueryService = proposalQueryService;
        this.accountQueryService = accountQueryService;
    }

    @Operation(summary = "Đề xuất khoang của tôi",
            description = "Proposal của khách đang đăng nhập, mới nhất trước. Lọc `status` (Pending / Agreed / "
                    + "Rejected / Canceled), bỏ trống = tất cả. Mỗi dòng kèm số lần đơn đã bị từ chối và ngưỡng "
                    + "`proposal.max_rejection_count` để FE hiển thị cảnh báo, không tự suy ra luật.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy danh sách thành công"),
            @ApiResponse(responseCode = "400", description = "`status` không hợp lệ"),
            @ApiResponse(responseCode = "401", description = "Thiếu / sai / hết hạn token"),
            @ApiResponse(responseCode = "403", description = "Không phải role CUSTOMER")
    })
    @GetMapping
    public ResponseEntity<ApiEnvelope<List<CustomerProposalResponse>>> list(
            Authentication authentication,
            @Parameter(description = "Pending / Agreed / Rejected / Canceled") @RequestParam(required = false)
            String status) {
        List<CustomerProposalResponse> data =
                proposalQueryService.listForCustomer(currentAccountId(authentication), status);
        return ResponseEntity.ok(ApiEnvelope.ok(LIST_MESSAGE, data));
    }

    @Operation(summary = "Đồng ý đề xuất khoang",
            description = "Kiểm khoang còn `Available` (khoá theo `unit_id`). Còn: proposal `Agreed`, gán khoang "
                    + "vào đơn, tạo hoá đơn `Deposit`/`Unpaid` (số tiền = giá thuê hiệu lực × "
                    + "`invoice.deposit_months`; hạn = min(now + `invoice.deposit_due_days`, hạn đơn)). Không còn: "
                    + "proposal chuyển `Canceled`, FM nhận `PROPOSAL_REPROPOSAL_REQUIRED`, trả 409 — thay đổi này "
                    + "vẫn được lưu.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đã đồng ý, kèm hoá đơn cọc"),
            @ApiResponse(responseCode = "401", description = "Thiếu / sai / hết hạn token"),
            @ApiResponse(responseCode = "403", description = "Không phải role CUSTOMER, hoặc không phải chủ proposal"),
            @ApiResponse(responseCode = "404", description = "Proposal không tồn tại"),
            @ApiResponse(responseCode = "409", description = "Proposal không còn Pending, hoặc khoang không còn "
                    + "khả dụng (\"Khoang đã có người đặt cọc / không khả dụng\")")
    })
    @PostMapping("/{proposalId}/agree")
    public ResponseEntity<?> agree(Authentication authentication, @PathVariable Long proposalId) {
        AgreeResult result = proposalService.agree(proposalId, currentAccountId(authentication));
        if (result.outcome() == AgreeResult.Outcome.UNIT_UNAVAILABLE) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.now(UNIT_UNAVAILABLE_MESSAGE));
        }
        return ResponseEntity.ok(ApiEnvelope.ok(AGREE_MESSAGE,
                new AgreeResponse(result.proposal(), result.invoice())));
    }

    @Operation(summary = "Từ chối đề xuất khoang",
            description = "Bắt buộc `note`. Proposal `Rejected`; chưa chạm ngưỡng thì FM nhận `PROPOSAL_REJECTED`. "
                    + "Lần từ chối thứ `proposal.max_rejection_count` (số Rejected + 1 >= ngưỡng): đơn chạy "
                    + "Cancellation Cascade, lý do \"Từ chối quá số khoang cho phép\", `orderCanceled = true`.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đã từ chối"),
            @ApiResponse(responseCode = "400", description = "Thiếu lý do từ chối"),
            @ApiResponse(responseCode = "401", description = "Thiếu / sai / hết hạn token"),
            @ApiResponse(responseCode = "403", description = "Không phải role CUSTOMER, hoặc không phải chủ proposal"),
            @ApiResponse(responseCode = "404", description = "Proposal không tồn tại"),
            @ApiResponse(responseCode = "409", description = "Proposal không còn Pending")
    })
    @PostMapping("/{proposalId}/reject")
    public ResponseEntity<ApiEnvelope<RejectResult>> reject(
            Authentication authentication,
            @PathVariable Long proposalId,
            @RequestBody(required = false) RejectProposalRequest request) {
        RejectResult result = proposalService.reject(proposalId, currentAccountId(authentication),
                request == null ? null : request.note());
        return ResponseEntity.ok(ApiEnvelope.ok(result.orderCanceled() ? REJECT_CANCELED_MESSAGE : REJECT_MESSAGE,
                result));
    }

    private Long currentAccountId(Authentication authentication) {
        return accountQueryService.requireIdByEmail(authentication.getName());
    }

    @Schema(description = "Lý do khách từ chối khoang")
    public record RejectProposalRequest(
            @Schema(description = "Lý do (bắt buộc)", example = "Khoang xa cửa ra vào") String note) {
    }

    @Schema(description = "Proposal sau khi đồng ý và hoá đơn đặt cọc vừa tạo")
    public record AgreeResponse(ProposalView proposal, InvoiceView invoice) {
    }
}
