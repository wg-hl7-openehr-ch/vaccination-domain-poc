package ch.bff.producer.mapstruct;

import ch.bff.producer.provider.models.AdministeredDose;
import ch.bff.producer.provider.models.PractitionerDto;
import ch.bff.producer.provider.models.VaccinationDto;
import ch.bff.producer.provider.models.VaccinationReason;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.Reference;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

@Mapper(componentModel = "spring", imports = { LocalDate.class, ZoneId.class })
public interface VaccinationsMapper {

	@Mapping(target = "id", expression = "java(parseId(immunization))")
	@Mapping(target = "vaccineName", source = "vaccineCode", qualifiedByName = "vaccineName")
	@Mapping(target = "vaccineCode", source = "vaccineCode", qualifiedByName = "vaccineCode")
	@Mapping(target = "doseSequence", expression = "java(doseSequence(immunization))")
	@Mapping(target = "vaccinationDate", source = "occurrenceDateTimeType", qualifiedByName = "toLocalDate")
	@Mapping(target = "manufacturer", expression = "java(manufacturerName(immunization))")
	@Mapping(target = "lotNumber", source = "lotNumber")
	@Mapping(target = "administrationRoute", source = "route", qualifiedByName = "conceptDisplay")
	@Mapping(target = "siteOfAdministration", source = "site", qualifiedByName = "conceptDisplay")
	@Mapping(target = "practitioner", expression = "java(mapPractitioner(immunization))")
	@Mapping(target = "vaccinationReason", expression = "java(mapVaccinationReason(immunization))")
	@Mapping(target = "vaccineSystem", source = "vaccineCode", qualifiedByName = "vaccineSystem")
	@Mapping(target = "administeredDose", expression = "java(administeredDose(immunization))")
	@Mapping(target = "doseNumber", expression = "java(doseNumber(immunization))")
	@Mapping(target = "seriesDoses", expression = "java(seriesDoses(immunization))")
	@Mapping(target = "version", expression = "java(immunization.getMeta().getVersionId())")
	@Mapping(target = "lastUpdated", expression = "java(lastUpdated(immunization))")
	@Mapping(target = "corrected", expression = "java(corrected(immunization))")
	VaccinationDto toVaccinationDto(Immunization immunization);

	@Named("vaccineName")
	default String vaccineName(CodeableConcept vaccineCode) {
		if (vaccineCode == null)
			return null;
		if (vaccineCode.hasCoding()) {
			return vaccineCode.getCodingFirstRep().getDisplay();
		}
		return vaccineCode.getText();
	}

	@Named("vaccineCode")
	default String vaccineCode(CodeableConcept vaccineCode) {
		if (vaccineCode == null)
			return null;
		if (vaccineCode.hasCoding()) {
			return vaccineCode.getCodingFirstRep().getCode();
		}
		return null;
	}

	@Named("vaccineSystem")
	default String vaccineSystem(CodeableConcept vaccineCode) {
		if (vaccineCode == null)
			return null;
		if (vaccineCode.hasCoding()) {
			return vaccineCode.getCodingFirstRep().getSystem();
		}
		return null;
	}

	@Named("toLocalDate")
	default LocalDate toLocalDate(DateTimeType dateTimeType) {
		if (dateTimeType == null)
			return null;
		String str = dateTimeType.getValueAsString();
		if (str == null)
			return null;
		int tIndex = str.indexOf('T');
		if (tIndex > 0) {
			try {
				return OffsetDateTime.parse(str).toLocalDate();
			} catch (Exception ignored) {
			}
		}
		return LocalDate.parse(str.substring(0, tIndex > 0 ? tIndex : str.length()));
	}

	default String parseId(Immunization immunization) {
		String idPart = immunization.getIdElement().getIdPart();
		if (idPart != null && idPart.startsWith("urn:uuid:")) {
			return idPart.substring("urn:uuid:".length());
		}
		return idPart;
	}

	default Instant lastUpdated(Immunization immunization) {
		var lastUpdated = immunization.getMeta().getLastUpdated();
		return lastUpdated != null ? lastUpdated.toInstant() : null;
	}

	default boolean corrected(Immunization immunization) {
		try {
			return Integer.parseInt(immunization.getMeta().getVersionId()) > 1;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	default AdministeredDose administeredDose(Immunization immunization) {
		if (!immunization.hasDoseQuantity())
			return null;
		var quantity = immunization.getDoseQuantity();
		if (!quantity.hasValue() || !quantity.hasUnit() || quantity.getUnit().isBlank())
			return null;
		return new AdministeredDose(quantity.getValue().doubleValue(), quantity.getUnit());
	}

	default Integer doseNumber(Immunization immunization) {
		if (!immunization.hasProtocolApplied())
			return null;
		var pa = immunization.getProtocolAppliedFirstRep();
		return pa.hasDoseNumberPositiveIntType() ? pa.getDoseNumberPositiveIntType().getValue() : null;
	}

	default Integer seriesDoses(Immunization immunization) {
		if (!immunization.hasProtocolApplied())
			return null;
		var pa = immunization.getProtocolAppliedFirstRep();
		return pa.hasSeriesDosesPositiveIntType() ? pa.getSeriesDosesPositiveIntType().getValue() : null;
	}

	default String manufacturerName(Immunization immunization) {
		if (immunization.hasManufacturer()) {
			return immunization.getManufacturer().getDisplay();
		}
		Optional<Extension> medExt = immunization.getExtension().stream().filter(
				e -> "http://fhir.ch/ig/ch-vacd/StructureDefinition/ch-vacd-ext-immunization-medication-reference"
						.equals(e.getUrl()))//
				.findFirst();
		if (medExt.isPresent()) {
			if (medExt.get().getValue() instanceof Reference) {
				Reference medRef = (Reference) medExt.get().getValue();
				if (medRef.getResource() instanceof Medication) {
					Medication med = (Medication) medRef.getResource();
					if (med.getManufacturer().getResource() instanceof Organization) {
						Organization manufacturer = (Organization) med.getManufacturer().getResource();
						return manufacturer.getName();
					}
				}
			}
		}

		return null;
	}

	@Named("conceptDisplay")
	default String conceptDisplay(CodeableConcept cc) {
		if (cc == null)
			return null;
		if (cc.hasCoding()) {
			return cc.getCodingFirstRep().getDisplay();
		}
		return cc.getText();
	}

	default String doseSequence(Immunization immunization) {
		var protocolApplied = immunization.getProtocolApplied();
		if (protocolApplied == null || protocolApplied.isEmpty())
			return null;
		var pa = protocolApplied.get(0);
		int dose = pa.getDoseNumberPositiveIntType().getValue();
		Integer series = pa.hasSeriesDosesPositiveIntType() ? pa.getSeriesDosesPositiveIntType().getValue() : null;
		return series != null ? dose + "/" + series : String.valueOf(dose);
	}

	default PractitionerDto mapPractitioner(Immunization immunization) {
		var performers = immunization.getPerformer();
		if (performers == null || performers.isEmpty())
			return null;
		var performer = performers.get(0);
		var actor = performer.getActor();
		if (actor == null)
			return null;

		String name = actor.getDisplay();
		String gln = null;
		if (actor.getResource() != null) {
			if (actor.getResource() instanceof Practitioner) {
				Practitioner practitioner = (Practitioner) actor.getResource();
				name = practitioner.getNameFirstRep().getNameAsSingleString();
			} else if (actor.getResource() instanceof PractitionerRole) {
				PractitionerRole practitionerRole = (PractitionerRole) actor.getResource();
				if (practitionerRole.getPractitioner().getResource() != null) {
					Practitioner practitioner = (Practitioner) practitionerRole.getPractitioner().getResource();
					name = practitioner.getNameFirstRep().getNameAsSingleString();
					if (practitioner.hasIdentifier()) {
						gln = practitioner.getIdentifier().stream()
								.filter(i -> "urn:oid:2.51.1.3".equals(i.getSystem())).findFirst()
								.map(i -> i.getValue()).orElse(null);
					}
				} else {
					name = actor.getResource().getIdElement().getIdPart();
				}
			}
		} else {
			name = actor.getReference();
		}
		if (name == null)
			return null;

		if (actor.hasIdentifier()) {
			gln = actor.getIdentifier().getValue();
		}
		return new PractitionerDto(name, gln);
	}

	default VaccinationReason mapVaccinationReason(Immunization immunization) {
		var reasonCodes = immunization.getReasonCode();
		if (reasonCodes == null || reasonCodes.isEmpty())
			return null;
		var reason = reasonCodes.get(0);
		String code = null;
		String display = null;
		if (reason.hasCoding()) {
			code = reason.getCodingFirstRep().getCode();
			display = reason.getCodingFirstRep().getDisplay();
		}
		return new VaccinationReason(code, display != null ? display : reason.getText(), null);
	}
}
