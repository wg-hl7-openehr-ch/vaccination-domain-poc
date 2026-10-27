package ch.vaccination.domain.audittrace.providers;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.hl7.fhir.r4.model.AuditEvent;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.param.DateParam;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ch.vaccination.domain.audittrace.domain.AuditTraceLog;
import ch.vaccination.domain.audittrace.repositories.AuditTraceLogRepository;
import jakarta.persistence.Column;
import jakarta.servlet.http.HttpServletRequest;

@Component
public class AuditEventProvider implements IResourceProvider {

	private FhirContext fhirContext;
	private final AuditTraceLogRepository auditTraceLogRepository;

	public AuditEventProvider(FhirContext fhirContext, AuditTraceLogRepository auditTraceLogRepository) {
		this.fhirContext = fhirContext;
		this.auditTraceLogRepository = auditTraceLogRepository;
	}

	@Override
	public Class<AuditEvent> getResourceType() {
		return AuditEvent.class;
	}

	@Create
	public MethodOutcome create(@ResourceParam AuditEvent auditEvent, HttpServletRequest request) {

		// Save the AuditEvent to the repository
		auditEvent.setId(UUID.randomUUID().toString());

		AuditTraceLog createdTraceLog = auditTraceLogRepository.save(toAuditTraceLog(auditEvent, request));
		AuditEvent createdAuditEvent = fromAuditTraceLog(createdTraceLog);

		MethodOutcome outcome = new MethodOutcome();
		outcome.setId(new IdType("AuditEvent", createdAuditEvent.getId()));
		outcome.setResource(auditEvent);
		return outcome;

	}

	@Read
	public AuditEvent read(@IdParam IdType theId) {
		Optional<AuditTraceLog> storedTraceLog = auditTraceLogRepository.findByFhirId(theId.getIdPart());
		if (storedTraceLog.isPresent()) {
			return fromAuditTraceLog(storedTraceLog.get());
		}
		return null;
	}

	@Search
	public List<AuditEvent> search(@OptionalParam(name = "date") DateParam dateParam,
			@OptionalParam(name = "agent-name") StringParam agentName) {

		return auditTraceLogRepository.findAll().stream().map(this::fromAuditTraceLog).toList();
	}

	private AuditEvent fromAuditTraceLog(AuditTraceLog createdTraceLog) {
		AuditEvent auditEvent = fhirContext.newJsonParser().parseResource(AuditEvent.class,
				createdTraceLog.getContentMessage());
		auditEvent.setId(createdTraceLog.getFhirId());
		return auditEvent;
	}

	private AuditTraceLog toAuditTraceLog(AuditEvent auditEvent, HttpServletRequest request) {
		AuditTraceLog auditTraceLog = new AuditTraceLog();
		auditTraceLog.setFhirId(auditEvent.getIdElement().getIdPart());
		auditTraceLog.setMessageSource(request.getRemoteHost());
		auditTraceLog.setRecordetDate(auditEvent.getRecorded());
		auditTraceLog.setAgentName(auditEvent.getAgentFirstRep().getName());
		auditTraceLog.setContentMessage(fhirContext.newJsonParser().encodeResourceToString(auditEvent));
		return auditTraceLog;
	}

}
