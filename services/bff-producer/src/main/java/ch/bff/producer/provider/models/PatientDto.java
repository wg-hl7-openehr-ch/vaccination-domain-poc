package ch.bff.producer.provider.models;
import java.time.LocalDate;

public record PatientDto(
        String id,
        String lastName,
        String firstName,
        LocalDate birthDate,
        int age,
        Gender gender,
        String ahvNumber,
        AddressDto address,
        String email,
        String phoneNumber
) {
    public PatientDto {
        if (lastName == null || lastName.isBlank()) {
            throw new IllegalArgumentException("Nachname darf nicht leer sein");
        }
        if (firstName == null || firstName.isBlank()) {
            throw new IllegalArgumentException("Vorname darf nicht leer sein");
        }
        if (ahvNumber != null && !ahvNumber.matches("^756\\d{10}$")) {
            throw new IllegalArgumentException("Ungültiges AHV-Format");
        }
    }
}

