package vn.lemar.selfstorage.handover.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.lemar.selfstorage.ApiEnvelope;
import vn.lemar.selfstorage.handover.application.UnitOverlockService;
import vn.lemar.selfstorage.handover.application.UnitOverlockService.OverlockView;

/** FS gắn / gỡ khoá chặn của cơ sở trên khoang dùng chìa cơ khi hợp đồng quá hạn kéo dài bị tạm khoá truy cập. */
@RestController
@RequestMapping("/api/staff/overlocks")
public class StaffOverlockController {

    private final UnitOverlockService overlocks;

    public StaffOverlockController(UnitOverlockService overlocks) {
        this.overlocks = overlocks;
    }

    @Operation(summary = "Việc khoá chặn ở cơ sở của tôi",
            description = "Khoá chặn đang mở (`Required` / `Applied` / `RemovalRequired`) tại các cơ sở FS đang đăng "
                    + "nhập được gán; chờ gỡ trước, rồi chờ gắn, rồi đang gắn. Cần `unit_access_key.issue` tại cơ sở.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Danh sách khoá chặn"),
            @ApiResponse(responseCode = "403", description = "Không phải FS / thiếu quyền tại cơ sở")
    })
    @GetMapping
    public ResponseEntity<ApiEnvelope<List<OverlockView>>> queue() {
        return ResponseEntity.ok(ApiEnvelope.ok("Khoá chặn", overlocks.staffQueue()));
    }

    @Operation(summary = "Xác nhận đã gắn khoá chặn",
            description = "`Required → Applied`; audit `OVERLOCK_APPLIED`. Truy cập đã mở lại trước đó thì yêu cầu đã "
                    + "`Canceled` → 409.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đã gắn"),
            @ApiResponse(responseCode = "400", description = "Ghi chú > 500 ký tự"),
            @ApiResponse(responseCode = "403", description = "Thiếu `unit_access_key.issue` tại cơ sở của khoang"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy khoá chặn"),
            @ApiResponse(responseCode = "409", description = "Khoá chặn không ở trạng thái chờ gắn")
    })
    @PostMapping("/{id}/apply")
    public ResponseEntity<ApiEnvelope<OverlockView>> apply(@PathVariable Long id,
                                                           @RequestBody(required = false) NoteRequest body) {
        return ResponseEntity.ok(ApiEnvelope.ok("Đã gắn khoá chặn", overlocks.apply(id, note(body))));
    }

    @Operation(summary = "Xác nhận đã gỡ khoá chặn",
            description = "`RemovalRequired → Removed` (truy cập đã mở lại); audit `OVERLOCK_REMOVED`; khách nhận "
                    + "`OVERLOCK_REMOVED`. Hợp đồng còn tạm khoá (`Applied`) → 409.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đã gỡ"),
            @ApiResponse(responseCode = "400", description = "Ghi chú > 500 ký tự"),
            @ApiResponse(responseCode = "403", description = "Thiếu `unit_access_key.issue` tại cơ sở của khoang"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy khoá chặn"),
            @ApiResponse(responseCode = "409", description = "Khoá chặn không ở trạng thái chờ gỡ")
    })
    @PostMapping("/{id}/remove")
    public ResponseEntity<ApiEnvelope<OverlockView>> remove(@PathVariable Long id,
                                                            @RequestBody(required = false) NoteRequest body) {
        return ResponseEntity.ok(ApiEnvelope.ok("Đã gỡ khoá chặn", overlocks.remove(id, note(body))));
    }

    private static String note(NoteRequest body) {
        return body == null ? null : body.note();
    }

    @Schema(description = "Ghi chú tuỳ chọn (vd. mã khoá chặn, vị trí)")
    public record NoteRequest(@Schema(example = "Khoá chặn số 12") @Size(max = 500) String note) {
    }
}
