/**
 * Author: Roeland Luykx
 * 
 * Copyright (c) 2026+ by RALY GmbH
 */

package ch.hl7.vacd.api.business.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier.IdentifierUse;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.projecthusky.fhir.vacd.ch.common.resource.r4.ChVacdImmunization;
import org.projecthusky.fhir.vacd.ch.common.resource.r4.ChVacdVaccinationRecordDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.param.StringParam;
import ch.hl7.vacd.api.business.PatientBusinessService;
import ch.hl7.vacd.api.client.EhrbaseClient;
import ch.hl7.vacd.api.client.OpenFhirClient;
import ch.hl7.vacd.api.entity.ResourceEntity;
import ch.hl7.vacd.api.exceptions.PatientNotFoundException;
import ch.hl7.vacd.api.openehr.ChVacdOpenEhrConstants;
import ch.hl7.vacd.api.repo.ResourceRepository;
import ch.hl7.vacd.api.utils.RessourceUtil;

/**
 * 	
 */
@Service
public class PatientBusinessServiceImpl extends AbstractBusinessService implements PatientBusinessService {

	private static final Logger log = LoggerFactory.getLogger(PatientBusinessServiceImpl.class);

	public PatientBusinessServiceImpl(FhirContext fhirContext, ResourceRepository store, OpenFhirClient openFhirClient,
			EhrbaseClient ehrbaseClient) {
		super(fhirContext, store, openFhirClient, ehrbaseClient);
	}

	@Override
	public Patient createPatient(Patient patient) {
		String type = patient.fhirType();
		// String json = fhirContext.newJsonParser().encodeResourceToString(patient);
		String id = patient.getIdElement() != null && patient.getIdElement().hasIdPart()
				? patient.getIdElement().getIdPart()
				: UUID.randomUUID().toString();
		patient.setId(type + "/" + id);
		String ehrId = ehrbaseClient.findOrCreateEhr(id);
		patient.addIdentifier().setSystem("urn:che:epr:ch-vacd:ehr-id").setValue("urn:uuid:" + ehrId)
				.setUse(IdentifierUse.SECONDARY);
		// String json = fhirContext.newJsonParser().encodeResourceToString(patient);

		createIfAbsent(patient, new HashMap<>());

		return patient;
	}

	@Override
	public Patient updatedPatient(Patient patient) {
		String type = patient.fhirType();
		String idPart = patient.getIdPart();
		String ehrId = ehrbaseClient.findOrCreateEhr(idPart);
		patient.addIdentifier().setSystem("urn:che:epr:ch-vacd:ehr-id").setValue("urn:uuid:" + ehrId).setUse(IdentifierUse.SECONDARY);
		String json = fhirContext.newJsonParser().encodeResourceToString(patient);
		List<ResourceEntity> found = store.findByResourceTypeAndResourceId(type, idPart);
		ResourceEntity entity;
		if (found != null && !found.isEmpty()) {
			entity = found.get(0);
			entity.setJson(json);
			store.save(entity);
		} else {
			createIfAbsent(patient, new HashMap<>());
		}

		return patient;

	}

	@Override
	public Patient readPatient(IdType theId) {
		List<ResourceEntity> found = store.findByResourceTypeAndResourceId("Patient", theId.getIdPart());
		if (found != null && !found.isEmpty()) {
			IBaseResource r = (IBaseResource) fhirContext.newJsonParser().parseResource(found.get(0).getJson());
			if (r != null)
				r.setId(theId.getIdPart());
			r.getMeta().addProfile("http://fhir.ch/ig/ch-core/StructureDefinition/ch-core-patient-epr");
			return (Patient) r;
		}
		Patient p = new Patient();
		p.getMeta().addProfile("http://fhir.ch/ig/ch-core/StructureDefinition/ch-core-patient-epr");
		p.setId(theId.getIdPart());
		p.addName().setFamily("Test").addGiven("Patient");
		return p;
	}

	@Override
	public List<Patient> searchPatient(StringParam name) {
		List<ResourceEntity> stored = store.findByResourceType("Patient").stream().filter(e -> {
			if (name == null || name.isEmpty())
				return true;
			try {
				IBaseResource r = fhirContext.newJsonParser().parseResource(e.getJson());
				if (r instanceof Patient) {
					Patient p = (Patient) r;
					return p.getName().stream().anyMatch(n -> n.getFamily().equalsIgnoreCase(name.getValue())
							|| n.getGiven().stream().anyMatch(g -> g.getValue().equalsIgnoreCase(name.getValue())));
				}
			} catch (Exception ex) {
				return false;
			}
			return false;
		}).toList();
		List<Patient> out = new ArrayList<>();
		for (ResourceEntity e : stored) {
			var pat = (Patient) fhirContext.newJsonParser().parseResource(e.getJson());
			if (pat.hasName()) {
				out.add(pat);
			}
		}
		return out;
	}

	@Override
	public Bundle exportDocument(IdType thePatientId, Parameters parameters) throws PatientNotFoundException {

		ChVacdVaccinationRecordDocument chVaccinationRecordDocument = RessourceUtil.createVaccinationRecordDocument();

		Patient patient = readPatient(thePatientId);
		chVaccinationRecordDocument.setPatient(patient);

		patient.setIdElement(null);

		String purePatientId = thePatientId.getIdPart();
		String ehrId = ehrbaseClient.findEhrByPatient(purePatientId);
		if (ehrId == null) {
			log.error("No EHR found for patientId: {}", thePatientId.getIdPart());
			throw new PatientNotFoundException("No EHR found for patientId: " + thePatientId.getIdPart());
		}

		log.info("Building vaccination record for patientId={} (ehrId={})", thePatientId.getIdPart(), ehrId);

		String immJson = ehrbaseClient.getImmunizations(ehrId);
		log.info("openEHR {}:\n{}", ehrId, immJson);
		if (immJson == null || immJson.isEmpty()) {
//			throw new PatientNotFoundException("No immunizations found for patientId: " + thePatientId.getIdPart());
			return chVaccinationRecordDocument;
		}
		String fhirJson = openFhirClient.toFhir(immJson, ChVacdOpenEhrConstants.VACC_TEMPLATE);
		log.info("Converted FHIR JSON:\n{}", fhirJson);
		Bundle bundle = (Bundle) fhirContext.newJsonParser().parseResource(fhirJson);
		List<Immunization> immEntries = bundle.getEntry().stream().filter(e -> e.getResource() instanceof Immunization)
				.map(e -> (Immunization) e.getResource()).collect(Collectors.toList());
		log.info("Bundle contains {} Immunization entries", immEntries.size());

		for (Immunization immunization : immEntries) {
			log.info("Immunization resource: id={}, status={}, vaccineCode={}", immunization.getId(),
					immunization.getStatus(), immunization.getVaccineCode().getCodingFirstRep().getCode());

			ChVacdImmunization immun = copyImmunization(immunization, patient, chVaccinationRecordDocument);
			chVaccinationRecordDocument.addImmunization(immun);
		}

		log.info("VaccinationRecord:\n{}",
				fhirContext.newJsonParser().encodeResourceToString(chVaccinationRecordDocument));
		return chVaccinationRecordDocument;

	}

}
