package com.tech.docflow.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tech.docflow.models.AuditEvent;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
    
    List<AuditEvent> findByEntityTypeAndEntityId(String entityType, Long entityId);
    
    @Query("SELECT a FROM AuditEvent a WHERE a.entityType = :entityType AND a.entityId = :entityId AND a.timestamp >= :startTime AND a.timestamp <= :endTime")
    List<AuditEvent> findByEntityAndDateRange(
        @Param("entityType") String entityType,
        @Param("entityId") Long entityId,
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime
    );
    
}
