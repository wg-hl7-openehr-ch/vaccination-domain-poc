package ch.hl7.vacd.api.business.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import ch.hl7.vacd.api.business.PatientBusinessService;
import ch.hl7.vacd.api.client.EhrbaseClient;
import ch.hl7.vacd.api.client.OpenFhirClient;
import ch.hl7.vacd.api.entity.ResourceEntity;
import ch.hl7.vacd.api.exceptions.PatientNotFoundException;
import ch.hl7.vacd.api.repo.ArtefactRepository;
import ch.hl7.vacd.api.repo.ResourceRepository;

/**
 * Occurrence-date validation in createBundle: which Immunizations and which
 * birthDate are checked, and that a rejected Bundle has no side effects.
 */
@ExtendWith(MockitoExtension.class)
class BundleBusinessServiceImplTest {

	private static final String PATIENT_ID = "11111111-2222-3333-4444-555555555555";

	@Mock
	private ResourceRepository store;

	@Mock
	private ArtefactRepository artefactRepository;

	@Mock
	private EhrbaseClient ehrbaseClient;

	@Mock
	private OpenFhirClient openFhirClient;

	@Mock
	private PatientBusinessService patientBusinessService;

	private FhirContext fhirContext;
	private BundleBusinessServiceImpl service;

	@BeforeEach
	void setUp() {
		fhirContext = FhirContext.forR4();
		service = new BundleBusinessServiceImpl(fhirContext, store, artefactRepository, openFhirClient, ehrbaseClient,
				patientBusinessService);
	}

	private static Immunization immunization(String occurrence) {
		Immunization immunization = new Immunization();
		immunization.setStatus(Immunization.ImmunizationStatus.COMPLETED);
		immunization.setOccurrence(new DateTimeType(occurrence));
		return immunization;
	}

	/** Document Bundle whose Composition section references only {@code referenced}. */
	private static Bundle bundle(String birthDate, Immunization referenced, Immunization... unreferenced) {
		Patient patient = new Patient();
		if (birthDate != null) {
			patient.setBirthDateElement(new DateType(birthDate));
		}
		Composition composition = new Composition();
		composition.setSubject(new Reference("urn:uuid:" + PATIENT_ID));
		composition.addSection().addEntry(new Reference("urn:uuid:imm-0"));

		Bundle bundle = new Bundle().setType(Bundle.BundleType.DOCUMENT);
		bundle.addEntry().setFullUrl("urn:uuid:composition").setResource(composition);
		bundle.addEntry().setFullUrl("urn:uuid:" + PATIENT_ID).setResource(patient);
		bundle.addEntry().setFullUrl("urn:uuid:imm-0").setResource(referenced);
		for (int i = 0; i < unreferenced.length; i++) {
			bundle.addEntry().setFullUrl("urn:uuid:orphan-" + i).setResource(unreferenced[i]);
		}
		return bundle;
	}

	private void storePatient(String birthDate) {
		Patient stored = new Patient();
		stored.setId("Patient/" + PATIENT_ID);
		if (birthDate != null) {
			stored.setBirthDateElement(new DateType(birthDate));
		}
		ResourceEntity entity = new ResourceEntity();
		entity.setResourceType("Patient");
		entity.setResourceId(PATIENT_ID);
		entity.setJson(fhirContext.newJsonParser().encodeResourceToString(stored));
		when(store.findByResourceTypeAndResourceId("Patient", PATIENT_ID)).thenReturn(List.of(entity));
	}

	private void assertRejectedWithoutSideEffects(Bundle bundle, String expectedMessage) {
		var ex = assertThrows(UnprocessableEntityException.class, () -> service.createBundle(bundle));
		assertTrue(ex.getMessage().contains(expectedMessage), ex.getMessage());
		verifyNoInteractions(artefactRepository, ehrbaseClient, openFhirClient, patientBusinessService);
		verify(store, never()).save(any());
	}

	/**
	 * Validation passed if createBundle gets as far as the EHR lookup, which
	 * (no EHR, createPatientIfAbsent unset) ends in PatientNotFoundException.
	 */
	private void assertPassesValidation(Bundle bundle) {
		assertThrows(PatientNotFoundException.class, () -> service.createBundle(bundle));
		verify(ehrbaseClient).findEhrByPatient(PATIENT_ID);
	}

	@Test
	void createBundle_unreferencedImmunizationInFuture_isRejected() {
		when(store.findByResourceTypeAndResourceId("Patient", PATIENT_ID)).thenReturn(Collections.emptyList());
		Bundle bundle = bundle("2020-05-10", immunization("2021-01-01"), immunization("2099-01-01"));

		assertRejectedWithoutSideEffects(bundle, "must not be in the future");
	}

	@Test
	void createBundle_unreferencedImmunizationBeforeBirth_isRejected() {
		when(store.findByResourceTypeAndResourceId("Patient", PATIENT_ID)).thenReturn(Collections.emptyList());
		Bundle bundle = bundle("2020-05-10", immunization("2021-01-01"), immunization("2019-01-01"));

		assertRejectedWithoutSideEffects(bundle, "must not be before Patient.birthDate");
	}

	@Test
	void createBundle_storedPatientWithBirthDate_takesPrecedence() {
		storePatient("2010-01-01");
		Bundle bundle = bundle("2020-05-10", immunization("2015-01-01"));

		assertPassesValidation(bundle);
	}

	@Test
	void createBundle_storedPatientBirthDate_rejectsEarlierImmunization() {
		storePatient("2010-01-01");
		Bundle bundle = bundle("2000-01-01", immunization("2005-01-01"));

		assertRejectedWithoutSideEffects(bundle, "must not be before Patient.birthDate");
	}

	@Test
	void createBundle_allImmunizationsValid_passesValidation() {
		when(store.findByResourceTypeAndResourceId("Patient", PATIENT_ID)).thenReturn(Collections.emptyList());
		Bundle bundle = bundle("2020-05-10", immunization("2021-01-01"), immunization("2022-01-01"));

		assertPassesValidation(bundle);
	}
}
