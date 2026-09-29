package vn.lemar.selfstorage;

import java.util.Map;
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

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "service", "self-storage");
    }
}
