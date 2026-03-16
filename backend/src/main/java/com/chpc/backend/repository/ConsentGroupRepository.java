package com.chpc.backend.repository;

import com.chpc.backend.entity.ConsentGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ConsentGroupRepository
        extends JpaRepository<ConsentGroup, Long> {

    List<ConsentGroup> findByNhcOrderByCreatedAtDesc(String nhc);

    List<ConsentGroup> findByEpisodeIdOrderByCreatedAtDesc(String episodeId);
}