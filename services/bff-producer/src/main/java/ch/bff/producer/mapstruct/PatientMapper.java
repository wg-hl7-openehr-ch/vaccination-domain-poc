package ch.bff.producer.mapstruct;

import ch.bff.producer.provider.models.AddressDto;
import ch.bff.producer.provider.models.Gender;
import ch.bff.producer.provider.models.PatientCreateDto;
import ch.bff.producer.provider.models.PatientDto;
import io.micrometer.common.util.StringUtils;

import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.projecthusky.fhir.core.ch.resource.r4.ChCorePatient;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Mapper(componentModel = "spring", imports = {LocalDate.class, Period.class, ZoneId.class})
public interface PatientMapper {

    String AHV_SYSTEM = "urn:oid:2.16.756.5.32";

    @Mapping(target = "id", source = "idElement.idPart")
    @Mapping(target = "lastName", source = "nameFirstRep.family")
    @Mapping(target = "firstName", source = "nameFirstRep.givenAsSingleString")
    @Mapping(target = "birthDate", source = "birthDate", qualifiedByName = "toLocalDate")
    @Mapping(target = "age", expression = "java(patient.getBirthDate() != null ? Period.between(patient.getBirthDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate(), LocalDate.now()).getYears() : 0)")
    @Mapping(target = "gender", expression = "java(mapGender(patient.getGender()))")
    @Mapping(target = "ahvNumber", source = "identifier", qualifiedByName = "extractAhv")
    @Mapping(target = "address", source = "addressFirstRep", qualifiedByName = "mapAddress")
    @Mapping(target = "email", source = "telecom", qualifiedByName = "extractEmail")
    @Mapping(target = "phoneNumber", source = "telecom", qualifiedByName = "extractPhone")
    PatientDto toPatientDto(org.hl7.fhir.r4.model.Patient patient);

    @Mapping(target = "name", expression = "java(mapName(patientDto.firstName(), patientDto.lastName()))")
    @Mapping(target = "birthDate", source = "birthDate", qualifiedByName = "toDate")
    @Mapping(target = "gender", source = "gender", qualifiedByName = "mapGenderToFhir")
    @Mapping(target = "address", expression = "java(buildAddressList(patientDto.address()))")
    @Mapping(target = "telecom", expression = "java(buildTelecom(patientDto.email(), patientDto.phoneNumber()))")
    @Mapping(target = "identifier", expression = "java(buildAhvIdentifier(patientDto.ahv()))")
    @Mapping(target = "active", constant = "true")
    org.hl7.fhir.r4.model.Patient toPatient(PatientCreateDto patientDto);

    
    
    @Named("toLocalDate")
    default LocalDate toLocalDate(Date date) {
        if (date == null) return null;
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    @Named("mapGender")
    default Gender mapGender(Enumerations.AdministrativeGender fhirGender) {
        if (fhirGender == null) return Gender.DIVERS;
        return switch (fhirGender) {
            case MALE -> Gender.MÄNNLICH;
            case FEMALE -> Gender.WEIBLICH;
            default -> Gender.DIVERS;
        };
    }

    @Named("extractAhv")
    default String extractAhv(List<Identifier> identifiers) {
        if (identifiers == null) return null;
//        identifiers.forEach(i -> LoggerFactory.getLogger(getClass()).info("Identifier: system={}, value={}", i.getSystem(), i.getValue()));
        
        return identifiers.stream()
                .filter(id -> AHV_SYSTEM.equals(id.getSystem()))
                .findFirst()
                .map(Identifier::getValue)
                .orElseGet(() -> identifiers.stream()
                        .map(Identifier::getValue)
                        .filter(v -> v != null && v.matches("756[.-]?\\d{4}[.-]?\\d{4}[.-]?\\d{2}"))
                        .findFirst()
                        .orElse(null));
    }

    @Named("mapAddress")
    default AddressDto mapAddress(Address fhirAddress) {
        if (fhirAddress == null || fhirAddress.isEmpty()) return null;
        String street = fhirAddress.hasLine()
                ? String.join(" ", fhirAddress.getLine().stream().map(ln -> ln.getValueNotNull()).toList())
                : null;
        return new AddressDto(street, fhirAddress.getPostalCode(), fhirAddress.getCity());
    }

    @Named("extractEmail")
    default String extractEmail(List<ContactPoint> telecoms) {
        return extractTelecom(telecoms, ContactPoint.ContactPointSystem.EMAIL);
    }

    @Named("extractPhone")
    default String extractPhone(List<ContactPoint> telecoms) {
        return extractTelecom(telecoms, ContactPoint.ContactPointSystem.PHONE);
    }

    private static String extractTelecom(List<ContactPoint> telecoms, ContactPoint.ContactPointSystem system) {
        if (telecoms == null) return null;
        return telecoms.stream()
                .filter(cp -> system.equals(cp.getSystem()))
                .findFirst()
                .map(ContactPoint::getValue)
                .orElse(null);
    }

    @Named("toDate")
    default Date toDate(LocalDate localDate) {
        if (localDate == null) return null;
        return Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    @Named("mapGenderToFhir")
    default Enumerations.AdministrativeGender mapGenderToFhir(Gender gender) {
        if (gender == null) return Enumerations.AdministrativeGender.UNKNOWN;
        return switch (gender) {
            case MÄNNLICH -> Enumerations.AdministrativeGender.MALE;
            case WEIBLICH -> Enumerations.AdministrativeGender.FEMALE;
            default -> Enumerations.AdministrativeGender.OTHER;
        };
    }

    default List<HumanName> mapName(String firstName, String lastName) {
        HumanName name = new HumanName();
        name.setFamily(lastName);
        if (firstName != null) {
            name.addGiven(firstName);
        }
        return Collections.singletonList(name);
    }

    default List<Identifier> buildAhvIdentifier(String ahvNumber) {
        if (ahvNumber == null) return Collections.emptyList();
        Identifier identifier = new Identifier();
        identifier.setUse(Identifier.IdentifierUse.OFFICIAL);
        identifier.setSystem(AHV_SYSTEM);
        identifier.setValue(ahvNumber);
        return Collections.singletonList(identifier);
    }

    default List<Address> buildAddressList(AddressDto addressDto) {
        if (addressDto == null) return Collections.emptyList();
        Address address = new Address();
        if (addressDto.street() != null) {
            address.addLine(addressDto.street());
        }
        address.setPostalCode(addressDto.zipCode());
        address.setCity(addressDto.city());
        return Collections.singletonList(address);
    }

    default List<ContactPoint> buildTelecom(String email, String phoneNumber) {
        List<ContactPoint> telecoms = new ArrayList<>();
        if (StringUtils.isNotEmpty(email)) {
            ContactPoint emailCp = new ContactPoint();
            emailCp.setSystem(ContactPoint.ContactPointSystem.EMAIL);
            emailCp.setValue(email);
            telecoms.add(emailCp);
        }
        if (StringUtils.isNotEmpty(phoneNumber)) {
            ContactPoint phoneCp = new ContactPoint();
            phoneCp.setSystem(ContactPoint.ContactPointSystem.PHONE);
            phoneCp.setValue(phoneNumber);
            telecoms.add(phoneCp);
        }
        return telecoms;
    }

}
