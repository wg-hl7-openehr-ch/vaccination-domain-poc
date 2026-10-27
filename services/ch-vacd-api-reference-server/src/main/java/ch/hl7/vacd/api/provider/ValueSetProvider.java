package ch.hl7.vacd.api.provider;

import java.util.List;

import org.hl7.fhir.r4.model.Enumerations.PublicationStatus;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.ValueSet;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.param.UriParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ch.hl7.vacd.api.business.ValueSetBusinessService;
import ch.hl7.vacd.api.repo.ResourceRepository;

@Component
public class ValueSetProvider extends AbstractProvider implements IResourceProvider {

	private final ValueSetBusinessService valueSetBusinessService;

	public ValueSetProvider(FhirContext fhirContext, ResourceRepository store,
			ValueSetBusinessService valueSetBusinessService) {
		super(fhirContext);
		this.valueSetBusinessService = valueSetBusinessService;
	}

	@Override
	public Class<ValueSet> getResourceType() {
		return ValueSet.class;
	}

	@Search
	public List<ValueSet> search(@OptionalParam(name = "url") UriParam url) {
		return valueSetBusinessService.searchValueSets(url);
	}

	@Read
	public ValueSet read(@IdParam IdType theId) {
		return valueSetBusinessService.readValueSet(theId);
	}

	@Operation(name = "expand", idempotent = false)
	public ValueSet expand(@IdParam IdType theId, @ResourceParam Parameters parameters) {
		ValueSet valueSet = valueSetBusinessService.expandValueSet(theId, parameters);
		// For now, we just return the ValueSet as is, without any expansion logic.
		
		return valueSet;
	}
}
