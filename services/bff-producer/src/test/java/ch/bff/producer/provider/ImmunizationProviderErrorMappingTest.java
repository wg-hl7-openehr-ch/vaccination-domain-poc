package ch.bff.producer.provider;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import ch.bff.producer.exception.NotFoundException;
import ch.bff.producer.exception.RestExceptionHandler;
import ch.bff.producer.exception.ValidationException;
import ch.bff.producer.provider.models.ImmunizationCreateDto;
import ch.bff.producer.provider.models.ImmunizationUpdateDto;
import ch.bff.producer.provider.models.VaccinationDto;
import ch.bff.producer.services.ImmunizationAdministrationService;
import feign.FeignException;
import feign.Request;
import feign.Response;
import jakarta.servlet.ServletException;

/**
 * Verifies how failures surface over HTTP: create → 400 with the message,
 * import → 412 with the unprefixed message, correction → 409 on a version
 * conflict and 404 for an unknown entry.
 */
class ImmunizationProviderErrorMappingTest {

	private static final String CREATE_BODY = """
			{"vaccineName":"Boostrix","marketingAuthorizationHolder":"GSK","lotNumber":"LOT1","vaccineCode":"123",
			 "vaccinationDate":"2020-05-09","routeOfAdministration":"IM","administeredDose":{"value":0.5,"unit":"ml"},
			 "siteOfAdministration":"Oberarm","vaccinationReason":{"code":"373068000"},"doseNumber":1}
			""";

	private static final String UPDATE_BODY = """
			{"version":"1","reason":"Lot falsch erfasst","lotNumber":"LOT2"}
			""";

	private MockMvc mvcThrowing(RuntimeException ex) {
		ImmunizationAdministrationService service = new ImmunizationAdministrationService() {
			@Override
			public VaccinationDto createImmunizationAdministration(String patientIamId, ImmunizationCreateDto dto) {
				throw ex;
			}

			@Override
			public VaccinationDto updateImmunization(String patientIamId, String immunizationId,
					ImmunizationUpdateDto dto) {
				throw ex;
			}

			@Override
			public void importVaccinations(String personId, String contentType, InputStream inputStream) {
				throw ex;
			}
		};
		return MockMvcBuilders.standaloneSetup(new ImmunizationProvider(service))
				.setControllerAdvice(new RestExceptionHandler()).build();
	}

	@Test
	void create_validationException_returns400WithMessage() throws Exception {
		mvcThrowing(new ValidationException("Impfdatum darf nicht vor dem Geburtsdatum liegen"))
				.perform(post("/api/immunizations").param("personId", "p1").contentType(MediaType.APPLICATION_JSON)
						.content(CREATE_BODY))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Impfdatum darf nicht vor dem Geburtsdatum liegen"));
	}

	private static FeignException fhirError(int status, String diagnostics) {
		var request = Request.create(Request.HttpMethod.POST, "http://fhir/Bundle", Map.of(), null,
				StandardCharsets.UTF_8, null);
		var response = Response.builder().status(status).reason("Error").request(request)
				.headers(Map.of())
				.body("{\"resourceType\":\"OperationOutcome\",\"issue\":[{\"diagnostics\":\"" + diagnostics + "\"}]}",
						StandardCharsets.UTF_8)
				.build();
		return FeignException.errorStatus("FhirClient#call", response);
	}

	@Test
	void create_fhirServer422_returns400WithDiagnostics() throws Exception {
		mvcThrowing(fhirError(422, "must not be in the future"))
				.perform(post("/api/immunizations").param("personId", "p1").contentType(MediaType.APPLICATION_JSON)
						.content(CREATE_BODY))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("must not be in the future"));
	}

	// ---- correction ----

	@Test
	void update_fhirServer412_returns409WithReloadMessage() throws Exception {
		mvcThrowing(fhirError(412, "Immunization/i1 is at version 2, not at version 1"))
				.perform(put("/api/immunizations/i1").param("personId", "p1").contentType(MediaType.APPLICATION_JSON)
						.content(UPDATE_BODY))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Eintrag wurde inzwischen geändert, bitte neu laden"));
	}

	@Test
	void update_fhirServer404_returns404WithDiagnostics() throws Exception {
		mvcThrowing(fhirError(404, "Resource Immunization/i1 is not known"))
				.perform(put("/api/immunizations/i1").param("personId", "p1").contentType(MediaType.APPLICATION_JSON)
						.content(UPDATE_BODY))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Resource Immunization/i1 is not known"));
	}

	@Test
	void update_notFoundException_returns404WithMessage() throws Exception {
		mvcThrowing(new NotFoundException("Impfeintrag nicht gefunden"))
				.perform(put("/api/immunizations/i1").param("personId", "p1").contentType(MediaType.APPLICATION_JSON)
						.content(UPDATE_BODY))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Impfeintrag nicht gefunden"));
	}

	@Test
	void update_fhirServer422_returns400WithDiagnostics() throws Exception {
		mvcThrowing(fhirError(422, "Immunization.status must stay 'completed' in a correction"))
				.perform(put("/api/immunizations/i1").param("personId", "p1").contentType(MediaType.APPLICATION_JSON)
						.content(UPDATE_BODY))
				.andExpect(status().isBadRequest()).andExpect(
						jsonPath("$.message").value("Immunization.status must stay 'completed' in a correction"));
	}

	@Test
	void update_withoutReason_returns400WithMessage() throws Exception {
		mvcThrowing(new IllegalStateException("service must not be called"))
				.perform(put("/api/immunizations/i1").param("personId", "p1").contentType(MediaType.APPLICATION_JSON)
						.content("{\"version\":\"1\",\"lotNumber\":\"LOT2\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Grund der Korrektur ist ein Pflichtfeld"));
	}

	@Test
	void update_otherFhirClientError_isNotMapped() {
		// only 412, 404 and 422 have a mapping; a 400 of the FHIR server stays unhandled
		var fhirError = fhirError(400, "X-Provenance must be a Provenance resource in JSON");

		var ex = assertThrows(ServletException.class,
				() -> mvcThrowing(fhirError).perform(put("/api/immunizations/i1").param("personId", "p1")
						.contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY)));

		assertSame(fhirError, ex.getCause());
	}

	@Test
	void import_validationException_returns412WithUnprefixedMessage() throws Exception {
		var file = new MockMultipartFile("file", "bundle.json", "application/json", "{}".getBytes());

		mvcThrowing(new ValidationException("must not be before Patient.birthDate"))
				.perform(multipart("/api/immunizations/import").file(file).param("personId", "p1"))
				.andExpect(status().isPreconditionFailed())
				.andExpect(jsonPath("$.message").value("must not be before Patient.birthDate"));
	}
}
