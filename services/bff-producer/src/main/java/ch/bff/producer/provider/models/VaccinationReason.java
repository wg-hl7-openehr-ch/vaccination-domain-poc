package ch.bff.producer.provider.models;

public record VaccinationReason(String code, String display, String swissLabel) {
    public VaccinationReason {
        if (code == null || code.isBlank())
            throw new IllegalArgumentException("vaccinationReason.code is required");
    }
}
