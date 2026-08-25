package ch.bff.producer.services;

import java.util.List;

import ch.bff.producer.provider.models.PatientDto;

public interface PatientReadService {

	List<PatientDto> getPatientList();

}
