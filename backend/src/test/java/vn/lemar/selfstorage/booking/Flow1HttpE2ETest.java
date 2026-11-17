package vn.lemar.selfstorage.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.lemar.selfstorage.e2e.HttpE2ESupport;
import vn.lemar.selfstorage.payment.application.PaymentGateway;

/**
 * Flow 1 end-to-end qua HTTP thật: form public → FM duyệt (email đã có tài khoản nên convert ngay) → khách đồng ý
 * proposal → mở phiên thanh toán → IPN VNPay chốt cọc → đặt lịch check-in → FM phân công FS → FS xác nhận khách đến.
 *
 * <p>Khác {@code Flow1CompletionIntegrationTest} (bắt đầu từ bước thanh toán, gọi service trực tiếp): test này đi qua
 * security filter, JWT thật của seed, RBAC theo cơ sở và envelope của mọi route, nên nó bắt được lỗi hợp đồng HTTP
 * mà test service không thấy.
 *
 * <p>Một chỗ cố ý lệch thực tế: route đặt lịch chỉ nhận khung trong tương lai, còn FS chỉ được xác nhận khách đến sớm
 * tối đa 15 phút. Test khẳng định 409 "chưa tới giờ" rồi dời {@code started_at}/{@code end_at} về hiện tại bằng SQL
 * trước khi gọi {@code arrive} — không có cổng nào để tua đồng hồ qua HTTP.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class Flow1HttpE2ETest extends HttpE2ESupport {

    private static final String CUSTOMER = "customer1@lemar.vn";
    private static final String FM = "fm1@lemar.vn";
    private static final String FS = "fs1@lemar.vn";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockBean
    private PaymentGateway gateway;
    @MockBean
    private JavaMailSender mailSender;

    @BeforeEach
    void stubGateway() {
        when(gateway.buildPaymentUrl(any())).thenReturn("https://pay.test/deposit");
        when(gateway.verifySignature(anyMap())).thenReturn(true);
        when(gateway.newTxnRef(any())).thenCallRealMethod();
        when(mailSender.createMimeMessage()).thenReturn(
                new jakarta.mail.internet.MimeMessage(jakarta.mail.Session.getInstance(new java.util.Properties())));
    }

    @Test
    void runsFromPublicFormToCheckinHandover() {
        long facilityId = id("select id from facilities where code = 'Q7'");
        long unitTypeId = id("select id from unit_types where name = 'Small'");
        long unitId = id("select id from storage_units where facility_id = ? and unit_type_id = ?"
                + " and status = 'Available' order by id limit 1", facilityId, unitTypeId);
        long customerId = id("select id from accounts where email = ?", CUSTOMER);
        long fsId = id("select id from accounts where email = ?", FS);

        // ---------------------------------------------------------------- 1.1 form public (không token)
        JsonNode created = ok(post("/api/rental-requests", null, Map.of(
                "facilityId", facilityId,
                "customerName", "Khách E2E Flow 1",
                "customerEmail", CUSTOMER,
                "customerPhone", "0901234567",
                "unitTypeId", unitTypeId,
                "startDate", LocalDate.now(VN).plusDays(1).toString(),
                "period", 3)));
        long requestId = created.path("id").asLong();
        assertThat(requestId).isPositive();
        assertThat(text("select status from rental_requests where id = ?", requestId)).isEqualTo("Pending");

        // ---------------------------------------------------------------- 1.1 FM duyệt → convert ngay
        JsonNode approved = ok(post("/api/fm/rental-requests/" + requestId + "/approve", FM,
                Map.of("unitId", unitId)));
        assertThat(approved.path("status").asText()).isEqualTo("Approved");
        assertThat(approved.path("unitId").asLong()).isEqualTo(unitId);
        assertThat(auditCount("RENTAL_REQUEST_APPROVED", "RentalRequest", requestId)).isEqualTo(1);

        assertThat(text("select status from rental_requests where id = ?", requestId)).isEqualTo("Converted");
        long orderId = id("select id from rental_orders where request_id = ?", requestId);
        long proposalId = id("select id from proposal_feedbacks where order_id = ? and status = 'Pending'",
                orderId);
        assertThat(text("select status from rental_orders where id = ?", orderId)).isEqualTo("Pending");
        assertThat(auditCount("RENTAL_REQUEST_CONVERTED", "RentalRequest", requestId)).isEqualTo(1);
        assertThat(auditCount("PROPOSAL_CREATED", "ProposalFeedback", proposalId)).isEqualTo(1);
        assertThat(notifications(CUSTOMER, "RENTAL_REQUEST_APPROVED", orderId)).isEqualTo(1);

        // ---------------------------------------------------------------- 1.3 khách đồng ý → hoá đơn cọc
        JsonNode invoice = ok(post("/api/proposals/" + proposalId + "/agree", CUSTOMER, null)).path("invoice");
        long depositInvoiceId = invoice.path("id").asLong();
        assertThat(invoice.path("type").asText()).isEqualTo("Deposit");
        assertThat(invoice.path("status").asText()).isEqualTo("Unpaid");
        // Cọc = giá hiệu lực của khoang Small (500.000) × invoice.deposit_months (1).
        assertThat(invoice.path("amount").decimalValue()).isEqualByComparingTo("500000");
        assertThat(id("select unit_id from rental_orders where id = ?", orderId)).isEqualTo(unitId);
        assertThat(auditCount("PROPOSAL_AGREED", "ProposalFeedback", proposalId)).isEqualTo(1);

        // ---------------------------------------------------------------- 1.4 mở phiên + IPN chốt cọc
        JsonNode session = ok(post("/api/invoices/" + depositInvoiceId + "/pay", CUSTOMER, null));
        assertThat(session.path("paymentUrl").asText()).isEqualTo("https://pay.test/deposit");
        String txnRef = latestTxnRef(depositInvoiceId);

        assertThat(ipn(txnRef, new java.math.BigDecimal("500000"))).isEqualTo("00");
        assertThat(text("select status from invoices where id = ?", depositInvoiceId)).isEqualTo("Paid");
        assertThat(text("select status from rental_orders where id = ?", orderId)).isEqualTo("Deposited");
        assertThat(text("select status from storage_units where id = ?", unitId)).isEqualTo("Reserved");
        assertThat(id("select reserved_order_id from storage_units where id = ?", unitId)).isEqualTo(orderId);
        assertThat(auditCount("RENTAL_ORDER_DEPOSITED", "RentalOrder", orderId)).isEqualTo(1);
        assertThat(notifications(CUSTOMER, "DEPOSIT_PAYMENT_SUCCEEDED", orderId)).isEqualTo(1);

        // VNPay retry cùng IPN: không ghi thêm gì.
        assertThat(ipn(txnRef, new java.math.BigDecimal("500000"))).isEqualTo("02");
        assertThat(count("select count(*) from payment_transactions where invoice_id = ? and status = 'Success'",
                depositInvoiceId)).isEqualTo(1);

        // Khách quay lại từ cổng: đọc trạng thái thật, không gọi cổng lần hai.
        JsonNode check = ok(post("/api/invoices/" + depositInvoiceId + "/payment-check", CUSTOMER, null));
        assertThat(check.path("invoiceStatus").asText()).isEqualTo("Paid");
        assertThat(check.path("paymentStatus").asText()).isEqualTo("Success");

        // ---------------------------------------------------------------- 1.5 đặt lịch check-in
        JsonNode window = ok(get("/api/rental-orders/" + orderId + "/appointment-slots", CUSTOMER));
        assertThat(window.path("orderId").asLong()).isEqualTo(orderId);
        String date = null;
        String startAt = null;
        for (JsonNode day : window.path("days")) {
            for (JsonNode slot : day.path("slots")) {
                if (slot.path("available").asBoolean()) {
                    date = day.path("date").asText();
                    startAt = slot.path("startAt").asText();
                    break;
                }
            }
            if (startAt != null) {
                break;
            }
        }
        assertThat(startAt).as("phải còn ít nhất một khung trống trong cửa sổ đặt lịch").isNotNull();

        JsonNode booked = ok(post("/api/rental-orders/" + orderId + "/appointments", CUSTOMER,
                Map.of("date", date, "startAt", startAt)));
        long appointmentId = booked.path("appointmentId").asLong();
        assertThat(booked.path("orderStatus").asText()).isEqualTo("Scheduled");
        assertThat(text("select status from appointments where id = ?", appointmentId)).isEqualTo("Pending");
        assertThat(text("select status from rental_orders where id = ?", orderId)).isEqualTo("Scheduled");
        assertThat(count("select count(*) from handover_records where appointment_id = ? and result = 'IN_PROGRESS'",
                appointmentId)).isEqualTo(1);
        assertThat(auditCount("APPOINTMENT_CREATED", "Appointment", appointmentId)).isEqualTo(1);
        assertThat(notifications(CUSTOMER, "APPOINTMENT_CREATED", orderId)).isEqualTo(1);

        // ---------------------------------------------------------------- 1.5 FM phân công FS
        JsonNode assigned = ok(post("/api/fm/appointments/" + appointmentId + "/staff", FM,
                Map.of("staffId", fsId)));
        assertThat(assigned.path("staffId").asLong()).isEqualTo(fsId);
        assertThat(text("select status from rental_orders where id = ?", orderId)).isEqualTo("InProgress");
        assertThat(auditCount("FS_ASSIGNED", "Appointment", appointmentId)).isEqualTo(1);
        assertThat(notifications(FS, "FS_ASSIGNED", orderId)).isEqualTo(1);

        // ---------------------------------------------------------------- 2.1 FS xác nhận khách đến
        // Lịch vừa đặt ở tương lai nên FS chưa được xác nhận; dời khung về hiện tại rồi mới arrive.
        assertThat(post("/api/staff/appointments/" + appointmentId + "/arrive", FS, null).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        jdbc.update("update appointments set started_at = now() - interval '1 minute',"
                + " end_at = now() + interval '1 hour' where id = ?", appointmentId);

        JsonNode arrived = ok(post("/api/staff/appointments/" + appointmentId + "/arrive", FS, null));
        assertThat(arrived.path("status").asText()).isEqualTo("Done");
        assertThat(text("select status from appointments where id = ?", appointmentId)).isEqualTo("Done");
        assertThat(count("select count(*) from appointments where id = ? and arrived_at is not null",
                appointmentId)).isEqualTo(1);
        assertThat(auditCount("APPOINTMENT_ARRIVED", "Appointment", appointmentId)).isEqualTo(1);
        // Flow 2.1: listener handover set due_at trên record Flow 1 đã mở.
        assertThat(count("select count(*) from handover_records where appointment_id = ? and due_at is not null",
                appointmentId)).isEqualTo(1);

        // Bất biến cuối: đơn đi trọn 1.1 → 2.1 với đúng một khoang, đúng khách.
        assertThat(text("select status from rental_orders where id = ?", orderId)).isEqualTo("InProgress");
        assertThat(id("select customer_id from rental_orders where id = ?", orderId)).isEqualTo(customerId);
        assertThat(count("select count(*) from rental_orders where request_id = ?", requestId)).isEqualTo(1);
    }
}
