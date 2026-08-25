package ch.bff.producer.services;

import ch.bff.producer.client.FhirClient;
import ch.bff.producer.mapstruct.PatientMapper;
import ch.bff.producer.provider.models.PatientDto;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PatientReadService extends AbstractReadService {

	private final PatientMapper patientMapper;

	public PatientReadService(FhirClient fhirClient, PatientMapper patientMapper) {
		super(fhirClient);
		this.patientMapper = patientMapper;
	}

	public List<PatientDto> getPatientList() {
		return fhirClient.getPatient().getEntry().stream()
				.filter(entry -> entry.getResource() instanceof org.hl7.fhir.r4.model.Patient)
				.map(entry -> (org.hl7.fhir.r4.model.Patient) entry.getResource()).map(patientMapper::toPatientDto)
				.toList();
	}
}
