package vn.lemar.selfstorage.identity.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.lemar.selfstorage.identity.application.AuthService;
import vn.lemar.selfstorage.identity.application.dto.LoginRequest;
import vn.lemar.selfstorage.identity.application.dto.LoginResponse;
import vn.lemar.selfstorage.identity.application.dto.RegisterRequest;
import vn.lemar.selfstorage.identity.application.dto.RegisterResponse;
import vn.lemar.selfstorage.identity.application.dto.ResendVerificationRequest;
import vn.lemar.selfstorage.identity.application.dto.ResendVerificationResponse;
import vn.lemar.selfstorage.identity.application.dto.VerifyEmailRequest;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Đăng ký, xác minh email và đăng nhập JWT (A3a-BE)")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Đăng nhập", description = """
            Trả JWT trong `accessToken`. Claims: `sub` = accountId, `email`, `role` (ADMIN|BOM|FM|FS|CUSTOMER).
            Body phản hồi: accountId, email, role, accessToken, expiresInSeconds.""")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/register")
    @Operation(summary = "Đăng ký Customer", description = "Tạo account UNVERIFIED và gửi email xác minh.")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @GetMapping("/verify-email")
    @Operation(
            summary = "Xác minh email (link trong mail)",
            description = "Contract chính cho FE: GET với query `token`.")
    public ResponseEntity<Void> verifyEmail(@RequestParam("token") String token) {
        authService.verifyEmail(new VerifyEmailRequest(token));
        return ResponseEntity.noContent().build();
    }

    /** Alias giữ tương thích với `VERIFY_BASE_URL` cũ trỏ `/api/auth/verify`. */
    @GetMapping("/verify")
    public ResponseEntity<Void> verifyEmailLegacy(@RequestParam("token") String token) {
        return verifyEmail(token);
    }

    @PostMapping("/resend-verification")
    @Operation(
            summary = "Gửi lại email xác minh",
            description = "Luôn trả cùng một thông báo (không lộ email có tồn tại hay không).")
    public ResponseEntity<ResendVerificationResponse> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {
        return ResponseEntity.ok(authService.resendVerificationEmail(request));
    }
}
