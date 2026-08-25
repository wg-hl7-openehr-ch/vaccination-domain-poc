package ch.hl7.vacd.api.config;

import com.fasterxml.jackson.databind.JsonNode;

import ch.hl7.vacd.api.client.EhrbaseClient;
import ch.hl7.vacd.api.client.OpenFhirClient;
import ch.hl7.vacd.api.openehr.ChVacdOpenEhrConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One-shot, idempotent bootstrap for openFHIR and EHRbase.
 *
 * Uploads OPTs (Operational Templates), FHIRconnect context YAMLs, and model
 * YAMLs on application startup. openFHIR requires these to map FHIR resources
 * to openEHR FLAT format.
 *
 * Runs synchronously (blocks startup) so the server only accepts requests once
 * the context mappers are configured.
 */
@Component
public class OpenFhirBootstrap implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(OpenFhirBootstrap.class);

	private static final List<OptSpec> OPTS = List.of(
			new OptSpec(ChVacdOpenEhrConstants.ADMIN_TEMPLATE,
					"bootstrap/ch-vacd-immunization-administration.v1-alpha.opt"),
			new OptSpec(ChVacdOpenEhrConstants.VACC_TEMPLATE,
					"bootstrap/ch-vacd-vaccination-record.v1-alpha.opt"));

	private static final List<String> CONTEXT_FILES = List.of(
			"bootstrap/swiss.vacd.context.yml",
			"bootstrap/swiss.vacd-list.context.yml");

	private static final List<ModelSpec> MODELS = List.of(
			new ModelSpec("COMPOSITION.encounter.v1", "bootstrap/COMPOSITION.encounter.v1.model.yml"),
			new ModelSpec("COMPOSITION.vaccination_list.v0", "bootstrap/COMPOSITION.vaccination_list.v0.model.yml"),
			new ModelSpec("ACTION.medication.v1", "bootstrap/ACTION.medication.v1.model.yml"),
			new ModelSpec("CLUSTER.medication.v2", "bootstrap/CLUSTER.medication.v2.model.yml"));

	private final EhrbaseClient ehrbaseClient;
	private final OpenFhirClient openFhirClient;

	public OpenFhirBootstrap(EhrbaseClient ehrbaseClient, OpenFhirClient openFhirClient) {
		this.ehrbaseClient = ehrbaseClient;
		this.openFhirClient = openFhirClient;
	}

	@Override
	public void run(ApplicationArguments args) {
		log.info("Starting openFHIR/EHRbase bootstrap (blocking)...");
		try {
			waitForServices();
			cleanupOldMappings();
			uploadOptsToEhrbase();
			uploadOptsToOpenFhir();
			uploadContexts();
			uploadModels();
			verifyContexts();
			log.info("Bootstrap READY — openFHIR context and models configured");
		} catch (Exception e) {
			log.error("Bootstrap FAILED: {}", e.getMessage(), e);
		}
	}

	private void waitForServices() throws InterruptedException {
		for (int attempt = 1; attempt <= 60; attempt++) {
			try {
				if (openFhirClient.isHealthy()) {
					ehrbaseClient.listTemplates();
					log.info("openFHIR and EHRbase are reachable");
					return;
				}
			} catch (Exception ignored) {
			}
			log.info("Waiting for openFHIR + EHRbase (attempt {})", attempt);
			Thread.sleep(2000);
		}
		throw new RuntimeException("openFHIR / EHRbase not reachable after 120s");
	}

	private void cleanupOldMappings() {
		if (openFhirClient.deleteContext("ch-vacd-immunization.context")) {
			log.info("openFHIR: deleted old V1 context 'ch-vacd-immunization.context'");
		}
		if (openFhirClient.deleteModel("ACTION.medication.v1")) {
			log.info("openFHIR: deleted old model 'ACTION.medication.v1' (will re-upload)");
		}
	}

	private void uploadOptsToEhrbase() {
		JsonNode existing = ehrbaseClient.listTemplates();
		Set<String> presentIds = new HashSet<>();
		if (existing.isArray()) {
			for (JsonNode entry : existing) {
				JsonNode tid = entry.get("template_id");
				if (tid != null) presentIds.add(tid.asText());
			}
		}

		for (OptSpec opt : OPTS) {
			if (presentIds.contains(opt.templateId)) {
				log.info("EHRbase: template '{}' already present, skipping", opt.templateId);
				continue;
			}
			String xml = readClasspath(opt.classpathFile);
			ehrbaseClient.uploadOpt(xml);
			log.info("EHRbase: uploaded OPT '{}'", opt.templateId);
		}
	}

	private void uploadOptsToOpenFhir() {
		JsonNode existing = openFhirClient.listOpts();
		Set<String> presentIds = new HashSet<>();
		if (existing.isArray()) {
			for (JsonNode entry : existing) {
				addIfPresent(presentIds, entry, "templateId");
				addIfPresent(presentIds, entry, "originalTemplateId");
				addIfPresent(presentIds, entry, "displayTemplateId");
				addIfPresent(presentIds, entry, "template_id");
				JsonNode metadata = entry.get("metadata");
				if (metadata != null) addIfPresent(presentIds, metadata, "name");
			}
		}

		for (OptSpec opt : OPTS) {
			String normalized = opt.templateId.replace(' ', '_');
			if (presentIds.contains(opt.templateId) || presentIds.contains(normalized)) {
				log.info("openFHIR: template '{}' already present, skipping", opt.templateId);
				continue;
			}
			String xml = readClasspath(opt.classpathFile);
			openFhirClient.postOpt(xml);
			log.info("openFHIR: uploaded OPT '{}'", opt.templateId);
		}
	}

	private void uploadContexts() {
		// Always re-upload contexts to ensure they match the current configuration.
		for (String file : CONTEXT_FILES) {
			String yaml = readClasspath(file);
			try {
				String result = openFhirClient.postContextYaml(yaml);
				log.info("openFHIR: uploaded context from {} → {}", file, result);
			} catch (Exception e) {
				log.warn("openFHIR: context upload from {} failed (may already exist): {}", file, e.getMessage());
			}
		}
	}

	private void verifyContexts() {
		JsonNode contexts = openFhirClient.listContexts();
		log.info("openFHIR: {} context(s) registered after bootstrap", contexts.isArray() ? contexts.size() : 0);
		if (contexts.isArray()) {
			for (JsonNode ctx : contexts) {
				log.info("  context: {}", ctx.has("metadata") ? ctx.get("metadata") : ctx);
			}
		}
		JsonNode models = openFhirClient.listModels();
		log.info("openFHIR: {} model(s) registered after bootstrap", models.isArray() ? models.size() : 0);
	}

	private void uploadModels() {
		JsonNode existing = openFhirClient.listModels();
		Set<String> presentNames = new HashSet<>();
		if (existing.isArray()) {
			for (JsonNode entry : existing) {
				addIfPresent(presentNames, entry, "name");
				JsonNode metadata = entry.get("metadata");
				if (metadata != null) addIfPresent(presentNames, metadata, "name");
			}
		}

		for (ModelSpec model : MODELS) {
			if (presentNames.contains(model.metadataName)) {
				log.info("openFHIR: model '{}' already present, skipping", model.metadataName);
				continue;
			}
			String yaml = readClasspath(model.classpathFile);
			openFhirClient.postModelYaml(yaml);
			log.info("openFHIR: uploaded model '{}'", model.metadataName);
		}
	}

	private static void addIfPresent(Set<String> set, JsonNode node, String field) {
		JsonNode value = node.get(field);
		if (value != null && value.isTextual()) {
			set.add(value.asText());
		}
	}

	private static String readClasspath(String path) {
		try (InputStream is = new ClassPathResource(path).getInputStream()) {
			return new String(is.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new RuntimeException("classpath resource not found: " + path, e);
		}
	}

	private record OptSpec(String templateId, String classpathFile) {
	}

	private record ModelSpec(String metadataName, String classpathFile) {
	}
}
