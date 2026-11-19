package vn.lemar.selfstorage.handover.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.lemar.selfstorage.ApiEnvelope;
import vn.lemar.selfstorage.handover.application.UnitOverlockService;
import vn.lemar.selfstorage.handover.application.UnitOverlockService.OverlockView;

/** FM xem việc khoá chặn của cơ sở và giao một FS cụ thể đi gắn / gỡ. */
@RestController
@RequestMapping("/api/fm/overlocks")
public class FmOverlockController {

    private final UnitOverlockService overlocks;

    public FmOverlockController(UnitOverlockService overlocks) {
        this.overlocks = overlocks;
    }

    @Operation(summary = "Khoá chặn đang mở của cơ sở",
            description = "`Required` / `Applied` / `RemovalRequired` tại cơ sở, kèm FS phụ trách (nếu đã giao). Cần "
                    + "`rental_order.read` tại cơ sở.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Danh sách khoá chặn"),
            @ApiResponse(responseCode = "403", description = "Không phải FM / ngoài phạm vi cơ sở")
    })
    @GetMapping
    public ResponseEntity<ApiEnvelope<List<OverlockView>>> list(@RequestParam Long facilityId) {
        return ResponseEntity.ok(ApiEnvelope.ok("Khoá chặn", overlocks.facilityQueue(facilityId)));
    }

    @Operation(summary = "Giao FS gắn / gỡ khoá chặn",
            description = "Chỉ khi khoá chặn đang chờ gắn (`Required`) hoặc chờ gỡ (`RemovalRequired`); giao lại được. "
                    + "FS phải đang hoạt động và được gán cơ sở; FS nhận `OVERLOCK_ASSIGNED`, audit "
                    + "`OVERLOCK_ASSIGNED`. Đã giao thì chỉ FS đó xác nhận gắn / gỡ. Cần `appointment.assign`.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đã giao"),
            @ApiResponse(responseCode = "400", description = "Thiếu `staffId`, FS không thuộc cơ sở / không hoạt động"),
            @ApiResponse(responseCode = "403", description = "Thiếu `appointment.assign` tại cơ sở"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy khoá chặn"),
            @ApiResponse(responseCode = "409", description = "Khoá chặn đang gắn (chưa cần giao) hoặc đã đóng")
    })
    @PostMapping("/{id}/assign")
    public ResponseEntity<ApiEnvelope<OverlockView>> assign(@PathVariable Long id, @RequestBody AssignRequest body) {
        Long staffId = body == null ? null : body.staffId();
        return ResponseEntity.ok(ApiEnvelope.ok("Đã giao FS", overlocks.assign(id, staffId)));
    }

    @Schema(description = "FS phụ trách")
    public record AssignRequest(@Schema(example = "5") Long staffId) {
    }
}
