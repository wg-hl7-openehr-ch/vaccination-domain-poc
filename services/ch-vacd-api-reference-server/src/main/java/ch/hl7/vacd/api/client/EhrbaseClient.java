package ch.hl7.vacd.api.client;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import ca.uhn.fhir.rest.server.exceptions.PreconditionFailedException;
import ch.hl7.vacd.api.domain.CompositionVersion;
import ch.hl7.vacd.api.domain.FlatComposition;

public interface EhrbaseClient {


	/**
	 * List all ADL 1.4 templates registered in EHRbase.
	 */
	JsonNode listTemplates();

	/**
	 * Upload an OPT (Operational Template) XML to EHRbase.
	 */
	String uploadOpt(String xml);

	/**
	 * Find an EHR linked to the given FHIR Patient id via
	 * EHR_STATUS.subject.external_ref.
	 */
	String findEhrByPatient(String patientId);

	/**
	 * Create a new EHR with subject linked to the given FHIR Patient id.
	 */
	String createEhr(String patientId);

	/**
	 * Find an existing EHR for the patient, or create one if none exists.
	 */
	String findOrCreateEhr(String patientId);

	/**
	 * POST a FLAT-format Composition to the given EHR. Returns the composition uid.
	 */
	String postCompositionFlat(String ehrId, String flatBody, String templateId);

	String getCompositionFlat(String ehrId, String compositionUid);

	/**
	 * GET the latest version of a Composition in FLAT format.
	 *
	 * @param versionedObjectUid the composition uid without system id and version
	 * @return the FLAT JSON and the version uid it was read from
	 */
	FlatComposition getLatestCompositionFlat(String ehrId, String versionedObjectUid);

	/**
	 * PUT a FLAT-format Composition as the new version of an existing one.
	 *
	 * @param precedingVersionUid the version uid the new version is based on
	 * @return the version uid of the new version
	 * @throws PreconditionFailedException if precedingVersionUid is not the latest
	 *                                     version
	 */
	String updateCompositionFlat(String ehrId, String precedingVersionUid, String flatBody, String templateId);

	/**
	 * Latest version and commit time of one immunization administration
	 * Composition.
	 *
	 * @param versionedObjectUid the composition uid without system id and version
	 * @return the version, or null if the EHR has no such Composition
	 */
	CompositionVersion getCompositionVersion(String ehrId, String versionedObjectUid);

	/**
	 * Latest version and commit time of every immunization administration
	 * Composition of an EHR.
	 *
	 * @return the versions by versioned object uid
	 */
	Map<String, CompositionVersion> getCompositionVersions(String ehrId);

	/**
	 * Execute an AQL query and return the parsed rows. Each row is a JsonNode
	 * array.
	 */
	List<JsonNode> executeAql(String aql);

	/**
	 * Method to retrieve all Immunization resources for a given EHR id. This is
	 * used in the read-path to build the vaccination record document.
	 * 
	 * @param ehrId the EHR id to retrieve the immunizations for
	 * @return a list of Immunization resources in JSON format
	 */
	String getImmunizations(String ehrId);

}