package ch.hl7.vacd.api.provider;

import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.annotation.Update;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ch.hl7.vacd.api.business.PatientBusinessService;
import ch.hl7.vacd.api.exceptions.PatientNotFoundException;

@Component
public class PatientProvider extends AbstractProvider implements IResourceProvider {

	public final PatientBusinessService patientBusinessService;

	public PatientProvider(FhirContext fhirContext, PatientBusinessService patientBusinessService) {
		super(fhirContext);
		this.patientBusinessService = patientBusinessService;
	}

	@Create
	public MethodOutcome create(@ResourceParam Patient patient) {

		validatePatient(patient);

		Patient createdPatient = patientBusinessService.createPatient(patient);

		MethodOutcome outcome = new MethodOutcome();
		outcome.setId(new IdType("Patient", createdPatient.getId()));
		outcome.setResource(patient);
		return outcome;
	}

	@Update
	public MethodOutcome update(@IdParam IdType id, @ResourceParam Patient patient) {

		validatePatient(patient);

		Patient updatedPatient = patientBusinessService.updatedPatient(patient);

		MethodOutcome outcome = new MethodOutcome();
		outcome.setId(new IdType(updatedPatient.fhirType(), updatedPatient.getId()));
		outcome.setResource(updatedPatient);
		outcome.setCreated(updatedPatient == null || updatedPatient.isEmpty());
		return outcome;
	}

	@Read
	public Patient read(@IdParam IdType theId) {
		return patientBusinessService.readPatient(theId);
	}

	@Search
	public List<Patient> search(@OptionalParam(name = "name") StringParam name) {
		return patientBusinessService.searchPatient(name);
	}

	@Operation(name = "export-document", idempotent = false)
	public Bundle exportDocument(@IdParam IdType theId, @ResourceParam Parameters parameters)
			throws PatientNotFoundException {
		if ((parameters.getParameter("type") != null) && //
				(parameters.getParameter("type").getValue() instanceof Coding) && //
				("urn:oid:2.16.756.5.30.1.127.3.10.10".equals(//
						((Coding) parameters.getParameter("type").getValue()).getSystem()))
				&& //
				("urn:che:epr:ch-vacd:vaccination-record:2022".equals(//
						((Coding) parameters.getParameter("type").getValue()).getCode()))//
		) {

			// In a real implementation, you would retrieve the patient and related
			// resources based on the provided ID
			return patientBusinessService.exportDocument(theId, parameters);
		} else {
			throw new IllegalArgumentException("Unsupported type code!");
		}
	}

	@Override
	public Class<Patient> getResourceType() {
		return Patient.class;
	}

	private void validatePatient(Patient patient) {
		if (patient.getName().isEmpty()) {
			throw new InvalidRequestException("Patient name is required");
		}
		if (patient.getBirthDate() == null) {
			throw new InvalidRequestException("Patient birth date is required");
		}
		if (patient.getGender() == null) {
			throw new InvalidRequestException("Patient gender is required");
		}
		if (!patient.hasActive()) {
			throw new InvalidRequestException("Patient active status is required");
		}

	}
}