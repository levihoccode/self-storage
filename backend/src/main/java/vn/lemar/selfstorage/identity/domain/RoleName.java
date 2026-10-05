package vn.lemar.selfstorage.identity.domain;

import org.springframework.modulith.NamedInterface;

/** Public role names accepted by identity authorization APIs. */
@NamedInterface("application")
public enum RoleName {
    ADMIN, BOM, FM, FS, CUSTOMER
}
