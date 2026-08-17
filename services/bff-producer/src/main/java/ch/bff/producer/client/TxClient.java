package ch.bff.producer.client;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent;
import org.hl7.fhir.r4.model.ValueSet;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

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
	Parameters getTargetDiseasesForVaccine(@RequestBody Parameters parameters);

	
	@PostMapping("/CodeSystem/$lookup")
	Parameters lookupCode(@RequestBody Parameters parameters);
}
