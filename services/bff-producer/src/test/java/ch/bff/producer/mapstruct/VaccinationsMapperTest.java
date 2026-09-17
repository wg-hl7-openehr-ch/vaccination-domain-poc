package ch.bff.producer.mapstruct;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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

}
