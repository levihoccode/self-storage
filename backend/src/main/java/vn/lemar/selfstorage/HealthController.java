package vn.lemar.selfstorage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint duy nhất của bộ khung, dùng để xác nhận ứng dụng chạy được.
 *
 * <p>Đặt ở package gốc chứ không nằm trong module nghiệp vụ nào, vì đây là hạ tầng
 * chung chứ không thuộc flow nào.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @SecurityRequirements
    @Operation(summary = "Health check", description = "Xác nhận ứng dụng còn sống; không chạm DB/Redis.")
    @ApiResponse(responseCode = "200", description = "Server còn sống tốt!")
    @GetMapping("/health")
    public ResponseEntity<ApiEnvelope<Map<String, String>>> health() {
        return ResponseEntity.ok(ApiEnvelope.ok("Server còn sống tốt!",
                Map.of("status", "ok", "service", "self-storage")));
    }
}
