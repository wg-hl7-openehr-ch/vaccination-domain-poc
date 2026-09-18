package ch.vaccination.domain.audittrace.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ch.vaccination.domain.audittrace.domain.AuditTraceLog;

@Repository
public interface AuditTraceLogRepository extends JpaRepository<AuditTraceLog, Long> {

	Optional<AuditTraceLog> findByFhirId(String idPart);

}
