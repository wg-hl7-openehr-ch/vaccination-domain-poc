package ch.vaccination.domain.audittrace.config;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.openapi.OpenApiInterceptor;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.interceptor.LoggingInterceptor;
import jakarta.servlet.Servlet;

@Configuration
public class FhirServletConfig {

	private Logger loggger = LoggerFactory.getLogger(FhirServletConfig.class);

	@Bean
	FhirContext fhirContext() {
		FhirContext ctx = FhirContext.forR4();
		return ctx;
	}

	@Bean
	public ServletRegistrationBean<Servlet> fhirServlet(FhirContext fhirContext,
			Collection<IResourceProvider> providers) {

		RestfulServer server = new RestfulServer(fhirContext);

		// Try to register the HAPI OpenAPI interceptor if present on the classpath
		try {
			OpenApiInterceptor openApiInterCept = new OpenApiInterceptor();
			openApiInterCept.setUseResourcePages(true);
			server.registerInterceptor(openApiInterCept);

		} catch (Exception ignored) {
			// ignore - openapi support is optional
			System.out
					.println("OpenAPI interceptor not registered - OpenAPI support is not available on the classpath.");
		}

		server.setResourceProviders(providers);

		LoggingInterceptor loggingInterceptor = new LoggingInterceptor();
		loggingInterceptor.setLoggerName("fhir.log");
		server.registerInterceptor(loggingInterceptor);

		ServletRegistrationBean<Servlet> registration = new ServletRegistrationBean<>(server, "/fhir/*");
		registration.setName("FhirServlet");
		return registration;
	}
}