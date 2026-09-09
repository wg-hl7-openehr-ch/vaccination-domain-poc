/**
 * Author: Roeland Luykx
 * 
 * Copyright (c) 2026+ by RALY GmbH
 */

package ch.hl7.vacd.api.business.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.Resource;
import org.projecthusky.fhir.vacd.ch.common.resource.r4.ChVacdImmunization;
import org.projecthusky.fhir.vacd.ch.common.resource.r4.ChVacdImmunizationAdministrationDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ca.uhn.fhir.context.FhirContext;
import ch.hl7.vacd.api.business.BundleBusinessService;
import ch.hl7.vacd.api.client.EhrbaseClient;
import ch.hl7.vacd.api.client.OpenFhirClient;
import ch.hl7.vacd.api.domain.Peeled;
import ch.hl7.vacd.api.entity.ResourceEntity;
import ch.hl7.vacd.api.exceptions.PatientNotFoundException;
import ch.hl7.vacd.api.repo.ResourceRepository;
import ch.hl7.vacd.api.utils.RessourceUtil;
import jakarta.transaction.Transactional;

/**
 * 	
 */
@Service
public class BundleBusinessServiceImpl extends AbstractBusinessService implements BundleBusinessService {

	private static final Logger log = LoggerFactory.getLogger(BundleBusinessServiceImpl.class);

	public BundleBusinessServiceImpl(FhirContext fhirContext, ResourceRepository store, OpenFhirClient openFhirClient,
			EhrbaseClient ehrbaseClient) {
		super(fhirContext, store, openFhirClient, ehrbaseClient);
//		this.ehrbaseClient = ehrbaseClient;
//		this.openFhirClient = openFhirClient;
	}

	@Override
	@Transactional
	public Bundle createBundle(Bundle bundle) throws PatientNotFoundException {
		log.info("Creating Bundle:\n{}",
				fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle));

		// Validate and extract bundle structure.
		Peeled peeled = RessourceUtil.peel(bundle);

		// Validate each Immunization status.
		for (Immunization immunization : peeled.immunizations) {
			RessourceUtil.validateStatus(immunization);
		}

		Map<Resource, String> fullUrlMap = RessourceUtil.buildFullUrlMap(bundle);

		// Extract IDs.
		String patientId = RessourceUtil.extractId(peeled.patient, fullUrlMap);
		String ehrId = ehrbaseClient.findEhrByPatient(patientId);

		if (ehrId == null) {
			log.error("No EHR found for patientId: {}", patientId);
			throw new PatientNotFoundException("No EHR found for patientId: " + patientId);
		}

		log.info("Found ehrId: {} for patientId: {}", ehrId, patientId);

		// CreateIfAbsent for Practitioners, Organizations, and PractitionerRoles.

		for (Practitioner practitioner : peeled.practitioners) {
			createIfAbsent(practitioner, fullUrlMap);
		}
		for (Organization organization : peeled.organizations) {
			createIfAbsent(organization, fullUrlMap);
		}
		for (PractitionerRole practitionerRole : peeled.practitionerRoles) {
			/* ResourceEntity praRoleEntry = */ createIfAbsent(practitionerRole, fullUrlMap);
		}

//		patientId = RessourceUtil.removeUrn(patientId);
		List<String> practitionerIds = peeled.practitioners.stream().map(p -> RessourceUtil.extractId(p, fullUrlMap))
				.collect(Collectors.toList());
		List<String> organizationIds = peeled.organizations.stream().map(o -> RessourceUtil.extractId(o, fullUrlMap))
				.collect(Collectors.toList());

		// Assign Bundle ID and persist to the FHIR store.
		String type = bundle.fhirType();
		String id = bundle.getIdElement() != null && bundle.getIdElement().hasIdPart()
				? bundle.getIdElement().getIdPart()
				: UUID.randomUUID().toString();
		bundle.setId(type + "/" + id);

		for (Immunization immunization : peeled.immunizations) {
			immunization.addIdentifier(new Identifier()//
					.setSystem("urn:che:epr:ch-vacd:ehr-id")//
					.setValue("urn:uuid:" + ehrId)//
					.setUse(Identifier.IdentifierUse.SECONDARY));
		}

		List<String> compositionUids = new ArrayList<>();

		log.info("FHIR server accepted Bundle, id={}", id);
		Bundle bundleFromEhr = processImmunizationAdmnistration(bundle, fullUrlMap, compositionUids,
				peeled.immunizations, ehrId, patientId);

		log.info(
				"Completed ingestion: bundleId={} patientId={} practitioners={} organizations={} immunizations={} compositions={}",
				id, patientId, practitionerIds, organizationIds, peeled.immunizations.size(), compositionUids.size());

		log.info("Processed bundle:\n{}",
				fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundleFromEhr));

		// create a new bundle to return, containing only the
		// ImmunizationAdministrationDocument resources
		ChVacdImmunizationAdministrationDocument bundleOut = RessourceUtil.createImmunizationAdministrationDocument();
		Patient patientOut = peeled.patient.copy();
		patientOut.setId(RessourceUtil.removeUrn(patientOut.getId()));
		bundleOut.setPatient(patientOut);
		patientOut.setIdElement(null);
		bundleOut.setIdentifier(bundle.getIdentifier().copy());

//		for (PractitionerRole practitionerRole : peeled.practitionerRoles) {
//			PractitionerRole practitionerRoleOut = practitionerRole.copy();
//			bundleOut.addEntry().setResource(practitionerRoleOut)
//					.setFullUrl("urn:uuid:" + RessourceUtil.removeUrn(practitionerRoleOut.getId()));
//		}
//
//		for (Practitioner practitioner : peeled.practitioners) {
//			Practitioner practitionerOut = practitioner.copy();
//
//			bundleOut.addEntry().setResource(practitionerOut)
//					.setFullUrl("urn:uuid:" + RessourceUtil.removeUrn(practitioner.getId()));
//
//		}
//		for (Organization organization : peeled.organizations) {
//			Organization organizationOut = organization.copy();
//			bundleOut.addEntry().setResource(organizationOut)
//					.setFullUrl("urn:uuid:" + RessourceUtil.removeUrn(organizationOut.getId()));
//		}
		List<Immunization> immEntries = bundleFromEhr.getEntry().stream()
				.filter(e -> e.getResource() instanceof Immunization).map(e -> (Immunization) e.getResource())
				.collect(Collectors.toList());
		for (Immunization immunization : immEntries) {
			log.info("Immunization resource: id={}, status={}, vaccineCode={}", immunization.getId(),
					immunization.getStatus(), immunization.getVaccineCode().getCodingFirstRep().getCode());

			ChVacdImmunization immun = copyImmunization(immunization, patientOut, bundleOut);
			bundleOut.addImmunization(immun);
		}

		log.info("Out bundle:\n{}", fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundleOut));
		return bundleOut;

	}

	@Override
	public Bundle readBundle(IdType id) {
		List<ResourceEntity> found = store.findByResourceTypeAndResourceId("Bundle", id.getIdPart());
		if (found != null && !found.isEmpty()) {
			IBaseResource r = fhirContext.newJsonParser().parseResource(found.get(0).getJson());
			if (r instanceof Bundle)
				return (Bundle) r;
		}
		return null;
	}

	@Override
	public List<Bundle> searchBundles() {
		List<ResourceEntity> entities = store.findByResourceType("Bundle");
		List<Bundle> out = new ArrayList<>();
		for (ResourceEntity e : entities) {
			IBaseResource r = fhirContext.newJsonParser().parseResource(e.getJson());
			if (r instanceof Bundle)
				out.add((Bundle) r);
		}
		return out;
	}

}
