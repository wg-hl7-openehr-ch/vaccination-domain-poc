package ch.bff.producer.provider.models;

// Zugehöriges Record für die medizinische Fachperson (Arzt)
public record PractitionerDto(
        String doctorName,
        String gln
) {
    public PractitionerDto {
        if (doctorName == null || doctorName.isBlank()) {
            throw new IllegalArgumentException("doctorName must not be blank");
        }
        if (gln != null && !gln.matches("^7601\\d{9}$")) {
            throw new IllegalArgumentException("gln must be 13 digits starting with 7601");
        }
    }
}
