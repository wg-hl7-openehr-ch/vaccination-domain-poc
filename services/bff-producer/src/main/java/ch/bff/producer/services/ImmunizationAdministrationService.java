package ch.bff.producer.services;

import java.io.InputStream;

import ch.bff.producer.provider.models.ImmunizationCreateDto;
import ch.bff.producer.provider.models.ImmunizationUpdateDto;
import ch.bff.producer.provider.models.VaccinationDto;

public interface ImmunizationAdministrationService {

	VaccinationDto createImmunizationAdministration(String patientIamId, ImmunizationCreateDto createDto);

	VaccinationDto updateImmunization(String patientIamId, String immunizationId, ImmunizationUpdateDto updateDto);

	void importVaccinations(String personId, String contentType, InputStream inputStream);
}
