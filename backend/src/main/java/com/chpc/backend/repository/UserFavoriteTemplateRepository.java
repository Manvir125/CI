package com.chpc.backend.repository;

import com.chpc.backend.entity.UserFavoriteTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserFavoriteTemplateRepository extends JpaRepository<UserFavoriteTemplate, Long> {
    Optional<UserFavoriteTemplate> findByUserId(Long userId);
}
