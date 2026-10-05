package vn.lemar.selfstorage.notification.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import vn.lemar.selfstorage.ApiEnvelope;
import vn.lemar.selfstorage.identity.application.AccountQueryService;
import vn.lemar.selfstorage.notification.application.NotificationService;
import vn.lemar.selfstorage.notification.application.dto.CreateOtherRequest;
import vn.lemar.selfstorage.notification.application.dto.NotificationDetailResponse;
import vn.lemar.selfstorage.notification.application.dto.NotificationSummaryResponse;
import vn.lemar.selfstorage.notification.application.dto.UnreadCountResponse;
import vn.lemar.selfstorage.notification.domain.Notification;

/**
 * API notification của account đang đăng nhập — accountId lấy từ principal, không nhận từ caller.
 *
 * <p>POST {@code OTHER} chỉ ADMIN/BOM (chặn ở {@code SecurityConfig}).
 */
@Validated
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private static final String LIST_MESSAGE = "Lấy danh sách thông báo thành công";
    private static final String DETAIL_MESSAGE = "Lấy chi tiết thông báo thành công";
    private static final String UNREAD_MESSAGE = "Lấy số thông báo chưa đọc thành công";
    private static final String CREATE_MESSAGE = "Tạo thông báo thành công";
    private static final String MARK_READ_MESSAGE = "Đã đánh dấu thông báo là đã đọc";
    private static final String READ_ALL_MESSAGE = "Đã đánh dấu tất cả thông báo là đã đọc";

    private final NotificationService notificationService;
    private final AccountQueryService accountQueryService;

    public NotificationController(NotificationService notificationService, AccountQueryService accountQueryService) {
        this.notificationService = notificationService;
        this.accountQueryService = accountQueryService;
    }

    @Operation(summary = "Danh sách thông báo của tôi",
            description = "Summary không gồm `body`, sắp xếp mới nhất trước; lọc `is_read`, phân trang `page`/`size`.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy danh sách thành công"),
            @ApiResponse(responseCode = "401", description = "Thiếu / sai / hết hạn token")
    })
    @GetMapping
    public ResponseEntity<ApiEnvelope<List<NotificationSummaryResponse>>> list(
            Authentication authentication,
            @RequestParam(name = "is_read", required = false) Boolean isRead,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        List<NotificationSummaryResponse> data = notificationService
                .listForAccount(currentAccountId(authentication), isRead, page, size)
                .stream()
                .map(NotificationSummaryResponse::from)
                .toList();
        return ResponseEntity.ok(ApiEnvelope.ok(LIST_MESSAGE, data));
    }

    @Operation(summary = "Chi tiết thông báo", description = "Chỉ trả notification thuộc account đang đăng nhập.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy chi tiết thành công"),
            @ApiResponse(responseCode = "401", description = "Thiếu / sai / hết hạn token"),
            @ApiResponse(responseCode = "404", description = "Không tồn tại hoặc không thuộc account hiện tại")
    })
    @GetMapping("/{notificationId}")
    public ResponseEntity<ApiEnvelope<NotificationDetailResponse>> detail(
            Authentication authentication,
            @PathVariable Long notificationId) {
        Notification notification =
                notificationService.getForAccount(currentAccountId(authentication), notificationId);
        NotificationDetailResponse response = NotificationDetailResponse.from(notification);
        return ResponseEntity.ok(ApiEnvelope.ok(DETAIL_MESSAGE, response));
    }

    @Operation(summary = "Số thông báo chưa đọc",
            description = "Đếm notification `read_at = null` của account hiện tại.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đếm thành công"),
            @ApiResponse(responseCode = "401", description = "Thiếu / sai / hết hạn token")
    })
    @GetMapping("/unread-count")
    public ResponseEntity<ApiEnvelope<UnreadCountResponse>> unreadCount(Authentication authentication) {
        long unread = notificationService.unreadCount(currentAccountId(authentication));
        return ResponseEntity.ok(ApiEnvelope.ok(UNREAD_MESSAGE, new UnreadCountResponse(unread)));
    }

    @Operation(summary = "Tạo thông báo OTHER",
            description = "Thông báo khẩn cấp thủ công; chỉ ADMIN/BOM. `recipientAccountId` phải tồn tại.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tạo thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ"),
            @ApiResponse(responseCode = "401", description = "Thiếu / sai / hết hạn token"),
            @ApiResponse(responseCode = "403", description = "Không đủ quyền"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy account nhận")
    })
    @PostMapping
    public ResponseEntity<ApiEnvelope<NotificationDetailResponse>> createOther(
            @Valid @RequestBody CreateOtherRequest request) {
        String recipientEmail = accountQueryService.requireEmailById(request.recipientAccountId());
        Notification notification = notificationService.createOther(
                request.recipientAccountId(), recipientEmail, request.title(), request.body());
        return ResponseEntity.ok(ApiEnvelope.ok(CREATE_MESSAGE, NotificationDetailResponse.from(notification)));
    }

    @Operation(summary = "Đánh dấu thông báo đã đọc",
            description = "Idempotent; 404 nếu không thuộc account hiện tại.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đánh dấu thành công"),
            @ApiResponse(responseCode = "401", description = "Thiếu / sai / hết hạn token"),
            @ApiResponse(responseCode = "404", description = "Không tồn tại hoặc không thuộc account hiện tại")
    })
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiEnvelope<NotificationDetailResponse>> markRead(
            Authentication authentication,
            @PathVariable Long notificationId) {
        Notification notification = notificationService.markRead(currentAccountId(authentication), notificationId);
        return ResponseEntity.ok(ApiEnvelope.ok(MARK_READ_MESSAGE, NotificationDetailResponse.from(notification)));
    }

    @Operation(summary = "Đánh dấu tất cả thông báo đã đọc",
            description = "Cập nhật mọi notification chưa đọc của account hiện tại; trả số chưa đọc sau cùng (0).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đánh dấu thành công"),
            @ApiResponse(responseCode = "401", description = "Thiếu / sai / hết hạn token")
    })
    @PatchMapping("/read-all")
    public ResponseEntity<ApiEnvelope<UnreadCountResponse>> markAllRead(Authentication authentication) {
        notificationService.markAllRead(currentAccountId(authentication));
        return ResponseEntity.ok(ApiEnvelope.ok(READ_ALL_MESSAGE, new UnreadCountResponse(0)));
    }

    private Long currentAccountId(Authentication authentication) {
        return accountQueryService.requireIdByEmail(authentication.getName());
    }
}
