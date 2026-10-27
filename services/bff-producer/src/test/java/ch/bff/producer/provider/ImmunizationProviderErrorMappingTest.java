package ch.bff.producer.provider;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import ch.bff.producer.exception.RestExceptionHandler;
import ch.bff.producer.exception.ValidationException;
import ch.bff.producer.provider.models.ImmunizationCreateDto;
import ch.bff.producer.provider.models.VaccinationDto;
import ch.bff.producer.services.ImmunizationAdministrationService;
import feign.FeignException;
import feign.Request;
import feign.Response;

/**
 * Verifies how validation failures surface over HTTP: create → 400 with the
 * message, import → 412 with the unprefixed message.
 */
class ImmunizationProviderErrorMappingTest {

	private static final String CREATE_BODY = """
			{"vaccineName":"Boostrix","marketingAuthorizationHolder":"GSK","lotNumber":"LOT1","vaccineCode":"123",
			 "vaccinationDate":"2020-05-09","routeOfAdministration":"IM","administeredDose":{"value":0.5,"unit":"ml"},
			 "siteOfAdministration":"Oberarm","vaccinationReason":{"code":"373068000"},"doseNumber":1}
			""";

	private MockMvc mvcThrowing(RuntimeException ex) {
		ImmunizationAdministrationService service = new ImmunizationAdministrationService() {
			@Override
			public VaccinationDto createImmunizationAdministration(String patientIamId, ImmunizationCreateDto dto) {
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

	@Test
	void create_fhirServer422_returns400WithDiagnostics() throws Exception {
		var request = Request.create(Request.HttpMethod.POST, "http://fhir/Bundle", Map.of(), null,
				StandardCharsets.UTF_8, null);
		var response = Response.builder().status(422).reason("Unprocessable Entity").request(request)
				.headers(Map.of())
				.body("{\"resourceType\":\"OperationOutcome\",\"issue\":[{\"diagnostics\":\"must not be in the future\"}]}",
						StandardCharsets.UTF_8)
				.build();

		mvcThrowing(FeignException.errorStatus("FhirClient#post", response))
				.perform(post("/api/immunizations").param("personId", "p1").contentType(MediaType.APPLICATION_JSON)
						.content(CREATE_BODY))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("must not be in the future"));
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
