package ch.bff.producer.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Map;

@ControllerAdvice
public class RestExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        var cause = ex.getCause();
        String message = "Malformed request body";
        if (cause instanceof JsonMappingException jme && jme.getCause() != null) {
            message = jme.getCause().getMessage();
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Bad Request", "message", message));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(ValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Bad Request", "message", ex.getMessage()));
    }

    /** Business-rule rejection by the CH VACD FHIR server */
    @ExceptionHandler(FeignException.UnprocessableEntity.class)
    public ResponseEntity<Map<String, Object>> handleFhirUnprocessable(FeignException.UnprocessableEntity ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Bad Request", "message", FhirErrorMessages.diagnostics(ex)));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Not Found", "message", ex.getMessage()));
    }

    /** Unknown resource on the CH VACD FHIR server */
    @ExceptionHandler(FeignException.NotFound.class)
    public ResponseEntity<Map<String, Object>> handleFhirNotFound(FeignException.NotFound ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Not Found", "message", FhirErrorMessages.diagnostics(ex)));
    }

    /**
     * Version conflict on the CH VACD FHIR server (412): the entry was changed
     * since it was read. Feign has no exception type for 412, so every other
     * client error is thrown again and stays what it was.
     */
    @ExceptionHandler(FeignException.FeignClientException.class)
    public ResponseEntity<Map<String, Object>> handleFhirVersionConflict(FeignException.FeignClientException ex) {
        if (ex.status() != HttpStatus.PRECONDITION_FAILED.value()) {
            throw ex;
        }
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "Conflict", "message", "Eintrag wurde inzwischen geändert, bitte neu laden"));
    }
}
