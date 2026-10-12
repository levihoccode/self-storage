package vn.lemar.selfstorage.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import vn.lemar.selfstorage.payment.application.dto.PaymentRequest;

/** Validate ngay tại ranh giới caller — DB cũng chặn nhưng lỗi ở đây dễ hiểu hơn. */
class PaymentRequestTest {

    @Test
    void acceptsValidInput() {
        PaymentRequest request =
                new PaymentRequest("TXN1", new BigDecimal("10000"), "Thanh toan", "127.0.0.1");

        assertThat(request.txnRef()).isEqualTo("TXN1");
    }

    @Test
    void rejectsBlankTxnRef() {
        assertThatThrownBy(() -> new PaymentRequest("  ", new BigDecimal("10000"), "x", "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("txnRef");
    }

    @Test
    void rejectsNullAmount() {
        assertThatThrownBy(() -> new PaymentRequest("TXN1", null, "x", "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    @Test
    void rejectsNonPositiveAmount() {
        assertThatThrownBy(() -> new PaymentRequest("TXN1", BigDecimal.ZERO, "x", "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentRequest("TXN1", new BigDecimal("-1"), "x", "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
