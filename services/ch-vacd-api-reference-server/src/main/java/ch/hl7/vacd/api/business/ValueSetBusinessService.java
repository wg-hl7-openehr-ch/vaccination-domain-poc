package ch.hl7.vacd.api.business;

import java.util.List;

import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.ValueSet;

import ca.uhn.fhir.rest.param.UriParam;

public interface ValueSetBusinessService {


	List<ValueSet> searchValueSets(UriParam url);

	ValueSet readValueSet(IdType theId);

	ValueSet expandValueSet(IdType theId, Parameters parameters);

}
