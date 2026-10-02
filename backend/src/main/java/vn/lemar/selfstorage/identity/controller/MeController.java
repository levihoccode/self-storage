package vn.lemar.selfstorage.identity.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.lemar.selfstorage.identity.application.exception.UnauthenticatedException;

@RestController
@RequestMapping("/api/me")
@SecurityRequirement(name = "bearerAuth")
public class MeController {

    @GetMapping
    public ResponseEntity<Map<String, Object>> me(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            throw new UnauthenticatedException();
        }
        List<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        return ResponseEntity.ok(Map.of(
                "subject", authentication.getName(),
                "authorities", authorities));
    }
}