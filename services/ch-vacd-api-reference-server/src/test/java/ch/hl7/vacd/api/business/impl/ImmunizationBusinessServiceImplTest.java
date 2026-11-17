package ch.hl7.vacd.api.business.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Provenance;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.projecthusky.fhir.vacd.ch.common.resource.r4.ChVacdImmunization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.PreconditionFailedException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import ch.hl7.vacd.api.client.EhrbaseClient;
import ch.hl7.vacd.api.client.OpenFhirClient;
import ch.hl7.vacd.api.domain.CompositionVersion;
import ch.hl7.vacd.api.domain.FlatComposition;
import ch.hl7.vacd.api.entity.ResourceEntity;
import ch.hl7.vacd.api.entity.ResourceIdentifierEntity;
import ch.hl7.vacd.api.entity.ResourceReferenceEntity;
import ch.hl7.vacd.api.openehr.ChVacdOpenEhrConstants;
import ch.hl7.vacd.api.repo.ArtefactRepository;
import ch.hl7.vacd.api.repo.ResourceRepository;

/**
 * Read and correction of a stored Immunization. The EHR and openFHIR are faked:
 * the EHR holds one Composition in FLAT, and openFHIR maps lotNumber and
 * occurrenceDateTime between FHIR and FLAT.
 */
@ExtendWith(MockitoExtension.class)
class ImmunizationBusinessServiceImplTest {

	private static final String IMMUNIZATION_ID = "aaaaaaaa-0000-4000-8000-000000000001";
	private static final String PATIENT_ID = "11111111-2222-3333-4444-555555555555";
	private static final String EHR_ID = "eeeeeeee-0000-4000-8000-000000000001";
	private static final String COMPOSITION_UID = "cccccccc-0000-4000-8000-000000000001";

	private static final String ACTION = "ch_vacd_immunization_administration/medication_management:0/";
	private static final String LOT_KEY = ACTION + "medication_details/lot_number/text_value";
	private static final String TIME_KEY = ACTION + "time";
	/** A key the EHR holds but openFHIR does not return when mapping from FHIR. */
	private static final String ONLY_IN_EHR_KEY = ACTION + "_other_participation:0|function";
	private static final String FEEDER_AUDIT_KEY = "ch_vacd_immunization_administration/_feeder_audit/original_content";

	private static final ObjectMapper MAPPER = new ObjectMapper();

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

	/** The Composition the faked EHR holds. */
	private FlatComposition inEhr;
	private Date committed = new Date(1794000000000L);
	private ResourceEntity entity;

	@BeforeEach
	void setUp() throws Exception {
		fhirContext = FhirContext.forR4();
		service = new ImmunizationBusinessServiceImpl(fhirContext, store, artefactRepository, openFhirClient,
				ehrbaseClient);

		entity = storedImmunization();
		ehrHolds(1, "LOT-V1", "2021-01-01T00:00:00+00:00");

		lenient().when(store.findByResourceTypeAndResourceId("Immunization", IMMUNIZATION_ID))
				.thenReturn(List.of(entity));
		lenient().when(store.findByResourceTypeAndResourceId("Patient", PATIENT_ID))
				.thenReturn(List.of(storedPatient("2020-05-10")));

		lenient().when(ehrbaseClient.getLatestCompositionFlat(EHR_ID, COMPOSITION_UID)).thenAnswer(inv -> inEhr);
		lenient().when(ehrbaseClient.getCompositionVersion(EHR_ID, COMPOSITION_UID))
				.thenAnswer(inv -> new CompositionVersion(inEhr.versionUid(), committed));
		lenient().when(ehrbaseClient.updateCompositionFlat(eq(EHR_ID), anyString(), anyString(), anyString()))
				.thenAnswer(inv -> {
					int next = Integer.parseInt(CompositionVersion.versionNumber(inEhr.versionUid())) + 1;
					inEhr = new FlatComposition(versionUid(next), inv.getArgument(2));
					return inEhr.versionUid();
				});

		lenient().when(openFhirClient.toFhir(anyString())).thenAnswer(inv -> toFhir(inv.getArgument(0)));
		lenient().when(openFhirClient.toOpenEhr(anyString())).thenAnswer(inv -> toOpenEhr(inv.getArgument(0)));
	}

	// -------------------------------------------------------------------------
	// Fakes
	// -------------------------------------------------------------------------

	private static String versionUid(int version) {
		return COMPOSITION_UID + "::local.ehrbase.org::" + version;
	}

	private void ehrHolds(int version, String lotNumber, String time) {
		ObjectNode flat = MAPPER.createObjectNode();
		flat.put("ch_vacd_immunization_administration/_uid", versionUid(version));
		flat.put(LOT_KEY, lotNumber);
		flat.put(TIME_KEY, time);
		flat.put(ONLY_IN_EHR_KEY, "performer");
		flat.put(FEEDER_AUDIT_KEY, "{\"the\":\"original Bundle\"}");
		inEhr = new FlatComposition(versionUid(version), flat.toString());
	}

	/** openFHIR, FLAT to FHIR: a Bundle with the Immunization of the Composition. */
	private String toFhir(String flatJson) throws Exception {
		JsonNode flat = MAPPER.readTree(flatJson);
		Immunization immunization = new Immunization();
		immunization.setStatus(Immunization.ImmunizationStatus.COMPLETED);
		immunization.addIdentifier().setSystem("urn:ietf:rfc:3986").setValue("urn:uuid:" + IMMUNIZATION_ID);
		immunization.setVaccineCode(new CodeableConcept().addCoding(
				new Coding("http://fhir.ch/ig/ch-vacd/CodeSystem/ch-vacd-swissmedic-cs", "637", "Boostrix")));
		immunization.setLotNumber(flat.path(LOT_KEY).asText());
		immunization.setOccurrence(new DateTimeType(flat.path(TIME_KEY).asText()));
		immunization.addProtocolApplied().addTargetDisease(
				new CodeableConcept().addCoding(new Coding("http://snomed.info/sct", "397430003", "Diphtheria")));
		Bundle bundle = new Bundle();
		bundle.addEntry().setResource(immunization);
		return fhirContext.newJsonParser().encodeResourceToString(bundle);
	}

	/**
	 * openFHIR, FHIR to FLAT: the mapped fields of the Immunization in the Bundle.
	 * Like the real one it returns less than the EHR holds, and a context that
	 * differs from call to call.
	 */
	private String toOpenEhr(String bundleJson) {
		Bundle bundle = fhirContext.newJsonParser().parseResource(Bundle.class, bundleJson);
		Immunization immunization = bundle.getEntry().stream().map(Bundle.BundleEntryComponent::getResource)
				.filter(Immunization.class::isInstance).map(Immunization.class::cast).findFirst().orElseThrow();
		ObjectNode flat = MAPPER.createObjectNode();
		flat.put("ctx/time", "call-" + System.nanoTime());
		flat.put(LOT_KEY, immunization.getLotNumber());
		flat.put(TIME_KEY, immunization.getOccurrenceDateTimeType().getValueAsString());
		return flat.toString();
	}

	/** Stored Immunization as the create path leaves it: composition-uid of version 1. */
	private static ResourceEntity storedImmunization() {
		ResourceEntity entity = new ResourceEntity();
		entity.setResourceType("Immunization");
		entity.setResourceId(IMMUNIZATION_ID);
		entity.setJson("{\"resourceType\":\"Immunization\",\"lotNumber\":\"LOT-V1\"}");
		entity.addIdentifier(new ResourceIdentifierEntity().setIdSystem("urn:che:epr:ch-vacd:composition-uid")
				.setIdValue(versionUid(1)).setResourceEntity(entity));
		entity.addIdentifier(new ResourceIdentifierEntity().setIdSystem("urn:che:epr:ch-vacd:ehr-id")
				.setIdValue("urn:uuid:" + EHR_ID).setResourceEntity(entity));
		entity.addReference(new ResourceReferenceEntity().setSourceField("Immunization.patient")
				.setTargetType("Patient").setTargetId(PATIENT_ID).setSourceEntity(entity));
		return entity;
	}

	private ResourceEntity storedPatient(String birthDate) {
		Patient patient = new Patient();
		patient.setId("Patient/" + PATIENT_ID);
		patient.addName().setFamily("Muster").addGiven("Max");
		patient.setBirthDateElement(new DateType(birthDate));
		ResourceEntity entity = new ResourceEntity();
		entity.setResourceType("Patient");
		entity.setResourceId(PATIENT_ID);
		entity.setJson(fhirContext.newJsonParser().encodeResourceToString(patient));
		return entity;
	}

	/** The Immunization as a client reads it, with {@code change} applied. */
	private ChVacdImmunization corrected(Consumer<ChVacdImmunization> change) {
		Immunization read = service.readImmunization(new IdType("Immunization", IMMUNIZATION_ID));
		ChVacdImmunization corrected = fhirContext.newJsonParser().parseResource(ChVacdImmunization.class,
				fhirContext.newJsonParser().encodeResourceToString(read));
		change.accept(corrected);
		return corrected;
	}

	private static IdType id(String ifMatchVersion) {
		return new IdType("Immunization", IMMUNIZATION_ID, ifMatchVersion);
	}

	private void assertNothingWritten() {
		verify(ehrbaseClient, never()).updateCompositionFlat(anyString(), anyString(), anyString(), anyString());
		verify(ehrbaseClient, never()).postCompositionFlat(anyString(), anyString(), anyString());
		verify(store, never()).save(any());
		verifyNoInteractions(artefactRepository);
	}

	private void assertRejectedWithoutSideEffects(Class<? extends BaseServerResponseException> expected,
			String expectedMessage, IdType id, ChVacdImmunization corrected) {
		var ex = assertThrows(expected, () -> service.updateImmunization(id, corrected, null));
		assertTrue(ex.getMessage().contains(expectedMessage), ex.getMessage());
		assertNothingWritten();
	}

	// -------------------------------------------------------------------------
	// readImmunization / searchImmunizations
	// -------------------------------------------------------------------------

	@Test
	void readImmunization_readsLatestVersion_andReportsItInMeta() {
		ehrHolds(3, "LOT-V3", "2021-01-01T00:00:00+00:00");

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
		ehrHolds(3, "LOT-V3", "2021-01-01T00:00:00+00:00");
		// a new version was committed between the two calls
		when(ehrbaseClient.getCompositionVersion(EHR_ID, COMPOSITION_UID))
				.thenReturn(new CompositionVersion(versionUid(4), new Date()));

		Immunization result = service.readImmunization(new IdType("Immunization", IMMUNIZATION_ID));

		assertEquals("3", result.getMeta().getVersionId());
		assertNull(result.getMeta().getLastUpdated());
	}

	@Test
	void searchImmunizations_reportsVersionInMeta() {
		ehrHolds(2, "LOT-V2", "2021-01-01T00:00:00+00:00");
		when(store.findByResourceType("Immunization")).thenReturn(List.of(entity));

		List<Immunization> result = service.searchImmunizations(new ReferenceParam("Patient/" + PATIENT_ID));

		assertEquals(1, result.size());
		assertEquals("2", result.get(0).getMeta().getVersionId());
		assertEquals(committed, result.get(0).getMeta().getLastUpdated());
	}

	// -------------------------------------------------------------------------
	// updateImmunization
	// -------------------------------------------------------------------------

	@Test
	void updateImmunization_correction_writesNewVersionWithOnlyTheChangedKeys() throws Exception {
		ChVacdImmunization corrected = corrected(imm -> {
			imm.setLotNumber("LOT-KORR");
			imm.setOccurrence(new DateTimeType("2021-02-02T00:00:00+00:00"));
		});

		Immunization result = service.updateImmunization(id("1"), corrected, null);

		ArgumentCaptor<String> flat = ArgumentCaptor.forClass(String.class);
		verify(ehrbaseClient).updateCompositionFlat(eq(EHR_ID), eq(versionUid(1)), flat.capture(),
				eq(ChVacdOpenEhrConstants.ADMIN_TEMPLATE));
		JsonNode written = MAPPER.readTree(flat.getValue());
		assertEquals("LOT-KORR", written.path(LOT_KEY).asText());
		assertEquals("2021-02-02T00:00:00+00:00", written.path(TIME_KEY).asText());
		assertEquals("performer", written.path(ONLY_IN_EHR_KEY).asText(), "Keys only the EHR holds are kept");
		assertEquals("{\"the\":\"original Bundle\"}", written.path(FEEDER_AUDIT_KEY).asText());
		assertFalse(written.has("ctx/time"), "The context of the mapping is not written");
		assertEquals(5, written.size());

		assertEquals("LOT-KORR", result.getLotNumber());
		assertEquals("2", result.getMeta().getVersionId());
		assertEquals(IMMUNIZATION_ID, result.getIdElement().getIdPart());

		// local store: new JSON, identifiers and references untouched
		verify(store).save(entity);
		assertTrue(entity.getJson().contains("LOT-KORR"));
		assertEquals(2, entity.getIdentifiers().size());
		assertEquals(versionUid(1), entity.getIdentifiers().get(0).getIdValue());
		assertEquals(1, entity.getReferences().size());
		verifyNoInteractions(artefactRepository);
	}

	@Test
	void updateImmunization_withoutIfMatch_isAccepted() {
		ChVacdImmunization corrected = corrected(imm -> imm.setLotNumber("LOT-KORR"));

		Immunization result = service.updateImmunization(new IdType("Immunization", IMMUNIZATION_ID), corrected,
				null);

		assertEquals("2", result.getMeta().getVersionId());
	}

	@Test
	void updateImmunization_unchanged_writesNothing() {
		ChVacdImmunization unchanged = corrected(imm -> {
		});

		Immunization result = service.updateImmunization(id("1"), unchanged, "{\"resourceType\":\"Provenance\"}");

		assertEquals("1", result.getMeta().getVersionId());
		assertNothingWritten();
	}

	@Test
	void updateImmunization_unknownId_isNotFound_andNotCreated() {
		String unknown = "bbbbbbbb-0000-4000-8000-000000000002";
		ChVacdImmunization corrected = corrected(imm -> imm.setId(unknown));

		assertThrows(ResourceNotFoundException.class,
				() -> service.updateImmunization(new IdType("Immunization", unknown), corrected, null));

		assertNothingWritten();
		verify(openFhirClient, never()).toOpenEhr(anyString());
	}

	@Test
	void updateImmunization_idInBodyDiffersFromUrl_isRejected() {
		ChVacdImmunization corrected = corrected(imm -> imm.setId("bbbbbbbb-0000-4000-8000-000000000002"));

		assertRejectedWithoutSideEffects(UnprocessableEntityException.class, "must match the id in the URL", id("1"),
				corrected);
	}

	@Test
	void updateImmunization_staleIfMatch_isRejected() {
		ehrHolds(2, "LOT-V2", "2021-01-01T00:00:00+00:00");
		ChVacdImmunization corrected = corrected(imm -> imm.setLotNumber("LOT-KORR"));

		assertRejectedWithoutSideEffects(PreconditionFailedException.class, "is at version 2, not at version 1",
				id("1"), corrected);
	}

	@Test
	void updateImmunization_changedPatient_isRejected() {
		ChVacdImmunization corrected = corrected(
				imm -> imm.setPatient(new Reference("Patient/99999999-0000-4000-8000-000000000009")));

		assertRejectedWithoutSideEffects(UnprocessableEntityException.class, "Immunization.patient must not be changed",
				id("1"), corrected);
	}

	@Test
	void updateImmunization_changedStatus_isRejected() {
		ChVacdImmunization corrected = corrected(
				imm -> imm.setStatus(Immunization.ImmunizationStatus.ENTEREDINERROR));

		assertRejectedWithoutSideEffects(UnprocessableEntityException.class, "status must stay 'completed'", id("1"),
				corrected);
	}

	@Test
	void updateImmunization_dateBeforeBirth_isRejected() {
		ChVacdImmunization corrected = corrected(imm -> imm.setOccurrence(new DateTimeType("2019-01-01")));

		assertRejectedWithoutSideEffects(UnprocessableEntityException.class, "must not be before Patient.birthDate",
				id("1"), corrected);
	}

	@Test
	void updateImmunization_changedIdentifier_isRejected() {
		ChVacdImmunization corrected = corrected(imm -> imm.getIdentifier().clear());

		assertRejectedWithoutSideEffects(UnprocessableEntityException.class,
				"Immunization.identifier must not be changed", id("1"), corrected);
	}

	@Test
	void updateImmunization_changedVaccine_isRejected() {
		ChVacdImmunization corrected = corrected(imm -> imm.getVaccineCode().getCodingFirstRep().setCode("450"));

		assertRejectedWithoutSideEffects(UnprocessableEntityException.class, "cannot be corrected", id("1"),
				corrected);
	}

	@Test
	void updateImmunization_changedTargetDiseases_isRejected() {
		ChVacdImmunization corrected = corrected(imm -> imm.getProtocolAppliedFirstRep().addTargetDisease(
				new CodeableConcept().addCoding(new Coding("http://snomed.info/sct", "76902006", "Tetanus"))));

		assertRejectedWithoutSideEffects(UnprocessableEntityException.class, "cannot be corrected", id("1"),
				corrected);
	}

	@Test
	void updateImmunization_ehrRejectsVersion_isPreconditionFailed_withoutLocalChange() {
		ChVacdImmunization corrected = corrected(imm -> imm.setLotNumber("LOT-KORR"));
		// another correction was committed after the current version was read
		when(ehrbaseClient.updateCompositionFlat(eq(EHR_ID), anyString(), anyString(), anyString()))
				.thenThrow(new PreconditionFailedException("not the latest version anymore"));

		assertThrows(PreconditionFailedException.class, () -> service.updateImmunization(id("1"), corrected, null));

		verify(store, never()).save(any());
		assertFalse(entity.getJson().contains("LOT-KORR"));
	}

	@Test
	void updateImmunization_ehrRejectsComposition_isServerError_withoutLocalChange() {
		ChVacdImmunization corrected = corrected(imm -> imm.setLotNumber("LOT-KORR"));
		when(ehrbaseClient.updateCompositionFlat(eq(EHR_ID), anyString(), anyString(), anyString()))
				.thenThrow(new RuntimeException("EHRbase PUT composition 400 BAD_REQUEST: ..."));

		RuntimeException ex = assertThrows(RuntimeException.class,
				() -> service.updateImmunization(id("1"), corrected, null));

		// HAPI answers 500 for anything that is not one of its own exceptions
		assertFalse(ex instanceof BaseServerResponseException);
		verify(store, never()).save(any());
		assertFalse(entity.getJson().contains("LOT-KORR"));
	}

	@Test
	void updateImmunization_withProvenance_storesItForTheNewVersion() {
		ChVacdImmunization corrected = corrected(imm -> imm.setLotNumber("LOT-KORR"));

		service.updateImmunization(id("1"), corrected,
				"{\"resourceType\":\"Provenance\",\"reason\":[{\"text\":\"Falsches Lot erfasst\"}]}");

		ArgumentCaptor<ResourceEntity> saved = ArgumentCaptor.forClass(ResourceEntity.class);
		verify(store, times(2)).save(saved.capture());
		ResourceEntity provenanceEntity = saved.getAllValues().stream()
				.filter(e -> "Provenance".equals(e.getResourceType())).findFirst().orElseThrow();
		Provenance provenance = fhirContext.newJsonParser().parseResource(Provenance.class,
				provenanceEntity.getJson());
		assertEquals("Falsches Lot erfasst", provenance.getReasonFirstRep().getText());
		assertEquals("Immunization/" + IMMUNIZATION_ID + "/_history/2", provenance.getTargetFirstRep().getReference());
		assertTrue(provenance.hasRecorded());
		assertEquals(provenance.getIdElement().getIdPart(), provenanceEntity.getResourceId());
		assertEquals(IMMUNIZATION_ID, provenanceEntity.getReferences().get(0).getTargetId());
	}

	@Test
	void updateImmunization_invalidProvenance_isRejected() {
		ChVacdImmunization corrected = corrected(imm -> imm.setLotNumber("LOT-KORR"));

		assertThrows(InvalidRequestException.class,
				() -> service.updateImmunization(id("1"), corrected, "not a Provenance"));

		assertNothingWritten();
	}
}
