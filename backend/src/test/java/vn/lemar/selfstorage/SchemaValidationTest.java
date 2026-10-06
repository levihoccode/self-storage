package vn.lemar.selfstorage;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boot context trên Postgres đã migrate — Hibernate (`ddl-auto=validate`) đối chiếu
 * entity với schema V1/V2; lệch là CI đỏ. Máy không có Docker thì test tự skip.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
    "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
    // `vnpay.*` để @NotBlank (A6) nên thiếu là context không load được — đó là chủ đích,
    // app phải chết lúc khởi động thay vì NPE giữa luồng thu tiền. Test này không đụng tới
    // thanh toán nên chỉ cần giá trị giả cho qua.
    "vnpay.tmn-code=TESTTMN1",
    "vnpay.hash-secret=0123456789abcdef0123456789abcdef",
    "vnpay.pay-url=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
    "vnpay.return-url=http://localhost:8080/api/payments/vnpay/return"
})
class SchemaValidationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void contextLoads() {
        // Không assert gì thêm: context chỉ load được nếu Flyway chạy và schema khớp entity.
    }
}
