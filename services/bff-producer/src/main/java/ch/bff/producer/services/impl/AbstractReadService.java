package ch.bff.producer.services.impl;

import ch.bff.producer.client.FhirClient;

public class AbstractReadService {

	protected final FhirClient fhirClient;

	protected AbstractReadService(FhirClient fhirClient) {
		this.fhirClient = fhirClient;
	}

}
