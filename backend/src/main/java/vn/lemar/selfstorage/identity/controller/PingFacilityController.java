package vn.lemar.selfstorage.identity.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.lemar.selfstorage.identity.application.Access;
import vn.lemar.selfstorage.identity.domain.RoleName;

import java.util.Map;

/**
 * Endpoint DEMO de test Access.can()/canAccessFacility() (A3b). Module
 * facility chua co controller rieng — xoa file nay khi facility co endpoint
 * that su de thay the, hoac giu lam vi du tham khao cach dung Access.
 */
@RestController
@RequestMapping("/api/fm")
public class PingFacilityController {

    private final Access access;

    public PingFacilityController(Access access) {
        this.access = access;
    }

    @GetMapping("/ping-facility/{facilityId}")
    public ResponseEntity<Map<String, Object>> pingFacility(@PathVariable Long facilityId) {
        access.can(RoleName.FM, RoleName.FS, RoleName.ADMIN, RoleName.BOM);
        access.canAccessFacility(facilityId);

        return ResponseEntity.ok(Map.of(
                "message", "pong",
                "facilityId", facilityId
        ));
    }
}