package ch.bff.producer.provider;

import ch.bff.producer.provider.models.PractitionerDto;
import ch.bff.producer.provider.models.VaccinationDto;
import ch.bff.producer.provider.models.VaccinationReason;
import ch.bff.producer.services.VaccinationsReadService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/vaccinations")
public class VaccinationProvider {

	private static final Logger log = LoggerFactory.getLogger(VaccinationProvider.class);

	private final VaccinationsReadService vacctinationsReadService;

	public VaccinationProvider(VaccinationsReadService vacctinationsReadService) {
		this.vacctinationsReadService = vacctinationsReadService;
	}

	@GetMapping
	public List<VaccinationDto> getVaccinations(String personId) {
		try {
			return vacctinationsReadService.getVaccinationList(personId);
		} catch (Exception e) {
			log.warn("FHIR unavailable, falling back to sample vaccinations: {}", e.getMessage(), e);
			return getSampleVaccinations();
		}
	}

	@PostMapping(value = "/import", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
	public void importVaccinations(@RequestParam String personId, @RequestParam String format,
			@RequestBody() MultipartFile file) {
		try {
			log.info("Importing vaccinations for personId: {}, format: {}, fileName: {}, contentType: {}",
					personId, format, file.getOriginalFilename(), file.getContentType());
		} catch (Exception e) {
			log.warn("Import failed: {}", e.getMessage(), e);
		}
	}

	@GetMapping("/export")
	public String exportVaccinations(String personId, String format) {
		try {
			return vacctinationsReadService.exportVaccinations(personId, format);
		} catch (Exception e) {
			log.warn("FHIR unavailable, falling back to sample vaccinations: {}", e.getMessage(), e);
			return null;
		}
	}

	private static List<VaccinationDto> getSampleVaccinations() {
		return List.of(
				// 1. Impfung aus dem Screenshot
				new VaccinationDto(UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6"), "Priorix", "00000-09",
						"1/2", LocalDate.of(2020, 1, 22), "GlaxoSmithKline", "PR2001", "s.c.", "Oberarm links",
						new PractitionerDto("Dr. S. Müller", "7601000233456"),
						new VaccinationReason("223366009", "Grundimmunisierung", null), List.of()),

				// 2. Impfung (Folgeimpfung MMR)
				new VaccinationDto(UUID.fromString("7b921e12-3456-7890-abcd-ef1234567890"), "Priorix", "00000-09",
						"2/2", LocalDate.of(2020, 3, 24), "GlaxoSmithKline", "PR2005", "s.c.", "Oberarm rechts",
						new PractitionerDto("Dr. S. Müller", "7601000233456"),
						new VaccinationReason("171257003", "Auffrischimpfung", null), List.of()),

				// 3. Impfung (Starrkrampf / DTP)
				new VaccinationDto(UUID.fromString("a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d"), "Boostrix", "00000-01",
						"1/1", LocalDate.of(2021, 6, 15), "GlaxoSmithKline", "BS9942", "i.m.", "Oberschenkel links",
						new PractitionerDto("Dr. med. A. Pfister", "7601003445566"),
						new VaccinationReason("171257003", "Auffrischimpfung", null), List.of()),

				// 4. Impfung (FSME / Zecken)
				new VaccinationDto(UUID.fromString("f8e7d6c5-b4a3-9281-7065-5443322110aa"), "FSME-Immun", "00000-04",
						"1/3", LocalDate.of(2023, 4, 10), "Pfizer", "VNR402A", "i.m.", "Oberarm links",
						new PractitionerDto("Dr. S. Müller", "7601000233456"),
						new VaccinationReason("223366009", "Grundimmunisierung", null), List.of()),

				// 5. Impfung (Grippe)
				new VaccinationDto(UUID.fromString("00000000-1111-2222-3333-444455556666"), "Fluarix Tetra", "00000-06",
						"1/1", LocalDate.of(2025, 10, 05), "GlaxoSmithKline", "FL2025X", "i.m.", "Oberarm rechts",
						new PractitionerDto("Apotheke am Bahnhof", "7601009998877"),
						new VaccinationReason("386472008", "saisonale Influenza", null), List.of()));
	}
}