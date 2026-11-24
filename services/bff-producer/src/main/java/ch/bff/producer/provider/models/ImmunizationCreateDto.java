package ch.bff.producer.provider.models;

import java.time.LocalDate;

public record ImmunizationCreateDto(
        String vaccineName,
        String marketingAuthorizationHolder,
        String lotNumber,
        String vaccineCode,
        LocalDate expiryDate, // Optional laut Formular
        LocalDate vaccinationDate,
        RouteOfAdministration routeOfAdministration,
        AdministeredDose administeredDose,
        String siteOfAdministration,
        VaccinationReason vaccinationReason,
        int doseNumber,
        Integer seriesDoses, // Integer statt int, da es für Booster leer (null) sein darf
        boolean adverseReactionObserved
) {
    // Kompakter Konstruktor für die Validierung der Pflichtfelder
    public ImmunizationCreateDto {
        if (vaccineName == null || vaccineName.isBlank()) throw new IllegalArgumentException("vaccineName is required");
        if (vaccineCode == null /* || vaccineCode.isBlank()*/) throw new IllegalArgumentException("vaccineCode is required");
        if (marketingAuthorizationHolder == null /*|| marketingAuthorizationHolder.isBlank()*/) throw new IllegalArgumentException("marketingAuthorizationHolder is required");
        if (lotNumber == null || lotNumber.isBlank()) throw new IllegalArgumentException("lotNumber is required");
        if (vaccinationDate == null) throw new IllegalArgumentException("vaccinationDate is required");
        if (routeOfAdministration == null) throw new IllegalArgumentException("routeOfAdministration is required");
        if (administeredDose == null) throw new IllegalArgumentException("administeredDose is required");
        if (siteOfAdministration == null || siteOfAdministration.isBlank()) throw new IllegalArgumentException("siteOfAdministration is required");
        if (vaccinationReason == null || vaccinationReason.code() == null || vaccinationReason.code().isBlank()) throw new IllegalArgumentException("vaccinationReason is required");
        if (doseNumber < 1) throw new IllegalArgumentException("doseNumber must be at least 1");
    }
}

