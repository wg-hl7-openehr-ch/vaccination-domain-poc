package ch.hl7.vacd.api.business.impl;

import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.ValueSet;
import org.hl7.fhir.r4.model.Enumerations.PublicationStatus;
import org.springframework.stereotype.Service;

import ca.uhn.fhir.context.support.IValidationSupport.ValueSetExpansionOutcome;
import ca.uhn.fhir.context.support.ValidationSupportContext;
import ca.uhn.fhir.context.support.ValueSetExpansionOptions;
import ca.uhn.fhir.rest.param.UriParam;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import ch.hl7.vacd.api.business.ValueSetBusinessService;
import ch.hl7.vacd.api.config.ChVacdNpmPackageValidationSupport;

@Service
public class ValueSetBusinessServiceImpl implements ValueSetBusinessService {

	private final ChVacdNpmPackageValidationSupport npmSupport;

	public ValueSetBusinessServiceImpl(ChVacdNpmPackageValidationSupport npmSupport) {
		this.npmSupport = npmSupport;
	}

	@Override
	public List<ValueSet> searchValueSets(UriParam url) {
		List<ValueSet> valueSets = new ArrayList<>();
		if (url == null) {
			npmSupport.fetchAllConformanceResources().stream()//
					.filter(resource -> resource instanceof ValueSet)//
					.map(resource -> (ValueSet) resource)//
					.forEach(valueSets::add);
		} else {
			IBaseResource valueSet = npmSupport.fetchValueSet(url.getValue());
			if (valueSet != null && valueSet instanceof ValueSet) {
				valueSets.add((ValueSet) valueSet);
			}
		}
		return valueSets; // Return an empty list for now, as ValueSet search is not implemented yet
	}

	@Override
	public ValueSet readValueSet(IdType theId) {
		IBaseResource valueSet = npmSupport.fetchValueSet(theId.getIdPart());
		if (valueSet == null || !(valueSet instanceof ValueSet)) {
			throw new IllegalArgumentException("ValueSet with ID " + theId.getIdPart() + " not found.");
		}
		return (ValueSet) valueSet;
	}

	@Override
	public ValueSet expandValueSet(IdType theId, Parameters parameters) {
		IBaseResource valueSet = npmSupport.fetchValueSet(theId.getIdPart());
		ValidationSupportChain validationSupportChain = new ValidationSupportChain();
		validationSupportChain.addValidationSupport(npmSupport);
		validationSupportChain
				.addValidationSupport(new InMemoryTerminologyServerValidationSupport(npmSupport.getFhirContext()));
		ValidationSupportContext validationSupportContext = new ValidationSupportContext(validationSupportChain);

		ValueSetExpansionOptions valuesetExpansionOptions = new ValueSetExpansionOptions();
		if (parameters.hasParameter("includeDesignations") && //
				parameters.getParameter("includeDesignations").getValue() instanceof BooleanType includeDesignations && //
				includeDesignations.getValue() != null) {
			valuesetExpansionOptions.setIncludeHierarchy(includeDesignations.getValue());
		}

		if (parameters.hasParameter("displayLanguage")) {
			valuesetExpansionOptions
					.setTheDisplayLanguage(parameters.getParameter("displayLanguage").getValue().toString());
		}
		if (parameters.hasParameter("filter")) {
			valuesetExpansionOptions.setFilter(parameters.getParameter("filter").getValue().toString());
		}
		ValueSetExpansionOutcome expandOutcome = validationSupportChain.expandValueSet(validationSupportContext,
				valuesetExpansionOptions, valueSet);
		if (expandOutcome == null || expandOutcome.getValueSet() == null) {
			throw new InvalidRequestException("ValueSet with ID " + theId.getIdPart() + " could not be expanded.");
		}
		ValueSet retVal = (ValueSet) expandOutcome.getValueSet();
		retVal.setStatus(PublicationStatus.ACTIVE);
		retVal.getExpansion().setTimestamp(java.util.Date.from(java.time.Instant.now()));
		retVal.getExpansion().setTotal(retVal.getExpansion().getContains().size());
		parameters.getParameter().forEach(param -> {
			retVal.getExpansion().addParameter().setName(param.getName()).setValue(param.getValue());
		});
		return retVal;
	}

}
