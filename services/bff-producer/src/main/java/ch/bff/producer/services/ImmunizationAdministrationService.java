package ch.bff.producer.services;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.PositiveIntType;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.SimpleQuantity;
import org.hl7.fhir.r4.model.UriType;
import org.projecthusky.fhir.core.ch.resource.r4.ChCoreOrganization;
import org.projecthusky.fhir.core.ch.resource.r4.ChCoreOrganizationEpr;
import org.projecthusky.fhir.core.ch.resource.r4.ChCorePractitioner;
import org.projecthusky.fhir.vacd.ch.common.resource.r4.ChVacdImmunization;
import org.projecthusky.fhir.vacd.ch.common.resource.r4.ChVacdImmunizationAdministrationDocument;
import org.projecthusky.fhir.vacd.ch.common.resource.r4.ChVacdMedicationForImmunization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import ca.uhn.fhir.context.FhirContext;
import ch.bff.producer.client.FhirClient;
import ch.bff.producer.client.TxClient;
import ch.bff.producer.provider.models.CodingDto;
import ch.bff.producer.provider.models.ImmunizationCreateDto;
import ch.bff.producer.provider.models.PractitionerDto;
import ch.bff.producer.provider.models.RouteOfAdministration;
import ch.bff.producer.provider.models.VaccinationDto;

@Service
public class ImmunizationAdministrationService extends AbstractReadService {

	private static final Logger log = LoggerFactory.getLogger(ImmunizationAdministrationService.class);

	private final TxClient txClient;
	private final FhirContext fhirContext;

	public ImmunizationAdministrationService(FhirClient fhirClient, TxClient txClient) {
		super(fhirClient);
		this.txClient = txClient;
		this.fhirContext = FhirContext.forR4();
	}

	public VaccinationDto createImmunizationAdministration(String patientIamId, ImmunizationCreateDto createDto) {

		log.info("Creating Immunization Administration for patient IAM ID: {}\n{}", patientIamId, createDto);

		// 1. Assemble Immunization Administration Bundle
		ChVacdImmunizationAdministrationDocument bundle = new ChVacdImmunizationAdministrationDocument();
		// 2. Real patient from FHIR
		var fhirPatient = fhirClient.getPatientById(patientIamId);
		bundle.setPatient(fhirPatient);

		// 3. Build PractitionerRole, Practitioner and Organization resources for author
		// and performer
		var practitionerRole = bundle.addPractitionerRole();
		buildPractitioner((ChCorePractitioner) practitionerRole.getPractitioner().getResource());
		buildOrganization((ChCoreOrganization) practitionerRole.getOrganization().getResource());
		bundle.addAuthor(practitionerRole, new Date());

		// 4. Build Immunization resource
		var immunization = buildImmunization(bundle, createDto, fhirPatient, practitionerRole);
		var immunizationId = immunization.getIdElement().getIdPart();
		var immunizationUuid = UUID.fromString(immunizationId.substring("urn:uuid:".length()));
//        var immunization = buildImmunization(createDto, immunizationUuid, patientUuid, practitionerUuid);

		log.info("Assembled Immunization Administration Bundle:\n{}",
				fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle));

		// 5. Post to FHIR server
		var response = fhirClient.postImmunizationAdministrationBundle(bundle);
		log.info("Posted Immunization Administration Bundle, response ID: {}", response.getIdElement().getIdPart());

		// 6. Build VaccinationDto
		return buildVaccinationDto(createDto, immunizationUuid,
				(immunization.getProtocolAppliedFirstRep() != null
						&& immunization.getProtocolAppliedFirstRep().getTargetDisease() != null)
								? immunization.getProtocolAppliedFirstRep().getTargetDiseaseFirstRep().getCoding()
								: List.of());
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
		imm.setPatient(new Reference(fhirPatient));
		var vaccineCode = toCoding(dto.vaccineName());
		imm.setVaccineCode(new CodeableConcept().addCoding(vaccineCode));

		ChCoreOrganizationEpr manufacturer = null;
		if (StringUtils.hasText(dto.marketingAuthorizationHolder())) {
			manufacturer = bundle.addOrganization();
			manufacturer.setName(dto.marketingAuthorizationHolder());
			imm.setManufacturer(new Reference(manufacturer).setDisplay(dto.marketingAuthorizationHolder()));
		}

		if (StringUtils.hasText(dto.vaccineCode())) {
			ChVacdMedicationForImmunization medication = bundle.addMedication();
			medication.setCode(new CodeableConcept()
					.addCoding(new Coding().setSystem("urn:oid:2.51.1.1").setCode(dto.vaccineCode())));
			medication.setStatus(Medication.MedicationStatus.ACTIVE);
			if (manufacturer != null) {
				medication.setManufacturer(new Reference(manufacturer));
			}
			imm.setMedication(medication);
		}

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

		imm.setRecorder(new Reference(practitionerRole).setDisplay("Dr. med. Sarah Müller"));

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

		List<CodeableConcept> targetDieseases = lookupTargetDiseasesForVaccine(imm, vaccineCode);
		imm.setTargetDiseases(targetDieseases);

		return imm;
	}

	private List<CodeableConcept> lookupTargetDiseasesForVaccine(ChVacdImmunization imm, Coding vaccineCode) {
		Parameters diseaseParameters = new Parameters();
		diseaseParameters.addParameter().setName("url")
				.setValue(new UriType("http://fhir.ch/ig/ch-vacd/ConceptMap/ch-vacd-vaccines-targetdiseases-cm"));
		diseaseParameters.addParameter().setName("sourceCode").setValue(new CodeType(vaccineCode.getCode()));
		diseaseParameters.addParameter().setName("system").setValue(new UriType(vaccineCode.getSystem()));
		Parameters targetDiseaseParameters = txClient.getTargetDiseasesForVaccine(diseaseParameters);
		List<CodeableConcept> targetDieseases = List.of(toCodeableConcept(targetDiseaseParameters));
		return targetDieseases;
	}

	private CodeableConcept toCodeableConcept(Parameters targetDiseaseParameters) {
		CodeableConcept cc = new CodeableConcept();
		if (targetDiseaseParameters.hasParameter("result") && targetDiseaseParameters.getParameterBool("result")) {
			targetDiseaseParameters.getParameters("match").forEach(matchParam -> {
				matchParam.getPart().forEach(part -> {
					if ("concept".equals(part.getName()) && part.getValue() instanceof Coding coding) {
						Parameters lookupParam = new Parameters();
						lookupParam.addParameter().setName("coding").setValue(coding);
						Parameters lookup = txClient.lookupCode(lookupParam);
						if (lookup.hasParameter("display")) {
							String display = lookup.getParameterValue("display").primitiveValue();
							coding.setDisplay(display);
						}
						cc.addCoding(coding);
					}
				});
			});
		}
		return cc;
	}

	private Coding toCoding(String vaccineSystemCodeDisplay) {
		String[] splits = vaccineSystemCodeDisplay.split("\\|");
		return new Coding().setSystem(splits[0]).setCode(splits[1]).setDisplay(splits[2]);
	}

	private Practitioner buildPractitioner(ChCorePractitioner p) {
//		var p = new ChCorePractitionerEpr();
//		p.setId("urn:uuid:" + practitionerUuid);
		p.addIdentifier().setSystem("urn:oid:2.51.1.3").setValue("7601000123456");
		p.addName().setFamily("Müller").addGiven("Sarah");
		return p;
	}

	private Organization buildOrganization(ChCoreOrganization org) {
//		var org = new ChCoreOrganizationEpr();
		// org.setId("urn:uuid:" + organizationUuid);
		org.addIdentifier().setSystem("urn:oid:2.51.1.3").setValue("7601000999999");
		org.setName("Praxis am Bahnhof");
		return org;
	}

//	private PractitionerRole buildPractitionerRole(Practitioner practitioner, Organization organization) {
//		var pr = new ChCorePractitionerRoleEpr();
////		pr.setId("urn:uuid:" + roleUuid);
//		pr.getPractitioner().setResource(practitioner); // .setReference("urn:uuid:" + practitioner);
//		pr.getOrganization().setResource(organization);// .setReference("urn:uuid:" + organization);
//		return pr;
//	}

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

	private VaccinationDto buildVaccinationDto(ImmunizationCreateDto dto, UUID immunizationUuid,
			List<Coding> targetDiseases) {
		return new VaccinationDto(immunizationUuid, dto.vaccineName(), dto.vaccineCode(),
				dto.doseNumber() + "/" + (dto.seriesDoses() != null ? dto.seriesDoses() : "-"), dto.vaccinationDate(),
				dto.marketingAuthorizationHolder(), dto.lotNumber(),
				dto.routeOfAdministration().toString().toLowerCase(), dto.siteOfAdministration(),
				new PractitionerDto("Dr. med. Sarah Müller", "7601000123456"), dto.vaccinationReason(),
				toCodingDtos(targetDiseases));
	}

	private List<CodingDto> toCodingDtos(List<Coding> targetDiseases) {
		List<CodingDto> list = targetDiseases.stream()
				.map(c -> new CodingDto(c.getSystem() + "|" + c.getCode() + "|" + c.getDisplay(), //
						c.getSystem(), //
						c.getCode(), //
						c.getDisplay()))
				.toList();
		return list;
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
