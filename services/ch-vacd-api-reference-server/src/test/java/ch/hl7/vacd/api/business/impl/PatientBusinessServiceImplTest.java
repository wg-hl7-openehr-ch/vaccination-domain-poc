package ch.hl7.vacd.api.business.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Enumeration;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.rest.param.StringParam;
import ch.hl7.vacd.api.client.EhrbaseClient;
import ch.hl7.vacd.api.client.OpenFhirClient;
import ch.hl7.vacd.api.entity.ResourceEntity;
import ch.hl7.vacd.api.exceptions.PatientNotFoundException;
import ch.hl7.vacd.api.openehr.ChVacdOpenEhrConstants;
import ch.hl7.vacd.api.repo.ResourceRepository;

@ExtendWith(MockitoExtension.class)
class PatientBusinessServiceImplTest {

	@Mock
	private ResourceRepository store;

	@Mock
	private EhrbaseClient ehrbaseClient;

	@Mock
	private OpenFhirClient openFhirClient;

	private FhirContext fhirContext;
	private PatientBusinessServiceImpl service;

	@BeforeEach
	void setUp() throws Exception {
		fhirContext = FhirContext.forR4();
		service = new PatientBusinessServiceImpl(fhirContext, store, openFhirClient, ehrbaseClient);
	}

	// -------------------------------------------------------------------------
	// createPatient
	// -------------------------------------------------------------------------

	@Test
	void testCreatePatient_assignsEhrIdentifier() {
		String ehrId = UUID.randomUUID().toString();
		when(ehrbaseClient.findOrCreateEhr(anyString())).thenReturn(ehrId);
		when(store.findByResourceTypeAndResourceId(eq("Patient"), anyString()))
				.thenReturn(Collections.emptyList());
		when(store.save(any(ResourceEntity.class))).thenAnswer(inv -> inv.getArgument(0));

		Patient patient = new Patient();
		patient.addName().setFamily("Doe").addGiven("Jane");

		Patient result = service.createPatient(patient);

		assertNotNull(result);
		assertTrue(result.getIdentifier().stream()
				.anyMatch(i -> "urn:che:epr:ch-vacd:ehr-id".equals(i.getSystem())
						&& ("urn:uuid:" + ehrId).equals(i.getValue())),
				"Patient should carry the ehr-id identifier");
		verify(ehrbaseClient, times(1)).findOrCreateEhr(anyString());
		verify(store, atLeastOnce()).save(any(ResourceEntity.class));
	}

	@Test
	void testCreatePatient_usesExistingIdWhenPresent() {
		String fixedId = UUID.randomUUID().toString();
		String ehrId = UUID.randomUUID().toString();
		when(ehrbaseClient.findOrCreateEhr(fixedId)).thenReturn(ehrId);
		when(store.findByResourceTypeAndResourceId(eq("Patient"), eq(fixedId)))
				.thenReturn(Collections.emptyList());
		when(store.save(any(ResourceEntity.class))).thenAnswer(inv -> inv.getArgument(0));

		Patient patient = new Patient();
		patient.setId("Patient/" + fixedId);
		patient.addName().setFamily("Smith");

		Patient result = service.createPatient(patient);

		// createIfAbsent rewrites the ID to just the id-part (without the resource type prefix)
		assertTrue(result.getId().contains(fixedId), "Patient id should contain the fixed id");
		verify(ehrbaseClient).findOrCreateEhr(fixedId);
	}

	// -------------------------------------------------------------------------
	// readPatient
	// -------------------------------------------------------------------------

	@Test
	void testReadPatient_foundInStore() {
		String patientId = UUID.randomUUID().toString();
		Patient stored = new Patient();
		stored.setId("Patient/" + patientId);
		stored.addName().setFamily("Huber").addGiven("Hans");
		String json = fhirContext.newJsonParser().encodeResourceToString(stored);

		ResourceEntity entity = new ResourceEntity();
		entity.setResourceType("Patient");
		entity.setResourceId(patientId);
		entity.setJson(json);

		when(store.findByResourceTypeAndResourceId("Patient", patientId))
				.thenReturn(List.of(entity));

		Patient result = service.readPatient(new IdType("Patient", patientId));

		assertNotNull(result);
		assertEquals("Huber", result.getNameFirstRep().getFamily());
		assertTrue(result.getMeta().getProfile().stream()
				.anyMatch(p -> p.getValue().contains("ch-core-patient-epr")),
				"Profile should be set on the returned patient");
	}

	@Test
	void testReadPatient_notInStore_returnsDefault() {
		String patientId = UUID.randomUUID().toString();
		when(store.findByResourceTypeAndResourceId("Patient", patientId))
				.thenReturn(Collections.emptyList());

		Patient result = service.readPatient(new IdType("Patient", patientId));

		assertNotNull(result);
		assertEquals(patientId, result.getIdElement().getIdPart());
		assertEquals("Test", result.getNameFirstRep().getFamily());
		assertEquals("Patient", result.getNameFirstRep().getGivenAsSingleString());
	}

	// -------------------------------------------------------------------------
	// searchPatient
	// -------------------------------------------------------------------------

	@Test
	void testSearchPatient_withoutFilter_returnsAll() {
		String id1 = UUID.randomUUID().toString();
		String id2 = UUID.randomUUID().toString();

		Patient p1 = buildPatient(id1, "Müller", "Anna");
		Patient p2 = buildPatient(id2, "Meier", "Beat");

		when(store.findByResourceType("Patient")).thenReturn(List.of(
				toEntity("Patient", id1, fhirContext.newJsonParser().encodeResourceToString(p1)),
				toEntity("Patient", id2, fhirContext.newJsonParser().encodeResourceToString(p2))));

		List<Patient> result = service.searchPatient(null);

		assertEquals(2, result.size());
	}

	@Test
	void testSearchPatient_withFamilyNameFilter_returnsMatch() {
		String id1 = UUID.randomUUID().toString();
		String id2 = UUID.randomUUID().toString();

		Patient p1 = buildPatient(id1, "Müller", "Anna");
		Patient p2 = buildPatient(id2, "Meier", "Beat");

		when(store.findByResourceType("Patient")).thenReturn(List.of(
				toEntity("Patient", id1, fhirContext.newJsonParser().encodeResourceToString(p1)),
				toEntity("Patient", id2, fhirContext.newJsonParser().encodeResourceToString(p2))));

		List<Patient> result = service.searchPatient(new StringParam("Müller"));

		assertEquals(1, result.size());
		assertEquals("Müller", result.get(0).getNameFirstRep().getFamily());
	}

	@Test
	void testSearchPatient_noMatch_returnsEmpty() {
		String id1 = UUID.randomUUID().toString();
		Patient p1 = buildPatient(id1, "Müller", "Anna");

		when(store.findByResourceType("Patient")).thenReturn(List.of(
				toEntity("Patient", id1, fhirContext.newJsonParser().encodeResourceToString(p1))));

		List<Patient> result = service.searchPatient(new StringParam("Unknown"));

		assertTrue(result.isEmpty());
	}

	// -------------------------------------------------------------------------
	// exportDocument
	// -------------------------------------------------------------------------

	@Test
	void testExportDocument_noEhrFound_throwsPatientNotFoundException() {
		String patientId = UUID.randomUUID().toString();
		Patient stored = buildPatient(patientId, "Tanner", "Eva");
		String json = fhirContext.newJsonParser().encodeResourceToString(stored);

		when(store.findByResourceTypeAndResourceId("Patient", patientId))
				.thenReturn(List.of(toEntity("Patient", patientId, json)));
		when(ehrbaseClient.findEhrByPatient(patientId)).thenReturn(null);

		assertThrows(PatientNotFoundException.class,
				() -> service.exportDocument(new IdType("Patient", patientId), new Parameters()));
	}

	@Test
	void testExportDocument_emptyImmunizations_returnsEmptyDocument() throws PatientNotFoundException {
		String patientId = UUID.randomUUID().toString();
		String ehrId = UUID.randomUUID().toString();
		Patient stored = buildPatient(patientId, "Brugger", "Felix");
		String json = fhirContext.newJsonParser().encodeResourceToString(stored);

		when(store.findByResourceTypeAndResourceId("Patient", patientId))
				.thenReturn(List.of(toEntity("Patient", patientId, json)));
		when(ehrbaseClient.findEhrByPatient(patientId)).thenReturn(ehrId);
		when(ehrbaseClient.getImmunizations(ehrId)).thenReturn(null);

		Bundle result = service.exportDocument(new IdType("Patient", patientId), new Parameters());

		assertNotNull(result);
		// No openFHIR conversion should happen for empty immunizations
		verify(openFhirClient, never()).toFhir(anyString(), anyString());
	}

	@Test
	void testExportDocument_withImmunizations_returnsDocumentWithEntries() throws PatientNotFoundException, DataFormatException, IOException {
		String patientId = UUID.randomUUID().toString();
		String ehrId = UUID.randomUUID().toString();

		Patient storedPatient = buildPatient(patientId, "Weber", "Greta");
		String patientJson = fhirContext.newJsonParser().encodeResourceToString(storedPatient);

		// Build a minimal FHIR Bundle with one Immunization for the openFHIR response
		Immunization immunization = new Immunization();
		immunization.setId(UUID.randomUUID().toString());
		immunization.setStatus(Immunization.ImmunizationStatus.COMPLETED);
		immunization.setOccurrence(new org.hl7.fhir.r4.model.DateTimeType(new Date()));
		immunization.setVaccineCode(new CodeableConcept()
				.addCoding(new Coding("http://fhir.ch/ig/ch-vacd/CodeSystem/ch-vacd-swissmedic-cs", "637", "Boostrix")));
		immunization.setPatient(new Reference("Patient/" + patientId));

		Bundle fhirBundle = new Bundle();
		fhirBundle.setType(Bundle.BundleType.COLLECTION);
		fhirBundle.addEntry().setResource(immunization);
		String fhirBundleJson = fhirContext.newJsonParser().encodeResourceToString(fhirBundle);

		when(store.findByResourceTypeAndResourceId("Patient", patientId))
				.thenReturn(List.of(toEntity("Patient", patientId, patientJson)));
		when(ehrbaseClient.findEhrByPatient(patientId)).thenReturn(ehrId);
		when(ehrbaseClient.getImmunizations(ehrId)).thenReturn("{\"someOpenEhrFlat\":true}");
		when(openFhirClient.toFhir("{\"someOpenEhrFlat\":true}", ChVacdOpenEhrConstants.VACC_TEMPLATE))
				.thenReturn(fhirBundleJson);
		// performer lookup - no performer on this immunization, so no further store calls

		Bundle result = service.exportDocument(new IdType("Patient", patientId), new Parameters());

		assertNotNull(result);
		verify(openFhirClient, times(1)).toFhir(anyString(), eq(ChVacdOpenEhrConstants.VACC_TEMPLATE));
		LoggerFactory.getLogger(PatientBusinessServiceImplTest.class).info("Exported FHIR Bundle:\n{}", fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(result));
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private Patient buildPatient(String id, String family, String given) {
		Patient p = new Patient();
		p.setId("Patient/" + id);
		p.addName().setFamily(family).addGiven(given);
		return p;
	}

	private ResourceEntity toEntity(String type, String id, String json) {
		ResourceEntity e = new ResourceEntity();
		e.setResourceType(type);
		e.setResourceId(id);
		e.setJson(json);
		return e;
	}
}
