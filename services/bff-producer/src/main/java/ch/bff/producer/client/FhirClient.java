package ch.bff.producer.client;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.ListResource;
import org.hl7.fhir.r4.model.Parameters;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "fhir-server", url = "${FHIR_BASE_URL:http://localhost:9111/ch-vacd-api-reference-server/fhir}")
public interface FhirClient {

    @GetMapping("/Patient")
    Bundle getPatient();

    @GetMapping("/Patient/{id}")
    org.hl7.fhir.r4.model.Patient getPatientById(@PathVariable("id") String id);

    @PostMapping("/Patient")
    org.hl7.fhir.r4.model.Patient createPatient(@RequestBody org.hl7.fhir.r4.model.Patient patient);

    /**
     * Beispiel: https://fhir.ch/ig/ch-vacd/4.0.0/Bundle-1-3-VaccinationRecord.json.html
     *
     * @param parameters
     * {
     *   "resourceType": "Parameters",
     *   "parameter": [
     *     {
     *       "name": "type",
     *       "valueCoding": {
     *         "system": "urn:oid:2.16.756.5.30.1.127.3.10.10",
     *         "code": "urn:che:epr:ch-vacd:vaccination-record:2022"
     *       }
     *     }
     *   ]
     * }
     * @return Bundle with all vaccinations (VaccinationRecord)
     */
    @PostMapping("/Patient/{id}/$export-document")
    Bundle getVaccinationRecord(@PathVariable("id") String id, @RequestBody Parameters parameters);

    /**
     * https://fhir.ch/ig/ch-vacd/6.0.0/immunization-administration-document.html
     * Required Sections:
     * - Section: Immunization Administration
     * Required Entries:
     * - Entry: Patient
     * - Entry: Immunization
     * - Entry: Organization
     * - Entry: Practitioner
     * - Entry: PractitionerRole
     *
     * @param immunizationAdministrationBundle
     * @return Bundle with all vaccinations (VaccinationRecord)
     */
    @PostMapping("/Bundle")
    Bundle postImmunizationAdministrationBundle(@RequestBody Bundle immunizationAdministrationBundle);

    @GetMapping("/List")
	Bundle getPatientLogEntries(@RequestParam("patient") String patient);

    /**
     * @return the Immunization at its latest version (meta.versionId); an unknown
     *         ID comes back as an empty resource
     */
    @GetMapping("/Immunization/{id}")
    Immunization getImmunizationById(@PathVariable("id") String id);

    /**
     * Corrects an Immunization: the server writes a new version of it.
     *
     * @param ifMatch    the version the correction is based on, as W/"&lt;version&gt;";
     *                   the server answers 412 if it is no longer the latest
     * @param provenance a Provenance resource as JSON carrying the reason
     * @return the corrected Immunization at its new version
     */
    @PutMapping("/Immunization/{id}")
    Immunization putImmunization(@PathVariable("id") String id, @RequestBody Immunization immunization,
            @RequestHeader("If-Match") String ifMatch, @RequestHeader("X-Provenance") String provenance);
}
