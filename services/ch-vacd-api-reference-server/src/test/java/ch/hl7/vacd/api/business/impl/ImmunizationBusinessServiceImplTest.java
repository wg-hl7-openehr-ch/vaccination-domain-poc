package ch.hl7.vacd.api.business.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Date;
import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Immunization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ch.hl7.vacd.api.client.EhrbaseClient;
import ch.hl7.vacd.api.client.OpenFhirClient;
import ch.hl7.vacd.api.domain.CompositionVersion;
import ch.hl7.vacd.api.domain.FlatComposition;
import ch.hl7.vacd.api.entity.ResourceEntity;
import ch.hl7.vacd.api.entity.ResourceIdentifierEntity;
import ch.hl7.vacd.api.entity.ResourceReferenceEntity;
import ch.hl7.vacd.api.repo.ArtefactRepository;
import ch.hl7.vacd.api.repo.ResourceRepository;

/**
 * Read path of an Immunization: the latest version of its Composition is read
 * and the version is reported in meta.
 */
@ExtendWith(MockitoExtension.class)
class ImmunizationBusinessServiceImplTest {

	private static final String IMMUNIZATION_ID = "aaaaaaaa-0000-4000-8000-000000000001";
	private static final String PATIENT_ID = "11111111-2222-3333-4444-555555555555";
	private static final String EHR_ID = "eeeeeeee-0000-4000-8000-000000000001";
	private static final String COMPOSITION_UID = "cccccccc-0000-4000-8000-000000000001";
	private static final String FLAT = "{\"flat\":true}";

	@Mock
	private ResourceRepository store;

	@Mock
	private ArtefactRepository artefactRepository;

	@Mock
	private EhrbaseClient ehrbaseClient;

	@Mock
	private OpenFhirClient openFhirClient;

	private FhirContext fhirContext;
	private ImmunizationBusinessServiceImpl service;

	@BeforeEach
	void setUp() {
		fhirContext = FhirContext.forR4();
		service = new ImmunizationBusinessServiceImpl(fhirContext, store, artefactRepository, openFhirClient,
				ehrbaseClient);
	}

	private static String versionUid(int version) {
		return COMPOSITION_UID + "::local.ehrbase.org::" + version;
	}

	/** Stored Immunization as the create path leaves it: composition-uid of version 1. */
	private static ResourceEntity storedImmunization() {
		ResourceEntity entity = new ResourceEntity();
		entity.setResourceType("Immunization");
		entity.setResourceId(IMMUNIZATION_ID);
		entity.addIdentifier(new ResourceIdentifierEntity().setIdSystem("urn:che:epr:ch-vacd:composition-uid")
				.setIdValue(versionUid(1)).setResourceEntity(entity));
		entity.addIdentifier(new ResourceIdentifierEntity().setIdSystem("urn:che:epr:ch-vacd:ehr-id")
				.setIdValue("urn:uuid:" + EHR_ID).setResourceEntity(entity));
		entity.addReference(new ResourceReferenceEntity().setSourceField("Immunization.patient")
				.setTargetType("Patient").setTargetId(PATIENT_ID).setSourceEntity(entity));
		return entity;
	}

	/** The EHR holds {@code version} of the Composition; openFHIR maps it to an Immunization with {@code lotNumber}. */
	private void ehrHolds(int version, String lotNumber) {
		Immunization fromEhr = new Immunization();
		fromEhr.setStatus(Immunization.ImmunizationStatus.COMPLETED);
		fromEhr.setLotNumber(lotNumber);
		Bundle bundle = new Bundle();
		bundle.addEntry().setResource(fromEhr);

		when(ehrbaseClient.getLatestCompositionFlat(EHR_ID, COMPOSITION_UID))
				.thenReturn(new FlatComposition(versionUid(version), FLAT));
		when(openFhirClient.toFhir(FLAT)).thenReturn(fhirContext.newJsonParser().encodeResourceToString(bundle));
	}

	@Test
	void readImmunization_readsLatestVersion_andReportsItInMeta() {
		Date committed = new Date(1794000000000L);
		when(store.findByResourceTypeAndResourceId("Immunization", IMMUNIZATION_ID))
				.thenReturn(List.of(storedImmunization()));
		ehrHolds(3, "LOT-V3");
		when(ehrbaseClient.getCompositionVersion(EHR_ID, COMPOSITION_UID))
				.thenReturn(new CompositionVersion(versionUid(3), committed));

		Immunization result = service.readImmunization(new IdType("Immunization", IMMUNIZATION_ID));

		assertEquals("LOT-V3", result.getLotNumber());
		assertEquals(IMMUNIZATION_ID, result.getIdElement().getIdPart());
		assertEquals("3", result.getMeta().getVersionId());
		assertEquals(committed, result.getMeta().getLastUpdated());
		assertEquals("Patient/" + PATIENT_ID, result.getPatient().getReference());
		// the stored composition-uid of version 1 is not read anymore
		verify(ehrbaseClient, never()).getCompositionFlat(anyString(), anyString());
	}

	@Test
	void readImmunization_commitTimeOfAnotherVersion_isNotReported() {
		when(store.findByResourceTypeAndResourceId("Immunization", IMMUNIZATION_ID))
				.thenReturn(List.of(storedImmunization()));
		ehrHolds(3, "LOT-V3");
		// a new version was committed between the two calls
		when(ehrbaseClient.getCompositionVersion(EHR_ID, COMPOSITION_UID))
				.thenReturn(new CompositionVersion(versionUid(4), new Date()));

		Immunization result = service.readImmunization(new IdType("Immunization", IMMUNIZATION_ID));

		assertEquals("3", result.getMeta().getVersionId());
		assertNull(result.getMeta().getLastUpdated());
	}

	@Test
	void searchImmunizations_reportsVersionInMeta() {
		Date committed = new Date(1794000000000L);
		ResourceEntity entity = storedImmunization();
		when(store.findByResourceType("Immunization")).thenReturn(List.of(entity));
		when(store.findByResourceTypeAndResourceId("Immunization", IMMUNIZATION_ID)).thenReturn(List.of(entity));
		ehrHolds(2, "LOT-V2");
		when(ehrbaseClient.getCompositionVersion(EHR_ID, COMPOSITION_UID))
				.thenReturn(new CompositionVersion(versionUid(2), committed));

		List<Immunization> result = service.searchImmunizations(new ReferenceParam("Patient/" + PATIENT_ID));

		assertEquals(1, result.size());
		assertEquals("2", result.get(0).getMeta().getVersionId());
		assertEquals(committed, result.get(0).getMeta().getLastUpdated());
	}
}
