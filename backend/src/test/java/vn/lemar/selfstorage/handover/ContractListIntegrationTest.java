package vn.lemar.selfstorage.handover;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.lemar.selfstorage.handover.application.ContractQueryService;
import vn.lemar.selfstorage.handover.application.dto.ContractSummaryResponse;

/** Read model hợp đồng: join khoang/cơ sở thật trên Postgres, lọc theo khách và trạng thái. */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class ContractListIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ContractQueryService service;
    @Autowired
    private JdbcTemplate jdbc;

    private long customer;
    private long otherCustomer;
    private long facility;
    private long unit;

    @BeforeEach
    void setUp() {
        customer = accountId("customer1@lemar.vn");
        // Seed chỉ có một CUSTOMER dùng được — tạo thêm để kiểm ownership.
        otherCustomer = jdbc.queryForObject("""
                insert into accounts (email, password_hash, email_verified_at, role_id, status)
                values (?, 'x', now(), (select id from roles where name = 'CUSTOMER'), 'ACTIVE')
                returning id""", Long.class, "contract-other-" + UUID.randomUUID() + "@example.com");
        facility = jdbc.queryForObject("select id from facilities where code = 'Q7'", Long.class);
        unit = jdbc.queryForObject("select id from storage_units where code = 'A-01'", Long.class);
    }

    @Test
    void returnsOnlyActiveContractsOfTheCustomerSortedByEndDate() {
        // Hạn xa trước, hạn gần sau — kiểm thứ tự tăng dần theo end_date.
        long later = seedContract(customer, LocalDate.now().plusMonths(6), "Active");
        long sooner = seedContract(customer, LocalDate.now().plusDays(10), "Active");
        seedContract(customer, LocalDate.now().minusDays(5), "Ended");
        seedContract(otherCustomer, LocalDate.now().plusDays(20), "Active");

        List<ContractSummaryResponse> contracts = service.listForCustomer(customer);

        assertThat(contracts).extracting(ContractSummaryResponse::id).containsExactly(sooner, later);
        ContractSummaryResponse first = contracts.get(0);
        assertThat(first.unitCode()).isEqualTo("A-01");
        assertThat(first.facilityName()).isEqualTo("Self Storage Quận 7");
        assertThat(first.unitTypeName()).isEqualTo("Small");
        assertThat(first.overdue()).isFalse();
    }

    @Test
    void marksContractPastItsEndDateAsOverdue() {
        seedContract(customer, LocalDate.now().minusDays(1), "Active");

        assertThat(service.listForCustomer(customer)).singleElement()
                .satisfies(contract -> {
                    assertThat(contract.overdue()).isTrue();
                    assertThat(contract.expiringSoon()).isFalse();
                });
    }

    @Test
    void marksContractInsideTheExpiringWindow() {
        // Seed V22: contract.expiring_soon_days = 7.
        long inside = seedContract(customer, LocalDate.now().plusDays(3), "Active");
        long outside = seedContract(customer, LocalDate.now().plusDays(30), "Active");

        List<ContractSummaryResponse> contracts = service.listForCustomer(customer);

        assertThat(contracts).extracting(ContractSummaryResponse::id).containsExactly(inside, outside);
        assertThat(contracts.get(0).expiringSoon()).isTrue();
        assertThat(contracts.get(1).expiringSoon()).isFalse();
    }

    @Test
    void returnsEmptyForCustomerWithoutActiveContract() {
        seedContract(otherCustomer, LocalDate.now().plusDays(10), "Active");

        assertThat(service.listForCustomer(customer)).isEmpty();
    }

    private long seedContract(long customerId, LocalDate endDate, String status) {
        Long requestId = jdbc.queryForObject("""
                insert into rental_requests (facility_id, customer_name, normalized_customer_email,
                    customer_phone, unit_type_id, start_date, period, unit_id, status)
                values (?, 'Contract Test', 'customer1@lemar.vn', '0900000000',
                    (select id from unit_types where name = 'Small'), current_date, 3, ?, 'Converted')
                returning id""", Long.class, facility, unit);
        long orderId = jdbc.queryForObject("""
                insert into rental_orders (request_id, customer_id, unit_id, status)
                values (?, ?, ?, 'Deposited') returning id""", Long.class, requestId, customerId, unit);
        return jdbc.queryForObject("""
                insert into rental_contracts (order_id, customer_id, unit_id, code, terms_version,
                    monthly_price, deposit_amount, period, start_date, end_date, status)
                values (?, ?, ?, ?, 'v1', 500000.00, 500000.00, 3, current_date, ?, ?)
                returning id""", Long.class, orderId, customerId, unit,
                "HD-" + UUID.randomUUID(), endDate, status);
    }

    private long accountId(String email) {
        return jdbc.queryForObject("select id from accounts where email = ?", Long.class, email);
    }
}
