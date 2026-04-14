package com.chpc.backend.repository;

import com.chpc.backend.entity.HisProfessional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HisProfessionalRepository extends JpaRepository<HisProfessional, String> {
}
