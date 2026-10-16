package vn.lemar.selfstorage.booking.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import vn.lemar.selfstorage.SecurityConfig;
import vn.lemar.selfstorage.booking.application.RentalRequestService;
import vn.lemar.selfstorage.booking.application.dto.CreateRentalRequestRequest;
import vn.lemar.selfstorage.booking.application.dto.RentalRequestResponse;
import vn.lemar.selfstorage.booking.application.exception.RentalRequestValidationException;
import vn.lemar.selfstorage.identity.application.AccountJwtAuthenticationConverter;
import vn.lemar.selfstorage.identity.application.AuthService;
import vn.lemar.selfstorage.identity.config.JwtConfiguration;
import vn.lemar.selfstorage.identity.controller.AuthController;
import vn.lemar.selfstorage.identity.repository.AccountRepository;

@WebMvcTest(controllers = {RentalRequestController.class, AuthController.class})
@Import({SecurityConfig.class, JwtConfiguration.class, AccountJwtAuthenticationConverter.class,
        BookingExceptionHandler.class})
@TestPropertySource(properties = "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
class RentalRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RentalRequestService rentalRequestService;

    @MockBean
    private AccountRepository accountRepository;

    @MockBean
    private AuthService authService;

    @Test
    void anonymousCreateReturns201Envelope() throws Exception {
        RentalRequestResponse data = new RentalRequestResponse(1L, "Pending",
                LocalDate.of(2026, 11, 1), Instant.parse("2026-10-19T00:00:00Z"));
        when(rentalRequestService.create(any(CreateRentalRequestRequest.class))).thenReturn(data);

        mockMvc.perform(post("/api/rental-requests").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"facilityId":1,"customerName":"Nguyen Van A",
                                "customerEmail":"khach@example.com","customerPhone":"0901234567",
                                "unitTypeId":1,"startDate":"2026-11-01","period":6}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Đã gửi yêu cầu đặt kho"))
                .andExpect(jsonPath("$.data.status").value("Pending"));
    }

    @Test
    void invalidBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/rental-requests").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"facilityId":1,"customerName":"","customerEmail":"khong-phai-email",
                                "customerPhone":"0901234567","unitTypeId":1,
                                "startDate":"2026-11-01","period":0}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dữ liệu không hợp lệ"));
    }

    @Test
    void serviceValidationReturns400() throws Exception {
        when(rentalRequestService.create(any(CreateRentalRequestRequest.class)))
                .thenThrow(new RentalRequestValidationException("Loại khoang không tồn tại"));

        mockMvc.perform(post("/api/rental-requests").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"facilityId":1,"customerName":"Nguyen Van A",
                                "customerEmail":"khach@example.com","customerPhone":"0901234567",
                                "unitTypeId":999,"startDate":"2026-11-01","period":6}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Loại khoang không tồn tại"));
    }
}
