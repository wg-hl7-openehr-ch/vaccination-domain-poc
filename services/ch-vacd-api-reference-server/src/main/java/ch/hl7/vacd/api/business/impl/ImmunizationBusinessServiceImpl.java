package ch.hl7.vacd.api.business.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.projecthusky.fhir.vacd.ch.common.resource.r4.ChVacdImmunization;
import org.projecthusky.fhir.vacd.ch.common.resource.r4.ChVacdImmunizationAdministrationDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ch.hl7.vacd.api.business.ImmunizationBusinessService;
import ch.hl7.vacd.api.client.EhrbaseClient;
import ch.hl7.vacd.api.client.OpenFhirClient;
import ch.hl7.vacd.api.entity.ResourceEntity;
import ch.hl7.vacd.api.entity.ResourceReferenceEntity;
import ch.hl7.vacd.api.repo.ResourceRepository;
import ch.hl7.vacd.api.utils.RessourceUtil;
import jakarta.transaction.Transactional;

@Service
public class ImmunizationBusinessServiceImpl extends AbstractBusinessService implements ImmunizationBusinessService {

	private static final Logger log = LoggerFactory.getLogger(ImmunizationBusinessServiceImpl.class);

	public ImmunizationBusinessServiceImpl(FhirContext fhirContext, ResourceRepository store,
			OpenFhirClient openFhirClient, EhrbaseClient ehrbaseClient) {
		super(fhirContext, store, openFhirClient, ehrbaseClient);
	}

	@Override
	public Immunization createImmunization(ChVacdImmunization immunization) {

		validateImmunization(immunization);

		// Get ehr id
		String patientId = immunization.getPatient().getReferenceElement().getIdPart();

		ResourceEntity parResEnt = store.findByResourceTypeAndResourceId("Patient", patientId).stream().findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Patient not found in local store: " + patientId));

		Patient patient = new Patient();
		patient.setId(patientId);
		if (parResEnt != null) {
			patient = fhirContext.newJsonParser().parseResource(patient.getClass(), parResEnt.getJson());
		}
//		String ehrId = parResEnt.getIdentifiers().stream().filter(id -> "urn:che:epr:ch-vacd:ehr-id".equals(id.getIdSystem())).findFirst()
//				.orElseThrow(() -> new IllegalArgumentException("Patient does not have urn:che:epr:ch-vacd:ehr-id identifier"))
//				.getIdValue();

		String ehrId = ehrbaseClient.findEhrByPatient(patientId);
		log.info("EHR ID for patient {}: {}", patientId, ehrId);

		// Create the immunization resource in the Open FHIR server
		immunization
				.addIdentifier(new Identifier().setSystem("urn:che:epr:ch-vacd:ehr-id").setValue("urn:uuid:" + ehrId));

//		String json = fhirContext.newJsonParser().encodeResourceToString(immunization);

		// Convert FHIR resource to openEHR FLAT format via openFHIR.
		ChVacdImmunizationAdministrationDocument immAdmin = RessourceUtil.createImmunizationAdministrationDocument();
		immAdmin.addImmunization(immunization);
		immAdmin.setPatient(patient);

		/*Bundle retBundle = */processImmunizationAdmnistration(immAdmin, new HashMap<Resource, String>(),
				new ArrayList<>(), Arrays.asList(immunization), ehrId, patientId);

//		String immAdminJson = fhirContext.newJsonParser().encodeResourceToString(immAdmin);
//		String flatJson = openFhirClient.toOpenEhr(immAdminJson);
//		log.info(flatJson);
//
//		// Enrich with feeder_audit (Konkretisierung §13) and composition metadata.
//		String enrichedFlat = FeederAuditEnricher.addOriginal(flatJson, immAdminJson);
//
//		// Split the enriched FLAT JSON by medication_management:X identifier.
//		// Each immunization gets its own complete document with common fields.
//		List<String> splitDocuments = RessourceUtil.splitByMedicationManagement(enrichedFlat);
//
//		log.info("Storing {} split Composition documents for immunization.id={} patientId={} ", splitDocuments.size(),
//				immunization.getId(), patientId);
//
//		List<String> compositionUids = new ArrayList<>();
//		if (splitDocuments.size() == 1) {
//			String splitDoc = splitDocuments.get(0);
//
//			log.info("Split document for immunization index {}:\n{}", 0, splitDoc);
//
//			String compositionUid = ehrbaseClient.postCompositionFlat(ehrId, splitDoc,
//					"ch-vacd-immunization-administration.v1-alpha");
//			compositionUids.add(compositionUid);
//
//			log.info("Stored split Composition[{}] uid={} ehrId={}", 0, compositionUid, ehrId);
//
//			String fhirString = openFhirClient.toFhir(splitDoc);
//
//			Immunization fromEhr = fhirContext.newJsonParser().parseResource(Immunization.class, fhirString);
//
//			// Store the Immunization FHIR resource with compositionUid identifier for later
//			// retrieval.
//			immunization.addIdentifier(
//					new Identifier().setSystem("urn:che:epr:ch-vacd:composition-uid").setValue(compositionUid));
//
//			Map fullUrlMap = new HashMap<>();
//			ResourceEntity entity = createIfAbsent(immunization, fullUrlMap);
//			try {
//				ResourceReferenceEntity refEntity = new ResourceReferenceEntity()//
//						.setTargetType("Patient")//
//						.setTargetId(patientId)//
//						.setSourceEntity(entity)//
//						.setSourceField("Immunization.patient");
//
//				entity.addReference(refEntity);
//
//				store.save(entity);
//			} catch (Exception e) {
//				log.error("Error saving Immunization resource to local store: {}", e.getMessage(), e);
//			}
//
//			String immId = RessourceUtil.extractId(immunization, fullUrlMap);
//			log.info("Stored Immunization id={} with compositionUid={}", immId, compositionUid);
//		}
//
		return immunization;

	}

	private void validateImmunization(ChVacdImmunization immunization) {

		if (immunization.getId() == null || immunization.getId().isEmpty()) {
			immunization.setId(UUID.randomUUID().toString());
		} else {
			immunization.setId(RessourceUtil.removeUrn(immunization.getId()));
		}

		immunization.getMeta().getProfile().stream()
				.filter(profile -> !profile.getValue()
						.equals("http://fhir.ch/ig/ch-vacd/StructureDefinition/ch-vacd-immunization"))
				.findFirst().ifPresent(profile -> {
					throw new IllegalArgumentException(
							"Invalid profile for Immunization resource: " + profile.getValue());
				});

		if (immunization.getPatient() == null || immunization.getPatient().isEmpty()) {
			throw new IllegalArgumentException("Immunization resource must have a patient reference.");
		}
		if (immunization.getPerformerFirstRep() != null && (immunization.getPerformerFirstRep().getActor() == null
				|| immunization.getPerformerFirstRep().getActor().isEmpty())) {
			throw new IllegalArgumentException("Immunization resource must have a performer actor reference.");
		}
		IIdType patRefEle = immunization.getPatient().getReferenceElement();
		checkForReference(patRefEle.getResourceType(), patRefEle.getIdPart());

		IIdType perfRefEle = immunization.getPerformerFirstRep().getActor().getReferenceElement();
		checkForReference(perfRefEle.getResourceType(), perfRefEle.getIdPart());

		if (immunization instanceof ChVacdImmunization) {
			ChVacdImmunization imm = (ChVacdImmunization) immunization;
			if (imm.getRecorder() != null && imm.getRecorder().getReferenceElement() != null) {
				IIdType recRefEle = imm.getRecorder().getReferenceElement();
				checkForReference(recRefEle.getResourceType(), recRefEle.getIdPart());
			}
		}
	}

	@Override
	@Transactional
	public Immunization readImmunization(IdType id) {

		List<ResourceEntity> found = store.findByResourceTypeAndResourceId("Immunization", id.getIdPart());
		if (found != null && !found.isEmpty()) {
			ResourceEntity entity = found.get(0);
			String compositionuid = entity.getIdentifiers().stream()
					.filter(idt -> "urn:che:epr:ch-vacd:composition-uid".equals(idt.getIdSystem())).findFirst()
					.orElseThrow(() -> new IllegalArgumentException(
							"Stored Immunization resource does not have composition-uid identifier"))
					.getIdValue();

			String ehrId = entity.getIdentifiers().stream()
					.filter(idt -> "urn:che:epr:ch-vacd:ehr-id".equals(idt.getIdSystem())).findFirst()
					.orElseThrow(() -> new IllegalArgumentException(
							"Stored Immunization resource does not have ehr-id identifier"))
					.getIdValue();

			String flatImmJson = ehrbaseClient.getCompositionFlat(RessourceUtil.removeUrn(ehrId), compositionuid);
			log.info("Retrieved flat JSON for compositionUid={} ehrId={}:\n{}", compositionuid, ehrId, flatImmJson);

			List<ResourceReferenceEntity> references = entity.getReferences();
			log.info("References for Immunization id={}: {}", id.getIdPart(), references);
			Optional<ResourceReferenceEntity> patientReference = references.stream()
					.filter(ref -> "Immunization.patient".equals(ref.getSourceField())).findFirst();

			String fhirString = openFhirClient.toFhir(flatImmJson);
			log.info("toFhir:\n{}", fhirString);
			Bundle fromEhr = fhirContext.newJsonParser().parseResource(Bundle.class, fhirString);

			Optional<Immunization> immOpt = fromEhr.getEntry().stream()
					.filter(entry -> entry.getResource() instanceof Immunization).findFirst()
					.map(entry -> (Immunization) entry.getResource());

			if (immOpt.isPresent()) {
				Immunization imm = immOpt.get();
				imm.setId(id.getIdPart());

				if (patientReference.isPresent()) {
					imm.setPatient(new Reference().setReference(
							patientReference.get().getTargetType() + "/" + patientReference.get().getTargetId()));
				}

				RessourceUtil.fixProtocolApplied(imm);

				log.info("Immunization:\n{}", fhirContext.newJsonParser().encodeResourceToString(imm));
				return imm;
			} else {
				throw new IllegalStateException(
						"No Immunization resource found in Composition with uid=" + compositionuid);
			}

//			try {
//				log.info(entity.getJson());
//				return (Immunization) fhirContext.newJsonParser().parseResource(entity.getJson());
//			} catch (Exception ex) {
//				// Try to get the FHIR json from the endpoint
//				String fhirJson = openFhirClient.toFhir(entity.getJson());
//				log.info("Parsed FHIR JSON from openFHIR: " + fhirJson);
//				try {
//					return (Immunization) fhirContext.newJsonParser().parseResource(fhirJson);
//				} catch (Exception ex2) {
//					log.error("Failed to parse FHIR JSON for Immunization id={}: {}", id.getIdPart(), ex2.getMessage());
//				}
//			}
		}
		return new Immunization();
	}

	@Override
	public Immunization updateImmunization(ChVacdImmunization resource) {
		String type = resource.fhirType();
		String idPart = resource.getIdPart();
		String json = fhirContext.newJsonParser().encodeResourceToString(resource);
		List<ResourceEntity> found = store.findByResourceTypeAndResourceId(type, idPart);
		ResourceEntity entity;
		if (found != null && !found.isEmpty()) {
			entity = found.get(0);
			entity.setJson(json);
		} else {
			entity = new ResourceEntity();
			entity.setResourceType(type);
			entity.setResourceId(
					idPart == null || idPart.isEmpty()
							? (resource.getIdElement() != null ? resource.getIdElement().getIdPart()
									: java.util.UUID.randomUUID().toString())
							: idPart);
			entity.setJson(json);
		}
		store.save(entity);
		resource.setId(entity.getResourceId());
		return resource;
	}

	@Override
	@Transactional
	public List<Immunization> searchImmunizations(ReferenceParam patient) {
		List<ResourceEntity> stored = store.findByResourceType("Immunization");
		List<Immunization> out = new ArrayList<>();
		for (ResourceEntity e : stored) {

			if (!e.getReferences().stream()
					.filter(ref -> ("Immunization.patient".equals(ref.getSourceField())
							&& "Patient".equals(ref.getTargetType()) && ref.getTargetId().equals(patient.getIdPart())))
					.findFirst().isPresent()) {
				continue;
			}
			String immunizationId = e.getResourceId();
			Immunization imm = readImmunization(new IdType("Immunization/" + immunizationId));
			out.add(imm);

//			var imm = ((Immunization) fhirContext.newJsonParser().parseResource(e.getJson()));
//			imm.setId(e.getResourceId());
//			if (patient == null) {
//				out.add(imm);
//				continue;
//			}
//			if (imm.getPatient() == null || imm.getPatient().getReference() == null
//					|| !imm.getPatient().getReference().equals("urn:uuid:" + patient.getValue())) {
//				log.info("Skipping Immunization id={} due to patient reference mismatch: expected {}, actual {}",
//						imm.getId(), "urn:uuid:" + patient.getValue(),
//						imm.getPatient() != null ? imm.getPatient().getReference() : "null");
//				continue;
//			}
//
//			String patientRef = imm.getPatient().getReference().substring("urn:uuid:".length());
//			Patient p = store.findByResourceTypeAndResourceId("Patient", patientRef).stream().map(pe -> {
//				try {
//					return (Patient) fhirContext.newJsonParser().parseResource(pe.getJson());
//				} catch (Exception ex) {
//					log.error("Failed to parse Patient JSON for reference {}: {}", patientRef, ex.getMessage());
//					return null;
//				}
//			}).filter(parsed -> parsed != null).findFirst().orElse(null);
//
//			String practitionerRef = imm.getPerformer().isEmpty() ? null
//					: imm.getPerformer().get(0).getActor().getReference().substring("urn:uuid:".length());
//			Practitioner practitioner = store.findByResourceTypeAndResourceId("Practitioner", practitionerRef).stream()
//					.map(pe -> {
//						try {
//							return (Practitioner) fhirContext.newJsonParser().parseResource(pe.getJson());
//						} catch (Exception ex) {
//							log.error("Failed to parse Practitioner JSON for reference {}: {}", practitionerRef,
//									ex.getMessage());
//							return null;
//						}
//					}).filter(parsed -> parsed != null).findFirst().orElse(null);
//
//			log.info("Immunization id={} references patient with id={} and practitioner with id={}", imm.getId(),
//					patientRef, practitionerRef);
//			log.info("Patient resource: {}",
//					p != null ? fhirContext.newJsonParser().encodeResourceToString(p) : "null");
//			log.info("Practitioner resource: {}",
//					practitioner != null ? fhirContext.newJsonParser().encodeResourceToString(practitioner) : "null");
//
//			out.add(imm);
		}
		return out;
	}

}
