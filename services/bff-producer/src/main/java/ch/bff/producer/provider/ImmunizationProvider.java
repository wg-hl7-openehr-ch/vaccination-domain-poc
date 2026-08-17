package ch.bff.producer.provider;

import ch.bff.producer.provider.models.ImmunizationCreateDto;
import ch.bff.producer.provider.models.VaccinationDto;
import ch.bff.producer.services.ImmunizationAdministrationService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/immunizations")
public class ImmunizationProvider {

    private final ImmunizationAdministrationService immunizationService;

    public ImmunizationProvider(ImmunizationAdministrationService immunizationService) {
        this.immunizationService = immunizationService;
    }

    @PostMapping
    public ResponseEntity<VaccinationDto> createImmunization(
            @RequestParam String personId,
            @RequestBody ImmunizationCreateDto createDto) {

        var result = immunizationService.createImmunizationAdministration(personId, createDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
}