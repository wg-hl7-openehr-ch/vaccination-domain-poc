package ch.hl7.vacd.api.business;

import java.util.List;

import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Immunization;
import org.projecthusky.fhir.vacd.ch.common.resource.r4.ChVacdImmunization;

import ca.uhn.fhir.rest.param.ReferenceParam;

public interface ImmunizationBusinessService {

	Immunization createImmunization(ChVacdImmunization immunization);

	Immunization readImmunization(IdType id);

	/**
	 * Corrects the content of a stored Immunization.
	 *
	 * @param id         the id of the stored Immunization; its version part, if
	 *                   present, is the version the correction is based on
	 * @param resource   the corrected Immunization
	 * @param provenance a Provenance resource (JSON) describing the correction, or
	 *                   null
	 */
	Immunization updateImmunization(IdType id, ChVacdImmunization resource, String provenance);

	List<Immunization> searchImmunizations(ReferenceParam patient);

}
