package ch.hl7.vacd.api.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.projecthusky.fhir.vacd.ch.common.resource.r4.ChVacdImmunizationAdministrationDocument;
import org.slf4j.LoggerFactory;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;

class RessourceUtilTest {

	private FhirContext fhirCtx;
	private Bundle testBundle;

	@BeforeEach
	void setUp() throws Exception {
		fhirCtx = FhirContext.forR4();
		testBundle = fhirCtx.newJsonParser().parseResource(Bundle.class,
				this.getClass().getResourceAsStream("/IA-Bundle.json"));

	}

	@Test
	void testCreateOpenFhirImmunizationAdministrationDocument() {
		ChVacdImmunizationAdministrationDocument ref = RessourceUtil
				.createOpenFhirImmunizationAdministrationDocument(testBundle, fhirCtx);

		assertNotNull(ref);
		LoggerFactory.getLogger(getClass()).info("Converted:\n{}",
				fhirCtx.newJsonParser().setPrettyPrint(true).encodeToString(ref));
	}

	// --- validateOccurrenceDate ---

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);

	private static Immunization immunization(String occurrence) {
		return new Immunization().setOccurrence(new DateTimeType(occurrence));
	}

	private static Patient patient(String birthDate) {
		return new Patient().setBirthDateElement(new DateType(birthDate));
	}

	@Test
	void validateOccurrenceDate_beforeBirth_throws() {
		var ex = assertThrows(UnprocessableEntityException.class, () -> RessourceUtil
				.validateOccurrenceDate(immunization("2020-05-09"), patient("2020-05-10"), TODAY));
		assertTrue(ex.getMessage().contains("must not be before Patient.birthDate"));
	}

	@Test
	void validateOccurrenceDate_onBirthDate_isAccepted() {
		assertDoesNotThrow(() -> RessourceUtil.validateOccurrenceDate(immunization("2020-05-10"),
				patient("2020-05-10"), TODAY));
	}

	@Test
	void validateOccurrenceDate_localMidnightWithOffset_isNotShiftedToPreviousDay() {
		// As produced by bff-producer: local midnight with offset. In UTC this is the 11th.
		assertDoesNotThrow(() -> RessourceUtil.validateOccurrenceDate(
				immunization("2026-10-12T00:00:00+02:00"), patient("2026-10-12"), TODAY));
	}

	@Test
	void validateOccurrenceDate_future_throws() {
		var ex = assertThrows(UnprocessableEntityException.class, () -> RessourceUtil
				.validateOccurrenceDate(immunization("2026-10-13"), patient("2020-05-10"), TODAY));
		assertTrue(ex.getMessage().contains("must not be in the future"));
	}

	@Test
	void validateOccurrenceDate_today_isAccepted() {
		assertDoesNotThrow(() -> RessourceUtil.validateOccurrenceDate(immunization("2026-10-12T15:30:00+02:00"),
				patient("2020-05-10"), TODAY));
	}

	@Test
	void validateOccurrenceDate_partialOccurrenceInBirthMonth_isAccepted() {
		assertDoesNotThrow(() -> RessourceUtil.validateOccurrenceDate(immunization("2009-04"),
				patient("2009-04-15"), TODAY));
		assertThrows(UnprocessableEntityException.class, () -> RessourceUtil
				.validateOccurrenceDate(immunization("2009-03"), patient("2009-04-15"), TODAY));
	}

	@Test
	void validateOccurrenceDate_partialBirthDate_usesEarliestDay() {
		assertDoesNotThrow(() -> RessourceUtil.validateOccurrenceDate(immunization("2009-01-01"),
				patient("2009"), TODAY));
		assertThrows(UnprocessableEntityException.class, () -> RessourceUtil
				.validateOccurrenceDate(immunization("2008-12-31"), patient("2009"), TODAY));
	}

	@Test
	void validateOccurrenceDate_partialOccurrenceInCurrentYear_isNotFuture() {
		assertDoesNotThrow(() -> RessourceUtil.validateOccurrenceDate(immunization("2026"),
				patient("2020-05-10"), TODAY));
	}

	@Test
	void validateOccurrenceDate_noBirthDate_onlyChecksFuture() {
		assertDoesNotThrow(() -> RessourceUtil.validateOccurrenceDate(immunization("1900-01-01"),
				new Patient(), TODAY));
		assertDoesNotThrow(() -> RessourceUtil.validateOccurrenceDate(immunization("1900-01-01"), null, TODAY));
	}

	@Test
	void validateOccurrenceDate_birthDateWithExtensionOnly_isSkipped() {
		var patient = new Patient();
		patient.getBirthDateElement().addExtension("http://hl7.org/fhir/StructureDefinition/data-absent-reason",
				new StringType("unknown"));
		assertDoesNotThrow(() -> RessourceUtil.validateOccurrenceDate(immunization("1900-01-01"), patient, TODAY));
	}

	@Test
	void validateOccurrenceDate_occurrenceStringOrMissing_isSkipped() {
		assertDoesNotThrow(() -> RessourceUtil.validateOccurrenceDate(
				new Immunization().setOccurrence(new StringType("als Kind")), patient("2020-05-10"), TODAY));
		assertDoesNotThrow(() -> RessourceUtil.validateOccurrenceDate(new Immunization(), patient("2020-05-10"),
				TODAY));
	}

	@Test
	void validateOccurrenceDate_exampleBundle_isAccepted() {
		var peeled = RessourceUtil.peel(testBundle);
		for (Immunization imm : peeled.immunizations) {
			assertDoesNotThrow(() -> RessourceUtil.validateOccurrenceDate(imm, peeled.patient, TODAY));
		}
	}

}
