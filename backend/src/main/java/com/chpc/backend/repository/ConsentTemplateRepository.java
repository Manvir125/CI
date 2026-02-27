package com.chpc.backend.repository;

import com.chpc.backend.entity.ConsentTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface ConsentTemplateRepository extends JpaRepository<ConsentTemplate, Long> {

    // Busca plantillas activas por servicio
    List<ConsentTemplate> findByServiceCodeAndIsActiveTrue(String serviceCode);

    // Busca por nombre (contiene, ignorando mayúsculas)
    List<ConsentTemplate> findByNameContainingIgnoreCaseAndIsActiveTrue(String name);

    // Solo las activas
    List<ConsentTemplate> findByIsActiveTrue();

    // Última versión de una plantilla por nombre y procedimiento
    @Query("SELECT t FROM ConsentTemplate t WHERE t.procedureCode = :code " +
            "ORDER BY t.version DESC LIMIT 1")
    java.util.Optional<ConsentTemplate> findLatestByProcedureCode(String code);

    // Busca todas las versiones de una plantilla por nombre y procedimiento
    List<ConsentTemplate> findByNameAndProcedureCodeOrderByVersionDesc(String name, String procedureCode);
}