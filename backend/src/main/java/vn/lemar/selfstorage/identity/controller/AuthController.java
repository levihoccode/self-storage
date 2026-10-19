package vn.lemar.selfstorage.identity.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
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
import vn.lemar.selfstorage.identity.application.EmailVerificationResult;
import vn.lemar.selfstorage.identity.application.RegistrationService;
import vn.lemar.selfstorage.identity.application.dto.LoginRequest;
import vn.lemar.selfstorage.identity.application.dto.LoginResponse;
import vn.lemar.selfstorage.identity.application.dto.MeResponse;
import vn.lemar.selfstorage.identity.application.dto.RegisterRequest;
import vn.lemar.selfstorage.identity.application.dto.ResendVerificationRequest;
import vn.lemar.selfstorage.identity.application.dto.VerifyEmailRequest;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RegistrationService registrationService;

    public AuthController(AuthService authService, RegistrationService registrationService) {
        this.authService = authService;
        this.registrationService = registrationService;
    }

    @SecurityRequirements
    @Operation(summary = "Đăng nhập", description = "Xác thực email + mật khẩu, trả access token (JWT). "
            + "Sai quá nhiều lần bị chặn tạm thời (429).")
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
            @ApiResponse(responseCode = "403", description = "Tài khoản không được đăng nhập — "
                    + "bị chặn hoặc chưa xác minh email",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = {
                                    @ExampleObject(name = "banned", value = """
                                            {"message":"Tài khoản đã bị chặn",
                                            "timestamp":"2026-10-04T08:14:28.027349693Z"}"""),
                                    @ExampleObject(name = "unverified", value = """
                                            {"message":"Tài khoản chưa xác minh email",
                                            "timestamp":"2026-10-04T08:14:28.027349693Z"}""")
                            })),
            @ApiResponse(responseCode = "429", description = "Sai quá nhiều lần "
                    + "(5 lần/15 phút theo email hoặc 20 lần/15 phút theo IP)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(value = """
                                    {"message":"Quá nhiều lần thử. Vui lòng thử lại sau.",
                                    "timestamp":"2026-10-15T10:08:49.428678409Z"}""")))
    })
    @PostMapping("/login")
    public ResponseEntity<ApiEnvelope<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiEnvelope.ok("Đăng nhập thành công",
                authService.login(request, httpRequest.getRemoteAddr())));
    }

    @SecurityRequirements
    @Operation(summary = "Đăng ký tài khoản khách hàng",
            description = "Ghi nhận yêu cầu đăng ký và gửi email chứa mã xác minh 6 số (hiệu lực 10 phút); "
                    + "Account chỉ được tạo sau khi khách nhập đúng mã. Email đã có account → 409; "
                    + "các lần đăng ký/gửi lại mã cách nhau tối thiểu 60 giây.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đăng ký thành công, đã gửi email xác minh"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ "
                    + "(email sai định dạng, mật khẩu dưới 8 ký tự, JSON hỏng)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(value = """
                                    {"message":"Dữ liệu không hợp lệ",
                                    "timestamp":"2026-10-15T10:07:56.947684468Z"}"""))),
            @ApiResponse(responseCode = "409", description = "Email đã được đăng ký",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(value = """
                                    {"message":"Email đã được đăng ký",
                                    "timestamp":"2026-10-15T10:07:56.911733683Z"}"""))),
            @ApiResponse(responseCode = "429", description = "Gửi mã quá nhanh (cooldown 60 giây)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(value = """
                                    {"message":"Vui lòng chờ trước khi yêu cầu gửi lại mã.",
                                    "timestamp":"2026-10-15T10:08:37.700101223Z"}""")))
    })
    @PostMapping("/register")
    public ResponseEntity<ApiEnvelope<Void>> register(@Valid @RequestBody RegisterRequest request) {
        registrationService.register(request);
        return ResponseEntity.ok(ApiEnvelope.ok("Đăng ký thành công. Kiểm tra email để lấy mã xác minh.", null));
    }

    @SecurityRequirements
    @Operation(summary = "Xác minh email bằng mã",
            description = "Nhập mã 6 số nhận qua email (hiệu lực 10 phút, tối đa 5 lần nhập sai). "
                    + "Mã đúng → tạo Account role CUSTOMER đã xác minh (nếu chưa có). "
                    + "Account đã xác minh trước đó → 200 với message \"Email đã được xác minh trước đó\" "
                    + "(không kiểm mã, không lỗi).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Xác minh email thành công (tạo account nếu "
                    + "chưa có) — hoặc \"Email đã được xác minh trước đó\" nếu account đã xác minh từ trước"),
            @ApiResponse(responseCode = "400", description = "Mã sai hoặc đã hết hạn",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(value = """
                                    {"message":"Mã xác minh không đúng hoặc đã hết hạn",
                                    "timestamp":"2026-10-15T10:08:37.807650115Z"}"""))),
            @ApiResponse(responseCode = "429", description = "Nhập sai quá 5 lần — gửi lại mã mới",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(value = """
                                    {"message":"Bạn đã nhập sai quá nhiều lần. Vui lòng gửi lại mã mới.",
                                    "timestamp":"2026-10-15T10:08:38.184864391Z"}""")))
    })
    @PostMapping("/verify-email")
    public ResponseEntity<ApiEnvelope<Void>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        EmailVerificationResult result = registrationService.verifyEmail(request);
        String message = result == EmailVerificationResult.ALREADY_VERIFIED
                ? "Email đã được xác minh trước đó"
                : "Xác minh email thành công";
        return ResponseEntity.ok(ApiEnvelope.ok(message, null));
    }

    @SecurityRequirements
    @Operation(summary = "Gửi lại mã xác minh",
            description = "Phát mã mới (ghi đè mã cũ) cho yêu cầu đăng ký đang chờ xác minh hoặc account "
                    + "legacy chưa xác minh; mỗi lần cách nhau tối thiểu 60 giây.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đã gửi lại mã xác minh"),
            @ApiResponse(responseCode = "400", description = "Email chưa đăng ký hoặc tài khoản đã xác minh",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(value = """
                                    {"message":"Email chưa được đăng ký",
                                    "timestamp":"2026-10-15T10:09:07.393585034Z"}"""))),
            @ApiResponse(responseCode = "429", description = "Gửi lại quá nhanh (cooldown 60 giây)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(value = """
                                    {"message":"Vui lòng chờ trước khi yêu cầu gửi lại mã.",
                                    "timestamp":"2026-10-15T10:08:37.700101223Z"}""")))
    })
    @PostMapping("/resend-verification")
    public ResponseEntity<ApiEnvelope<Void>> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        registrationService.resendVerification(request);
        return ResponseEntity.ok(ApiEnvelope.ok("Đã gửi lại mã xác minh", null));
    }

    @Operation(summary = "Thông tin account hiện tại",
            description = "Trả email + role đọc từ DB cho token hiện tại; FE dùng sau login để biết quyền.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy thông tin tài khoản thành công"),
            @ApiResponse(responseCode = "401", description = "Thiếu / sai / hết hạn token, hoặc account đã bị xóa",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(value = """
                                    {"message":"Bạn cần đăng nhập để tiếp tục.",
                                    "timestamp":"2026-10-04T08:14:32.840856214Z"}"""))),
            @ApiResponse(responseCode = "403", description = "Token hợp lệ nhưng account BANNED "
                    + "hoặc chưa xác minh email",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = {
                                    @ExampleObject(name = "banned", value = """
                                            {"message":"Tài khoản đã bị chặn",
                                            "timestamp":"2026-10-04T08:14:28.027349693Z"}"""),
                                    @ExampleObject(name = "unverified", value = """
                                            {"message":"Tài khoản chưa xác minh email",
                                            "timestamp":"2026-10-04T08:14:28.027349693Z"}""")
                            }))
    })
    @GetMapping("/me")
    public ResponseEntity<ApiEnvelope<MeResponse>> me(Authentication authentication) {
        return ResponseEntity.ok(ApiEnvelope.ok("Lấy thông tin tài khoản thành công",
                authService.me(authentication.getName())));
    }
}
