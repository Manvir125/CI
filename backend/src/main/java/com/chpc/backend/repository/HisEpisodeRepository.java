package com.chpc.backend.repository;

import com.chpc.backend.entity.HisEpisode;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HisEpisodeRepository extends JpaRepository<HisEpisode, String> {
}
