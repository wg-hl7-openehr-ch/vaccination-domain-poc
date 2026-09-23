package ch.bff.producer.client;

import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.ValueSet;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.TokenParam;

@FeignClient(name = "tx-server", url = "${TX_BASE_URL:https://tx.fhir.ch/r4}")
public interface TxClient {

	/**
	 * Beispiel: https://tx.fhir.ch/r4/ValueSet/$expand
	 * 
	 * { "resourceType" : "Parameters", "parameter" : [ { "name": "url", "valueUri":
	 * "http://fhir.ch/ig/ch-vacd/ValueSet/ch-vacd-vaccines-vs" }, { "name":
	 * "includeDesignations", "valueBoolean": true } ] }
	 * 
	 * @param parameters the parameters to send to the $expand operation
	 * @return the expanded ValueSet
	 */
	@PostMapping("/ValueSet/$expand")
	ValueSet getExpandedValueSet(@RequestBody Parameters parameters);

	/**
	 * Beispiel: POST https://tx.fhir.ch/r4/ConceptMap/$translate { "resourceType" :
	 * "Parameters", "parameter" : [ { "name": "url", "valueUri":
	 * "http://fhir.ch/ig/ch-vacd/ConceptMap/ch-vacd-vaccines-targetdiseases-cm" },
	 * { "name": "sourceCode", "valueCode": "681" }, { "name": "system", "valueUri":
	 * "http://fhir.ch/ig/ch-vacd/CodeSystem/ch-vacd-swissmedic-cs" } ] }
	 * 
	 * @param parameters the parameters to send to the $translate operation
	 * @return the translated target diseases for the given vaccine code
	 */
	@PostMapping("/ConceptMap/$translate")
	Parameters translate(@RequestBody Parameters parameters);

	/**
	 * The POST to $lookup is used to retrieve information about a specific code in a
	 * code system. It takes the coding, display language, and version as parameters
	 * and returns the corresponding Parameters resource.
	 * 
	 * @param parameters the parameters to send to the $lookup operation
	 * @return the Parameters resource containing information about the code
	 */
	@PostMapping("/CodeSystem/$lookup")
	Parameters lookupCode(@RequestBody Parameters parameters);

	/**
	 * The GET to $lookup is used to retrieve information about a specific code in a
	 * code system. It takes the coding, display language, and version as parameters
	 * and returns the corresponding Parameters resource.
	 * 
	 * @param coding          the coding to look up
	 * @param displayLanguage the display language for the code (optional)
	 * @param version
	 * @return the Parameters resource containing information about the code
	 */
	@GetMapping("/CodeSystem/$lookup")
	Parameters lookupCode(@RequestParam("coding") String coding,
			@RequestParam(value = "displayLanguage", required = false) String displayLanguage,
			@RequestParam(value = "version", required = false) String version);
	
	
	@PostMapping("/ValueSet/$validate-code")
	Parameters validateCode(@RequestBody Parameters parameters);
}
