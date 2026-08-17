package ch.bff.producer.services;

import java.util.Date;
import java.util.UUID;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.PositiveIntType;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.SimpleQuantity;
import org.projecthusky.fhir.core.ch.resource.r4.ChCoreOrganizationEpr;
import org.projecthusky.fhir.core.ch.resource.r4.ChCorePractitionerEpr;
import org.projecthusky.fhir.core.ch.resource.r4.ChCorePractitionerRoleEpr;
import org.projecthusky.fhir.vacd.ch.common.resource.r4.ChVacdImmunizationAdministrationDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ca.uhn.fhir.context.FhirContext;
import ch.bff.producer.client.FhirClient;
import ch.bff.producer.provider.models.ImmunizationCreateDto;
import ch.bff.producer.provider.models.PractitionerDto;
import ch.bff.producer.provider.models.RouteOfAdministration;
import ch.bff.producer.provider.models.VaccinationDto;

@Service
public class ImmunizationAdministrationService {

	private static final Logger log = LoggerFactory.getLogger(ImmunizationAdministrationService.class);

	private final FhirClient fhirClient;
	private final FhirContext fhirContext;

	public ImmunizationAdministrationService(FhirClient fhirClient) {
		this.fhirClient = fhirClient;
		this.fhirContext = FhirContext.forR4();
	}

	public VaccinationDto createImmunizationAdministration(String patientIamId, ImmunizationCreateDto createDto) {

		log.info("Creating Immunization Administration for patient IAM ID: {}\n{}", patientIamId, createDto);

		ChVacdImmunizationAdministrationDocument bundle = new ChVacdImmunizationAdministrationDocument();
		// 1. Real patient from FHIR
		var fhirPatient = fhirClient.getPatientById(patientIamId);
		// var patient = copyPatient(fhirPatient, patientUuid);
		bundle.setPatient(fhirPatient);

		var practitioner = buildPractitioner(null);
		var organization = buildOrganization(null);
		var practitionerRole = buildPractitionerRole(practitioner, organization);
		bundle.addAuthor(practitionerRole, new Date());
//		var composition = buildComposition(compositionUuid, patientUuid, practitionerRoleUuid, immunizationUuid);

		// 2. Build FHIR resources
		var immunization = buildImmunization(bundle, createDto, fhirPatient, practitionerRole);
		var immunizationUuid = UUID.fromString(immunization.getIdElement().getIdPart());
//        var immunization = buildImmunization(createDto, immunizationUuid, patientUuid, practitionerUuid);

		// 3. Assemble Bundle
//		var bundle = new Bundle();
//		bundle.setId(UUID.randomUUID().toString());
//		bundle.setType(Bundle.BundleType.DOCUMENT);
//		bundle.setTimestamp(new Date());
//		bundle.getMeta().addProfile(
//				"http://fhir.ch/ig/ch-vacd/StructureDefinition/ch-vacd-document-immunization-administration");
//		bundle.setIdentifier(new Identifier().setSystem("urn:ietf:rfc:3986").setValue("urn:uuid:" + bundle.getId()));
//		bundle.addEntry(new BundleEntryComponent().setFullUrl("urn:uuid:" + compositionUuid).setResource(composition));
//		bundle.addEntry(new BundleEntryComponent().setFullUrl("urn:uuid:" + patientUuid).setResource(patient));
//		bundle.addEntry(
//				new BundleEntryComponent().setFullUrl("urn:uuid:" + immunizationUuid).setResource(immunization));
//		bundle.addEntry(
//				new BundleEntryComponent().setFullUrl("urn:uuid:" + practitionerUuid).setResource(practitioner));
//		bundle.addEntry(
//				new BundleEntryComponent().setFullUrl("urn:uuid:" + organizationUuid).setResource(organization));
//		bundle.addEntry(new BundleEntryComponent().setFullUrl("urn:uuid:" + practitionerRoleUuid)
//				.setResource(practitionerRole));

		log.info("Assembled Immunization Administration Bundle:\n{}",
				fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle));

		// 4. Post to FHIR server
		var response = fhirClient.postImmunizationAdministrationBundle(bundle);
		log.info("Posted Immunization Administration Bundle, response ID: {}", response.getIdElement().getIdPart());

		// 5. Build VaccinationDto
		return buildVaccinationDto(createDto, immunizationUuid);
	}

	// ---- Resource builders ----

//	private Patient copyPatient(Patient source, UUID patientUuid) {
//		var json = fhirContext.newJsonParser().encodeResourceToString(source);
//		var copy = fhirContext.newJsonParser().parseResource(Patient.class, json);
//		copy.setId("urn:uuid:" + patientUuid);
//		return copy;
//	}

	private Immunization buildImmunization(ChVacdImmunizationAdministrationDocument bundle, ImmunizationCreateDto dto,
			Patient fhirPatient, PractitionerRole practitionerRole) {
		var imm = bundle.addImmunization();
//		imm.setId("urn:uuid:" + immunizationUuid);
		imm.setStatus(Immunization.ImmunizationStatus.COMPLETED);
//		imm.getMeta().addProfile("http://fhir.ch/ig/ch-vacd/StructureDefinition/ch-vacd-immunization-administration");
		imm.setPatient(new Reference("urn:uuid:" + fhirPatient.getIdElement().getIdPart()));
		var vaccineCode = new CodeableConcept();
		vaccineCode.addCoding(toCoding(dto.vaccineName()));
		imm.setVaccineCode(vaccineCode);

		var manufacturer = new Organization();
		manufacturer.setName(dto.marketingAuthorizationHolder());
		imm.setManufacturer(new Reference(manufacturer).setDisplay(dto.marketingAuthorizationHolder()));

		Medication medication = new Medication(); // TODO Replace by bundle.addMedication() when the
													// ChVacdImmunizationAdministrationDocument supports it
		medication.getMeta()
				.addProfile("http://fhir.ch/ig/ch-vacd/StructureDefinition/ch-vacd-medication-for-immunization");
		medication.setCode(
				new CodeableConcept().addCoding(new Coding().setSystem("urn:oid:2.51.1.1").setCode(dto.vaccineCode())));
		medication.setStatus(Medication.MedicationStatus.ACTIVE);
		medication.setManufacturer(new Reference(manufacturer));
		bundle.addEntry().setFullUrl("urn:uuid:" + UUID.randomUUID().toString()).setResource(medication);
		bundle.addEntry().setFullUrl("urn:uuid:" + UUID.randomUUID().toString()).setResource(manufacturer);

		Extension medicationExt = imm.addExtension();
		medicationExt.setUrl("http://fhir.ch/ig/ch-vacd/StructureDefinition/ch-vacd-immunization-medication");
		medicationExt.setValue(new Reference(medication));

		imm.setOccurrence(new DateTimeType(
				Date.from(dto.vaccinationDate().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant())));

		imm.setLotNumber(dto.lotNumber());

		imm.setRoute(routeToConcept(dto.routeOfAdministration()));

		var site = new CodeableConcept();
		site.setText(dto.siteOfAdministration());
		imm.setSite(site);

		var doseQty = new SimpleQuantity();
		doseQty.setValue(dto.administeredDose().value());
		doseQty.setUnit(dto.administeredDose().unit());
		imm.setDoseQuantity(doseQty);

//		imm.getPatient().setReference("urn:uuid:" + patientUuid);

		var performer = imm.addPerformer();
		performer.getActor().setResource(practitionerRole);
		performer.getActor().setDisplay("Dr. med. Sarah Müller");

		var pa = imm.addProtocolApplied();
		pa.setDoseNumber(new PositiveIntType(dto.doseNumber()));
		if (dto.seriesDoses() != null) {
			pa.setSeriesDoses(new PositiveIntType(dto.seriesDoses()));
		}

		if (dto.vaccinationReason() != null) {
			var reason = new CodeableConcept();
			reason.addCoding(new Coding().setSystem("http://snomed.info/sct").setCode(dto.vaccinationReason().code()));
			reason.setText(dto.vaccinationReason().display());
			imm.addReasonCode(reason);
		}

		return imm;
	}

	private Coding toCoding(String vaccineSystemCodeDisplay) {
		String[] splits = vaccineSystemCodeDisplay.split("\\|");
		return new Coding().setSystem(splits[0]).setCode(splits[1]).setDisplay(splits[2]);
	}

	private Practitioner buildPractitioner(UUID practitionerUuid) {
		var p = new ChCorePractitionerEpr();
//		p.setId("urn:uuid:" + practitionerUuid);
		p.addIdentifier().setSystem("urn:oid:2.51.1.3").setValue("7601000123456");
		p.addName().setFamily("Müller").addGiven("Sarah");
		return p;
	}

	private Organization buildOrganization(UUID organizationUuid) {
		var org = new ChCoreOrganizationEpr();
		// org.setId("urn:uuid:" + organizationUuid);
		org.addIdentifier().setSystem("urn:oid:2.51.1.3").setValue("7601000999999");
		org.setName("Praxis am Bahnhof");
		return org;
	}

	private PractitionerRole buildPractitionerRole(Practitioner practitioner, Organization organization) {
		var pr = new ChCorePractitionerRoleEpr();
//		pr.setId("urn:uuid:" + roleUuid);
		pr.getPractitioner().setResource(practitioner); // .setReference("urn:uuid:" + practitioner);
		pr.getOrganization().setResource(organization);// .setReference("urn:uuid:" + organization);
		return pr;
	}

//	private Composition buildComposition(UUID compositionUuid, UUID patientUuid, UUID practitionerRoleUuid,
//			UUID immunizationUuid) {
//		var comp = new Composition();
//		comp.setId("urn:uuid:" + compositionUuid);
//		comp.getMeta().addProfile(
//				"http://fhir.ch/ig/ch-vacd/StructureDefinition/ch-vacd-composition-immunization-administration");
//
//		comp.setIdentifier(new Identifier().setSystem("urn:ietf:rfc:3986").setValue("urn:uuid:" + compositionUuid));
//
//		comp.setStatus(Composition.CompositionStatus.FINAL);
//
//		var type = new CodeableConcept();
//		type.addCoding(new Coding("http://snomed.info/sct", "41000179103", "Immunization record"));
//		comp.setType(type);
//
//		var category = new CodeableConcept();
//		category.addCoding(new Coding("urn:oid:2.16.756.5.30.1.127.3.10.10",
//				"urn:che:epr:ch-vacd:immunization-administration:2022", "CH VACD Immunization Administration"));
//		comp.addCategory(category);
//
//		comp.setTitle("Immunization Administration");
//		comp.setDate(new Date());
//
//		comp.getSubject().setReference("urn:uuid:" + patientUuid);
//		comp.addAuthor().setReference("urn:uuid:" + practitionerRoleUuid);
//
//		var section = comp.addSection();
//		section.setTitle("Immunization Administration");
//		section.getCode().addCoding(new Coding("http://loinc.org", "11369-6", "Immunization Administration"));
//		section.addEntry().setReference("urn:uuid:" + immunizationUuid);
//
//		return comp;
//	}

	// ---- helpers ----

	private VaccinationDto buildVaccinationDto(ImmunizationCreateDto dto, UUID immunizationUuid) {
		return new VaccinationDto(immunizationUuid, dto.vaccineName(), dto.vaccineCode(),
				dto.doseNumber() + "/" + (dto.seriesDoses() != null ? dto.seriesDoses() : "-"), dto.vaccinationDate(),
				dto.marketingAuthorizationHolder(), dto.lotNumber(),
				dto.routeOfAdministration().toString().toLowerCase(), dto.siteOfAdministration(),
				new PractitionerDto("Dr. med. Sarah Müller", "7601000123456"), dto.vaccinationReason());
	}

	private static CodeableConcept routeToConcept(RouteOfAdministration route) {
		var cc = new CodeableConcept();
		switch (route) {
		case IM -> cc.addCoding(
				new Coding("http://terminology.hl7.org/CodeSystem/v3-RouteOfAdministration", "IM", "Intramuscular"));
		case SC -> cc.addCoding(
				new Coding("http://terminology.hl7.org/CodeSystem/v3-RouteOfAdministration", "SC", "Subcutaneous"));
		case ID -> cc.addCoding(
				new Coding("http://terminology.hl7.org/CodeSystem/v3-RouteOfAdministration", "ID", "Intradermal"));
		case ORAL ->
			cc.addCoding(new Coding("http://terminology.hl7.org/CodeSystem/v3-RouteOfAdministration", "ORAL", "Oral"));
		case NASAL -> cc.addCoding(
				new Coding("http://terminology.hl7.org/CodeSystem/v3-RouteOfAdministration", "NASAL", "Nasal"));
		}
		cc.setText(route.name());
		return cc;
	}
}
