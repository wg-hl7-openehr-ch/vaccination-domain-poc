package ch.hl7.vacd.api.provider;

import java.util.List;
import java.util.UUID;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.annotation.Validate;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import ca.uhn.fhir.validation.ValidationResult;
import ch.hl7.vacd.api.business.BundleBusinessService;
import ch.hl7.vacd.api.exceptions.PatientNotFoundException;
import ch.hl7.vacd.api.utils.RessourceUtil;

@Component
public class BundleProvider implements IResourceProvider {

	private static final String CH_VACD_BUNDLE_PROFILE = "http://fhir.ch/ig/ch-vacd/StructureDefinition/ch-vacd-document-immunization-administration";

	private final BundleBusinessService bundleBusinessService;
	private final FhirContext fhirContext;

	public BundleProvider(FhirContext fhirContext, BundleBusinessService bundleBusinessService) {
		this.fhirContext = fhirContext;
		this.bundleBusinessService = bundleBusinessService;
	}

	@Override
	public Class<Bundle> getResourceType() {
		return Bundle.class;
	}

	// --- CRUD operations ---

	@Create
	public MethodOutcome create(@ResourceParam Bundle bundle) {
		// Ensure CH VACD profile is present in meta.
		bundle = RessourceUtil.ensureBundleProfile(bundle, CH_VACD_BUNDLE_PROFILE);

		try {
			MethodOutcome outcome = new MethodOutcome();
			Bundle created = bundleBusinessService.createBundle(bundle);
			outcome.setId(created.getIdElement());
			outcome.setResource(created);
			outcome.setCreated(true);
			return outcome;
		} catch (PatientNotFoundException e) {
			OperationOutcome oo = new OperationOutcome();
			oo.addIssue()//
					.setSeverity(OperationOutcome.IssueSeverity.ERROR)//
					.setCode(OperationOutcome.IssueType.NOTFOUND)//
					.setDiagnostics(e.getMessage());
			oo.setId(UUID.randomUUID().toString());
			throw new ResourceNotFoundException(e.getMessage(), oo);
		}

	}

	@Validate
	public MethodOutcome validate(@ResourceParam Bundle bundle) {
		// Ensure CH VACD profile is present in meta.
		ValidationResult result = fhirContext.newValidator().validateWithResult(bundle);

		MethodOutcome outcome = new MethodOutcome();
		outcome.setOperationOutcome((OperationOutcome) result.toOperationOutcome());
		return outcome;
	}

//	@Read
//	public Bundle read(@IdParam IdType id) {
//
//		return bundleBusinessService.readBundle(id);
//
//	}
//
//	@Search
//	public List<Bundle> search() {
//		return bundleBusinessService.searchBundles();
//
//	}

}
