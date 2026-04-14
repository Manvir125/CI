package com.chpc.backend.repository;

import com.chpc.backend.entity.HisPatient;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HisPatientRepository extends JpaRepository<HisPatient, String> {
}
