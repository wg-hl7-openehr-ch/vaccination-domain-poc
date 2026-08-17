package ch.hl7.vacd.api.business.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.projecthusky.fhir.vacd.ch.common.resource.r4.ChVacdAbstractDocument;
import org.projecthusky.fhir.vacd.ch.common.resource.r4.ChVacdImmunization;
import org.projecthusky.fhir.vacd.ch.common.resource.r4.ChVacdImmunizationAdministrationDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import ch.hl7.vacd.api.client.EhrbaseClient;
import ch.hl7.vacd.api.client.FeederAuditEnricher;
import ch.hl7.vacd.api.client.OpenFhirClient;
import ch.hl7.vacd.api.entity.ResourceEntity;
import ch.hl7.vacd.api.entity.ResourceIdentifierEntity;
import ch.hl7.vacd.api.entity.ResourceReferenceEntity;
import ch.hl7.vacd.api.openehr.ChVacdOpenEhrConstants;
import ch.hl7.vacd.api.repo.ResourceRepository;
import ch.hl7.vacd.api.utils.RessourceUtil;

public class AbstractBusinessService {

	private static final Logger log = LoggerFactory.getLogger(AbstractBusinessService.class);

	protected final FhirContext fhirContext;
	protected final ResourceRepository store;
	protected final EhrbaseClient ehrbaseClient;
	protected final OpenFhirClient openFhirClient;

	public AbstractBusinessService(FhirContext fhirContext, ResourceRepository store, OpenFhirClient openFhirClient,
			EhrbaseClient ehrbaseClient) {
		this.fhirContext = fhirContext;
		this.store = store;
		this.openFhirClient = openFhirClient;
		this.ehrbaseClient = ehrbaseClient;

	}

	protected ResourceEntity createIfAbsent(PractitionerRole practitionerRole, Map<Resource, String> fullUrlMap) {
		ResourceEntity entity = createIfAbsent((Resource) practitionerRole, fullUrlMap);

		try {
			ResourceReferenceEntity refEntity1 = new ResourceReferenceEntity()//
					.setTargetType("Practitioner")//
					.setTargetId(RessourceUtil
							.removeUrn(practitionerRole.getPractitioner().getReferenceElement().getIdPart()))//
					.setSourceEntity(entity)//
					.setSourceField("PractitionerRole.practitioner");
			entity.addReference(refEntity1);

			ResourceReferenceEntity refEntity2 = new ResourceReferenceEntity()//
					.setTargetType("Organization")//
					.setTargetId(RessourceUtil
							.removeUrn(practitionerRole.getOrganization().getReferenceElement().getIdPart()))//
					.setSourceEntity(entity)//
					.setSourceField("PractitionerRole.organization");
			entity.addReference(refEntity2);

			store.save(entity);
		} catch (Exception e) {
			log.error("Error saving Immunization resource to local store: {}", e.getMessage(), e);
		}
		return entity;
	}

	protected ResourceEntity createIfAbsent(Immunization immunization, Map<Resource, String> fullUrlMap) {
		ResourceEntity entity = createIfAbsent((Resource) immunization, fullUrlMap);
		try {
			ResourceReferenceEntity refEntity = new ResourceReferenceEntity()//
					.setTargetType("Patient")//
					.setTargetId(RessourceUtil.removeUrn(immunization.getPatient().getReferenceElement().getIdPart()))//
					.setSourceEntity(entity)//
					.setSourceField("Immunization.patient");

			entity.addReference(refEntity);

			store.save(entity);
		} catch (Exception e) {
			log.error("Error saving Immunization resource to local store: {}", e.getMessage(), e);
		}
		return entity;
	}

	// --- CreateIfAbsent ---
	protected ResourceEntity createIfAbsent(Resource resource, Map<Resource, String> fullUrlMap) {
		ResourceEntity retVal = new ResourceEntity();
		String resourceType = resource.fhirType();
		String resourceId = RessourceUtil.extractId(resource, fullUrlMap);
		resource.setId(resourceId);
		List<ResourceEntity> existing = store.findByResourceTypeAndResourceId(resourceType,
				RessourceUtil.removeUrn(resourceId));
		if (existing == null || existing.isEmpty()) {
			ResourceEntity entity = new ResourceEntity();
			entity.setResourceType(resourceType);
			entity.setResourceId(RessourceUtil.removeUrn(resourceId));
			entity.setJson(fhirContext.newJsonParser().encodeResourceToString(resource));

			RessourceUtil.getIdentifiers(resource).forEach(identifier -> {
				entity.addIdentifier(new ResourceIdentifierEntity()//
						.setIdSystem(identifier.getSystem())//
						.setIdValue(identifier.getValue())//
						.setIdUse((identifier.getUse() != null) ? identifier.getUse().toCode() : null)//
						.setResourceEntity(entity));
			});

			retVal = store.save(entity);
			log.info("Created absent {} id={}", resourceType, resourceId);
		} else {
			log.info("Found existing {} id={}", resourceType, resourceId);
			retVal = existing.get(0);
		}
		return retVal;
	}

	protected void checkForReference(String resourceType, String idPart) {
		List<ResourceEntity> existing = store.findByResourceTypeAndResourceId(resourceType, idPart);
		if (existing == null || existing.isEmpty()) {
			throw new ResourceNotFoundException(
					String.format("Referenced resource %s/%s not found", resourceType, idPart));
		}
	}

	protected Bundle processImmunizationAdmnistration(Bundle bundle, Map<Resource, String> fullUrlMap,
			List<String> compositionUids2, List<Immunization> immunizations, String ehrId, String patientId) {

		ChVacdImmunizationAdministrationDocument chvacdToEHR = RessourceUtil
				.createOpenFhirImmunizationAdministrationDocument(bundle, fhirContext);

//		String bundleJson = fhirContext.newJsonParser().encodeResourceToString(bundle);
		String bundleJson = fhirContext.newJsonParser().encodeResourceToString(chvacdToEHR);
		log.info("New Bundle\n{}", bundleJson);

		// Convert FHIR Bundle to openEHR FLAT format via openFHIR.
		String flatJson = openFhirClient.toOpenEhr(bundleJson);
		log.info("Flat Json from openFHIR:\n{}", flatJson);

		// Enrich with feeder_audit (Konkretisierung §13) and composition metadata.
		String enrichedFlat = FeederAuditEnricher.addOriginal(flatJson, bundleJson);
		log.info("Enriched Flat Json from openFHIR:\n{}", enrichedFlat);

		// Split the enriched FLAT JSON by medication_management:X identifier.
		// Each immunization gets its own complete document with common fields.
		List<String> splitDocuments = RessourceUtil.splitByMedicationManagement(enrichedFlat);

		// Persist each split immunization document separately.
		List<String> compositionUids = new ArrayList<>();

		for (int i = 0; i < splitDocuments.size(); i++) {
			String splitDoc = splitDocuments.get(i);

			log.info("Split document for immunization index {}:\n{}", i, splitDoc);

			String compositionUid = ehrbaseClient.postCompositionFlat(ehrId, splitDoc,
					ChVacdOpenEhrConstants.ADMIN_TEMPLATE);
			compositionUids.add(compositionUid);
			log.info("Stored split Composition[{}] uid={} ehrId={}", i, compositionUid, ehrId);

			// Store the Immunization FHIR resource with compositionUid identifier for later
			// retrieval.
			if (i < immunizations.size()) {
				Immunization imm = immunizations.get(i);
				imm.addIdentifier(
						new Identifier().setSystem("urn:che:epr:ch-vacd:composition-uid").setValue(compositionUid));

				createIfAbsent(imm, fullUrlMap);

				String immId = RessourceUtil.extractId(imm, fullUrlMap);
				log.info("Stored Immunization id={} with compositionUid={}", immId, compositionUid);
			}
		}

		return bundle;

	}

	@SuppressWarnings("unchecked")
	protected <T extends Resource> T getResourceEntry(String resourceType, String resourceId) {
		List<ResourceEntity> entities = store.findByResourceTypeAndResourceId(resourceType, resourceId);
		if (entities != null && !entities.isEmpty()) {
			ResourceEntity entity = entities.get(0);
			try {
				return (T) fhirContext.newJsonParser().parseResource(entity.getJson());
			} catch (Exception e) {
				log.warn("Failed to parse stored {} id={}: {}", resourceType, entity.getResourceId(), e.getMessage());
			}
		}
		return null;
	}

	protected ChVacdImmunization copyImmunization(Immunization immunization, Patient patient,
			ChVacdAbstractDocument document) {

		// fill up the recorder
		// TODO: recorder

		// fill up perfomer actor
		List<String> perfomerIds = immunization.getPerformer().stream()//
				.filter(f -> (f.getActor() != null && f.getActor().getIdentifier() != null
						&& f.getActor().getIdentifier().getValue() != null))//
				.map(f -> RessourceUtil.removeUrn(f.getActor().getIdentifier().getValue()))//
				.collect(Collectors.toList());

		List<String> perfomerIds2 = immunization.getPerformer().stream()//
				.filter(f -> (f.getActor() != null && f.getActor().getReference() != null))//
				.map(f -> RessourceUtil.removeUrn(f.getActor().getReferenceElement().getIdPart()))//
				.collect(Collectors.toList());
		perfomerIds.addAll(perfomerIds2);

		immunization.getPerformer().clear();

		log.info("Performer IDs for immunization {}: {}", immunization.getId(), perfomerIds);
		ChVacdImmunization immun = new ChVacdImmunization();
		immunization.copyValues(immun);

		for (String performerId : perfomerIds) {
			if (performerId == null) {
				continue;
			}
			IdType idType = new IdType(performerId);
			log.info("Performer reference for id:\n{}: {} {}", performerId, idType.getResourceType(),
					idType.getIdPart());

			DomainResource perfomerDR = getResourceEntry(
					(idType.getResourceType() != null) ? idType.getResourceType() : "PractitionerRole",
					idType.getIdPart());
			if (perfomerDR != null && perfomerDR instanceof Practitioner) {
				Practitioner perfomer = (Practitioner) perfomerDR;
//				Practitioner practitioner = getResourceEntry("Practitioner",
//						RessourceUtil.removeUrn(perfomer.getIdPart()));
				if (checkEntryAbsent(document, perfomer)) {
					document.addEntry().setResource(perfomer).setFullUrl("urn:uuid:" + perfomer.getIdPart());
					perfomer.setIdElement(null);
				}
				immun.addPerformer().setActor(new Reference(perfomer));

			}
			// complete practitionerrole with reference to practitioner and organization
			else if (perfomerDR != null && perfomerDR instanceof PractitionerRole) {
				PractitionerRole perfomer = (PractitionerRole) perfomerDR;
				Practitioner practitioner = getResourceEntry("Practitioner",
						RessourceUtil.removeUrn(perfomer.getPractitioner().getReferenceElement().getIdPart()));
				if (checkEntryAbsent(document, practitioner)) {
					document.addEntry().setResource(practitioner).setFullUrl("urn:uuid:" + practitioner.getIdPart());
					practitioner.setIdElement(null);
				}
				perfomer.setPractitioner(new Reference(practitioner));

				Organization organization = getResourceEntry("Organization",
						RessourceUtil.removeUrn(perfomer.getOrganization().getReferenceElement().getIdPart()));
				if (checkEntryAbsent(document, organization)) {
					document.addEntry().setResource(organization).setFullUrl("urn:uuid:" + organization.getIdPart());
					organization.setIdElement(null);
				}
				perfomer.setOrganization(new Reference(organization));

				if (checkEntryAbsent(document, perfomer)) {
					document.addEntry().setResource(perfomer).setFullUrl("urn:uuid:" + perfomer.getIdPart());
					perfomer.setIdElement(null);
				}
				perfomer.setIdElement(null);
				immun.addPerformer().setActor(new Reference(perfomer));

			}
		}

		// fix the protocol applied reference to immunization
		RessourceUtil.fixProtocolApplied(immun);

		// set the patient reference to the immunization
		immun.setPatient(new Reference(patient));
		return immun;
	}

	private boolean checkEntryAbsent(ChVacdAbstractDocument document, Resource resource) {
		return !document.getEntry().stream()//
				.filter(e -> e.getResource().fhirType().equals(resource.fhirType())
						&& e.getFullUrl().equals("urn:uuid:" + resource.getIdElement().getIdPart()))//
				.findFirst()//
				.isPresent();
	}

}
