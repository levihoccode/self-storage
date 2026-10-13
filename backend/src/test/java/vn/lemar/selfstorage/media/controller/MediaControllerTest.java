package vn.lemar.selfstorage.media.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import vn.lemar.selfstorage.SecurityConfig;
import vn.lemar.selfstorage.identity.application.AccountJwtAuthenticationConverter;
import vn.lemar.selfstorage.identity.application.AccountQueryService;
import vn.lemar.selfstorage.identity.config.JwtConfiguration;
import vn.lemar.selfstorage.identity.domain.Account;
import vn.lemar.selfstorage.identity.domain.AccountStatus;
import vn.lemar.selfstorage.identity.domain.Role;
import vn.lemar.selfstorage.identity.repository.AccountRepository;
import vn.lemar.selfstorage.media.FileStorage;
import vn.lemar.selfstorage.media.application.MediaService;
import vn.lemar.selfstorage.media.application.exception.InvalidUploadException;

@WebMvcTest(controllers = MediaController.class)
@Import({SecurityConfig.class, JwtConfiguration.class, AccountJwtAuthenticationConverter.class})
@TestPropertySource(properties = "security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
class MediaControllerTest {

    private static final String INVALID_TYPE_MESSAGE =
            "Định dạng file không được hỗ trợ (chỉ nhận JPEG, PNG, WebP, PDF)";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @MockBean
    private MediaService mediaService;

    @MockBean
    private AccountQueryService accountQueryService;

    @MockBean
    private AccountRepository accountRepository;

    @Test
    void uploadRejectsAnonymousRequest() throws Exception {
        mockMvc.perform(multipart("/api/media/uploads")
                        .file(new MockMultipartFile("file", "anh.png", "image/png", new byte[]{1})))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadReturnsStoredFile() throws Exception {
        authenticate("customer@example.com", "CUSTOMER");
        when(mediaService.upload(any(byte[].class), eq("anh.png"), eq("image/png")))
                .thenReturn(new FileStorage.StoredFile(
                        "https://res.cloudinary.com/x/anh.png", "anh", 2, "png"));

        mockMvc.perform(multipart("/api/media/uploads")
                        .file(new MockMultipartFile("file", "anh.png", "image/png", new byte[]{1, 2}))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("customer@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Tải file lên thành công"))
                .andExpect(jsonPath("$.data.url").value("https://res.cloudinary.com/x/anh.png"))
                .andExpect(jsonPath("$.data.publicId").value("anh"))
                .andExpect(jsonPath("$.data.bytes").value(2))
                .andExpect(jsonPath("$.data.format").value("png"));
    }

    @Test
    void uploadReturns400WithSpecificMessageWhenInvalid() throws Exception {
        authenticate("customer@example.com", "CUSTOMER");
        when(mediaService.upload(any(byte[].class), any(), any()))
                .thenThrow(new InvalidUploadException(INVALID_TYPE_MESSAGE));

        mockMvc.perform(multipart("/api/media/uploads")
                        .file(new MockMultipartFile("file", "a.exe", "application/x-msdownload", new byte[]{1}))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("customer@example.com")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(INVALID_TYPE_MESSAGE));
    }

    @Test
    void uploadRejectsMissingFilePart() throws Exception {
        authenticate("customer@example.com", "CUSTOMER");

        mockMvc.perform(multipart("/api/media/uploads")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("customer@example.com")))
                .andExpect(status().isBadRequest());
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
