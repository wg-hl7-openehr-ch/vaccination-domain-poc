package ch.hl7.vacd.api.provider;

import ca.uhn.fhir.context.FhirContext;

public abstract class AbstractProvider {

	protected final FhirContext fhirContext;

	AbstractProvider(FhirContext fhirContext) {
		this.fhirContext = fhirContext;
	}

}
