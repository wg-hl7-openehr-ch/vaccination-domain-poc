package ch.bff.producer.provider.models;

// Record für die verabreichte Menge
public record AdministeredDose(
        double value,
        String unit // z.B. "ml"
) {
    public AdministeredDose {
        if (unit == null || unit.isBlank()) throw new IllegalArgumentException("administeredDose.unit is required");
    }
}
