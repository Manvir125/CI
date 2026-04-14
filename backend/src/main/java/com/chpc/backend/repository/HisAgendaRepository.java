package com.chpc.backend.repository;

import com.chpc.backend.entity.HisAgenda;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HisAgendaRepository extends JpaRepository<HisAgenda, String> {
}
