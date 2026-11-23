package ch.bff.producer.mapstruct;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.hl7.fhir.r4.model.Immunization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.projecthusky.fhir.vacd.ch.common.resource.r4.ChVacdImmunization;
import org.projecthusky.fhir.vacd.ch.common.resource.r4.ChVacdVaccinationRecordDocument;

import ca.uhn.fhir.context.FhirContext;
import ch.bff.producer.provider.models.VaccinationDto;

class VaccinationsMapperTest {

	private VaccinationsMapper mapper;
	private Immunization testImmunization;

	@BeforeEach
	void setUp() throws Exception {
		mapper = Mappers.getMapper(VaccinationsMapper.class);
		ChVacdVaccinationRecordDocument bundle = FhirContext.forR4().newJsonParser()
				.parseResource(ChVacdVaccinationRecordDocument.class, this.getClass().getResourceAsStream("/vr.json"));

		testImmunization = bundle.getEntry().stream().filter(e -> e.getResource() instanceof Immunization)
				.map(e -> (Immunization) e.getResource()).findFirst().orElseThrow();
	}

	@Test
	void testToVaccinationDto() {

		VaccinationDto ref = mapper.toVaccinationDto(testImmunization);

		assertNotNull(ref);

		assertEquals("Sarah Müller", ref.practitioner().doctorName());
		assertEquals("7601000123456", ref.practitioner().gln());
		assertEquals(null, ref.manufacturer());
	}

	@Test
	void testToVaccinationDto_singleImmunizationReadFromServer() {
		Immunization read = FhirContext.forR4().newJsonParser().parseResource(Immunization.class,
				this.getClass().getResourceAsStream("/immunization-read.json"));

		VaccinationDto dto = mapper.toVaccinationDto(read);

		assertEquals("0df77a69-3ab1-46de-8d15-3fbe050696ae", dto.id());
		assertEquals("8", dto.version());
		assertEquals(Instant.parse("2026-11-17T14:00:59.889Z"), dto.lastUpdated());
		assertTrue(dto.corrected());

		// what the form needs to prefill
		assertEquals("Boostrix Polio", dto.vaccineName());
		assertEquals("681", dto.vaccineCode());
		assertEquals("http://fhir.ch/ig/ch-vacd/CodeSystem/ch-vacd-swissmedic-cs", dto.vaccineSystem());
		assertEquals(0.5, dto.administeredDose().value(), 0.001);
		assertEquals("ml", dto.administeredDose().unit());
		assertEquals(2, dto.doseNumber());
		assertNull(dto.seriesDoses());
		assertEquals("GlaxoSmithKline", dto.manufacturer());
		assertEquals("A1B2C3", dto.lotNumber());

		// the server does not resolve the performer of a single Immunization
		assertNull(dto.practitioner());
	}

	@Test
	void testToVaccinationDto_firstVersionIsNotCorrected() {
		testImmunization.getMeta().setVersionId("1");

		assertFalse(mapper.toVaccinationDto(testImmunization).corrected());
	}

	@Test
	void testToVaccinationDto_withoutMeta_hasNoVersion() {
		testImmunization.setMeta(null);

		VaccinationDto dto = mapper.toVaccinationDto(testImmunization);

		assertNull(dto.version());
		assertNull(dto.lastUpdated());
		assertFalse(dto.corrected());
	}

}
