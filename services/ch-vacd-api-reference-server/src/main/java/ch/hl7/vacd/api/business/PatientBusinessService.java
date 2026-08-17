
package ch.hl7.vacd.api.business;

import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;

import ca.uhn.fhir.rest.param.StringParam;
import ch.hl7.vacd.api.exceptions.PatientNotFoundException;

public interface PatientBusinessService {

	Patient createPatient(Patient patient);

	Patient updatedPatient(Patient patient);

	Patient readPatient(IdType theId);

	List<Patient> searchPatient(StringParam name);

	Bundle exportDocument(IdType theId, Parameters parameters) throws PatientNotFoundException;

}
