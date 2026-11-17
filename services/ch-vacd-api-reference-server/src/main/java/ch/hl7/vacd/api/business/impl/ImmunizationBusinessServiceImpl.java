package ch.hl7.vacd.api.business.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Provenance;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.projecthusky.fhir.vacd.ch.common.resource.r4.ChVacdImmunization;
import org.projecthusky.fhir.vacd.ch.common.resource.r4.ChVacdImmunizationAdministrationDocument;
import org.projecthusky.fhir.vacd.ch.common.resource.r4.ChVacdMedicationForImmunization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.PreconditionFailedException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import ch.hl7.vacd.api.business.ImmunizationBusinessService;
import ch.hl7.vacd.api.client.EhrbaseClient;
import ch.hl7.vacd.api.client.OpenFhirClient;
import ch.hl7.vacd.api.domain.CompositionVersion;
import ch.hl7.vacd.api.domain.FlatComposition;
import ch.hl7.vacd.api.entity.ArtefactEntity;
import ch.hl7.vacd.api.entity.ArtefactEntityType;
import ch.hl7.vacd.api.entity.ResourceEntity;
import ch.hl7.vacd.api.entity.ResourceReferenceEntity;
import ch.hl7.vacd.api.openehr.ChVacdOpenEhrConstants;
import ch.hl7.vacd.api.repo.ArtefactRepository;
import ch.hl7.vacd.api.repo.ResourceRepository;
import ch.hl7.vacd.api.utils.FlatDiff;
import ch.hl7.vacd.api.utils.RessourceUtil;
import jakarta.transaction.Transactional;

@Service
public class ImmunizationBusinessServiceImpl extends AbstractBusinessService implements ImmunizationBusinessService {

	private static final Logger log = LoggerFactory.getLogger(ImmunizationBusinessServiceImpl.class);

	public ImmunizationBusinessServiceImpl(FhirContext fhirContext, ResourceRepository store,
			ArtefactRepository artefactRepository, OpenFhirClient openFhirClient, EhrbaseClient ehrbaseClient) {
		super(fhirContext, store, artefactRepository, openFhirClient, ehrbaseClient);
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

		RessourceUtil.validateOccurrenceDate(immunization, patient);

		String ehrId = ehrbaseClient.findEhrByPatient(patientId);
		log.info("EHR ID for patient {}: {}", patientId, ehrId);

		// Create the immunization resource in the Open FHIR server
		immunization.addIdentifier(new Identifier()//
				.setSystem("urn:che:epr:ch-vacd:ehr-id")//
				.setValue("urn:uuid:" + ehrId)//
				.setUse(Identifier.IdentifierUse.SECONDARY));

		ChVacdMedicationForImmunization medication = immunization.getMedication();
		if(medication != null) {
			medication.addIdentifier(new Identifier()//
					.setSystem("urn:che:epr:ch-vacd:ehr-id")//
					.setValue("urn:uuid:" + ehrId)//
					.setUse(Identifier.IdentifierUse.SECONDARY));
		}

		// Convert FHIR resource to openEHR FLAT format via openFHIR.
		ChVacdImmunizationAdministrationDocument immAdmin = RessourceUtil.createImmunizationAdministrationDocument();
		immAdmin.addImmunization(immunization);
		immAdmin.setPatient(patient);

		String sessionId = UUID.randomUUID().toString();
		logArtefact(sessionId, patientId, ArtefactEntityType.INBUNDLE,
				fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(immAdmin));

		/* Bundle retBundle = */processImmunizationAdmnistration(immAdmin, new HashMap<Resource, String>(),
				new ArrayList<>(), Arrays.asList(immunization), Arrays.asList(medication), ehrId, patientId, sessionId);

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
			return loadCurrent(found.get(0)).immunization();

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

	/**
	 * The current state of a stored Immunization: the latest version of its
	 * Composition in the EHR and the Immunization read from it.
	 */
	private record Current(String ehrId, String versionUid, String flat, Immunization immunization) {
	}

	/**
	 * Loads the latest version of the Composition of a stored Immunization from the
	 * EHR and converts it to FHIR. The stored composition-uid may carry the version
	 * the Immunization was created with; only its versioned object uid is used.
	 */
	private Current loadCurrent(ResourceEntity entity) {
		String compositionuid = entity.getIdentifiers().stream()
				.filter(idt -> "urn:che:epr:ch-vacd:composition-uid".equals(idt.getIdSystem())).findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"Stored Immunization resource does not have composition-uid identifier"))
				.getIdValue();

		String ehrId = RessourceUtil.removeUrn(entity.getIdentifiers().stream()
				.filter(idt -> "urn:che:epr:ch-vacd:ehr-id".equals(idt.getIdSystem())).findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"Stored Immunization resource does not have ehr-id identifier"))
				.getIdValue());

		String versionedObjectUid = CompositionVersion.versionedObjectUid(compositionuid);
		FlatComposition composition = ehrbaseClient.getLatestCompositionFlat(ehrId, versionedObjectUid);
		String flatImmJson = composition.flat();
		log.info("Retrieved flat JSON for compositionUid={} ehrId={}:\n{}", composition.versionUid(), ehrId,
				flatImmJson);

		List<ResourceReferenceEntity> references = entity.getReferences();
		log.info("References for Immunization id={}: {}", entity.getResourceId(), references);
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
			imm.setId(entity.getResourceId());

			// HAPI builds the ETag and Last-Modified headers from these.
			CompositionVersion version = ehrbaseClient.getCompositionVersion(ehrId, versionedObjectUid);
			imm.getMeta().setVersionId(CompositionVersion.versionNumber(composition.versionUid()));
			if (version != null && composition.versionUid().equals(version.versionUid())) {
				imm.getMeta().setLastUpdated(version.timeCommitted());
			}

			if (patientReference.isPresent()) {
				imm.setPatient(new Reference().setReference(
						patientReference.get().getTargetType() + "/" + patientReference.get().getTargetId()));
			}

			RessourceUtil.fixProtocolApplied(imm);

			log.info("Immunization:\n{}", fhirContext.newJsonParser().encodeResourceToString(imm));
			return new Current(ehrId, composition.versionUid(), flatImmJson, imm);
		} else {
			throw new IllegalStateException(
					"No Immunization resource found in Composition with uid=" + composition.versionUid());
		}
	}

	/**
	 * Corrects the content of a stored Immunization by writing a new version of its
	 * Composition. Only the keys that differ between the current and the corrected
	 * Immunization are written, see {@link FlatDiff}.
	 */
	@Override
	@Transactional
	public Immunization updateImmunization(IdType id, ChVacdImmunization resource, String provenance) {

		List<ResourceEntity> found = store.findByResourceTypeAndResourceId("Immunization", id.getIdPart());
		if (found == null || found.isEmpty()) {
			throw new ResourceNotFoundException(id);
		}
		ResourceEntity entity = found.get(0);
		if (resource.getIdElement().hasIdPart() && !id.getIdPart().equals(resource.getIdElement().getIdPart())) {
			throw new UnprocessableEntityException("Immunization.id (" + resource.getIdElement().getIdPart()
					+ ") must match the id in the URL (" + id.getIdPart() + ")");
		}
		Provenance provenanceResource = parseProvenance(provenance);

		Current current = loadCurrent(entity);
		String currentVersion = CompositionVersion.versionNumber(current.versionUid());
		if (id.hasVersionIdPart() && !id.getVersionIdPart().equals(currentVersion)) {
			throw new PreconditionFailedException("Immunization/" + id.getIdPart() + " is at version "
					+ currentVersion + ", not at version " + id.getVersionIdPart());
		}

		String patientId = current.immunization().getPatient().getReferenceElement().getIdPart();
		Patient patient = getResourceEntry("Patient", patientId);
		if (patient == null) {
			throw new IllegalStateException("Patient not found in local store: " + patientId);
		}
		validateCorrection(current.immunization(), resource, patient);

		FlatDiff.Diff diff = FlatDiff.between(toFlat(current.immunization(), patient), toFlat(resource, patient));
		if (diff.isEmpty()) {
			log.info("Immunization id={} is unchanged, no new version", id.getIdPart());
			return current.immunization();
		}
		log.info("Correcting Immunization id={}: changed={} removed={}", id.getIdPart(), diff.changed().keySet(),
				diff.removed());

		String versionUid = ehrbaseClient.updateCompositionFlat(current.ehrId(), current.versionUid(),
				FlatDiff.apply(current.flat(), diff), ChVacdOpenEhrConstants.ADMIN_TEMPLATE);
		log.info("Stored Composition uid={} for Immunization id={}", versionUid, id.getIdPart());

		// identifiers and references of the entity stay as they are
		entity.setJson(fhirContext.newJsonParser().encodeResourceToString(resource));
		store.save(entity);
		if (provenanceResource != null) {
			storeProvenance(provenanceResource, id.getIdPart(), CompositionVersion.versionNumber(versionUid));
		}

		return loadCurrent(entity).immunization();
	}

	/**
	 * The rules a correction has to meet. This is the one place for them.
	 *
	 * @param current   the Immunization as it is stored
	 * @param corrected the Immunization as it should be
	 * @param patient   the patient of the stored Immunization
	 */
	private void validateCorrection(Immunization current, Immunization corrected, Patient patient) {
		String currentPatientId = current.getPatient().getReferenceElement().getIdPart();
		String correctedPatientId = RessourceUtil.removeUrn(corrected.getPatient().getReferenceElement().getIdPart());
		if (!currentPatientId.equals(correctedPatientId)) {
			throw new UnprocessableEntityException("Immunization.patient must not be changed by a correction");
		}

		// the export finds the stored Immunization by these
		if (!identifiers(current).equals(identifiers(corrected))) {
			throw new UnprocessableEntityException("Immunization.identifier must not be changed by a correction");
		}

		if (corrected.getStatus() != Immunization.ImmunizationStatus.COMPLETED) {
			throw new UnprocessableEntityException("Immunization.status must stay 'completed' in a correction");
		}
		RessourceUtil.validateOccurrenceDate(corrected, patient);

		// The vaccine comes with its target diseases and its product (Medication),
		// which a correction does not carry.
		if (!current.getVaccineCode().equalsDeep(corrected.getVaccineCode())
				|| !targetDiseases(current).equals(targetDiseases(corrected))) {
			throw new UnprocessableEntityException(
					"Immunization.vaccineCode and protocolApplied.targetDisease cannot be corrected");
		}
	}

	private static Set<String> identifiers(Immunization immunization) {
		return immunization.getIdentifier().stream().map(idt -> idt.getSystem() + "|" + idt.getValue())
				.collect(Collectors.toSet());
	}

	private static Set<String> targetDiseases(Immunization immunization) {
		return immunization.getProtocolApplied().stream().flatMap(pa -> pa.getTargetDisease().stream())
				.flatMap(td -> td.getCoding().stream()).map(c -> c.getSystem() + "|" + c.getCode())
				.collect(Collectors.toSet());
	}

	private String toFlat(Immunization immunization, Patient patient) {
		// work on a copy: the document resolves the patient from the reference
		ChVacdImmunization imm = fhirContext.newJsonParser().parseResource(ChVacdImmunization.class,
				fhirContext.newJsonParser().encodeResourceToString(immunization));
		imm.setPatient(new Reference(patient));

		ChVacdImmunizationAdministrationDocument immAdmin = RessourceUtil.createImmunizationAdministrationDocument();
		immAdmin.addImmunization(imm);
		immAdmin.setPatient(patient);

		ChVacdImmunizationAdministrationDocument chvacdToEHR = RessourceUtil
				.createOpenFhirImmunizationAdministrationDocument(immAdmin, fhirContext);
		String flatJson = openFhirClient.toOpenEhr(fhirContext.newJsonParser().encodeResourceToString(chvacdToEHR));

		List<String> splitDocuments = RessourceUtil.splitByMedicationManagement(flatJson);
		if (splitDocuments.size() != 1) {
			throw new IllegalStateException(
					"Expected one Composition for one Immunization, got " + splitDocuments.size());
		}
		return splitDocuments.get(0);
	}

	private Provenance parseProvenance(String provenance) {
		if (provenance == null || provenance.isBlank()) {
			return null;
		}
		try {
			return fhirContext.newJsonParser().parseResource(Provenance.class, provenance);
		} catch (DataFormatException e) {
			throw new InvalidRequestException("X-Provenance must be a Provenance resource in JSON: " + e.getMessage());
		}
	}

	private void storeProvenance(Provenance provenance, String immunizationId, String version) {
		provenance.setId(UUID.randomUUID().toString());
		provenance.setTarget(List.of(new Reference("Immunization/" + immunizationId + "/_history/" + version)));
		if (!provenance.hasRecorded()) {
			provenance.setRecorded(new Date());
		}

		ResourceEntity entity = new ResourceEntity();
		entity.setResourceType(provenance.fhirType());
		entity.setResourceId(provenance.getIdElement().getIdPart());
		// keep the version in the target reference; the parser strips it by default
		entity.setJson(fhirContext.newJsonParser().setStripVersionsFromReferences(false)
				.encodeResourceToString(provenance));
		entity.addReference(new ResourceReferenceEntity()
				.setTargetType("Immunization")
				.setTargetId(immunizationId)
				.setSourceEntity(entity)
				.setSourceField("Provenance.target"));
		store.save(entity);
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
