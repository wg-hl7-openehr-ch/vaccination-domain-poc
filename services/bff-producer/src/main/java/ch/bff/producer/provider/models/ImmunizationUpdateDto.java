package ch.bff.producer.provider.models;

import java.time.LocalDate;


public record ImmunizationUpdateDto(
        String version,
        String reason,
        String marketingAuthorizationHolder,
        String lotNumber,
        LocalDate vaccinationDate,
        RouteOfAdministration routeOfAdministration,
        AdministeredDose administeredDose,
        VaccinationReason vaccinationReason,
        Integer doseNumber
) {
    private static final int MAX_REASON_LENGTH = 500;

    public ImmunizationUpdateDto {
        if (version == null || version.isBlank()) throw new IllegalArgumentException("Version ist ein Pflichtfeld");
        if (!version.matches("\\d+")) throw new IllegalArgumentException("Version muss eine Zahl sein");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Grund der Korrektur ist ein Pflichtfeld");
        if (reason.length() > MAX_REASON_LENGTH) throw new IllegalArgumentException("Grund der Korrektur darf höchstens " + MAX_REASON_LENGTH + " Zeichen lang sein");
        if (lotNumber != null && lotNumber.isBlank()) throw new IllegalArgumentException("Chargennummer darf nicht leer sein");
        if (doseNumber != null && doseNumber < 1) throw new IllegalArgumentException("Dosisnummer muss mindestens 1 sein");
    }
}
