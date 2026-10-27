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
}
