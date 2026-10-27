package ch.hl7.vacd.api.provider;

import java.util.List;

import org.hl7.fhir.r4.model.ListResource;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.annotation.RequiredParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ch.hl7.vacd.api.business.PatientBusinessService;

@Component
public class ListProvider extends AbstractProvider implements IResourceProvider {

	public final PatientBusinessService patientBusinessService;

	public ListProvider(FhirContext fhirContext, PatientBusinessService patientBusinessService) {
		super(fhirContext);
		this.patientBusinessService = patientBusinessService;
	}

	@Search
	public List<ListResource> search(@RequiredParam(name = "patient") ReferenceParam patient) {
		return patientBusinessService.getPatientLatestArtefact(patient.getIdPart());
	}

	@Override
	public Class<ListResource> getResourceType() {
		return ListResource.class;
	}
}