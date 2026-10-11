package vn.lemar.selfstorage.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vn.lemar.selfstorage.payment.application.dto.IpnResponse;

/**
 * VNPay đọc phản hồi IPN theo đúng hai key {@code RspCode} và {@code Message}. Trả về chữ thường
 * thì VNPay coi như merchant chưa xác nhận và retry đủ 10 lần, dù mình đã ghi nhận xong.
 */
class IpnResponseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void serializeDungTenKeyVnpayDoiHoi() throws Exception {
        String json = mapper.writeValueAsString(IpnResponse.success());

        assertThat(json).isEqualTo("{\"RspCode\":\"00\",\"Message\":\"Confirm Success\"}");
    }

    @Test
    void nhanhChanIpnLapCungDungTenKeyDo() throws Exception {
        String json = mapper.writeValueAsString(IpnResponse.alreadyConfirmed());

        assertThat(json).contains("\"RspCode\":\"02\"").doesNotContain("rspCode");
    }
}
