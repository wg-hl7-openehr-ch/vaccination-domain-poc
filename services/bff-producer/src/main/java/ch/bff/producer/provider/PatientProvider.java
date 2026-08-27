package ch.bff.producer.provider;

import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ch.bff.producer.provider.models.AddressDto;
import ch.bff.producer.provider.models.Gender;
import ch.bff.producer.provider.models.PatientCreateDto;
import ch.bff.producer.provider.models.PatientDto;
import ch.bff.producer.services.PatientService;

@RestController
@RequestMapping("/api/patients")
public class PatientProvider {

	private static final Logger log = LoggerFactory.getLogger(PatientProvider.class);

	private final PatientService patientReadService;

	public PatientProvider(PatientService patientReadService) {
		this.patientReadService = patientReadService;
	}

	@GetMapping
	public List<PatientDto> getPatients() {
		try {
			return patientReadService.getPatientList();
		} catch (Exception e) {
			log.warn("FHIR server unavailable, falling back to sample patients: {}", e.getMessage(), e);
			return getSamplePatients();
		}
	}
	
	@PostMapping
	public PatientDto createPatient(@RequestBody PatientCreateDto patientDto) {
		try {
			return patientReadService.createPatient(patientDto);
		} catch (Exception e) {
			log.warn("FHIR server unavailable, falling back to sample patients: {}", e.getMessage(), e);
			return getSamplePatient();
		}
	}
	
	

	private PatientDto getSamplePatient() {
		// TODO Auto-generated method stub
		return new PatientDto("00000000-0000-0000-0000-000000000010", "Brunner", "Noah", LocalDate.of(2018, 12, 3), 7,
				Gender.MÄNNLICH, "756.6789.0123.45", new AddressDto("Hauptstrasse 45", "8400", "Winterthur"),
				"eltern.brunner@hotmail.com", "+41 79 456 78 90");
	}

	public static List<PatientDto> getSamplePatients() {
		return List.of(
				new PatientDto("00000000-0000-0000-0000-000000000010", "Brunner", "Noah", LocalDate.of(2018, 12, 3), 7,
						Gender.MÄNNLICH, "756.6789.0123.45", new AddressDto("Hauptstrasse 45", "8400", "Winterthur"),
						"eltern.brunner@hotmail.com", "+41 79 456 78 90"),
				new PatientDto("00000000-0000-0000-0000-000000000011", "Meier", "Elena", LocalDate.of(1992, 5, 14), 34,
						Gender.WEIBLICH, "756.3124.5589.12", new AddressDto("Bahnhofstrasse 12", "8001", "Zürich"),
						"elena.meier@gmx.ch", "+41 44 211 33 44"),
				new PatientDto("00000000-0000-0000-0000-000000000012", "Favre", "Jean-Luc", LocalDate.of(1965, 11, 22),
						60, Gender.MÄNNLICH, "756.8941.2233.76", new AddressDto("Rue du Simplon 5", "1006", "Lausanne"),
						"jl.favre@bluewin.ch", "+41 21 614 11 22"),
				new PatientDto("00000000-0000-0000-0000-000000000013", "Keller", "Sarah", LocalDate.of(2010, 8, 19), 15,
						Gender.WEIBLICH, "756.4452.9811.03", new AddressDto("Grenzacherstrasse 8", "4058", "Basel"),
						"sarah.keller@fhnw.ch", "+41 61 324 88 99"),
				new PatientDto("00000000-0000-0000-0000-000000000014", "Bernasconi", "Matteo",
						LocalDate.of(1987, 3, 30), 39, Gender.MÄNNLICH, "756.1298.7744.51",
						new AddressDto("Via Nassa 24", "6900", "Lugano"), "matteo.bernasconi@ticino.com",
						"+41 91 923 55 66"));
	}
}