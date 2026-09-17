package ch.bff.producer.services;

import java.util.List;

import ch.bff.producer.provider.models.LogEntryDto;
import ch.bff.producer.provider.models.PatientCreateDto;
import ch.bff.producer.provider.models.PatientDto;

public interface PatientService {

	List<PatientDto> getPatientList();

	PatientDto createPatient(PatientCreateDto patientDto);

	String exportPatient(String patientId, String format);

	List<LogEntryDto> getLogEntries(String personId);

}
