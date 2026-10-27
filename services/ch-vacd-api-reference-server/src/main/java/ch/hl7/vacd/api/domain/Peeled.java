package ch.hl7.vacd.api.domain;

import java.util.List;

import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;

public class Peeled {
	public final Composition composition;
	public final List<Immunization> immunizations;
	public final Patient patient;
	public final List<Practitioner> practitioners;
	public final List<Organization> organizations;
	public final List<Location> locations;
	public final List<PractitionerRole> practitionerRoles;
	public final List<Medication> medications;

	public Peeled(Composition composition, List<Immunization> immunizations, Patient patient,
			List<Practitioner> practitioners, List<Organization> organizations, List<Location> locations,
			List<PractitionerRole> practitionerRoles, List<Medication> medications) {
		this.composition = composition;
		this.immunizations = immunizations;
		this.patient = patient;
		this.practitioners = practitioners;
		this.organizations = organizations;
		this.locations = locations;
		this.practitionerRoles = practitionerRoles;
		this.medications = medications;
	}
}