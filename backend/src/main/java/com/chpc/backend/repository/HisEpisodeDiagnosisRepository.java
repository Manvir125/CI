package com.chpc.backend.repository;

import com.chpc.backend.entity.HisEpisodeDiagnosis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface HisEpisodeDiagnosisRepository extends JpaRepository<HisEpisodeDiagnosis, Long> {

    @Transactional
    void deleteByEpisodeEpisodeId(String episodeId);
}
