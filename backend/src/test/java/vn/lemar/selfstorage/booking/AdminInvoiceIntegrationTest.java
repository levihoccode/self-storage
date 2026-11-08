package vn.lemar.selfstorage.booking;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.lemar.selfstorage.identity.application.JwtService;
import vn.lemar.selfstorage.identity.domain.Account;
import vn.lemar.selfstorage.identity.repository.AccountRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Transactional
@TestPropertySource(properties = {
        "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
class AdminInvoiceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private AccountRepository accounts;

    @BeforeEach
    void seedInvoice() {
        Long requestId = jdbc.queryForObject("""
                insert into rental_requests (facility_id, customer_name, normalized_customer_email,
                    customer_phone, unit_type_id, start_date, period, status)
                values (1, 'Khách admin invoice', 'customer1@lemar.vn', '0901234567', 1,
                    current_date, 6, 'Converted')
                returning id
                """, Long.class);
        Long orderId = jdbc.queryForObject("""
                insert into rental_orders (request_id, customer_id, unit_id, status)
                values (?, 6, 1, 'Deposited') returning id
                """, Long.class, requestId);
        jdbc.update("""
                insert into invoices (order_id, customer_id, code, title, type, status, amount,
                    created_at, due_date)
                values (?, 6, 'INV-ADMIN-LIST', 'Đặt cọc', 'Deposit', 'Paid', 500000,
                    now() - interval '1 hour', now() + interval '2 days')
                """, orderId);
    }

    @Test
    void adminSeesAllInvoices() throws Exception {
        mockMvc.perform(get("/api/admin/invoices")
                        .header(HttpHeaders.AUTHORIZATION, bearerOf("admin@lemar.vn")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Lấy danh sách hoá đơn thành công"))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].code").value("INV-ADMIN-LIST"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void invalidPagingIsRejected() throws Exception {
        mockMvc.perform(get("/api/admin/invoices")
                        .param("size", "0")
                        .header(HttpHeaders.AUTHORIZATION, bearerOf("admin@lemar.vn")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonAdminCannotSeeInvoiceList() throws Exception {
        mockMvc.perform(get("/api/admin/invoices")
                        .header(HttpHeaders.AUTHORIZATION, bearerOf("fm1@lemar.vn")))
                .andExpect(status().isForbidden());
    }

    private String bearerOf(String email) {
        Account account = accounts.findByEmail(email).orElseThrow();
        return "Bearer " + jwtService.issueAccessToken(account);
    }
}
