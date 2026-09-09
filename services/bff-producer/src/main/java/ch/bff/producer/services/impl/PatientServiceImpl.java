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
public class PatientServiceImpl extends AbstractReadService implements PatientService {

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
		Patient patient = patientMapper.toPatient(patientDto);
		patient.addIdentifier()//
				.setSystem("urn:ietf:rfc:3986")//
				.setValue("urn:uuid:" + java.util.UUID.randomUUID())//
				.setUse(org.hl7.fhir.r4.model.Identifier.IdentifierUse.USUAL);
		org.hl7.fhir.r4.model.Patient created = fhirClient.createPatient(patient);
		return patientMapper.toPatientDto(created);
	}

	@Override
	public String exportPatient(String patientId, String format) {
		Patient patient = fhirClient.getPatientById(patientId);
		FhirContext ctx = FhirContext.forR4();
		if ("json".equalsIgnoreCase(format)) {
			return ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(patient);
		} else if ("xml".equalsIgnoreCase(format)) {
			return ctx.newXmlParser().setPrettyPrint(true).encodeResourceToString(patient);
		} else {
			return "";
		}
	}
}
