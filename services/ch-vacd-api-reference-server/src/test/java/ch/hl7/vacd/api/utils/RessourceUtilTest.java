package ch.hl7.vacd.api.utils;

import static org.junit.jupiter.api.Assertions.*;

import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.projecthusky.fhir.vacd.ch.common.resource.r4.ChVacdImmunizationAdministrationDocument;
import org.slf4j.LoggerFactory;

import ca.uhn.fhir.context.FhirContext;

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

}
