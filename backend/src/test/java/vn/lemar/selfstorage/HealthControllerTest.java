package vn.lemar.selfstorage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HealthControllerTest {

    @Test
    void healthReportsOk() {
        assertThat(new HealthController().health())
                .containsEntry("status", "ok")
                .containsEntry("service", "self-storage");
    }
}
