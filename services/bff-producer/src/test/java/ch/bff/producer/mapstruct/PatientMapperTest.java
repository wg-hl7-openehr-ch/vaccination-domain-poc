package ch.bff.producer.mapstruct;

import ch.bff.producer.provider.models.AddressDto;
import ch.bff.producer.provider.models.Gender;
import ch.bff.producer.provider.models.PatientCreateDto;
import ch.bff.producer.provider.models.PatientDto;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PatientMapperTest {

	private static final String AHV_SYSTEM = "urn:oid:2.16.756.5.30.1.123.100.1.1.1";

	private PatientMapper mapper;

	@BeforeEach
	void setUp() {
		mapper = Mappers.getMapper(PatientMapper.class);
	}

	// -------------------------------------------------------------------------
	// toPatientDto
	// -------------------------------------------------------------------------

	@Test
	void toPatientDto_fullPatient_mapsAllFields() {
		Patient patient = buildFullFhirPatient();

		PatientDto dto = mapper.toPatientDto(patient);

		assertNotNull(dto);
		assertEquals("patient-1", dto.id());
		assertEquals("Muster", dto.lastName());
		assertEquals("Max", dto.firstName());
		assertEquals(LocalDate.of(1980, 6, 15), dto.birthDate());
		assertTrue(dto.age() > 0);
		assertEquals(Gender.MÄNNLICH, dto.gender());
		assertEquals("756.1234.5678.90", dto.ahvNumber());
		assertNotNull(dto.address());
		assertEquals("Bahnhofstrasse 1", dto.address().street());
		assertEquals("8001", dto.address().zipCode());
		assertEquals("Zürich", dto.address().city());
		assertEquals("max.muster@example.com", dto.email());
		assertEquals("+41791234567", dto.phoneNumber());
	}

	@Test
	void toPatientDto_femaleGender_mapsToWeiblich() {
		Patient patient = new Patient();
		patient.setGender(Enumerations.AdministrativeGender.FEMALE);
		patient.addName().setFamily("Muster").addGiven("Max");

		PatientDto dto = mapper.toPatientDto(patient);

		assertEquals(Gender.WEIBLICH, dto.gender());
	}

	@Test
	void toPatientDto_unknownGender_mapsToDivers() {
		Patient patient = new Patient();
		patient.setGender(Enumerations.AdministrativeGender.UNKNOWN);
		patient.addName().setFamily("Muster").addGiven("Max");

		PatientDto dto = mapper.toPatientDto(patient);

		assertEquals(Gender.DIVERS, dto.gender());
	}

	@Test
	void toPatientDto_nullGender_mapsToDivers() {
		Patient patient = new Patient();
		patient.addName().setFamily("Muster").addGiven("Max");

		PatientDto dto = mapper.toPatientDto(patient);

		assertEquals(Gender.DIVERS, dto.gender());
	}

	@Test
	void toPatientDto_nullBirthDate_returnsNullBirthDateAndZeroAge() {
		Patient patient = new Patient();
		patient.addName().setFamily("Muster").addGiven("Max");

		PatientDto dto = mapper.toPatientDto(patient);

		assertNull(dto.birthDate());
		assertEquals(0, dto.age());
	}

	@Test
	void toPatientDto_ahvBySystem_extractsCorrectly() {
		Patient patient = new Patient();
		patient.addName().setFamily("Muster").addGiven("Max");
		patient.addIdentifier().setSystem(AHV_SYSTEM).setValue("756.1234.5678.90");
		patient.addIdentifier().setSystem("other-system").setValue("other-value");

		PatientDto dto = mapper.toPatientDto(patient);

		assertEquals("756.1234.5678.90", dto.ahvNumber());
	}

	@Test
	void toPatientDto_ahvByPattern_fallsBackToPatternMatch() {
		Patient patient = new Patient();
		patient.addName().setFamily("Muster").addGiven("Max");
		patient.addIdentifier().setSystem("other-system").setValue("756.1234.5678.90");

		PatientDto dto = mapper.toPatientDto(patient);

		assertEquals("756.1234.5678.90", dto.ahvNumber());
	}

	@Test
	void toPatientDto_noIdentifiers_ahvIsNull() {
		Patient patient = new Patient();
		patient.addName().setFamily("Muster").addGiven("Max");

		PatientDto dto = mapper.toPatientDto(patient);

		assertNull(dto.ahvNumber());
	}

	@Test
	void toPatientDto_noTelecom_emailAndPhoneAreNull() {
		Patient patient = new Patient();
		patient.addName().setFamily("Muster").addGiven("Max");

		PatientDto dto = mapper.toPatientDto(patient);

		assertNull(dto.email());
		assertNull(dto.phoneNumber());
	}

	@Test
	void toPatientDto_noAddress_addressIsNull() {
		Patient patient = new Patient();
		patient.addName().setFamily("Muster").addGiven("Max");

		PatientDto dto = mapper.toPatientDto(patient);

		assertNull(dto.address());
	}

	// -------------------------------------------------------------------------
	// toPatient
	// -------------------------------------------------------------------------

	@Test
	void toPatient_fullDto_mapsAllFields() {
		PatientCreateDto dto = buildFullPatientCreateDto();

		Patient patient = mapper.toPatient(dto);

		assertNotNull(patient);
		assertEquals("Muster", patient.getNameFirstRep().getFamily());
		assertEquals("Max", patient.getNameFirstRep().getGivenAsSingleString());
		assertEquals(Enumerations.AdministrativeGender.MALE, patient.getGender());

		Date expectedBirthDate = Date.from(LocalDate.of(1980, 6, 15).atStartOfDay(ZoneId.systemDefault()).toInstant());
		assertEquals(expectedBirthDate, patient.getBirthDate());

		List<Identifier> identifiers = patient.getIdentifier();
		assertEquals(1, identifiers.size());
		assertEquals(PatientMapper.AHV_SYSTEM, identifiers.get(0).getSystem());
		assertEquals("756.1234.5678.90", identifiers.get(0).getValue());

		Address address = patient.getAddressFirstRep();
		assertTrue(address.getLine().stream().anyMatch(l -> l.getValue().contains("Bahnhofstrasse 1")));
		assertEquals("8001", address.getPostalCode());
		assertEquals("Zürich", address.getCity());

		String email = patient.getTelecom().stream()
				.filter(cp -> ContactPoint.ContactPointSystem.EMAIL.equals(cp.getSystem())).findFirst()
				.map(ContactPoint::getValue).orElse(null);
		assertEquals("max.muster@example.com", email);

		String phone = patient.getTelecom().stream()
				.filter(cp -> ContactPoint.ContactPointSystem.PHONE.equals(cp.getSystem())).findFirst()
				.map(ContactPoint::getValue).orElse(null);
		assertEquals("+41791234567", phone);
	}

	@Test
	void toPatient_maleGender_mapsToFhirMale() {
		PatientCreateDto dto = new PatientCreateDto("Muster", "Max", null, Gender.MÄNNLICH, null, null, null, null);

		Patient patient = mapper.toPatient(dto);

		assertEquals(Enumerations.AdministrativeGender.MALE, patient.getGender());
	}

	@Test
	void toPatient_femaleGender_mapsToFhirFemale() {
		PatientCreateDto dto = new PatientCreateDto("Muster", "Max", null, Gender.WEIBLICH, null, null, null, null);

		Patient patient = mapper.toPatient(dto);

		assertEquals(Enumerations.AdministrativeGender.FEMALE, patient.getGender());
	}

	@Test
	void toPatient_diversGender_mapsToFhirOther() {
		PatientCreateDto dto = new PatientCreateDto("Muster", "Max", null, Gender.DIVERS, null, null, null, null);

		Patient patient = mapper.toPatient(dto);

		assertEquals(Enumerations.AdministrativeGender.OTHER, patient.getGender());
	}

	@Test
	void toPatient_nullBirthDate_birthDateIsNull() {
		PatientCreateDto dto = new PatientCreateDto("Muster", "Max", null, Gender.MÄNNLICH, null, null, null, null);

		Patient patient = mapper.toPatient(dto);

		assertNull(patient.getBirthDate());
	}

	@Test
	void toPatient_nullAhvNumber_noIdentifiers() {
		PatientCreateDto dto = new PatientCreateDto("Muster", "Max", null, Gender.MÄNNLICH, null, null, null, null);

		Patient patient = mapper.toPatient(dto);

		assertTrue(patient.getIdentifier().isEmpty());
	}

	@Test
	void toPatient_nullAddress_noAddresses() {
		PatientCreateDto dto = new PatientCreateDto("Muster", "Max", null, Gender.MÄNNLICH, null, null, null, null);

		Patient patient = mapper.toPatient(dto);

		assertTrue(patient.getAddress().isEmpty());
	}

	@Test
	void toPatient_nullEmailAndPhone_noTelecom() {
		PatientCreateDto dto = new PatientCreateDto("Muster", "Max", null, Gender.MÄNNLICH, null, null, null, null);

		Patient patient = mapper.toPatient(dto);

		assertTrue(patient.getTelecom().isEmpty());
	}

	@Test
	void toPatient_onlyEmail_onlyEmailTelecom() {
		PatientCreateDto dto = new PatientCreateDto("Muster", "Max", null, Gender.MÄNNLICH, null, "test@example.com", null, null);

		Patient patient = mapper.toPatient(dto);

		assertEquals(1, patient.getTelecom().size());
		assertEquals(ContactPoint.ContactPointSystem.EMAIL, patient.getTelecom().get(0).getSystem());
		assertEquals("test@example.com", patient.getTelecom().get(0).getValue());
	}

	// -------------------------------------------------------------------------
	// Roundtrip
	// -------------------------------------------------------------------------

	@Test
	void roundtrip_toPatientDto_toPatient_preservesCoreFields() {
		Patient original = buildFullFhirPatient();

		PatientDto dto = mapper.toPatientDto(original);
		PatientCreateDto cdto = new PatientCreateDto(dto.lastName(), dto.firstName(), dto.birthDate(), dto.gender(), dto.address(), dto.email(), dto.phoneNumber(), dto.ahvNumber());
		Patient restored = mapper.toPatient(cdto);

		assertEquals(original.getNameFirstRep().getFamily(), restored.getNameFirstRep().getFamily());
		assertEquals(original.getNameFirstRep().getGivenAsSingleString(),
				restored.getNameFirstRep().getGivenAsSingleString());
		assertEquals(original.getGender(), restored.getGender());
		assertEquals(original.getBirthDate(), restored.getBirthDate());
		assertEquals(
				original.getIdentifier().stream().filter(i -> PatientMapper.AHV_SYSTEM.equals(i.getSystem())).findFirst()
						.map(Identifier::getValue).orElse(null),
				restored.getIdentifier().stream().filter(i -> PatientMapper.AHV_SYSTEM.equals(i.getSystem())).findFirst()
						.map(Identifier::getValue).orElse(null));
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private Patient buildFullFhirPatient() {
		Patient patient = new Patient();
		patient.setId("patient-1");

		HumanName name = new HumanName();
		name.setFamily("Muster");
		name.addGiven("Max");
		patient.addName(name);

		patient.setGender(Enumerations.AdministrativeGender.MALE);
		patient.setBirthDate(Date.from(LocalDate.of(1980, 6, 15).atStartOfDay(ZoneId.systemDefault()).toInstant()));

		patient.addIdentifier().setSystem(AHV_SYSTEM).setValue("756.1234.5678.90");

		Address address = new Address();
		address.addLine("Bahnhofstrasse 1");
		address.setPostalCode("8001");
		address.setCity("Zürich");
		patient.addAddress(address);

		patient.addTelecom().setSystem(ContactPoint.ContactPointSystem.EMAIL).setValue("max.muster@example.com");
		patient.addTelecom().setSystem(ContactPoint.ContactPointSystem.PHONE).setValue("+41791234567");

		return patient;
	}

	private PatientDto buildFullPatientDto() {
		return new PatientDto("patient-1", "Muster", "Max", LocalDate.of(1980, 6, 15), 43, Gender.MÄNNLICH,
				"756.1234.5678.90", new AddressDto("Bahnhofstrasse 1", "8001", "Zürich"), "max.muster@example.com",
				"+41791234567");
	}

	private PatientCreateDto buildFullPatientCreateDto() {
		return new PatientCreateDto("Muster", "Max", LocalDate.of(1980, 6, 15), Gender.MÄNNLICH,
				new AddressDto("Bahnhofstrasse 1", "8001", "Zürich"), "max.muster@example.com", "+41791234567",
				"756.1234.5678.90");
	}
}
