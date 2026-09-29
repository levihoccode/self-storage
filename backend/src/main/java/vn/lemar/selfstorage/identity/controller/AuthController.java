package vn.lemar.selfstorage.identity.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.lemar.selfstorage.identity.application.AuthService;
import vn.lemar.selfstorage.identity.application.dto.LoginRequest;
import vn.lemar.selfstorage.identity.application.dto.LoginResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}