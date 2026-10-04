package vn.lemar.selfstorage.identity.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.lemar.selfstorage.ApiEnvelope;
import vn.lemar.selfstorage.ApiError;
import vn.lemar.selfstorage.identity.application.AuthService;
import vn.lemar.selfstorage.identity.application.dto.LoginRequest;
import vn.lemar.selfstorage.identity.application.dto.LoginResponse;
import vn.lemar.selfstorage.identity.application.dto.MeResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @SecurityRequirements
    @Operation(summary = "Đăng nhập", description = "Xác thực email + mật khẩu, trả access token (JWT).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đăng nhập thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ (validate hoặc JSON hỏng)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(value = """
                                    {"message":"Dữ liệu không hợp lệ",
                                    "timestamp":"2026-10-04T08:14:28.048550698Z"}"""))),
            @ApiResponse(responseCode = "401", description = "Email hoặc mật khẩu không đúng",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(value = """
                                    {"message":"Email hoặc mật khẩu không đúng",
                                    "timestamp":"2026-10-04T08:14:27.947381536Z"}"""))),
            @ApiResponse(responseCode = "403", description = "Tài khoản đã bị chặn",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(value = """
                                    {"message":"Tài khoản đã bị chặn",
                                    "timestamp":"2026-10-04T08:14:28.027349693Z"}""")))
    })
    @PostMapping("/login")
    public ResponseEntity<ApiEnvelope<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiEnvelope.ok("Đăng nhập thành công", authService.login(request)));
    }

    @Operation(summary = "Thông tin account hiện tại",
            description = "Trả email + role đọc từ DB cho token hiện tại; FE dùng sau login để biết quyền.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy thông tin tài khoản thành công"),
            @ApiResponse(responseCode = "401", description = "Thiếu / sai / hết hạn token, hoặc account đã bị xóa",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(value = """
                                    {"message":"An error occurred while attempting to decode the Jwt: Malformed token",
                                    "timestamp":"2026-10-04T08:14:32.840856214Z"}"""))),
            @ApiResponse(responseCode = "403", description = "Token hợp lệ nhưng account BANNED",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(value = """
                                    {"message":"Tài khoản đã bị chặn",
                                    "timestamp":"2026-10-04T08:14:28.027349693Z"}""")))
    })
    @GetMapping("/me")
    public ResponseEntity<ApiEnvelope<MeResponse>> me(Authentication authentication) {
        return ResponseEntity.ok(ApiEnvelope.ok("Lấy thông tin tài khoản thành công",
                authService.me(authentication.getName())));
    }
}
