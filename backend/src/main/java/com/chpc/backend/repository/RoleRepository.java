package com.chpc.backend.repository;

import com.chpc.backend.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByType(Role.RoleType type);
}