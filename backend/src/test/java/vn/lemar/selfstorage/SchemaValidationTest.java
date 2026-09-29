package vn.lemar.selfstorage;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boot context trên Postgres đã migrate — Hibernate (`ddl-auto=validate`) đối chiếu
 * entity với schema V1/V2; lệch là CI đỏ. Máy không có Docker thì test tự skip.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class SchemaValidationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void contextLoads() {
        // Không assert gì thêm: context chỉ load được nếu Flyway chạy và schema khớp entity.
    }
}
