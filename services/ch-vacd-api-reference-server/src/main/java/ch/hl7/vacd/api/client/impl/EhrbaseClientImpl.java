package ch.hl7.vacd.api.client.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.PreconditionFailedException;
import ch.hl7.vacd.api.client.EhrbaseClient;
import ch.hl7.vacd.api.domain.CompositionVersion;
import ch.hl7.vacd.api.domain.FlatComposition;
import ch.hl7.vacd.api.openehr.ChVacdOpenEhrConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Thin client for EHRbase 2.31.0 over the openEHR REST API. Auth: BASIC
 * ehrbase-user:SuperSecretPassword (dev creds).
 */
@Component
public class EhrbaseClientImpl implements EhrbaseClient {

	private static final Logger log = LoggerFactory.getLogger(EhrbaseClientImpl.class);
	private static final String NAMESPACE = "ch-vacd";

	private final RestTemplate restTemplate;
	private final ObjectMapper objectMapper;
	private final String baseUrl;
	private final String authHeader;

	public EhrbaseClientImpl(@Value("${ehrbase.url:http://ehrbase:8080/ehrbase/rest/openehr/v1}") String baseUrl,
			@Value("${ehrbase.user:ehrbase-user}") String user,
			@Value("${ehrbase.password:SuperSecretPassword}") String password) {
		this.restTemplate = new RestTemplate();
		this.objectMapper = new ObjectMapper();
		this.baseUrl = baseUrl;
		this.authHeader = "Basic "
				+ Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
	}

	private HttpHeaders headers(MediaType contentType) {
		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", authHeader);
		headers.setContentType(contentType);
		headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
		return headers;
	}

	/**
	 * List all ADL 1.4 templates registered in EHRbase.
	 */
	@Override
	public JsonNode listTemplates() {
		try {
			HttpHeaders hdrs = new HttpHeaders();
			hdrs.set("Authorization", authHeader);
			hdrs.set("Accept", MediaType.APPLICATION_JSON_VALUE);
			HttpEntity<Void> request = new HttpEntity<>(hdrs);
			ResponseEntity<String> response = restTemplate.exchange(baseUrl + "/definition/template/adl1.4",
					HttpMethod.GET, request, String.class);
			return objectMapper.readTree(response.getBody());
		} catch (Exception e) {
			log.warn("listTemplates failed: {}", e.getMessage());
			return objectMapper.createArrayNode();
		}
	}

	/**
	 * Upload an OPT (Operational Template) XML to EHRbase.
	 */
	@Override
	public String uploadOpt(String xml) {
		try {
			HttpHeaders hdrs = new HttpHeaders();
			hdrs.set("Authorization", authHeader);
			hdrs.setContentType(MediaType.APPLICATION_XML);
			HttpEntity<String> request = new HttpEntity<>(xml, hdrs);
			ResponseEntity<String> response = restTemplate.exchange(baseUrl + "/definition/template/adl1.4",
					HttpMethod.POST, request, String.class);
			return response.getBody();
		} catch (HttpClientErrorException e) {
			throw new RuntimeException("EHRbase upload OPT " + e.getStatusCode() + ": " + e.getResponseBodyAsString(),
					e);
		} catch (Exception e) {
			throw new RuntimeException("EHRbase upload OPT failed", e);
		}
	}

	/**
	 * Find an EHR linked to the given FHIR Patient id via
	 * EHR_STATUS.subject.external_ref.
	 */
	@Override
	public String findEhrByPatient(String patientId) {
		String aql = "SELECT e/ehr_id/value FROM EHR e " + "WHERE e/ehr_status/subject/external_ref/id/value = '"
				+ patientId + "' " + "AND e/ehr_status/subject/external_ref/namespace = '" + NAMESPACE + "'";
		try {
			ObjectNode body = objectMapper.createObjectNode();
			body.put("q", aql);
			HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(body),
					headers(MediaType.APPLICATION_JSON));
			ResponseEntity<String> response = restTemplate.exchange(baseUrl + "/query/aql", HttpMethod.POST, request,
					String.class);
			JsonNode parsed = objectMapper.readTree(response.getBody());
			JsonNode rows = parsed.get("rows");
			if (rows != null && rows.isArray() && !rows.isEmpty()) {
				JsonNode first = rows.get(0);
				if (first.isArray() && !first.isEmpty()) {
					return first.get(0).asText();
				}
			}
		} catch (HttpClientErrorException e) {
			log.debug("findEhrByPatient query returned {}", e.getStatusCode());
		} catch (Exception e) {
			log.warn("findEhrByPatient failed: {}", e.getMessage());
		}
		return null;
	}

	/**
	 * Create a new EHR with subject linked to the given FHIR Patient id.
	 */
	@Override
	public String createEhr(String patientId) {
		try {
			ObjectNode status = objectMapper.createObjectNode();
			status.put("_type", "EHR_STATUS");
			status.put("archetype_node_id", "openEHR-EHR-EHR_STATUS.generic.v1");
			status.putObject("name").put("value", "EHR Status");

			ObjectNode subject = status.putObject("subject");
			ObjectNode extRef = subject.putObject("external_ref");
			ObjectNode id = extRef.putObject("id");
			id.put("_type", "GENERIC_ID");
			id.put("value", patientId);
			id.put("scheme", "fhir");
			extRef.put("namespace", NAMESPACE);
			extRef.put("type", "PERSON");

			status.put("is_queryable", true);
			status.put("is_modifiable", true);

			HttpHeaders hdrs = headers(MediaType.APPLICATION_JSON);
			hdrs.set("Prefer", "return=representation");
			HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(status), hdrs);

			ResponseEntity<String> response = restTemplate.exchange(baseUrl + "/ehr", HttpMethod.POST, request,
					String.class);

			JsonNode parsed = objectMapper.readTree(response.getBody());
			JsonNode ehrIdNode = parsed.path("ehr_id").path("value");
			if (!ehrIdNode.isMissingNode()) {
				return ehrIdNode.asText();
			}
			// Fallback: check ETag or Location header
			String etag = response.getHeaders().getFirst("ETag");
			if (etag != null)
				return etag.replace("\"", "").trim();
			String location = response.getHeaders().getFirst("Location");
			if (location != null) {
				String[] parts = location.split("/");
				return parts[parts.length - 1].replace("\"", "").trim();
			}
			throw new RuntimeException("createEhr: no ehrId in response: " + response.getBody());
		} catch (HttpClientErrorException e) {
			throw new RuntimeException("EHRbase create EHR " + e.getStatusCode() + ": " + e.getResponseBodyAsString(),
					e);
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException("EHRbase create EHR failed", e);
		}
	}

	/**
	 * Find an existing EHR for the patient, or create one if none exists.
	 */
	@Override
	public String findOrCreateEhr(String patientId) {
		String ehrId = findEhrByPatient(patientId);
		if (ehrId != null)
			return ehrId;
		return createEhr(patientId);
	}

	/**
	 * POST a FLAT-format Composition to the given EHR. Returns the composition uid.
	 */
	@Override
	public String postCompositionFlat(String ehrId, String flatBody, String templateId) {
		try {
			String tidParam = (templateId != null) ? "&templateId=" + templateId : "";
			String url = baseUrl + "/ehr/" + ehrId + "/composition?format=FLAT" + tidParam;

			HttpHeaders hdrs = headers(MediaType.APPLICATION_JSON);
			hdrs.set("Prefer", "return=representation");
			HttpEntity<String> request = new HttpEntity<>(flatBody, hdrs);

			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
			String responseBody = response.getBody();

			// Try to extract compositionUid from response body
			JsonNode parsed = objectMapper.readTree(responseBody);
			if (parsed != null) {
				JsonNode ctxUid = parsed.path("ctx/composition_uid");
				if (!ctxUid.isMissingNode())
					return ctxUid.asText();
				JsonNode compUid = parsed.path("compositionUid");
				if (!compUid.isMissingNode())
					return compUid.asText();
				JsonNode uidValue = parsed.path("uid").path("value");
				if (!uidValue.isMissingNode())
					return uidValue.asText();
			}
			// Fallback: ETag or Location
			String etag = response.getHeaders().getFirst("ETag");
			if (etag != null)
				return etag.replace("\"", "").trim();
			String location = response.getHeaders().getFirst("Location");
			if (location != null) {
				String[] parts = location.split("/");
				return parts[parts.length - 1].replace("\"", "").trim();
			}
			throw new RuntimeException("EHRbase POST composition: no uid in response: " + responseBody);
		} catch (HttpClientErrorException e) {
			throw new RuntimeException(
					"EHRbase POST composition " + e.getStatusCode() + ": " + e.getResponseBodyAsString(), e);
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException("EHRbase POST composition failed", e);
		}
	}

	@Override
	public String getCompositionFlat(String ehrId, String compositionUid) {
		try {
			String url = baseUrl + "/ehr/" + ehrId + "/composition/" + compositionUid + "?format=FLAT";
			HttpEntity<Void> request = new HttpEntity<>(headers(MediaType.APPLICATION_JSON));
			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);
			return response.getBody();
		} catch (HttpClientErrorException e) {
			throw new RuntimeException(
					"EHRbase GET composition " + e.getStatusCode() + ": " + e.getResponseBodyAsString(), e);
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException("EHRbase GET composition failed", e);
		}
	}

	/**
	 * GET the latest version of a Composition in FLAT format. EHRbase returns the
	 * latest version when the uid carries no version; the version uid is in the
	 * ETag header.
	 */
	@Override
	public FlatComposition getLatestCompositionFlat(String ehrId, String versionedObjectUid) {
		try {
			String url = baseUrl + "/ehr/" + ehrId + "/composition/" + versionedObjectUid + "?format=FLAT";
			HttpEntity<Void> request = new HttpEntity<>(headers(MediaType.APPLICATION_JSON));
			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);
			String etag = response.getHeaders().getFirst("ETag");
			if (etag == null) {
				throw new RuntimeException("EHRbase GET composition: no ETag in response for " + versionedObjectUid);
			}
			return new FlatComposition(etag.replace("\"", "").trim(), response.getBody());
		} catch (HttpClientErrorException e) {
			throw new RuntimeException(
					"EHRbase GET composition " + e.getStatusCode() + ": " + e.getResponseBodyAsString(), e);
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException("EHRbase GET composition failed", e);
		}
	}

	/**
	 * PUT a FLAT-format Composition as the new version of an existing one. Returns
	 * the new version uid. EHRbase takes the preceding version uid unquoted in
	 * If-Match and answers 412 if it is not the latest version. It parses the body
	 * first, so any other error means the body is not a valid Composition.
	 */
	@Override
	public String updateCompositionFlat(String ehrId, String precedingVersionUid, String flatBody,
			String templateId) {
		try {
			String url = baseUrl + "/ehr/" + ehrId + "/composition/"
					+ CompositionVersion.versionedObjectUid(precedingVersionUid) + "?format=FLAT&templateId="
					+ templateId;

			HttpHeaders hdrs = headers(MediaType.APPLICATION_JSON);
			hdrs.set("If-Match", precedingVersionUid);
			HttpEntity<String> request = new HttpEntity<>(flatBody, hdrs);

			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PUT, request, String.class);
			String etag = response.getHeaders().getFirst("ETag");
			if (etag == null) {
				throw new RuntimeException("EHRbase PUT composition: no ETag in response for " + precedingVersionUid);
			}
			return etag.replace("\"", "").trim();
		} catch (HttpStatusCodeException e) {
			log.error("EHRbase PUT composition {} returned {}: {}", precedingVersionUid, e.getStatusCode(),
					e.getResponseBodyAsString());
			if (e.getStatusCode().value() == 412) {
				throw new PreconditionFailedException(
						"Composition " + precedingVersionUid + " is not the latest version anymore");
			}
			throw new RuntimeException(
					"EHRbase PUT composition " + e.getStatusCode() + ": " + e.getResponseBodyAsString(), e);
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException("EHRbase PUT composition failed", e);
		}
	}

	@Override
	public CompositionVersion getCompositionVersion(String ehrId, String versionedObjectUid) {
		return fetchCompositionVersions(ehrId, versionedObjectUid).stream().findFirst().orElse(null);
	}

	@Override
	public Map<String, CompositionVersion> getCompositionVersions(String ehrId) {
		Map<String, CompositionVersion> versions = new LinkedHashMap<>();
		for (CompositionVersion version : fetchCompositionVersions(ehrId, null)) {
			versions.put(CompositionVersion.versionedObjectUid(version.versionUid()), version);
		}
		return versions;
	}

	/**
	 * ONE AQL query returns a row of [version uid, commit time] per admin
	 * composition. AQL only sees the latest version of a composition.
	 */
	private List<CompositionVersion> fetchCompositionVersions(String ehrId, String versionedObjectUid) {
		String aql = "SELECT c/uid/value, v/commit_audit/time_committed/value "
				+ "FROM EHR e CONTAINS VERSION v CONTAINS COMPOSITION c "
				+ "WHERE c/archetype_details/template_id/value = '" + ChVacdOpenEhrConstants.ADMIN_TEMPLATE + "' "
				+ "AND e/ehr_id/value = '" + ehrId + "'";
		if (versionedObjectUid != null) {
			aql += " AND c/uid/value = '" + versionedObjectUid + "'";
		}

		return executeAql(aql).stream()
				.map(row -> new CompositionVersion(row.get(0).asText(),
						Date.from(OffsetDateTime.parse(row.get(1).asText()).toInstant())))
				.collect(Collectors.toList());
	}

	/**
	 * Execute an AQL query and return the parsed rows. Each row is a JsonNode
	 * array.
	 */
	@Override
	public List<JsonNode> executeAql(String aql) {
		try {
			ObjectNode body = objectMapper.createObjectNode();
			body.put("q", aql);
			HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(body),
					headers(MediaType.APPLICATION_JSON));
			ResponseEntity<String> response = restTemplate.exchange(baseUrl + "/query/aql", HttpMethod.POST, request,
					String.class);
			JsonNode parsed = objectMapper.readTree(response.getBody());
			JsonNode rows = parsed.get("rows");
			if (rows != null && rows.isArray()) {
				List<JsonNode> result = new ArrayList<>();
				for (JsonNode row : rows) {
					result.add(row);
				}
				return result;
			}
			return Collections.emptyList();
		} catch (HttpClientErrorException e) {
			throw new RuntimeException("EHRbase AQL " + e.getStatusCode() + ": " + e.getResponseBodyAsString(), e);
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException("EHRbase AQL failed", e);
		}
	}

	@Override
	public String getImmunizations(String ehrId) {
		try {
			// Step 1: Fetch all admin compositions from EHRbase via AQL.
			List<JsonNode> compositions = fetchAdminCompositions(ehrId);
			if (compositions.isEmpty()) {
				log.info("No admin compositions found for ehrId={}", ehrId);
				return null;
			}
			log.info("Found {} admin composition(s) for ehrId={}", compositions.size(), ehrId);

			// Step 2: Merge content[] arrays across all compositions.
			ObjectNode merged = mergeCompositions(compositions);

			// Step 3: Convert merged canonical composition to FHIR via openFHIR.
			ObjectMapper objectMapper = new ObjectMapper();
			String mergedJson = objectMapper.writeValueAsString(merged);
			return mergedJson;
		} catch (Exception e) {
			log.error("Failed to build vaccination record for ehrId={}: {}", ehrId, e.getMessage(), e);
			throw new InvalidRequestException("Failed to build vaccination record: " + e.getMessage());
		}
	}

	/**
	 * Step 1: ONE AQL query returns N rows of [canonical COMPOSITION].
	 */
	private List<JsonNode> fetchAdminCompositions(String ehrId) {
		String aql = "SELECT c " + "FROM EHR e CONTAINS COMPOSITION c "
				+ "WHERE c/archetype_details/template_id/value = '" + ChVacdOpenEhrConstants.ADMIN_TEMPLATE + "' " + "AND e/ehr_id/value = '"
				+ ehrId + "'";

		List<JsonNode> rows = executeAql(aql);

		// Each row is a single-column array [composition]; extract row[0].
		return rows.stream().map(row -> row.get(0)).collect(Collectors.toList());
	}

	/**
	 * Step 2: Deep-clone the first composition as a wrapper, replace its content[]
	 * with every ACTION from every source composition, sorted chronologically by
	 * ACTION.time.
	 */
	private ObjectNode mergeCompositions(List<JsonNode> compositions) {
		ObjectNode merged = compositions.get(0).deepCopy();

		ArrayNode allActions = compositions.stream()
				.flatMap(c -> StreamSupport.stream(c.path("content").spliterator(), false))
				.sorted(Comparator.comparing(a -> a.path("time").path("value").asText("")))
				.collect(objectMapper::createArrayNode, ArrayNode::add, ArrayNode::addAll);

		merged.set("content", allActions);
		return merged;
	}
}
