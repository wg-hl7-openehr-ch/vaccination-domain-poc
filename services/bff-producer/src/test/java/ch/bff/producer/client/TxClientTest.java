package ch.bff.producer.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;

import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.UriType;
import org.hl7.fhir.r4.model.ValueSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.test.context.ActiveProfiles;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.TokenParam;

@SpringBootTest(properties = { "TX_BASE_URL=https://swisshds.u.c.bag.admin.ch/terminology/fhir" })
@ActiveProfiles(value = "test")
class TxClientTest {

	private Logger logger = LoggerFactory.getLogger(TxClientTest.class);

	@Autowired
	private TxClient txClient;

	@BeforeEach
	void setUp() throws Exception {
	}
	
	@Test
	void testGetExpandedValueSetVersion() throws FileNotFoundException, DataFormatException, IOException {
		Parameters parameters = new Parameters();
		parameters.addParameter().setName("url")
				.setValue(new org.hl7.fhir.r4.model.UriType("http://fhir.ch/ig/ch-vacd/ValueSet/ch-vacd-vaccines-vs"));
		parameters.addParameter().setName("valueSetVersion").setValue(new StringType("3.3.0"));
	

		ValueSet valueSet = txClient.getExpandedValueSet(parameters);
		assertNotNull(valueSet);
		new FileOutputStream("target/expanded-vs_"+UUID.randomUUID().toString()+".json").write(FhirContext.forR4().newJsonParser().setPrettyPrint(true).encodeResourceToString(valueSet).getBytes());
	}

	@Test
	void testGetExpandedValueSetVersionIncludeDesignations() throws FileNotFoundException, DataFormatException, IOException {
		Parameters parameters = new Parameters();
		parameters.addParameter().setName("url")
				.setValue(new org.hl7.fhir.r4.model.UriType("http://fhir.ch/ig/ch-vacd/ValueSet/ch-vacd-vaccines-vs"));
		parameters.addParameter().setName("valueSetVersion").setValue(new StringType("3.3.0"));
		parameters.addParameter().setName("includeDesignations").setValue(new org.hl7.fhir.r4.model.BooleanType(true));
		parameters.addParameter().setName("displayLanguage").setValue(new CodeType("fr-CH"));

		ValueSet valueSet = txClient.getExpandedValueSet(parameters);
		assertNotNull(valueSet);
	
		new FileOutputStream("target/expanded-vs_"+UUID.randomUUID().toString()+".json").write(FhirContext.forR4().newJsonParser().setPrettyPrint(true).encodeResourceToString(valueSet).getBytes());
	}
	
	@Test
	void testGetExpandedValueSetVersionNotExist() throws FileNotFoundException, DataFormatException, IOException {
		Parameters parameters = new Parameters();
		parameters.addParameter().setName("url")
				.setValue(new org.hl7.fhir.r4.model.UriType("http://fhir.ch/ig/ch-vacd/ValueSet/ch-vacd-vaccines-vs-2"));
		parameters.addParameter().setName("valueSetVersion").setValue(new StringType("3.3.0"));
	

		ValueSet valueSet = txClient.getExpandedValueSet(parameters);
		assertNotNull(valueSet);
		
	}

	@Test
	void testTranslateToTargetDesease() {

		Parameters diseaseParameters = new Parameters();
		diseaseParameters.addParameter().setName("url")
				.setValue(new UriType("http://fhir.ch/ig/ch-vacd/ConceptMap/ch-vacd-vaccines-targetdiseases-cm"));

		diseaseParameters.addParameter().setName("code").setValue(new CodeType("681"));
		diseaseParameters.addParameter().setName("system")
				.setValue(new UriType("http://fhir.ch/ig/ch-vacd/CodeSystem/ch-vacd-swissmedic-cs"));
//		diseaseParameters.addParameter().setName("source").setValue(new UriType("http://fhir.ch/ig/ch-vacd/ValueSet/ch-vacd-vaccines-vs"));
//		diseaseParameters.addParameter().setName("target").setValue(new UriType("http://fhir.ch/ig/ch-vacd/ValueSet/ch-vacd-targetdiseasesandillnessesundergoneforimmunization-vs"));

		Parameters targetDiseaseParameters = txClient.translate(diseaseParameters);
		assertNotNull(targetDiseaseParameters);
		assertTrue(targetDiseaseParameters.hasParameter("result"));
		assertTrue(targetDiseaseParameters.hasParameter("message"));
		assertEquals(targetDiseaseParameters.getParameter("message").getValue().toString(), "No Matches found");

	}

	@Test
	void testTranslateToSnomeCt() {
		//$translate?url={cm-url}&source={src-valueset}&code={src-code}&system={src-system}&target={tgt-valueset}
		Parameters diseaseParameters = new Parameters();
		diseaseParameters.addParameter().setName("url")
				.setValue(new UriType("http://fhir.ch/ig/ch-vacd/ConceptMap/ch-vacd-vaccines-sm-sct-cm"));

		diseaseParameters.addParameter().setName("code").setValue(new CodeType("681"));
		diseaseParameters.addParameter().setName("system")
				.setValue(new UriType("http://fhir.ch/ig/ch-vacd/CodeSystem/ch-vacd-swissmedic-cs"));
		diseaseParameters.addParameter().setName("source").setValue(new UriType("http://fhir.ch/ig/ch-vacd/ValueSet/ch-vacd-vaccines-vs"));
		diseaseParameters.addParameter().setName("target").setValue(new UriType("http://fhir.ch/ig/ch-vacd/ValueSet/ch-vacd-vaccines-snomedct-vs"));
		

		Parameters targetDiseaseParameters = txClient.translate(diseaseParameters);
		assertNotNull(targetDiseaseParameters);
		assertTrue(targetDiseaseParameters.hasParameter("result"));
		assertTrue(targetDiseaseParameters.hasParameter("message"));
		assertEquals(targetDiseaseParameters.getParameter("message").getValue().toString(), "Matches found");

	}
	
	@Test
	void testTranslateToSnomeCt1() {
		//$translate?url={cm-url}&source={src-valueset}&code={src-code}&system={src-system}&target={tgt-valueset}
		Parameters diseaseParameters = new Parameters();
		diseaseParameters.addParameter().setName("url")
				.setValue(new UriType("http://fhir.ch/ig/ch-vacd/ConceptMap/ch-vacd-vaccines-sm-sct-cm"));

		diseaseParameters.addParameter().setName("code").setValue(new CodeType("62961"));
		diseaseParameters.addParameter().setName("system")
				.setValue(new UriType("http://fhir.ch/ig/ch-vacd/CodeSystem/ch-vacd-swissmedic-cs"));
		diseaseParameters.addParameter().setName("source").setValue(new UriType("http://fhir.ch/ig/ch-vacd/ValueSet/ch-vacd-vaccines-vs"));
		diseaseParameters.addParameter().setName("target").setValue(new UriType("http://fhir.ch/ig/ch-vacd/ValueSet/ch-vacd-vaccines-snomedct-vs"));
		diseaseParameters.addParameter().setName("targetsystem").setValue(new UriType("http://snomed.info/sct"));

		Parameters targetDiseaseParameters = txClient.translate(diseaseParameters);
		assertNotNull(targetDiseaseParameters);
		assertTrue(targetDiseaseParameters.hasParameter("result"));
		assertTrue(targetDiseaseParameters.hasParameter("message"));
		assertEquals(targetDiseaseParameters.getParameter("message").getValue().toString(), "Matches found");

	}
	

	@Test
	void testLookupCode() {
//		fail("Not yet implemented");
		Parameters lookupParam = new Parameters();
		// lookup coding
		Coding coding = new Coding()//
				.setSystem("http://fhir.ch/ig/ch-vacd/CodeSystem/ch-vacd-swissmedic-cs").setCode("681");
		lookupParam.addParameter().setName("coding").setValue(coding);

//		logger.info("Lookup params: {}",
//				FhirContext.forR4().newJsonParser().setPrettyPrint(true).encodeResourceToString(lookupParam));
		Parameters lookup = txClient.lookupCode(lookupParam);

//		logger.info("Lookup result: {}",
//				FhirContext.forR4().newJsonParser().setPrettyPrint(true).encodeResourceToString(lookup));
		assertNotNull(lookup);
		assertTrue(lookup.hasParameter("display"));

	}

	@Test
	void testlookupCodeGET() {
		Parameters lookup = txClient
				.lookupCode("http://fhir.ch/ig/ch-vacd/CodeSystem/ch-vacd-swissmedic-cs" + "|" + "681", null, null);

		assertNotNull(lookup);
		assertTrue(lookup.hasParameter("display"));
	}

	@Test
	void testLookupCodeAndDisplayLanguage() {
//		fail("Not yet implemented");
		Parameters lookupParam = new Parameters();
		// lookup coding
		Coding coding = new Coding()//
				.setSystem("http://fhir.ch/ig/ch-vacd/CodeSystem/ch-vacd-swissmedic-cs").setCode("681");
		lookupParam.addParameter().setName("coding").setValue(coding);

		lookupParam.addParameter().setName("displayLanguage").setValue(new CodeType("de-CH"));

//		logger.info("Lookup params: {}",
//				FhirContext.forR4().newJsonParser().setPrettyPrint(true).encodeResourceToString(lookupParam));
		Parameters lookup = txClient.lookupCode(lookupParam);

//		logger.info("Lookup result: {}",
//				FhirContext.forR4().newJsonParser().setPrettyPrint(true).encodeResourceToString(lookup));
		assertNotNull(lookup);
		assertTrue(lookup.hasParameter("display"));

	}

	@Test
	void testLookupCodeAndDisplayLanguageGET() {
		Parameters lookup = txClient.lookupCode("http://fhir.ch/ig/ch-vacd/CodeSystem/ch-vacd-swissmedic-cs" + "|" + "681", "de-CH", null);
		assertNotNull(lookup);
		assertTrue(lookup.hasParameter("display"));

	}

	@Test
	void testLookupCodeAndDisplayLanguageAndVersion() {
//		fail("Not yet implemented");
		Parameters lookupParam = new Parameters();
		// lookup coding
		Coding coding = new Coding()//
				.setSystem("http://fhir.ch/ig/ch-vacd/CodeSystem/ch-vacd-swissmedic-cs").setCode("681");
		lookupParam.addParameter().setName("coding").setValue(coding);

		lookupParam.addParameter().setName("displayLanguage").setValue(new CodeType("de-CH"));

		lookupParam.addParameter().setName("version").setValue(new StringType("3.2.0"));

//		logger.info("Lookup params: {}",
//				FhirContext.forR4().newJsonParser().setPrettyPrint(true).encodeResourceToString(lookupParam));
		Parameters lookup = txClient.lookupCode(lookupParam);

//		logger.info("Lookup result: {}",
//				FhirContext.forR4().newJsonParser().setPrettyPrint(true).encodeResourceToString(lookup));
		assertNotNull(lookup);
		assertTrue(lookup.hasParameter("display"));

	}

	@Test
	void testLookupCodeAndDisplayLanguageAndVersionGET() {

		Parameters lookup = txClient.lookupCode("http://fhir.ch/ig/ch-vacd/CodeSystem/ch-vacd-swissmedic-cs" + "|" + "681", "de-CH", "3.2.0");
		assertNotNull(lookup);
		assertTrue(lookup.hasParameter("display"));

	}
	
	@Test
	void testLookupCodeNotExisting() {
		Parameters lookupParam = new Parameters();
		Coding coding = new Coding()//
				.setSystem("http://fhir.ch/ig/ch-vacd/CodeSystem/ch-vacd-swissmedic-cs").setCode("99999");
		lookupParam.addParameter().setName("coding").setValue(coding);
		Parameters lookup = txClient.lookupCode(lookupParam);
		assertNotNull(lookup);
		assertTrue(lookup.hasParameter("display"));

	}
	
	@Test
	void testLookupCodeNotExistingGET() {
		Parameters lookup = txClient
				.lookupCode("http://fhir.ch/ig/ch-vacd/CodeSystem/ch-vacd-swissmedic-cs" + "|" + "99999", "de-CH", "3.4.0");

		assertNotNull(lookup);
		assertTrue(lookup.hasParameter("display"));
	}
	
	@Test
	void testValidateCode1() {
		// GET [base]/ValueSet/$validate-code?url={vs-url}&system={cs-url}&code={valid-code}
		Parameters validateParam = new Parameters();
		validateParam.addParameter().setName("url")
				.setValue(new UriType("http://fhir.ch/ig/ch-vacd/ValueSet/ch-vacd-vaccines-vs"));
		validateParam.addParameter().setName("code").setValue(new CodeType("681"));
		validateParam.addParameter().setName("system")
				.setValue(new UriType("http://fhir.ch/ig/ch-vacd/CodeSystem/ch-vacd-swissmedic-cs"));

		Parameters validate = txClient.validateCode(validateParam);
		assertNotNull(validate);
		assertTrue(validate.hasParameter("result"));
		assertTrue(validate.getParameter("result").getValue() instanceof org.hl7.fhir.r4.model.BooleanType);
		assertTrue(((org.hl7.fhir.r4.model.BooleanType) validate.getParameter("result").getValue()).booleanValue());
	}
	

	@Test
	void testValidateCodeAndDisplay() {
		// GET [base]/ValueSet/$validate-code?url={vs-url}&system={cs-url}&code={valid-code}
		Parameters validateParam = new Parameters();
		validateParam.addParameter().setName("url")
				.setValue(new UriType("http://fhir.ch/ig/ch-vacd/ValueSet/ch-vacd-vaccines-vs"));
		validateParam.addParameter().setName("code").setValue(new CodeType("65387"));
		validateParam.addParameter().setName("system")
				.setValue(new UriType("http://fhir.ch/ig/ch-vacd/CodeSystem/ch-vacd-swissmedic-cs"));
		validateParam.addParameter().setName("display").setValue(new StringType("Gardasil 9"));

		Parameters validate = txClient.validateCode(validateParam);
		assertNotNull(validate);
		assertTrue(validate.hasParameter("result"));
		assertTrue(validate.getParameter("result").getValue() instanceof org.hl7.fhir.r4.model.BooleanType);
		assertTrue(((org.hl7.fhir.r4.model.BooleanType) validate.getParameter("result").getValue()).booleanValue());
	}
	
	@Test
	void testValidateCodeAndDisplayNotOK() {
		// GET [base]/ValueSet/$validate-code?url={vs-url}&system={cs-url}&code={valid-code}
		Parameters validateParam = new Parameters();
		validateParam.addParameter().setName("url")
				.setValue(new UriType("http://fhir.ch/ig/ch-vacd/ValueSet/ch-vacd-vaccines-vs"));
		validateParam.addParameter().setName("code").setValue(new CodeType("65387"));
		validateParam.addParameter().setName("system")
				.setValue(new UriType("http://fhir.ch/ig/ch-vacd/CodeSystem/ch-vacd-swissmedic-cs"));
		validateParam.addParameter().setName("display").setValue(new StringType("Something 9"));

		Parameters validate = txClient.validateCode(validateParam);
		assertNotNull(validate);
		assertTrue(validate.hasParameter("result"));
		assertTrue(validate.getParameter("result").getValue() instanceof org.hl7.fhir.r4.model.BooleanType);
		assertTrue(((org.hl7.fhir.r4.model.BooleanType) validate.getParameter("result").getValue()).booleanValue());
	}
	
	@Test
	void testValidateCodeNotKnown() {
		// GET [base]/ValueSet/$validate-code?url={vs-url}&system={cs-url}&code={valid-code}
		Parameters validateParam = new Parameters();
		validateParam.addParameter().setName("url")
				.setValue(new UriType("http://fhir.ch/ig/ch-vacd/ValueSet/ch-vacd-vaccines-vs"));
		validateParam.addParameter().setName("code").setValue(new CodeType("99999"));
		validateParam.addParameter().setName("system")
				.setValue(new UriType("http://fhir.ch/ig/ch-vacd/CodeSystem/ch-vacd-swissmedic-cs"));

		Parameters validate = txClient.validateCode(validateParam);
		assertNotNull(validate);
		assertTrue(validate.hasParameter("result"));
		assertTrue(validate.getParameter("result").getValue() instanceof org.hl7.fhir.r4.model.BooleanType);
		assertTrue(((org.hl7.fhir.r4.model.BooleanType) validate.getParameter("result").getValue()).booleanValue());
	}
	

}
