package com.staffdesk.repository;

import com.staffdesk.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Süzgeçler isteğe bağlı olduğundan sorgu Specification ile dinamik kurulur (boş parametre tipi sorunu olmaz). */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {}
