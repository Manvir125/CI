package com.chpc.backend.repository;

import com.chpc.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    boolean existsByUsername(String username);

    Optional<User> findByServiceCode(String serviceCode);

    @Query("""
            SELECT DISTINCT u FROM User u
            JOIN u.roles r
            WHERE u.isActive = true
              AND r.type = com.chpc.backend.entity.Role.RoleType.PROFESSIONAL
            ORDER BY u.fullName ASC
            """)
    List<User> findActiveProfessionals();
}
