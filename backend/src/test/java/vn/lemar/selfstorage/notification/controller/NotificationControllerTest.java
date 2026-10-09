package vn.lemar.selfstorage.notification.controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import vn.lemar.selfstorage.SecurityConfig;
import vn.lemar.selfstorage.identity.application.Access;
import vn.lemar.selfstorage.identity.application.AccountJwtAuthenticationConverter;
import vn.lemar.selfstorage.identity.application.AccountQueryService;
import vn.lemar.selfstorage.identity.application.exception.AccountNotFoundException;
import vn.lemar.selfstorage.identity.application.exception.ForbiddenException;
import vn.lemar.selfstorage.identity.config.JwtConfiguration;
import vn.lemar.selfstorage.identity.domain.Account;
import vn.lemar.selfstorage.identity.domain.AccountStatus;
import vn.lemar.selfstorage.identity.domain.Role;
import vn.lemar.selfstorage.identity.repository.AccountRepository;
import vn.lemar.selfstorage.notification.application.NotificationService;
import vn.lemar.selfstorage.notification.application.exception.NotificationNotFoundException;
import vn.lemar.selfstorage.notification.domain.Notification;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = NotificationController.class)
@Import({SecurityConfig.class, JwtConfiguration.class, AccountJwtAuthenticationConverter.class})
@TestPropertySource(properties = "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @MockBean
    private NotificationService notificationService;

    @MockBean
    private AccountQueryService accountQueryService;

    @MockBean
    private Access access;

    @MockBean
    private AccountRepository accountRepository;

    @Test
    void listRejectsAnonymousRequest() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void listReturnsSummaryOfCurrentAccountWithoutBody() throws Exception {
        authenticate("customer@example.com", "CUSTOMER");
        when(accountQueryService.requireIdByEmail("customer@example.com")).thenReturn(1L);
        when(notificationService.listForAccount(1L, null, 0, 20))
                .thenReturn(List.of(notification(10L, "RENTAL_REQUEST_APPROVED")));

        mockMvc.perform(get("/api/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("customer@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Lấy danh sách thông báo thành công"))
                .andExpect(jsonPath("$.data[0].id").value(10))
                .andExpect(jsonPath("$.data[0].type").value("RENTAL_REQUEST_APPROVED"))
                .andExpect(jsonPath("$.data[0].title").value("Tiêu đề"))
                .andExpect(jsonPath("$.data[0].body").doesNotExist());
    }

    @Test
    void listRejectsInvalidPage() throws Exception {
        authenticate("customer@example.com", "CUSTOMER");

        mockMvc.perform(get("/api/notifications?page=-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("customer@example.com")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dữ liệu không hợp lệ"));
    }

    @Test
    void detailReturnsNotFoundWhenNotificationBelongsToAnotherAccount() throws Exception {
        authenticate("customer@example.com", "CUSTOMER");
        when(accountQueryService.requireIdByEmail("customer@example.com")).thenReturn(1L);
        when(notificationService.getForAccount(1L, 99L)).thenThrow(new NotificationNotFoundException());

        mockMvc.perform(get("/api/notifications/99")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("customer@example.com")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy thông báo"));
    }

    @Test
    void detailReturnsFullNotificationOfOwner() throws Exception {
        authenticate("customer@example.com", "CUSTOMER");
        when(accountQueryService.requireIdByEmail("customer@example.com")).thenReturn(1L);
        when(notificationService.getForAccount(1L, 10L)).thenReturn(notification(10L, "DEPOSIT_PAYMENT_SUCCEEDED"));

        mockMvc.perform(get("/api/notifications/10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("customer@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.body").value("Nội dung"));
    }

    @Test
    void listIncludesOrderIdForOrderLinkedNotification() throws Exception {
        authenticate("customer@example.com", "CUSTOMER");
        when(accountQueryService.requireIdByEmail("customer@example.com")).thenReturn(1L);
        when(notificationService.listForAccount(1L, null, 0, 20))
                .thenReturn(List.of(notification(10L, "RENTAL_ORDER_EXPIRING_SOON")));
        when(notificationService.orderIdsOf(List.of(10L))).thenReturn(Map.of(10L, 77L));

        mockMvc.perform(get("/api/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("customer@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].orderId").value(77));
    }

    @Test
    void detailIncludesOrderIdWhenNotificationLinkedToOrder() throws Exception {
        authenticate("customer@example.com", "CUSTOMER");
        when(accountQueryService.requireIdByEmail("customer@example.com")).thenReturn(1L);
        when(notificationService.getForAccount(1L, 10L)).thenReturn(notification(10L, "DEPOSIT_PAYMENT_SUCCEEDED"));
        when(notificationService.orderIdOf(10L)).thenReturn(77L);

        mockMvc.perform(get("/api/notifications/10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("customer@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(77));
    }

    @Test
    void unreadCountReturnsCurrentAccountCount() throws Exception {
        authenticate("customer@example.com", "CUSTOMER");
        when(accountQueryService.requireIdByEmail("customer@example.com")).thenReturn(1L);
        when(notificationService.unreadCount(1L)).thenReturn(3L);

        mockMvc.perform(get("/api/notifications/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("customer@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(3));
    }

    @Test
    void createOtherIsForbiddenForCustomer() throws Exception {
        authenticate("customer@example.com", "CUSTOMER");
        doThrow(new ForbiddenException()).when(access).can("notification.create_other");
        String request = "{\"recipientEmail\":\"target@example.com\",\"title\":\"Bảo trì\",\"body\":\"Nội dung\"}";

        mockMvc.perform(post("/api/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("customer@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Bạn không có quyền thực hiện thao tác này"));
    }

    @Test
    void createOtherIsAllowedForAdmin() throws Exception {
        authenticate("admin@example.com", "ADMIN");
        when(accountQueryService.requireIdByEmail("target@example.com")).thenReturn(5L);
        when(accountQueryService.requireEmailById(5L)).thenReturn("target@example.com");
        when(notificationService.createOther(5L, "target@example.com", "Bảo trì", "Nội dung"))
                .thenReturn(notification(20L, "OTHER"));
        String request = "{\"recipientEmail\":\"target@example.com\",\"title\":\"Bảo trì\",\"body\":\"Nội dung\"}";

        mockMvc.perform(post("/api/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("admin@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Tạo thông báo thành công"))
                .andExpect(jsonPath("$.data.id").value(20))
                .andExpect(jsonPath("$.data.type").value("OTHER"));
        verify(access).can("notification.create_other");
    }

    @Test
    void createOtherIsAllowedForBom() throws Exception {
        authenticate("bom@example.com", "BOM");
        when(accountQueryService.requireIdByEmail("target@example.com")).thenReturn(5L);
        when(accountQueryService.requireEmailById(5L)).thenReturn("target@example.com");
        when(notificationService.createOther(5L, "target@example.com", "Bảo trì", "Nội dung"))
                .thenReturn(notification(20L, "OTHER"));
        String request = "{\"recipientEmail\":\"target@example.com\",\"title\":\"Bảo trì\",\"body\":\"Nội dung\"}";

        mockMvc.perform(post("/api/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("bom@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("OTHER"));
        verify(access).can("notification.create_other");
    }

    @Test
    void createOtherReturnsNotFoundForUnknownEmail() throws Exception {
        authenticate("admin@example.com", "ADMIN");
        when(accountQueryService.requireIdByEmail("missing@example.com"))
                .thenThrow(new AccountNotFoundException());
        String request = "{\"recipientEmail\":\"missing@example.com\",\"title\":\"Bảo trì\",\"body\":\"Nội dung\"}";

        mockMvc.perform(post("/api/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("admin@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isNotFound());
    }

    @Test
    void createOtherRejectsInvalidEmail() throws Exception {
        authenticate("admin@example.com", "ADMIN");
        String request = "{\"recipientEmail\":\"not-an-email\",\"title\":\"Bảo trì\",\"body\":\"Nội dung\"}";

        mockMvc.perform(post("/api/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("admin@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dữ liệu không hợp lệ"));
    }

    @Test
    void createOtherRejectsMissingTitle() throws Exception {
        authenticate("admin@example.com", "ADMIN");

        mockMvc.perform(post("/api/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("admin@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientEmail\":\"target@example.com\",\"body\":\"Nội dung\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dữ liệu không hợp lệ"));
    }

    @Test
    void listPassesUnreadFilter() throws Exception {
        authenticate("customer@example.com", "CUSTOMER");
        when(accountQueryService.requireIdByEmail("customer@example.com")).thenReturn(1L);
        when(notificationService.listForAccount(1L, false, 0, 20)).thenReturn(List.of());

        mockMvc.perform(get("/api/notifications?is_read=false")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("customer@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void markReadReturnsUpdatedNotification() throws Exception {
        authenticate("customer@example.com", "CUSTOMER");
        when(accountQueryService.requireIdByEmail("customer@example.com")).thenReturn(1L);
        when(notificationService.markRead(1L, 10L)).thenReturn(readNotification(10L, "RENTAL_REQUEST_APPROVED"));

        mockMvc.perform(patch("/api/notifications/10/read")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("customer@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã đánh dấu thông báo là đã đọc"))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.readAt").isNotEmpty());
    }

    @Test
    void markReadReturns404ForOtherAccountNotification() throws Exception {
        authenticate("customer@example.com", "CUSTOMER");
        when(accountQueryService.requireIdByEmail("customer@example.com")).thenReturn(1L);
        when(notificationService.markRead(1L, 99L)).thenThrow(new NotificationNotFoundException());

        mockMvc.perform(patch("/api/notifications/99/read")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("customer@example.com")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy thông báo"));
    }

    @Test
    void markAllReadReturnsZeroUnread() throws Exception {
        authenticate("customer@example.com", "CUSTOMER");
        when(accountQueryService.requireIdByEmail("customer@example.com")).thenReturn(1L);
        when(notificationService.markAllRead(1L)).thenReturn(1);

        mockMvc.perform(patch("/api/notifications/read-all")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("customer@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã đánh dấu tất cả thông báo là đã đọc"))
                .andExpect(jsonPath("$.data.unreadCount").value(0));
    }

    private Notification readNotification(Long id, String type) {
        Notification notification = notification(id, type);
        ReflectionTestUtils.setField(notification, "readAt", Instant.now());
        return notification;
    }

    private void authenticate(String email, String roleName) {
        Account account = account(email, roleName);
        when(accountRepository.findWithRoleByEmail(email)).thenReturn(Optional.of(account));
    }

    private Account account(String email, String roleName) {
        Role role = mock(Role.class);
        when(role.getName()).thenReturn(roleName);
        Account account = mock(Account.class);
        when(account.getEmail()).thenReturn(email);
        when(account.getRole()).thenReturn(role);
        when(account.getStatus()).thenReturn(AccountStatus.ACTIVE);
        when(account.isLoginAllowed()).thenReturn(true);
        return account;
    }

    private Notification notification(Long id, String type) {
        Notification notification = new Notification(1L, type, "Tiêu đề", "Nội dung");
        ReflectionTestUtils.setField(notification, "id", id);
        return notification;
    }

    private String tokenFor(String email) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("self-storage")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .claim("email", email)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
