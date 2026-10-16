package vn.lemar.selfstorage.pricing.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import vn.lemar.selfstorage.SecurityConfig;
import vn.lemar.selfstorage.booking.controller.BookingExceptionHandler;
import vn.lemar.selfstorage.identity.application.AccountJwtAuthenticationConverter;
import vn.lemar.selfstorage.identity.application.AuthService;
import vn.lemar.selfstorage.identity.application.RegistrationService;
import vn.lemar.selfstorage.identity.config.JwtConfiguration;
import vn.lemar.selfstorage.identity.controller.AuthController;
import vn.lemar.selfstorage.identity.repository.AccountRepository;
import vn.lemar.selfstorage.pricing.application.UnitTypeQueryService;

@WebMvcTest(controllers = {UnitTypeController.class, AuthController.class})
@Import({SecurityConfig.class, JwtConfiguration.class, AccountJwtAuthenticationConverter.class,
        BookingExceptionHandler.class})
@TestPropertySource(properties = "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
class UnitTypeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UnitTypeQueryService unitTypeQueryService;

    @MockBean
    private AccountRepository accountRepository;

    @MockBean
    private AuthService authService;

    @MockBean
    private RegistrationService registrationService;

    @Test
    void anonymousListReturnsOptions() throws Exception {
        Mockito.when(unitTypeQueryService.listByFacility(1L)).thenReturn(List.of(
                new UnitTypeQueryService.UnitTypeOption(1L, "Small", new BigDecimal("1.00"),
                        new BigDecimal("2.00"), new BigDecimal("2.20"), new BigDecimal("2.00"),
                        new BigDecimal("500000")),
                new UnitTypeQueryService.UnitTypeOption(2L, "Medium", new BigDecimal("2.00"),
                        new BigDecimal("2.50"), new BigDecimal("2.20"), new BigDecimal("5.00"),
                        new BigDecimal("900000"))));

        mockMvc.perform(get("/api/unit-types?facilityId=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Lấy danh sách loại khoang thành công"))
                .andExpect(jsonPath("$.data[0].name").value("Small"))
                .andExpect(jsonPath("$.data[1].name").value("Medium"));
    }

    @Test
    void unknownFacilityReturnsEmpty() throws Exception {
        Mockito.when(unitTypeQueryService.listByFacility(99L)).thenReturn(List.of());

        mockMvc.perform(get("/api/unit-types?facilityId=99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
