package com.chpc.backend.repository;

import com.chpc.backend.entity.HisAgendaAppointment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HisAgendaAppointmentRepository extends JpaRepository<HisAgendaAppointment, String> {
}
