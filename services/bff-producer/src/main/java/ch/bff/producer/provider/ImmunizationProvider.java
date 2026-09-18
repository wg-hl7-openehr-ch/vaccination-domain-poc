package ch.bff.producer.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import ch.bff.producer.provider.models.ImmunizationCreateDto;
import ch.bff.producer.provider.models.ImportDto;
import ch.bff.producer.provider.models.VaccinationDto;
import ch.bff.producer.services.ImmunizationAdministrationService;

@RestController
@RequestMapping("/api/immunizations")
public class ImmunizationProvider {

	private static final Logger log = LoggerFactory.getLogger(VaccinationProvider.class);

	private final ImmunizationAdministrationService immunizationService;

	public ImmunizationProvider(ImmunizationAdministrationService immunizationService) {
		this.immunizationService = immunizationService;
	}

	@PostMapping
	public ResponseEntity<VaccinationDto> createImmunization(@RequestParam String personId,
			@RequestBody ImmunizationCreateDto createDto) {

		var result = immunizationService.createImmunizationAdministration(personId, createDto);
		return ResponseEntity.status(HttpStatus.CREATED).body(result);
	}

	@PostMapping(value = "/import", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<ImportDto> importVaccinations(@RequestParam String personId,
			@RequestBody() MultipartFile file) {
		try {
			log.info("Importing vaccinations for\npersonId: {},\nfileName: {},\norigFileName: {},\ncontentType: {}",
					personId, file.getName(), file.getOriginalFilename(), file.getContentType());

			immunizationService.importVaccinations(personId, file.getContentType(), file.getInputStream());
		} catch (Exception e) {
			log.warn("Import failed: {}", e.getMessage(), e);
			return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
					.body(new ImportDto(HttpStatus.PRECONDITION_FAILED.name(), "import failed: " + e.getMessage()));
		}
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(new ImportDto(HttpStatus.CREATED.name(), "imported successfully"));
	}
}