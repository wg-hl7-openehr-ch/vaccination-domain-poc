package ch.bff.producer.services.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import ca.uhn.fhir.context.FhirContext;
import ch.bff.producer.client.FhirClient;
import ch.bff.producer.mapstruct.VaccinationsMapper;
import ch.bff.producer.provider.models.VaccinationDto;
import ch.bff.producer.services.VaccinationsReadService;

class VaccinationsReadServiceImplTest {

	private static final FhirContext FHIR_CTX = FhirContext.forR4();

	private FhirClientStub fhirClient;
	private VaccinationsReadService service;

	@BeforeEach
	void setUp() throws Exception {
		fhirClient = new FhirClientStub();
		try (var in = getClass().getResourceAsStream("/vacd-vaccination-record-bundle.json")) {
			fhirClient.bundleToReturn = FHIR_CTX.newJsonParser().parseResource(Bundle.class, in);
		}
		var mapper = Mappers.getMapper(VaccinationsMapper.class);
		service = new VaccinationsReadServiceImpl(fhirClient, mapper);
	}

	@Test
	void getVaccinationList_returnsAllImmunizationsFromSection() {
		var result = service.getVaccinationList("test-patient");

		assertEquals(2, result.size());
	}

	@Test
	void getVaccinationList_mapsBoostrixCorrectly() {
		var result = service.getVaccinationList("test-patient");

		var imm = findByIdPart(result, "7-2-Immunization");
		assertNotNull(imm);
		assertEquals("Boostrix", imm.vaccineName());
		assertEquals("1", imm.doseSequence());
		assertEquals(LocalDate.of(2013, 9, 15), imm.vaccinationDate());
		assertNull(imm.manufacturer());
		assertEquals("12-34244", imm.lotNumber());
		assertEquals("Intramuscular use", imm.administrationRoute());
		assertNull(imm.siteOfAdministration());
		assertNotNull(imm.practitioner());
		assertEquals("Detlef Demo", imm.practitioner().doctorName());
		assertNull(imm.practitioner().gln());
		assertNull(imm.vaccinationReason());
	}

	@Test
	void getVaccinationList_mapsMMR_IICorrectly() {
		var result = service.getVaccinationList("test-patient");

		var imm = findByIdPart(result, "7-5-Immunization");
		assertNotNull(imm);
		assertEquals("MMR-II", imm.vaccineName());
		assertEquals("1", imm.doseSequence());
		assertEquals(LocalDate.of(2016, 3, 5), imm.vaccinationDate());
		assertNull(imm.manufacturer());
		assertEquals("12-34244", imm.lotNumber());
		assertEquals("Intramuscular use", imm.administrationRoute());
		assertNull(imm.siteOfAdministration());
		assertNotNull(imm.practitioner());
		assertEquals("Max Muster", imm.practitioner().doctorName());
		assertNull(imm.practitioner().gln());
		assertNull(imm.vaccinationReason());
	}

	@Test
	void getVaccinationList_idIsTheFhirId() {
		var result = service.getVaccinationList("test-patient");

		assertEquals(List.of("7-2-Immunization", "7-5-Immunization"),
				result.stream().map(VaccinationDto::id).toList());
	}

	@Test
	void getVaccinationList_takesIdAndVersionOfAnEntryWithoutResourceId() {
		// $export-document carries the Immunization id only in the fullUrl
		var json = FHIR_CTX.newJsonParser().encodeResourceToString(fhirClient.bundleToReturn)
				.replace("http://test.fhir.ch/r4/Immunization/7-2-Immunization",
						"urn:uuid:0df77a69-3ab1-46de-8d15-3fbe050696ae")
				.replace("Immunization/7-2-Immunization", "urn:uuid:0df77a69-3ab1-46de-8d15-3fbe050696ae")
				.replace("\"id\":\"7-2-Immunization\",",
						"\"meta\":{\"versionId\":\"2\",\"lastUpdated\":\"2026-11-17T14:00:59.889+00:00\"},");
		fhirClient.bundleToReturn = FHIR_CTX.newJsonParser().parseResource(Bundle.class, json);

		var result = service.getVaccinationList("test-patient");

		assertEquals(2, result.size());
		var imm = result.stream().filter(dto -> "Boostrix".equals(dto.vaccineName())).findFirst().orElseThrow();
		assertEquals("0df77a69-3ab1-46de-8d15-3fbe050696ae", imm.id());
		assertEquals("2", imm.version());
		assertEquals(java.time.Instant.parse("2026-11-17T14:00:59.889Z"), imm.lastUpdated());
		assertTrue(imm.corrected());
	}

	@Test
	void getVaccinationList_practitionerIsNotNullEvenWhenOnlyReference() {
		var result = service.getVaccinationList("test-patient");

		for (var imm : result) {
			assertNotNull(imm.practitioner());
		}
	}

	@Test
	void getVaccinationList_emptyBundle_returnsEmptyList() {
		fhirClient.bundleToReturn = new Bundle();

		var result = service.getVaccinationList("test-patient");

		assertTrue(result.isEmpty());
	}

	@Test
	void getVaccinationList_bundleWithoutComposition_returnsEmptyList() {
		var bundle = new Bundle();
		bundle.addEntry().setResource(new Patient());

		fhirClient.bundleToReturn = bundle;
		var result = service.getVaccinationList("test-patient");

		assertTrue(result.isEmpty());
	}

	// ---- helpers ----

	private VaccinationDto findByIdPart(List<VaccinationDto> list, String idPart) {
		return list.stream().filter(dto -> dto.id().equals(idPart)).findFirst().orElse(null);
	}

	// ---- manual stub ----

	private static class FhirClientStub implements FhirClient {

		Bundle bundleToReturn;

		@Override
		public Bundle getPatient() {
			return null;
		}

		@Override
		public Patient getPatientById(String id) {
			return null;
		}

		@Override
		public Bundle postImmunizationAdministrationBundle(Bundle bundle) {
			return bundleToReturn;
		}

		@Override
		public Bundle getVaccinationRecord(String id, Parameters parameters) {
			// TODO Auto-generated method stub
			return bundleToReturn;
		}

		@Override
		public Patient createPatient(Patient patient) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public Bundle getPatientLogEntries(String personId) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public Immunization getImmunizationById(String id) {
			return null;
		}

		@Override
		public Immunization putImmunization(String id, Immunization immunization, String ifMatch,
				String provenance) {
			return null;
		}
	}
}
