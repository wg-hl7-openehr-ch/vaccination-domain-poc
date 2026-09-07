package ch.hl7.vacd.api.config;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.PrePopulatedValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.SnapshotGeneratingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.projecthusky.fhir.vacd.ch.common.narrative.ChVacdThymeleafNarrativeGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.interceptor.RequestValidatingInterceptor;
import ca.uhn.fhir.rest.server.interceptor.ResponseValidatingInterceptor;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import jakarta.servlet.Servlet;

@Configuration
public class FhirServletConfig {

	private Logger loggger = LoggerFactory.getLogger(FhirServletConfig.class);

	@Value("${fhir.providers:}")
	private List<String> resourceProviderClassNames;

	@Value(value = "${fhir.ig.files}")
	private List<String> igFiles;

	@Bean
	FhirContext fhirContext() {
		FhirContext ctx = FhirContext.forR4();
		
		ctx.setNarrativeGenerator(new ChVacdThymeleafNarrativeGenerator());
		// addNpmPackageValidationSupport(ctx);

		return ctx;
	}
	
	@Bean
	ChVacdNpmPackageValidationSupport npmPackageValidationSupport(FhirContext ctx) {
		ChVacdNpmPackageValidationSupport npmPackageSupport = new ChVacdNpmPackageValidationSupport(ctx);
		igFiles.forEach(file -> {
			loggger.info("ig file: " + file);
			try {
				// load the npm package of the ig
				npmPackageSupport.loadPackageFromClasspath(file);
			} catch (IOException e) {
				loggger.error("Error loading IG from package for validation (" + file + ")", e);
			}
		});
		return npmPackageSupport;
	}

	private ValidationSupportChain addNpmPackageValidationSupport(FhirContext ctx, ChVacdNpmPackageValidationSupport npmPackageSupport) {
		// Create a support chain including the NPM Package Support
		ValidationSupportChain validationSupportChain = new ValidationSupportChain();
//		validationSupportChain.addValidationSupport(new DefaultProfileValidationSupport(ctx));
		validationSupportChain.addValidationSupport(new PrePopulatedValidationSupport(ctx));
		validationSupportChain.addValidationSupport(new DefaultProfileValidationSupport(ctx));
		validationSupportChain.addValidationSupport(new SnapshotGeneratingValidationSupport(ctx));
		validationSupportChain.addValidationSupport(new InMemoryTerminologyServerValidationSupport(ctx));
//		validationSupportChain.addValidationSupport(new CommonCodeSystemsTerminologyService(ctx));
		validationSupportChain.addValidationSupport(npmPackageSupport);
		ctx.setValidationSupport(validationSupportChain);

//		IValidationSupport validationSupport = new CachingValidationSupport(validationSupportChain);
//		ctx.setValidationSupport(validationSupport);

		return validationSupportChain;
	}

	@Bean
	public ServletRegistrationBean<Servlet> fhirServlet(FhirContext fhirContext,
			Collection<IResourceProvider> providers, ChVacdNpmPackageValidationSupport npmPackageSupport) {

		RestfulServer server = new RestfulServer(fhirContext);

		// Try to register the HAPI OpenAPI interceptor if present on the classpath
		try {
//					OpenApiInterceptor openApiInterceptor = new OpenApiInterceptor();
//					server.registerInterceptor(openApiInterceptor);
			ChVacdOpenApiInterceptor openApiInterCept = new ChVacdOpenApiInterceptor();
			openApiInterCept.setUseResourcePages(true);
			server.registerInterceptor(openApiInterCept);

		} catch (Exception ignored) {
			// ignore - openapi support is optional
			System.out
					.println("OpenAPI interceptor not registered - OpenAPI support is not available on the classpath.");
		}

		// Register all discovered resource providers
		if (resourceProviderClassNames.isEmpty()) {
			server.setResourceProviders(providers);
		} else {
			List<IResourceProvider> filteredProviders = new java.util.ArrayList<>();
			providers.forEach(provider -> {
				String providerName = provider.getClass().getSimpleName();
				if (resourceProviderClassNames.contains(providerName)) {
					filteredProviders.add(provider);
				}
			});
			server.setResourceProviders(filteredProviders);
		}

		ValidationSupportChain validationSupportChain = addNpmPackageValidationSupport(fhirContext, npmPackageSupport);
		FhirInstanceValidator instanceValidator = new FhirInstanceValidator(validationSupportChain);
		{
			RequestValidatingInterceptor reqValidatorInterceptor = new RequestValidatingInterceptor();
			reqValidatorInterceptor.setFailOnSeverity(ResultSeverityEnum.FATAL);
			// reqValidatorInterceptor.setValidator(validator);
			reqValidatorInterceptor.addValidatorModule(instanceValidator);
			//reqValidatorInterceptor.setAddResponseHeaderOnSeverity(ResultSeverityEnum.WARNING);
			server.registerInterceptor(reqValidatorInterceptor);
		}

		{
			ResponseValidatingInterceptor resValidatorInterceptor = new ResponseValidatingInterceptor();
			resValidatorInterceptor.setFailOnSeverity(ResultSeverityEnum.FATAL);
			resValidatorInterceptor.addValidatorModule(instanceValidator);
			//resValidatorInterceptor.setAddResponseHeaderOnSeverity(ResultSeverityEnum.WARNING);
			server.registerInterceptor(resValidatorInterceptor);
		}

//		LoggingInterceptor logInterceptor = new LoggingInterceptor();
//		logInterceptor.setLogger(loggger);
//		server.registerInterceptor(logInterceptor);

		ChVacdLoggingInterceptor loggingInterceptor = new ChVacdLoggingInterceptor(fhirContext);
		loggingInterceptor.setLoggerName("fhir.log");
//		loggingInterceptor.setMessageFormat(
//				"Source[${remoteAddr}] Operation[${operationType} ${idOrResourceName}] Body[${requestBodyFhir}]");
//		loggingInterceptor.setMessageFormat(
//	            "Source[${remoteAddr}] Operation[${operationType} ${idOrResourceName}] UA[${requestHeader.user-agent}] Params[${requestParameters}]");
		loggingInterceptor.setMessageFormat1(
				"Source[${remoteAddr}] - Operation[${operationType} ${idOrResourceName}] - UA[${requestHeader.user-agent}] - Params[${requestParameters}]\nResource: ${requestBodyFhir}");
		loggingInterceptor.setMessageFormat2(
				"Source[${remoteAddr}] - Operation[${operationType} ${idOrResourceName}] - UA[${requestHeader.user-agent}] - Params[${requestParameters}]");
		server.registerInterceptor(loggingInterceptor);

		ServletRegistrationBean<Servlet> registration = new ServletRegistrationBean<>(server, "/fhir/*");
		registration.setName("FhirServlet");
		return registration;
	}
}