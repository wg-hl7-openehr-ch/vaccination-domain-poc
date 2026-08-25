package ch.bff.producer.services;

import java.util.List;

import ch.bff.producer.provider.models.VaccinationDto;

public interface VaccinationsReadService {

	List<VaccinationDto> getVaccinationList(String patientIamId);
	
	String exportVaccinations(String patientIamId, String format);

}