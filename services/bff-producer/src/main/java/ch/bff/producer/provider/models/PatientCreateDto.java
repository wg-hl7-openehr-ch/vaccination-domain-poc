package ch.bff.producer.provider.models;
import java.time.LocalDate;

public record PatientCreateDto(
        String lastName,
        String firstName,
        LocalDate birthDate,
        Gender gender,
        AddressDto address,
        String email,
        String phoneNumber,
        String ahv
) {
//    public PatientCreateDto {
//        if (lastName == null || lastName.isBlank()) {
//            throw new IllegalArgumentException("Nachname darf nicht leer sein");
//        }
//        if (firstName == null || firstName.isBlank()) {
//            throw new IllegalArgumentException("Vorname darf nicht leer sein");
//        }
//    }
}

