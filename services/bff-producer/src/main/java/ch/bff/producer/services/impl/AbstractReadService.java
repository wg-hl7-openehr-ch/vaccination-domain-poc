package ch.bff.producer.services;

import ch.bff.producer.client.FhirClient;

public class AbstractReadService {

	protected final FhirClient fhirClient;

	AbstractReadService(FhirClient fhirClient) {
		this.fhirClient = fhirClient;
	}

}
