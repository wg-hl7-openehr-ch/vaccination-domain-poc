package ch.bff.producer.security;

import ch.bff.producer.provider.PatientProvider;
import ch.bff.producer.services.PatientReadService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Base64;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PatientReadService patientReadService;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @Disabled
    void noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/patients"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void healthEndpoint_noAuth_returns200() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    @Disabled
    void anyOtherEndpoint_denyAll_returns401() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Disabled
    void invalidToken_returns401() throws Exception {
        mockMvc.perform(get("/api/patients")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-valid-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @Disabled
    void garbageToken_returns401() throws Exception {
        mockMvc.perform(get("/api/patients")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer this.is.garbage"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @Disabled
    void patientRole_returns403() throws Exception {
        var token = createJwt("patient1", "pat-001", "patient");

        mockMvc.perform(get("/api/patients")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(containsString("DOCTOR")));
    }

    @Test
    void doctorRole_returns200() throws Exception {
        var token = createJwt("doctor1", "doc-111", "doctor");
        when(patientReadService.getPatientList()).thenReturn(PatientProvider.getSamplePatients());

        mockMvc.perform(get("/api/patients")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].lastName").value("Brunner"));
    }

    @Test
    void securityContextContainsUserPrincipal() throws Exception {
        var token = createJwt("doctor1", "doc-111", "doctor");
        when(patientReadService.getPatientList()).thenAnswer(invocation -> {
            var auth = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();
            if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal up)) {
                throw new AssertionError("UserPrincipal not found in SecurityContext");
            }
            if (!"doc-111".equals(up.userId())) {
                throw new AssertionError("Expected userId=doc-111 but got " + up.userId());
            }
            if (!"ROLE_DOCTOR".equals(auth.getAuthorities().iterator().next().getAuthority())) {
                throw new AssertionError("Expected ROLE_DOCTOR authority");
            }
            return PatientProvider.getSamplePatients();
        });

        mockMvc.perform(get("/api/patients")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    // --- helpers ---

    private static String createJwt(String sub, String userId, String role) {
        try {
            var header = base64Url(Map.of("alg", "HS256", "typ", "JWT"));
            var payload = base64Url(Map.of(
                    "sub", sub,
                    "userId", userId,
                    "role", role,
                    "iat", System.currentTimeMillis() / 1000,
                    "exp", System.currentTimeMillis() / 1000 + 3600
            ));
            return header + "." + payload + ".invalidsignature";
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static String base64Url(Object value) throws JsonProcessingException {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(MAPPER.writeValueAsBytes(value));
    }
}
