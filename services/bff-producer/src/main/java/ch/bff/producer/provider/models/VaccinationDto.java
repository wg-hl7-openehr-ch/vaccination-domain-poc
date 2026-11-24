package ch.bff.producer.provider.models;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record VaccinationDto(
        UUID id,
        String vaccineName,
        String vaccineCode,
        String doseSequence,
        LocalDate vaccinationDate,
        String manufacturer,
        String lotNumber,
        String administrationRoute,
        String siteOfAdministration,
        PractitionerDto practitioner,
        VaccinationReason vaccinationReason,
        List<CodingDto> targetDiseases
) {
    public VaccinationDto {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (vaccineName == null || vaccineName.isBlank()) {
            throw new IllegalArgumentException("vaccineName must not be blank");
        }
        if (vaccineCode == null/* || vaccineCode.isBlank(*/) {
            throw new IllegalArgumentException("vaccineCode must not be null");
        }
//        if (doseSequence == null || doseSequence.isBlank()) {
//            throw new IllegalArgumentException("Dosis-Reihenfolge (z.B. 1/2) darf nicht leer sein");
//        }
        if (vaccinationDate == null) {
            throw new IllegalArgumentException("vaccinationDate must not be null");
        }
//        if (lotNumber == null || lotNumber.isBlank()) {
//            throw new IllegalArgumentException("Chargennummer (Lot) darf nicht leer sein");
//        }
//        if (practitioner == null) {
//            throw new IllegalArgumentException("Angaben zur medizinischen Fachperson dürfen nicht null sein");
//        }
    }
}

