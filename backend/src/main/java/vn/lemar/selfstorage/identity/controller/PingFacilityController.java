package vn.lemar.selfstorage.identity.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.lemar.selfstorage.ApiError;
import vn.lemar.selfstorage.identity.application.Access;
import vn.lemar.selfstorage.identity.domain.RoleName;

import java.util.Map;

/**
 * Endpoint DEMO để test Access.can()/canAccessFacility() (A3b).
 *
 * <p>Cố tình KHÔNG đặt dưới /api/fm/**, /api/bom/**, /api/admin/**,
 * /api/staff/** — các prefix đó bị SecurityConfig chặn cứng theo role qua
 * hasRole(...) ngay tại tầng route (trước khi tới controller), nên không
 * dùng được để test việc nhiều role cùng gọi 1 endpoint rồi để Access tự
 * quyết định ai được/không. Endpoint này rơi vào rule tổng quát cuối của
 * SecurityConfig ("/api/**" -> hasAuthority(ACTIVE_ACCOUNT_AUTHORITY)):
 * chỉ cần account đã login + active, Access mới là nơi quyết định role nào
 * được, facility nào được — đúng đúng tinh thần "diem kiem quyen duy nhat".
 */
@RestController
@RequestMapping("/api/facility-access")
public class PingFacilityController {

    private final Access access;

    public PingFacilityController(Access access) {
        this.access = access;
    }

    @Operation(summary = "Kiểm tra quyền truy cập cơ sở (demo)",
            description = "Endpoint demo dùng để kiểm tra RBAC và facility scope.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Có quyền truy cập cơ sở"),
            @ApiResponse(responseCode = "401", description = "Request chưa được xác thực",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Role hoặc facility scope bị từ chối",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/ping/{facilityId}")
    public ResponseEntity<Map<String, Object>> pingFacility(
            @Parameter(description = "ID cơ sở cần kiểm tra quyền truy cập", required = true)
            @PathVariable Long facilityId) {
        access.can(RoleName.ADMIN, RoleName.BOM, RoleName.FM, RoleName.FS);
        access.canAccessFacility(facilityId);

        return ResponseEntity.ok(Map.of(
                "message", "pong",
                "facilityId", facilityId
        ));
    }
}
