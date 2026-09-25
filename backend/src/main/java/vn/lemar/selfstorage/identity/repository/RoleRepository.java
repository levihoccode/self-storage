package vn.lemar.selfstorage.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.lemar.selfstorage.identity.domain.Role;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(String name);
}