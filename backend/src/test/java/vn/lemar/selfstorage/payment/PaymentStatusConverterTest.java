package vn.lemar.selfstorage.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import vn.lemar.selfstorage.payment.domain.PaymentStatus;
import vn.lemar.selfstorage.payment.domain.PaymentStatusConverter;

/**
 * Giá trị ghi xuống DB phải khớp CHECK constraint của bảng {@code payment_transactions}
 * (`Pending`, `Failed`, `Success`). Sai chữ hoa chữ thường là insert bị DB chặn.
 */
class PaymentStatusConverterTest {

    private final PaymentStatusConverter converter = new PaymentStatusConverter();

    @Test
    void ghiXuongDbDungChuNhuCheckConstraint() {
        assertThat(converter.convertToDatabaseColumn(PaymentStatus.PENDING)).isEqualTo("Pending");
        assertThat(converter.convertToDatabaseColumn(PaymentStatus.FAILED)).isEqualTo("Failed");
        assertThat(converter.convertToDatabaseColumn(PaymentStatus.SUCCESS)).isEqualTo("Success");
    }

    @Test
    void docNguocLaiTuDb() {
        assertThat(converter.convertToEntityAttribute("Pending")).isEqualTo(PaymentStatus.PENDING);
        assertThat(converter.convertToEntityAttribute("Success")).isEqualTo(PaymentStatus.SUCCESS);
    }
}
