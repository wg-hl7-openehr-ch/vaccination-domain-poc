package ch.bff.producer.exception;

import com.fasterxml.jackson.databind.ObjectMapper;

import feign.FeignException;

/**
 * Extracts the human-readable message from a FHIR server error response
 * (OperationOutcome.issue[0].diagnostics), falling back to the Feign message.
 */
public final class FhirErrorMessages {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FhirErrorMessages() {
    }

    public static String diagnostics(FeignException ex) {
        try {
            var diagnostics = MAPPER.readTree(ex.contentUTF8()).path("issue").path(0).path("diagnostics");
            if (diagnostics.isTextual() && !diagnostics.asText().isBlank()) {
                return diagnostics.asText();
            }
        } catch (Exception ignored) {
            // body is not an OperationOutcome
        }
        return ex.getMessage();
    }
}
