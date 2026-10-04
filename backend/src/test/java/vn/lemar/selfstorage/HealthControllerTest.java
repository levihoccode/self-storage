package vn.lemar.selfstorage;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class HealthControllerTest {

    @Test
    void healthReportsOk() {
        ResponseEntity<ApiEnvelope<Map<String, String>>> response = new HealthController().health();

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Server còn sống tốt!");
        assertThat(response.getBody().data())
                .containsEntry("status", "ok")
                .containsEntry("service", "self-storage");
    }
}
