package vn.lemar.selfstorage;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint duy nháº¥t cá»§a bá»™ khung, dÃ¹ng Ä‘á»ƒ xÃ¡c nháº­n á»©ng dá»¥ng cháº¡y Ä‘Æ°á»£c.
 *
 * <p>Äáº·t á»Ÿ package gá»‘c chá»© khÃ´ng náº±m trong module nghiá»‡p vá»¥ nÃ o, vÃ¬ Ä‘Ã¢y lÃ  háº¡ táº§ng
 * chung chá»© khÃ´ng thuá»™c flow nÃ o.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "service", "self-storage");
    }
}
