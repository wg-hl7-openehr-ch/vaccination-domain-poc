package ch.hl7.vacd.api.provider;

import java.util.List;

import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Immunization;
import org.projecthusky.fhir.vacd.ch.common.resource.r4.ChVacdImmunization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.annotation.Update;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ch.hl7.vacd.api.business.ImmunizationBusinessService;

@Component
public class ImmunizationProvider extends AbstractProvider implements IResourceProvider {

	private ImmunizationBusinessService immunizationBusinessService;

	private static final Logger log = LoggerFactory.getLogger(ImmunizationProvider.class);

	public ImmunizationProvider(FhirContext fhirContext, ImmunizationBusinessService immunizationBusinessService) {
		super(fhirContext);
		this.immunizationBusinessService = immunizationBusinessService;
	}

	@Create
	public MethodOutcome create(@ResourceParam ChVacdImmunization immunization) {

		Immunization retImmunization = immunizationBusinessService.createImmunization(immunization);

		MethodOutcome outcome = new MethodOutcome();
		outcome.setId(new IdType("Immunization", retImmunization.getId()));
		outcome.setResource(retImmunization);
		return outcome;
	}

	@Read
	public Immunization read(@IdParam IdType id) {

		return immunizationBusinessService.readImmunization(id);

	}

	@Update
	public MethodOutcome update(@IdParam IdType id, @ResourceParam ChVacdImmunization resource) {

		Immunization retImmunization = immunizationBusinessService.updateImmunization(resource);

		MethodOutcome outcome = new MethodOutcome();
		outcome.setId(new IdType(retImmunization.fhirType(), retImmunization.getId()));
		outcome.setResource(retImmunization);
		outcome.setCreated(retImmunization == null || retImmunization.isEmpty());
		return outcome;
	}

	@Search
	public List<Immunization> search(@OptionalParam(name = "patient") ReferenceParam patient) {

		return immunizationBusinessService.searchImmunizations(patient);

	}

	@Override
	public Class<Immunization> getResourceType() {
		return Immunization.class;
	}
}