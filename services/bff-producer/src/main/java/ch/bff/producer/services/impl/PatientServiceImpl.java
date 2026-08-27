package ch.bff.producer.services.impl;

import ch.bff.producer.client.FhirClient;
import ch.bff.producer.mapstruct.PatientMapper;
import ch.bff.producer.provider.models.PatientCreateDto;
import ch.bff.producer.provider.models.PatientDto;
import ch.bff.producer.services.PatientService;

import org.hl7.fhir.r4.model.Patient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ca.uhn.fhir.context.FhirContext;

import java.util.List;

@Service
public class PatientServiceImpl extends AbstractReadService implements PatientService{
	
	private Logger logger = LoggerFactory.getLogger(PatientServiceImpl.class);

	private final PatientMapper patientMapper;

	public PatientServiceImpl(FhirClient fhirClient, PatientMapper patientMapper) {
		super(fhirClient);
		this.patientMapper = patientMapper;
	}

	@Override
	public List<PatientDto> getPatientList() {
		return fhirClient.getPatient().getEntry().stream()
				.filter(entry -> entry.getResource() instanceof org.hl7.fhir.r4.model.Patient)
				.map(entry -> (org.hl7.fhir.r4.model.Patient) entry.getResource()).map(patientMapper::toPatientDto)
				.toList();
	}

	@Override
	public PatientDto createPatient(PatientCreateDto patientDto) {
		org.hl7.fhir.r4.model.Patient created = fhirClient.createPatient(patientMapper.toPatient(patientDto));
		return patientMapper.toPatientDto(created);
	}
}
