package ch.bff.producer.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Calendar;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import ca.uhn.fhir.context.FhirContext;
import ch.bff.producer.client.FhirClient;
import ch.bff.producer.provider.models.AdministeredDose;
import ch.bff.producer.provider.models.ImmunizationCreateDto;
import ch.bff.producer.provider.models.RouteOfAdministration;
import ch.bff.producer.provider.models.VaccinationReason;

class ImmunizationAdministrationServiceTest {

	private static final FhirContext FHIR_CTX = FhirContext.forR4();
	private static final ObjectMapper MAPPER = new ObjectMapper();

	private FhirClientStub fhirClient;
	private ImmunizationAdministrationService service;

	@BeforeEach
	void setUp() {
		fhirClient = new FhirClientStub();
		fhirClient.patientToReturn = createMockPatient();
		service = new ImmunizationAdministrationService(fhirClient);
	}

	@Test
	void createImmunizationAdministration_returnsCorrectBundleJson() throws Exception {
		var dto = createDefaultDto();

		service.createImmunizationAdministration("test-patient", dto);

		var bundle = fhirClient.postedBundle;
		assertNotNull(bundle);
		var json = FHIR_CTX.newJsonParser().encodeResourceToString(bundle);
		var root = MAPPER.readTree(json);

		// ---- Bundle-level ----
		assertEquals("Bundle", root.get("resourceType").asText());
		assertEquals("document", root.get("type").asText());
		assertEquals("http://fhir.ch/ig/ch-vacd/StructureDefinition/ch-vacd-document-immunization-administration",
				root.get("meta").get("profile").get(0).asText());
		assertEquals("urn:ietf:rfc:3986", root.get("identifier").get("system").asText());
		assertNotNull(root.get("timestamp"));
		assertNotNull(root.get("id"));

		assertEquals(8, root.get("entry").size());

		// ---- Entry[0] Composition ----
		var comp = root.get("entry").get(0).get("resource");
		assertEquals("Composition", comp.get("resourceType").asText());
		assertEquals("http://fhir.ch/ig/ch-vacd/StructureDefinition/ch-vacd-composition-immunization-administration",
				comp.get("meta").get("profile").get(0).asText());
		assertEquals("final", comp.get("status").asText());
		assertEquals("Immunization Administration", comp.get("title").asText());
		assertEquals("urn:ietf:rfc:3986", comp.get("identifier").get("system").asText());
		assertEquals("41000179103", comp.get("type").get("coding").get(0).get("code").asText());
		assertEquals("urn:che:epr:ch-vacd:immunization-administration:2022",
				comp.get("category").get(0).get("coding").get(0).get("code").asText());
		assertNotNull(comp.get("date"));
		assertTrue(comp.get("subject").get("reference").asText().startsWith("urn:uuid:"));
		assertEquals(1, comp.get("author").size());
		assertTrue(comp.get("author").get(0).get("reference").asText().startsWith("urn:uuid:"));

		// Composition section
		assertEquals(1, comp.get("section").size());
		assertEquals("List Immunization Administration", comp.get("section").get(0).get("title").asText());
		assertEquals("11369-6", comp.get("section").get(0).get("code").get("coding").get(0).get("code").asText());
		assertEquals(1, comp.get("section").get(0).get("entry").size());
		assertTrue(comp.get("section").get(0).get("entry").get(0).get("reference").asText().startsWith("urn:uuid:"));

		// ---- Entry[1] Patient (copied from stub) ----
		var patientJson = root.get("entry").get(1).get("resource");
		assertEquals("Patient", patientJson.get("resourceType").asText());
		assertEquals("Test", patientJson.get("name").get(0).get("family").asText());
		assertEquals("Patient", patientJson.get("name").get(0).get("given").get(0).asText());
		assertEquals("male", patientJson.get("gender").asText());
		assertEquals("1990-01-15", patientJson.get("birthDate").asText());
		assertTrue(root.get("entry").get(1).get("fullUrl").asText().startsWith("urn:uuid:"));

		// ---- Entry[5] Immunization ----
		var imm = root.get("entry").get(5).get("resource");
		assertEquals("Immunization", imm.get("resourceType").asText());
		assertEquals("http://fhir.ch/ig/ch-vacd/StructureDefinition/ch-vacd-immunization",
				imm.get("meta").get("profile").get(0).asText());
		assertEquals("completed", imm.get("status").asText());
//		assertEquals("Testivac", imm.get("vaccineCode").get("text").asText());
		assertEquals("Boostrix Polio", imm.get("vaccineCode").get("coding").get(0).get("display").asText());
		assertTrue(imm.get("occurrenceDateTime").asText().startsWith("2025-06-15"));
		assertEquals("LOT12345", imm.get("lotNumber").asText());
		assertTrue(imm.get("patient").get("reference").asText().startsWith("urn:uuid:"));

		// Route
		assertEquals("IM", imm.get("route").get("coding").get(0).get("code").asText());
		assertEquals("Intramuscular", imm.get("route").get("coding").get(0).get("display").asText());
		assertEquals("http://terminology.hl7.org/CodeSystem/v3-RouteOfAdministration",
				imm.get("route").get("coding").get(0).get("system").asText());

		// Dose quantity
		assertEquals(0.5, imm.get("doseQuantity").get("value").asDouble(), 0.001);
		assertEquals("ml", imm.get("doseQuantity").get("unit").asText());

		// Site
		assertEquals("Left upper arm", imm.get("site").get("text").asText());

		// Manufacturer
		assertEquals("TestPharma AG", imm.get("manufacturer").get("display").asText());

		// Reason code
		assertEquals(1, imm.get("reasonCode").size());
		assertEquals("840539006", imm.get("reasonCode").get(0).get("coding").get(0).get("code").asText());
		assertEquals("http://snomed.info/sct",
				imm.get("reasonCode").get(0).get("coding").get(0).get("system").asText());
		assertEquals("COVID-19", imm.get("reasonCode").get(0).get("text").asText());

		// Protocol applied
		assertEquals(1, imm.get("protocolApplied").size());
		assertEquals(3, imm.get("protocolApplied").get(0).get("doseNumberPositiveInt").asInt());
		assertEquals(3, imm.get("protocolApplied").get(0).get("seriesDosesPositiveInt").asInt());

		// Performer
		assertEquals(1, imm.get("performer").size());
		assertEquals("Dr. med. Sarah Müller", imm.get("performer").get(0).get("actor").get("display").asText());

		// ---- Entry[4] Practitioner ----
		var pract = root.get("entry").get(4).get("resource");
		assertEquals("Practitioner", pract.get("resourceType").asText());
		assertEquals(1, pract.get("identifier").size());
		assertEquals("urn:oid:2.51.1.3", pract.get("identifier").get(0).get("system").asText());
		assertEquals("7601000123456", pract.get("identifier").get(0).get("value").asText());
		assertEquals("Müller", pract.get("name").get(0).get("family").asText());
		assertEquals("Sarah", pract.get("name").get(0).get("given").get(0).asText());

		// ---- Entry[3] Organization ----
		var org = root.get("entry").get(3).get("resource");
		assertEquals("Organization", org.get("resourceType").asText());
		assertEquals("Praxis am Bahnhof", org.get("name").asText());

		// ---- Entry[5] PractitionerRole ----
		var role = root.get("entry").get(2).get("resource");
		assertEquals("PractitionerRole", role.get("resourceType").asText());
		var rolePractRef = role.get("practitioner").get("reference").asText();
		var roleOrgRef = role.get("organization").get("reference").asText();
		assertTrue(rolePractRef.startsWith("urn:uuid:"));
		assertTrue(roleOrgRef.startsWith("urn:uuid:"));

		// PractitionerRole references match Practitioner and Organization fullUrls
		assertEquals(root.get("entry").get(4).get("fullUrl").asText(), rolePractRef);
		assertEquals(root.get("entry").get(3).get("fullUrl").asText(), roleOrgRef);
	}

	@Test
	void createImmunizationAdministration_withoutSeriesDoses_omitsField() throws Exception {
		var dto = new ImmunizationCreateDto("http://fhir.ch/ig/ch-vacd/CodeSystem/ch-vacd-swissmedic-cs|637|Boostrix", "TESTCODE", "TestPharma AG", "LOT12345",
				LocalDate.of(2026, 1, 1), LocalDate.of(2025, 6, 15), RouteOfAdministration.IM,
				new AdministeredDose(0.5, "ml"), "Left upper arm", new VaccinationReason("840539006", "COVID-19", null),
				3, null, false);

		service.createImmunizationAdministration("test-patient", dto);

		var json = FHIR_CTX.newJsonParser().encodeResourceToString(fhirClient.postedBundle);
		var root = MAPPER.readTree(json);
		var imm = root.get("entry").get(5).get("resource");

		assertEquals(3, imm.get("protocolApplied").get(0).get("doseNumberPositiveInt").asInt());
		assertNull(imm.get("protocolApplied").get(0).get("seriesDosesPositiveInt"));
	}

	@ParameterizedTest
	@EnumSource(RouteOfAdministration.class)
	void allRoutes_mapCorrectly(RouteOfAdministration route) throws Exception {
		var dto = new ImmunizationCreateDto("http://fhir.ch/ig/ch-vacd/CodeSystem/ch-vacd-swissmedic-cs|637|Boostrix", "TESTCODE", "TestPharma AG", "LOT12345",
				LocalDate.of(2026, 1, 1), LocalDate.of(2025, 6, 15), route, new AdministeredDose(0.5, "ml"),
				"Left upper arm", new VaccinationReason("840539006", "COVID-19", null), 3, 3, false);

		service.createImmunizationAdministration("test-patient", dto);

		var json = FHIR_CTX.newJsonParser().encodeResourceToString(fhirClient.postedBundle);
		var root = MAPPER.readTree(json);
		var imm = root.get("entry").get(5).get("resource");

		var routeCode = imm.get("route").get("coding").get(0).get("code").asText();
		assertEquals(route.name(), routeCode);

		var display = imm.get("route").get("coding").get(0).get("display").asText();
		assertNotNull(display);
		assertFalse(display.isBlank());

		assertEquals("http://terminology.hl7.org/CodeSystem/v3-RouteOfAdministration",
				imm.get("route").get("coding").get(0).get("system").asText());
	}

	@Test
	void createImmunizationAdministration_returnsVaccinationDto() {
		var dto = createDefaultDto();

		var result = service.createImmunizationAdministration("test-patient", dto);

		assertNotNull(result);
		assertNotNull(result.id());
		assertEquals("http://fhir.ch/ig/ch-vacd/CodeSystem/ch-vacd-swissmedic-cs|681|Boostrix Polio", result.vaccineName());
		assertEquals("3/3", result.doseSequence());
		assertEquals(LocalDate.of(2025, 6, 15), result.vaccinationDate());
		assertEquals("TestPharma AG", result.manufacturer());
		assertEquals("LOT12345", result.lotNumber());
		assertEquals("im", result.administrationRoute());
		assertEquals("Left upper arm", result.siteOfAdministration());
		assertEquals("840539006", result.vaccinationReason().code());
		assertEquals("COVID-19", result.vaccinationReason().display());
		assertNotNull(result.practitioner());
		assertEquals("Dr. med. Sarah Müller", result.practitioner().doctorName());
		assertEquals("7601000123456", result.practitioner().gln());
	}

	@Test
	void createImmunizationAdministration_callsFhirClientWithCorrectPatientId() {
		var dto = createDefaultDto();

		service.createImmunizationAdministration("test-patient", dto);

		assertEquals("test-patient", fhirClient.capturedPatientId);
	}

	// ---- helpers ----

	private static Patient createMockPatient() {
		var patient = new Patient();
		patient.setId("patient-123");
		patient.addName().setFamily("Test").addGiven("Patient");
		patient.setGender(Enumerations.AdministrativeGender.MALE);
		var cal = Calendar.getInstance();
		cal.set(1990, Calendar.JANUARY, 15, 0, 0, 0);
		cal.set(Calendar.MILLISECOND, 0);
		patient.setBirthDate(cal.getTime());
		return patient;
	}

	private static ImmunizationCreateDto createDefaultDto() {
		return new ImmunizationCreateDto(//
				"http://fhir.ch/ig/ch-vacd/CodeSystem/ch-vacd-swissmedic-cs|681|Boostrix Polio",//
				"TestPharma AG",//
				"LOT12345",//
				"12345678987654",//
				
				LocalDate.of(2026, 1, 1),
				LocalDate.of(2025, 6, 15), RouteOfAdministration.IM, new 
				AdministeredDose(0.5, "ml"), "Left upper arm",
				new VaccinationReason("840539006", "COVID-19", null), 3, 3, false);
	}

	// ---- manual stub ----

	private static class FhirClientStub implements FhirClient {

		Patient patientToReturn;
		Bundle postedBundle;
		String capturedPatientId;

		@Override
		public Bundle getPatient() {
			return null;
		}

		@Override
		public Patient getPatientById(String id) {
			capturedPatientId = id;
			return patientToReturn;
		}

		@Override
		public Bundle postImmunizationAdministrationBundle(Bundle bundle) {
			postedBundle = bundle;
			var response = new Bundle();
			response.setId("response-123");
			return response;
		}

		@Override
		public Bundle getVaccinationRecord(String id, Parameters parameters) {
			// TODO Auto-generated method stub
			return null;
		}
	}
}
